package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Supplier;

/** Shared visual toolkit: typography, controls, layout and painted primitives. */
final class Ui {

    private Ui() {
    }

    // ------------------------------------------------------------------
    // typography
    // ------------------------------------------------------------------

    static JLabel title(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.TEXT);
        label.setFont(Theme.font(Font.BOLD, 26f));
        return label;
    }

    static JLabel h2(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.TEXT);
        label.setFont(Theme.font(Font.BOLD, 17f));
        return label;
    }

    static JLabel h3(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.TEXT);
        label.setFont(Theme.font(Font.BOLD, 15f));
        return label;
    }

    static JLabel body(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.TEXT);
        label.setFont(Theme.font(Font.PLAIN, 14f));
        return label;
    }

    static JLabel bold(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.TEXT);
        label.setFont(Theme.font(Font.BOLD, 14f));
        return label;
    }

    static JLabel muted(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.MUTED);
        label.setFont(Theme.font(Font.PLAIN, 13f));
        return label;
    }

    static JLabel mutedWrapped(String text, int width) {
        JLabel label = new ThemeLabel(htmlWrap(text, width), () -> Theme.MUTED);
        label.setFont(Theme.font(Font.PLAIN, 13f));
        return label;
    }

    static JLabel small(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.MUTED);
        label.setFont(Theme.font(Font.PLAIN, 12f));
        return label;
    }

    static JLabel faint(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.FAINT);
        label.setFont(Theme.font(Font.PLAIN, 12f));
        return label;
    }

    static JLabel overline(String text) {
        JLabel label = new ThemeLabel(text.toUpperCase(java.util.Locale.ROOT), () -> Theme.MUTED);
        label.setFont(Theme.font(Font.BOLD, 12f));
        return label;
    }

    static JLabel value(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.TEXT);
        label.setFont(Theme.font(Font.BOLD, 26f));
        return label;
    }

    static JLabel mono(String text) {
        JLabel label = new ThemeLabel(text, () -> Theme.TEXT);
        label.setFont(Theme.mono(13f));
        return label;
    }

    static JLabel colored(String text, Supplier<Color> color, float size, int style) {
        JLabel label = new ThemeLabel(text, color);
        label.setFont(Theme.font(style, size));
        return label;
    }

    private static String htmlWrap(String text, int width) {
        return "<html><body style='width:" + width + "px'>" + escape(text) + "</body></html>";
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ------------------------------------------------------------------
    // buttons
    // ------------------------------------------------------------------

    enum ButtonStyle {
        PRIMARY, SECONDARY, GHOST, DANGER, LINK
    }

    static JButton primary(String text) {
        return new FlatButton(text, null, ButtonStyle.PRIMARY);
    }

    static JButton primary(String text, String icon) {
        return new FlatButton(text, icon, ButtonStyle.PRIMARY);
    }

    static JButton button(String text) {
        return new FlatButton(text, null, ButtonStyle.SECONDARY);
    }

    static JButton button(String text, String icon) {
        return new FlatButton(text, icon, ButtonStyle.SECONDARY);
    }

    static JButton ghost(String text) {
        return new FlatButton(text, null, ButtonStyle.GHOST);
    }

    static JButton ghost(String text, String icon) {
        return new FlatButton(text, icon, ButtonStyle.GHOST);
    }

    static JButton danger(String text) {
        return new FlatButton(text, null, ButtonStyle.DANGER);
    }

    static JButton danger(String text, String icon) {
        return new FlatButton(text, icon, ButtonStyle.DANGER);
    }

    static JButton link(String text) {
        return new FlatButton(text, null, ButtonStyle.LINK);
    }

    static JButton link(String text, String icon) {
        return new FlatButton(text, icon, ButtonStyle.LINK);
    }

    static JButton iconButton(String icon, String tooltip) {
        return iconButton(icon, tooltip, 36);
    }

    static JButton iconButton(String icon, String tooltip, int size) {
        FlatButton button = new FlatButton("", icon, ButtonStyle.GHOST);
        button.setPreferredSize(new Dimension(Theme.px(size), Theme.px(size)));
        button.setMinimumSize(button.getPreferredSize());
        button.setMaximumSize(button.getPreferredSize());
        button.setIconOnly(true);
        button.setFocusable(false);
        hint(button, tooltip);
        return button;
    }

    static void hint(JComponent component, String text) {
        component.setToolTipText(text == null ? null : "<html>" + escape(text) + "</html>");
    }

    // ------------------------------------------------------------------
    // text controls
    // ------------------------------------------------------------------

    static JTextField field(int columns) {
        JTextField field = new JTextField(columns);
        styleField(field);
        return field;
    }

    static JTextField field(int columns, String placeholder) {
        JTextField field = field(columns);
        placeholder(field, placeholder);
        return field;
    }

    static void placeholder(JTextField field, String text) {
        field.putClientProperty("JTextField.placeholderText", text);
    }

    static void placeholder(JTextArea area, String text) {
        area.putClientProperty("JTextField.placeholderText", text);
        area.setToolTipText(text);
    }

    static void styleField(JComponent component) {
        component.putClientProperty("JComponent.roundRect", true);
        component.setFont(Theme.font(Font.PLAIN, 14f));
    }

    static JTextArea editor(int rows, int columns) {
        ThemeArea area = new ThemeArea(rows, columns, false);
        area.setFont(Theme.font(Font.PLAIN, 14f));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    static JTextArea code(int rows, int columns) {
        ThemeArea area = new ThemeArea(rows, columns, true);
        area.setFont(Theme.mono(13f));
        area.setLineWrap(false);
        return area;
    }

    static JComboBox<String> combo(String... items) {
        JComboBox<String> combo = new JComboBox<String>(items);
        combo.setFont(Theme.font(Font.PLAIN, 14f));
        combo.putClientProperty("JComponent.roundRect", true);
        return combo;
    }

    static JSpinner spinner(SpinnerModel model) {
        JSpinner spinner = new JSpinner(model);
        spinner.setFont(Theme.font(Font.PLAIN, 14f));
        spinner.putClientProperty("JComponent.roundRect", true);
        JComponent editor = spinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor) {
            ((JSpinner.DefaultEditor) editor).getTextField().setFont(Theme.font(Font.PLAIN, 14f));
        }
        return spinner;
    }

    static JScrollPane scroll(Component content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.setOpaque(false);
        pane.getViewport().setOpaque(false);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pane.getVerticalScrollBar().setUnitIncrement(Theme.px(18));
        pane.getHorizontalScrollBar().setUnitIncrement(Theme.px(18));
        return pane;
    }

    static JScrollPane scrollRounded(Component content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        pane.getVerticalScrollBar().setUnitIncrement(Theme.px(18));
        pane.getVerticalScrollBar().setOpaque(false);
        pane.getViewport().setOpaque(true);
        JScrollBar bar = pane.getVerticalScrollBar();
        bar.setUnitIncrement(Theme.px(18));
        return pane;
    }

    // ------------------------------------------------------------------
    // layout helpers
    // ------------------------------------------------------------------

    static JPanel row(int gap, Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        for (int i = 0; i < components.length; i++) {
            if (i > 0 && gap > 0) {
                panel.add(Box.createHorizontalStrut(gap));
            }
            Component component = components[i];
            if (component != null) {
                panel.add(component);
            }
        }
        return panel;
    }

    static JPanel col(int gap, Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (int i = 0; i < components.length; i++) {
            if (i > 0 && gap > 0) {
                panel.add(Box.createVerticalStrut(gap));
            }
            Component component = components[i];
            if (component == null) {
                continue;
            }
            if (component instanceof JComponent) {
                ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            panel.add(component);
        }
        return panel;
    }

    static Component vgap(int height) {
        return Box.createVerticalStrut(height);
    }

    static Component hgap(int width) {
        return Box.createHorizontalStrut(width);
    }

    static Component vglue() {
        return Box.createVerticalGlue();
    }

    static Component hglue() {
        return Box.createHorizontalGlue();
    }

    /** 1px themed hairline. */
    static JComponent hline() {
        JComponent line = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(0, 0, getWidth(), 1);
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, 1);
            }

            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, 1);
            }
        };
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    static JPanel fieldGroup(String caption, JComponent field) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = muted(caption);
        label.setFont(Theme.font(Font.BOLD, 13f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createVerticalStrut(5));
        panel.add(field);
        return panel;
    }

    static JPanel inlineField(String caption, JComponent field, int captionWidth) {
        JPanel panel = new JPanel(new java.awt.BorderLayout(10, 0));
        panel.setOpaque(false);
        JLabel label = muted(caption);
        label.setPreferredSize(new Dimension(captionWidth, 26));
        panel.add(label, java.awt.BorderLayout.WEST);
        panel.add(field, java.awt.BorderLayout.CENTER);
        return panel;
    }

    static JPanel page() {
        JPanel page = new JPanel(new java.awt.BorderLayout(0, 18));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(22, 26, 26, 26));
        return page;
    }

    /**
     * Panel used as the scrollable body of every page. It implements
     * {@link javax.swing.Scrollable} with viewport-width tracking so long labels
     * can never widen the page beyond the window and force a clipped layout.
     */
    static JPanel pageBody() {
        return new VStackPanel();
    }

    /**
     * Deterministic vertical stack: every visible child gets the full container
     * width and its preferred height. Unlike BoxLayout this cannot leave rows
     * centred or half-width depending on their maximum size.
     */
    static final class VStackPanel extends JPanel implements javax.swing.Scrollable {

        VStackPanel() {
            setOpaque(false);
            setLayout(new VStackLayout());
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect,
                                               int orientation, int direction) {
            return Theme.px(18);
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect,
                                               int orientation, int direction) {
            return Theme.px(90);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Stretch-to-width vertical flow layout used by page and card bodies. */
    static final class VStackLayout implements java.awt.LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int width = 0;
                int height = 0;
                int count = 0;
                for (Component child : parent.getComponents()) {
                    if (!child.isVisible()) {
                        continue;
                    }
                    Dimension size = child.getPreferredSize();
                    width = Math.max(width, size.width);
                    height += size.height;
                    count++;
                }
                return new Dimension(insets.left + insets.right + width,
                        insets.top + insets.bottom + height);
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int width = 0;
                int height = 0;
                for (Component child : parent.getComponents()) {
                    if (!child.isVisible()) {
                        continue;
                    }
                    Dimension size = child.getMinimumSize();
                    width = Math.max(width, size.width);
                    height += size.height;
                }
                return new Dimension(insets.left + insets.right + width,
                        insets.top + insets.bottom + height);
            }
        }

        @Override
        public void layoutContainer(Container parent) {
            synchronized (parent.getTreeLock()) {
                Insets insets = parent.getInsets();
                int width = Math.max(0, parent.getWidth() - insets.left - insets.right);
                int y = insets.top;
                for (Component child : parent.getComponents()) {
                    if (!child.isVisible()) {
                        continue;
                    }
                    int height = child.getPreferredSize().height;
                    child.setBounds(insets.left, y, width, height);
                    y += height;
                }
            }
        }
    }

    /** Scroll wrapper used for every page of the content area. */
    static JScrollPane pageScroll(JComponent content) {
        JScrollPane pane = scroll(content);
        pane.getVerticalScrollBar().setBlockIncrement(Theme.px(80));
        return pane;
    }

    static JPanel transparent(int gap) {
        JPanel panel = new JPanel(new java.awt.BorderLayout(0, gap));
        panel.setOpaque(false);
        return panel;
    }

    /** Transparent vertical stack (used for card bodies and settings rows). */
    static JPanel column() {
        return new VStackPanel();
    }

    // ------------------------------------------------------------------
    // decorative labels
    // ------------------------------------------------------------------

    static final class Pill extends JLabel {
        private final Supplier<Color> background;
        private final Supplier<Color> foreground;

        Pill(String text, Supplier<Color> background, Supplier<Color> foreground) {
            super(text);
            this.background = background;
            this.foreground = foreground;
            setFont(Theme.font(Font.BOLD, 12f));
            setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
            setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Color getForeground() {
            return foreground == null ? Theme.TEXT : foreground.get();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int arc = Theme.px(3);
            g2.setColor(background.get());
            g2.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, arc, arc));
            g2.setColor(Theme.alpha(Theme.ACCENT, Theme.dark ? 110 : 80));
            g2.setStroke(new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 2, getHeight() - 2, arc, arc));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static JLabel pill(String text) {
        return new Pill(text, () -> Theme.ACCENT_SOFT, () -> Theme.blend(Theme.ACCENT,
                Theme.dark ? Color.WHITE : Color.BLACK, 0.18f));
    }

    static JLabel pill(String text, Supplier<Color> background, Supplier<Color> foreground) {
        return new Pill(text, background, foreground);
    }

    static JLabel badge(String icon, String text, Supplier<Color> tone) {
        JLabel label = new ThemeLabel(text, tone);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setIcon(Icons.themeIcon(icon, 13, tone));
        label.setIconTextGap(6);
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return label;
    }

    static JLabel iconLabel(String icon, int size, Supplier<Color> color) {
        JLabel label = new ThemeLabel("", color);
        label.setIcon(Icons.themeIcon(icon, Theme.px(size), color));
        return label;
    }

    // ------------------------------------------------------------------
    // formatting
    // ------------------------------------------------------------------

    static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", bytes / 1024.0);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        return String.format(java.util.Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    static String humanDuration(long millis) {
        if (millis < 1000) {
            return millis + " ms";
        }
        return String.format(java.util.Locale.ROOT, "%.2f s", millis / 1000.0);
    }

    // ------------------------------------------------------------------
    // dynamic (theme aware) labels
    // ------------------------------------------------------------------

    private static class ThemeLabel extends JLabel {
        private final Supplier<Color> color;

        ThemeLabel(String text, Supplier<Color> color) {
            super(text);
            this.color = color;
        }

        @Override
        public Color getForeground() {
            // Called from the JLabel super constructor before `color` is assigned.
            return color == null ? Theme.TEXT : color.get();
        }
    }

    /** Text area whose surface colors follow the active theme. */
    static final class ThemeArea extends JTextArea {
        private final boolean terminal;

        ThemeArea(int rows, int columns, boolean terminal) {
            super(rows, columns);
            this.terminal = terminal;
            setOpaque(true);
            setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
            setCaretColor(Theme.ACCENT2);
            setSelectionColor(Theme.ACCENT_SOFT);
            setSelectedTextColor(Theme.TEXT);
        }

        boolean isTerminal() {
            return terminal;
        }

        @Override
        public Color getBackground() {
            return terminal ? Theme.TERMINAL : Theme.CARD;
        }

        @Override
        public Color getForeground() {
            return terminal ? new Color(0xCFE3F1) : Theme.TEXT;
        }

        @Override
        public Color getCaretColor() {
            return Theme.ACCENT2;
        }

        @Override
        public Color getSelectionColor() {
            return Theme.ACCENT_SOFT;
        }

        @Override
        public Color getSelectedTextColor() {
            return Theme.TEXT;
        }
    }

    // ------------------------------------------------------------------
    // custom painted button
    // ------------------------------------------------------------------

    static final class FlatButton extends JButton {
        private final ButtonStyle style;
        private String iconName;
        private boolean hover;
        private boolean pressed;
        private boolean iconOnly;

        FlatButton(String text, String iconName, ButtonStyle style) {
            super(text == null ? "" : text);
            this.iconName = iconName;
            this.style = style;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(Theme.font(style == ButtonStyle.PRIMARY ? Font.BOLD : Font.PLAIN, 14f));
            if (iconName != null) {
                setIconTextGap(Theme.px(7));
            }
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        hover = true;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    pressed = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (isEnabled()) {
                        pressed = true;
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    repaint();
                }
            });
        }

        void setIconOnly(boolean iconOnly) {
            this.iconOnly = iconOnly;
            setText("");
        }

        /** Swaps the painted vector icon (used by the theme toggle). */
        void setIconName(String iconName) {
            this.iconName = iconName;
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(getFont());
            int padding = style == ButtonStyle.PRIMARY ? Theme.px(22) : Theme.px(16);
            int height = style == ButtonStyle.PRIMARY ? Theme.px(40) : Theme.px(36);
            if (style == ButtonStyle.LINK) {
                padding = Theme.px(4);
                height = Theme.px(24);
            }
            if (iconOnly) {
                return new Dimension(height, height);
            }
            int width = metrics.stringWidth(stripHtml(getText())) + padding * 2;
            if (iconName != null) {
                width += Theme.px(17) + Theme.px(7);
            }
            return new Dimension(Math.max(width, Theme.px(52)), height);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        private String stripHtml(String text) {
            return text == null ? "" : text.replaceAll("<[^>]+>", "");
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            int arc = Theme.px(4);
            boolean enabled = isEnabled();
            Color textColor = Theme.TEXT;
            Color iconColor = Theme.MUTED;

            if (style == ButtonStyle.PRIMARY) {
                Color primary = enabled ? (pressed || hover ? Theme.ACCENT2 : Theme.ACCENT)
                        : Theme.alpha(Theme.BORDER_STRONG, 150);
                g2.setColor(primary);
                g2.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
                g2.setColor(Theme.alpha(Theme.BORDER_STRONG, enabled ? 90 : 40));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, arc, arc));
                // Ink text on bronze, parchment text on dark ink accents.
                textColor = Theme.dark ? new Color(0x1A1610) : new Color(0xFBF8F1);
                iconColor = textColor;
            } else if (style == ButtonStyle.SECONDARY) {
                Color fill = enabled ? Theme.CARD_ALT : Theme.alpha(Theme.CARD_ALT, 120);
                if (enabled && pressed) {
                    fill = Theme.alpha(Theme.BORDER_STRONG, 120);
                } else if (enabled && hover) {
                    fill = Theme.CARD_HOVER;
                }
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
                g2.setColor(enabled ? (hover ? Theme.BORDER_STRONG : Theme.BORDER) : Theme.BORDER);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, arc, arc));
                textColor = enabled ? Theme.TEXT : Theme.MUTED;
                iconColor = enabled ? Theme.MUTED : Theme.FAINT;
            } else if (style == ButtonStyle.GHOST) {
                if (enabled && (hover || pressed)) {
                    g2.setColor(pressed ? Theme.CARD_HOVER : Theme.CARD_ALT);
                    g2.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
                }
                textColor = enabled ? (hover ? Theme.TEXT : Theme.MUTED) : Theme.FAINT;
                iconColor = enabled ? (hover ? Theme.TEXT : Theme.MUTED) : Theme.FAINT;
            } else if (style == ButtonStyle.DANGER) {
                Color base = Theme.DANGER;
                if (enabled && (hover || pressed)) {
                    g2.setColor(Theme.alpha(base, 22));
                    g2.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
                }
                g2.setColor(Theme.alpha(base, enabled ? (hover ? 140 : 90) : 40));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, arc, arc));
                textColor = enabled ? base : Theme.alpha(base, 110);
                iconColor = textColor;
            } else { // LINK
                textColor = enabled ? (hover ? Theme.ACCENT2 : Theme.ACCENT) : Theme.FAINT;
                iconColor = textColor;
            }

            if (hasFocus() && enabled && style != ButtonStyle.LINK) {
                g2.setColor(Theme.alpha(Theme.ACCENT, 130));
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(1, 1, width - 3, height - 3, arc, arc));
            }

            String text = getText();
            boolean hasText = text != null && !text.isEmpty();
            int contentWidth = 0;
            int textWidth = hasText ? g2.getFontMetrics(getFont()).stringWidth(stripHtml(text)) : 0;
            if (iconName != null) {
                contentWidth += Theme.px(17);
                if (hasText) {
                    contentWidth += Theme.px(7);
                }
            }
            contentWidth += textWidth;
            int contentX = Math.max(Theme.px(4), (width - contentWidth) / 2);
            int centerY = height / 2;

            if (iconName != null) {
                int iconSize = Theme.px(17);
                Icons.paint(g2, iconName, contentX, centerY - iconSize / 2, iconSize,
                        iconColor, 1.7f);
                contentX += iconSize + (hasText ? Theme.px(7) : 0);
            }
            if (hasText) {
                g2.setFont(getFont());
                g2.setColor(textColor);
                FontMetrics metrics = g2.getFontMetrics();
                int baseline = centerY - metrics.getHeight() / 2 + metrics.getAscent();
                g2.drawString(stripHtml(text), contentX, baseline);
            }
            g2.dispose();
        }

        private PaintPair primaryColors(boolean enabled) {
            if (!enabled) {
                return new PaintPair(Theme.alpha(Theme.BORDER_STRONG, 160));
            }
            if (pressed) {
                return new PaintPair(Theme.accentPaint(getBounds(), true));
            }
            if (hover) {
                return new PaintPair(Theme.accentHoverPaint(getBounds()));
            }
            return new PaintPair(Theme.accentPaint(getBounds(), true));
        }
    }

    private static final class PaintPair {
        private final java.awt.Paint paint;

        PaintPair(java.awt.Paint paint) {
            this.paint = paint;
        }
    }

    // ------------------------------------------------------------------
    // misc
    // ------------------------------------------------------------------

    static JComponent constrain(JComponent component, int height) {
        Dimension preferred = component.getPreferredSize();
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(height, preferred.height)));
        return component;
    }

    /** Caps a component to its preferred height so BoxLayout rows stay compact. */
    static <T extends JComponent> T limitHeight(T component) {
        Dimension preferred = component.getPreferredSize();
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferred.height));
        return component;
    }

    static <T extends Component> T fullWidth(T component) {
        if (component instanceof JComponent) {
            ((JComponent) component).setAlignmentX(Component.LEFT_ALIGNMENT);
        }
        return component;
    }

    static JComponent smallSpinner(JSpinner spinner) {
        Dimension size = new Dimension(Theme.px(86), Theme.px(30));
        spinner.setPreferredSize(size);
        spinner.setMinimumSize(size);
        spinner.setMaximumSize(size);
        return spinner;
    }

    static JComponent smallField(JTextField field) {
        Dimension size = new Dimension(Theme.px(170), Theme.px(30));
        field.setPreferredSize(size);
        field.setMinimumSize(size);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));
        return field;
    }

    static JLabel spacer() {
        return new JLabel(" ");
    }

    static Insets insets(int top, int left, int bottom, int right) {
        return new Insets(top, left, bottom, right);
    }

    static int textHeight(java.awt.Font font) {
        return UIManager.getFont("defaultFont") == null ? 16 : 16;
    }
}
