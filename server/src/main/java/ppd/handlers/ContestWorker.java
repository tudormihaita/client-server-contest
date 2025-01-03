package ppd.handlers;

import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppd.models.ScoreProcessingQueue;
import ppd.models.SynchronizedRankingLinkedList;
import ppd.request.Request;
import ppd.response.CountryScore;
import ppd.response.ParticipantScore;
import ppd.response.Response;
import ppd.response.ResponseType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static ppd.utils.ContestConfig.DELTA_T;

public class ContestWorker extends Thread {
    private final Socket clientSocket;
    private final ServerSocket serverSocket;

    private final ExecutorService readerExecutor;
    private final ExecutorService rankingExecutor;

    private final ScoreProcessingQueue queue;
    private final SynchronizedRankingLinkedList rankingList;

    private static final Logger log = LogManager.getLogger(ContestWorker.class);

    private final CountDownLatch finalRankingReadyLatch;
    private final AtomicInteger remainingClients;
    private final AtomicInteger countriesLeft;
    private final Set<Integer> finishedCountries;

    private volatile List<CountryScore> cachedPartialRanking = null;
    private volatile long lastComputedTime = 0L;

    public ContestWorker(Socket clientSocket,
                         ServerSocket socket,
                         ExecutorService readerExecutor,
                         ExecutorService rankingExecutor,
                         AtomicInteger remainingClients,
                         AtomicInteger countriesLeft,
                         Set<Integer> finishedCountries,
                         CountDownLatch finalRankingReadyLatch,
                         ScoreProcessingQueue queue,
                         SynchronizedRankingLinkedList rankingList) {
        this.clientSocket = clientSocket;
        this.serverSocket = socket;
        this.readerExecutor = readerExecutor;
        this.rankingExecutor = rankingExecutor;
        this.finalRankingReadyLatch = finalRankingReadyLatch;
        this.remainingClients = remainingClients;
        this.countriesLeft = countriesLeft;
        this.finishedCountries = finishedCountries;
        this.queue = queue;
        this.rankingList = rankingList;
    }


    @Override
    public void run() {
        log.info("Worker started for connected client client");
        try (var out = new ObjectOutputStream(clientSocket.getOutputStream());
             var in = new ObjectInputStream(clientSocket.getInputStream())) {

            var request = (Request) in.readObject();
            log.info("Received request: {}", request);

            handleRequest(request, out);
            clientSocket.close();
        } catch (IOException | ClassNotFoundException e) {
            log.error(e);
        }
    }

    private void handleRequest(Request request, ObjectOutputStream out) throws IOException {
        switch (request.getType()) {
            case SCORE_SUBMISSION -> {
                log.info("Received score submission request: {}", request);
                processScoreSubmissions(request, out);
            }
            case PARTIAL_COUNTRY_RANKING -> {
                log.info("Received partial country ranking request: {}", request);
                processPartialCountryRanking(request, out);
            }
            case FINAL_PARTICIPANT_RANKING -> {
                log.info("Received final participant ranking request: {}", request);
                processFinalParticipantRanking(request, out);
            }
            default -> log.error("Invalid request type: {}", request.getType());
        }
    }

    @SneakyThrows
    private void sendErrorResponse(ObjectOutputStream out, String message) {
        var response = Response.builder()
                .type(ResponseType.ERROR)
                .message(message)
                .build();

        out.writeObject(response);
        out.flush();
    }

    private void signalCountrySubmissionsFinished(Request request) {
        if (!finishedCountries.contains(request.getCountry())) {
            finishedCountries.add(request.getCountry());
            countriesLeft.decrementAndGet();
            finalRankingReadyLatch.countDown();
        }
    }

    private void signalClientFinished(Request request) {
        if (finishedCountries.contains(request.getCountry())) {
            log.info("Client {} finished", request.getCountry());
            remainingClients.decrementAndGet();
            log.info("Clients left: {}", remainingClients.get());
        }
    }

    @SneakyThrows
    private void processScoreSubmissions(Request request, ObjectOutputStream out) {
        var submissions = request.getSubmissions();
        var country = request.getCountry();
        readerExecutor.submit(new SubmissionsReader(submissions, country, queue));

        var response = Response.builder()
                .type(ResponseType.SUCCESS)
                .message("Score submissions received successfully")
                .build();

        out.writeObject(response);
        out.flush();
    }

    private void processPartialCountryRanking(Request request, ObjectOutputStream out) {
        try {
            long currentTime = System.currentTimeMillis();
            if (cachedPartialRanking != null && (currentTime - lastComputedTime) <= DELTA_T) {
                log.info("Sending cached partial country ranking...");
                var response = Response.builder()
                        .type(ResponseType.SUCCESS)
                        .countryRanking(cachedPartialRanking)
                        .build();

                out.writeObject(response);
                out.flush();
            } else {

                log.info("Computing partial country ranking...");
                Future<List<CountryScore>> rankingComputation = rankingExecutor.submit(rankingList::getCountryRanking);
                var partialRanking = rankingComputation.get();
                cachedPartialRanking = partialRanking;
                lastComputedTime = currentTime;

                var response = Response.builder()
                        .type(ResponseType.SUCCESS)
                        .countryRanking(partialRanking)
                        .build();

                out.writeObject(response);
                out.flush();
            }
            signalCountrySubmissionsFinished(request);
            log.info("Partial country ranking sent to client: {}", request.getCountry());
        } catch (IOException | ExecutionException | InterruptedException e) {
            log.error(e);
            sendErrorResponse(out, "Error processing partial country ranking");
        }
    }

    private void processFinalParticipantRanking(Request request, ObjectOutputStream out) {
        try {
            finalRankingReadyLatch.await();

            Future<List<ParticipantScore>> rankingComputation = rankingExecutor.submit(rankingList::getParticipantRanking);
            var finalRanking = rankingComputation.get();
            var response = Response.builder()
                    .type(ResponseType.SUCCESS)
                    .participantRanking(finalRanking)
                    .build();

            out.writeObject(response);
            out.flush();
            log.info("Final participant ranking sent to client: {}", request.getCountry());

            signalClientFinished(request);

            if (remainingClients.get() == 0) {
                serverSocket.close();
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            log.error(e);
            sendErrorResponse(out, "Error processing final participant ranking");
        }

    }
}
