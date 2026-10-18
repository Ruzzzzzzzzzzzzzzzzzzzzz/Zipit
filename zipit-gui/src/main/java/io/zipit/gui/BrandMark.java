package io.zipit.gui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * Zipit brand mark in the restrained-classic style: an ink tile with a thin
 * antique-bronze rule and a serif "Z" monogram.
 */
final class BrandMark {

    private static final Color TILE = new Color(0x14110D);
    private static final Color BRONZE = new Color(0xC7A76C);

    private BrandMark() {
    }

    static void paintTile(Graphics2D g, int x, int y, int size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.translate(x, y);
        double radius = Math.max(3, size * 0.16);
        RoundRectangle2D tile = new RoundRectangle2D.Double(0, 0, size, size, radius * 2, radius * 2);
        g2.setColor(TILE);
        g2.fill(tile);
        g2.setColor(new Color(BRONZE.getRed(), BRONZE.getGreen(), BRONZE.getBlue(), 210));
        g2.setStroke(new BasicStroke(Math.max(1f, size * 0.022f)));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, size - 1, size - 1, radius * 2, radius * 2));

        String glyph = "Z";
        Font font = Theme.serifFont(Font.BOLD, size * 0.64f);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        int textWidth = metrics.stringWidth(glyph);
        int baseline = (size - metrics.getHeight()) / 2 + metrics.getAscent();
        g2.setColor(BRONZE);
        g2.drawString(glyph, (size - textWidth) / 2 - 1, baseline);
        g2.dispose();
    }

    /** Glyph only (transparent background), used on About / decorative surfaces. */
    static void paintGlyph(Graphics2D g, int x, int y, int size, Color from, Color to) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(x, y);
        Font font = Theme.serifFont(Font.BOLD, size * 0.86f);
        g2.setFont(font);
        FontMetrics metrics = g2.getFontMetrics();
        g2.setColor(from);
        g2.drawString("Z", (size - metrics.stringWidth("Z")) / 2,
                (size - metrics.getHeight()) / 2 + metrics.getAscent());
        g2.dispose();
    }

    static Icon tileIcon(final int size) {
        return new Icon() {
            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                paintTile((Graphics2D) g, x, y, size);
            }
        };
    }

    static BufferedImage appIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        paintTile(g2, 0, 0, size);
        g2.dispose();
        return image;
    }
}
