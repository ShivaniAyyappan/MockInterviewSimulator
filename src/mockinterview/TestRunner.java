package mockinterview;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Plain-Java test runner (no framework). Runs the 13 tests against one iteration config.
 * Usage: java mockinterview.TestRunner [iteration 1-3] [verbose]
 */
public final class TestRunner {

    static QuestionBank bank;
    static Config cfg;

    record Result(String id, String name, boolean pass) {}

    // a controllable clock so deadlines can be tested without waiting
    static final class FakeClock implements java.util.function.LongSupplier {
        final AtomicLong t = new AtomicLong(1_000_000);
        public long getAsLong() { return t.get(); }
        void advance(long ms) { t.addAndGet(ms); }
    }

    static final String GOOD_T12 = "Binary search works only on a sorted array. You compare the target with the middle "
            + "element, discard the half that cannot contain it and repeat, so it runs in O(log n) time.";

    public static List<Result> runSuite(Config c) throws Exception {
        cfg = c;
        bank = QuestionBank.load();
        List<Result> r = new ArrayList<>();
        r.add(t("T01", "Question bank loads at least 30 questions", () -> bank.all().size() >= 30));
        r.add(t("T02", "Bank has both Technical and HR questions", () ->
                bank.byCategory(Question.Category.TECHNICAL).size() >= 20 && bank.byCategory(Question.Category.HR).size() >= 10));
        r.add(t("T03", "Selection returns distinct questions", TestRunner::distinct));
        r.add(t("T04", "Same seed gives the same question set", () ->
                ids(bank.select(QuestionBank.Mode.MIXED, 8, 42)).equals(ids(bank.select(QuestionBank.Mode.MIXED, 8, 42)))));
        r.add(t("T05", "Paraphrased correct answer scores >= 7", () -> eval("T12", GOOD_T12).total >= 7.0));
        r.add(t("T06", "Empty answer scores exactly 0", () -> eval("T12", "").total == 0.0));
        r.add(t("T07", "Irrelevant answer scores below 4", () ->
                eval("T12", "Searching is an important operation in programming and we do it all the time in our programs.").total < 4.0));
        r.add(t("T08", "Keyword-stuffed answer scores below 4", () ->
                eval("T12", "sorted middle half O(log n) compare target").total < 4.0));
        r.add(t("T09", "Matching ignores case and punctuation", () ->
                eval("T11", "A STACK is LIFO (last in, first out) with PUSH and POP; a queue is FIFO.").content
                        >= eval("T11", "a stack is lifo last in first out with push and pop a queue is fifo").content - 1e-9
                        && eval("T11", "A STACK is LIFO (last in, first out) with PUSH and POP; a queue is FIFO.").content >= 6.0));
        r.add(t("T10", "Answer after the deadline gets no credit", TestRunner::lateAnswer));
        r.add(t("T11", "Overall percentage and grade are computed correctly", TestRunner::summaryMath));
        r.add(t("T12", "Report lists every question and the totals", TestRunner::reportContent));
        r.add(t("T13", "Timer and user submitting together record one answer (30 x 200 races)", TestRunner::raceExactlyOnce));
        return r;
    }

    // ------------------------------------------------------------ the tests

    static boolean distinct() {
        for (long seed = 1; seed <= 200; seed++) {
            List<Question> qs = cfg.uniqueSelection ? bank.select(QuestionBank.Mode.MIXED, 10, seed)
                                                    : bank.selectNaive(QuestionBank.Mode.MIXED, 10, seed);
            if (new HashSet<>(ids(qs)).size() != qs.size()) return false;
        }
        return true;
    }

    static boolean lateAnswer() {
        FakeClock clock = new FakeClock();
        Question q = bank.byId("T12");
        InterviewSession s = new InterviewSession("t", List.of(q), cfg, clock);
        s.next();
        clock.advance(q.seconds * 1000L + 5_000);          // 5 s after the deadline
        s.submit(GOOD_T12, InterviewSession.Source.USER);
        InterviewSession.Answer a = s.answers().get(0);
        return a.timedOut && a.evaluation.total == 0.0;
    }

