package ppd.models;

import lombok.Getter;
import ppd.response.CountryScore;
import ppd.response.ParticipantScore;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Getter
public class SynchronizedRankingLinkedList {
    private final ScoreRecord head = new ScoreRecord(-1, -1, Integer.MAX_VALUE);
    private final ScoreRecord tail = new ScoreRecord(-1, -1, Integer.MIN_VALUE);

    private final Set<Integer> blacklist = new HashSet<>();
    private final Lock blacklistLock = new ReentrantLock();

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

        head.lock();
        head.getNext().lock();
        var prev = head;
        var current = head.getNext();

        try {
            while (current != tail) {
                if (current.getId() == id) {
                    current.setScore(current.getScore() + points);
                    return;
                }
                prev.unlock();
                prev = current;
                current = current.getNext();
                current.lock();
            }

            if (isBlacklisted(id)) {
                return;
            }

            var newNode = new ScoreRecord(id, country, points);
            newNode.setNext(current);
            prev.setNext(newNode);
        } finally {
            prev.unlock();
            current.unlock();
        }
    }

    private void removeAndBlacklist(int id) {
        var removed = false;

        head.lock();
        head.getNext().lock();
        var prev = head;
        var current = head.getNext();

        try {
            while (current != tail && !removed) {
                try {
                    if (current.getId() == id) {
                        prev.setNext(current.getNext());
                        removed = true;
                    }
                } finally {
                    prev.unlock();
                    prev = current;
                    current = current.getNext();
                    current.lock();
                }
            }

            if (!removed || !isBlacklisted(id)) {
                addToBlacklist(id);
            }
        } finally {
            prev.unlock();
            current.unlock();
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

    public List<ParticipantScore> getParticipantRanking() {
        List<ParticipantScore> list = new LinkedList<>();

        var current = head.getNext();
        current.lock();
        try {
            while (current != tail) {
                list.add(new ParticipantScore(current.getId(), current.getCountry(), current.getScore()));
                var next = current.getNext();
                next.lock();
                current.unlock();
                current = next;
            }
        } finally {
            current.unlock();
        }

        list.sort((a, b) -> {
            if (b.score() != a.score()) {
                return Integer.compare(b.score(), a.score());
            } else {
                return Integer.compare(b.id(), a.id());
            }
        });
        return list;
    }

    public List<CountryScore> getCountryRanking() {
        List<CountryScore> list = new LinkedList<>();
        Map<Integer, Integer> countryScores = new HashMap<>();

        var current = head.getNext();
        current.lock();
        try {
            while (current != tail) {
                var country = current.getCountry();
                var score = current.getScore();
                countryScores.merge(country, score, Integer::sum);

                var next = current.getNext();
                next.lock();
                current.unlock();
                current = next;
            }
        } finally {
            current.unlock();
        }

        countryScores.forEach((country, score) -> list.add(new CountryScore(country, score)));
        list.sort((a, b) -> {
            if (b.totalScore() != a.totalScore()) {
                return Integer.compare(b.totalScore(), a.totalScore());
            } else {
                return Integer.compare(b.country(), a.country());
            }
        });
        return list;
    }
}
