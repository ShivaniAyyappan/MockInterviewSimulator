package mockinterview;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregated performance of one finished interview. */
public final class Summary {
    public final String candidate;
    public final List<InterviewSession.Answer> answers;
    public final double overallPercent;
    public final double technicalPercent;   // -1 when no technical question was asked
    public final double hrPercent;          // -1 when no HR question was asked
    public final String grade;
    public final int timedOut;
    public final int skipped;
    public final double avgSeconds;
    public final String strongestTopic;
    public final String weakestTopic;
    public final Map<String, Double> topicPercent;

    private Summary(String candidate, List<InterviewSession.Answer> answers, double overall, double tech,
                    double hr, String grade, int timedOut, int skipped, double avgSec,
                    String strong, String weak, Map<String, Double> topics) {
        this.candidate = candidate;
        this.answers = answers;
        this.overallPercent = overall;
        this.technicalPercent = tech;
        this.hrPercent = hr;
        this.grade = grade;
        this.timedOut = timedOut;
        this.skipped = skipped;
        this.avgSeconds = avgSec;
        this.strongestTopic = strong;
        this.weakestTopic = weak;
        this.topicPercent = topics;
    }

    public static String gradeFor(double pct) {
        if (pct >= 85) return "Excellent";
        if (pct >= 70) return "Good";
        if (pct >= 50) return "Average";
        return "Needs Improvement";
    }

    public static Summary of(String candidate, List<InterviewSession.Answer> answers) {
        double sum = 0, tSum = 0, hSum = 0;
        int tN = 0, hN = 0, to = 0, sk = 0;
        long ms = 0;
        Map<String, double[]> byTopic = new LinkedHashMap<>();
        for (InterviewSession.Answer a : answers) {
            double s = a.evaluation.total;
            sum += s;
            if (a.question.category == Question.Category.TECHNICAL) { tSum += s; tN++; } else { hSum += s; hN++; }
            if (a.timedOut) to++;
            if (a.skipped) sk++;
            ms += a.elapsedMs;
            double[] t = byTopic.computeIfAbsent(a.question.topic, k -> new double[2]);
            t[0] += s;
            t[1]++;
        }
        int n = answers.size();
        double overall = n == 0 ? 0 : sum / (10.0 * n) * 100;
        double tech = tN == 0 ? -1 : tSum / (10.0 * tN) * 100;
        double hr = hN == 0 ? -1 : hSum / (10.0 * hN) * 100;
        Map<String, Double> topics = new LinkedHashMap<>();
        String strong = "-", weak = "-";
        double best = -1, worst = 101;
        for (Map.Entry<String, double[]> e : byTopic.entrySet()) {
            double p = e.getValue()[0] / (10.0 * e.getValue()[1]) * 100;
            topics.put(e.getKey(), p);
            if (p > best) { best = p; strong = e.getKey(); }
            if (p < worst) { worst = p; weak = e.getKey(); }
        }
        return new Summary(candidate, answers, overall, tech, hr, gradeFor(overall), to, sk,
                n == 0 ? 0 : ms / 1000.0 / n, strong, weak, topics);
    }
}
