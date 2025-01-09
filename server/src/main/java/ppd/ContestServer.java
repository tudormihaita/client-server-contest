package ppd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppd.handlers.ContestWorker;
import ppd.handlers.RankingWriter;
import ppd.models.ScoreProcessingQueue;
import ppd.models.SynchronizedRankingLinkedList;
import ppd.response.CountryScore;
import ppd.response.ParticipantScore;

import java.io.*;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static ppd.utils.ContestConfig.*;

public class ContestServer {
    private static final AtomicInteger countriesLeft = new AtomicInteger(COUNTRIES);
    private static final AtomicInteger remainingClients = new AtomicInteger(COUNTRIES);
    private static final CountDownLatch finalRankingReadyLatch = new CountDownLatch(COUNTRIES);
    private static final Set<Integer> finishedCountries = new ConcurrentSkipListSet<>();

    private static final ExecutorService readerExecutor = Executors.newFixedThreadPool(READERS);
    private static final ExecutorService rankingExecutor = Executors.newSingleThreadExecutor();

    private static final ScoreProcessingQueue queue = new ScoreProcessingQueue(MAX_QUEUE_CAPACITY, countriesLeft);
    private static final SynchronizedRankingLinkedList rankingList = new SynchronizedRankingLinkedList();

    protected static final Logger log = LogManager.getLogger(ContestServer.class);

    private static double startTime = 0;
    private static double endTime = 0;

    public static void main(String[] args) {
        var workerThreads = new ArrayList<Thread>();
        var writerThreads = new Thread[WRITERS];

        for (int i = 0; i < WRITERS; i++) {
            var worker = new RankingWriter(queue, rankingList);
            writerThreads[i] = worker;
        }
        Arrays.stream(writerThreads).forEach(Thread::start);

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            log.info("Server started on port: {}, waiting for clients...", PORT);
            // Alternative to shutting down the server socket prematurely: setting an accept timeout to re-check loop break condition
            // serverSocket.setSoTimeout(1000 * SERVER_TIMEOUT);

            while (true) {
                log.info("Waiting for client connection...");

                if (remainingClients.get() == 0) {
                    log.info("All clients have connected, shutting down socket and waiting for workers to finish...");
                    break;
                }

                try {
                    final var clientSocket = serverSocket.accept();

                    if (startTime == 0) {
                        startTime = System.nanoTime();
                    }

                    log.info("Client connected, starting reader to process request...");
                    var worker = new ContestWorker(
                            clientSocket, serverSocket, readerExecutor, rankingExecutor,
                            remainingClients, countriesLeft, finishedCountries,
                            finalRankingReadyLatch, queue, rankingList);
                    workerThreads.add(worker);
                    worker.start();
                } catch (SocketTimeoutException e) {
                    log.debug("Socket timeout, waiting for new connections...");
                } catch (IOException e) {
                    log.error(e);
                }
            }
        } catch (IOException e) {
            log.error(e);
        }

        log.info("Cleaning up threads...");
        queue.close();

        workerThreads.forEach(worker -> {
            try {
                worker.join();
            } catch (InterruptedException e) {
                log.error(e);
            }
        });

        Arrays.stream(writerThreads).forEach(writer -> {
            try {
                writer.join();
                log.info("Writer {} finished", writer.getName());
            } catch (InterruptedException e) {
                log.error(e);
            }
        });

        readerExecutor.shutdown();
        try {
            if (!readerExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                readerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            readerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        rankingExecutor.shutdown();
        try {
            if (!rankingExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                rankingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            rankingExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        endTime = System.nanoTime();
        var elapsedTime = (endTime - startTime) / 1e6;

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
        log.info("Server finished processing all data in {} milliseconds.", elapsedTime);
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
