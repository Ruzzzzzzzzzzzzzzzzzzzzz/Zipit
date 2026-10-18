package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Styled dialogs: About, tool results and simple notifications. */
final class Dialogs {

    private static final String[] FEATURES = {
            I18n.t("ui.deterministic.renaming.with.stable.mapping.reuse"),
            I18n.t("ui.string.integer.and.long.constant.encryption"),
            I18n.t("ui.control.flow.obfuscation.and.state.machine.flattening"),
            I18n.t("ui.custom.vm.method.virtualization"),
            I18n.t("ui.anti.llm.decoys.wrapper.walls.and.shielding"),
            I18n.t("ui.reference.hiding.through.invokedynamic"),
            I18n.t("ui.resource.content.and.path.obfuscation"),
            I18n.t("ui.reflection.auto.keep.analysis"),
            I18n.t("ui.html.json.audit.reports"),
            I18n.t("ui.per.class.output.verification"),
            I18n.t("ui.enterprise.licensing.and.watermark.tracing"),
            I18n.t("ui.gradle.and.maven.build.plugins"),
    };

    private Dialogs() {
    }

    // ------------------------------------------------------------------
    // about
    // ------------------------------------------------------------------

    static void about(Component parent) {
        aboutDialog(parent, true).setVisible(true);
    }

    /** Non-modal About dialog used by the screenshot harness to show off glass. */
    static JDialog aboutPreview(Component parent) {
        JDialog dialog = aboutDialog(parent, false);
        dialog.setVisible(true);
        dialog.toFront();
        return dialog;
    }

