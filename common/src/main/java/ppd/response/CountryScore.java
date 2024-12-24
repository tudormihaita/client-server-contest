package ppd.response;

import java.io.Serializable;

public record CountryScore(int country, int totalScore) implements Serializable {}
