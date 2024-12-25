package ppd.response;

import ppd.utils.CountryMapper;

import java.io.Serializable;

public record ParticipantScore(int id, int country, int score) implements Serializable {
    @Override
    public String toString() {
        return String.format("Participant %d from %s: %d", id, CountryMapper.getCountryName(country), score);
    }
}
