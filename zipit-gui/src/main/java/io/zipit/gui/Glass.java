package io.zipit.gui;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Surface painting helpers for the restrained-classic theme.
 *
 * <p>The previous frosted-glass blur was replaced by flat parchment/ink panels
 * with hairline borders (DESIGN §1). The class name and API are kept so the rest
 * of the UI stays simple; {@code -Dzipit.gui.glass=off} still forces opaque
 * chrome.</p>
 */
final class Glass {

    private static final int CHROME_ALPHA = 246;

    private Glass() {
    }

    static boolean enabled() {
        return !"off".equalsIgnoreCase(System.getProperty("zipit.gui.glass", ""));
    }

    static void clearCache() {
        // Nothing cached anymore; kept for API compatibility.
    }

    /** Flat parchment/ink chrome surface for the top bar, sidebar and status bar. */
    static void paintFrosted(Graphics2D g2, Component component, int radius) {
        int width = component.getWidth();
        int height = component.getHeight();
        Color base = Theme.dark ? Theme.CARD : new Color(0xFAF6ED);
        Color fill = new Color(base.getRed(), base.getGreen(), base.getBlue(),
                enabled() ? CHROME_ALPHA : 255);
        Shape shape = new RoundRectangle2D.Double(0, 0, width, height, radius, radius);
        g2.setColor(fill);
        g2.fill(shape);
        // A single quiet vertical tone shift instead of a glossy gradient.
        g2.setColor(Theme.dark ? new Color(255, 240, 214, 7) : new Color(255, 255, 255, 90));
        g2.fillRect(0, 0, width, Math.max(1, height / 3));
        g2.setColor(Theme.glassBorder());
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, radius, radius));
    }

    /** Solid dialog surface with a single hairline border (no blur). */
    static void paintDialogGlass(Graphics2D g2, JComponent component, Window owner) {
        int width = Math.max(1, component.getWidth());
        int height = Math.max(1, component.getHeight());
        int radius = Theme.px(8);
        Shape shape = new RoundRectangle2D.Double(0, 0, width - 1, height - 1, radius, radius);
        g2.setColor(Theme.dark ? Theme.CARD : new Color(0xFBF8F1));
        g2.fill(shape);
        g2.setColor(Theme.BORDER);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 2, height - 2, radius, radius));
        g2.setColor(Theme.glassHighlight());
        g2.draw(new RoundRectangle2D.Double(1.5, 1.5, width - 4, height - 4, radius - 1, radius - 1));
    }

    static JComponent glassRoot(final JDialog dialog, final Window owner) {
        JComponent root = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                paintDialogGlass(g2, this, owner == null ? dialog.getOwner() : owner);
                g2.dispose();
            }
        };
        root.setOpaque(false);
        return root;
    }

    /** Kept for compatibility; returns a flat thematic surface image. */
    static BufferedImage backdropFor(Window window, int width, int height) {
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height),
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        g2.setColor(Theme.dark ? Theme.BG : new Color(0xF6F1E7));
        g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        g2.dispose();
        return image;
    }

    static BufferedImage blur(BufferedImage source, int downscale, int radiusX, int radiusY) {
        return source;
    }

    static BufferedImage blurredFrame(JFrame frame) {
        return null;
    }

    /** Unused import guard so ImageIO stays referenced if future code needs it. */
    static void savePng(BufferedImage image, java.io.File file) throws Exception {
        ImageIO.write(image, "png", file);
    }

    static void invokeLater(Runnable runnable) {
        SwingUtilities.invokeLater(runnable);
    }
}
