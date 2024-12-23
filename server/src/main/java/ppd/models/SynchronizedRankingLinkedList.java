package ppd.models;

import lombok.Getter;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Getter
public class SynchronizedRankingLinkedList {
    private final ScoreRecord head = new ScoreRecord(-1, -1, Integer.MAX_VALUE);
    private final ScoreRecord tail = new ScoreRecord(-1, -1, Integer.MIN_VALUE);

    private final Set<Integer> blacklist = new HashSet<>();
    private final Lock blacklistLock = new ReentrantLock();
    private final Lock listLock = new ReentrantLock();

    public SynchronizedRankingLinkedList() {
        head.setNext(tail);
    }

    public void addOrUpdate(int id, int country, int points) {
        if (isBlacklisted(id)) {
            return;
        }

        if (points == -1) {
            removeAndBlacklist(id);
            return;
        }

        listLock.lock();
        try {
            var current = head.getNext();
            var prev = head;
            while (current != tail) {
                if (current.getId() == id) {
                    current.setScore(current.getScore() + points);
                    return;
                }
                prev = current;
                current = current.getNext();
            }

            var newNode = new ScoreRecord(id, country, points);
            newNode.setNext(current);
            prev.setNext(newNode);
        } finally {
            listLock.unlock();
        }
    }

    private void removeAndBlacklist(int id) {
        listLock.lock();

        try {
            var removed = false;
            var current = head.getNext();
            var prev = head;

            while (current != tail && !removed) {
                if (current.getId() == id) {
                    prev.setNext(current.getNext());
                    removed = true;
                }
                prev = current;
                current = current.getNext();
            }

            if (!removed && !isBlacklisted(id)) {
                addToBlacklist(id);
            }
        } finally {
            listLock.unlock();
        }
    }

    private void addToBlacklist(int id) {
        blacklistLock.lock();
        try {
            blacklist.add(id);
        } finally {
            blacklistLock.unlock();
        }
    }

    private boolean isBlacklisted(int id) {
        blacklistLock.lock();
        try {
            return blacklist.contains(id);
        } finally {
            blacklistLock.unlock();
        }
    }

    public List<ScoreRecord> getRanking() {
        List<ScoreRecord> list = new LinkedList<>();
        var current = head.getNext();

        listLock.lock();
        try {
            while (current != tail) {
                list.add(current);
                current = current.getNext();
            }
            list.sort((a, b) -> {
                if (b.getScore() != a.getScore()) {
                    return Integer.compare(b.getScore(), a.getScore());
                } else {
                    return Integer.compare(b.getId(), a.getId());
                }
            });
            return list;
        } finally {
            listLock.unlock();
        }
    }
}
