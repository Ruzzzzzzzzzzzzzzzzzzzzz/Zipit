package io.zipit.gui;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/** Modern animated switch toggle plus the labelled configuration row built on it. */
final class Switch extends JToggleButton {

    private float position;
    private Timer animator;
    private boolean hover;

    Switch(boolean selected) {
        this(selected, false);
    }

    Switch(boolean selected, boolean compact) {
        super();
        setOpaque(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setSelected(selected);
        position = selected ? 1f : 0f;
        Dimension size = compact
                ? new Dimension(Theme.px(36), Theme.px(20))
                : new Dimension(Theme.px(42), Theme.px(24));
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

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        animateTo(selected ? 1f : 0f);
    }

    private void animateTo(final float target) {
        if (animator != null) {
            animator.stop();
        }
        animator = new Timer(15, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                float delta = target - position;
                if (Math.abs(delta) < 0.03f) {
                    position = target;
                    ((Timer) e.getSource()).stop();
                } else {
                    position += delta * 0.28f;
                }
                repaint();
            }
        });
        animator.setInitialDelay(0);
        animator.start();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth();
        int height = getHeight();
        boolean enabled = isEnabled();
        int arc = Theme.px(4);
        boolean on = position > 0.5f;

        Color off = hover ? Theme.CARD_HOVER : Theme.CARD_ALT;
        if (on) {
            g2.setColor(enabled ? Theme.ACCENT : Theme.alpha(Theme.ACCENT, 120));
        } else {
            g2.setColor(off);
        }
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, arc, arc));
        g2.setColor(on ? Theme.alpha(Theme.ACCENT2, 180) : Theme.BORDER_STRONG);
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, arc, arc));

        double knobSize = height - Theme.px(5);
        double travel = width - knobSize - Theme.px(5);
        double knobX = Theme.px(2.5f) + travel * Math.max(0f, Math.min(1f, position));
        double knobY = (height - knobSize) / 2.0;
        g2.setColor(on ? (Theme.dark ? new Color(0x1A1610) : new Color(0xFBF8F1))
                : (Theme.dark ? new Color(0x8C8170) : new Color(0xFBF8F1)));
        g2.fill(new RoundRectangle2D.Double(knobX, knobY, knobSize, knobSize,
                Theme.px(2), Theme.px(2)));
        g2.setColor(Theme.alpha(Theme.BORDER_STRONG, 190));
        g2.setStroke(new java.awt.BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(knobX + 0.5, knobY + 0.5, knobSize - 1, knobSize - 1,
                Theme.px(2), Theme.px(2)));

        if (hasFocus()) {
            g2.setColor(Theme.alpha(Theme.ACCENT, 150));
            g2.setStroke(new java.awt.BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Double(-1.5, -1.5, width + 2, height + 2, arc, arc));
        }
        g2.dispose();
    }
}

/**
 * A titled configuration row: bold label, muted explanation, an optional inline
 * control and a switch bound to the underlying form model.
 */
final class SwitchRow extends JPanel {

    SwitchRow(JCheckBoxModel model) {
        this(model.title, model.description, model.checkBox, null, model.compact);
    }

    SwitchRow(String title, String description, javax.swing.JCheckBox checkBox) {
        this(title, description, checkBox, null, false);
    }

    SwitchRow(String title, String description, javax.swing.JCheckBox checkBox,
              JComponent accessory) {
        this(title, description, checkBox, accessory, false);
    }

    SwitchRow(String title, String description, final javax.swing.JCheckBox checkBox,
              JComponent accessory, boolean compact) {
        super(new BorderLayout(Theme.px(12), 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.px(5), Theme.px(2),
                Theme.px(5), Theme.px(2)));

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        JLabel label = Ui.bold(title);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(label);
        if (description != null && !description.isEmpty()) {
            JLabel hint = Ui.muted(description);
            hint.setFont(Theme.font(java.awt.Font.PLAIN, 12f));
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(javax.swing.Box.createVerticalStrut(Theme.px(2)));
            text.add(hint);
        }
        add(text, BorderLayout.CENTER);

        final Switch toggle = new Switch(checkBox.isSelected(), compact);
        toggle.setToolTipText(title);
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new javax.swing.BoxLayout(right, javax.swing.BoxLayout.X_AXIS));
        if (accessory != null) {
            accessory.setAlignmentY(Component.CENTER_ALIGNMENT);
            right.add(accessory);
            right.add(javax.swing.Box.createHorizontalStrut(Theme.px(10)));
        }
        toggle.setAlignmentY(Component.CENTER_ALIGNMENT);
        right.add(toggle);
        add(right, BorderLayout.EAST);

        final boolean[] syncing = {false};
        toggle.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (syncing[0]) {
                    return;
                }
                syncing[0] = true;
                try {
                    checkBox.setSelected(toggle.isSelected());
                } finally {
                    syncing[0] = false;
                }
            }
        });
        checkBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (syncing[0]) {
                    return;
                }
                syncing[0] = true;
                try {
                    if (toggle.isSelected() != checkBox.isSelected()) {
                        toggle.setSelected(checkBox.isSelected());
                    }
                } finally {
                    syncing[0] = false;
                }
            }
        });

        // Clicking the text also flips the switch, like a native settings row.
        MouseAdapter clickToToggle = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    toggle.doClick();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                setCursor(Cursor.getDefaultCursor());
            }
        };
        text.addMouseListener(clickToToggle);
        label.addMouseListener(clickToToggle);
    }

    /**
     * Row with a trailing control but no switch (numeric settings, selects, …).
     */
    static JPanel control(String title, String description, JComponent... controls) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(12), 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(Theme.px(5), Theme.px(2),
                Theme.px(5), Theme.px(2)));
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
        JLabel label = Ui.bold(title);
        text.add(label);
        if (description != null && !description.isEmpty()) {
            JLabel hint = Ui.muted(description);
            hint.setFont(Theme.font(java.awt.Font.PLAIN, 12f));
            text.add(javax.swing.Box.createVerticalStrut(Theme.px(2)));
            text.add(hint);
        }
        row.add(text, BorderLayout.CENTER);
        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new javax.swing.BoxLayout(right, javax.swing.BoxLayout.X_AXIS));
        for (int i = 0; i < controls.length; i++) {
            if (i > 0) {
                right.add(javax.swing.Box.createHorizontalStrut(Theme.px(8)));
            }
            JComponent control = controls[i];
            control.setAlignmentY(Component.CENTER_ALIGNMENT);
            right.add(control);
        }
        row.add(right, BorderLayout.EAST);
        return row;
    }

    /** Simple record used by the I18n.t("ui.one.liner") constructor. */
    static final class JCheckBoxModel {
        final String title;
        final String description;
        final javax.swing.JCheckBox checkBox;
        boolean compact;

        JCheckBoxModel(String title, String description, javax.swing.JCheckBox checkBox) {
            this.title = title;
            this.description = description;
            this.checkBox = checkBox;
        }
    }

    /** Convenience builder used by pages when the row has a dedicated model. */
    static SwitchRow of(String title, String description, javax.swing.JCheckBox checkBox) {
        return new SwitchRow(title, description, checkBox);
    }
}
