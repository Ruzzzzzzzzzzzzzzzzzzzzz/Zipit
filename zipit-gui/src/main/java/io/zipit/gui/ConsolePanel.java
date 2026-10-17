package io.zipit.gui;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Colored, non-editable log console. */
final class ConsolePanel extends JPanel {

    private final JTextPane text = new JTextPane();
    private final SimpleAttributeSet warn = style(new Color(0xD97706));
    private final SimpleAttributeSet error = style(new Color(0xDC2626));
    private final SimpleAttributeSet success = style(new Color(0x059669));

    ConsolePanel() {
        super(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new javax.swing.BoxLayout(header, javax.swing.BoxLayout.Y_AXIS));
        JLabel title = Ui.h1("Console");
        JLabel subtitle = Ui.muted("Live engine output · warnings · errors");
        title.setAlignmentX(LEFT_ALIGNMENT);
        subtitle.setAlignmentX(LEFT_ALIGNMENT);
        header.add(title);
        header.add(javax.swing.Box.createVerticalStrut(3));
        header.add(subtitle);
        add(header, BorderLayout.NORTH);

        text.setEditable(false);
        text.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        text.setBackground(Ui.surface());
        text.setForeground(Ui.ink());
        text.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JScrollPane scroll = Ui.scroll(text);
        scroll.setBackground(Ui.surface());
        scroll.getViewport().setBackground(Ui.surface());
        Ui.CardPanel surface = new Ui.CardPanel();
        surface.setLayout(new BorderLayout());
        surface.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        surface.add(scroll, BorderLayout.CENTER);
        add(surface, BorderLayout.CENTER);

        JPanel actions = Ui.horizontal(8,
                button("Clear", this::clear),
                button("Copy", this::copy),
                button("Save log...", this::save));
        add(actions, BorderLayout.SOUTH);
    }

    void applyTheme() {
        text.setBackground(Ui.surface());
        text.setForeground(Ui.ink());
        repaint();
    }

    void append(final String level, final String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> append(level, message));
            return;
        }
        SimpleAttributeSet attribute;
        if ("ERROR".equals(level)) {
            attribute = error;
        } else if ("WARN".equals(level)) {
            attribute = warn;
        } else if ("DEBUG".equals(level)) {
            attribute = style(Ui.isDark() ? new Color(0x94A3B8) : new Color(0x64748B));
        } else if ("SUCCESS".equals(level)) {
            attribute = success;
        } else {
            attribute = style(Ui.isDark() ? new Color(0xE2E8F0) : new Color(0x334155));
        }
        StyledDocument document = text.getStyledDocument();
        try {
            document.insertString(document.getLength(), "[" + level + "] " + message + "\n", attribute);
            text.setCaretPosition(document.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    void clear() {
        text.setText("");
    }

    private void copy() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text.getText()), null);
    }

    private void save() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("zipit-log.txt"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.write(chooser.getSelectedFile().toPath(),
                    text.getText().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            append("ERROR", "Unable to save log: " + e.getMessage());
        }
    }

    private static JButton button(String text, Runnable action) {
        JButton button = Ui.secondary(text);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static SimpleAttributeSet style(Color color) {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, color);
        StyleConstants.setFontFamily(set, java.awt.Font.MONOSPACED);
        StyleConstants.setFontSize(set, 12);
        return set;
    }
}
