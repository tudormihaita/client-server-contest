package ppd.response;

import java.io.Serializable;

public record ParticipantScore(int id, int country, int score) implements Serializable {}
