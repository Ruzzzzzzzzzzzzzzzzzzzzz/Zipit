package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Polished terminal-style log view with level colors, filtering, search,
 * copy and export.
 */
final class TerminalPanel extends JPanel {

    private static final Color TERMINAL_TEXT = new Color(0xDCD3C2);
    private static final Color TERMINAL_TIME = new Color(0x8A7E6C);
    private static final Color TERMINAL_DEBUG = new Color(0x9A8E7C);
    private static final Color TERMINAL_BORDER = new Color(0x2E2820);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_ENTRIES = 3000;

    static final class Entry {
        final String time;
        final String level;
        final String message;

        Entry(String time, String level, String message) {
            this.time = time;
            this.level = level == null ? "INFO" : level;
            this.message = message == null ? "" : message;
        }
    }

    private final boolean compact;
    private final List<Entry> entries = new ArrayList<Entry>();
    private final JTextPane pane = new JTextPane();
    private final JTextField search;
    private java.util.function.Supplier<Color> counterTone = () -> Theme.FAINT;
    private final JLabel counter = Ui.colored(I18n.t("terminal.lineCount"), () -> counterTone.get(), 12f,
            Font.PLAIN);
    private final List<Chip> levelChips = new ArrayList<Chip>();
    private String levelFilter = I18n.t("terminal.levelAll");
    private boolean follow = true;

    TerminalPanel(boolean compact) {
        super(new BorderLayout());
        this.compact = compact;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        pane.setEditable(false);
        pane.setOpaque(true);
        pane.setBackground(Theme.TERMINAL);
        pane.setBorder(BorderFactory.createEmptyBorder(Theme.px(10), Theme.px(13),
                Theme.px(12), Theme.px(13)));
        pane.setFont(Theme.mono(13f));

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.TERMINAL);
        scroll.setBackground(Theme.TERMINAL);
        scroll.getVerticalScrollBar().setUnitIncrement(Theme.px(18));
        scroll.getVerticalScrollBar().setOpaque(false);

