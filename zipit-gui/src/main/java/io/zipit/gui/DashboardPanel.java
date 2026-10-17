package io.zipit.gui;

import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.Stats;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.Box;
import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Landing page with input selection, run summary and output inventory. */
final class DashboardPanel extends JPanel {

    private final GuiState state;
    private final ZipitFrame frame;
    private final JTextField input;
    private final JTextField output;
    private final Tile classesTile = new Tile("Classes", Ui.ACCENT);
    private final Tile resourcesTile = new Tile("Resources", Ui.PURPLE);
    private final Tile durationTile = new Tile("Duration", Ui.TEAL);
    private final Tile stringsTile = new Tile("Strings encrypted", Ui.WARNING);
    private final Tile flowTile = new Tile("Flow methods", Ui.INDIGO);
    private final Tile riskTile = new Tile("Reflection sites", Ui.DANGER);
    private final Tile licenseTile = new Tile("License", Ui.SUCCESS);
    private final Tile statusTile = new Tile("Status", Ui.TEAL);
    private final JTree outputTree = new JTree(new DefaultMutableTreeNode("No output yet"));
    private final JScrollPane outputScroll = Ui.scroll(outputTree);

    DashboardPanel(GuiState state, ZipitFrame frame) {
        super(new BorderLayout(0, 16));
        this.state = state;
        this.frame = frame;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        input = state.input;
        output = state.output;

        JPanel top = new JPanel(new BorderLayout(0, 14));
        top.setOpaque(false);
        top.add(buildDropZone(), BorderLayout.NORTH);
        top.add(buildFileRows(), BorderLayout.CENTER);

        JPanel tiles = new JPanel(new GridLayout(0, 4, 12, 12));
        tiles.setOpaque(false);
        tiles.add(classesTile);
        tiles.add(resourcesTile);
        tiles.add(durationTile);
        tiles.add(stringsTile);
        tiles.add(flowTile);
        tiles.add(riskTile);
        tiles.add(licenseTile);
        tiles.add(statusTile);
        statusTile.setValue("Ready");

        outputTree.setRowHeight(23);
        outputTree.setFont(outputTree.getFont().deriveFont(12f));
        outputTree.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        outputScroll.setBackground(Ui.surface());
        outputScroll.getViewport().setBackground(Ui.surface());

        JPanel summary = new JPanel(new BorderLayout(0, 14));
        summary.setOpaque(false);
        summary.add(tiles, BorderLayout.NORTH);
        summary.add(Ui.card("Output inventory", "Protected classes and resources",
                outputScroll), BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);
        add(summary, BorderLayout.CENTER);
    }

    private JPanel buildDropZone() {
        DropZone zone = new DropZone();
        zone.setTransferHandler(new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            public boolean importData(TransferSupport support) {
                try {
                    Transferable transferable = support.getTransferable();
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        if (file.getName().toLowerCase().endsWith(".jar")) {
                            state.setInput(file.toPath());
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                }
                return false;
            }
        });
        Ui.onClick(zone, this::browseInput);
        return zone;
    }

    private static final class DropZone extends JPanel {
        private boolean hover;

