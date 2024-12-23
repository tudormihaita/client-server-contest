package ppd;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppd.handlers.ContestReader;
import ppd.handlers.ContestWorker;
import ppd.models.ScoreProcessingQueue;
import ppd.models.SynchronizedRankingLinkedList;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static ppd.utils.ContestConfig.*;

public class ContestServer {
    private static final AtomicInteger readersLeft = new AtomicInteger(COUNTRIES * PROBLEMS);
    private static final ExecutorService executor = Executors.newFixedThreadPool(READERS);

    private static final ScoreProcessingQueue queue = new ScoreProcessingQueue(MAX_QUEUE_CAPACITY, readersLeft);
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
            // TODO: change condition for automatic server shutdown
            var running = true;
            while (running) {
                try {
                    final var clientSocket = serverSocket.accept();
                    log.info("Client connected, starting reader to process request...");
                    executor.submit(new ContestReader(clientSocket));
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

        // write final results
    }
}
