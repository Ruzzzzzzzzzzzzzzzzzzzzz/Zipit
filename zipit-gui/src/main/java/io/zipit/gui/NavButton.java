package io.zipit.gui;

import javax.swing.JButton;
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

/** Sidebar navigation item with an active pill, accent indicator and vector icon. */
final class NavButton extends JButton {

    private final String iconName;
    private boolean active;
    private boolean hover;

    NavButton(String label, String iconName) {
        super(label);
        this.iconName = iconName;
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setHorizontalAlignment(LEFT);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setFont(Theme.font(Font.PLAIN, 14f));
        Dimension size = new Dimension(Theme.px(208), Theme.px(42));
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
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

    void setActive(boolean active) {
        this.active = active;
        setFont(Theme.font(active ? Font.BOLD : Font.PLAIN, 14f));
        repaint();
    }

    boolean isActive() {
        return active;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(208), Theme.px(42));
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
        int arc = Theme.px(4);

        if (active) {
            g2.setColor(Theme.CARD_ALT);
            g2.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
            // A single 2px antique-bronze rule marks the active page.
            g2.setColor(Theme.ACCENT);
            g2.fillRect(0, Theme.px(6), Theme.px(2), height - Theme.px(12));
        } else if (hover) {
            g2.setColor(Theme.CARD);
            g2.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
        }

        int iconX = Theme.px(14);
        int iconSize = Theme.px(19);
        Color iconColor = active ? Theme.ACCENT
                : (hover ? Theme.TEXT : Theme.NAV_MUTED);
        Icons.paint(g2, iconName, iconX, (height - iconSize) / 2, iconSize, iconColor, 1.7f);

        Color textColor = active ? Theme.TEXT : (hover ? Theme.TEXT : Theme.NAV_MUTED);
        g2.setFont(getFont());
        g2.setColor(textColor);
        FontMetrics metrics = g2.getFontMetrics();
        int baseline = height / 2 - metrics.getHeight() / 2 + metrics.getAscent();
        g2.drawString(getText(), Theme.px(44), baseline);
        g2.dispose();
    }
}
