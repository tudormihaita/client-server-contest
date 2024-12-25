package ppd.request;

import lombok.*;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Request implements Serializable {
    private RequestType type;
    private List<ScoreSubmission> submissions;
    private int country;

    @Override
    public String toString() {
        return "Request{" +
                "type=" + type +
                ", submissions=" + submissions +
                ", country=" + country +
                '}';
    }
}
