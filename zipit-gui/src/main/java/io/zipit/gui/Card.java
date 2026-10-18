package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/**
 * Elevated rounded surface that hosts a titled section.
 *
 * <p>Cards are the primary layout primitive of the redesign: hairline border,
 * soft shadow, an optional tinted icon chip and a body area filled by the
 * caller.</p>
 */
final class Card extends JPanel {

    static final int RADIUS = 6;

    private final JPanel headerRight = new JPanel();
    private final JPanel body;
    private JComponent iconChip;
    private boolean bodyIsBox = true;

    Card() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.px(14), Theme.px(16),
                Theme.px(16), Theme.px(16)));
        body = Ui.column();
        add(body, BorderLayout.CENTER);
    }

    Card(String icon, String title, String subtitle) {
        this(icon, title, subtitle, () -> Theme.ACCENT);
    }

    Card(String icon, String title, String subtitle, Supplier<Color> accent) {
        this();
        JPanel header = new JPanel(new BorderLayout(Theme.px(13), 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 0, Theme.px(10), 0));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new javax.swing.BoxLayout(titles, javax.swing.BoxLayout.Y_AXIS));
        JLabel titleLabel = Ui.h3(title);
        titleLabel.setFont(Theme.serifFont(java.awt.Font.BOLD, 16f));
        titles.add(titleLabel);
        if (subtitle != null && !subtitle.isEmpty()) {
            titles.add(javax.swing.Box.createVerticalStrut(Theme.px(2)));
            JLabel subtitleLabel = Ui.muted(subtitle);
            subtitleLabel.setFont(Theme.font(java.awt.Font.PLAIN, 12f));
            titles.add(subtitleLabel);
        }

        headerRight.setOpaque(false);
        headerRight.setLayout(new javax.swing.BoxLayout(headerRight, javax.swing.BoxLayout.X_AXIS));
        headerRight.setAlignmentY(Component.CENTER_ALIGNMENT);

        if (icon != null) {
            iconChip = new IconChip(icon, accent);
            JPanel left = new JPanel();
            left.setOpaque(false);
            left.setLayout(new javax.swing.BoxLayout(left, javax.swing.BoxLayout.X_AXIS));
            iconChip.setAlignmentY(Component.CENTER_ALIGNMENT);
            left.add(iconChip);
            left.add(javax.swing.Box.createHorizontalStrut(Theme.px(13)));
            titles.setAlignmentY(Component.CENTER_ALIGNMENT);
            left.add(titles);
            header.add(left, BorderLayout.WEST);
        } else {
            header.add(titles, BorderLayout.WEST);
        }
        header.add(headerRight, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);
    }

    Card body(int gap, Component... components) {
        for (int i = 0; i < components.length; i++) {
            if (i > 0 && gap > 0) {
                body.add(javax.swing.Box.createVerticalStrut(gap));
            }
            Component component = components[i];
            if (component == null) {
                continue;
            }
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            body.add(component);
        }
        return this;
    }

    Card body(Component... components) {
        return body(Theme.px(9), components);
    }

    /** Replaces the vertical body stack with a single border-layout component. */
    Card fill(JComponent component) {
        if (bodyIsBox) {
            body.removeAll();
            body.setLayout(new BorderLayout());
            bodyIsBox = false;
        }
        body.add(component, BorderLayout.CENTER);
        return this;
    }

    Card bottom(Component component) {
        add(component, BorderLayout.SOUTH);
        return this;
    }

    Card headerAction(Component component) {
        headerRight.add(component);
        return this;
    }

    JPanel bodyPanel() {
        return body;
    }

    void setAccent(Supplier<Color> accent) {
        if (iconChip instanceof IconChip) {
            ((IconChip) iconChip).setAccent(accent);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth() - 1;
        int height = getHeight() - 1;
        int radius = Theme.px(RADIUS);

        // Extremely light shadow: a single offset hairline of ink.
        g2.setColor(Theme.alpha(Theme.SHADOW, Theme.dark ? 60 : 18));
        g2.fill(new RoundRectangle2D.Double(1, 2, width - 1, height - 1, radius, radius));
        g2.setColor(Theme.CARD);
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, radius, radius));
        g2.setColor(Theme.BORDER);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, radius, radius));
        g2.dispose();
        super.paintComponent(g);
    }

    /** Small tinted square that carries the section icon. */
    static final class IconChip extends JComponent {
        private final String icon;
        private Supplier<Color> accent;

        IconChip(String icon, Supplier<Color> accent) {
            this.icon = icon;
            this.accent = accent;
            Dimension size = new Dimension(Theme.px(38), Theme.px(38));
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            setOpaque(false);
        }

        void setAccent(Supplier<Color> accent) {
            this.accent = accent;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color color = accent == null ? Theme.ACCENT : accent.get();
            int size = Math.min(getWidth(), getHeight());
            g2.setColor(Theme.alpha(color, Theme.dark ? 40 : 26));
            g2.fill(new RoundRectangle2D.Double(0, 0, size, size, Theme.px(4), Theme.px(4)));
            g2.setColor(Theme.alpha(color, Theme.dark ? 80 : 60));
            g2.setStroke(new java.awt.BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, size - 1, size - 1,
                    Theme.px(4), Theme.px(4)));
            Icons.paint(g2, icon, Theme.px(10), Theme.px(10), size - Theme.px(20), color, 1.7f);
            g2.dispose();
        }
    }
}
