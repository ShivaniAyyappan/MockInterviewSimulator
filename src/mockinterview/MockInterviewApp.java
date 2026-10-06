package mockinterview;

import javax.swing.*;

/** Entry point of the desktop application. */
public final class MockInterviewApp {
    public static void main(String[] args) throws Exception {
        QuestionBank bank = QuestionBank.load();
        SwingUtilities.invokeLater(() -> new InterviewFrame(bank).setVisible(true));
    }
}
