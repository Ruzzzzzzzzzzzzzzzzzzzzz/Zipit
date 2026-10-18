package io.zipit.gui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Window backdrop for the restrained-classic theme: flat parchment (light) or
 * ink (dark) with an extremely subtle paper-fibre texture and a 1% vertical
 * gradient. No coloured blobs or glows.
 *
 * <p>The texture is generated once per size/theme and cached, so repaints stay
 * cheap even during the theme slide animation.</p>
 */
final class AmbientBackground extends JPanel {

    private BufferedImage cache;
    private int cachedWidth;
    private int cachedHeight;
    private boolean cachedDark;

    AmbientBackground() {
        setOpaque(true);
        setBackground(Theme.BG);
    }

    @Override
    protected void paintComponent(Graphics g) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        if (cache == null || width != cachedWidth || height != cachedHeight
                || cachedDark != Theme.dark) {
            cache = render(width, height);
            cachedWidth = width;
            cachedHeight = height;
            cachedDark = Theme.dark;
        }
        g.drawImage(cache, 0, 0, null);
    }

    private static BufferedImage render(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 1% gradient from a slightly lighter top edge to the base colour.
        Color base = Theme.BG;
        Color top = Theme.blend(base, Theme.dark ? new Color(0x241F18) : Color.WHITE, 0.10f);
        g.setPaint(new GradientPaint(0, 0, top, 0, height, base));
        g.fillRect(0, 0, width, height);

        // Paper fibres: a deterministic sprinkle of 2-6 px warm/cool strokes.
        Random random = new Random(0x5A17C1A55L);
        int fibres = Math.max(60, (width * height) / 1400);
        for (int i = 0; i < fibres; i++) {
            int x = random.nextInt(Math.max(1, width));
            int y = random.nextInt(Math.max(1, height));
            int length = 2 + random.nextInt(5);
            int alpha = 5 + random.nextInt(7);
            boolean warm = random.nextBoolean();
            Color fibre = Theme.dark
                    ? new Color(warm ? 214 : 180, warm ? 196 : 176, warm ? 158 : 158, alpha)
                    : new Color(warm ? 150 : 120, warm ? 132 : 116, warm ? 102 : 104, alpha);
            g.setColor(fibre);
            if (random.nextInt(4) == 0) {
                g.fillRect(x, y, 1, length);
            } else {
                g.fillRect(x, y, length, 1);
            }
        }

        // Very soft vignette keeps the content edges calm without glowing.
        if (Theme.dark) {
            g.setPaint(new java.awt.RadialGradientPaint(
                    new java.awt.geom.Point2D.Double(width * 0.5, height * 0.42),
                    Math.max(width, height) * 0.85f,
                    new float[]{0.55f, 1f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 54)},
                    java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE));
            g.fillRect(0, 0, width, height);
        }
        g.dispose();
        return image;
    }
}
