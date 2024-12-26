package ppd.handlers;

import lombok.AllArgsConstructor;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static ppd.utils.ContestConfig.DELTA_T;


@AllArgsConstructor
public class ContestReader implements Runnable {
    private final Socket clientSocket;
    private final ServerSocket serverSocket;
    private final ExecutorService executor;
    private final ScoreProcessingQueue queue;
    private final SynchronizedRankingLinkedList rankingList;

    private final AtomicInteger countriesLeft;
    private final AtomicInteger clientConnectionsLeft;
    private final Set<Integer> finishedCountries;

    private static final Logger log = LogManager.getLogger(ContestReader.class);

    private volatile List<CountryScore> cachedPartialRanking = null;
    private volatile long lastComputedTime = 0L;

    public ContestReader(Socket clientSocket, ServerSocket serverSocket, ExecutorService executor, ScoreProcessingQueue queue, SynchronizedRankingLinkedList rankingList,
                         AtomicInteger countriesLeft, AtomicInteger clientConnectionsLeft, Set<Integer> finishedCountries) {
        this.clientSocket = clientSocket;
        this.serverSocket = serverSocket;
        this.executor = executor;
        this.queue = queue;
        this.rankingList = rankingList;
        this.countriesLeft = countriesLeft;
        this.clientConnectionsLeft = clientConnectionsLeft;
        this.finishedCountries = finishedCountries;
    }

    @Override
    public void run() {
        log.info("Reader started for client: {}", clientSocket.getInetAddress());
        try (var out = new ObjectOutputStream(clientSocket.getOutputStream());
             var in = new ObjectInputStream(clientSocket.getInputStream())) {

            var request = (Request) in.readObject();
            log.info("Received request: {}", request);
            handleRequest(request, out);
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
        clientSocket.close();
    }

    @SneakyThrows
    private void processScoreSubmissions(Request request, ObjectOutputStream out) {
        var submissions = request.getSubmissions();
        submissions.forEach(submission -> {
            try {
                var id = submission.id();
                var points = submission.score();
                var country = request.getCountry();
                queue.enqueue(id, country, points);
            } catch (InterruptedException e) {
                log.error(e);
                sendErrorResponse(out, "Error processing score submissions");
            }
        });

        var response = Response.builder()
                .type(ResponseType.SUCCESS)
                .message("Score submissions processed successfully")
                .build();

        out.writeObject(response);
        out.flush();
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

    private void processPartialCountryRanking(Request request, ObjectOutputStream out) {
        try {
            long currentTime = System.currentTimeMillis();
            if (cachedPartialRanking != null && (currentTime - lastComputedTime) <= DELTA_T * 1000) {
                log.info("Sending cached partial country ranking...");
                var response = Response.builder()
                        .type(ResponseType.SUCCESS)
                        .countryRanking(cachedPartialRanking)
                        .build();

                out.writeObject(response);
                out.flush();
            } else {

                log.info("Computing partial country ranking...");
                Future<List<CountryScore>> rankingComputation = executor.submit(rankingList::getCountryRanking);
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
            log.info("Partial country ranking sent to client: {}", request.getCountry());

            if (!finishedCountries.contains(request.getCountry())) {
                finishedCountries.add(request.getCountry());
                countriesLeft.decrementAndGet();
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            log.error(e);
            sendErrorResponse(out, "Error processing partial country ranking");
        }
    }

    private boolean isFinalRankingReady() {
        return countriesLeft.get() == 0;
    }

    private void processFinalParticipantRanking(Request request, ObjectOutputStream out) {
        try {
            var ready = isFinalRankingReady();
            if (!ready) {
                sendErrorResponse(out, "Final ranking not ready yet");
                return;
            }

            Future<List<ParticipantScore>> rankingComputation = executor.submit(rankingList::getParticipantRanking);
            var finalRanking = rankingComputation.get();
            var response = Response.builder()
                    .type(ResponseType.SUCCESS)
                    .participantRanking(finalRanking)
                    .build();

            out.writeObject(response);
            out.flush();
            log.info("Final participant ranking sent to client: {}", request.getCountry());

            if (finishedCountries.contains(request.getCountry())) {
                log.info("Client {} finished", request.getCountry());
                clientConnectionsLeft.decrementAndGet();
                log.info("Clients left: {}", clientConnectionsLeft.get());
            }

            if (clientConnectionsLeft.get() == 0) {
                serverSocket.close();
            }
        } catch (IOException | ExecutionException | InterruptedException e) {
            log.error(e);
            sendErrorResponse(out, "Error processing final participant ranking");
        }

    }
}