    static boolean summaryMath() {
        FakeClock clock = new FakeClock();
        List<Question> qs = List.of(bank.byId("T12"), bank.byId("H01"));
        InterviewSession s = new InterviewSession("t", qs, cfg, clock);
        s.next(); s.submit("", InterviewSession.Source.SKIP);   // 0 / 10
        s.next(); s.submit("", InterviewSession.Source.SKIP);   // 0 / 10
        Summary z = s.summary();
        if (z.overallPercent != 0.0 || !z.grade.equals("Needs Improvement") || z.skipped != 2) return false;
        return Summary.gradeFor(85).equals("Excellent") && Summary.gradeFor(70).equals("Good")
                && Summary.gradeFor(50).equals("Average") && Summary.gradeFor(49.9).equals("Needs Improvement");
    }

    static boolean reportContent() {
        FakeClock clock = new FakeClock();
        List<Question> qs = bank.select(QuestionBank.Mode.MIXED, 5, 7);
        InterviewSession s = new InterviewSession("Asha", qs, cfg, clock);
        for (int i = 0; i < qs.size(); i++) {
            s.next();
            clock.advance(20_000);
            s.submit("I am not sure about this.", InterviewSession.Source.USER);
        }
        String rep = ReportGenerator.build(s.summary(), LocalDateTime.of(2026, 10, 6, 9, 0));
        for (Question q : qs) if (!rep.contains(q.text)) return false;
        return rep.contains("OVERALL SCORE") && rep.contains("Asha") && rep.contains("GRADE");
    }

    /** Timer and user both try to answer the same question at the same instant. */
    static boolean raceExactlyOnce() {
        for (int round = 0; round < 30; round++) {
            for (int k = 0; k < 200; k++) {
                FakeClock clock = new FakeClock();
                InterviewSession s = new InterviewSession("t", List.of(bank.byId("T12")), cfg, clock);
                s.next();
                CountDownLatch go = new CountDownLatch(1);
                Thread a = new Thread(() -> { await(go); s.submit("sorted middle half", InterviewSession.Source.USER); });
                Thread b = new Thread(() -> { await(go); s.submit("sorted", InterviewSession.Source.TIMER); });
                a.start(); b.start();
                go.countDown();
                join(a); join(b);
                if (s.answers().size() != 1) return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------ helpers

    static Evaluation eval(String qid, String ans) {
        return Evaluators.forVersion(cfg.evaluatorVersion).evaluate(bank.byId(qid), ans);
    }

    static List<String> ids(List<Question> qs) {
        List<String> out = new ArrayList<>();
        for (Question q : qs) out.add(q.id);
        return out;
    }

    static void await(CountDownLatch l) { try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
    static void join(Thread t) { try { t.join(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }

    static Result t(String id, String name, BooleanSupplier body) {
        boolean ok;
        try { ok = body.getAsBoolean(); } catch (Throwable e) { ok = false; }
        return new Result(id, name, ok);
    }

    // ------------------------------------------------------------ main

    public static void main(String[] args) throws Exception {
        int it = args.length > 0 ? Integer.parseInt(args[0]) : 3;
        boolean verbose = args.length > 1;
        long t0 = System.nanoTime();
        List<Result> res = runSuite(Config.iteration(it));
        int pass = 0;
        System.out.println("MockInterview test run - iteration " + it + " configuration");
        System.out.println("-".repeat(70));
        for (Result r : res) {
            if (r.pass()) pass++;
            System.out.printf("%s  %-4s %s%n", r.pass() ? "[PASS]" : "[FAIL]", r.id(), r.name());
        }
        System.out.println("-".repeat(70));
        System.out.printf("%d / %d tests passed   (%.1f s)%n", pass, res.size(), (System.nanoTime() - t0) / 1e9);
        if (verbose) System.out.println(pass == res.size() ? "BUILD PASSING" : "BUILD FAILING");
    }
}
