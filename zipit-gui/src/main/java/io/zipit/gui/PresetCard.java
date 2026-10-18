package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;

/** Rich, clickable preset card used on the Rules &amp; Presets page. */
final class PresetCard extends JPanel {

    private final String value;
    private final String title;
    private final String description;
    private final String iconName;
    private final JLabel selectedBadge;
    private Runnable onClick;
    private boolean selected;
    private boolean hover;

    PresetCard(String value, String title, String description, String iconName) {
        super(new BorderLayout(0, Theme.px(8)));
        this.value = value;
        this.title = title;
        this.description = description;
        this.iconName = iconName;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.px(14), Theme.px(15),
                Theme.px(13), Theme.px(15)));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new javax.swing.BoxLayout(top, javax.swing.BoxLayout.X_AXIS));
        top.add(new Card.IconChip(iconName, () -> selected ? Theme.ACCENT2 : Theme.ACCENT));
        top.add(javax.swing.Box.createHorizontalGlue());
        selectedBadge = Ui.pill(I18n.t("ui.active"));
        selectedBadge.setVisible(false);
        top.add(selectedBadge);
        add(top, BorderLayout.NORTH);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        JLabel name = Ui.h3(title);
        name.setFont(Theme.font(Font.BOLD, 14f));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(name);
        text.add(javax.swing.Box.createVerticalStrut(Theme.px(3)));
        JLabel desc = Ui.mutedWrapped(description, 216);
        desc.setFont(Theme.font(Font.PLAIN, 12f));
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(desc);
        add(text, BorderLayout.CENTER);
        setMinimumSize(new Dimension(Theme.px(120), Theme.px(96)));

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

            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClick != null && javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    onClick.run();
                }
            }
        });
    }

    void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    String value() {
        return value;
    }

    boolean isPresetSelected() {
        return selected;
    }

    void setPresetSelected(boolean selected) {
        this.selected = selected;
        selectedBadge.setVisible(selected);
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(200), Theme.px(150));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth() - 1;
        int height = getHeight() - 1;
        int radius = Theme.px(6);

        g2.setColor(Theme.alpha(Theme.SHADOW, Theme.dark ? 60 : 20));
        g2.fill(new RoundRectangle2D.Double(1, 2, width - 1, height - 1, radius, radius));

        g2.setColor(selected ? Theme.CARD_ALT : (hover ? Theme.CARD_HOVER : Theme.CARD));
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, radius, radius));

        Color border = selected ? Theme.ACCENT
                : (hover ? Theme.BORDER_STRONG : Theme.BORDER);
        g2.setColor(border);
        g2.setStroke(new java.awt.BasicStroke(selected ? 1.4f : 1f));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, radius, radius));
        g2.dispose();
        super.paintComponent(g);
    }
}
