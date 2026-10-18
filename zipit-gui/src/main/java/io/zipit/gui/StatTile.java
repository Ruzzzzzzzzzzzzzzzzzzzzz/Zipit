package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/** Compact KPI tile with a tinted icon chip, caption, value and sub-line. */
final class StatTile extends JPanel {

    private final String caption;
    private final Supplier<Color> accent;
    private final JLabel valueLabel;
    private final JLabel subLabel;

    StatTile(String iconName, String caption, Supplier<Color> accent) {
        super(new BorderLayout(Theme.px(12), 0));
        this.caption = caption;
        this.accent = accent;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.px(13), Theme.px(15),
                Theme.px(13), Theme.px(15)));

        Card.IconChip chip = new Card.IconChip(iconName, accent);
        chip.setAlignmentY(Component.CENTER_ALIGNMENT);
        add(chip, BorderLayout.WEST);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        JLabel captionLabel = Ui.overline(caption);
        captionLabel.setFont(Theme.font(Font.BOLD, 12f));
        captionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(captionLabel);
        text.add(javax.swing.Box.createVerticalStrut(Theme.px(1)));
        valueLabel = Ui.value("—");
        valueLabel.setFont(Theme.serifFont(Font.BOLD, 26f));
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(valueLabel);
        subLabel = Ui.faint(" ");
        subLabel.setFont(Theme.font(Font.PLAIN, 12f));
        subLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(subLabel);
        add(text, BorderLayout.CENTER);
    }

    void setValue(String value) {
        valueLabel.setText(value);
    }

    void setSub(String sub) {
        subLabel.setText(sub == null || sub.isEmpty() ? " " : sub);
    }

    String caption() {
        return caption;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(198), Theme.px(92));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth() - 1;
        int height = getHeight() - 1;
        int radius = Theme.px(6);
        Color tone = accent == null ? Theme.ACCENT : accent.get();

        g2.setColor(Theme.alpha(Theme.SHADOW, Theme.dark ? 55 : 16));
        g2.fill(new RoundRectangle2D.Double(1, 2, width - 1, height - 1, radius, radius));
        g2.setColor(Theme.CARD);
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, radius, radius));
        g2.setColor(Theme.BORDER);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, radius, radius));

        // Status colour is restricted to a 2px rule on the left edge.
        g2.setColor(tone);
        g2.fillRect(0, Theme.px(12), Theme.px(2), height - Theme.px(24));
        g2.dispose();
        super.paintComponent(g);
    }
}
