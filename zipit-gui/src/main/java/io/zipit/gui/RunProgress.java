package io.zipit.gui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;

/** Slim gradient progress bar with a moving highlight while a run is active. */
final class RunProgress extends JComponent {

    private int value;
    private boolean running;
    private float shimmer;
    private Timer animator;

    RunProgress() {
        setOpaque(false);
        Dimension size = new Dimension(Theme.px(220), Theme.px(8));
        setPreferredSize(size);
        setMinimumSize(new Dimension(Theme.px(80), Theme.px(8)));
    }

    void setProgress(int value) {
        this.value = Math.max(0, Math.min(100, value));
        repaint();
    }

    int progress() {
        return value;
    }

    void setRunning(boolean running) {
        this.running = running;
        if (running) {
            if (animator == null) {
                animator = new Timer(28, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        shimmer += 0.035f;
                        if (shimmer > 1f) {
                            shimmer = 0f;
                        }
                        repaint();
                    }
                });
            }
            animator.start();
        } else if (animator != null) {
            animator.stop();
        }
        repaint();
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, Theme.px(8));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        float arc = height;

        g2.setColor(Theme.dark ? new Color(0x241F18) : new Color(0xEDE5D6));
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));

        int fillWidth = Math.round(width * (value / 100f));
        if (fillWidth > 2) {
            g2.setColor(Theme.ACCENT);
            g2.fill(new RoundRectangle2D.Double(0, 0, fillWidth, height, arc, arc));
        }
        g2.setColor(Theme.alpha(Theme.BORDER_STRONG, 120));
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, arc, arc));
        g2.dispose();
    }
}
