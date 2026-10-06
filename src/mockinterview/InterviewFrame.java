package mockinterview;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Swing front end: setup screen, timed question screen and result screen. */
public class InterviewFrame extends JFrame {

    static final Color NAVY = new Color(0x1F3A5F);
    static final Color ACCENT = new Color(0x2E7D6B);
    static final Color WARN = new Color(0xC0392B);
    static final Font BASE = new Font("DejaVu Sans", Font.PLAIN, 14);

    private final QuestionBank bank;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    // setup
    final JTextField nameField = new JTextField("Candidate", 18);
    final JComboBox<QuestionBank.Mode> modeBox = new JComboBox<>(QuestionBank.Mode.values());
    final JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(5, 1, 20, 1));

    // interview
    final JLabel progressLabel = new JLabel();
    final JLabel metaLabel = new JLabel();
    final JTextArea questionArea = new JTextArea();
    final JLabel timerLabel = new JLabel("00:00", SwingConstants.RIGHT);
    final JProgressBar timerBar = new JProgressBar(0, 1000);
    final JTextArea answerArea = new JTextArea();
    final JLabel feedbackLabel = new JLabel(" ");
    final JButton submitBtn = new JButton("Submit & Next");
    final JButton skipBtn = new JButton("Skip");

    // result
    final JLabel scoreLabel = new JLabel();
    final JLabel gradeLabel = new JLabel();
    final JLabel splitLabel = new JLabel();
    final DefaultTableModel tableModel = new DefaultTableModel(
            new Object[]{"#", "Category", "Topic", "Score /10", "Time (s)", "Status"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    final JTable resultTable = new JTable(tableModel);
    final JTextArea reportArea = new JTextArea();

    private InterviewSession session;
    private Timer ticker;
    private Summary lastSummary;

    public InterviewFrame(QuestionBank bank) {
        super("Mock Interview Simulator");
        this.bank = bank;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        root.add(buildSetup(), "setup");
        root.add(buildInterview(), "interview");
        root.add(buildResult(), "result");
        setContentPane(root);
        setSize(900, 620);
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------ screens

    private JPanel buildSetup() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(header("Mock Interview Simulator", "Technical and HR practice with timed questions - no AI, no internet"), BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new EmptyBorder(40, 80, 40, 80));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(10, 8, 10, 8);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0; c.gridy = 0; form.add(label("Candidate name"), c);
        c.gridx = 1; form.add(nameField, c);
        c.gridx = 0; c.gridy = 1; form.add(label("Interview type"), c);
        c.gridx = 1; form.add(modeBox, c);
        c.gridx = 0; c.gridy = 2; form.add(label("Number of questions"), c);
        c.gridx = 1; form.add(countSpinner, c);
        JTextArea rules = new JTextArea(
                "Rules\n"
              + "  - Every question has its own countdown (60 to 150 seconds).\n"
              + "  - When time runs out, the answer typed so far is submitted automatically.\n"
              + "  - Answers are scored by keyword rules; write full sentences, not keyword lists.\n"
              + "  - A performance report is generated at the end.");
        styleInfo(rules);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 2; form.add(rules, c);
        JButton start = primary("Start Interview");
        start.addActionListener(e -> startInterview());
        c.gridy = 4; c.anchor = GridBagConstraints.CENTER; form.add(start, c);
        p.add(form, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildInterview() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(header("Interview in progress", ""), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBorder(new EmptyBorder(16, 28, 16, 28));

        JPanel top = new JPanel(new BorderLayout(0, 6));
        JPanel row = new JPanel(new BorderLayout());
        progressLabel.setFont(BASE.deriveFont(Font.BOLD, 15f));
        metaLabel.setFont(BASE.deriveFont(12f));
        metaLabel.setForeground(Color.DARK_GRAY);
        JPanel left = new JPanel(new GridLayout(2, 1));
        left.add(progressLabel);
        left.add(metaLabel);
        timerLabel.setFont(new Font("DejaVu Sans Mono", Font.BOLD, 30));
        row.add(left, BorderLayout.WEST);
        row.add(timerLabel, BorderLayout.EAST);
        timerBar.setStringPainted(false);
        timerBar.setPreferredSize(new Dimension(100, 10));
        top.add(row, BorderLayout.NORTH);
        top.add(timerBar, BorderLayout.SOUTH);

        questionArea.setFont(BASE.deriveFont(Font.BOLD, 18f));
        questionArea.setLineWrap(true);
        questionArea.setWrapStyleWord(true);
        questionArea.setEditable(false);
        questionArea.setOpaque(false);
        questionArea.setBorder(new EmptyBorder(6, 0, 6, 0));

        JPanel mid = new JPanel(new BorderLayout(0, 8));
        mid.add(questionArea, BorderLayout.NORTH);
        answerArea.setFont(BASE);
        answerArea.setLineWrap(true);
        answerArea.setWrapStyleWord(true);
        mid.add(new JScrollPane(answerArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        feedbackLabel.setFont(BASE.deriveFont(12f));
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        skipBtn.addActionListener(e -> finishQuestion(InterviewSession.Source.SKIP));
        submitBtn.addActionListener(e -> finishQuestion(InterviewSession.Source.USER));
        stylePrimary(submitBtn);
        btns.add(skipBtn);
        btns.add(submitBtn);
        bottom.add(feedbackLabel, BorderLayout.WEST);
        bottom.add(btns, BorderLayout.EAST);

        body.add(top, BorderLayout.NORTH);
        body.add(mid, BorderLayout.CENTER);
        body.add(bottom, BorderLayout.SOUTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildResult() {
        JPanel p = new JPanel(new BorderLayout());
        p.add(header("Performance Report", ""), BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setBorder(new EmptyBorder(14, 24, 14, 24));

        JPanel score = new JPanel(new GridLayout(1, 3, 12, 0));
        scoreLabel.setFont(BASE.deriveFont(Font.BOLD, 28f));
        gradeLabel.setFont(BASE.deriveFont(Font.BOLD, 22f));
        splitLabel.setFont(BASE.deriveFont(14f));
        score.add(scoreLabel);
        score.add(gradeLabel);
        score.add(splitLabel);

        resultTable.setRowHeight(24);
        resultTable.setFont(BASE.deriveFont(13f));
        resultTable.getColumnModel().getColumn(0).setMaxWidth(40);
        JScrollPane tablePane = new JScrollPane(resultTable);
        tablePane.setPreferredSize(new Dimension(100, 170));

        reportArea.setEditable(false);
        reportArea.setFont(new Font("DejaVu Sans Mono", Font.PLAIN, 12));
        JScrollPane reportPane = new JScrollPane(reportArea);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton save = primary("Save Report");
        save.addActionListener(e -> saveReport());
        JButton again = new JButton("New Interview");
        again.addActionListener(e -> cards.show(root, "setup"));
        btns.add(again);
        btns.add(save);

        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.add(tablePane, BorderLayout.NORTH);
        center.add(reportPane, BorderLayout.CENTER);
        body.add(score, BorderLayout.NORTH);
        body.add(center, BorderLayout.CENTER);
        body.add(btns, BorderLayout.SOUTH);
        p.add(body, BorderLayout.CENTER);
        return p;
    }

    // ------------------------------------------------------------ behaviour

    public void startInterview() {
        QuestionBank.Mode mode = (QuestionBank.Mode) modeBox.getSelectedItem();
        int n = (Integer) countSpinner.getValue();
        String name = nameField.getText().trim().isEmpty() ? "Candidate" : nameField.getText().trim();
        begin(new InterviewSession(name, bank.select(mode, n, System.nanoTime())));
    }

    /** Starts the interview screen for an already prepared session (also used for screenshots). */
    void begin(InterviewSession s) {
        session = s;
        cards.show(root, "interview");
        showNextQuestion();
        if (ticker != null) ticker.stop();
        ticker = new Timer(250, e -> tick());
        ticker.start();
    }

    private void showNextQuestion() {
        Question q = session.next();
        if (q == null) { showResult(); return; }
        progressLabel.setText("Question " + session.position() + " of " + session.total());
        metaLabel.setText(q.category + "  |  " + q.topic + "  |  Difficulty " + "*".repeat(q.difficulty) + "  |  " + q.seconds + " s limit");
        questionArea.setText(q.text);
        answerArea.setText("");
        answerArea.requestFocusInWindow();
        feedbackLabel.setText(" ");
        tick();
    }

    private void tick() {
        if (session == null || session.current() == null) return;
        long rem = session.remainingMs();
        long total = session.current().seconds * 1000L;
        timerLabel.setText(String.format("%02d:%02d", rem / 60000, (rem / 1000) % 60));
        timerBar.setValue((int) (rem * 1000 / total));
        boolean low = rem <= 10_000;
        timerLabel.setForeground(low ? WARN : NAVY);
        if (rem <= 0) finishQuestion(InterviewSession.Source.TIMER);
    }

    private void finishQuestion(InterviewSession.Source src) {
        if (session == null) return;
        if (!session.submit(answerArea.getText(), src)) return;
        showNextQuestion();
    }

    private void showResult() {
        if (ticker != null) ticker.stop();
        lastSummary = session.summary();
        Summary s = lastSummary;
        scoreLabel.setText(String.format("%.1f%%", s.overallPercent));
        gradeLabel.setText("Grade: " + s.grade);
        splitLabel.setText("<html>Technical: " + pct(s.technicalPercent) + "<br>HR: " + pct(s.hrPercent) + "</html>");
        tableModel.setRowCount(0);
        int i = 1;
        for (InterviewSession.Answer a : s.answers) {
            tableModel.addRow(new Object[]{i++, a.question.category, a.question.topic,
                    String.format("%.1f", a.evaluation.total), String.format("%.0f", a.elapsedMs / 1000.0),
                    a.timedOut ? "Timed out" : a.skipped ? "Skipped" : "Answered"});
        }
        reportArea.setText(ReportGenerator.build(s, java.time.LocalDateTime.now()));
        reportArea.setCaretPosition(0);
        cards.show(root, "result");
    }

    private void saveReport() {
        if (lastSummary == null) return;
        try {
            Path p = ReportGenerator.save(lastSummary, Path.of("reports"));
            JOptionPane.showMessageDialog(this, "Report saved to " + p.toAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save report: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------ small helpers

    static String pct(double v) { return v < 0 ? "-" : String.format("%.1f%%", v); }

    private JPanel header(String title, String sub) {
        JPanel h = new JPanel(new GridLayout(sub.isEmpty() ? 1 : 2, 1));
        h.setBackground(NAVY);
        h.setBorder(new EmptyBorder(14, 24, 14, 24));
        JLabel t = new JLabel(title);
        t.setForeground(Color.WHITE);
        t.setFont(BASE.deriveFont(Font.BOLD, 22f));
        h.add(t);
        if (!sub.isEmpty()) {
            JLabel s = new JLabel(sub);
            s.setForeground(new Color(0xCFD8E3));
            s.setFont(BASE.deriveFont(13f));
            h.add(s);
        }
        return h;
    }

    private JLabel label(String s) { JLabel l = new JLabel(s); l.setFont(BASE); return l; }

    private void styleInfo(JTextArea t) {
        t.setEditable(false);
        t.setOpaque(false);
        t.setFont(BASE.deriveFont(13f));
        t.setBorder(new EmptyBorder(10, 0, 10, 0));
    }

    private JButton primary(String text) { JButton b = new JButton(text); stylePrimary(b); return b; }

    private void stylePrimary(JButton b) {
        b.setBackground(ACCENT);
        b.setForeground(Color.WHITE);
        b.setFont(BASE.deriveFont(Font.BOLD));
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setBorder(new EmptyBorder(8, 18, 8, 18));
    }

    // ------------------------------------------------------------ test hooks (screenshots)

    void showCard(String name) { cards.show(root, name); }
    InterviewSession session() { return session; }
    void stopTicker() { if (ticker != null) ticker.stop(); }
    void showResultNow() { showResult(); }
    void useSession(InterviewSession s) { session = s; }
}
