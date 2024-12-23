package ppd.models;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ScoreProcessingQueue {
    private final int capacity;
    private final AtomicInteger readersLeft;
    private final ScoreRecord NULL_RECORD = new ScoreRecord(-1, -1, -1);

    private final Lock queueLock = new ReentrantLock();
    private final Condition notFull = queueLock.newCondition();
    private final Condition notEmpty = queueLock.newCondition();

    private final Queue<ScoreRecord> queue = new LinkedList<>();

    public ScoreProcessingQueue(int capacity, AtomicInteger readers) {
        this.capacity = capacity;
        this.readersLeft = readers;
    }

    public void enqueue(int id, int country, int points) throws InterruptedException {
        var record = new ScoreRecord(id, country, points);
        queueLock.lock();
        try {
            while (queue.size() == capacity && readersLeft.get() > 0) {
                notFull.await();
            }

            queue.add(record);
            notEmpty.signalAll();
        } finally {
            queueLock.unlock();
        }
    }

    public ScoreRecord dequeue() throws InterruptedException {
        queueLock.lock();
        try {
            while (queue.isEmpty() && readersLeft.get() > 0) {
                notEmpty.await();
            }

            if (queue.isEmpty() && readersLeft.get() == 0) {
                return NULL_RECORD;
            }

            var record = queue.poll();
            notFull.signalAll();
            return record;
        } finally {
            queueLock.unlock();
        }
    }

    public void close() {
        queueLock.lock();
        try {
            notEmpty.signalAll();
        } finally {
            queueLock.unlock();
        }
    }
}
