package mockinterview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;

/** Produces the figures quoted in Chapter 6 of the report. */
public final class Experiment {

    record Labelled(String qid, String label, String answer) {}

    static List<Labelled> loadLabelled() throws IOException {
        List<Labelled> out = new ArrayList<>();
        try (InputStream in = Experiment.class.getResourceAsStream("/labelled.txt")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", 3);
                out.add(new Labelled(p[0].trim(), p[1].trim(), p[2].trim()));
            }
        }
        return out;
    }

    static String band(double s) { return s >= 7 ? "HIGH" : s >= 4 ? "MID" : "LOW"; }
    static String expected(String label) { return label.equals("GOOD") ? "HIGH" : label.equals("PARTIAL") ? "MID" : "LOW"; }

    public static void main(String[] args) throws Exception {
        QuestionBank bank = QuestionBank.load();
        List<Labelled> data = loadLabelled();
        System.out.println("labelled answers: " + data.size());
        System.out.println("questions: " + bank.all().size() + " (tech " + bank.byCategory(Question.Category.TECHNICAL).size()
                + ", hr " + bank.byCategory(Question.Category.HR).size() + ")");

        for (int v = 1; v <= 3; v++) {
            Evaluators.Evaluator ev = Evaluators.forVersion(v);
            int agree = 0, stuffedAccepted = 0, goodMissed = 0;
            Map<String, int[]> perLabel = new TreeMap<>();
            double[] sumByLabel = new double[4];
            String[] labels = {"GOOD", "PARTIAL", "POOR", "STUFFED"};
            int[] cnt = new int[4];
            for (Labelled l : data) {
                double sc = ev.evaluate(bank.byId(l.qid()), l.answer()).total;
                boolean ok = band(sc).equals(expected(l.label()));
                if (ok) agree++;
                if (l.label().equals("STUFFED") && sc >= 4) stuffedAccepted++;
                if (l.label().equals("GOOD") && sc < 7) goodMissed++;
                int li = Arrays.asList(labels).indexOf(l.label());
                sumByLabel[li] += sc;
                cnt[li]++;
                int[] pl = perLabel.computeIfAbsent(l.label(), k -> new int[2]);
                pl[1]++;
                if (ok) pl[0]++;
            }
            System.out.printf("V%d: band agreement %d/%d = %.1f%% | stuffed accepted(>=4): %d | good answers below 7: %d%n",
                    v, agree, data.size(), 100.0 * agree / data.size(), stuffedAccepted, goodMissed);
            for (Map.Entry<String, int[]> e : perLabel.entrySet())
                System.out.printf("     %-8s %d/%d%n", e.getKey(), e.getValue()[0], e.getValue()[1]);
            for (int i = 0; i < 4; i++) System.out.printf("     mean score %-8s %.2f%n", labels[i], sumByLabel[i] / cnt[i]);
        }

        // concurrency: timer and user submit at the same instant, 30 x 200 races per configuration
        for (int it = 1; it <= 3; it++) {
            Config cfg = Config.iteration(it);
            int dup = 0, worst = 0, races = 0;
            for (int round = 0; round < 30; round++) {
                int roundDup = 0;
                for (int k = 0; k < 200; k++) {
                    InterviewSession s = new InterviewSession("t", List.of(bank.byId("T12")), cfg, System::currentTimeMillis);
                    s.next();
                    CountDownLatch go = new CountDownLatch(1);
                    Thread a = new Thread(() -> { await(go); s.submit("sorted middle half", InterviewSession.Source.USER); });
                    Thread b = new Thread(() -> { await(go); s.submit("sorted", InterviewSession.Source.TIMER); });
                    a.start(); b.start(); go.countDown(); a.join(); b.join();
                    races++;
                    if (s.answers().size() > 1) roundDup++;
                }
                dup += roundDup;
                worst = Math.max(worst, roundDup);
            }
            System.out.printf("race iter%d: %d double-recorded in %d races (worst round %d / 200)%n", it, dup, races, worst);
        }

        // selection repeats over 200 seeds of 10 questions
        int repN = 0, repU = 0;
        for (long seed = 1; seed <= 200; seed++) {
            if (new HashSet<>(TestRunner.ids(bank.selectNaive(QuestionBank.Mode.MIXED, 10, seed))).size() < 10) repN++;
            if (new HashSet<>(TestRunner.ids(bank.select(QuestionBank.Mode.MIXED, 10, seed))).size() < 10) repU++;
        }
        System.out.printf("selection with repeats: naive %d/200 sets, unique %d/200 sets%n", repN, repU);

        // latency of evaluating one answer (final evaluator), and report generation
        Evaluators.Evaluator ev = Evaluators.forVersion(3);
        Question q = bank.byId("T06");
        String ans = data.stream().filter(l -> l.qid().equals("T06") && l.label().equals("GOOD")).findFirst().get().answer();
        for (int i = 0; i < 5000; i++) ev.evaluate(q, ans);          // warm-up
        int N = 20000;
        long t0 = System.nanoTime();
        for (int i = 0; i < N; i++) ev.evaluate(q, ans);
        double us = (System.nanoTime() - t0) / 1000.0 / N;
        System.out.printf("evaluation latency V3: %.1f microseconds per answer%n", us);

        List<Question> qs = bank.select(QuestionBank.Mode.MIXED, 10, 3);
        InterviewSession s = new InterviewSession("Bench", qs);
        for (Question x : qs) { s.next(); s.submit("A short test answer about this topic in two sentences. It covers little.", InterviewSession.Source.USER); }
        Summary sm = s.summary();
        for (int i = 0; i < 2000; i++) ReportGenerator.build(sm, LocalDateTime.now());
        t0 = System.nanoTime();
        for (int i = 0; i < 5000; i++) ReportGenerator.build(sm, LocalDateTime.now());
        System.out.printf("report generation (10 questions): %.1f microseconds%n", (System.nanoTime() - t0) / 1000.0 / 5000);

        // whole-suite worst of 30 for each iteration
        for (int it = 1; it <= 3; it++) {
            int worstPass = 99;
            for (int run = 0; run < 30; run++) {
                int p = 0;
                for (TestRunner.Result r : TestRunner.runSuite(Config.iteration(it))) if (r.pass()) p++;
                worstPass = Math.min(worstPass, p);
            }
            System.out.printf("suite iter%d: worst of 30 runs = %d / 13%n", it, worstPass);
        }
    }

    static void await(CountDownLatch l) { try { l.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
}
