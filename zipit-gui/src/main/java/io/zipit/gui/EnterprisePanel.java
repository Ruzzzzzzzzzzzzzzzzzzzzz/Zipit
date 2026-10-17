package io.zipit.gui;

import io.zipit.core.enterprise.LicenseInfo;
import io.zipit.core.enterprise.LicenseOnlineValidator;
import io.zipit.core.enterprise.LicenseValidator;
import io.zipit.core.enterprise.MappingDiff;
import io.zipit.core.enterprise.WatermarkTracer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.GridLayout;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/** Licensing, audit and release-review tools. */
final class EnterprisePanel extends JPanel {

    private final GuiState state;
    private final ZipitFrame frame;
    private final JLabel licenseStatus = new JLabel("Community edition");

    EnterprisePanel(GuiState state, ZipitFrame frame) {
        super(new GridLayout(0, 2, 14, 14));
        this.state = state;
        this.frame = frame;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        add(Ui.card("License", "RSA-SHA256 signed enterprise license", buildLicense()));
        add(Ui.card("Audit", "HTML and JSON audit reports", buildAudit()));
        add(Ui.card("Release tools", "Compare mappings and trace watermarks", buildTools()));
        add(Ui.card("Notes", "Enterprise workflow", notes()));
    }

    private JPanel buildLicense() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        panel.add(fileRow("License file", state.licenseFile, () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("YAML license", "yml", "yaml"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                state.licenseFile.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }));
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(fileRow("Public key", state.licenseKey, () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileFilter(new FileNameExtensionFilter("PEM public key", "pem", "key", "txt"));
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                state.licenseKey.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        }));
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(textRow("License server", state.licenseServer));
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(state.licenseRequire);
        panel.add(javax.swing.Box.createVerticalStrut(8));

        JButton validate = Ui.secondary("Validate license");
        validate.addActionListener(e -> validateLicense());
        JButton online = Ui.secondary("Validate online");
        online.addActionListener(e -> validateOnline());
        JPanel actions = Ui.horizontal(8, validate, online, licenseStatus);
        actions.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        panel.add(actions);
        return panel;
    }

    private JPanel buildAudit() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.add(fileRow("HTML report", state.reportHtml, () -> chooseReport(state.reportHtml, ".html")));
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(fileRow("JSON report", state.reportJson, () -> chooseReport(state.reportJson, ".json")));
        panel.add(javax.swing.Box.createVerticalStrut(8));
        JButton open = Ui.secondary("Open last HTML report");
        open.addActionListener(e -> openReport());
        open.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        panel.add(open);
        return panel;
    }

    private JPanel buildTools() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        JButton diff = Ui.secondary("Compare mapping files...");
        diff.addActionListener(e -> showDiff());
        diff.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JButton trace = Ui.secondary("Trace watermark in a JAR...");
        trace.addActionListener(e -> showTrace());
        trace.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        JButton folder = Ui.secondary("Open output folder");
        folder.addActionListener(e -> openOutputFolder());
        folder.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        panel.add(diff);
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(trace);
        panel.add(javax.swing.Box.createVerticalStrut(6));
        panel.add(folder);
        return panel;
    }

    private JPanel notes() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.add(Ui.muted("1. Keep the mapping file next to the output."));
        panel.add(Ui.muted("2. Feed it back via Previous mapping for stable releases."));
        panel.add(Ui.muted("3. Attach the JSON audit report to the release ticket."));
        panel.add(Ui.muted("4. Require a signed license in regulated pipelines."));
        return panel;
    }

    private void validateLicense() {
        String file = state.licenseFile.getText().trim();
        if (file.isEmpty()) {
            setStatus("Select a license file", Ui.WARNING);
            return;
        }
        try {
            String key = state.licenseKey.getText().trim();
            LicenseInfo info = LicenseValidator.validate(Paths.get(file),
                    key.isEmpty() ? null : Paths.get(key));
            if (info.isValid()) {
                setStatus(info.licensee() + " · " + info.edition() + " · " + info.expires(), Ui.SUCCESS);
            } else {
                setStatus(info.error(), Ui.DANGER);
            }
        } catch (Exception e) {
            setStatus(e.getMessage(), Ui.DANGER);
        }
    }

    private void validateOnline() {
        String file = state.licenseFile.getText().trim();
        String server = state.licenseServer.getText().trim();
        if (file.isEmpty() || server.isEmpty()) {
            setStatus("Select a license file and server URL", Ui.WARNING);
            return;
        }
        try {
            LicenseOnlineValidator.Result result = LicenseOnlineValidator.check(Paths.get(file), server);
            if (result.isValid()) {
                setStatus("Online validation OK", Ui.SUCCESS);
            } else {
                setStatus(result.reason(), Ui.DANGER);
            }
        } catch (Exception e) {
            setStatus(e.getMessage(), Ui.DANGER);
        }
    }

    private void setStatus(String text, java.awt.Color color) {
        licenseStatus.setText(text);
        licenseStatus.setForeground(color);
    }

    private void chooseReport(JTextField field, String suffix) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("zipit-audit" + suffix));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            field.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    void openReport() {
        String html = state.reportHtml.getText().trim();
        if (html.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Set an HTML report path first.");
            return;
        }
        Path path = Paths.get(html);
        if (!Files.exists(path)) {
            JOptionPane.showMessageDialog(this, "Report not found: " + path);
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Unable to open report: " + e.getMessage());
        }
    }

    void openOutputFolder() {
        Path output = Paths.get(state.output.getText().trim());
        Path parent = output.toAbsolutePath().getParent();
        if (parent == null || !Files.exists(parent)) {
            JOptionPane.showMessageDialog(this, "Output folder does not exist yet.");
            return;
        }
        try {
            Desktop.getDesktop().open(parent.toFile());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Unable to open folder: " + e.getMessage());
        }
    }

    void showDiff() {
        Path oldFile = chooseMapping("Select old mapping");
        if (oldFile == null) {
            return;
        }
        Path newFile = chooseMapping("Select new mapping");
        if (newFile == null) {
            return;
        }
        try {
            showText("Mapping diff", MappingDiff.compare(oldFile, newFile));
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Diff failed: " + e.getMessage());
        }
    }

    private Path chooseMapping(String title) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileFilter(new FileNameExtensionFilter("Mapping files", "txt", "map"));
        return chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
                ? chooser.getSelectedFile().toPath() : null;
    }

    void showTrace() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JAR files", "jar", "zip", "war"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            List<String> marks = WatermarkTracer.trace(chooser.getSelectedFile().toPath());
            StringBuilder out = new StringBuilder();
            if (marks.isEmpty()) {
                out.append("No Zipit watermark found.");
            } else {
                for (String mark : marks) {
                    out.append(mark).append('\n');
                }
            }
            showText("Watermark trace", out.toString());
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Trace failed: " + e.getMessage());
        }
    }

    private void showText(String title, String content) {
        JTextArea area = new JTextArea(content, 24, 90);
        area.setEditable(false);
        area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(area);
        JOptionPane.showMessageDialog(this, scroll, title, JOptionPane.INFORMATION_MESSAGE);
    }

    private JPanel textRow(String label, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.setPreferredSize(new java.awt.Dimension(96, 26));
        row.add(caption, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        return row;
    }

    private JPanel fileRow(String label, JTextField field, Runnable browse) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.setPreferredSize(new java.awt.Dimension(96, 26));
        row.add(caption, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        JButton button = Ui.secondary("...");
        button.addActionListener(e -> browse.run());
        row.add(button, BorderLayout.EAST);
        row.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 30));
        row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        return row;
    }
}
