package io.zipit.gui;

import javax.swing.JToggleButton;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Selectable pill used for presets, filters and quick options. */
final class Chip extends JToggleButton {

    private final String iconName;
    private boolean hover;

    Chip(String text) {
        this(text, null);
    }

    Chip(String text, String iconName) {
        super(text);
        this.iconName = iconName;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.font(Font.BOLD, 12f));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                repaint();
            }
        });
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics metrics = getFontMetrics(getFont());
        int width = metrics.stringWidth(getText()) + Theme.px(30);
        if (iconName != null) {
            width += Theme.px(17);
        }
        return new Dimension(width, Theme.px(32));
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        boolean selected = isSelected();
        int arc = Theme.px(4);
        Color fill = selected ? Theme.CARD_ALT : (hover ? Theme.CARD_HOVER : Theme.CARD);
        Color border = selected ? Theme.ACCENT : (hover ? Theme.BORDER_STRONG : Theme.BORDER);
        g2.setColor(fill);
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
        g2.setColor(border);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, arc, arc));
        // Selected chips use a quiet bronze underline, not a filled pill.
        if (selected) {
            g2.setColor(Theme.ACCENT);
            g2.fillRect(Theme.px(5), height - Theme.px(2), width - Theme.px(10), Theme.px(2));
        }

        Color textColor = selected ? Theme.TEXT : Theme.MUTED;
        FontMetrics metrics = g2.getFontMetrics(getFont());
        String text = getText();
        int textWidth = metrics.stringWidth(text);
        int contentWidth = textWidth + (iconName == null ? 0 : Theme.px(17) + Theme.px(6));
        int x = (width - contentWidth) / 2;
        int baseline = height / 2 - metrics.getHeight() / 2 + metrics.getAscent();
        if (iconName != null) {
            Icons.paint(g2, iconName, x, height / 2 - Theme.px(8), Theme.px(16), textColor, 1.7f);
            x += Theme.px(17) + Theme.px(6);
        }
        g2.setFont(getFont());
        g2.setColor(textColor);
        g2.drawString(text, x, baseline);
        g2.dispose();
    }
}