        DropZone() {
            super(new GridBagLayout());
            setOpaque(false);
            setPreferredSize(new Dimension(100, 130));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            JPanel content = new JPanel();
            content.setOpaque(false);
            content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));
            JLabel icon = new JLabel(new UploadIcon());
            icon.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel title = Ui.h2("Drop a JAR to protect");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 16.5f));
            title.setAlignmentX(Component.CENTER_ALIGNMENT);
            JLabel subtitle = Ui.muted("or click to browse · JAR / ZIP / WAR");
            subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
            content.add(icon);
            content.add(Box.createVerticalStrut(7));
            content.add(title);
            content.add(Box.createVerticalStrut(3));
            content.add(subtitle);
            add(content);

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
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth() - 1;
            int height = getHeight() - 1;
            g2.setColor(hover ? Ui.accentSoft() : Ui.surface());
            g2.fillRoundRect(0, 0, width, height, 18, 18);
            g2.setColor(hover ? Ui.ACCENT : Ui.line());
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND, 0f, new float[]{7f, 5f}, 0f));
            g2.drawRoundRect(1, 1, width - 2, height - 2, 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static final class UploadIcon implements Icon {
        @Override
        public int getIconWidth() {
            return 46;
        }

        @Override
        public int getIconHeight() {
            return 46;
        }

        @Override
        public void paintIcon(Component component, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(Ui.ACCENT.getRed(), Ui.ACCENT.getGreen(), Ui.ACCENT.getBlue(), 38));
            g2.fillOval(x, y, 46, 46);
            g2.setColor(Ui.ACCENT);
            g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(x + 23, y + 32, x + 23, y + 14);
            g2.drawLine(x + 23, y + 14, x + 15, y + 22);
            g2.drawLine(x + 23, y + 14, x + 31, y + 22);
            g2.drawLine(x + 13, y + 36, x + 33, y + 36);
            g2.dispose();
        }
    }

    private JPanel buildFileRows() {
        JPanel rows = new JPanel(new GridLayout(2, 1, 0, 8));
        rows.setOpaque(false);
        rows.add(fileRow("Input JAR", input, this::browseInput));
        rows.add(fileRow("Output JAR", output, this::browseOutput));
        return rows;
    }

    private JPanel fileRow(String label, JTextField field, Runnable browse) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        JLabel caption = Ui.muted(label);
        caption.setFont(caption.getFont().deriveFont(Font.BOLD, 12f));
        caption.setPreferredSize(new Dimension(96, 26));
        row.add(caption, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        JButton button = Ui.secondary("Browse");
        button.addActionListener(e -> browse.run());
        row.add(button, BorderLayout.EAST);
        return row;
    }

    private void browseInput() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JAR files", "jar", "zip", "war"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            state.setInput(chooser.getSelectedFile().toPath());
        }
    }

    private void browseOutput() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JAR files", "jar", "zip", "war"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".jar")) {
                path += ".jar";
            }
            output.setText(path);
        }
    }

    void update(EngineResult result) {
        classesTile.setValue(String.valueOf(result.classes()));
        resourcesTile.setValue(String.valueOf(result.resources()));
        durationTile.setValue(result.millis() + " ms");
        Stats stats = result.stats();
        stringsTile.setValue(String.valueOf(stat(stats, "strings.encrypted")));
        flowTile.setValue(String.valueOf(stat(stats, "flow.methods")));
        riskTile.setValue(String.valueOf(result.risk().reflectionCallSites()));
        if (result.license() == null) {
            licenseTile.setValue("community");
        } else if (result.license().isValid()) {
            licenseTile.setValue(result.license().edition());
        } else {
            licenseTile.setValue("invalid");
        }
        statusTile.setValue("Done");
        rebuildTree(result.output());
        frame.showPage(ZipitFrame.PAGE_DASHBOARD);
    }

    void applyTheme() {
        outputTree.setBackground(Ui.surface());
        outputTree.setForeground(Ui.ink());
        outputScroll.setBackground(Ui.surface());
        outputScroll.getViewport().setBackground(Ui.surface());
        repaint();
    }

    void reset() {
        for (Tile tile : new Tile[]{classesTile, resourcesTile, durationTile, stringsTile,
                flowTile, riskTile, licenseTile}) {
            tile.setValue("—");
        }
        statusTile.setValue("Ready");
        outputTree.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("No output yet")));
    }

    private static long stat(Stats stats, String key) {
        if (stats == null) {
            return 0L;
        }
        Map<String, Long> values = stats.snapshot();
        Long value = values.get(key);
        return value == null ? 0L : value.longValue();
    }

    private void rebuildTree(Path jar) {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode(jar.getFileName().toString());
        DefaultMutableTreeNode classes = new DefaultMutableTreeNode("classes");
        DefaultMutableTreeNode resources = new DefaultMutableTreeNode("resources");
        List<String> classNames = new ArrayList<String>();
        List<String> resourceNames = new ArrayList<String>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                if (entry.getName().endsWith(".class")) {
                    classNames.add(entry.getName());
                } else {
                    resourceNames.add(entry.getName());
                }
            }
        } catch (Exception e) {
            root.add(new DefaultMutableTreeNode("Unable to read output: " + e.getMessage()));
            outputTree.setModel(new DefaultTreeModel(root));
            return;
        }
        Collections.sort(classNames);
        Collections.sort(resourceNames);
        for (String name : classNames) {
            classes.add(new DefaultMutableTreeNode(name));
        }
        for (String name : resourceNames) {
            resources.add(new DefaultMutableTreeNode(name));
        }
        root.add(classes);
        root.add(resources);
        outputTree.setModel(new DefaultTreeModel(root));
        for (int i = 0; i < outputTree.getRowCount(); i++) {
            outputTree.expandRow(i);
        }
    }

    /** Small KPI card with an accent marker. */
    private static final class Tile extends Ui.CardPanel {
        private final JLabel value = new JLabel("—") {
            @Override
            public Color getForeground() {
                return Ui.ink();
            }
        };

        Tile(String caption, Color accent) {
            super();
            setLayout(new BorderLayout(0, 6));
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
            JPanel header = new JPanel(new BorderLayout(8, 0));
            header.setOpaque(false);
            JLabel dot = new JLabel("●");
            dot.setForeground(accent);
            dot.setFont(new Font("SansSerif", Font.PLAIN, 10));
            header.add(dot, BorderLayout.WEST);
            header.add(Ui.muted(caption), BorderLayout.CENTER);
            value.setFont(value.getFont().deriveFont(Font.BOLD, 27f));
            add(header, BorderLayout.NORTH);
            add(value, BorderLayout.CENTER);
        }

        void setValue(String text) {
            value.setText(text);
        }
    }
}
