package io.zipit.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** Shared visual helpers for the Zipit desktop client. */
final class Ui {

    static final Color ACCENT = new Color(0x3B82F6);
    static final Color ACCENT_DARK = new Color(0x2563EB);
    static final Color PURPLE = new Color(0x7C3AED);
    static final Color TEAL = new Color(0x0D9488);
    static final Color SUCCESS = new Color(0x10B981);
    static final Color WARNING = new Color(0xF59E0B);
    static final Color DANGER = new Color(0xEF4444);
    static final Color INDIGO = new Color(0x6366F1);

    private static boolean dark;

    private Ui() {
    }

    static void install() {
        FlatLightLaf.setup();
        Font base = new Font("Segoe UI", Font.PLAIN, 13);
        UIManager.put("defaultFont", base);
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Button.arc", 12);
        UIManager.put("Component.arc", 12);
        UIManager.put("TextComponent.arc", 10);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));
        UIManager.put("Tree.rowHeight", 24);
        UIManager.put("Tree.paintLines", false);
        UIManager.put("Tree.showHorizontalLines", false);
    }

    static boolean isDark() {
        return dark;
    }

    static void toggleTheme(Window window) {
        dark = !dark;
        try {
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
            UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 13));
            UIManager.put("Component.focusColor", ACCENT);
        } catch (Exception ignored) {
        }
        if (window != null) {
            SwingUtilities.updateComponentTreeUI(window);
            window.repaint();
        }
    }

    static Color pageBackground() {
        return dark ? new Color(0x0B1220) : new Color(0xF3F6FC);
    }

    static Color surface() {
        return dark ? new Color(0x111827) : Color.WHITE;
    }

    static Color sidebarColor() {
        return dark ? new Color(0x0F172A) : Color.WHITE;
    }

    static Color line() {
        return dark ? new Color(0x243041) : new Color(0xE2E8F0);
    }

    static Color ink() {
        return dark ? new Color(0xE5E7EB) : new Color(0x0F172A);
    }

    static Color mutedInk() {
        return dark ? new Color(0x94A3B8) : new Color(0x64748B);
    }

    static Color accentSoft() {
        return dark ? new Color(0x1E293B) : new Color(0xE8F0FE);
    }

    static JLabel h1(String text) {
        JLabel label = new InkLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 26f));
        return label;
    }

    static JLabel h2(String text) {
        JLabel label = new InkLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15.5f));
        return label;
    }

    static JLabel muted(String text) {
        return new MutedLabel(text);
    }

    static JButton primary(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setBackground(ACCENT);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        return button;
    }

    static JButton secondary(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 13f));
        return button;
    }

    static JButton nav(String text) {
        return nav(text, null);
    }

    static JButton nav(String text, Icon icon) {
        JButton button = new JButton(text);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setIcon(icon);
        button.setIconTextGap(12);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 13f));
        button.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        button.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 42));
        button.setPreferredSize(new java.awt.Dimension(200, 42));
        button.putClientProperty("JButton.buttonType", "roundRect");
        return button;
    }

    static void styleField(JComponent component) {
        component.putClientProperty("JComponent.roundRect", true);
    }

    /** Prevents BoxLayout from stretching single-line controls vertically. */
    static void constrainHeight(JComponent component) {
        if (component instanceof javax.swing.JTextField
                || component instanceof javax.swing.JSpinner
                || component instanceof javax.swing.JComboBox) {
            java.awt.Dimension preferred = component.getPreferredSize();
            int height = Math.max(30, preferred.height);
            component.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, height));
            component.setPreferredSize(new java.awt.Dimension(preferred.width, height));
        }
    }

    static JScrollPane scroll(Component component) {
        JScrollPane pane = new JScrollPane(component);
        pane.setBorder(BorderFactory.createEmptyBorder());
        pane.getVerticalScrollBar().setUnitIncrement(16);
        return pane;
    }

    static JPanel card(String title, String subtitle, JComponent body) {
        CardPanel card = new CardPanel();
        card.setLayout(new java.awt.BorderLayout(0, 10));
        card.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
        JLabel heading = h2(title);
        header.add(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            header.add(javax.swing.Box.createVerticalStrut(2));
            header.add(muted(subtitle));
        }
        card.add(header, java.awt.BorderLayout.NORTH);
        card.add(body, java.awt.BorderLayout.CENTER);
        return card;
    }

    static JPanel horizontal(int gap, Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.X_AXIS));
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                panel.add(javax.swing.Box.createHorizontalStrut(gap));
            }
            panel.add(components[i]);
        }
        return panel;
    }

    static JPanel vertical(int gap, Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                panel.add(javax.swing.Box.createVerticalStrut(gap));
            }
            panel.add(components[i]);
        }
        return panel;
    }

    static String humanBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /** Scales an image with high-quality interpolation for the header logo. */
    static java.awt.Image scaleImage(java.awt.Image source, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(source, 0, 0, size, size, null);
        g2.dispose();
        return out;
    }

    /** Compact rounded badge used in the header. */
    static JLabel pill(String text, Color background, Color foreground) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(background);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setForeground(foreground);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        return label;
    }

    /** Small vector glyph for the sidebar navigation. */
    static Icon navIcon(String kind, Color color) {
        return new NavIcon(kind, color);
    }

    /** Rounded card surface with a soft shadow that follows the active theme. */
    static class CardPanel extends JPanel {
        CardPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth() - 1;
            int height = getHeight() - 1;
            g2.setColor(dark ? new Color(0, 0, 0, 60) : new Color(15, 23, 42, 18));
            g2.fillRoundRect(1, 2, width - 1, height - 1, 18, 18);
            g2.setColor(surface());
            g2.fillRoundRect(0, 0, width, height - 2, 18, 18);
            g2.setColor(line());
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(0, 0, width, height - 2, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Header with a subtle blue-violet gradient and soft light accents. */
    static final class GradientPanel extends JPanel {
        GradientPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new java.awt.GradientPaint(0, 0, ACCENT_DARK, getWidth(), 0, PURPLE));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(new Color(255, 255, 255, 16));
            g2.fillOval(-90, -120, 320, 320);
            g2.fillOval(getWidth() - 210, -90, 300, 300);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Makes a component clickable without a visible button chrome. */
    static void onClick(JComponent component, Runnable action) {
        component.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });
    }

    private static final class InkLabel extends JLabel {
        InkLabel(String text) {
            super(text);
        }

        @Override
        public Color getForeground() {
            return ink();
        }
    }

    private static final class MutedLabel extends JLabel {
        MutedLabel(String text) {
            super(text);
            setFont(getFont().deriveFont(Font.PLAIN, 12f));
        }

        @Override
        public Color getForeground() {
            return mutedInk();
        }
    }

    private static final class NavIcon implements Icon {
        private final String kind;
        private final Color color;

        NavIcon(String kind, Color color) {
            this.kind = kind == null ? "" : kind;
            this.color = color == null ? ACCENT : color;
        }

        @Override
        public int getIconWidth() {
            return 18;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }

        @Override
        public void paintIcon(Component component, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if ("dashboard".equals(kind)) {
                g2.fillRoundRect(x + 1, y + 2, 6, 6, 2, 2);
                g2.fillRoundRect(x + 10, y + 2, 6, 6, 2, 2);
                g2.fillRoundRect(x + 1, y + 10, 6, 6, 2, 2);
                g2.fillRoundRect(x + 10, y + 10, 6, 6, 2, 2);
            } else if ("transforms".equals(kind)) {
                g2.drawLine(x + 2, y + 4, x + 16, y + 4);
                g2.drawLine(x + 2, y + 9, x + 16, y + 9);
                g2.drawLine(x + 2, y + 14, x + 16, y + 14);
                g2.fillOval(x + 5, y + 2, 4, 4);
                g2.fillOval(x + 11, y + 7, 4, 4);
                g2.fillOval(x + 3, y + 12, 4, 4);
            } else if ("rules".equals(kind)) {
                g2.drawRoundRect(x + 3, y + 1, 12, 16, 3, 3);
                g2.drawLine(x + 6, y + 6, x + 12, y + 6);
                g2.drawLine(x + 6, y + 9, x + 12, y + 9);
                g2.drawLine(x + 6, y + 12, x + 9, y + 12);
            } else if ("enterprise".equals(kind)) {
                int[] xs = {x + 9, x + 16, x + 9, x + 2};
                int[] ys = {y + 1, y + 9, y + 17, y + 9};
                g2.drawPolygon(xs, ys, 4);
                g2.drawLine(x + 6, y + 9, x + 12, y + 9);
            } else if ("console".equals(kind)) {
                g2.drawRoundRect(x + 1, y + 2, 16, 14, 3, 3);
                g2.drawLine(x + 4, y + 6, x + 7, y + 9);
                g2.drawLine(x + 7, y + 9, x + 4, y + 12);
                g2.drawLine(x + 9, y + 12, x + 13, y + 12);
            } else {
                g2.fillOval(x + 5, y + 5, 8, 8);
            }
            g2.dispose();
        }
    }
}