    private static JDialog aboutDialog(Component parent, boolean modal) {
        final Window ownerWindow = owner(parent);
        final JDialog dialog = new JDialog(ownerWindow, I18n.t("ui.about.zipit"),
                modal ? java.awt.Dialog.ModalityType.APPLICATION_MODAL
                        : java.awt.Dialog.ModalityType.MODELESS);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JComponent root = Glass.glassRoot(dialog, ownerWindow);
        root.setLayout(new BorderLayout());

        JPanel hero = new JPanel(new BorderLayout(Theme.px(16), 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, new Color(20, 27, 46, 206), getWidth(),
                        getHeight(), new Color(12, 18, 32, 176)));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setPaint(new java.awt.RadialGradientPaint(
                        new java.awt.geom.Point2D.Double(getWidth() * 0.2, -20),
                        getWidth() * 0.8f, new float[]{0f, 1f},
                        new Color[]{Theme.alpha(Theme.ACCENT, 130), new Color(0, 0, 0, 0)}));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(Theme.alpha(Theme.ACCENT2, 110));
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.dispose();
            }
        };
        hero.setOpaque(false);
        hero.setBorder(BorderFactory.createEmptyBorder(Theme.px(26), Theme.px(30),
                Theme.px(24), Theme.px(30)));

        JLabel mark = new JLabel(BrandMark.tileIcon(Theme.px(62)));
        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JPanel nameRow = new JPanel();
        nameRow.setOpaque(false);
        nameRow.setLayout(new BoxLayout(nameRow, BoxLayout.X_AXIS));
        JLabel name = Ui.colored("ZIPIT", () -> Color.WHITE, 30f, Font.BOLD);
        nameRow.add(name);
        nameRow.add(Box.createHorizontalStrut(Theme.px(10)));
        nameRow.add(Ui.pill("v1.0.0", () -> new Color(255, 255, 255, 34),
                () -> new Color(0xE0E7FF)));
        nameRow.add(Box.createHorizontalGlue());
        brand.add(nameRow);
        brand.add(Box.createVerticalStrut(Theme.px(5)));
        brand.add(Ui.colored(I18n.t("ui.enterprise.java.bytecode.protection"),
                () -> new Color(0xC7D2FE), 15f, Font.PLAIN));
        brand.add(Box.createVerticalStrut(Theme.px(2)));
        brand.add(Ui.colored(I18n.t("ui.deterministic.auditable.build.integrated"),
                () -> new Color(0xA5B6D4), 13f, Font.PLAIN));

        JPanel heroLeft = new JPanel();
        heroLeft.setOpaque(false);
        heroLeft.setLayout(new BoxLayout(heroLeft, BoxLayout.X_AXIS));
        mark.setAlignmentY(Component.CENTER_ALIGNMENT);
        heroLeft.add(mark);
        heroLeft.add(Box.createHorizontalStrut(Theme.px(20)));
        brand.setAlignmentY(Component.CENTER_ALIGNMENT);
        heroLeft.add(brand);
        hero.add(heroLeft, BorderLayout.WEST);
        root.add(hero, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, Theme.px(16)));
        body.setOpaque(false);
        body.setBorder(BorderFactory.createEmptyBorder(Theme.px(22), Theme.px(30),
                Theme.px(22), Theme.px(30)));

        JPanel grid = new JPanel(new GridLayout(0, 2, Theme.px(12), Theme.px(10)));
        grid.setOpaque(false);
        for (String feature : FEATURES) {
            JPanel row = new JPanel(new BorderLayout(Theme.px(8), 0));
            row.setOpaque(false);
            JLabel check = Ui.iconLabel("check-circle", 16, () -> Theme.SUCCESS);
            row.add(check, BorderLayout.WEST);
            JLabel text = Ui.body(feature);
            text.setFont(Theme.font(Font.PLAIN, 14f));
            row.add(text, BorderLayout.CENTER);
            grid.add(row);
        }
        body.add(grid, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(Theme.px(12), 0));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(Theme.px(16), 0, 0, 0));
        JLabel meta = Ui.faint(I18n.t("ui.asm.9.11.flatlaf.3.5.4.java.8.2026.zipit"));
        meta.setFont(Theme.font(Font.PLAIN, 13f));
        footer.add(meta, BorderLayout.WEST);
        JButton close = Ui.primary(I18n.t("ui.close"));
        close.addActionListener(e -> dialog.dispose());
        footer.add(close, BorderLayout.EAST);
        body.add(footer, BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setSize(Theme.px(780), Theme.px(620));
        dialog.setLocationRelativeTo(ownerWindow);
        return dialog;
    }

    // ------------------------------------------------------------------
    // tool result viewer
    // ------------------------------------------------------------------

    static void result(Component parent, String icon, String title, String subtitle,
                       String content, String suggestedFileName) {
        final JDialog dialog = new JDialog(owner(parent), title,
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JComponent root = Glass.glassRoot(dialog, owner(parent));
        root.setLayout(new BorderLayout(0, Theme.px(12)));
        root.setBorder(BorderFactory.createEmptyBorder(Theme.px(20), Theme.px(22),
                Theme.px(18), Theme.px(22)));

        JPanel header = new JPanel(new BorderLayout(Theme.px(12), 0));
        header.setOpaque(false);
        header.add(new Card.IconChip(icon, () -> Theme.ACCENT), BorderLayout.WEST);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel heading = Ui.h2(title);
        heading.setFont(Theme.font(Font.BOLD, 17f));
        titles.add(heading);
        if (subtitle != null && !subtitle.isEmpty()) {
            titles.add(Box.createVerticalStrut(Theme.px(2)));
            titles.add(Ui.muted(subtitle));
        }
        header.add(titles, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        JTextArea area = Ui.code(20, 90);
        area.setEditable(false);
        area.setText(content);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        scroll.getViewport().setBackground(Theme.CARD);
        root.add(scroll, BorderLayout.CENTER);

        final JLabel status = Ui.faint(" ");
        JPanel footer = new JPanel(new BorderLayout(Theme.px(10), 0));
        footer.setOpaque(false);
        footer.add(status, BorderLayout.WEST);
        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        JButton copy = Ui.button(I18n.t("ui.copy"), "copy");
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(area.getText()), null);
            status.setText(I18n.t("ui.copied.to.clipboard"));
        });
        JButton save = Ui.button(I18n.t("ui.save"), "download");
        save.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            if (suggestedFileName != null) {
                chooser.setSelectedFile(new File(suggestedFileName));
            }
            if (chooser.showSaveDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                try {
                    Files.write(chooser.getSelectedFile().toPath(),
                            area.getText().getBytes(StandardCharsets.UTF_8));
                    status.setText(I18n.t("dialog.savedTo", chooser.getSelectedFile().getAbsolutePath()));
                } catch (Exception ex) {
                    status.setText(I18n.t("dialog.saveFailed", ex.getMessage()));
                }
            }
        });
        JButton close = Ui.primary(I18n.t("ui.close"));
        close.addActionListener(e -> dialog.dispose());
        buttons.add(copy);
        buttons.add(Box.createHorizontalStrut(Theme.px(8)));
        buttons.add(save);
        buttons.add(Box.createHorizontalStrut(Theme.px(8)));
        buttons.add(close);
        footer.add(buttons, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(close);
        dialog.setSize(Theme.px(880), Theme.px(620));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    // ------------------------------------------------------------------
    // simple messages
    // ------------------------------------------------------------------

    static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    static void error(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
    }

    static boolean confirm(Component parent, String title, String message) {
        return JOptionPane.showConfirmDialog(parent, message, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.OK_OPTION;
    }

    static Window owner(Component parent) {
        if (parent == null) {
            return null;
        }
        if (parent instanceof Window) {
            return (Window) parent;
        }
        return SwingUtilities.getWindowAncestor(parent);
    }
}
