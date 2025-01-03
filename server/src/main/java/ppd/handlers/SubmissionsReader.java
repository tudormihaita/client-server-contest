package ppd.handlers;

import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppd.models.ScoreProcessingQueue;
import ppd.request.ScoreSubmission;

import java.util.List;


@AllArgsConstructor
public class SubmissionsReader implements Runnable {
    private final List<ScoreSubmission> submissions;
    private final int country;
    private final ScoreProcessingQueue queue;

    private static final Logger log = LogManager.getLogger(SubmissionsReader.class);

    @Override
    public void run() {
        try {
            processSubmissions();
        } catch (InterruptedException e) {
            log.error(e);
        }
    }

    private void processSubmissions() throws InterruptedException {
        for (var submission : submissions) {
            var id = submission.id();
            var points = submission.score();
            queue.enqueue(id, country, points);
        }
    }
}
