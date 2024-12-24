package ppd.request;

import java.io.Serializable;

public record ScoreSubmission(int id, int score) implements Serializable {}
