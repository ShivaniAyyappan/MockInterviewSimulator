package mockinterview;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Loads the predefined question bank and draws interview question sets from it. */
public final class QuestionBank {

    public enum Mode { TECHNICAL, HR, MIXED }

    private final List<Question> all = new ArrayList<>();

    public static QuestionBank load() throws IOException {
        try (InputStream in = QuestionBank.class.getResourceAsStream("/questions.txt")) {
            if (in == null) throw new IOException("questions.txt not found on the classpath");
            return parse(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    public static QuestionBank parse(BufferedReader r) throws IOException {
        QuestionBank b = new QuestionBank();
        String line;
        while ((line = r.readLine()) != null) {
            if (line.isBlank() || line.startsWith("#")) continue;
            b.all.add(Question.parse(line));
        }
        return b;
    }

    public List<Question> all() { return Collections.unmodifiableList(all); }

    public List<Question> byCategory(Question.Category c) {
        List<Question> out = new ArrayList<>();
        for (Question q : all) if (q.category == c) out.add(q);
        return out;
    }

    public Question byId(String id) {
        for (Question q : all) if (q.id.equals(id)) return q;
        return null;
    }

    /**
     * Draws {@code count} distinct questions. MIXED gives about 60% technical questions
     * followed by HR questions, as in a real interview. The same seed gives the same set.
     */
    public List<Question> select(Mode mode, int count, long seed) {
        Random rnd = new Random(seed);
        List<Question> tech = byCategory(Question.Category.TECHNICAL);
        List<Question> hr = byCategory(Question.Category.HR);
        Collections.shuffle(tech, rnd);
        Collections.shuffle(hr, rnd);
        List<Question> out = new ArrayList<>();
        switch (mode) {
            case TECHNICAL -> out.addAll(tech.subList(0, Math.min(count, tech.size())));
            case HR -> out.addAll(hr.subList(0, Math.min(count, hr.size())));
            case MIXED -> {
                int nt = Math.min((int) Math.ceil(count * 0.6), tech.size());
                int nh = Math.min(count - nt, hr.size());
                out.addAll(tech.subList(0, nt));
                out.addAll(hr.subList(0, nh));
            }
        }
        return out;
    }

    /** Baseline draw (iteration 1): picks at random WITH replacement, so repeats are possible. */
    public List<Question> selectNaive(Mode mode, int count, long seed) {
        Random rnd = new Random(seed);
        List<Question> pool = new ArrayList<>();
        if (mode != Mode.HR) pool.addAll(byCategory(Question.Category.TECHNICAL));
        if (mode != Mode.TECHNICAL) pool.addAll(byCategory(Question.Category.HR));
        List<Question> out = new ArrayList<>();
        for (int i = 0; i < count; i++) out.add(pool.get(rnd.nextInt(pool.size())));
        return out;
    }
}
