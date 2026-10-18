package io.zipit.gui;

import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.Stats;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Landing page: artifact intake, pipeline summary, live run and output inventory. */
final class DashboardPage extends JPanel {

    private final ZipitFrame frame;
    private final GuiState state;
    private final DropZone dropZone;
    private final TerminalPanel miniTerminal = new TerminalPanel(true);
    private final RunProgress runProgress = new RunProgress();
    private final JLabel runPercent = Ui.colored("0%", () -> Theme.MUTED, 12f, Font.BOLD);
    private final JLabel runStage = Ui.colored(I18n.t("ui.idle.ready.to.protect"), () -> Theme.FAINT,
            12f, Font.PLAIN);
    private final JButton runButton = Ui.primary(I18n.t("ui.protect.jar"), "play");
    private final JButton cancelButton = Ui.danger(I18n.t("ui.cancel"), "stop");
    private final List<Chip> presetChips = new ArrayList<Chip>();

    private final StatTile classesTile = new StatTile("box", I18n.t("ui.classes"), () -> Theme.ACCENT);
    private final StatTile resourcesTile = new StatTile("database", I18n.t("ui.resources"),
            () -> Theme.ACCENT2);
    private final StatTile durationTile = new StatTile("clock", I18n.t("ui.duration"), () -> Theme.INFO);
    private final StatTile verificationTile = new StatTile("check-circle", I18n.t("ui.verification"),
            () -> Theme.SUCCESS);
    private final StatTile stringsTile = new StatTile("hash", I18n.t("ui.strings.encrypted"),
            () -> Theme.WARNING);
    private final StatTile flowTile = new StatTile("activity", I18n.t("ui.flow.methods"),
            () -> Theme.ACCENT);
    private final StatTile reflectionTile = new StatTile("sparkles", I18n.t("ui.reflection.sites"),
            () -> Theme.DANGER);
    private final StatTile licenseTile = new StatTile("fingerprint", I18n.t("ui.license"),
            () -> Theme.INFO);

    private final JPanel planContent = Ui.column();
    private final RunProgress planMeter = new RunProgress();
    private final JLabel planScore = Ui.colored("—", () -> Theme.TEXT, 32f, Font.BOLD);
    private Timer planDebounce;

    private Color verifyTone = Theme.FAINT;
    private final JLabel verifyStatus = Ui.colored(I18n.t("ui.not.run.yet"), () -> verifyTone,
            14f, Font.BOLD);
    private final JLabel verifyDetail = Ui.muted(I18n.t("ui.run.a.protection.to.link.every.class"));
    private final JTextArea verifyErrors = Ui.code(5, 48);
    private final JScrollPane verifyScroll;

    private final JTree inventoryTree = new JTree(new DefaultMutableTreeNode(I18n.t("ui.no.output.yet")));
    private final DefaultTreeModel inventoryModel = new DefaultTreeModel(
            new DefaultMutableTreeNode(I18n.t("ui.no.output.yet")));
    private final JPanel timingsContent = Ui.column();
    private final JLabel timingsSummary = Ui.faint(I18n.t("ui.no.run.yet"));

