package ppd.response;

import ppd.utils.CountryMapper;

import java.io.Serializable;

public record CountryScore(int country, int totalScore) implements Serializable {
    @Override
    public String toString() {
        return String.format("Country %s: %d", CountryMapper.getCountryName(country), totalScore);
    }
}
