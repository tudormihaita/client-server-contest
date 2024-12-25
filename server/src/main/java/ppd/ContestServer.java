package ppd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppd.handlers.ContestReader;
import ppd.handlers.ContestWorker;
import ppd.models.ScoreProcessingQueue;
import ppd.models.SynchronizedRankingLinkedList;
import ppd.response.CountryScore;
import ppd.response.ParticipantScore;

import java.io.*;
import java.net.ServerSocket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static ppd.utils.ContestConfig.*;

public class ContestServer {
    private static final AtomicInteger countriesLeft = new AtomicInteger(COUNTRIES);
    private static final AtomicInteger clientsConnectionsLeft = new AtomicInteger(COUNTRIES);
    private static final ExecutorService executor = Executors.newFixedThreadPool(READERS);

    private static final List<Integer> finishedCountries = new ArrayList<>();
    private static final ScoreProcessingQueue queue = new ScoreProcessingQueue(MAX_QUEUE_CAPACITY, countriesLeft);
    private static final SynchronizedRankingLinkedList rankingList = new SynchronizedRankingLinkedList();

    protected static final Logger log = LogManager.getLogger(ContestServer.class);

    public static void main(String[] args) {
        var workerThreads = new Thread[WORKERS];
        for (int i = 0; i < WORKERS; i++) {
            var worker = new ContestWorker(queue, rankingList);
            workerThreads[i] = worker;
        }
        Arrays.stream(workerThreads).forEach(Thread::start);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log.info("Server started on port: {}, waiting for clients...", PORT);
            while (clientsConnectionsLeft.get() > 0) {
                try {
                    final var clientSocket = serverSocket.accept();
                    log.info("Client connected, starting reader to process request...");
                    executor.submit(new ContestReader(clientSocket, executor, queue, rankingList,
                            countriesLeft, clientsConnectionsLeft, finishedCountries));
                } catch (IOException e) {
                    log.error(e);
                }
            }
        } catch (IOException e) {
            log.error(e);
        }

        Arrays.stream(workerThreads).forEach(worker -> {
            try {
                worker.join();
                log.info("Worker {} finished", worker.getName());
            } catch (InterruptedException e) {
                log.error(e);
            }
        });
        executor.shutdown();

        var participantRankingPath = SERVER_DATA_DIR + "/participant_ranking_parallel.txt";
        var countryRankingPath = SERVER_DATA_DIR + "/country_ranking_parallel.txt";
        outputParticipantRanking(rankingList.getParticipantRanking(), participantRankingPath);
        outputCountryRanking(rankingList.getCountryRanking(), countryRankingPath);

        var validParticipantRankingPath = SERVER_DATA_DIR + "/participant_ranking_valid.txt";
        var validCountryRankingPath = SERVER_DATA_DIR + "/country_ranking_valid.txt";

        if (!validateRanking(validParticipantRankingPath, participantRankingPath) ||
                !validateRanking(validCountryRankingPath, countryRankingPath)) {
            log.error("Invalid ranking computed!");
        } else {
            log.info("Ranking is valid.");
        }

    }

    public static void outputParticipantRanking(List<ParticipantScore> ranking, String outputPath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            for (var node : ranking) {
                writer.write(node.id() + "," + node.score() + "," + node.country());
                writer.println();
            }
        } catch (IOException e) {
            log.error("Error writing participant ranking to file {}: {}", outputPath, e.getMessage());
        }
    }

    public static void outputCountryRanking(List<CountryScore> ranking, String outputPath) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputPath))) {
            for (var node : ranking) {
                writer.write(node.country() + "," + node.totalScore());
                writer.println();
            }
        } catch (IOException e) {
            log.error("Error writing country ranking to file {}: {}", outputPath, e.getMessage());
        }
    }

    public static boolean validateRanking(String validSequentialPath, String parallelPath) {
        try (var sequentialScanner = new Scanner(new File(validSequentialPath));
             var parallelScanner = new Scanner(new File(parallelPath))) {
            while (sequentialScanner.hasNextLine() && parallelScanner.hasNextLine()) {
                var sequentialLine = sequentialScanner.nextLine();
                var parallelLine = parallelScanner.nextLine();
                if (!sequentialLine.equals(parallelLine)) {
                    return false;
                }
            }
            return !sequentialScanner.hasNextLine() && !parallelScanner.hasNextLine();
        } catch (FileNotFoundException e) {
            return false;
        }
    }
}
