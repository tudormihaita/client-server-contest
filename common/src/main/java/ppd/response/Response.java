package ppd.response;

import lombok.*;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Response implements Serializable {
    private ResponseType type;
    private String message;
    private List<CountryScore> countryRanking;
    private List<ParticipantScore> participantRanking;

    @Override
    public String toString() {
        return "Response{" +
                "type=" + type +
                ", message='" + message + '\'' +
                ", countryRanking=" + countryRanking +
                ", participantRanking=" + participantRanking +
                '}';
    }
}
