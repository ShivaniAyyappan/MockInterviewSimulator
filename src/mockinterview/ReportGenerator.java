package mockinterview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Builds the performance report and stores it, and keeps a CSV history of past attempts. */
public final class ReportGenerator {

    private ReportGenerator() {}

    public static String build(Summary s, LocalDateTime when) {
        StringBuilder b = new StringBuilder();
        String line = "=".repeat(64);
        b.append(line).append('\n');
        b.append("  MOCK INTERVIEW PERFORMANCE REPORT\n");
        b.append(line).append('\n');
        b.append(String.format("Candidate : %s%n", s.candidate));
        b.append(String.format("Date      : %s%n", when.format(DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm"))));
        b.append(String.format("Questions : %d   Timed out: %d   Skipped: %d%n", s.answers.size(), s.timedOut, s.skipped));
        b.append(String.format("Avg time  : %.1f s per question%n%n", s.avgSeconds));
        b.append(String.format("OVERALL SCORE : %.1f%%   GRADE : %s%n", s.overallPercent, s.grade));
        if (s.technicalPercent >= 0) b.append(String.format("  Technical   : %.1f%%%n", s.technicalPercent));
        if (s.hrPercent >= 0) b.append(String.format("  HR          : %.1f%%%n", s.hrPercent));
        b.append('\n').append("TOPIC-WISE PERFORMANCE\n").append("-".repeat(64)).append('\n');
        for (Map.Entry<String, Double> e : s.topicPercent.entrySet()) {
            int bars = (int) Math.round(e.getValue() / 5);
            b.append(String.format("  %-22s %5.1f%%  %s%n", e.getKey(), e.getValue(), "#".repeat(bars)));
        }
        b.append(String.format("%n  Strongest topic : %s%n  Weakest topic   : %s%n", s.strongestTopic, s.weakestTopic));
        b.append('\n').append("QUESTION-WISE FEEDBACK\n").append("-".repeat(64)).append('\n');
        int i = 1;
        for (InterviewSession.Answer a : s.answers) {
            b.append(String.format("Q%d [%s | %s] %s%n", i++, a.question.category, a.question.topic, a.question.text));
            b.append(String.format("   Score: %.1f/10   Time: %.0f s of %d s%s%n", a.evaluation.total,
                    a.elapsedMs / 1000.0, a.question.seconds,
                    a.timedOut ? "   (TIMED OUT)" : a.skipped ? "   (SKIPPED)" : ""));
            b.append("   Feedback: ").append(a.evaluation.feedback).append('\n');
            b.append("   Model answer: ").append(a.question.modelAnswer).append("\n\n");
        }
        b.append("RECOMMENDATION\n").append("-".repeat(64)).append('\n');
        b.append(recommendation(s)).append('\n');
        return b.toString();
    }

    static String recommendation(Summary s) {
        if (s.overallPercent >= 85) return "Interview-ready. Keep practising harder questions and refine delivery.";
        if (s.overallPercent >= 70) return "Good performance. Revise '" + s.weakestTopic + "' and practise with the timer.";
        if (s.overallPercent >= 50) return "Average performance. Focus on '" + s.weakestTopic + "' and answer in full sentences.";
        return "Significant revision needed, starting with '" + s.weakestTopic + "'. Study the model answers above.";
    }

    public static Path save(Summary s, Path dir) throws IOException {
        Files.createDirectories(dir);
        LocalDateTime now = LocalDateTime.now();
        String safe = s.candidate.replaceAll("[^A-Za-z0-9]+", "_");
        Path p = dir.resolve("report_" + safe + "_" + now.format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".txt");
        Files.writeString(p, build(s, now), StandardCharsets.UTF_8);
        appendHistory(s, dir.resolve("history.csv"), now);
        return p;
    }

    public static void appendHistory(Summary s, Path csv, LocalDateTime when) throws IOException {
        boolean fresh = !Files.exists(csv);
        StringBuilder b = new StringBuilder();
        if (fresh) b.append("date,candidate,questions,overall,technical,hr,grade\n");
        b.append(String.format("%s,%s,%d,%.1f,%.1f,%.1f,%s%n", when.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                s.candidate.replace(',', ' '), s.answers.size(), s.overallPercent, s.technicalPercent,
                s.hrPercent, s.grade));
        Files.writeString(csv, b.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
