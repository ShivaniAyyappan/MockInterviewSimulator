package mockinterview;

import java.util.ArrayList;
import java.util.List;

/** One entry of the question bank: text, rule data used for evaluation and a time limit. */
public final class Question {

    public enum Category { TECHNICAL, HR }

    public final String id;
    public final Category category;
    public final String topic;
    public final int difficulty;      // 1 = easy ... 3 = hard
    public final int seconds;         // time limit for this question
    public final String text;
    public final List<List<String>> groups; // each group = synonyms of one expected concept
    public final String modelAnswer;

    public Question(String id, Category category, String topic, int difficulty, int seconds,
                    String text, List<List<String>> groups, String modelAnswer) {
        this.id = id;
        this.category = category;
        this.topic = topic;
        this.difficulty = difficulty;
        this.seconds = seconds;
        this.text = text;
        this.groups = groups;
        this.modelAnswer = modelAnswer;
    }

    /** Parses one pipe-separated line of questions.txt. */
    public static Question parse(String line) {
        String[] p = line.split("\\|", 8);
        if (p.length != 8) throw new IllegalArgumentException("Bad question line: " + line);
        List<List<String>> groups = new ArrayList<>();
        for (String g : p[6].split(";")) {
            List<String> syn = new ArrayList<>();
            for (String s : g.split("/")) {
                if (!s.isBlank()) syn.add(s.trim().toLowerCase());
            }
            if (!syn.isEmpty()) groups.add(syn);
        }
        return new Question(p[0].trim(), Category.valueOf(p[1].trim()), p[2].trim(),
                Integer.parseInt(p[3].trim()), Integer.parseInt(p[4].trim()),
                p[5].trim(), groups, p[7].trim());
    }

    @Override public String toString() { return id + " [" + category + "/" + topic + "] " + text; }
}
