package io.zipit.gui;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ConfigLoader;
import io.zipit.core.config.ConfigWriter;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.enterprise.MappingDiff;
import io.zipit.core.enterprise.WatermarkTracer;
import io.zipit.core.util.Log;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
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

/** Main window: custom top bar, sidebar navigation, pages and status bar. */
final class ZipitFrame extends JFrame {

    static final String PAGE_DASHBOARD = "dashboard";
    static final String PAGE_TRANSFORMS = "transforms";
    static final String PAGE_RULES = "rules";
    static final String PAGE_ENTERPRISE = "enterprise";
    static final String PAGE_CONSOLE = "console";

    private final GuiState state;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<String, NavButton> navButtons = new LinkedHashMap<String, NavButton>();
    private final Map<String, String> navIcons = new LinkedHashMap<String, String>();
    private final List<JComponent> pageViews = new ArrayList<JComponent>();

    private final DashboardPage dashboardPage;
    private final TransformsPage transformsPage;
    private final RulesPage rulesPage;
    private final EnterprisePage enterprisePage;
    private final ConsolePage consolePage;

    private final RunProgress statusProgress = new RunProgress();
    private final JLabel statusLabel = Ui.colored(I18n.t("ui.ready"), () -> Theme.TEXT, 13f, Font.PLAIN);
    private final JLabel stageLabel = Ui.colored("", () -> Theme.FAINT, 12f, Font.PLAIN);
    private final JLabel editionLabel = Ui.colored(I18n.t("ui.community.edition.asm.9.11"),
            () -> Theme.FAINT, 12f, Font.PLAIN);
    private final JLabel pageCrumb = Ui.colored(I18n.t("ui.dashboard"), () -> Theme.TEXT, 14f, Font.BOLD);
    private final StatusDot statusDot = new StatusDot();

    private final JButton runButton;
    private final JButton cancelButton;
    private final JButton themeButton;
    private final JButton languageButton;
    private final Runnable themeListener;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private SwingWorker<EngineResult, Void> worker;
    private List<String> verifyErrors = new ArrayList<String>();
    private String currentPage = PAGE_DASHBOARD;
    private EngineResult lastResult;
    private Color statusTone = Theme.ACCENT2;

    ZipitFrame() {
        this(new GuiState(), null, null, true);
    }

    /** Rebuild constructor used by the language switch (shares the form state). */
    ZipitFrame(GuiState sharedState, String initialPage, java.awt.Rectangle bounds) {
        this(sharedState, initialPage, bounds, false);
    }

