package mockinterview;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Runs one interview: hands out questions one at a time, enforces the time limit,
 * records each answer exactly once and scores it with the chosen evaluator.
 * The clock is injected so that timing rules can be tested without waiting.
 */
public final class InterviewSession {

    public enum Source { USER, TIMER, SKIP }

    /** The recorded outcome for one question. */
    public static final class Answer {
        public final Question question;
        public final String text;
        public final long elapsedMs;
        public final boolean timedOut;
        public final boolean skipped;
        public final Evaluation evaluation;

        Answer(Question q, String text, long elapsedMs, boolean timedOut, boolean skipped, Evaluation ev) {
            this.question = q;
            this.text = text;
            this.elapsedMs = elapsedMs;
            this.timedOut = timedOut;
            this.skipped = skipped;
            this.evaluation = ev;
        }
    }

    private final String candidate;
    private final List<Question> questions;
    private final Config cfg;
    private final Evaluators.Evaluator evaluator;
    private final LongSupplier clock;

    private final List<Answer> answers = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean gate = new AtomicBoolean();   // used by the atomic version
    private volatile boolean answeredFlag;                    // used by the baseline version
    private int index = -1;
    private long startedAt;

    public InterviewSession(String candidate, List<Question> questions, Config cfg, LongSupplier clock) {
        this.candidate = candidate;
        this.questions = new ArrayList<>(questions);
        this.cfg = cfg;
        this.evaluator = Evaluators.forVersion(cfg.evaluatorVersion);
        this.clock = clock;
    }

    public InterviewSession(String candidate, List<Question> questions) {
        this(candidate, questions, Config.finalConfig(), System::currentTimeMillis);
    }

    public String candidate() { return candidate; }
    public List<Question> questions() { return Collections.unmodifiableList(questions); }
    public int total() { return questions.size(); }
    public int position() { return index + 1; }
    public boolean finished() { return answers.size() >= questions.size(); }
    public List<Answer> answers() { return new ArrayList<>(answers); }

    /** Moves to the next question and starts its timer; returns null when the interview is over. */
    public synchronized Question next() {
        if (index + 1 >= questions.size()) return null;
        index++;
        startedAt = clock.getAsLong();
        gate.set(false);
        answeredFlag = false;
        return questions.get(index);
    }

    public synchronized Question current() { return index >= 0 ? questions.get(index) : null; }

    public long remainingMs() {
        Question q = current();
        if (q == null) return 0;
        return Math.max(0, q.seconds * 1000L - (clock.getAsLong() - startedAt));
    }

    /**
     * Records the answer to the current question. Returns true if this call recorded it and
     * false if the question had already been answered. A manual answer that arrives after
     * the deadline is recorded as a time-out with no credit.
     */
    public boolean submit(String text, Source src) {
        final Question q;
        final long start;
        synchronized (this) {
            q = current();
            start = startedAt;
        }
        if (q == null) return false;
        long limit = q.seconds * 1000L;
        long elapsed = Math.min(clock.getAsLong() - start, src == Source.TIMER ? limit : Long.MAX_VALUE);
        boolean late = elapsed > limit;

        // claim the right to record this question's answer
        if (cfg.atomicSubmit) {
            if (!gate.compareAndSet(false, true)) return false;
        } else {
            if (answeredFlag) return false;
            Thread.yield();              // widens the check-then-act window (baseline defect)
            answeredFlag = true;
        }

        String body = text == null ? "" : text;
        boolean timedOut = src == Source.TIMER;
        if (late && cfg.enforceDeadline && src == Source.USER) {
            body = "";                   // too late: no credit
            timedOut = true;
        }
        boolean skipped = src == Source.SKIP;
        Evaluation ev = skipped ? evaluator.evaluate(q, "") : evaluator.evaluate(q, body);
        answers.add(new Answer(q, body, elapsed, timedOut, skipped, ev));
        return true;
    }

    public Summary summary() { return Summary.of(candidate, answers()); }
}
