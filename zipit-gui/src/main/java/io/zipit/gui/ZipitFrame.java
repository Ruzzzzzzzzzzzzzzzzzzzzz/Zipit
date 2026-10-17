package io.zipit.gui;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ConfigLoader;
import io.zipit.core.config.ConfigWriter;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.util.Log;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Main window with sidebar navigation, dashboard and run console. */
final class ZipitFrame extends JFrame {

    static final String PAGE_DASHBOARD = "dashboard";
    static final String PAGE_TRANSFORMS = "transforms";
    static final String PAGE_RULES = "rules";
    static final String PAGE_ENTERPRISE = "enterprise";
    static final String PAGE_CONSOLE = "console";

    private final GuiState state = new GuiState();
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, JButton> navButtons = new LinkedHashMap<String, JButton>();
    private final Map<String, String> navKinds = new LinkedHashMap<String, String>();
    private final ConsolePanel console = new ConsolePanel();
    private JPanel sidebar;
    private JPanel statusBar;
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel modeLabel = new JLabel("Community edition · ASM 9.11");
    private final JProgressBar progress = new JProgressBar(0, 100);
    private final JButton runButton;
    private final JButton cancelButton;
    private final JButton themeButton;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final DashboardPanel dashboard;
    private final EnterprisePanel enterprise;
    private String currentPage = PAGE_DASHBOARD;
    private SwingWorker<EngineResult, Void> worker;
    private List<String> verifyErrors = new ArrayList<String>();

