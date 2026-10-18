package io.zipit.gui;

import io.zipit.core.enterprise.LicenseInfo;
import io.zipit.core.enterprise.LicenseOnlineValidator;
import io.zipit.core.enterprise.LicenseValidator;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Licensing, audit reports and enterprise release tools. */
final class EnterprisePage extends JPanel {

    private final ZipitFrame frame;
    private final GuiState state;
    private Color licenseTone = Theme.MUTED;
    private final JLabel licenseStatus = Ui.colored(I18n.t("ui.community.edition.2"),
            () -> licenseTone, 15f, Font.BOLD);
    private final JLabel licenseDetail = Ui.muted(
            I18n.t("enterprise.msg.communityMode"));
    private final JLabel fieldLicensee = Ui.faint("—");
    private final JLabel fieldEdition = Ui.faint("—");
    private final JLabel fieldExpires = Ui.faint("—");
    private final JLabel fieldFeatures = Ui.faint("—");
    private Color authTone = Theme.FAINT;
    private final JLabel authStatus = Ui.colored(I18n.t("enterprise.msg.notTested"), () -> authTone,
            14f, Font.BOLD);
    private final JLabel authDetail = Ui.muted(
            I18n.t("enterprise.msg.probeHint"));

    EnterprisePage(ZipitFrame frame, GuiState state) {
        super(new BorderLayout());
        this.frame = frame;
        this.state = state;
        setOpaque(false);

        PageHeader header = new PageHeader(I18n.t("nav.section.operationsPage"), I18n.t("ui.enterprise"),
                I18n.t("ui.signed.licensing.audit.reports.and.release.review.tooling"));
        JButton dashboard = Ui.button(I18n.t("ui.dashboard"), "dashboard");
        dashboard.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_DASHBOARD));
        JButton protect = Ui.primary(I18n.t("ui.protect.jar"), "play");
        protect.addActionListener(e -> frame.runNow());
        header.actions(dashboard, protect);
        add(header, BorderLayout.NORTH);

        JPanel body = Ui.pageBody();
        body.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(24), Theme.px(22),
                Theme.px(24)));

        addSectionHeader(body, "01", I18n.t("ui.software.authorization.auth.gate"),
                I18n.t("ui.multi.software.auth.server.register.accounts.activate.card"));
        body.add(buildAuthGateCard());
        body.add(Box.createVerticalStrut(Theme.px(14)));

        addSectionHeader(body, "02", I18n.t("ui.licensing"),
                I18n.t("ui.rsa.sha256.signed.licenses.validated.locally.or.against.a.server"));
        body.add(twoColumns(buildLicenseCard(), buildStatusCard()));
        body.add(Box.createVerticalStrut(Theme.px(14)));

        addSectionHeader(body, "03", I18n.t("ui.audit.release.tooling"),
                I18n.t("ui.reports.mapping.diffs.watermark.traces.and.output.access"));
        body.add(twoColumns(buildAuditCard(), buildToolsCard()));
        body.add(Box.createVerticalStrut(Theme.px(14)));

        addSectionHeader(body, "04", I18n.t("ui.workflow"),
                I18n.t("ui.how.enterprise.teams.ship.protected.artifacts"));
        body.add(buildWorkflowCard());

        add(Ui.pageScroll(body), BorderLayout.CENTER);
        refreshStatus();
    }

    // ------------------------------------------------------------------
    // licensing
    // ------------------------------------------------------------------

    private Card buildLicenseCard() {
        Card card = new Card("lock", I18n.t("ui.license"),
                I18n.t("ui.point.zipit.at.a.signed.license.and.optional.public.key"),
                () -> Theme.ACCENT);
        JPanel body = Ui.column();
        body.add(fileRow(I18n.t("ui.license.file"), state.licenseFile, false, "yml", "yaml"));
        body.add(fieldGap());
        body.add(fileRow(I18n.t("ui.public.key"), state.licenseKey, false, "pem", "key", "txt"));
        body.add(fieldGap());
        body.add(textRow(I18n.t("ui.license.server"), state.licenseServer));
        body.add(fieldGap());
        body.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.require.valid.license"),
                I18n.t("ui.fail.the.run.when.the.license.is.missing.or.invalid"), state.licenseRequire)));
        body.add(Box.createVerticalStrut(Theme.px(10)));
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        JButton validate = Ui.button(I18n.t("ui.validate.license"), "check-circle");
        validate.addActionListener(e -> validateLicense());
        JButton online = Ui.button(I18n.t("ui.validate.online"), "server");
        online.addActionListener(e -> validateOnline());
        actions.add(validate);
        actions.add(Box.createHorizontalStrut(Theme.px(8)));
        actions.add(online);
        body.add(Ui.limitHeight(actions));
        JLabel hint = Ui.faint(I18n.t("ui.the.public.key.and.server.url.are.optional.for.offline.licenses"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(Box.createVerticalStrut(Theme.px(6)));
        body.add(hint);
        card.body(body);
        return card;
    }

    private Card buildStatusCard() {
        Card card = new Card("fingerprint", I18n.t("ui.license.status"),
                I18n.t("ui.current.edition.and.validated.claims"), () -> Theme.SUCCESS);
        JPanel body = Ui.column();
        licenseStatus.setFont(Theme.font(Font.BOLD, 15f));
        licenseStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(licenseStatus);
        body.add(Box.createVerticalStrut(Theme.px(4)));
        licenseDetail.setFont(Theme.font(Font.PLAIN, 12f));
        licenseDetail.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(licenseDetail);
        body.add(Box.createVerticalStrut(Theme.px(12)));
        body.add(Ui.hline());
        body.add(Box.createVerticalStrut(Theme.px(10)));
        body.add(claimRow(I18n.t("ui.licensee"), fieldLicensee));
        body.add(claimRow(I18n.t("ui.edition"), fieldEdition));
        body.add(claimRow(I18n.t("ui.expires"), fieldExpires));
        body.add(claimRow(I18n.t("ui.features"), fieldFeatures));
        body.add(Box.createVerticalStrut(Theme.px(10)));
        JLabel note = Ui.faint(I18n.t("ui.community.mode.remains.fully.functional.for.local.builds")
                + I18n.t("ui.licensed.mode.adds.release.governance"));
        note.setFont(Theme.font(Font.PLAIN, 12f));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(note);
        card.body(body);
        return card;
    }

    private JComponent claimRow(String caption, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(10), 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(Theme.px(2), 0, Theme.px(2), 0));
        JLabel label = Ui.muted(caption);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setPreferredSize(new Dimension(Theme.px(86), Theme.px(18)));
        row.add(label, BorderLayout.WEST);
        value.setFont(Theme.mono(12f));
        row.add(value, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(24)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private void refreshStatus() {
        String file = state.licenseFile.getText().trim();
        if (file.isEmpty()) {
            licenseTone = Theme.MUTED;
            licenseStatus.setText(I18n.t("ui.community.edition.2"));
            licenseDetail.setText(
                    I18n.t("enterprise.msg.communityMode"));
            setClaims("—", I18n.t("ui.community"), "—", "—");
        } else {
            licenseTone = Theme.INFO;
            licenseStatus.setText(I18n.t("ui.license.configured"));
            licenseDetail.setText(I18n.t("ui.validate.the.license.to.load.its.claims"));
            setClaims("—", "—", "—", "—");
        }
        licenseStatus.repaint();
    }

    private void setClaims(String licensee, String edition, String expires, String features) {
        fieldLicensee.setText(licensee == null || licensee.isEmpty() ? "—" : licensee);
        fieldEdition.setText(edition == null || edition.isEmpty() ? "—" : edition);
        fieldExpires.setText(expires == null || expires.isEmpty() ? "—" : expires);
        fieldFeatures.setText(features == null || features.isEmpty() ? "—" : features);
    }

    private void validateLicense() {
        String file = state.licenseFile.getText().trim();
        if (file.isEmpty()) {
            licenseTone = Theme.WARNING;
            licenseStatus.setText(I18n.t("ui.no.license.file"));
            licenseDetail.setText(I18n.t("ui.select.a.license.file.first"));
            licenseStatus.repaint();
            return;
        }
        try {
            String key = state.licenseKey.getText().trim();
            LicenseInfo info = LicenseValidator.validate(Paths.get(file),
                    key.isEmpty() ? null : Paths.get(key));
            if (info.isValid()) {
                licenseTone = Theme.SUCCESS;
                licenseStatus.setText(I18n.t("ui.valid") + info.edition());
                licenseDetail.setText(I18n.t("ui.signature.verified.for") + info.licensee()
                        + (info.isSignatureVerified() ? I18n.t("ui.rsa.sha256.ok") : ""));
                setClaims(info.licensee(), info.edition(), info.expires(),
                        join(info.features()));
                frame.log("SUCCESS", I18n.t("ui.license.valid") + info.licensee() + " · "
                        + info.edition() + " · expires " + info.expires());
            } else {
                licenseTone = Theme.DANGER;
                licenseStatus.setText(I18n.t("ui.invalid.license"));
                licenseDetail.setText(info.error());
                setClaims("—", "—", "—", "—");
                frame.log("ERROR", I18n.t("ui.license.invalid") + info.error());
            }
        } catch (Exception e) {
            licenseTone = Theme.DANGER;
            licenseStatus.setText(I18n.t("ui.validation.failed"));
            licenseDetail.setText(e.getMessage());
            frame.log("ERROR", "License validation failed: " + e.getMessage());
        }
        licenseStatus.repaint();
        revalidate();
        repaint();
    }

    private void validateOnline() {
        String file = state.licenseFile.getText().trim();
        String server = state.licenseServer.getText().trim();
        if (file.isEmpty() || server.isEmpty()) {
            licenseTone = Theme.WARNING;
            licenseStatus.setText(I18n.t("ui.missing.input"));
            licenseDetail.setText(I18n.t("ui.select.a.license.file.and.enter.the.license.server.url"));
            licenseStatus.repaint();
            return;
        }
        try {
            LicenseOnlineValidator.Result result = LicenseOnlineValidator.check(
                    Paths.get(file), server);
            if (result.isValid()) {
                licenseTone = Theme.SUCCESS;
                licenseStatus.setText(I18n.t("ui.online.validation.ok"));
                licenseDetail.setText(I18n.t("ui.the.license.server.accepted.this.license"));
                frame.log("SUCCESS", I18n.t("ui.online.license.validation.succeeded"));
            } else {
                licenseTone = Theme.DANGER;
                licenseStatus.setText(I18n.t("ui.online.validation.failed"));
                licenseDetail.setText(result.reason());
                frame.log("ERROR", "Online license validation failed: " + result.reason());
            }
        } catch (Exception e) {
            licenseTone = Theme.DANGER;
            licenseStatus.setText(I18n.t("ui.server.unreachable"));
            licenseDetail.setText(e.getMessage());
            frame.log("ERROR", "Online license validation error: " + e.getMessage());
        }
        licenseStatus.repaint();
        revalidate();
        repaint();
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "—";
        }
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(value);
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // audit and tools
    // ------------------------------------------------------------------

    private Card buildAuditCard() {
        Card card = new Card("file", I18n.t("ui.audit.reports"),
                I18n.t("ui.html.and.json.reports.written.by.every.protection.run"), () -> Theme.INFO);
        JPanel body = Ui.column();
        body.add(fileRow(I18n.t("ui.html.report"), state.reportHtml, true, "html", "htm"));
        body.add(fieldGap());
        body.add(fileRow(I18n.t("ui.json.report"), state.reportJson, true, "json"));
        body.add(Box.createVerticalStrut(Theme.px(10)));
        JPanel actions = new JPanel();
        actions.setOpaque(false);
        actions.setLayout(new BoxLayout(actions, BoxLayout.X_AXIS));
        JButton open = Ui.button(I18n.t("ui.open.last.report"), "external");
        open.addActionListener(e -> frame.openReport());
        JButton folder = Ui.button(I18n.t("ui.output.folder"), "folder");
        folder.addActionListener(e -> frame.openOutputFolder());
        actions.add(open);
        actions.add(Box.createHorizontalStrut(Theme.px(8)));
        actions.add(folder);
        body.add(Ui.limitHeight(actions));
        JLabel hint = Ui.faint(I18n.t("ui.reports.include.the.risk.scan.pass.timings.statistics.and"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(Box.createVerticalStrut(Theme.px(6)));
        body.add(hint);
        card.body(body);
        return card;
    }

    private Card buildToolsCard() {
        Card card = new Card("diff", I18n.t("ui.release.tools"),
                I18n.t("ui.compare.builds.and.trace.embedded.watermarks"), () -> Theme.WARNING);
        JPanel body = Ui.column();
        body.add(toolRow("diff", I18n.t("ui.compare.mapping.files.2"),
                I18n.t("ui.diff.two.mapping.files.and.export.the.changed.symbols"),
                () -> frame.showDiffTool()));
        body.add(toolRow("fingerprint", I18n.t("ui.trace.watermark.2"),
                I18n.t("ui.extract.the.manifest.and.field.marks.from.a.protected.jar"),
                () -> frame.showTraceTool()));
        body.add(toolRow("folder", I18n.t("ui.open.output.folder"),
                I18n.t("ui.reveal.the.directory.that.holds.the.protected.artifact"),
                () -> frame.openOutputFolder()));
        body.add(toolRow("info", I18n.t("ui.about.zipit"),
                I18n.t("ui.version.runtime.and.the.full.feature.matrix"),
                () -> frame.showAbout()));
        card.body(body);
        return card;
    }

    private JComponent toolRow(String icon, String title, String description,
                               final Runnable action) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(12), 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(Theme.px(4), 0, Theme.px(4), 0),
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BORDER)));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(52)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        row.add(new Card.IconChip(icon, () -> Theme.ACCENT2), BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel name = Ui.bold(title);
        name.setFont(Theme.font(Font.BOLD, 13f));
        text.add(name);
        JLabel desc = Ui.faint(description);
        desc.setFont(Theme.font(Font.PLAIN, 12f));
        text.add(desc);
        row.add(text, BorderLayout.CENTER);
        JLabel arrow = Ui.iconLabel("chevron-right", 16, () -> Theme.MUTED);
        row.add(arrow, BorderLayout.EAST);
        row.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (javax.swing.SwingUtilities.isLeftMouseButton(e)) {
                    action.run();
                }
            }

            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                row.setOpaque(false);
            }
        });
        return row;
    }

    private Card buildWorkflowCard() {
        Card card = new Card("layers", I18n.t("ui.enterprise.workflow"),
                I18n.t("ui.a.repeatable.path.from.build.to.release"), () -> Theme.ACCENT);
        JPanel grid = new JPanel(new GridLayout(2, 2, Theme.px(14), Theme.px(14)));
        grid.setOpaque(false);
        grid.add(step("01", I18n.t("ui.keep.mappings"), I18n.t("ui.archive.zipit.mapping.txt.next.to.every.artifact")));
        grid.add(step("02", I18n.t("ui.stable.releases"), I18n.t("ui.feed.the.previous.mapping.back.for.unchanged.names")));
        grid.add(step("03", I18n.t("ui.audit.trail"), I18n.t("ui.attach.the.json.report.to.the.release.ticket")));
        grid.add(step("04", I18n.t("ui.governance"), I18n.t("ui.require.a.signed.license.in.regulated.pipelines")));
        card.body(grid);
        return card;
    }

    private JComponent step(String index, String title, String description) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.BORDER, 1, true),
                BorderFactory.createEmptyBorder(Theme.px(10), Theme.px(12),
                        Theme.px(10), Theme.px(12))));
        JLabel number = Ui.colored(index, () -> Theme.ACCENT2, 12f, Font.BOLD);
        number.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(number);
        panel.add(Box.createVerticalStrut(Theme.px(3)));
        JLabel heading = Ui.bold(title);
        heading.setFont(Theme.font(Font.BOLD, 13f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(heading);
        panel.add(Box.createVerticalStrut(Theme.px(2)));
        JLabel text = Ui.muted(description);
        text.setFont(Theme.font(Font.PLAIN, 12f));
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(text);
        return panel;
    }

    // ------------------------------------------------------------------
    // Auth Gate
    // ------------------------------------------------------------------

    private Card buildAuthGateCard() {
        Card card = new Card("lock", I18n.t("ui.auth.gate.settings"),
                I18n.t("ui.login.register.activate.card.gate.injected.into.protected"),
                () -> Theme.ACCENT2);

        JPanel columns = new JPanel(new GridLayout(1, 2, Theme.px(20), 0));
        columns.setOpaque(false);

        JPanel left = Ui.column();
        left.add(authSwitch(I18n.t("ui.enable.auth.gate"),
                I18n.t("ui.protects.main.and.the.configured.entry.points.at.runtime"), state.authEnabled));
        left.add(Box.createVerticalStrut(Theme.px(6)));
        left.add(authField(I18n.t("ui.server.url"), state.authServer));
        left.add(Box.createVerticalStrut(Theme.px(6)));
        left.add(authField(I18n.t("ui.software.id"), state.authSoftware));
        left.add(Box.createVerticalStrut(Theme.px(6)));
        left.add(authField(I18n.t("ui.window.title"), state.authTitle));

        JPanel right = Ui.column();
        right.add(authSwitch(I18n.t("ui.allow.registration"),
                I18n.t("ui.users.can.create.an.account.inside.the.gate.window"), state.authRegister));
        right.add(Box.createVerticalStrut(Theme.px(6)));
        right.add(authField(I18n.t("ui.token.cache"), state.authCache));
        right.add(Box.createVerticalStrut(Theme.px(6)));
        right.add(authControl(I18n.t("ui.timeout.ms"), state.authTimeout));
        right.add(Box.createVerticalStrut(Theme.px(6)));
        right.add(authEntryPoints());

        columns.add(left);
        columns.add(right);
        card.body(columns);
        card.body(Box.createVerticalStrut(Theme.px(12)));

        JPanel actions = new JPanel(new BorderLayout(Theme.px(14), 0));
        actions.setOpaque(false);
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton test = Ui.button(I18n.t("ui.test.connection"), "server");
        test.addActionListener(e -> testAuthConnection());
        JButton console = Ui.button(I18n.t("ui.open.admin.console"), "external");
        console.addActionListener(e -> openAdminConsole());
        buttons.add(test);
        buttons.add(Box.createHorizontalStrut(Theme.px(10)));
        buttons.add(console);
        actions.add(buttons, BorderLayout.WEST);

        JPanel status = Ui.column();
        authStatus.setFont(Theme.font(Font.BOLD, 14f));
        authDetail.setFont(Theme.font(Font.PLAIN, 12f));
        status.add(authStatus);
        status.add(Box.createVerticalStrut(Theme.px(2)));
        status.add(authDetail);
        actions.add(status, BorderLayout.CENTER);
        card.body(actions);
        return card;
    }

    private JComponent authSwitch(String title, String description,
                                  javax.swing.JCheckBox model) {
        return Ui.limitHeight(new SwitchRow(title, description, model));
    }

    private JComponent authField(String caption, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(12), 0));
        row.setOpaque(false);
        JLabel label = Ui.muted(caption);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setPreferredSize(new Dimension(Theme.px(112), Theme.px(30)));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(36)));
        return row;
    }

    private JComponent authControl(String caption, JComponent control) {
        if (control instanceof javax.swing.JSpinner) {
            Dimension size = new Dimension(Theme.px(110), Theme.px(32));
            control.setPreferredSize(size);
            control.setMinimumSize(size);
            control.setMaximumSize(size);
        }
        JPanel row = new JPanel(new BorderLayout(Theme.px(12), 0));
        row.setOpaque(false);
        JLabel label = Ui.muted(caption);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setPreferredSize(new Dimension(Theme.px(112), Theme.px(30)));
        row.add(label, BorderLayout.WEST);
        JPanel holder = new JPanel();
        holder.setOpaque(false);
        holder.setLayout(new BoxLayout(holder, BoxLayout.X_AXIS));
        holder.add(control);
        holder.add(Box.createHorizontalGlue());
        row.add(holder, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(36)));
        return row;
    }

    private JComponent authEntryPoints() {
        JPanel panel = new JPanel(new BorderLayout(0, Theme.px(4)));
        panel.setOpaque(false);
        JLabel label = Ui.muted(I18n.t("ui.entry.points.one.per.line"));
        label.setFont(Theme.font(Font.BOLD, 12f));
        panel.add(label, BorderLayout.NORTH);
        state.authEntryPoints.setRows(3);
        JScrollPane scroll = new JScrollPane(state.authEntryPoints);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        scroll.getViewport().setBackground(Theme.CARD);
        scroll.setPreferredSize(new Dimension(Theme.px(240), Theme.px(74)));
        panel.add(scroll, BorderLayout.CENTER);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(98)));
        return panel;
    }

    /** Runs the connection probe; safe to call from the snapshot harness too. */
    void testAuthConnection() {
        final String server = state.authServer.getText().trim();
        final int timeout = ((Number) state.authTimeout.getValue()).intValue();
        authTone = Theme.INFO;
        String target = server.isEmpty() ? I18n.t("ui.empty.server") : server;
        authStatus.setText(I18n.t("frame.testingServer", target));
        authDetail.setText(I18n.t("ui.get.api.health.and.get.api.auth.software"));
        authStatus.repaint();
        revalidate();
        repaint();
        new SwingWorker<AuthGateClient.Result, Void>() {
            @Override
            protected AuthGateClient.Result doInBackground() {
                return AuthGateClient.test(server, timeout);
            }

            @Override
            protected void done() {
                String message;
                try {
                    AuthGateClient.Result result = get();
                    authTone = result.ok ? Theme.SUCCESS : Theme.DANGER;
                    authStatus.setText(result.ok ? I18n.t("ui.connection.ok") : I18n.t("ui.connection.failed"));
                    message = result.message;
                    authDetail.setText(result.raw.isEmpty() ? message
                            : message + "   ·   " + result.raw);
                    frame.log(result.ok ? "SUCCESS" : "ERROR", I18n.t("ui.auth.gate.test") + message);
                } catch (Exception e) {
                    authTone = Theme.DANGER;
                    authStatus.setText(I18n.t("ui.connection.failed"));
                    message = e.getMessage() == null ? e.toString() : e.getMessage();
                    authDetail.setText(message);
                    frame.log("ERROR", I18n.t("ui.auth.gate.test.failed") + message);
                }
                authStatus.repaint();
                revalidate();
                repaint();
            }
        }.execute();
    }

    private void openAdminConsole() {
        final String base = AuthGateClient.normalize(state.authServer.getText());
        if (base.isEmpty()) {
            authTone = Theme.WARNING;
            authStatus.setText(I18n.t("ui.server.url.required"));
            authDetail.setText(I18n.t("ui.enter.the.auth.gate.server.url.before.opening.the.console"));
            authStatus.repaint();
            revalidate();
            repaint();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(new URI(base + "/"));
                        frame.log("INFO", I18n.t("ui.opened.admin.console") + base + "/");
                    } else {
                        frame.log("WARN", I18n.t("ui.desktop.browsing.is.not.supported"));
                    }
                } catch (Exception e) {
                    frame.log("ERROR", I18n.t("ui.unable.to.open.admin.console") + e.getMessage());
                }
            }
        }, "zipit-open-admin-console").start();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Component fieldGap() {
        return Box.createVerticalStrut(Theme.px(8));
    }

    private JComponent fileRow(String caption, JTextField field, final boolean save,
                               final String... extensions) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(12), 0));
        row.setOpaque(false);
        JLabel label = Ui.muted(caption);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setPreferredSize(new Dimension(Theme.px(120), Theme.px(28)));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        JButton browse = Ui.iconButton("folder", I18n.t("ui.browse"), 30);
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter(I18n.t("ui.supported.files"), extensions));
            int option = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
            if (option == JFileChooser.APPROVE_OPTION) {
                field.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        row.add(browse, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(34)));
        return row;
    }

    private JComponent textRow(String caption, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(12), 0));
        row.setOpaque(false);
        JLabel label = Ui.muted(caption);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setPreferredSize(new Dimension(Theme.px(120), Theme.px(28)));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        JLabel hint = Ui.faint(I18n.t("ui.optional"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setPreferredSize(new Dimension(Theme.px(56), Theme.px(28)));
        hint.setHorizontalAlignment(JLabel.RIGHT);
        row.add(hint, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(34)));
        return row;
    }

    private void addSectionHeader(JPanel body, String index, String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout(Theme.px(12), 0));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(Theme.px(4), Theme.px(2),
                Theme.px(12), Theme.px(2)));
        JLabel number = Ui.colored(index, () -> Theme.ACCENT2, 12f, Font.BOLD);
        number.setPreferredSize(new Dimension(Theme.px(24), Theme.px(18)));
        header.add(number, BorderLayout.WEST);
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel heading = Ui.h2(title);
        heading.setFont(Theme.font(Font.BOLD, 17f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(heading);
        JLabel sub = Ui.muted(subtitle);
        sub.setFont(Theme.font(Font.PLAIN, 12f));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        text.add(Box.createVerticalStrut(Theme.px(2)));
        text.add(sub);
        header.add(text, BorderLayout.CENTER);
        body.add(Ui.limitHeight(header));
    }

    private JPanel twoColumns(JComponent left, JComponent right) {
        JPanel grid = new JPanel(new GridLayout(1, 2, Theme.px(16), Theme.px(16)));
        grid.setOpaque(false);
        grid.add(left);
        grid.add(right);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        return grid;
    }
}