        JPanel header = new JPanel(new BorderLayout(Theme.px(10), 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(Theme.px(9), Theme.px(12),
                Theme.px(9), Theme.px(8)));

        JPanel leads = new JPanel();
        leads.setOpaque(false);
        leads.setLayout(new BoxLayout(leads, BoxLayout.X_AXIS));
        leads.add(trafficLight(new Color(0xF87171)));
        leads.add(Box.createHorizontalStrut(Theme.px(6)));
        leads.add(trafficLight(new Color(0xFBBF24)));
        leads.add(Box.createHorizontalStrut(Theme.px(6)));
        leads.add(trafficLight(new Color(0x34D399)));
        leads.add(Box.createHorizontalStrut(Theme.px(10)));
        JLabel title = Ui.colored(compact ? I18n.t("terminal.liveOutput") : I18n.t("terminal.title"), () -> Theme.ACCENT, 12f, java.awt.Font.BOLD);
        title.setFont(Theme.mono(12f));
        title.setForeground(TERMINAL_TIME);
        leads.add(title);
        header.add(leads, BorderLayout.WEST);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));

        search = new JTextField(14);
        Ui.styleField(search);
        Ui.placeholder(search, I18n.t("terminal.filter"));
        search.setFont(Theme.font(Font.PLAIN, 13f));
        search.setPreferredSize(new Dimension(Theme.px(150), Theme.px(28)));
        search.setMaximumSize(new Dimension(Theme.px(170), Theme.px(28)));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                rebuild();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                rebuild();
            }
        });

        if (!compact) {
            actions.add(search);
            actions.add(Box.createHorizontalStrut(Theme.px(8)));
            for (String level : new String[]{I18n.t("terminal.levelAll"), "INFO", "WARN", "ERROR"}) {
                final String value = level;
                final Chip chip = new Chip(level);
                if (I18n.t("terminal.levelAll").equals(level)) {
                    chip.setSelected(true);
                }
                chip.addActionListener(e -> {
                    levelFilter = value;
                    for (Chip other : levelChips) {
                        other.setSelected(other == chip);
                    }
                    rebuild();
                });
                levelChips.add(chip);
                chip.setAlignmentY(java.awt.Component.CENTER_ALIGNMENT);
                actions.add(chip);
                actions.add(Box.createHorizontalStrut(Theme.px(4)));
            }
            actions.add(Box.createHorizontalStrut(Theme.px(4)));
        }

        JButton copy = Ui.iconButton("copy", I18n.t("terminal.copy"), 30);
        copy.addActionListener(e -> copy());
        JButton save = Ui.iconButton("download", I18n.t("terminal.export"), 30);
        save.addActionListener(e -> export());
        JButton clear = Ui.iconButton("trash", I18n.t("terminal.clear"), 30);
        clear.addActionListener(e -> clear());
        actions.add(copy);
        actions.add(Box.createHorizontalStrut(Theme.px(2)));
        actions.add(save);
        actions.add(Box.createHorizontalStrut(Theme.px(2)));
        actions.add(clear);
        header.add(actions, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        if (!compact) {
            JPanel footer = new JPanel(new BorderLayout(Theme.px(10), 0));
            footer.setOpaque(false);
            footer.setBorder(BorderFactory.createEmptyBorder(Theme.px(7), Theme.px(13),
                    Theme.px(8), Theme.px(12)));
            counter.setFont(Theme.font(Font.PLAIN, 12f));
            footer.add(counter, BorderLayout.WEST);
            Chip followChip = new Chip(I18n.t("terminal.autoScroll"));
            followChip.setSelected(true);
            followChip.addActionListener(e -> follow = followChip.isSelected());
            JPanel right = new JPanel();
            right.setOpaque(false);
            right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
            right.add(followChip);
            footer.add(right, BorderLayout.EAST);
            add(footer, BorderLayout.SOUTH);
        }
    }

    private static JComponentDot trafficLight(Color color) {
        return new JComponentDot(color);
    }

    /** Tiny painted dot used in the terminal chrome. */
    private static final class JComponentDot extends JPanel {
        private final Color color;

        JComponentDot(Color color) {
            this.color = color;
            setOpaque(false);
            Dimension size = new Dimension(Theme.px(9), Theme.px(9));
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.alpha(color, 220));
            g2.fillOval(0, 0, getWidth(), getHeight());
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------
    // public API
    // ------------------------------------------------------------------

    void append(final String level, final String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    append(level, message);
                }
            });
            return;
        }
        Entry entry = new Entry(LocalTime.now().format(TIME),
                level == null ? "INFO" : level.toUpperCase(Locale.ROOT), message);
        entries.add(entry);
        if (entries.size() > MAX_ENTRIES) {
            entries.subList(0, entries.size() - MAX_ENTRIES).clear();
            rebuild();
            return;
        }
        if (!matches(entry)) {
            updateCounter();
            return;
        }
        insert(entry);
        updateCounter();
        if (follow) {
            pane.setCaretPosition(pane.getDocument().getLength());
        }
    }

    void clear() {
        entries.clear();
        pane.setText("");
        updateCounter();
    }

    List<Entry> entries() {
        return new ArrayList<Entry>(entries);
    }

    int count(String level) {
        int count = 0;
        for (Entry entry : entries) {
            if (level == null || level.equalsIgnoreCase(entry.level)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // rendering
    // ------------------------------------------------------------------

    private boolean matches(Entry entry) {
        if (!I18n.t("terminal.levelAll").equals(levelFilter)) {
            if ("INFO".equals(levelFilter)) {
                if (!"INFO".equals(entry.level) && !"SUCCESS".equals(entry.level)) {
                    return false;
                }
            } else if (!levelFilter.equals(entry.level)) {
                return false;
            }
        }
        String query = search == null ? "" : search.getText().trim();
        return query.isEmpty()
                || entry.message.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private void rebuild() {
        StyledDocument document = pane.getStyledDocument();
        try {
            document.remove(0, document.getLength());
        } catch (BadLocationException ignored) {
            pane.setText("");
        }
        for (Entry entry : entries) {
            if (matches(entry)) {
                insert(entry);
            }
        }
        updateCounter();
        if (follow) {
            pane.setCaretPosition(pane.getDocument().getLength());
        }
    }

    private void insert(Entry entry) {
        StyledDocument document = pane.getStyledDocument();
        SimpleAttributeSet timeStyle = style(TERMINAL_TIME, false);
        SimpleAttributeSet levelStyle = style(levelColor(entry.level), true);
        SimpleAttributeSet messageStyle = style(messageColor(entry.level), false);
        String level = entry.level;
        while (level.length() < 7) {
            level = level + " ";
        }
        try {
            document.insertString(document.getLength(), entry.time + "  ", timeStyle);
            document.insertString(document.getLength(), level + " ", levelStyle);
            document.insertString(document.getLength(), entry.message + "\n", messageStyle);
        } catch (BadLocationException ignored) {
            // Document was reset concurrently; the next rebuild will resync.
        }
    }

    private void updateCounter() {
        int errors = count("ERROR");
        int warnings = count("WARN");
        counter.setText(I18n.t("terminal.summary", entries.size(), warnings, errors));
        final Color tone = errors > 0 ? Theme.DANGER : warnings > 0 ? Theme.WARNING : Theme.FAINT;
        counterTone = () -> tone;
        counter.repaint();
    }

    private static Color levelColor(String level) {
        if ("ERROR".equals(level)) {
            return Theme.DANGER;
        }
        if ("WARN".equals(level)) {
            return Theme.WARNING;
        }
        if ("SUCCESS".equals(level)) {
            return Theme.SUCCESS;
        }
        if ("DEBUG".equals(level)) {
            return TERMINAL_DEBUG;
        }
        return Theme.INFO;
    }

    private static Color messageColor(String level) {
        if ("ERROR".equals(level)) {
            return new Color(0xE0A79E);
        }
        if ("WARN".equals(level)) {
            return new Color(0xDCC27E);
        }
        if ("SUCCESS".equals(level)) {
            return new Color(0xA9BFA0);
        }
        if ("DEBUG".equals(level)) {
            return TERMINAL_DEBUG;
        }
        return TERMINAL_TEXT;
    }

    private static SimpleAttributeSet style(Color color, boolean bold) {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, color);
        StyleConstants.setFontFamily(set, Theme.mono(13f).getFamily());
        StyleConstants.setFontSize(set, Theme.mono(13f).getSize());
        StyleConstants.setBold(set, bold);
        return set;
    }

    private String fullText() {
        StringBuilder out = new StringBuilder();
        for (Entry entry : entries) {
            if (matches(entry)) {
                out.append(entry.time).append("  ").append(entry.level).append(' ')
                        .append(entry.message).append('\n');
            }
        }
        return out.toString();
    }

    private void copy() {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(fullText()), null);
    }

    private void export() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("zipit-run.log"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.write(chooser.getSelectedFile().toPath(),
                    fullText().getBytes(StandardCharsets.UTF_8));
            append("SUCCESS", I18n.t("ui.log.exported.to") + chooser.getSelectedFile().getAbsolutePath());
        } catch (Exception e) {
            append("ERROR", I18n.t("ui.unable.to.export.log") + e.getMessage());
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth() - 1;
        int height = getHeight() - 1;
        int radius = Theme.px(6);
        g2.setColor(new Color(0, 0, 0, 60));
        g2.fill(new RoundRectangle2D.Double(1, 2, width - 1, height - 1, radius, radius));
        g2.setColor(Theme.TERMINAL);
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, radius, radius));
        g2.setColor(TERMINAL_BORDER);
        g2.setStroke(new BasicStroke(1f));
        g2.draw(new RoundRectangle2D.Double(0.5, 0.5, width - 1, height - 1, radius, radius));
        g2.dispose();
        super.paintComponent(g);
    }
}
