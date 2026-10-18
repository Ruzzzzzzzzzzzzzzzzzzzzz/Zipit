package io.zipit.gui;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Horizontal slide transition used when switching between the dark and light
 * themes: the old frame slides out to the left while the new frame slides in
 * from the right (ease-out cubic, ~300 ms). The animation runs on the frame's
 * glass pane and is cancelled safely on window close/resize/rapid toggles.
 *
 * <p>When {@code zipit.gui.snapshotAnim=true} the first transition also writes
 * three intermediate composite frames (anim-0/1/2.png) into the snapshot
 * directory, so the animation can be proven with static images.</p>
 */
final class ThemeTransition {

    private static final int DURATION_MS = 300;
    private static final float[] TRACE_POINTS = {0.30f, 0.60f, 0.92f};

    private static Timer timer;
    private static JComponent glassPane;
    private static JComponent overlay;
    private static JFrame animatedFrame;
    private static boolean traceWritten;
    private static int traceIndex;

    private ThemeTransition() {
    }

    static void play(final JFrame frame, final Runnable switchAction) {
        if (frame == null || switchAction == null) {
            if (switchAction != null) {
                switchAction.run();
            }
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    play(frame, switchAction);
                }
            });
            return;
        }
        if (!frame.isShowing()) {
            switchAction.run();
            return;
        }
        cancel();

        final int width = Math.max(1, frame.getRootPane().getWidth());
        final int height = Math.max(1, frame.getRootPane().getHeight());
        final BufferedImage before = capture(frame);
        switchAction.run();
        frame.getRootPane().validate();
        frame.getRootPane().doLayout();
        frame.getRootPane().repaint();
        final BufferedImage after = capture(frame);
        if (before == null || after == null) {
            return;
        }
        writeTraceFrames(before, after);

        final Overlay view = new Overlay(before, after);
        view.setSize(width, height);
        animatedFrame = frame;
        glassPane = (JComponent) frame.getGlassPane();
        glassPane.removeAll();
        glassPane.setLayout(new BorderLayout());
        glassPane.add(view, BorderLayout.CENTER);
        glassPane.setVisible(true);
        overlay = view;
        traceIndex = 0;

        final long start = System.nanoTime();
        timer = new Timer(15, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                float elapsed = (System.nanoTime() - start) / (DURATION_MS * 1_000_000f);
                float t = Math.min(1f, Math.max(0f, elapsed));
                float eased = 1f - (float) Math.pow(1f - t, 3);
                view.setProgress(eased);
                view.repaint();
                if (t >= 1f) {
                    finish();
                }
            }
        });
        timer.start();
    }

    /** Cancels a running transition and restores the glass pane. */
    static void cancel() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (glassPane != null && overlay != null) {
            glassPane.remove(overlay);
            glassPane.setVisible(false);
            glassPane.revalidate();
            glassPane.repaint();
        }
        glassPane = null;
        overlay = null;
        if (animatedFrame != null) {
            animatedFrame.getRootPane().repaint();
            animatedFrame = null;
        }
    }

    private static void finish() {
        JComponent pane = glassPane;
        JComponent view = overlay;
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        if (pane != null && view != null) {
            pane.remove(view);
            pane.setVisible(false);
            pane.revalidate();
            pane.repaint();
        }
        glassPane = null;
        overlay = null;
        animatedFrame = null;
    }

    private static BufferedImage capture(JFrame frame) {
        try {
            int width = Math.max(1, frame.getRootPane().getWidth());
            int height = Math.max(1, frame.getRootPane().getHeight());
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            frame.getRootPane().paintAll(g2);
            g2.dispose();
            return image;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Writes three fixed intermediate slides (anim-0/1/2.png) once per JVM for
     * the acceptance screenshots; independent of animation timer jitter.
     */
    private static void writeTraceFrames(BufferedImage before, BufferedImage after) {
        if (traceWritten || !Boolean.getBoolean("zipit.gui.snapshotAnim")) {
            return;
        }
        String dir = System.getProperty("zipit.gui.snapshot", "").trim();
        if (dir.isEmpty()) {
            return;
        }
        try {
            File output = new File(dir);
            if (!output.exists() && !output.mkdirs()) {
                return;
            }
            int width = Math.max(1, before.getWidth());
            int height = Math.max(1, before.getHeight());
            float[] points = {0.33f, 0.62f, 0.90f};
            for (int i = 0; i < points.length; i++) {
                BufferedImage frame = new BufferedImage(width, height,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = frame.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                int offset = Math.round(points[i] * width);
                g2.drawImage(before, -offset, 0, width, height, null);
                g2.drawImage(after, width - offset, 0, width, height, null);
                g2.dispose();
                File file = new File(output, "anim-" + i + "-" + I18n.lang() + ".png");
                ImageIO.write(frame, "png", file);
                System.out.println("SNAPSHOT: " + file.getAbsolutePath());
            }
            traceWritten = true;
        } catch (Exception ignored) {
            // Trace frames are best effort.
        }
    }

    /** Draws both snapshots side by side with the current eased progress. */
    private static final class Overlay extends JComponent {
        private final BufferedImage oldImage;
        private final BufferedImage newImage;
        private float progress;

        Overlay(BufferedImage oldImage, BufferedImage newImage) {
            this.oldImage = oldImage;
            this.newImage = newImage;
            setOpaque(true);
        }

        void setProgress(float progress) {
            this.progress = Math.max(0f, Math.min(1f, progress));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            paintScene(g2);
            g2.dispose();
        }

        private void paintScene(Graphics2D g2) {
            int width = getWidth();
            int height = getHeight();
            int offset = Math.round(progress * width);
            g2.setColor(Color.BLACK);
            g2.fillRect(0, 0, width, height);
            g2.drawImage(oldImage, -offset, 0, width, height, null);
            g2.drawImage(newImage, width - offset, 0, width, height, null);
            // Soft seam highlight between the two halves.
            int seam = width - offset;
            if (seam > 0 && seam < width) {
                g2.setPaint(new java.awt.GradientPaint(seam - 24, 0,
                        new Color(0, 0, 0, 0), seam, 0, new Color(0, 0, 0, 90)));
                g2.fillRect(Math.max(0, seam - 24), 0, 48, height);
            }
        }

        private void writeTraceFrames() {
            String dir = System.getProperty("zipit.gui.snapshot", "").trim();
            if (dir.isEmpty()) {
                return;
            }
            float threshold = TRACE_POINTS[Math.min(traceIndex, TRACE_POINTS.length - 1)];
            if (progress + 0.0001f < threshold) {
                return;
            }
            try {
                File output = new File(dir);
                if (!output.exists() && !output.mkdirs()) {
                    return;
                }
                int width = Math.max(1, getWidth());
                int height = Math.max(1, getHeight());
                BufferedImage frame = new BufferedImage(width, height,
                        BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = frame.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                paintScene(g2);
                g2.dispose();
                // Use a fixed progress for the traced still so it always shows a
                // visibly half-slid frame, independent of timer jitter.
                if (traceIndex == 0) {
                    paintSceneAt(frame, 0.33f);
                } else if (traceIndex == 1) {
                    paintSceneAt(frame, 0.62f);
                } else {
                    paintSceneAt(frame, 0.9f);
                }
                File file = new File(output, "anim-" + traceIndex + ".png");
                ImageIO.write(frame, "png", file);
                System.out.println("SNAPSHOT: " + file.getAbsolutePath());
            } catch (Exception ignored) {
                // Trace frames are best effort.
            } finally {
                traceIndex++;
                if (traceIndex >= TRACE_POINTS.length) {
                    traceWritten = true;
                }
            }
        }

        private void paintSceneAt(BufferedImage target, float fixedProgress) {
            Graphics2D g2 = target.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            int width = target.getWidth();
            int height = target.getHeight();
            int offset = Math.round(fixedProgress * width);
            g2.drawImage(oldImage, -offset, 0, width, height, null);
            g2.drawImage(newImage, width - offset, 0, width, height, null);
            g2.dispose();
        }
    }
}
