package mockinterview;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Rule-based answer evaluators. No machine learning and no network access is used:
 * every score is produced from keyword rules that can be read and tested.
 * Version 1 = baseline, version 2 = refined matching, version 3 = final.
 */
public final class Evaluators {

    private Evaluators() {}

    public interface Evaluator {
        Evaluation evaluate(Question q, String answer);
    }

    public static Evaluator forVersion(int v) {
        return switch (v) {
            case 1 -> new V1();
            case 2 -> new V2();
            default -> new V3();
        };
    }

    // ---------------------------------------------------------------- text helpers

    /** Lower-cases, strips punctuation and applies a light suffix stemmer. */
    static List<String> tokens(String s) {
        String t = s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
        List<String> out = new ArrayList<>();
        if (t.isEmpty()) return out;
        for (String w : t.split(" ")) out.add(stem(w));
        return out;
    }

    static String stem(String w) {
        if (w.length() > 4 && w.endsWith("ies")) return w.substring(0, w.length() - 3) + "y";
        if (w.length() > 5 && w.endsWith("ing")) return w.substring(0, w.length() - 3);
        if (w.length() > 4 && w.endsWith("ed")) return w.substring(0, w.length() - 2);
        if (w.length() > 4 && w.endsWith("es")) return w.substring(0, w.length() - 2);
        if (w.length() > 3 && w.endsWith("s") && !w.endsWith("ss")) return w.substring(0, w.length() - 1);
        return w;
    }

    static boolean containsPhrase(List<String> toks, List<String> phrase) {
        if (phrase.isEmpty()) return false;
        for (int i = 0; i + phrase.size() <= toks.size(); i++) {
            boolean ok = true;
            for (int j = 0; j < phrase.size(); j++) {
                if (!toks.get(i + j).equals(phrase.get(j))) { ok = false; break; }
            }
            if (ok) return true;
        }
        return false;
    }

    private static String label(List<String> group) { return group.get(0); }

    private static String feedback(List<String> matched, List<String> missed, double total) {
        StringBuilder sb = new StringBuilder();
        if (matched.isEmpty()) sb.append("None of the expected concepts were mentioned. ");
        else sb.append("Covered: ").append(String.join(", ", matched)).append(". ");
        if (!missed.isEmpty()) sb.append("Missing: ").append(String.join(", ", missed)).append(". ");
        if (total >= 8.5) sb.append("Excellent answer.");
        else if (total >= 7) sb.append("Good answer.");
        else if (total >= 4) sb.append("Partly correct - add the missing points.");
        else sb.append("Needs significant improvement.");
        return sb.toString();
    }

    // ---------------------------------------------------------------- version 1

    /** Baseline: raw, case-sensitive substring test of the FIRST synonym of every group. */
    static final class V1 implements Evaluator {
        public Evaluation evaluate(Question q, String answer) {
            List<String> matched = new ArrayList<>(), missed = new ArrayList<>();
            for (List<String> g : q.groups) {
                if (answer != null && answer.contains(g.get(0))) matched.add(label(g));
                else missed.add(label(g));
            }
            double c = 10.0 * matched.size() / q.groups.size();
            return new Evaluation(c, 0, c, matched, missed, false, feedback(matched, missed, c));
        }
    }

    // ---------------------------------------------------------------- version 2

    /** Refined: normalised, stemmed, whole-phrase matching of every synonym. */
    static class V2 implements Evaluator {
        List<String>[] match(Question q, String answer) {
            List<String> toks = tokens(answer == null ? "" : answer);
            @SuppressWarnings("unchecked")
            List<String>[] r = new List[]{new ArrayList<String>(), new ArrayList<String>()};
            for (List<String> g : q.groups) {
                boolean hit = false;
                for (String syn : g) {
                    if (containsPhrase(toks, tokens(syn))) { hit = true; break; }
                }
                (hit ? r[0] : r[1]).add(label(g));
            }
            return r;
        }

        public Evaluation evaluate(Question q, String answer) {
            List<String>[] m = match(q, answer);
            double c = 10.0 * m[0].size() / q.groups.size();
            return new Evaluation(c, 0, c, m[0], m[1], false, feedback(m[0], m[1], c));
        }
    }

    // ---------------------------------------------------------------- version 3

    /** Final: V2 matching + clarity score + keyword-stuffing and too-short caps. */
    static final class V3 extends V2 {
        private static final Set<String> STOP = new HashSet<>(Arrays.asList(
                "a", "an", "the", "is", "are", "was", "were", "be", "it", "its", "this", "that", "of", "and",
                "or", "to", "in", "on", "for", "with", "as", "by", "from", "can", "which", "when", "where",
                "than", "then", "have", "ha", "do", "doe", "i", "my", "we", "our", "so", "if", "not", "each",
                "at", "but", "they", "them", "there", "also", "use", "used", "one", "all", "any"));
        private static final Set<String> FILLER = new HashSet<>(Arrays.asList(
                "um", "uh", "basically", "actually", "literally", "like", "stuff", "things", "kind"));

        @Override public Evaluation evaluate(Question q, String answer) {
            String text = answer == null ? "" : answer.trim();
            List<String>[] m = match(q, text);
            double content = 10.0 * m[0].size() / q.groups.size();
            List<String> toks = tokens(text);
            int words = toks.size();
            if (words == 0) {
                return new Evaluation(0, 0, 0, m[0], m[1], false, "No answer was given.");
            }

            int stop = 0, filler = 0;
            for (String t : toks) {
                if (STOP.contains(t)) stop++;
                if (FILLER.contains(t)) filler++;
            }
            double stopRatio = (double) stop / words;
            double fillerRatio = (double) filler / words;
            int sentences = Math.max(1, text.split("[.!?]+\\s*").length);

            int minWords = q.category == Question.Category.HR ? 30 : 15;
            double length = Math.min(1.0, words / (double) minWords);
            double structure = Math.min(1.0, sentences / 2.0);
            double filler01 = 1.0 - Math.min(1.0, fillerRatio * 6);
            double clarity = 10.0 * (0.5 * length + 0.3 * structure + 0.2 * filler01);

            double wc = q.category == Question.Category.HR ? 0.7 : 0.9;
            double total = wc * content + (1 - wc) * clarity;

            boolean stuffing = !m[0].isEmpty() && (stopRatio < 0.15 || words < 8);
            String note = "";
            if (stuffing) {
                total = Math.min(total, 3.0);
                note = " Keyword stuffing detected: write full sentences that explain each idea.";
            } else if (words < 5) {
                total = Math.min(total, 2.5);
            }
            total = Math.max(0, Math.min(10, total));
            return new Evaluation(content, clarity, total, m[0], m[1], stuffing,
                    feedback(m[0], m[1], total) + note);
        }
    }
}
