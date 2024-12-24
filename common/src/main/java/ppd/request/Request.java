package ppd.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Request implements Serializable {
    private RequestType type;
    private List<ScoreSubmission> submissions;
    private int country;
}