    DashboardPage(ZipitFrame frame, GuiState state) {
        super(new BorderLayout());
        this.frame = frame;
        this.state = state;
        setOpaque(false);

        PageHeader header = new PageHeader(I18n.t("ui.workspace.2"), I18n.t("ui.dashboard"),
                I18n.t("ui.protect.a.java.artifact.with.a.deterministic.auditable.pipeline"));
        JButton openConfig = Ui.button(I18n.t("ui.open.config"), "folder");
        openConfig.addActionListener(e -> frame.openConfig());
        JButton saveConfig = Ui.button(I18n.t("ui.save.config"), "save");
        saveConfig.addActionListener(e -> frame.saveConfig());
        JButton outputFolder = Ui.iconButton("folder", I18n.t("ui.open.output.folder"));
        outputFolder.addActionListener(e -> frame.openOutputFolder());
        header.actions(openConfig, saveConfig, outputFolder);
        add(header, BorderLayout.NORTH);

        JPanel body = Ui.pageBody();
        body.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(24), Theme.px(22),
                Theme.px(24)));

        dropZone = new DropZone(state, () -> frame.chooseInputFile(this));
        runButton.addActionListener(e -> frame.runNow());
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> frame.cancelRun());
        verifyErrors.setEditable(false);
        verifyScroll = new JScrollPane(verifyErrors);
        verifyScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        verifyScroll.getViewport().setBackground(Theme.CARD);
        verifyScroll.setVisible(false);

        body.add(weightedRow(0.57, dropZone, 0.43, buildSourceCard(), Theme.px(16)));
        body.add(Box.createVerticalStrut(Theme.px(12)));
        body.add(buildTileRow());
        body.add(Box.createVerticalStrut(Theme.px(10)));
        body.add(buildTileRow2());
        body.add(Box.createVerticalStrut(Theme.px(12)));
        body.add(weightedRow(0.55, buildPipelineCard(), 0.45, buildPlanCard(), Theme.px(16)));
        body.add(Box.createVerticalStrut(Theme.px(12)));
        body.add(weightedRow(0.6, buildRunCard(), 0.4, buildVerificationCard(), Theme.px(16)));
        body.add(Box.createVerticalStrut(Theme.px(12)));
        body.add(weightedRow(0.56, buildInventoryCard(), 0.44, buildTimingsCard(), Theme.px(16)));

        add(Ui.pageScroll(body), BorderLayout.CENTER);

        state.addChangeListener(new Runnable() {
            @Override
            public void run() {
                schedulePlanRefresh();
            }
        });
        refreshPlan();
        syncPresetChips();
    }

    // ------------------------------------------------------------------
    // hero: source & target card
    // ------------------------------------------------------------------

    private Card buildSourceCard() {
        Card card = new Card("file", I18n.t("ui.source.target"),
                I18n.t("ui.artifact.paths.deterministic.seed.and.quick.policy"));

        JPanel body = Ui.column();

        JPanel inputRow = artifactRow(I18n.t("ui.input"), state.input, "folder",
                () -> frame.chooseInputFile(this));
        JPanel outputRow = artifactRow(I18n.t("ui.output"), state.output, "folder",
                () -> frame.chooseOutputFile(this));
        body.add(Ui.limitHeight(inputRow));
        body.add(Box.createVerticalStrut(Theme.px(8)));
        body.add(Ui.limitHeight(outputRow));
        body.add(Box.createVerticalStrut(Theme.px(10)));

        JPanel seedRow = new JPanel(new BorderLayout(Theme.px(10), 0));
        seedRow.setOpaque(false);
        JLabel seedLabel = Ui.muted(I18n.t("ui.seed"));
        seedLabel.setFont(Theme.font(Font.BOLD, 12f));
        seedLabel.setPreferredSize(new Dimension(Theme.px(58), Theme.px(28)));
        seedRow.add(seedLabel, BorderLayout.WEST);
        state.seed.setPreferredSize(new Dimension(Theme.px(128), Theme.px(30)));
        state.seed.setMaximumSize(new Dimension(Theme.px(128), Theme.px(30)));
        seedRow.add(state.seed, BorderLayout.CENTER);
        JLabel seedHint = Ui.faint(I18n.t("ui.same.seed.identical.output"));
        seedHint.setFont(Theme.font(Font.PLAIN, 12f));
        seedHint.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Theme.px(6)));
        seedRow.add(seedHint, BorderLayout.EAST);
        body.add(Ui.limitHeight(seedRow));
        body.add(Box.createVerticalStrut(Theme.px(12)));

        JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.px(6), 0));
        chipRow.setOpaque(false);
        String[][] quick = {
                {GuiState.PRESET_NONE, I18n.t("ui.default")},
                {"library", I18n.t("ui.library")},
                {"spring-boot", I18n.t("ui.spring")},
                {"aggressive", I18n.t("ui.aggressive")},
        };
        for (final String[] preset : quick) {
            Chip chip = new Chip(preset[1]);
            chip.addActionListener(e -> state.selectPreset(preset[0]));
            presetChips.add(chip);
            chipRow.add(chip);
        }
        JButton allPresets = Ui.link(I18n.t("ui.all.presets"));
        allPresets.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_RULES));
        chipRow.add(allPresets);
        body.add(Ui.limitHeight(chipRow));
        body.add(Box.createVerticalStrut(Theme.px(12)));

        SwitchRow verifyRow = new SwitchRow(I18n.t("ui.verify.output"),
                I18n.t("ui.link.every.protected.class.before.shipping"), state.verify);
        SwitchRow dryRunRow = new SwitchRow(I18n.t("ui.dry.run"),
                I18n.t("ui.analyze.and.build.the.audit.model.without.writing.an.artifact"), state.dryRun);
        body.add(Ui.limitHeight(verifyRow));
        body.add(Ui.limitHeight(dryRunRow));
        body.add(Box.createVerticalStrut(Theme.px(12)));

        JPanel actions = new JPanel(new BorderLayout(Theme.px(10), 0));
        actions.setOpaque(false);
        JPanel rightActions = new JPanel();
        rightActions.setOpaque(false);
        rightActions.setLayout(new BoxLayout(rightActions, BoxLayout.X_AXIS));
        JButton advanced = Ui.ghost(I18n.t("ui.all.settings"), "transforms");
        advanced.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_TRANSFORMS));
        cancelButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        runButton.setAlignmentY(Component.CENTER_ALIGNMENT);
        rightActions.add(advanced);
        rightActions.add(Box.createHorizontalStrut(Theme.px(6)));
        rightActions.add(cancelButton);
        rightActions.add(Box.createHorizontalStrut(Theme.px(6)));
        rightActions.add(runButton);
        actions.add(rightActions, BorderLayout.EAST);
        body.add(Ui.limitHeight(actions));

        card.body(body);
        return card;
    }

    private JPanel artifactRow(String caption, javax.swing.JTextField field, String icon,
                               final Runnable browse) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(10), 0));
        row.setOpaque(false);
        JLabel label = Ui.muted(caption);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setPreferredSize(new Dimension(Theme.px(58), Theme.px(28)));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        JButton button = Ui.iconButton(icon, I18n.t("dialog.browseFor",
                caption.toLowerCase(java.util.Locale.ROOT)), 30);
        button.addActionListener(e -> browse.run());
        row.add(button, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(34)));
        return row;
    }

    private JPanel buildTileRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, Theme.px(14), Theme.px(14)));
        row.setOpaque(false);
        row.add(classesTile);
        row.add(resourcesTile);
        row.add(durationTile);
        row.add(verificationTile);
        return row;
    }

    private JPanel buildTileRow2() {
        JPanel row = new JPanel(new GridLayout(1, 4, Theme.px(14), Theme.px(14)));
        row.setOpaque(false);
        row.add(stringsTile);
        row.add(flowTile);
        row.add(reflectionTile);
        row.add(licenseTile);
        return row;
    }

    // ------------------------------------------------------------------
    // pipeline quick switches
    // ------------------------------------------------------------------

    private Card buildPipelineCard() {
        Card card = new Card("transforms", I18n.t("ui.protection.pipeline"),
                I18n.t("ui.high.impact.transforms.tune.everything.on.the.transforms.page"));
        JButton all = Ui.link(I18n.t("ui.all.settings.2"));
        all.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_TRANSFORMS));
        card.headerAction(all);

        JPanel body = Ui.column();
        body.add(groupLabel(I18n.t("ui.naming.constants")));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.rename.symbols"),
                I18n.t("ui.classes.methods.and.fields.with.a.deterministic.dictionary"), state.rename)));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.encrypt.strings"),
                I18n.t("ui.decrypt.string.constants.at.their.call.sites"), state.stringEnc)));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.scramble.numbers"),
                I18n.t("ui.obfuscate.integer.and.long.constants"), state.intEnc)));
        body.add(Box.createVerticalStrut(Theme.px(8)));
        body.add(Ui.hline());
        body.add(Box.createVerticalStrut(Theme.px(8)));
        body.add(groupLabel(I18n.t("ui.control.flow.calls")));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.control.flow.predicates"),
                I18n.t("ui.opaque.branches.that.inflate.static.analysis.cost"), state.flow)));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.state.machine.flattening"),
                I18n.t("ui.rewrite.method.bodies.as.a.dispatcher.loop"), state.flatten)));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.method.virtualization"),
                I18n.t("ui.interpret.eligible.bodies.inside.a.custom.vm"), state.virtualize)));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.anti.llm.walls"),
                I18n.t("ui.decoy.semantics.and.nested.forwarding.layers"), state.antiLlm)));
        body.add(Box.createVerticalStrut(Theme.px(8)));
        body.add(Ui.hline());
        body.add(Box.createVerticalStrut(Theme.px(8)));
        body.add(groupLabel(I18n.t("ui.runtime.forensics")));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.anti.debug.guard"),
                I18n.t("ui.reject.common.jdwp.debug.agent.launches"), state.antiDebug)));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.watermark"),
                I18n.t("ui.embed.a.traceable.constant.and.manifest.attribute"), state.watermark)));
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.remove.source.attributes"),
                I18n.t("ui.strip.sourcefile.metadata.from.the.output.classes"), state.debugSource)));
        card.body(body);
        return card;
    }

    private JLabel groupLabel(String text) {
        JLabel label = Ui.overline(text);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(2), Theme.px(2),
                Theme.px(2)));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    // ------------------------------------------------------------------
    // protection plan
    // ------------------------------------------------------------------

    private Card buildPlanCard() {
        Card card = new Card("gauge", I18n.t("ui.protection.plan"),
                I18n.t("ui.live.strength.estimate.from.the.current.settings"),
                () -> Theme.ACCENT2);
        JButton refine = Ui.link(I18n.t("ui.refine"));
        refine.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_TRANSFORMS));
        card.headerAction(refine);
        card.body(planContent);
        return card;
    }

    private void schedulePlanRefresh() {
        if (planDebounce != null) {
            planDebounce.stop();
        }
        planDebounce = new Timer(140, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                ((Timer) e.getSource()).stop();
                refreshPlan();
            }
        });
        planDebounce.setRepeats(false);
        planDebounce.start();
    }

    void refreshPlan() {
        ProtectionPlan plan = ProtectionPlan.compute(state);
        planContent.removeAll();

        JPanel scoreRow = new JPanel(new BorderLayout(Theme.px(12), 0));
        scoreRow.setOpaque(false);
        JPanel scoreText = new JPanel();
        scoreText.setOpaque(false);
        scoreText.setLayout(new BoxLayout(scoreText, BoxLayout.X_AXIS));
        planScore.setText(String.valueOf(plan.score()));
        planScore.setFont(Theme.font(Font.BOLD, 34f));
        scoreText.add(planScore);
        JLabel outOf = Ui.faint("/ 100");
        outOf.setFont(Theme.font(Font.PLAIN, 13f));
        outOf.setBorder(BorderFactory.createEmptyBorder(Theme.px(12), Theme.px(3), 0, 0));
        outOf.setAlignmentY(Component.BOTTOM_ALIGNMENT);
        scoreText.add(outOf);
        scoreRow.add(scoreText, BorderLayout.WEST);

        Supplier<Color> gradeTone = plan.gradeColor();
        JLabel gradePill = Ui.pill("  " + plan.grade().toUpperCase(java.util.Locale.ROOT) + "  ",
                () -> Theme.alpha(gradeTone.get(), Theme.dark ? 44 : 28), gradeTone);
        gradePill.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        scoreRow.add(gradePill, BorderLayout.EAST);
        planContent.add(scoreRow);
        planContent.add(Box.createVerticalStrut(Theme.px(8)));

        planMeter.setProgress(plan.score());
        planMeter.setRunning(false);
        planMeter.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(8)));
        planMeter.setPreferredSize(new Dimension(Theme.px(200), Theme.px(8)));
        planContent.add(planMeter);
        planContent.add(Box.createVerticalStrut(Theme.px(4)));
        JLabel active = Ui.faint(I18n.t("dashboard.planSummary", plan.activeCount(), plan.totalCount()));
        active.setFont(Theme.font(Font.PLAIN, 12f));
        active.setAlignmentX(Component.LEFT_ALIGNMENT);
        planContent.add(active);
        planContent.add(Box.createVerticalStrut(Theme.px(10)));

        JPanel grid = new JPanel(new GridLayout(0, 2, Theme.px(6), Theme.px(3)));
        grid.setOpaque(false);
        for (ProtectionPlan.Stage stage : plan.stages()) {
            JPanel cell = new JPanel(new BorderLayout(Theme.px(6), 0));
            cell.setOpaque(false);
            JLabel dot = Ui.iconLabel(stage.enabled ? "check-circle" : "dot", 13,
                    () -> stage.enabled ? Theme.SUCCESS : Theme.FAINT);
            cell.add(dot, BorderLayout.WEST);
            JLabel name = Ui.colored(stage.label,
                    () -> stage.enabled ? Theme.TEXT : Theme.MUTED, 12f,
                    stage.enabled ? Font.BOLD : Font.PLAIN);
            cell.add(name, BorderLayout.CENTER);
            grid.add(cell);
        }
        planContent.add(grid);
        planContent.add(Box.createVerticalStrut(Theme.px(10)));

        List<ProtectionPlan.Stage> suggestions = plan.suggestions();
        if (!suggestions.isEmpty()) {
            planContent.add(Box.createVerticalStrut(Theme.px(2)));
            JLabel next = groupLabel(I18n.t("ui.next.best.actions"));
            planContent.add(next);
            planContent.add(Box.createVerticalStrut(Theme.px(3)));
            for (ProtectionPlan.Stage stage : suggestions) {
                JPanel row = new JPanel(new BorderLayout(Theme.px(6), 0));
                row.setOpaque(false);
                JLabel plus = Ui.iconLabel("plus", 12, () -> Theme.ACCENT2);
                row.add(plus, BorderLayout.WEST);
                JLabel text = Ui.muted(I18n.t("plan.enableAction", stage.label, stage.weight));
                text.setFont(Theme.font(Font.PLAIN, 12f));
                row.add(text, BorderLayout.CENTER);
                planContent.add(row);
            }
        }
        planContent.revalidate();
        planContent.repaint();
        syncPresetChips();
    }

    private void syncPresetChips() {
        String current = state.presetName();
        String[] values = {GuiState.PRESET_NONE, "library", "spring-boot", "aggressive"};
        for (int i = 0; i < presetChips.size() && i < values.length; i++) {
            presetChips.get(i).setSelected(values[i].equals(
                    current.isEmpty() ? GuiState.PRESET_NONE : current));
        }
    }

    // ------------------------------------------------------------------
    // run card with live log
    // ------------------------------------------------------------------

    private Card buildRunCard() {
        Card card = new Card("console", I18n.t("ui.run.live.output"),
                I18n.t("ui.progress.verification.and.the.engine.log.stream"));
        JButton console = Ui.link(I18n.t("ui.open.console"));
        console.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_CONSOLE));
        card.headerAction(console);

        JPanel body = new JPanel(new BorderLayout(0, Theme.px(10)));
        body.setOpaque(false);

        JPanel progressRow = new JPanel(new BorderLayout(Theme.px(10), 0));
        progressRow.setOpaque(false);
        JPanel barPanel = new JPanel(new BorderLayout(Theme.px(8), 0));
        barPanel.setOpaque(false);
        runProgress.setPreferredSize(new Dimension(Theme.px(300), Theme.px(8)));
        barPanel.add(runProgress, BorderLayout.CENTER);
        runPercent.setFont(Theme.font(Font.BOLD, 12f));
        runPercent.setPreferredSize(new Dimension(Theme.px(38), Theme.px(16)));
        barPanel.add(runPercent, BorderLayout.EAST);
        progressRow.add(barPanel, BorderLayout.CENTER);
        runStage.setFont(Theme.font(Font.PLAIN, 12f));
        progressRow.add(runStage, BorderLayout.SOUTH);
        body.add(progressRow, BorderLayout.NORTH);

        miniTerminal.setPreferredSize(new Dimension(Theme.px(320), Theme.px(158)));
        body.add(miniTerminal, BorderLayout.CENTER);
        card.fill(body);
        return card;
    }

    private Card buildVerificationCard() {
        Card card = new Card("shield", I18n.t("ui.verification"),
                I18n.t("ui.per.class.link.check.on.the.protected.output"), () -> Theme.SUCCESS);
        JPanel body = Ui.column();
        verifyStatus.setFont(Theme.font(Font.BOLD, 14f));
        verifyStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        verifyDetail.setFont(Theme.font(Font.PLAIN, 12f));
        verifyDetail.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(verifyStatus);
        body.add(Box.createVerticalStrut(Theme.px(4)));
        body.add(verifyDetail);
        body.add(Box.createVerticalStrut(Theme.px(8)));
        verifyScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        verifyScroll.setPreferredSize(new Dimension(Theme.px(320), Theme.px(160)));
        verifyScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(200)));
        body.add(verifyScroll);
        card.body(body);
        setVerification(I18n.t("ui.not.run.yet"), Theme.FAINT,
                I18n.t("ui.run.a.protection.to.link.every.class"), false);
        return card;
    }

    private void setVerification(String status, Color tone, String detail, boolean failed) {
        verifyTone = tone;
        verifyStatus.setText(status);
        verifyStatus.repaint();
        verifyDetail.setText(detail);
        verifyScroll.setVisible(failed);
        if (verifyStatus.getParent() != null) {
            verifyStatus.getParent().revalidate();
        }
    }

    // ------------------------------------------------------------------
    // inventory & pass timings
    // ------------------------------------------------------------------

    private Card buildInventoryCard() {
        Card card = new Card("box", I18n.t("ui.output.inventory"),
                I18n.t("ui.classes.and.resources.inside.the.protected.artifact"));
        inventoryTree.setModel(inventoryModel);
        inventoryTree.setRootVisible(true);
        inventoryTree.setShowsRootHandles(true);
        inventoryTree.setRowHeight(Theme.px(24));
        inventoryTree.setFont(Theme.mono(12f));
        inventoryTree.setBackground(Theme.CARD);
        inventoryTree.setOpaque(true);
        inventoryTree.setBorder(BorderFactory.createEmptyBorder(Theme.px(4), Theme.px(6),
                Theme.px(4), Theme.px(6)));
        inventoryTree.setCellRenderer(new InventoryRenderer());
        JScrollPane scroll = new JScrollPane(inventoryTree);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        scroll.getViewport().setBackground(Theme.CARD);
        scroll.setPreferredSize(new Dimension(Theme.px(320), Theme.px(240)));
        scroll.getVerticalScrollBar().setUnitIncrement(Theme.px(18));
        card.fill(scroll);
        return card;
    }

    private Card buildTimingsCard() {
        Card card = new Card("activity", I18n.t("ui.pass.timings"),
                I18n.t("ui.where.the.engine.spent.its.time"), () -> Theme.WARNING);
        timingsSummary.setFont(Theme.font(Font.PLAIN, 12f));
        timingsSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
        timingsContent.add(timingsSummary);
        card.body(timingsContent);
        return card;
    }

    private void rebuildTimings(EngineResult result) {
        timingsContent.removeAll();
        Map<String, Long> timings = result.passTimes();
        timingsSummary.setText(timings.isEmpty() ? I18n.t("ui.no.timed.passes.dry.run")
                : I18n.t("dashboard.passTimings", timings.size(), result.millis()));
        timingsContent.add(timingsSummary);
        if (timings.isEmpty()) {
            timingsContent.revalidate();
            timingsContent.repaint();
            return;
        }
        List<Map.Entry<String, Long>> entries =
                new ArrayList<Map.Entry<String, Long>>(timings.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Long>>() {
            @Override
            public int compare(Map.Entry<String, Long> a, Map.Entry<String, Long> b) {
                return Long.compare(b.getValue(), a.getValue());
            }
        });
        long max = 1;
        for (Map.Entry<String, Long> entry : entries) {
            max = Math.max(max, entry.getValue());
        }
        int shown = 0;
        for (Map.Entry<String, Long> entry : entries) {
            if (shown++ >= 9) {
                break;
            }
            JPanel row = new JPanel(new BorderLayout(Theme.px(8), 0));
            row.setOpaque(false);
            JLabel name = Ui.colored(entry.getKey(), () -> Theme.MUTED, 12f, Font.PLAIN);
            name.setFont(Theme.mono(12f));
            name.setPreferredSize(new Dimension(Theme.px(148), Theme.px(18)));
            name.setToolTipText(entry.getKey());
            row.add(name, BorderLayout.WEST);
            TimingBar bar = new TimingBar(entry.getValue() / (float) max);
            row.add(bar, BorderLayout.CENTER);
            JLabel ms = Ui.faint(entry.getValue() + " ms");
            ms.setFont(Theme.mono(12f));
            ms.setPreferredSize(new Dimension(Theme.px(58), Theme.px(18)));
            ms.setHorizontalAlignment(JLabel.RIGHT);
            row.add(ms, BorderLayout.EAST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(22)));
            timingsContent.add(Box.createVerticalStrut(Theme.px(2)));
            timingsContent.add(row);
        }
        timingsContent.revalidate();
        timingsContent.repaint();
    }

    private void rebuildInventory(Path jar) {
        DefaultMutableTreeNode root;
        try {
            root = new DefaultMutableTreeNode(jar.getFileName().toString());
            DefaultMutableTreeNode classes = new DefaultMutableTreeNode(I18n.t("ui.classes"));
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
        } catch (Exception e) {
            root = new DefaultMutableTreeNode(I18n.t("dashboard.readOutputFailed", e.getMessage()));
        }
        inventoryModel.setRoot(root);
        inventoryTree.setModel(inventoryModel);
        for (int i = 0; i < inventoryTree.getRowCount(); i++) {
            inventoryTree.expandRow(i);
        }
    }

    // ------------------------------------------------------------------
    // run lifecycle callbacks
    // ------------------------------------------------------------------

    void appendLog(String level, String message) {
        miniTerminal.append(level, message);
    }

    void onPageShown() {
        refreshPlan();
    }

    void refreshAll() {
        dropZone.refresh();
        refreshPlan();
    }

    void onRunStarted() {
        miniTerminal.clear();
        runProgress.setProgress(0);
        runProgress.setRunning(true);
        runPercent.setText("0%");
        runStage.setText(I18n.t("ui.preparing.pipeline"));
        cancelButton.setVisible(true);
        cancelButton.setEnabled(true);
        runButton.setEnabled(false);
        runButton.setText(I18n.t("ui.protecting"));
        runButton.revalidate();
        verificationTile.setValue(I18n.t("ui.pending"));
        verificationTile.setSub(I18n.t("ui.link.check.running"));
        setVerification(I18n.t("ui.running"), Theme.WARNING, I18n.t("ui.linking.protected.classes"), false);
        timingsSummary.setText(I18n.t("ui.collecting.timings"));
        timingsContent.removeAll();
        timingsContent.add(timingsSummary);
        timingsContent.revalidate();
        timingsContent.repaint();
        revalidate();
        repaint();
    }

    void onProgress(int percent, String message) {
        runProgress.setProgress(percent);
        runPercent.setText(percent + "%");
        runStage.setText(message);
        if (percent >= 100) {
            runStage.setText(I18n.t("ui.finalizing"));
        }
    }

    void onRunFinished(EngineResult result, List<String> verifyErrors) {
        runProgress.setRunning(false);
        runProgress.setProgress(100);
        runPercent.setText("100%");
        runStage.setText(I18n.t("ui.complete") + Ui.humanDuration(result.millis()));
        cancelButton.setVisible(false);
        runButton.setEnabled(true);
        runButton.setText(I18n.t("ui.protect.jar"));
        runButton.revalidate();
        revalidate();
        repaint();

        classesTile.setValue(String.valueOf(result.classes()));
        classesTile.setSub(I18n.t("ui.in.protected.artifact"));
        resourcesTile.setValue(String.valueOf(result.resources()));
        resourcesTile.setSub(I18n.t("ui.copied.rewritten"));
        durationTile.setValue(Ui.humanDuration(result.millis()));
        durationTile.setSub(I18n.t("ui.wall.clock"));
        Stats stats = result.stats();
        stringsTile.setValue(String.valueOf(stat(stats, "strings.encrypted")));
        stringsTile.setSub(I18n.t("ui.decrypted.at.runtime"));
        flowTile.setValue(String.valueOf(stat(stats, "flow.methods")));
        flowTile.setSub(I18n.t("ui.methods.rewritten"));
        if (result.risk() != null) {
            reflectionTile.setValue(String.valueOf(result.risk().reflectionCallSites()));
            reflectionTile.setSub(I18n.t("ui.pre.scan.call.sites"));
        }
        if (result.license() == null) {
            licenseTile.setValue(I18n.t("ui.community"));
            licenseTile.setSub(I18n.t("ui.edition.2"));
        } else if (result.license().isValid()) {
            licenseTile.setValue(result.license().edition());
            licenseTile.setSub(result.license().licensee());
        } else {
            licenseTile.setValue(I18n.t("ui.invalid"));
            licenseTile.setSub(result.license().error());
        }

        if (result.dryRun()) {
            verificationTile.setValue(I18n.t("ui.n.a"));
            verificationTile.setSub(I18n.t("ui.dry.run.2"));
            setVerification(I18n.t("ui.dry.run"), Theme.INFO,
                    I18n.t("ui.no.artifact.was.written.so.verification.was.skipped"), false);
        } else if (!state.verify.isSelected()) {
            verificationTile.setValue(I18n.t("ui.off"));
            verificationTile.setSub(I18n.t("ui.enable.in.source.card"));
            setVerification(I18n.t("ui.verification.off"), Theme.MUTED,
                    I18n.t("ui.enable.verify.output.to.link.classes.after.each.run"), false);
        } else if (verifyErrors.isEmpty()) {
            verificationTile.setValue(I18n.t("dashboard.verifyOk"));
            verificationTile.setSub(result.classes() + I18n.t("ui.classes.linked"));
            setVerification(I18n.t("ui.ok.all.classes.linked"), Theme.SUCCESS,
                    result.classes() + I18n.t("ui.classes.loaded.without.linkage.errors"), false);
        } else {
            verificationTile.setValue(verifyErrors.size() + I18n.t("ui.failed"));
            verificationTile.setSub(I18n.t("ui.open.console.for.detail"));
            StringBuilder text = new StringBuilder();
            for (String error : verifyErrors) {
                text.append(error).append('\n');
            }
            verifyErrorsBox(text.toString());
            setVerification(verifyErrors.size() + I18n.t("ui.class.es.failed"), Theme.DANGER,
                    I18n.t("ui.the.jvm.rejected.the.protected.bytecode.see.details.below"), true);
        }

        rebuildInventory(result.output());
        rebuildTimings(result);
        refreshPlan();
        if (result.output() != null && !result.dryRun()) {
            runStage.setText(I18n.t("ui.complete") + result.output().getFileName());
        }
    }

    private void verifyErrorsBox(String text) {
        verifyErrors.setText(text);
        verifyErrors.setCaretPosition(0);
        if (verifyScroll.getParent() != null) {
            verifyScroll.getParent().revalidate();
        }
    }

    void onRunCancelled() {
        runProgress.setRunning(false);
        runProgress.setProgress(0);
        runPercent.setText("0%");
        runStage.setText(I18n.t("ui.cancelled"));
        cancelButton.setVisible(false);
        runButton.setEnabled(true);
        runButton.setText(I18n.t("ui.protect.jar"));
        runButton.revalidate();
        setVerification(I18n.t("ui.cancelled"), Theme.WARNING,
                I18n.t("ui.the.run.was.cancelled.before.completion"), false);
        revalidate();
        repaint();
    }

    private static long stat(Stats stats, String key) {
        if (stats == null) {
            return 0L;
        }
        Map<String, Long> values = stats.snapshot();
        Long value = values.get(key);
        return value == null ? 0L : value.longValue();
    }

    // ------------------------------------------------------------------
    // layout and small components
    // ------------------------------------------------------------------

    private static JPanel weightedRow(double leftWeight, JComponent left,
                                      double rightWeight, JComponent right, int gap) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weighty = 1;
        constraints.insets = new Insets(0, 0, 0, gap / 2);
        constraints.gridx = 0;
        constraints.weightx = leftWeight;
        row.add(left, constraints);
        constraints.gridx = 1;
        constraints.weightx = rightWeight;
        constraints.insets = new Insets(0, gap / 2, 0, 0);
        row.add(right, constraints);
        return row;
    }

    /** Thin horizontal bar used by the pass timing list. */
    private final class TimingBar extends JComponent {
        private final float fraction;

        TimingBar(float fraction) {
            this.fraction = Math.max(0.02f, Math.min(1f, fraction));
            setOpaque(false);
            setPreferredSize(new Dimension(Theme.px(60), Theme.px(8)));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int height = Math.max(Theme.px(5), getHeight() - Theme.px(6));
            int y = (getHeight() - height) / 2;
            g2.setColor(Theme.dark ? new Color(0x1B2536) : new Color(0xE7ECF4));
            g2.fill(new RoundRectangle2D.Double(0, y, getWidth(), height, height, height));
            int width = Math.max(Theme.px(3), Math.round(getWidth() * fraction));
            g2.setPaint(new java.awt.GradientPaint(0, 0, Theme.ACCENT,
                    width, 0, Theme.ACCENT2));
            g2.fill(new RoundRectangle2D.Double(0, y, width, height, height, height));
            g2.dispose();
        }
    }

    /** Tree renderer that follows the theme and uses vector folder/file icons. */
    private final class InventoryRenderer extends DefaultTreeCellRenderer {
        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value,
                                                      boolean selected, boolean expanded,
                                                      boolean leaf, int row, boolean hasFocus) {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row,
                    hasFocus);
            setFont(Theme.mono(12f));
            setTextNonSelectionColor(leaf ? Theme.MUTED : Theme.TEXT);
            setTextSelectionColor(Theme.TEXT);
            setBackgroundNonSelectionColor(Theme.CARD);
            setBackgroundSelectionColor(Theme.ACCENT_SOFT);
            setBorderSelectionColor(null);
            if (leaf) {
                setIcon(Icons.icon("file", Theme.px(14), Theme.FAINT));
            } else {
                setIcon(Icons.icon(expanded ? "folder" : "folder", Theme.px(14),
                        Theme.ACCENT2));
            }
            return this;
        }
    }
}
