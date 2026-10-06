package mockinterview;

import java.util.List;

/** Result of scoring one answer. All scores are on a 0 to 10 scale. */
public final class Evaluation {
    public final double content;      // concept coverage
    public final double clarity;      // length, structure and filler words (0 when not used)
    public final double total;
    public final List<String> matched;
    public final List<String> missed;
    public final boolean stuffing;    // keyword stuffing detected
    public final String feedback;

    public Evaluation(double content, double clarity, double total, List<String> matched,
                      List<String> missed, boolean stuffing, String feedback) {
        this.content = content;
        this.clarity = clarity;
        this.total = total;
        this.matched = matched;
        this.missed = missed;
        this.stuffing = stuffing;
        this.feedback = feedback;
    }
}
