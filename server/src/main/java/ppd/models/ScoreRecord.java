package ppd.models;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class ScoreRecord {
    private int id;
    private int score;
    private int country;
    private ScoreRecord next = null;
    private final ReentrantLock lock = new ReentrantLock();

    public ScoreRecord(int id, int country, int score) {
        this.id = id;
        this.country = country;
        this.score = score;
    }

    public void lock() {
        lock.lock();
    }

    public void unlock() {
        lock.unlock();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScoreRecord that = (ScoreRecord) o;
        return id == that.id && score == that.score && country == that.country;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, score, country);
    }
}
