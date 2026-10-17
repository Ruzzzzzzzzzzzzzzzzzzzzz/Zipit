package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.GridLayout;

/** Keep rules, presets and batch options. */
final class RulesPanel extends JPanel {

    RulesPanel(GuiState s) {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));

        JPanel top = new JPanel(new GridLayout(1, 2, 14, 14));
        top.setOpaque(false);
        top.add(Ui.card("Policy preset", "Adds framework-aware keep rules",
                vertical(s.preset,
                        Ui.muted("Presets only add rules; explicit keep rules are preserved."))));
        top.add(Ui.card("Public API", "Control how public/protected members are treated",
                vertical(s.keepPublicApi, s.softKeepPublicApi,
                        Ui.muted("<html><body style='width: 300px'>Hard keep disables all "
                                + "transforms on the class. Soft keep preserves names but "
                                + "still encrypts strings and rewrites flow.</body></html>"))));

        s.keep.setRows(14);
        JScrollPane keepScroll = Ui.scroll(s.keep);

        JPanel keepCard = Ui.card("Keep rules", "One Ant-style glob, owner#member, or @annotation per line",
                keepScroll);
        keepCard.setPreferredSize(new java.awt.Dimension(100, 320));

        JPanel mapping = new JPanel(new GridLayout(2, 2, 8, 8));
        mapping.setOpaque(false);
        mapping.add(Ui.muted("Mapping file"));
        mapping.add(s.mappingFile);
        mapping.add(Ui.muted("Previous mapping (stable names)"));
        mapping.add(s.inputMapping);

        JPanel batch = new JPanel(new GridLayout(2, 4, 8, 8));
        batch.setOpaque(false);
        batch.add(Ui.muted("Seed"));
        batch.add(s.seed);
        batch.add(s.dryRun);
        batch.add(s.fixedTime);
        batch.add(new JLabel());
        batch.add(new JLabel());
        batch.add(new JLabel());
        batch.add(new JLabel());

        JPanel incrementalPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        incrementalPanel.setOpaque(false);
        incrementalPanel.add(s.incremental);
        incrementalPanel.add(new JLabel());
        incrementalPanel.add(s.previousOutput);
        incrementalPanel.add(s.incrementalMetadata);

        JPanel bottom = new JPanel(new BorderLayout(0, 14));
        bottom.setOpaque(false);
        bottom.add(Ui.card("Mapping", "Output and incremental mapping", mapping), BorderLayout.NORTH);
        bottom.add(Ui.card("Build", "Deterministic execution", batch), BorderLayout.CENTER);
        bottom.add(Ui.card("Incremental", "Reuse unchanged class bytes (stable seed and mapping required)",
                incrementalPanel), BorderLayout.SOUTH);

        add(top, BorderLayout.NORTH);
        add(keepCard, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
    }

    private static JPanel vertical(java.awt.Component... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (java.awt.Component component : components) {
            if (component instanceof javax.swing.JComponent) {
                ((javax.swing.JComponent) component).setAlignmentX(javax.swing.JComponent.LEFT_ALIGNMENT);
                Ui.constrainHeight((javax.swing.JComponent) component);
            }
            panel.add(component);
            panel.add(javax.swing.Box.createVerticalStrut(6));
        }
        return panel;
    }
}
