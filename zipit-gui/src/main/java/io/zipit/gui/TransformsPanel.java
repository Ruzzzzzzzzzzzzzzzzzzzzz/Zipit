package io.zipit.gui;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import java.awt.GridLayout;

/** Transform configuration cards. */
final class TransformsPanel extends JPanel {

    TransformsPanel(GuiState s) {
        super(new GridLayout(0, 2, 14, 14));
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 18, 18, 18));

        add(Ui.card("Renaming", "Class, method and field names",
                vertical(s.rename,
                        label("Dictionary"),
                        s.dictionary,
                        label("Minimum name length"),
                        s.minNameLength)));
        add(Ui.card("Strings", "Encrypt string constants at call sites", vertical(s.stringEnc)));
        add(Ui.card("Numbers", "Obfuscate integer and long constants", vertical(s.intEnc)));
        add(Ui.card("Control flow", "Opaque predicates, branch rewriting and flattening",
                vertical(s.flow, label("Level"), s.flowLevel, s.flatten)));
        add(Ui.card("Members", "Declaration order and synthetic members",
                vertical(s.shuffle, s.junk, label("Junk methods"), s.junkMethods,
                        label("Junk fields"), s.junkFields)));
        add(Ui.card("Call wrappers", "Forward static calls through generated methods",
                vertical(s.wrapper, label("Maximum wrappers per class"), s.wrapperMax)));
        add(Ui.card("Watermark", "Synthetic constant + manifest attribute",
                vertical(s.watermark, label("Watermark id"), s.watermarkId)));
        add(Ui.card("Reflection", "Automatically keep classes and members used via reflection",
                vertical(s.reflection)));
        add(Ui.card("Trimming", "Remove unreferenced private methods and fields",
                vertical(s.trim)));
        add(Ui.card("Parameters", "Add unused parameters to private methods",
                vertical(s.parameter)));
        add(Ui.card("Debug metadata", "Remove source information",
                vertical(debugGrid(s))));
        add(Ui.card("Resources", "Rewrite class names in XML/JSON/YAML/properties",
                vertical(s.rewriteResources, Ui.muted(
                        "Conservative word-boundary replacement; off by default."))));
        add(Ui.card("Reference hiding", "Replace accessible calls/fields with invokedynamic lookups",
                vertical(s.referenceObfuscation, s.referenceVirtual, s.referenceFields,
                        s.referenceConstructors, Ui.muted(
                        "Encrypted owner/member names; resolved lazily via MethodHandles."))));
        add(Ui.card("Exception noise", "Synthetic catch-all handlers",
                vertical(s.exceptionObfuscation, Ui.muted(
                        "The catch-all handler is appended last, so finally semantics are preserved."))));
        add(Ui.card("Method virtualization", "Interpret method bodies in a custom VM",
                vertical(s.virtualize, Ui.muted("Max instructions per method"),
                        s.virtualizeMaxInstructions, Ui.muted("Max methods per class (0 = unlimited)"),
                        s.virtualizeMaxMethods, Ui.muted(
                                "Constructors, try/catch, NEW and invokedynamic are skipped."))));
        add(Ui.card("Anti-LLM", "Decoy semantics, wrapper walls and VM payload shielding",
                vertical(s.antiLlm, Ui.muted("Decoy methods per class"),
                        s.antiLlmDecoys, Ui.muted("Nested forwarding layers (0-4)"),
                        s.antiLlmLayers, Ui.muted(
                                "Raises the cost of LLM-assisted static analysis; not a proof."))));
        add(Ui.card("Runtime protection", "Optional startup guards",
                vertical(s.antiDebug, Ui.muted(
                        "Detects common JDWP/Xdebug launch flags from main(). Deterrent only."))));
    }

    private static JComponent debugGrid(GuiState s) {
        JPanel panel = new JPanel(new GridLayout(0, 2, 4, 2));
        panel.setOpaque(false);
        panel.add(s.debugSource);
        panel.add(s.debugLines);
        panel.add(s.debugLocals);
        panel.add(s.debugSignatures);
        panel.add(s.debugInner);
        panel.add(s.debugKotlin);
        return panel;
    }

    private static JPanel vertical(JComponent... components) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        for (JComponent component : components) {
            component.setAlignmentX(JComponent.LEFT_ALIGNMENT);
            Ui.constrainHeight(component);
            panel.add(component);
            panel.add(javax.swing.Box.createVerticalStrut(6));
        }
        return panel;
    }

    private static JLabel label(String text) {
        JLabel label = Ui.muted(text);
        label.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return label;
    }
}
