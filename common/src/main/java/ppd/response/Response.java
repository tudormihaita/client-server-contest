package ppd.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Response implements Serializable {
    private ResponseType type;
    private String message;
    private List<CountryScore> countryRanking;
    private List<ParticipantScore> participantRanking;
}
