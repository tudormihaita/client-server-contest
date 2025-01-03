package ppd;

import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppd.request.Request;
import ppd.request.RequestType;
import ppd.request.ScoreSubmission;
import ppd.response.Response;
import ppd.response.ResponseType;
import ppd.utils.CountryMapper;

import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static ppd.utils.ContestConfig.*;

public class CountryHandler implements Runnable {
    private final List<String> fileNames = new ArrayList<>();
    private final int countryId;

    private static final Logger log = LogManager.getLogger(CountryHandler.class);

    public CountryHandler(int countryId) {
        this.countryId = countryId;
    }

    @SneakyThrows
    @Override
    public void run() {
        var countryName = CountryMapper.getCountryName(countryId);
        if (countryName == null) {
            log.error("Invalid country id.");
            System.exit(1);
        }

        initializeFileNames(countryId);
        log.info("Files to be read: {}", fileNames);
        var buffer = new ArrayList<ScoreSubmission>();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            for (var fileName : fileNames) {
                try (var scanner = new Scanner(new FileReader(fileName))) {
                    while (scanner.hasNextLine()) {
                        var line = scanner.nextLine();
                        var tokens = line.split(",");
                        var id = Integer.parseInt(tokens[0]);
                        var points = Integer.parseInt(tokens[1]);

                        buffer.add(new ScoreSubmission(id, points));
                        if (buffer.size() == CHUNK_SIZE) {
                            var submissions = new ArrayList<>(buffer);
                            sendScheduledRequest(scheduler, countryId, submissions);
                            buffer.clear();
                        }
                    }

                    if (!buffer.isEmpty()) {
                        var submissions = new ArrayList<>(buffer);
                        sendScheduledRequest(scheduler, countryId, submissions);
                        buffer.clear();
                    }
                } catch (IOException e) {
                    log.error("Error reading file: {}", fileName);
                }
            }
        } finally {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(1, TimeUnit.MINUTES)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
        }

        log.info("Client {} finished sending all data processing requests.", countryName);

        var partialRankingRequest = Request.builder()
                .type(RequestType.PARTIAL_COUNTRY_RANKING)
                .country(countryId)
                .build();
        log.info("Sending partial ranking request to server: {}", partialRankingRequest);
        var partialRankingResponse = sendRequest(partialRankingRequest);
        log.info("Received country partial ranking response from server: {}", partialRankingResponse);

        if (partialRankingResponse.getType() == ResponseType.SUCCESS) {
            System.out.println("Partial ranking received from server:");
            partialRankingResponse.getCountryRanking().forEach(System.out::println);
        } else {
            log.error("Error receiving partial ranking from server: {}", partialRankingResponse.getMessage());
        }

        var finalRankingRequest = Request.builder()
                .type(RequestType.FINAL_PARTICIPANT_RANKING)
                .country(countryId)
                .build();
        log.info("Sending final ranking request to server: {}", finalRankingRequest);
        var finalRankingResponse = sendRequest(finalRankingRequest);
        log.info("Received final ranking response from server: {}", finalRankingResponse);

        if (finalRankingResponse.getType() == ResponseType.SUCCESS) {
            System.out.println("Final ranking received from server:");
            finalRankingResponse.getParticipantRanking().forEach(System.out::println);
        } else {
            log.error("Error receiving final ranking from server: {}", finalRankingResponse.getMessage());
        }

        log.info("Client {} finished.", countryName);
    }

    private void initializeFileNames(int countryId) {
        for (int problem = 1; problem <= PROBLEMS; problem++) {
            var fileName = String.format("%s/results_c%d_p%d.txt", CLIENT_DATA_DIR, countryId, problem);
            fileNames.add(fileName);
        }
    }

    private Response sendRequest(Request request) throws IOException, ClassNotFoundException {
        try (var socket = new Socket("localhost", PORT);
             var out = new ObjectOutputStream((socket.getOutputStream()));
             var in = new ObjectInputStream(socket.getInputStream())) {

            out.writeObject(request);
            out.flush();

            return (Response) in.readObject();
        }
    }

    private Response sendRequestWithPolling(Request request) throws IOException, InterruptedException, ClassNotFoundException {
        int retries = 0;
        Response response;
        do {
            response = sendRequest(request);
            if (response.getType() == ResponseType.SUCCESS) {
                break;
            }
            retries++;
            Thread.sleep(1000 * RETRY_DELAY);
        } while (retries < MAX_RETRIES);

        return response;
    }

    private void sendScheduledRequest(ScheduledExecutorService scheduler, int countryId, List<ScoreSubmission> submissions) {
        scheduler.schedule(() -> {
            try {
                var request = Request.builder()
                        .type(RequestType.SCORE_SUBMISSION)
                        .country(countryId)
                        .submissions(submissions)
                        .build();

                log.info("Sending request to server: {}", request);
                var response = sendRequest(request);
                log.info("Received score submission response from server: {}", response);
            } catch (IOException | ClassNotFoundException e) {
                log.error("Error sending request to server: {}", e.getMessage());
            }
        }, DELTA_X, TimeUnit.SECONDS);
    }
}
