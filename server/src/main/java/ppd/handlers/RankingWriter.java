package ppd.handlers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ppd.models.ScoreProcessingQueue;
import ppd.models.ScoreRecord;
import ppd.models.SynchronizedRankingLinkedList;


public class RankingWriter extends Thread {
    private final ScoreProcessingQueue queue;
    private final SynchronizedRankingLinkedList rankingList;
    private final ScoreRecord NULL_RECORD = new ScoreRecord(-1, -1, -1);

    private static final Logger log = LogManager.getLogger(RankingWriter.class);

    public RankingWriter(ScoreProcessingQueue queue, SynchronizedRankingLinkedList rankingList) {
        this.queue = queue;
        this.rankingList = rankingList;
    }

    @Override
    public void run() {
        try {
            while (true) {
                var record = queue.dequeue();
                if (record.equals(NULL_RECORD)) {
                    log.info("No more records to process, writer {} finished", Thread.currentThread().getName());
                    break;
                }
                rankingList.addOrUpdate(record.getId(), record.getCountry(), record.getScore());
            }
        } catch (InterruptedException e) {
            log.error(e);
        }
    }
}
