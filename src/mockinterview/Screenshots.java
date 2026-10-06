package mockinterview;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Renders the real application screens to PNG files for the report (needs a display, e.g. Xvfb). */
public final class Screenshots {

    static void shot(JFrame f, String path) throws Exception {
        BufferedImage img = new BufferedImage(f.getContentPane().getWidth(), f.getContentPane().getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        f.getContentPane().paint(g);
        g.dispose();
        ImageIO.write(img, "png", new File(path));
    }

    public static void main(String[] args) throws Exception {
        String dir = args[0];
        QuestionBank bank = QuestionBank.load();
        for (UIManager.LookAndFeelInfo i : UIManager.getInstalledLookAndFeels())
            if (i.getName().equals("Nimbus")) UIManager.setLookAndFeel(i.getClassName());
        final InterviewFrame[] fr = new InterviewFrame[1];
        SwingUtilities.invokeAndWait(() -> { fr[0] = new InterviewFrame(bank); fr[0].setVisible(true); });
        InterviewFrame f = fr[0];
        f.setSize(900, 551); Thread.sleep(600);

        // 1) setup screen
        SwingUtilities.invokeAndWait(() -> { f.nameField.setText("Shivani Ayyappan"); f.countSpinner.setValue(5); f.modeBox.setSelectedItem(QuestionBank.Mode.MIXED); });
        Thread.sleep(300);
        SwingUtilities.invokeAndWait(() -> { try { shot(f, dir + "/ui_setup.png"); } catch (Exception e) { throw new RuntimeException(e); } });

        // 2) interview screen with a partly typed answer, 72 s left
        final AtomicLong now = new AtomicLong(5_000_000);
        List<Question> qs = List.of(bank.byId("T06"), bank.byId("T18"), bank.byId("T09"), bank.byId("H04"), bank.byId("H07"));
        InterviewSession live = new InterviewSession("Shivani Ayyappan", qs, Config.finalConfig(), now::get);
        SwingUtilities.invokeAndWait(() -> f.begin(live));
        now.addAndGet(48_000);
        SwingUtilities.invokeAndWait(() -> f.answerArea.setText(
                "A HashMap calls hashCode() on the key and uses it to pick a bucket in an internal array. "
              + "If two keys land in the same bucket there is a collision, which is handled by chaining the entries in a linked list. "));
        Thread.sleep(700);
        SwingUtilities.invokeAndWait(() -> { try { shot(f, dir + "/ui_interview.png"); } catch (Exception e) { throw new RuntimeException(e); } });
        SwingUtilities.invokeAndWait(f::stopTicker);

        // 3) result screen from a complete simulated attempt
        final AtomicLong c2 = new AtomicLong(9_000_000);
        InterviewSession done = new InterviewSession("Shivani Ayyappan", qs, Config.finalConfig(), c2::get);
        String[] ans = {
            "A HashMap calls hashCode on the key and uses it to pick a bucket in an array. A collision is handled by chaining entries in a linked list, and equals is used to find the right key. When the load factor is crossed the table is resized and rehashed.",
            "Atomicity means a transaction happens fully or not at all. Isolation means concurrent transactions do not interfere, and durability means committed data is permanent.",
            "Several threads using the same data can corrupt it, so the synchronized keyword gives a lock so one thread runs the code at a time.",
            "In our final year project two teammates disagreed about the design. I talked to both of them, we discussed the options and I suggested testing both quickly, and the result was that we delivered on time. I learned that listening first helps.",
            "I plan my work, stay calm and focused, and break the work into smaller tasks."
        };
        int[] secs = {71, 58, 120, 112, 64};
        for (int i = 0; i < qs.size(); i++) {
            done.next();
            c2.addAndGet(secs[i] * 1000L);
            if (i == 2) { done.submit(ans[i], InterviewSession.Source.TIMER); } else done.submit(ans[i], InterviewSession.Source.USER);
        }
        SwingUtilities.invokeAndWait(() -> { f.useSession(done); f.showResultNow(); });
        Thread.sleep(700);
        SwingUtilities.invokeAndWait(() -> { try { shot(f, dir + "/ui_result.png"); } catch (Exception e) { throw new RuntimeException(e); } });
        System.out.println(ReportGenerator.build(done.summary(), java.time.LocalDateTime.of(2026, 10, 6, 10, 30)));
        System.exit(0);
    }
}