    ZipitFrame() {
        super("Zipit 1.0.0 - Enterprise Java Bytecode Protection");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 760));
        setSize(1360, 900);
        setLocationRelativeTo(null);
        getContentPane().setBackground(Ui.pageBackground());
        installIcons();

        dashboard = new DashboardPanel(state, this);
        enterprise = new EnterprisePanel(state, this);

        runButton = headerPrimary("Run Zipit");
        cancelButton = headerGhost("Cancel");
        themeButton = headerGhost("Dark mode");
        cancelButton.setVisible(false);

        setJMenuBar(buildMenu());
        add(buildHeader(), BorderLayout.NORTH);
        sidebar = buildSidebar();
        add(sidebar, BorderLayout.WEST);
        buildContent();
        add(content, BorderLayout.CENTER);
        statusBar = buildStatusBar();
        add(statusBar, BorderLayout.SOUTH);
        applyThemeColors();

        installShortcuts();
        installDropTarget();
        String initialPage = System.getProperty("zipit.gui.page", PAGE_DASHBOARD);
        showPage(navButtons.containsKey(initialPage) ? initialPage : PAGE_DASHBOARD);
        updateNavStyles();
        if ("dark".equalsIgnoreCase(System.getProperty("zipit.gui.theme", ""))) {
            Ui.toggleTheme(this);
            themeButton.setText("Light mode");
            applyThemeColors();
            updateNavStyles();
            SwingUtilities.updateComponentTreeUI(this);
        }

        runButton.addActionListener(e -> runZipit());
        cancelButton.addActionListener(e -> {
            cancelled.set(true);
            cancelButton.setEnabled(false);
            statusLabel.setText("Cancelling...");
        });
        themeButton.addActionListener(e -> {
            Ui.toggleTheme(this);
            themeButton.setText(Ui.isDark() ? "Light mode" : "Dark mode");
            applyThemeColors();
            updateNavStyles();
            SwingUtilities.updateComponentTreeUI(this);
            repaint();
        });
    }

    private JPanel buildHeader() {
        Ui.GradientPanel header = new Ui.GradientPanel();
        header.setLayout(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(20, 28, 20, 28));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JPanel titleRow = new JPanel();
        titleRow.setOpaque(false);
        titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
        JLabel brand = new JLabel("ZIPIT");
        brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 30f));
        JLabel version = Ui.pill("1.0.0 · ASM 9.11",
                new Color(255, 255, 255, 38), new Color(0xEEF2FF));
        titleRow.add(brand);
        titleRow.add(Box.createHorizontalStrut(12));
        titleRow.add(version);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);

        JLabel tagline = new JLabel("Deterministic bytecode protection · audit · stable mappings");
        tagline.setForeground(new Color(0xDBEAFE));
        tagline.setFont(tagline.getFont().deriveFont(Font.PLAIN, 12.5f));
        tagline.setAlignmentX(LEFT_ALIGNMENT);
        titles.add(titleRow);
        titles.add(Box.createVerticalStrut(5));
        titles.add(tagline);
        titles.setAlignmentY(Component.CENTER_ALIGNMENT);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        actions.add(themeButton);
        actions.add(Box.createHorizontalStrut(10));
        actions.add(cancelButton);
        actions.add(Box.createHorizontalStrut(10));
        actions.add(runButton);
        actions.setAlignmentY(Component.CENTER_ALIGNMENT);

        JPanel brandRow = new JPanel();
        brandRow.setOpaque(false);
        brandRow.setLayout(new BoxLayout(brandRow, BoxLayout.X_AXIS));
        Image logoImage = loadImage("/zipit-icon-256.png");
        if (logoImage != null) {
            Image scaled = Ui.scaleImage(logoImage, 96);
            JLabel logo = new JLabel(new ImageIcon(scaled));
            logo.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(255, 255, 255, 105), 2, true),
                    BorderFactory.createEmptyBorder(3, 3, 3, 3)));
            brandRow.add(logo);
            brandRow.add(Box.createHorizontalStrut(18));
        }
        brandRow.add(titles);
        brandRow.setAlignmentY(Component.CENTER_ALIGNMENT);
        header.add(brandRow, BorderLayout.WEST);
        header.add(actions, BorderLayout.EAST);
        return header;
    }

    private void installIcons() {
        java.util.List<Image> icons = new java.util.ArrayList<Image>();
        Image small = loadImage("/zipit-icon-64.png");
        Image large = loadImage("/zipit-icon-256.png");
        if (small != null) {
            icons.add(small);
        }
        if (large != null) {
            icons.add(large);
        }
        if (icons.isEmpty()) {
            setIconImage(createIcon());
        } else {
            setIconImages(icons);
        }
    }

    static Image loadImage(String resource) {
        try (InputStream in = ZipitFrame.class.getResourceAsStream(resource)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(236, 100));
        sidebar.setOpaque(true);
        sidebar.setBackground(Ui.sidebarColor());
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.line()),
                BorderFactory.createEmptyBorder(20, 14, 16, 14)));

        JLabel workspace = Ui.muted("WORKSPACE");
        workspace.setFont(workspace.getFont().deriveFont(Font.BOLD, 10f));
        workspace.setAlignmentX(LEFT_ALIGNMENT);
        workspace.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 0));
        sidebar.add(workspace);
        addNav(sidebar, PAGE_DASHBOARD, "Dashboard", "dashboard");
        addNav(sidebar, PAGE_TRANSFORMS, "Transforms", "transforms");
        addNav(sidebar, PAGE_RULES, "Rules & Presets", "rules");
        addNav(sidebar, PAGE_ENTERPRISE, "Enterprise", "enterprise");
        addNav(sidebar, PAGE_CONSOLE, "Console", "console");

        sidebar.add(Box.createVerticalGlue());
        JSeparator separator = new JSeparator();
        separator.setForeground(Ui.line());
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sidebar.add(separator);
        sidebar.add(Box.createVerticalStrut(12));
        JLabel version = Ui.muted("Zipit 1.0.0 · ASM 9.11");
        version.setAlignmentX(LEFT_ALIGNMENT);
        sidebar.add(version);
        JLabel java = Ui.muted("Java 8+ runtime");
        java.setAlignmentX(LEFT_ALIGNMENT);
        sidebar.add(Box.createVerticalStrut(2));
        sidebar.add(java);
        return sidebar;
    }

    private void addNav(JPanel sidebar, final String page, String label, String kind) {
        final JButton button = Ui.nav(label, Ui.navIcon(kind, Ui.mutedInk()));
        button.setAlignmentX(LEFT_ALIGNMENT);
        button.addActionListener(e -> showPage(page));
        navButtons.put(page, button);
        navKinds.put(page, kind);
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(4));
    }

    private void buildContent() {
        content.setOpaque(false);
        content.add(dashboard, PAGE_DASHBOARD);
        content.add(wrap(new TransformsPanel(state)), PAGE_TRANSFORMS);
        content.add(wrap(new RulesPanel(state)), PAGE_RULES);
        content.add(wrap(enterprise), PAGE_ENTERPRISE);
        content.add(console, PAGE_CONSOLE);
    }

    private JScrollPane wrap(JComponent component) {
        JScrollPane scroll = Ui.scroll(component);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        return scroll;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0));
        bar.setOpaque(true);
        bar.setBackground(Ui.surface());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.line()),
                BorderFactory.createEmptyBorder(8, 20, 9, 20)));
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        statusLabel.setForeground(Ui.ink());
        modeLabel.setFont(modeLabel.getFont().deriveFont(Font.PLAIN, 12f));
        modeLabel.setForeground(Ui.mutedInk());
        progress.setStringPainted(true);
        progress.setPreferredSize(new Dimension(340, 20));
        progress.setString("");
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(progress, BorderLayout.CENTER);
        bar.add(modeLabel, BorderLayout.EAST);
        return bar;
    }

    /** Applies theme-dependent surfaces after a light/dark switch. */
    private void applyThemeColors() {
        getContentPane().setBackground(Ui.pageBackground());
        if (sidebar != null) {
            sidebar.setBackground(Ui.sidebarColor());
            sidebar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 0, 1, Ui.line()),
                    BorderFactory.createEmptyBorder(20, 14, 16, 14)));
        }
        if (statusBar != null) {
            statusBar.setBackground(Ui.surface());
            statusBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, Ui.line()),
                    BorderFactory.createEmptyBorder(8, 20, 9, 20)));
        }
        statusLabel.setForeground(Ui.ink());
        modeLabel.setForeground(Ui.mutedInk());
        console.applyTheme();
        dashboard.applyTheme();
        repaint();
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(item("Open configuration...", "control O", this::openConfig));
        file.add(item("Save configuration...", "control S", this::saveConfig));
        file.add(new JSeparator());
        file.add(item("Open output folder", null, () -> enterprise.openOutputFolder()));
        file.add(new JSeparator());
        file.add(item("Exit", null, this::dispose));
        bar.add(file);

        JMenu tools = new JMenu("Tools");
        tools.add(item("Compare mapping files...", null, () -> enterprise.showDiff()));
        tools.add(item("Trace watermark...", null, () -> enterprise.showTrace()));
        tools.add(item("Open last audit report", null, () -> enterprise.openReport()));
        bar.add(tools);

        JMenu view = new JMenu("View");
        view.add(item("Toggle light/dark", "control T", () -> {
            Ui.toggleTheme(this);
            themeButton.setText(Ui.isDark() ? "Light mode" : "Dark mode");
            applyThemeColors();
            updateNavStyles();
            SwingUtilities.updateComponentTreeUI(this);
        }));
        view.add(item("Console", null, () -> showPage(PAGE_CONSOLE)));
        bar.add(view);

        JMenu help = new JMenu("Help");
        help.add(item("About Zipit", null, this::showAbout));
        bar.add(help);
        return bar;
    }

    private JMenuItem item(String text, String accelerator, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        }
        item.addActionListener(e -> action.run());
        return item;
    }

    private void installShortcuts() {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F5"), "runZipit");
        getRootPane().getActionMap().put("runZipit", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                runZipit();
            }
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("control R"), "runZipit");
    }

    private void installDropTarget() {
        getRootPane().setTransferHandler(new javax.swing.TransferHandler() {
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
                            showPage(PAGE_DASHBOARD);
                            log("INFO", "Dropped input: " + file.getAbsolutePath());
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                }
                return false;
            }
        });
    }

    void showPage(String page) {
        currentPage = page;
        cards.show(content, page);
        updateNavStyles();
        if (PAGE_CONSOLE.equals(page)) {
            // Keep the console focused for keyboard users.
            SwingUtilities.invokeLater(console::requestFocusInWindow);
        }
    }

    private void updateNavStyles() {
        Color activeBackground = Ui.isDark() ? new Color(0x1E293B) : new Color(0xE8F0FE);
        Color activeForeground = Ui.isDark() ? Color.WHITE : new Color(0x1D4ED8);
        String current = currentPage;
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            JButton button = entry.getValue();
            boolean active = entry.getKey().equals(current);
            button.setOpaque(active);
            button.setContentAreaFilled(active);
            button.setBackground(active ? activeBackground : Ui.sidebarColor());
            button.setForeground(active ? activeForeground : Ui.ink());
            button.setFont(button.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN));
            button.setIcon(Ui.navIcon(navKinds.get(entry.getKey()),
                    active ? activeForeground : Ui.mutedInk()));
            button.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        }
    }

    private JButton headerPrimary(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
        button.setBackground(Color.WHITE);
        button.setForeground(Ui.ACCENT_DARK);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(9, 22, 9, 22));
        return button;
    }

    private JButton headerGhost(String text) {
        JButton button = new JButton(text);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setForeground(Color.WHITE);
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 110), 1, true),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
        return button;
    }

    private void runZipit() {
        if (worker != null && !worker.isDone()) {
            return;
        }
        final ZipitConfig config;
        try {
            config = state.toConfig();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Invalid configuration",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        cancelled.set(false);
        verifyErrors = new ArrayList<String>();
        console.clear();
        log("INFO", "Input:  " + config.input());
        log("INFO", "Output: " + config.output());
        log("INFO", "Seed:   " + config.seed());
        if (!config.preset().isEmpty()) {
            log("INFO", "Preset: " + config.preset());
        }
        runButton.setEnabled(false);
        cancelButton.setVisible(true);
        cancelButton.setEnabled(true);
        progress.setValue(0);
        progress.setString("0%");
        statusLabel.setText("Starting...");
        showPage(PAGE_CONSOLE);

        final Log log = new Log(new Log.Sink() {
            @Override
            public void line(String level, String message) {
                log(level, message);
            }
        }, true);

        worker = new SwingWorker<EngineResult, Void>() {
            @Override
            protected EngineResult doInBackground() throws Exception {
                EngineResult result = new ZipitEngine(log).run(config, new ProgressListener() {
                    @Override
                    public void onProgress(final int percent, final String message) {
                        SwingUtilities.invokeLater(() -> {
                            progress.setValue(percent);
                            progress.setString(percent + "% — " + message);
                            statusLabel.setText(message);
                        });
                    }

                    @Override
                    public boolean isCancelled() {
                        return cancelled.get();
                    }
                });
                if (!result.dryRun() && state.verify.isSelected()) {
                    verifyErrors = ZipitVerifier.verify(config.output(), config.libraries());
                }
                return result;
            }

            @Override
            protected void done() {
                runButton.setEnabled(true);
                cancelButton.setVisible(false);
                try {
                    if (cancelled.get()) {
                        throw new CancellationException("Cancelled");
                    }
                    EngineResult result = get();
                    progress.setValue(100);
                    progress.setString("100%");
                    statusLabel.setText("Finished in " + result.millis() + " ms");
                    modeLabel.setText(result.license() == null
                            ? "Community edition · ASM 9.11"
                            : "Licensed: " + result.license().licensee() + " · ASM 9.11");
                    dashboard.update(result);
                    log("SUCCESS", "Finished: " + result.classes() + " classes, "
                            + result.resources() + " resources in " + result.millis() + " ms");
                    if (!verifyErrors.isEmpty()) {
                        log("ERROR", "Verification failed for " + verifyErrors.size() + " class(es)");
                        for (String error : verifyErrors) {
                            log("ERROR", "  " + error);
                        }
                        showPage(PAGE_CONSOLE);
                    } else if (!result.dryRun() && state.verify.isSelected()) {
                        log("SUCCESS", "Verification: OK");
                    }
                    if (result.reportHtml() != null || result.reportJson() != null) {
                        log("INFO", "Audit: " + result.reportHtml() + " / " + result.reportJson());
                    }
                } catch (CancellationException e) {
                    statusLabel.setText("Cancelled");
                    log("WARN", "Run cancelled");
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    statusLabel.setText("Failed");
                    log("ERROR", cause.getMessage());
                    JOptionPane.showMessageDialog(ZipitFrame.this,
                            cause.getMessage(), "Zipit failed", JOptionPane.ERROR_MESSAGE);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        worker.execute();
    }

    private void openConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("YAML configuration", "yml", "yaml"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            ZipitConfig config = ConfigLoader.load(chooser.getSelectedFile().toPath());
            state.load(config);
            dashboard.reset();
            statusLabel.setText("Configuration loaded");
            log("INFO", "Loaded configuration " + chooser.getSelectedFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Unable to load configuration",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("YAML configuration", "yml", "yaml"));
        chooser.setSelectedFile(new File("zipit.yml"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            ZipitConfig config = state.toConfig();
            Files.write(chooser.getSelectedFile().toPath(),
                    ConfigWriter.toYaml(config).getBytes(StandardCharsets.UTF_8));
            statusLabel.setText("Configuration saved");
            log("INFO", "Saved configuration " + chooser.getSelectedFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Unable to save configuration",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void log(final String level, final String message) {
        console.append(level, message);
    }

    private void showAbout() {
        JOptionPane.showMessageDialog(this,
                "Zipit 1.0.0\n"
                        + "Enterprise Java bytecode protection\n\n"
                        + "Renaming · string/constant encryption · control flow ·\n"
                        + "method virtualization · anti-LLM decoys · package/resource obfuscation ·\n"
                        + "reflection auto-keep · trimming · parameter mutation ·\n"
                        + "audit reports · stable mappings · Gradle/Maven plugins\n\n"
                        + "ASM 9.11 tree API · Java 8+",
                "About Zipit", JOptionPane.INFORMATION_MESSAGE);
    }

    private Image createIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, Ui.ACCENT_DARK, 64, 64, Ui.PURPLE));
        g2.fillRoundRect(2, 2, 60, 60, 18, 18);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 40));
        g2.drawString("Z", 18, 46);
        g2.dispose();
        return image;
    }
}
