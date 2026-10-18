package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Page title block in the restrained-classic style: small bronze overline, a
 * serif display title with slight letter-spacing, a thin bronze rule and a sans
 * subtitle, with right-aligned actions.
 */
final class PageHeader extends JPanel {

    private final JPanel actions = new JPanel();
    private final JPanel titles = new JPanel();

    PageHeader(String overline, String title, String subtitle) {
        super(new BorderLayout(Theme.px(18), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.px(18), Theme.px(24),
                Theme.px(14), Theme.px(24)));

        titles.setOpaque(false);
        titles.setLayout(new javax.swing.BoxLayout(titles, javax.swing.BoxLayout.Y_AXIS));
        if (overline != null && !overline.isEmpty()) {
            javax.swing.JLabel over = Ui.colored(overline.toUpperCase(java.util.Locale.ROOT),
                    () -> Theme.ACCENT, 12f, Font.BOLD);
            over.setAlignmentX(Component.LEFT_ALIGNMENT);
            titles.add(over);
            titles.add(javax.swing.Box.createVerticalStrut(Theme.px(2)));
        }
        JLabel heading = new DisplayTitle(title);
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        titles.add(heading);
        titles.add(javax.swing.Box.createVerticalStrut(Theme.px(6)));
        JPanel rule = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.ACCENT);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        rule.setOpaque(false);
        rule.setAlignmentX(Component.LEFT_ALIGNMENT);
        Dimension ruleSize = new Dimension(Theme.px(72), 1);
        rule.setPreferredSize(ruleSize);
        rule.setMaximumSize(ruleSize);
        rule.setMinimumSize(ruleSize);
        titles.add(rule);
        if (subtitle != null && !subtitle.isEmpty()) {
            titles.add(javax.swing.Box.createVerticalStrut(Theme.px(6)));
            javax.swing.JLabel sub = Ui.muted(subtitle);
            sub.setFont(Theme.font(Font.PLAIN, 13f));
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            titles.add(sub);
        }
        add(titles, BorderLayout.WEST);

        actions.setOpaque(false);
        actions.setLayout(new javax.swing.BoxLayout(actions, javax.swing.BoxLayout.X_AXIS));
        actions.setAlignmentY(Component.CENTER_ALIGNMENT);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(actions, BorderLayout.EAST);
        add(holder, BorderLayout.EAST);
    }

    PageHeader action(Component component) {
        if (actions.getComponentCount() > 0) {
            actions.add(javax.swing.Box.createHorizontalStrut(Theme.px(8)));
        }
        actions.add(component);
        return this;
    }

    PageHeader actions(Component... components) {
        for (Component component : components) {
            action(component);
        }
        return this;
    }

    JPanel titles() {
        return titles;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension preferred = super.getPreferredSize();
        return new Dimension(preferred.width, Math.max(preferred.height, Theme.px(86)));
    }

    /** Serif display title with letter-spacing (manual tracking). */
    private static final class DisplayTitle extends JLabel {
        private static final float TRACKING = 1.3f;

        DisplayTitle(String text) {
            super(text);
            setFont(Theme.serifFont(Font.BOLD, 28f));
        }

        @Override
        public java.awt.Color getForeground() {
            return Theme.TEXT;
        }

        private float tracking() {
            return Theme.px(TRACKING);
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            float width = 0;
            String text = getText();
            for (int i = 0; i < text.length(); i++) {
                width += metrics.charWidth(text.charAt(i)) + tracking();
            }
            return new Dimension((int) Math.ceil(width) + Theme.px(2),
                    metrics.getHeight() + Theme.px(2));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            g2.setColor(getForeground());
            FontMetrics metrics = g2.getFontMetrics();
            float x = Theme.px(1);
            int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
            String text = getText();
            for (int i = 0; i < text.length(); i++) {
                String ch = String.valueOf(text.charAt(i));
                g2.drawString(ch, x, baseline);
                x += metrics.charWidth(text.charAt(i)) + tracking();
            }
            g2.dispose();
        }
    }
}