    private ZipitFrame(GuiState sharedState, String requestedPage,
                       java.awt.Rectangle bounds, boolean startup) {
        super(I18n.t("ui.zipit.1.0.0.enterprise.java.bytecode.protection"));
        this.state = sharedState;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 780));
        setSize(bounds == null ? new Dimension(1500, 950) : bounds.getSize());
        if (bounds == null) {
            setLocationRelativeTo(null);
        } else {
            setLocation(bounds.x, bounds.y);
        }
        if (Boolean.getBoolean("zipit.gui.topmost")) {
            setAlwaysOnTop(true);
        }
        installWindowIcon();

        runButton = Ui.primary(I18n.t("ui.protect.jar"), "play");
        cancelButton = Ui.danger(I18n.t("ui.cancel"), "stop");
        cancelButton.setVisible(false);
        themeButton = Ui.iconButton(Theme.dark ? "sun" : "moon", I18n.t("ui.toggle.light.dark.theme.ctrl.t"));
        languageButton = Ui.button(I18n.switchLabel(), "globe");
        languageButton.setToolTipText(I18n.t("ui.toggle.interface.language"));

        dashboardPage = new DashboardPage(this, state);
        transformsPage = new TransformsPage(this, state);
        rulesPage = new RulesPage(this, state);
        enterprisePage = new EnterprisePage(this, state);
        consolePage = new ConsolePage(this, state);

        AmbientBackground root = new AmbientBackground();
        root.setLayout(new BorderLayout());
        root.add(buildTopBar(), BorderLayout.NORTH);
        root.add(buildSidebar(), BorderLayout.WEST);

        content.setOpaque(false);
        content.add(dashboardPage, PAGE_DASHBOARD);
        content.add(transformsPage, PAGE_TRANSFORMS);
        content.add(rulesPage, PAGE_RULES);
        content.add(enterprisePage, PAGE_ENTERPRISE);
        content.add(consolePage, PAGE_CONSOLE);
        pageViews.add(dashboardPage);
        pageViews.add(transformsPage);
        pageViews.add(rulesPage);
        pageViews.add(enterprisePage);
        pageViews.add(consolePage);
        root.add(content, BorderLayout.CENTER);
        root.add(buildStatusBar(), BorderLayout.SOUTH);
        setContentPane(root);

        wireActions();
        installShortcuts();
        installDropTarget();

        String initialPage = requestedPage != null ? requestedPage
                : System.getProperty("zipit.gui.page", PAGE_DASHBOARD);
        showPage(navButtons.containsKey(initialPage) ? initialPage : PAGE_DASHBOARD);
        themeListener = new Runnable() {
            @Override
            public void run() {
                updateThemeButton();
                updateNavStyles();
                Glass.clearCache();
                SwingUtilities.updateComponentTreeUI(ZipitFrame.this);
                Theme.restyle(ZipitFrame.this);
                if (getContentPane() != null) {
                    getContentPane().repaint();
                }
                repaint();
            }
        };
        Theme.addChangeListener(themeListener);
        if (startup) {
            applyStartupArtifact();
            installSnapshotHarness();
        } else if (Boolean.getBoolean("zipit.gui.snapshotLangSwitchDone")) {
            installSnapshotHarness();
        }
    }

    /** Immediately switches zh/en by rebuilding the frame with a shared form state. */
    private void switchLanguage() {
        if (isRunning()) {
            setStatus(I18n.t("ui.language.switch.is.unavailable.while.a.protection.run.is.active"));
            return;
        }
        I18n.toggle();
        Theme.refreshFonts();
        GuiState shared = state;
        String page = currentPage;
        java.awt.Rectangle bounds = getBounds();
        shared.clearChangeListeners();
        Theme.removeChangeListener(themeListener);
        ZipitFrame next = new ZipitFrame(shared, page, bounds);
        next.setVisible(true);
        dispose();
    }

    // ------------------------------------------------------------------
    // screenshot harness (system properties only; no effect in normal use)
    // ------------------------------------------------------------------

    private void installSnapshotHarness() {
        final String dir = System.getProperty("zipit.gui.snapshot", "").trim();
        if (dir.isEmpty()) {
            return;
        }
        final java.io.File outputDir = new java.io.File(dir);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            System.out.println("SNAPSHOT: cannot create " + outputDir);
            return;
        }
        final String[] pages = System.getProperty("zipit.gui.snapshotPages",
                "dashboard,transforms,rules,enterprise,console").split(",");
        final String[] themes = System.getProperty("zipit.gui.snapshotThemes", "dark").split(",");
        final boolean autoRun = Boolean.getBoolean("zipit.gui.snapshotAutorun");
        Timer starter = new Timer(1400, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                if (autoRun) {
                    showPage(PAGE_DASHBOARD);
                    runNow();
                    waitForSnapshotRun(outputDir, pages, themes);
                } else {
                    snapshotSequence(outputDir, pages, themes, 0);
                }
            }
        });
        starter.setRepeats(false);
        starter.start();
    }

    private void waitForSnapshotRun(final java.io.File dir, final String[] pages,
                                    final String[] themes) {
        final Timer poll = new Timer(400, null);
        poll.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (isRunning()) {
                    return;
                }
                poll.stop();
                // Give the EDT a moment to finish the post-run UI updates.
                Timer settle = new Timer(900, null);
                settle.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        ((Timer) event.getSource()).stop();
                        snapshotSequence(dir, pages, themes, 0);
                    }
                });
                settle.setRepeats(false);
                settle.start();
            }
        });
        poll.start();
    }

    private void snapshotSequence(final java.io.File dir, final String[] pages,
                                  final String[] themes, final int index) {
        int total = pages.length * themes.length;
        if (index >= total) {
            captureGlassDialogs(dir, themes, 0);
            return;
        }
        final int themeIndex = index / pages.length;
        final int pageIndex = index % pages.length;
        final boolean wantDark = !"light".equalsIgnoreCase(themes[themeIndex].trim());
        if (Theme.dark != wantDark) {
            ThemeTransition.play(this, new Runnable() {
                @Override
                public void run() {
                    Theme.setDark(wantDark);
                }
            });
        }
        showPage(pages[pageIndex].trim());
        scrollPageToTop(pages[pageIndex].trim());
        final int captureDelay;
        if ("enterprise".equals(pages[pageIndex].trim())
                && Boolean.getBoolean("zipit.gui.snapshotAuthTest")) {
            enterprisePage.testAuthConnection();
            captureDelay = 1600;
        } else {
            captureDelay = 760;
        }
        Timer captureTimer = new Timer(captureDelay, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                writeSnapshot(dir, pages[pageIndex].trim(),
                        themes[themeIndex].trim().toLowerCase(java.util.Locale.ROOT));
                snapshotSequence(dir, pages, themes, index + 1);
            }
        });
        captureTimer.setRepeats(false);
        captureTimer.start();
    }

    /**
     * Captures the frosted-glass About dialog (blurred owner backdrop) in every
     * requested theme; used as visual proof for the glass effect.
     */
    private void captureGlassDialogs(final java.io.File dir, final String[] themes,
                                     final int index) {
        if (index >= themes.length) {
            if (Boolean.getBoolean("zipit.gui.snapshotLangSwitch")
                    && !Boolean.getBoolean("zipit.gui.snapshotLangSwitchDone")) {
                System.setProperty("zipit.gui.snapshotLangSwitchDone", "true");
                System.out.println("SNAPSHOT: switching language to " + I18n.switchLabel());
                switchLanguage();
                return;
            }
            System.out.println("SNAPSHOT: complete");
            System.exit(0);
            return;
        }
        final String theme = themes[index].trim().toLowerCase(java.util.Locale.ROOT);
        final boolean wantDark = !"light".equals(theme);
        if (Theme.dark != wantDark) {
            ThemeTransition.play(this, new Runnable() {
                @Override
                public void run() {
                    Theme.setDark(wantDark);
                }
            });
        }
        showPage(PAGE_ENTERPRISE);
        Timer openTimer = new Timer(800, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                final javax.swing.JDialog dialog = Dialogs.aboutPreview(ZipitFrame.this);
                Timer captureTimer = new Timer(800, new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent event) {
                        ((Timer) event.getSource()).stop();
                        try {
                            java.awt.image.BufferedImage image =
                                    new java.awt.image.BufferedImage(dialog.getWidth(),
                                            dialog.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                            Graphics2D g2 = image.createGraphics();
                            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                            dialog.paintAll(g2);
                            g2.dispose();
                            java.io.File file = new java.io.File(dir,
                                    "glass-about-" + theme + "-" + I18n.lang() + ".png");
                            ImageIO.write(image, "png", file);
                            System.out.println("SNAPSHOT: " + file.getAbsolutePath());
                        } catch (Exception failure) {
                            System.out.println("SNAPSHOT GLASS FAILED: " + failure);
                        } finally {
                            dialog.dispose();
                            captureGlassDialogs(dir, themes, index + 1);
                        }
                    }
                });
                captureTimer.setRepeats(false);
                captureTimer.start();
            }
        });
        openTimer.setRepeats(false);
        openTimer.start();
    }

    /** Resets the main page scroll pane so every snapshot starts at the top. */
    private void scrollPageToTop(String page) {
        java.awt.Component view;
        if (PAGE_DASHBOARD.equals(page)) {
            view = dashboardPage;
        } else if (PAGE_TRANSFORMS.equals(page)) {
            view = transformsPage;
        } else if (PAGE_RULES.equals(page)) {
            view = rulesPage;
        } else if (PAGE_ENTERPRISE.equals(page)) {
            view = enterprisePage;
        } else {
            view = consolePage;
        }
        if (view instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) view).getComponents()) {
                if (child instanceof javax.swing.JScrollPane) {
                    ((javax.swing.JScrollPane) child).getViewport()
                            .setViewPosition(new java.awt.Point(0, 0));
                }
            }
        }
    }

    private void writeSnapshot(java.io.File dir, String page, String theme) {
        try {
            int width = Math.max(1, getWidth());
            int height = Math.max(1, getHeight());
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                    width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = image.createGraphics();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            paintAll(g2);
            g2.dispose();
            java.io.File file = new java.io.File(dir,
                    "new-" + page + "-" + theme + "-" + I18n.lang() + ".png");
            ImageIO.write(image, "png", file);
            System.out.println("SNAPSHOT: " + file.getAbsolutePath());
            if (Boolean.getBoolean("zipit.gui.snapshotDesktop")) {
                try {
                    toFront();
                    java.awt.Rectangle bounds = getBounds();
                    java.awt.image.BufferedImage screen = new java.awt.Robot()
                            .createScreenCapture(bounds);
                    java.io.File desktopFile = new java.io.File(dir,
                            "desktop-" + page + "-" + theme + "-" + I18n.lang() + ".png");
                    ImageIO.write(screen, "png", desktopFile);
                    System.out.println("SNAPSHOT-DESKTOP: " + desktopFile.getAbsolutePath());
                } catch (Exception robotFailure) {
                    System.out.println("SNAPSHOT-DESKTOP FAILED: " + robotFailure);
                }
            }
        } catch (Exception e) {
            System.out.println("SNAPSHOT FAILED: " + e);
        }
    }

    // ------------------------------------------------------------------
    // chrome
    // ------------------------------------------------------------------

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(Theme.px(18), 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Glass.paintFrosted(g2, this, 0);
                g2.setColor(Theme.glassBorder());
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(100, Theme.px(64)));
        bar.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(18), 0, Theme.px(16)));

        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.X_AXIS));
        JLabel mark = new JLabel(BrandMark.tileIcon(Theme.px(33)));
        brand.add(mark);
        brand.add(Box.createHorizontalStrut(Theme.px(12)));
        JLabel wordmark = Ui.colored("ZIPIT", () -> Theme.TEXT, 22f, Font.BOLD);
        wordmark.setFont(Theme.serifFont(Font.BOLD, 22f));
        brand.add(wordmark);
        brand.add(Box.createHorizontalStrut(Theme.px(10)));
        brand.add(Ui.pill("v1.0.0"));
        brand.add(Box.createHorizontalStrut(Theme.px(18)));
        brand.add(verticalRule(Theme.px(24)));
        brand.add(Box.createHorizontalStrut(Theme.px(18)));
        pageCrumb.setFont(Theme.font(Font.BOLD, 16f));
        brand.add(pageCrumb);
        bar.add(brand, BorderLayout.WEST);

        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        themeButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        JButton menuButton = Ui.iconButton("menu", I18n.t("ui.application.menu.all.commands"));
        menuButton.addActionListener(e -> buildAppMenu().show(menuButton, 0, menuButton.getHeight() + 4));
        cancelButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        runButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        actions.add(languageButton);
        actions.add(Box.createHorizontalStrut(Theme.px(6)));
        actions.add(themeButton);
        actions.add(Box.createHorizontalStrut(Theme.px(2)));
        actions.add(menuButton);
        actions.add(Box.createHorizontalStrut(Theme.px(8)));
        actions.add(cancelButton);
        actions.add(Box.createHorizontalStrut(Theme.px(8)));
        actions.add(runButton);
        bar.add(actions, BorderLayout.EAST);
        return bar;
    }

    private JComponent verticalRule(int height) {
        JComponent rule = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Theme.BORDER);
                g.fillRect(getWidth() / 2, 0, 1, getHeight());
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, height);
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }
        };
        return rule;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Glass.paintFrosted(g2, this, 0);
                g2.setColor(Theme.glassBorder());
                g2.fillRect(getWidth() - 1, 0, 1, getHeight());
                g2.dispose();
            }
        };
        sidebar.setOpaque(false);
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setPreferredSize(new Dimension(Theme.px(248), 100));
        sidebar.setBorder(BorderFactory.createEmptyBorder(Theme.px(18), Theme.px(14),
                Theme.px(14), Theme.px(14)));

        sidebar.add(sectionLabel(I18n.t("ui.workspace")));
        sidebar.add(Box.createVerticalStrut(Theme.px(8)));
        addNav(sidebar, PAGE_DASHBOARD, I18n.t("ui.dashboard"), "dashboard");
        addNav(sidebar, PAGE_TRANSFORMS, I18n.t("ui.transforms"), "transforms");
        addNav(sidebar, PAGE_RULES, I18n.t("ui.rules.presets"), "rules");
        sidebar.add(Box.createVerticalStrut(Theme.px(18)));
        sidebar.add(sectionLabel(I18n.t("ui.operations")));
        sidebar.add(Box.createVerticalStrut(Theme.px(8)));
        addNav(sidebar, PAGE_ENTERPRISE, I18n.t("ui.enterprise"), "enterprise");
        addNav(sidebar, PAGE_CONSOLE, I18n.t("ui.console"), "console");
        sidebar.add(Box.createVerticalGlue());
        sidebar.add(buildEditionCard());
        sidebar.add(Box.createVerticalStrut(Theme.px(10)));
        JButton about = Ui.ghost(I18n.t("ui.about.zipit"), "info");
        about.setAlignmentX(Component.LEFT_ALIGNMENT);
        about.addActionListener(e -> showAbout());
        sidebar.add(about);
        sidebar.add(Box.createVerticalStrut(Theme.px(2)));
        JLabel build = Ui.faint(I18n.t("ui.v1.0.0.asm.9.11.java.8"));
        build.setFont(Theme.font(Font.PLAIN, 12f));
        build.setAlignmentX(Component.LEFT_ALIGNMENT);
        build.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(15), 0, 0));
        sidebar.add(build);
        return sidebar;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = Ui.overline(text);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(14), 0, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void addNav(JPanel sidebar, final String page, String label, String icon) {
        NavButton button = new NavButton(label, icon);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                showPage(page);
            }
        });
        navButtons.put(page, button);
        navIcons.put(page, icon);
        sidebar.add(button);
        sidebar.add(Box.createVerticalStrut(Theme.px(4)));
    }

    private JPanel buildEditionCard() {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.alpha(Theme.ACCENT, Theme.dark ? 30 : 18));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), Theme.px(12), Theme.px(12));
                g2.setColor(Theme.alpha(Theme.ACCENT, Theme.dark ? 80 : 50));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, Theme.px(12), Theme.px(12));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(Theme.px(11), Theme.px(13),
                Theme.px(12), Theme.px(13)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(88)));
        card.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        JLabel title = Ui.colored(I18n.t("ui.community.edition"), () -> Theme.ACCENT2, 12f, Font.BOLD);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(Theme.px(3)));
        JLabel line = Ui.body(I18n.t("ui.unlock.licensing.audit"));
        line.setFont(Theme.font(Font.BOLD, 12f));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(line);
        card.add(Box.createVerticalStrut(Theme.px(2)));
        JLabel hint = Ui.faint(I18n.t("ui.enterprise.tools"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(hint);
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                showPage(PAGE_ENTERPRISE);
            }
        });
        return card;
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(Theme.px(14), 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                Glass.paintFrosted(g2, this, 0);
                g2.setColor(Theme.glassBorder());
                g2.fillRect(0, 0, getWidth(), 1);
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(100, Theme.px(36)));
        bar.setBorder(BorderFactory.createEmptyBorder(Theme.px(6), Theme.px(18),
                Theme.px(7), Theme.px(16)));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.X_AXIS));
        statusDot.setAlignmentY(Component.CENTER_ALIGNMENT);
        left.add(statusDot);
        left.add(Box.createHorizontalStrut(Theme.px(9)));
        statusLabel.setFont(Theme.font(Font.PLAIN, 14f));
        left.add(statusLabel);
        bar.add(left, BorderLayout.WEST);

        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.X_AXIS));
        statusProgress.setPreferredSize(new Dimension(Theme.px(240), Theme.px(8)));
        statusProgress.setMaximumSize(new Dimension(Theme.px(240), Theme.px(8)));
        statusProgress.setAlignmentY(Component.CENTER_ALIGNMENT);
        statusProgress.setVisible(false);
        center.add(statusProgress);
        center.add(Box.createHorizontalStrut(Theme.px(10)));
        stageLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        center.add(stageLabel);
        JPanel centerHolder = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 0, 0));
        centerHolder.setOpaque(false);
        centerHolder.add(center);
        bar.add(centerHolder, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.X_AXIS));
        editionLabel.setAlignmentY(Component.CENTER_ALIGNMENT);
        right.add(editionLabel);
        right.add(Box.createHorizontalStrut(Theme.px(14)));
        JLabel runtime = Ui.faint("Java " + System.getProperty("java.version", "8"));
        runtime.setFont(Theme.font(Font.PLAIN, 12f));
        runtime.setAlignmentY(Component.CENTER_ALIGNMENT);
        right.add(runtime);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ------------------------------------------------------------------
    // actions & wiring
    // ------------------------------------------------------------------

    private void wireActions() {
        runButton.addActionListener(e -> runNow());
        cancelButton.addActionListener(e -> cancelRun());
        themeButton.addActionListener(e -> toggleTheme());
        languageButton.addActionListener(e -> switchLanguage());
    }

    private JPopupMenu buildAppMenu() {
        JPopupMenu menu = new JPopupMenu();
        menu.add(item(I18n.t("ui.open.configuration"), "control O", this::openConfig));
        menu.add(item(I18n.t("ui.save.configuration"), "control S", this::saveConfig));
        menu.addSeparator();
        menu.add(item(I18n.t("ui.protect.jar"), "F5", this::runNow));
        menu.addSeparator();
        JMenuItem tools = new JMenuItem(I18n.t("ui.tools"));
        tools.setEnabled(false);
        menu.add(tools);
        menu.add(item(I18n.t("ui.compare.mapping.files"), null, this::showDiffTool));
        menu.add(item(I18n.t("ui.trace.watermark"), null, this::showTraceTool));
        menu.add(item(I18n.t("ui.open.last.audit.report"), null, this::openReport));
        menu.add(item(I18n.t("ui.open.output.folder"), null, this::openOutputFolder));
        menu.addSeparator();
        menu.add(item(I18n.t("ui.toggle.light.dark"), "control T", this::toggleTheme));
        menu.add(item(I18n.t("ui.console"), null, () -> showPage(PAGE_CONSOLE)));
        menu.addSeparator();
        menu.add(item(I18n.t("ui.about.zipit"), "F1", this::showAbout));
        return menu;
    }

    private JMenuItem item(String text, String accelerator, final Runnable action) {
        JMenuItem item = new JMenuItem(text);
        if (accelerator != null) {
            item.setAccelerator(KeyStroke.getKeyStroke(accelerator));
        }
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
        return item;
    }

    private void installShortcuts() {
        bindShortcut(KeyStroke.getKeyStroke("F5"), "zipit.run", this::runNow);
        bindShortcut(KeyStroke.getKeyStroke("control R"), "zipit.run2", this::runNow);
        bindShortcut(KeyStroke.getKeyStroke("control O"), "zipit.open", this::openConfig);
        bindShortcut(KeyStroke.getKeyStroke("control S"), "zipit.save", this::saveConfig);
        bindShortcut(KeyStroke.getKeyStroke("control T"), "zipit.theme", this::toggleTheme);
        bindShortcut(KeyStroke.getKeyStroke("F1"), "zipit.about", this::showAbout);
        bindShortcut(KeyStroke.getKeyStroke("control 1"), "zipit.page1",
                () -> showPage(PAGE_DASHBOARD));
        bindShortcut(KeyStroke.getKeyStroke("control 2"), "zipit.page2",
                () -> showPage(PAGE_TRANSFORMS));
        bindShortcut(KeyStroke.getKeyStroke("control 3"), "zipit.page3",
                () -> showPage(PAGE_RULES));
        bindShortcut(KeyStroke.getKeyStroke("control 4"), "zipit.page4",
                () -> showPage(PAGE_ENTERPRISE));
        bindShortcut(KeyStroke.getKeyStroke("control 5"), "zipit.page5",
                () -> showPage(PAGE_CONSOLE));
    }

    private void bindShortcut(KeyStroke stroke, String key, final Runnable action) {
        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(stroke, key);
        root.getActionMap().put(key, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
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
                    List<File> files = (List<File>) transferable.getTransferData(
                            DataFlavor.javaFileListFlavor);
                    for (File file : files) {
                        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
                        if (name.endsWith(".jar") || name.endsWith(".war") || name.endsWith(".zip")) {
                            state.setInput(file.toPath());
                            showPage(PAGE_DASHBOARD);
                            log("INFO", "Dropped input artifact: " + file.getAbsolutePath());
                            return true;
                        }
                    }
                } catch (Exception ignored) {
                    // Not a file drop we can handle.
                }
                return false;
            }
        });
    }

    private void applyStartupArtifact() {
        String configPath = System.getProperty("zipit.gui.loadConfig", "").trim();
        if (!configPath.isEmpty()) {
            try {
                state.load(ConfigLoader.load(Paths.get(configPath)));
                log("INFO", "Loaded configuration " + configPath);
            } catch (Exception e) {
                log("ERROR", "Unable to load configuration " + configPath + ": " + e.getMessage());
            }
        }
        String input = System.getProperty("zipit.gui.input", "");
        String output = System.getProperty("zipit.gui.output", "");
        if (!input.trim().isEmpty()) {
            try {
                state.setInputAndOutput(Paths.get(input.trim()),
                        output.trim().isEmpty() ? null : Paths.get(output.trim()));
            } catch (RuntimeException ignored) {
                // Keep the empty form when the provided path is malformed.
            }
        }
        dashboardPage.refreshPlan();
        dashboardPage.refreshAll();
        rulesPage.refreshPresets();
        String saveConfigPath = System.getProperty("zipit.gui.saveConfigTo", "").trim();
        if (!saveConfigPath.isEmpty()) {
            try {
                Files.write(Paths.get(saveConfigPath),
                        ConfigWriter.toYaml(state.toConfig()).getBytes(StandardCharsets.UTF_8));
                log("INFO", "Configuration written to " + saveConfigPath);
            } catch (Exception e) {
                log("ERROR", "Unable to save configuration: " + e.getMessage());
            }
        }
        if (Boolean.getBoolean("zipit.gui.demoLog")) {
            appendDemoLog();
        }
        if (Boolean.getBoolean("zipit.gui.autorun")) {
            Timer timer = new Timer(650, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    ((Timer) e.getSource()).stop();
                    runNow();
                }
            });
            timer.setRepeats(false);
            timer.start();
        }
        String page = System.getProperty("zipit.gui.page", "");
        if (!page.trim().isEmpty() && navButtons.containsKey(page)) {
            showPage(page);
        }
    }

    private void appendDemoLog() {
        log("INFO", "Zipit 1.0.0 · seed 23063 · default preset");
        log("INFO", "Loading input C:\\demo\\application.jar");
        log("DEBUG", "Pass ReflectionAnalysisPass started");
        log("DEBUG", "Pass ReflectionAnalysisPass finished in 14 ms");
        log("INFO", "Pass RenamePass started");
        log("WARN", "No keep rule matched annotated service endpoint — inspect the audit report");
        log("INFO", "Pass RenamePass finished in 96 ms");
        log("INFO", "Output written to C:\\demo\\application-protected.jar (412 classes, 61 resources, 982 ms)");
        log("SUCCESS", "Verification: OK — 412 classes linked");
    }

    void updateThemeButton() {
        if (themeButton instanceof Ui.FlatButton) {
            ((Ui.FlatButton) themeButton).setIconName(Theme.dark ? "sun" : "moon");
        }
        themeButton.revalidate();
        themeButton.repaint();
    }

    void updateNavStyles() {
        for (Map.Entry<String, NavButton> entry : navButtons.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(currentPage));
        }
    }

    void showPage(String page) {
        if (!navButtons.containsKey(page)) {
            return;
        }
        currentPage = page;
        cards.show(content, page);
        updateNavStyles();
        javax.swing.JButton nav = navButtons.get(page);
        pageCrumb.setText(nav == null ? page : nav.getText());
        if (PAGE_DASHBOARD.equals(page)) {
            dashboardPage.onPageShown();
        }
    }

    String currentPage() {
        return currentPage;
    }

    // ------------------------------------------------------------------
    // run pipeline
    // ------------------------------------------------------------------

    void runNow() {
        if (worker != null && !worker.isDone()) {
            return;
        }
        final ZipitConfig config;
        try {
            config = state.toConfig();
        } catch (Exception e) {
            setStatusTone(Theme.DANGER);
            setStatus(I18n.t("ui.invalid.configuration"));
            log("ERROR", "Invalid configuration: " + e.getMessage());
            Dialogs.error(this, I18n.t("ui.invalid.configuration"), e.getMessage());
            return;
        }
        cancelled.set(false);
        verifyErrors = new ArrayList<String>();
        consolePage.onRunStarted();
        dashboardPage.onRunStarted();
        setRunning(true);
        setStatusTone(Theme.WARNING);
        setStatus(I18n.t("frame.protecting", config.input().getFileName()));
        statusProgress.setProgress(0);
        stageLabel.setText(I18n.t("frame.stageStarting"));
        log("INFO", "────────────────────────────────────────────────────────────");
        log("INFO", "Zipit 1.0.0  ·  seed " + config.seed() + "  ·  preset "
                + (config.preset().isEmpty() ? "default" : config.preset()));
        log("INFO", "Input   " + config.input());
        log("INFO", "Output  " + config.output());
        log("INFO", "Options verify=" + (state.verify.isSelected() ? "on" : "off")
                + "  dryRun=" + (config.dryRun() ? "on" : "off")
                + "  keep=" + config.keep().size() + " rule(s)"
                + "  libraries=" + config.libraries().size());

        final Log engineLog = new Log(new Log.Sink() {
            @Override
            public void line(String level, String message) {
                log(level, message);
            }
        }, true);

        worker = new SwingWorker<EngineResult, Void>() {
            @Override
            protected EngineResult doInBackground() throws Exception {
                EngineResult result = new ZipitEngine(engineLog).run(config,
                        new ProgressListener() {
                            @Override
                            public void onProgress(final int percent, final String message) {
                                SwingUtilities.invokeLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        statusProgress.setProgress(percent);
                                        stageLabel.setText(percent + "%  ·  " + message);
                                        setStatus(message);
                                        dashboardPage.onProgress(percent, message);
                                    }
                                });
                            }

                            @Override
                            public boolean isCancelled() {
                                return cancelled.get();
                            }
                        });
                if (!result.dryRun() && state.verify.isSelected()) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            statusProgress.setProgress(99);
                            stageLabel.setText(I18n.t("frame.stageVerifying"));
                            setStatus(I18n.t("ui.verifying.output.classes"));
                        }
                    });
                    verifyErrors = ZipitVerifier.verify(config.output(), config.libraries());
                }
                return result;
            }

            @Override
            protected void done() {
                setRunning(false);
                try {
                    if (cancelled.get()) {
                        throw new CancellationException(I18n.t("frame.stageCancelled"));
                    }
                    EngineResult result = get();
                    handleRunSuccess(result);
                } catch (CancellationException e) {
                    handleRunCancelled();
                } catch (ExecutionException e) {
                    handleRunFailure(e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        worker.execute();
    }

    private void handleRunSuccess(EngineResult result) {
        lastResult = result;
        statusProgress.setProgress(100);
        stageLabel.setText(I18n.t("frame.stageDone"));
        dashboardPage.onRunFinished(result, verifyErrors);
        consolePage.onRunFinished(result);
        log("SUCCESS", "Finished: " + result.classes() + " classes, "
                + result.resources() + " resources in " + result.millis() + " ms");
        if (!result.passTimes().isEmpty()) {
            StringBuilder passes = new StringBuilder();
            for (Map.Entry<String, Long> entry : result.passTimes().entrySet()) {
                if (passes.length() > 0) {
                    passes.append(" · ");
                }
                passes.append(entry.getKey()).append(' ')
                        .append(entry.getValue()).append("ms");
            }
            log("DEBUG", "Pass timings: " + passes);
        }
        if (result.reportHtml() != null || result.reportJson() != null) {
            log("INFO", "Audit report: " + result.reportHtml() + "  /  " + result.reportJson());
        }
        if (result.dryRun()) {
            setStatusTone(Theme.SUCCESS);
            setStatus(I18n.t("frame.dryRunStatus", result.millis()));
            setEdition(result);
            return;
        }
        if (state.verify.isSelected()) {
            if (verifyErrors.isEmpty()) {
                log("SUCCESS", "Verification: OK — " + result.classes()
                        + " classes linked without errors");
                setStatusTone(Theme.SUCCESS);
                setStatus(I18n.t("frame.protectedStatus", result.classes(), result.millis()));
            } else {
                log("ERROR", "Verification failed for " + verifyErrors.size() + " class(es)");
                for (String error : verifyErrors) {
                    log("ERROR", "  " + error);
                }
                setStatusTone(Theme.DANGER);
                setStatus(I18n.t("frame.verifyFailedFor", verifyErrors.size()));
                showPage(PAGE_CONSOLE);
                Dialogs.error(this, I18n.t("ui.verification.failed"),
                        I18n.t("frame.verifyFailedDetail", verifyErrors.size()) + "\n"
                                + I18n.t("frame.verifyHint"));
            }
        } else {
            setStatusTone(Theme.SUCCESS);
            setStatus(I18n.t("frame.protectedNoVerify", result.classes(), result.millis()));
        }
        setEdition(result);
    }

    private void handleRunCancelled() {
        statusProgress.setProgress(0);
        stageLabel.setText(I18n.t("frame.stageCancelled"));
        setStatusTone(Theme.WARNING);
        setStatus(I18n.t("ui.run.cancelled"));
        log("WARN", "Run cancelled by user");
        dashboardPage.onRunCancelled();
        consolePage.onRunCancelled();
    }

    private void handleRunFailure(ExecutionException e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        statusProgress.setProgress(0);
        stageLabel.setText(I18n.t("frame.stageFailed"));
        setStatusTone(Theme.DANGER);
        setStatus(I18n.t("ui.run.failed"));
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        log("ERROR", message);
        dashboardPage.onRunCancelled();
        consolePage.onRunCancelled();
        Dialogs.error(this, I18n.t("ui.zipit.failed"), message);
    }

    private void setEdition(EngineResult result) {
        if (result.license() == null) {
            editionLabel.setText(I18n.t("ui.community.edition.asm.9.11"));
        } else if (result.license().isValid()) {
            editionLabel.setText(result.license().licensee() + " · "
                    + result.license().edition() + " · ASM 9.11");
        } else {
            editionLabel.setText(I18n.t("ui.invalid.license.asm.9.11"));
        }
    }

    void cancelRun() {
        if (worker == null || worker.isDone()) {
            return;
        }
        cancelled.set(true);
        cancelButton.setEnabled(false);
        setStatusTone(Theme.WARNING);
        setStatus(I18n.t("ui.cancelling"));
        log("WARN", "Cancellation requested — waiting for the current pass to finish");
    }

    boolean isRunning() {
        return worker != null && !worker.isDone();
    }

    private void setRunning(boolean running) {
        runButton.setEnabled(!running);
        runButton.setText(running ? I18n.t("ui.protecting") : I18n.t("ui.protect.jar"));
        runButton.revalidate();
        cancelButton.setVisible(running);
        cancelButton.setEnabled(running);
        statusProgress.setRunning(running);
        statusProgress.setVisible(running);
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------
    // logging
    // ------------------------------------------------------------------

    void log(final String level, final String message) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    log(level, message);
                }
            });
            return;
        }
        consolePage.terminal().append(level, message);
        dashboardPage.appendLog(level, message);
    }

    void setStatus(String text) {
        statusLabel.setText(text);
    }

    void setStatusTone(Color tone) {
        statusTone = tone;
        statusDot.setColor(tone);
    }

    private void toggleTheme() {
        // Every theme entry point (top bar button, app menu, Ctrl+T) routes
        // through the animated horizontal slide.
        ThemeTransition.play(this, new Runnable() {
            @Override
            public void run() {
                Theme.toggle();
                updateThemeButton();
                updateNavStyles();
            }
        });
    }

    // ------------------------------------------------------------------
    // file dialogs and tools
    // ------------------------------------------------------------------

    void chooseInputFile(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t("ui.select.the.artifact.to.protect"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                I18n.t("ui.java.archives.jar.war.zip"), "jar", "war", "zip"));
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            state.setInput(chooser.getSelectedFile().toPath());
            showPage(PAGE_DASHBOARD);
        }
    }

    void chooseOutputFile(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t("ui.where.should.the.protected.artifact.be.written"));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("ui.jar.files"), "jar"));
        chooser.setSelectedFile(new File("application-protected.jar"));
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        String path = chooser.getSelectedFile().getAbsolutePath();
        if (!path.toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            path += ".jar";
        }
        state.output.setText(path);
    }

    void openConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t("ui.open.zipit.configuration"));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("ui.yaml.configuration"), "yml", "yaml"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            ZipitConfig config = ConfigLoader.load(chooser.getSelectedFile().toPath());
            state.load(config);
            dashboardPage.refreshAll();
            rulesPage.refreshPresets();
            showPage(PAGE_DASHBOARD);
            log("INFO", "Loaded configuration " + chooser.getSelectedFile());
            setStatus(I18n.t("ui.configuration.loaded"));
        } catch (Exception e) {
            log("ERROR", "Unable to load configuration: " + e.getMessage());
            Dialogs.error(this, I18n.t("ui.unable.to.load.configuration"), e.getMessage());
        }
    }

    void saveConfig() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t("ui.save.zipit.configuration"));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("ui.yaml.configuration"), "yml", "yaml"));
        chooser.setSelectedFile(new File("zipit.yml"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            ZipitConfig config = state.toConfig();
            Files.write(chooser.getSelectedFile().toPath(),
                    ConfigWriter.toYaml(config).getBytes(StandardCharsets.UTF_8));
            log("INFO", "Saved configuration " + chooser.getSelectedFile());
            setStatus(I18n.t("ui.configuration.saved"));
        } catch (Exception e) {
            log("ERROR", "Unable to save configuration: " + e.getMessage());
            Dialogs.error(this, I18n.t("ui.unable.to.save.configuration"), e.getMessage());
        }
    }

    void openOutputFolder() {
        String text = state.output.getText().trim();
        if (text.isEmpty()) {
            Dialogs.info(this, I18n.t("ui.no.output"), I18n.t("ui.set.an.output.jar.first"));
            return;
        }
        Path parent;
        try {
            parent = Paths.get(text).toAbsolutePath().getParent();
        } catch (RuntimeException e) {
            parent = null;
        }
        if (parent == null || !Files.exists(parent)) {
            Dialogs.info(this, I18n.t("ui.output.folder"), I18n.t("ui.the.output.folder.does.not.exist.yet"));
            return;
        }
        openDesktop(parent.toFile());
    }

    void openReport() {
        String html = state.reportHtml.getText().trim();
        if (html.isEmpty()) {
            Dialogs.info(this, I18n.t("ui.audit.report"), I18n.t("ui.set.an.html.report.path.on.the.enterprise.page.first"));
            return;
        }
        Path path;
        try {
            path = Paths.get(html);
        } catch (RuntimeException e) {
            Dialogs.info(this, I18n.t("ui.audit.report"), I18n.t("ui.the.report.path.is.not.valid"));
            return;
        }
        if (!Files.exists(path)) {
            Dialogs.info(this, I18n.t("ui.audit.report"), I18n.t("frame.reportNotFound", path));
            return;
        }
        openDesktop(path.toFile());
    }

    void showDiffTool() {
        Path oldFile = chooseMappingFile(I18n.t("ui.select.the.old.mapping.file"));
        if (oldFile == null) {
            return;
        }
        Path newFile = chooseMappingFile(I18n.t("ui.select.the.new.mapping.file"));
        if (newFile == null) {
            return;
        }
        try {
            String diff = MappingDiff.compare(oldFile, newFile);
            Dialogs.result(this, "diff", I18n.t("ui.mapping.diff"),
                    oldFile.getFileName() + "  →  " + newFile.getFileName(), diff, "mapping-diff.txt");
            log("INFO", "Mapping diff generated for " + oldFile.getFileName());
        } catch (Exception e) {
            log("ERROR", "Mapping diff failed: " + e.getMessage());
            Dialogs.error(this, I18n.t("ui.mapping.diff.failed"), e.getMessage());
        }
    }

    private Path chooseMappingFile(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("ui.mapping.files"), "txt", "map"));
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath() : null;
    }

    void showTraceTool() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18n.t("ui.trace.a.zipit.watermark"));
        chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("frame.filter.javaArchives"), "jar", "zip", "war"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            List<String> marks = WatermarkTracer.trace(chooser.getSelectedFile().toPath());
            StringBuilder out = new StringBuilder();
            if (marks.isEmpty()) {
                out.append(I18n.t("frame.watermarkNone", I18n.t("frame.watermarkTip")));
            } else {
                for (String mark : marks) {
                    out.append(mark).append('\n');
                }
            }
            Dialogs.result(this, "fingerprint", I18n.t("ui.watermark.trace"),
                    chooser.getSelectedFile().getName(), out.toString(), "watermark-trace.txt");
            log("INFO", "Watermark trace: " + marks.size() + " mark(s) found");
        } catch (Exception e) {
            log("ERROR", "Watermark trace failed: " + e.getMessage());
            Dialogs.error(this, I18n.t("ui.watermark.trace.failed"), e.getMessage());
        }
    }

    void showAbout() {
        Dialogs.about(this);
    }

    private void openDesktop(File file) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file);
            } else {
                Dialogs.info(this, I18n.t("frame.desktopTitle"), I18n.t("frame.openNotSupported"));
            }
        } catch (Exception e) {
            Dialogs.error(this, I18n.t("frame.unableToOpen", ""), e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    GuiState state() {
        return state;
    }

    ConsolePage consolePage() {
        return consolePage;
    }

    EngineResult lastResult() {
        return lastResult;
    }

    private void installWindowIcon() {
        try {
            List<Image> icons = new ArrayList<Image>();
            Image small = loadImage("/zipit-icon-64.png");
            Image large = loadImage("/zipit-icon-256.png");
            if (small != null) {
                icons.add(small);
            }
            if (large != null) {
                icons.add(large);
            }
            if (icons.isEmpty()) {
                icons.add(BrandMark.appIcon(256));
                icons.add(BrandMark.appIcon(64));
            }
            setIconImages(icons);
        } catch (RuntimeException ignored) {
            setIconImage(BrandMark.appIcon(64));
        }
    }

    static Image loadImage(String resource) {
        try (InputStream in = ZipitFrame.class.getResourceAsStream(resource)) {
            return in == null ? null : ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /** Tiny LED-style status dot. */
    private static final class StatusDot extends JComponent {
        private Color color = Theme.ACCENT2;

        StatusDot() {
            Dimension size = new Dimension(Theme.px(8), Theme.px(8));
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
        }

        void setColor(Color color) {
            this.color = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.alpha(color, 55));
            int size = Math.min(getWidth(), getHeight());
            g2.fillOval(0, 0, size, size);
            g2.setColor(color);
            int inset = Math.max(1, size / 4);
            g2.fillOval(inset, inset, size - inset * 2, size - inset * 2);
            g2.dispose();
        }
    }
}
