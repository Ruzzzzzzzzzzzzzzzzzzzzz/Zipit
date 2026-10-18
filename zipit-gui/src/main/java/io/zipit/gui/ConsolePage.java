package io.zipit.gui;

import io.zipit.core.engine.EngineResult;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.Map;

/** Full-page terminal with run summary badges. */
final class ConsolePage extends JPanel {

    private final ZipitFrame frame;
    private final TerminalPanel terminal = new TerminalPanel(false);
    private final JButton runButton = Ui.primary(I18n.t("ui.protect.jar"), "play");
    private final JButton cancelButton = Ui.danger(I18n.t("ui.cancel"), "stop");
    private final JLabel artifact = Ui.colored(I18n.t("ui.no.artifact.protected.yet"),
            () -> Theme.MUTED, 12f, Font.PLAIN);
    private final JLabel passes = Ui.colored("—", () -> Theme.MUTED, 12f, Font.BOLD);
    private final JLabel duration = Ui.colored("—", () -> Theme.MUTED, 12f, Font.BOLD);
    private final JLabel warnings = Ui.colored("0", () -> Theme.WARNING, 12f, Font.BOLD);
    private final JLabel errors = Ui.colored("0", () -> Theme.DANGER, 12f, Font.BOLD);

    ConsolePage(final ZipitFrame frame, GuiState state) {
        super(new BorderLayout());
        this.frame = frame;
        setOpaque(false);

        PageHeader header = new PageHeader(I18n.t("nav.section.operationsPage"), I18n.t("ui.console"),
                I18n.t("ui.complete.engine.log.filter.levels.search.messages.and.export"));
        JButton dashboard = Ui.button(I18n.t("ui.dashboard"), "dashboard");
        dashboard.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_DASHBOARD));
        cancelButton.setVisible(false);
        cancelButton.addActionListener(e -> frame.cancelRun());
        runButton.addActionListener(e -> frame.runNow());
        header.actions(dashboard, cancelButton, runButton);
        add(header, BorderLayout.NORTH);

        JPanel container = new JPanel(new BorderLayout(0, Theme.px(10)));
        container.setOpaque(false);
        container.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(26), Theme.px(26),
                Theme.px(26)));
        container.add(terminal, BorderLayout.CENTER);
        container.add(buildFooter(), BorderLayout.SOUTH);
        add(container, BorderLayout.CENTER);
    }

    TerminalPanel terminal() {
        return terminal;
    }

    private JComponent buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(Theme.px(10), 0));
        footer.setOpaque(false);
        JPanel summary = new JPanel();
        summary.setOpaque(false);
        summary.setLayout(new BoxLayout(summary, BoxLayout.X_AXIS));
        artifact.setFont(Theme.font(Font.PLAIN, 12f));
        summary.add(artifact);
        summary.add(Box.createHorizontalStrut(Theme.px(16)));
        summary.add(badge(I18n.t("ui.passes"), passes));
        summary.add(Box.createHorizontalStrut(Theme.px(10)));
        summary.add(badge(I18n.t("ui.duration"), duration));
        summary.add(Box.createHorizontalStrut(Theme.px(10)));
        summary.add(badge(I18n.t("ui.warnings"), warnings));
        summary.add(Box.createHorizontalStrut(Theme.px(10)));
        summary.add(badge(I18n.t("ui.errors"), errors));
        footer.add(summary, BorderLayout.WEST);

        JPanel hint = new JPanel();
        hint.setOpaque(false);
        hint.setLayout(new BoxLayout(hint, BoxLayout.X_AXIS));
        JLabel text = Ui.faint(I18n.t("ui.ctrl.r.run.ctrl.t.theme.ctrl.5.console"));
        text.setFont(Theme.font(Font.PLAIN, 12f));
        hint.add(text);
        footer.add(hint, BorderLayout.EAST);
        return Ui.limitHeight(footer);
    }

    private JComponent badge(String caption, JLabel value) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        JLabel label = Ui.faint(caption);
        label.setFont(Theme.font(Font.PLAIN, 12f));
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, Theme.px(5)));
        panel.add(label);
        panel.add(value);
        return panel;
    }

    void onRunStarted() {
        runButton.setEnabled(false);
        runButton.setText(I18n.t("ui.protecting"));
        runButton.revalidate();
        cancelButton.setVisible(true);
        cancelButton.setEnabled(true);
        artifact.setText(I18n.t("ui.protecting"));
        passes.setText("—");
        duration.setText("—");
        warnings.setText("0");
        errors.setText("0");
        revalidate();
        repaint();
    }

    void onRunFinished(EngineResult result) {
        runButton.setEnabled(true);
        runButton.setText(I18n.t("ui.protect.jar"));
        runButton.revalidate();
        cancelButton.setVisible(false);
        if (result != null) {
            artifact.setText(result.output() == null ? I18n.t("ui.dry.run")
                    : result.output().getFileName().toString());
            int passCount = 0;
            long passMillis = 0;
            for (Map.Entry<String, Long> entry : result.passTimes().entrySet()) {
                passCount++;
                passMillis += entry.getValue();
            }
            passes.setText(passCount == 0 ? "—" : String.valueOf(passCount));
            duration.setText(I18n.t("console.durationSummary",
                    Ui.humanDuration(result.millis()), Ui.humanDuration(passMillis)));
        }
        warnings.setText(String.valueOf(terminal.count("WARN")));
        errors.setText(String.valueOf(terminal.count("ERROR")));
        revalidate();
        repaint();
    }

    void onRunCancelled() {
        runButton.setEnabled(true);
        runButton.setText(I18n.t("ui.protect.jar"));
        cancelButton.setVisible(false);
        artifact.setText(I18n.t("ui.run.cancelled"));
        revalidate();
        repaint();
    }
}
