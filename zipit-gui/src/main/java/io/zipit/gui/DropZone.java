package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Hero drop target for the input artifact, with drag &amp; drop support. */
final class DropZone extends JPanel {

    private final GuiState state;
    private final Runnable browseAction;
    private final JLabel titleLabel;
    private final JLabel subtitleLabel;
    private final JButton browseButton;
    private final JLabel fileBadge;
    private boolean hover;
    private int scanToken;

    DropZone(GuiState state, Runnable browseAction) {
        super(new BorderLayout(0, 10));
        this.state = state;
        this.browseAction = browseAction;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.px(20), Theme.px(24),
                Theme.px(18), Theme.px(24)));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JLabel icon = new JLabel();
        icon.setIcon(new javax.swing.Icon() {
            @Override
            public int getIconWidth() {
                return Theme.px(56);
            }

            @Override
            public int getIconHeight() {
                return Theme.px(56);
            }

            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Theme.px(56);
                g2.setColor(Theme.CARD_ALT);
                g2.fillRoundRect(x, y, size, size, Theme.px(6), Theme.px(6));
                g2.setColor(Theme.BORDER_STRONG);
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(x, y, size - 1, size - 1, Theme.px(6), Theme.px(6));
                Icons.paint(g2, "upload", x + Theme.px(15), y + Theme.px(15),
                        size - Theme.px(30), Theme.ACCENT, 1.8f);
                g2.dispose();
            }
        });
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(icon);
        center.add(Box.createVerticalStrut(Theme.px(9)));

        titleLabel = Ui.h2(I18n.t("ui.drop.a.jar.to.protect"));
        titleLabel.setFont(Theme.serifFont(Font.BOLD, 18f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(titleLabel);
        center.add(Box.createVerticalStrut(Theme.px(3)));

        subtitleLabel = Ui.muted(I18n.t("ui.jar.war.zip.or.click.to.browse"));
        subtitleLabel.setFont(Theme.font(Font.PLAIN, 13f));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        center.add(subtitleLabel);
        center.add(Box.createVerticalStrut(Theme.px(12)));

        browseButton = Ui.button(I18n.t("ui.browse.files"), "folder");
        browseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        browseButton.addActionListener(e -> browseAction.run());
        center.add(browseButton);
        center.add(Box.createVerticalStrut(Theme.px(10)));

        JPanel formats = new JPanel();
        formats.setOpaque(false);
        formats.setLayout(new BoxLayout(formats, BoxLayout.X_AXIS));
        formats.setAlignmentX(Component.CENTER_ALIGNMENT);
        String[] accepted = {"JAR", "WAR", "ZIP"};
        for (int i = 0; i < accepted.length; i++) {
            if (i > 0) {
                formats.add(Box.createHorizontalStrut(Theme.px(6)));
            }
            JLabel chip = Ui.pill(accepted[i], () -> Theme.alpha(Theme.BORDER_STRONG, 70),
                    () -> Theme.MUTED);
            chip.setFont(Theme.font(Font.BOLD, 12f));
            formats.add(chip);
        }
        center.add(formats);
        add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setOpaque(false);
        footer.setLayout(new BoxLayout(footer, BoxLayout.X_AXIS));
        fileBadge = Ui.faint(I18n.t("ui.no.file.selected"));
        fileBadge.setFont(Theme.font(Font.PLAIN, 12f));
        footer.add(fileBadge);
        footer.add(Box.createHorizontalGlue());
        footer.add(Ui.faint(I18n.t("ui.drag.drop.anywhere.on.this.card")));
        add(footer, BorderLayout.SOUTH);

        setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    Transferable transferable = support.getTransferable();
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) transferable.getTransferData(
                            DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        if (isArchive(file.getName())) {
                            accept(file.toPath());
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                    // Fall through to the generic rejection path.
                }
                return false;
            }
        });

        MouseAdapter browseOnClick = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    browseAction.run();
                }
            }

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
        };
        addMouseListener(browseOnClick);
        center.addMouseListener(browseOnClick);
        icon.addMouseListener(browseOnClick);
        titleLabel.addMouseListener(browseOnClick);
        subtitleLabel.addMouseListener(browseOnClick);

        state.addChangeListener(new Runnable() {
            @Override
            public void run() {
                refresh();
            }
        });
        refresh();
    }

    private static boolean isArchive(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".war") || lower.endsWith(".zip");
    }

    private void accept(Path path) {
        state.setInput(path);
        refresh();
    }

    /** Updates the empty/filled presentation and scans the archive in the background. */
    void refresh() {
        String text = state.input.getText().trim();
        if (text.isEmpty()) {
            titleLabel.setText(I18n.t("ui.drop.a.jar.to.protect"));
            subtitleLabel.setText(I18n.t("ui.jar.war.zip.or.click.to.browse"));
            browseButton.setText(I18n.t("ui.browse.files"));
            fileBadge.setText(I18n.t("ui.no.file.selected"));
            return;
        }
        Path path;
        try {
            path = Paths.get(text);
        } catch (RuntimeException invalidPath) {
            titleLabel.setText(I18n.t("ui.select.a.jar.file"));
            subtitleLabel.setText(I18n.t("ui.the.typed.path.is.not.valid"));
            browseButton.setText(I18n.t("ui.browse.files"));
            fileBadge.setText(I18n.t("ui.no.file.selected"));
            return;
        }
        String name = path.getFileName() == null ? text : path.getFileName().toString();
        titleLabel.setText(name);
        browseButton.setText(I18n.t("ui.replace.jar"));
        final int token = ++scanToken;
        subtitleLabel.setText(I18n.t("ui.reading.artifact"));
        new SwingWorker<long[], Void>() {
            @Override
            protected long[] doInBackground() {
                long size = -1;
                long classes = -1;
                try {
                    if (Files.exists(path)) {
                        size = Files.size(path);
                        try (ZipFile zip = new ZipFile(path.toFile())) {
                            long count = 0;
                            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                            while (entries.hasMoreElements()) {
                                ZipEntry entry = entries.nextElement();
                                if (!entry.isDirectory() && entry.getName().endsWith(".class")) {
                                    count++;
                                }
                            }
                            classes = count;
                        }
                    }
                } catch (Exception ignored) {
                    // Keep the placeholders; the engine reports real errors on run.
                }
                return new long[]{size, classes};
            }

            @Override
            protected void done() {
                if (token != scanToken) {
                    return;
                }
                try {
                    long[] info = get();
                    StringBuilder line = new StringBuilder();
                    if (info[0] >= 0) {
                        line.append(Ui.humanBytes(info[0]));
                    } else {
                        line.append(I18n.t("ui.file.not.found"));
                    }
                    if (info[1] >= 0) {
                        line.append("  ·  ").append(info[1]).append(" classes");
                    }
                    subtitleLabel.setText(line.toString());
                    fileBadge.setText(I18n.t("ui.ready.to.protect") + name);
                } catch (Exception e) {
                    subtitleLabel.setText(name);
                }
            }
        }.execute();
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(Theme.px(430), Theme.px(242));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth() - 1;
        int height = getHeight() - 1;
        int radius = Theme.px(6);
        boolean filled = !state.input.getText().trim().isEmpty();

        g2.setColor(Theme.alpha(Theme.SHADOW, Theme.dark ? 55 : 16));
        g2.fill(new RoundRectangle2D.Double(1, 2, width - 1, height - 1, radius, radius));
        g2.setColor(hover ? Theme.CARD_HOVER : Theme.CARD);
        g2.fill(new RoundRectangle2D.Double(0, 0, width, height, radius, radius));

        // Antique dashed rule invites a drop without glowing.
        g2.setColor(hover ? Theme.ACCENT : Theme.BORDER_STRONG);
        g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10f, new float[]{Theme.px(6), Theme.px(4)}, 0f));
        g2.draw(new RoundRectangle2D.Double(1.5, 1.5, width - 3, height - 3, radius, radius));

        if (filled) {
            g2.setColor(Theme.SUCCESS);
            g2.fill(new RoundRectangle2D.Double(width - Theme.px(24), Theme.px(15),
                    Theme.px(7), Theme.px(7), Theme.px(7), Theme.px(7)));
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
