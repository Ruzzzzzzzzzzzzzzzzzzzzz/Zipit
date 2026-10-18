package io.zipit.gui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/** Every transform option grouped into logical sections. */
final class TransformsPage extends JPanel {

    private final ZipitFrame frame;
    private final GuiState s;

    TransformsPage(ZipitFrame frame, GuiState state) {
        super(new BorderLayout());
        this.frame = frame;
        this.s = state;
        setOpaque(false);

        PageHeader header = new PageHeader(I18n.t("ui.configuration"), I18n.t("ui.transforms"),
                I18n.t("ui.the.complete.obfuscation.pipeline.every.pass.is.exposed.and"));
        JButton dashboard = Ui.button(I18n.t("ui.dashboard"), "dashboard");
        dashboard.addActionListener(e -> frame.showPage(ZipitFrame.PAGE_DASHBOARD));
        JButton run = Ui.primary(I18n.t("ui.protect.jar"), "play");
        run.addActionListener(e -> frame.runNow());
        header.actions(dashboard, run);
        add(header, BorderLayout.NORTH);

        JPanel body = Ui.pageBody();
        body.setBorder(BorderFactory.createEmptyBorder(0, Theme.px(24), Theme.px(22),
                Theme.px(24)));

        addSection(body, "01", I18n.t("ui.naming.symbols"),
                new String[][]{{I18n.t("ui.renaming"), I18n.t("ui.symbols.prefix.and.declarations")},
                        {I18n.t("ui.constants.2"), I18n.t("ui.string.integer.and.long.encryption")}});
        body.add(twoColumns(buildRenamingCard(), buildConstantsCard()));
        body.add(gap());

        addSection(body, "02", I18n.t("ui.names.marks.runtime"),
                new String[][]{{I18n.t("ui.output.names"), I18n.t("ui.repackaging.dictionary.and.mapping")},
                        {I18n.t("ui.watermark.guard"), I18n.t("ui.traceability.and.anti.debug")}});
        body.add(twoColumns(buildNamesCard(), buildWatermarkCard()));
        body.add(gap());

        addSection(body, "03", I18n.t("ui.control.flow"),
                new String[][]{{I18n.t("ui.flow.obfuscation"), I18n.t("ui.predicates.levels.and.flattening")},
                        {I18n.t("ui.virtualization"), I18n.t("ui.custom.vm.interpretation")}});
        body.add(twoColumns(buildFlowCard(), buildVirtualizationCard()));
        body.add(gap());

        addSection(body, "04", I18n.t("ui.anti.analysis"),
                new String[][]{{I18n.t("ui.reference.hiding"), I18n.t("ui.invokedynamic.indirection")},
                        {I18n.t("transforms.antiLlmTitle"), I18n.t("ui.decoys.wrapper.walls.and.shielding")}});
        body.add(twoColumns(buildReferencesCard(), buildAntiLlmCard()));
        body.add(gap());

        addSection(body, "05", I18n.t("ui.structure"),
                new String[][]{{I18n.t("ui.members"), I18n.t("ui.shuffle.junk.and.trimming")},
                        {I18n.t("ui.call.shaping"), I18n.t("ui.wrappers.parameters.and.exceptions")}});
        body.add(twoColumns(buildMembersCard(), buildCallShapingCard()));
        body.add(gap());

        addSection(body, "06", I18n.t("ui.metadata.resources"),
                new String[][]{{I18n.t("ui.debug.metadata"), I18n.t("ui.attribute.removal")},
                        {I18n.t("ui.resources.reflection"), I18n.t("ui.content.paths.and.keep.analysis")}});
        body.add(twoColumns(buildDebugCard(), buildResourcesCard()));
        body.add(gap());

        addSection(body, "07", I18n.t("ui.build.determinism"),
                new String[][]{{I18n.t("ui.deterministic.build"), I18n.t("ui.seed.verification.and.incremental.runs")},
                        {null, null}});
        body.add(twoColumns(buildBuildCard(), buildBuildNotesCard()));
        body.add(gap());

        add(Ui.pageScroll(body), BorderLayout.CENTER);
    }

    // ------------------------------------------------------------------
    // sections
    // ------------------------------------------------------------------

    private void addSection(JPanel body, String index, String title, String[][] hints) {
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
        if (hints.length > 0 && hints[0][0] != null) {
            StringBuilder line = new StringBuilder();
            for (String[] hint : hints) {
                if (hint[0] == null) {
                    continue;
                }
                if (line.length() > 0) {
                    line.append("   ·   ");
                }
                line.append(hint[0]).append(" — ").append(hint[1]);
            }
            JLabel sub = Ui.muted(line.toString());
            sub.setFont(Theme.font(Font.PLAIN, 12f));
            sub.setAlignmentX(Component.LEFT_ALIGNMENT);
            text.add(Box.createVerticalStrut(Theme.px(2)));
            text.add(sub);
        }
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

    private Component gap() {
        return Box.createVerticalStrut(Theme.px(14));
    }

    // ------------------------------------------------------------------
    // cards
    // ------------------------------------------------------------------

    private Card buildRenamingCard() {
        Card card = new Card("hash", I18n.t("ui.renaming"),
                I18n.t("ui.deterministic.symbol.renaming.with.stable.mappings"), () -> Theme.ACCENT);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.enable.renaming"), I18n.t("ui.master.switch.for.the.rename.pass"), s.rename));
        body.add(Ui.hline());
        body.add(switchRow(I18n.t("ui.rename.classes"), null, s.renameClasses));
        body.add(switchRow(I18n.t("ui.rename.methods"), null, s.renameMethods));
        body.add(switchRow(I18n.t("ui.rename.fields"), null, s.renameFields));
        body.add(switchRow(I18n.t("ui.repackage.classes"), I18n.t("ui.move.classes.into.a.new.namespace"), s.repackage));
        body.add(fieldRow(I18n.t("ui.package"), s.repackagePackage));
        body.add(fieldRow(I18n.t("ui.symbol.prefix"), s.prefix));
        card.body(body);
        return card;
    }

    private Card buildConstantsCard() {
        Card card = new Card("lock", I18n.t("ui.constants.2"),
                I18n.t("ui.string.integer.and.long.constant.encryption"), () -> Theme.ACCENT2);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.encrypt.strings"), I18n.t("ui.decrypt.at.call.sites.through.the.runtime"), s.stringEnc));
        body.add(controlRow(I18n.t("ui.minimum.string.length"), s.stringMinLength));
        body.add(Ui.hline());
        body.add(switchRow(I18n.t("ui.encrypt.integers"), I18n.t("ui.fold.int.constants.into.reversible.expressions"), s.intEnc));
        body.add(switchRow(I18n.t("ui.include.long.constants"), I18n.t("ui.also.encrypt.long.literals"), s.includeLong));
        card.body(body);
        return card;
    }

    private Card buildNamesCard() {
        Card card = new Card("file", I18n.t("ui.output.names"),
                I18n.t("ui.dictionary.minimum.length.and.mapping.reuse"), () -> Theme.INFO);
        JPanel body = Ui.column();
        body.add(fieldRow(I18n.t("ui.dictionary"), s.dictionary));
        body.add(controlRow(I18n.t("ui.minimum.name.length"), s.minNameLength));
        body.add(fieldRow(I18n.t("ui.mapping.file"), s.mappingFile));
        body.add(fieldRow(I18n.t("ui.previous.mapping"), s.inputMapping));
        JLabel hint = Ui.faint(I18n.t("ui.reusing.a.previous.mapping.keeps.released.names.stable"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(hint);
        card.body(body);
        return card;
    }

    private Card buildWatermarkCard() {
        Card card = new Card("fingerprint", I18n.t("ui.watermark.guard"),
                I18n.t("ui.forensic.marks.and.startup.protection"), () -> Theme.WARNING);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.embed.watermark"), I18n.t("ui.synthetic.constant.plus.manifest.attribute"), s.watermark));
        body.add(fieldRow(I18n.t("ui.watermark.id"), s.watermarkId));
        body.add(Ui.hline());
        body.add(switchRow(I18n.t("ui.anti.debug.guard"),
                I18n.t("ui.detect.jdwp.xdebug.style.launches.at.startup"), s.antiDebug));
        JLabel hint = Ui.faint(I18n.t("ui.both.features.are.deterrents.not.cryptographic.proof"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(hint);
        card.body(body);
        return card;
    }

    private Card buildFlowCard() {
        Card card = new Card("activity", I18n.t("ui.flow.obfuscation"),
                I18n.t("ui.opaque.predicates.branch.rewriting.and.flattening"), () -> Theme.ACCENT);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.enable.flow.obfuscation"), null, s.flow));
        body.add(controlRow(I18n.t("ui.level"), s.flowLevel));
        body.add(controlRow(I18n.t("ui.max.instructions.per.method"), s.flowMaxInstructions));
        body.add(switchRow(I18n.t("ui.flatten.into.a.dispatcher"), I18n.t("ui.rewrite.bodies.as.a.switch.state.machine"), s.flatten));
        body.add(fieldRowTall(I18n.t("ui.flattening.excludes"), editorScroll(s.flattenExclude, 2), Theme.px(64)));
        card.body(body);
        return card;
    }

    private Card buildVirtualizationCard() {
        Card card = new Card("cpu", I18n.t("ui.method.virtualization"),
                I18n.t("ui.interpret.eligible.method.bodies.in.a.custom.vm"), () -> Theme.ACCENT2);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.enable.virtualization"), null, s.virtualize));
        body.add(controlRow(I18n.t("ui.max.instructions.per.method"), s.virtualizeMaxInstructions));
        body.add(controlRow(I18n.t("ui.max.methods.per.class.0.all"), s.virtualizeMaxMethods));
        JLabel hint = Ui.faint(I18n.t("ui.constructors.try.catch.new.and.invokedynamic.are.skipped"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(hint);
        card.body(body);
        return card;
    }

    private Card buildReferencesCard() {
        Card card = new Card("sparkles", I18n.t("ui.reference.hiding"),
                I18n.t("ui.route.calls.and.field.access.through.invokedynamic"), () -> Theme.INFO);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.static.calls"), I18n.t("ui.hide.invokestatic.behind.bootstrapped.handles"),
                s.referenceObfuscation));
        body.add(switchRow(I18n.t("ui.virtual.interface.calls"), null, s.referenceVirtual));
        body.add(switchRow(I18n.t("ui.field.access"), null, s.referenceFields));
        body.add(switchRow(I18n.t("ui.constructors.2"), null, s.referenceConstructors));
        JLabel hint = Ui.faint(I18n.t("ui.owner.and.member.names.are.encrypted.and.resolved.lazily"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(hint);
        card.body(body);
        return card;
    }

    private Card buildAntiLlmCard() {
        Card card = new Card("ghost", I18n.t("transforms.antiLlmTitle"),
                I18n.t("ui.raise.the.cost.of.automated.semantic.recovery"), () -> Theme.DANGER);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.enable.anti.llm.walls"), null, s.antiLlm));
        body.add(controlRow(I18n.t("ui.decoy.methods.per.class"), s.antiLlmDecoys));
        body.add(controlRow(I18n.t("ui.decoy.call.sites.per.class"), s.antiLlmCallSites));
        body.add(controlRow(I18n.t("ui.wrapper.layers.0.4"), s.antiLlmLayers));
        body.add(controlRow(I18n.t("ui.max.wrapped.methods.per.class"), s.antiLlmMaxWrapped));
        body.add(switchRow(I18n.t("ui.shield.virtualized.payloads"), I18n.t("ui.wrap.vm.payloads.in.extra.layers"),
                s.antiLlmShield));
        card.body(body);
        return card;
    }

    private Card buildMembersCard() {
        Card card = new Card("shuffle", I18n.t("ui.members"),
                I18n.t("ui.declaration.order.synthetic.members.and.trimming"), () -> Theme.ACCENT);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.shuffle.declarations"), I18n.t("ui.randomize.member.order.per.class"), s.shuffle));
        body.add(switchRow(I18n.t("ui.add.junk.members"), null, s.junk));
        body.add(controlRow(I18n.t("ui.junk.methods.per.class"), s.junkMethods));
        body.add(controlRow(I18n.t("ui.junk.fields.per.class"), s.junkFields));
        body.add(Ui.hline());
        body.add(switchRow(I18n.t("ui.trim.unused.members"), I18n.t("ui.remove.unreferenced.private.members"), s.trim));
        body.add(switchRow(I18n.t("ui.unused.private.methods"), null, s.trimMethods));
        body.add(switchRow(I18n.t("ui.unused.private.fields"), null, s.trimFields));
        card.body(body);
        return card;
    }

    private Card buildCallShapingCard() {
        Card card = new Card("code", I18n.t("ui.call.shaping"),
                I18n.t("ui.indirection.for.calls.parameters.and.exceptions"), () -> Theme.ACCENT2);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.wrap.static.calls"), I18n.t("ui.forward.selected.calls.through.generated.methods"), s.wrapper));
        body.add(controlRow(I18n.t("ui.max.wrappers.per.class"), s.wrapperMax));
        body.add(Ui.hline());
        body.add(switchRow(I18n.t("ui.mutate.private.parameters"), I18n.t("ui.add.unused.parameters.to.private.methods"), s.parameter));
        body.add(Ui.hline());
        body.add(switchRow(I18n.t("ui.exception.noise"), I18n.t("ui.append.synthetic.catch.all.handlers"), s.exceptionObfuscation));
        JLabel hint = Ui.faint(I18n.t("ui.catch.all.handlers.are.appended.last.so.finally.semantics"));
        hint.setFont(Theme.font(Font.PLAIN, 12f));
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(hint);
        card.body(body);
        return card;
    }

    private Card buildDebugCard() {
        Card card = new Card("file", I18n.t("ui.debug.metadata"),
                I18n.t("ui.remove.source.information.from.output.classes"), () -> Theme.INFO);
        JPanel grid = new JPanel(new GridLayout(0, 2, Theme.px(10), Theme.px(2)));
        grid.setOpaque(false);
        grid.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.sourcefile"), null, s.debugSource)));
        grid.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.line.numbers"), null, s.debugLines)));
        grid.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.local.variables"), null, s.debugLocals)));
        grid.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.generic.signatures"), null, s.debugSignatures)));
        grid.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.innerclasses"), null, s.debugInner)));
        grid.add(Ui.limitHeight(new SwitchRow(I18n.t("ui.kotlin.metadata"), null, s.debugKotlin)));
        card.body(grid);
        return card;
    }

    private Card buildResourcesCard() {
        Card card = new Card("database", I18n.t("ui.resources.reflection"),
                I18n.t("ui.text.rewriting.path.obfuscation.and.keep.analysis"), () -> Theme.WARNING);
        JPanel body = Ui.column();
        body.add(switchRow(I18n.t("ui.rewrite.text.resources"),
                I18n.t("ui.replace.class.names.inside.xml.json.yaml.properties"), s.rewriteResources));
        body.add(switchRow(I18n.t("ui.obfuscate.resource.paths"),
                I18n.t("ui.rename.asset.directories.and.their.references"), s.resourcesObfuscatePaths));
        body.add(fieldRowTall(I18n.t("ui.rewrite.domains"), editorScroll(s.resourceDomains, 2), Theme.px(64)));
        body.add(Ui.hline());
        body.add(switchRow(I18n.t("ui.reflection.auto.keep"),
                I18n.t("ui.keep.classes.and.members.reachable.through.reflection"), s.reflection));
        body.add(switchRow(I18n.t("ui.rewrite.forname.strings"),
                I18n.t("ui.rename.class.strings.found.by.class.forname"), s.reflectionRewrite));
        card.body(body);
        return card;
    }

    private Card buildBuildCard() {
        Card card = new Card("settings", I18n.t("ui.deterministic.build"),
                I18n.t("ui.seed.verification.and.incremental.protection"), () -> Theme.SUCCESS);
        JPanel body = Ui.column();
        // The seed field lives on the Dashboard; show its live value here instead
        // of re-parenting the same Swing component.
        final JLabel seedView = Ui.mono(s.seed.getText());
        seedView.setFont(Theme.mono(13f));
        s.addChangeListener(new Runnable() {
            @Override
            public void run() {
                seedView.setText(s.seed.getText());
            }
        });
        body.add(Ui.limitHeight(SwitchRow.control(I18n.t("ui.seed"),
                I18n.t("ui.configured.on.the.dashboard.live.value"), seedView)));
        body.add(switchRow(I18n.t("ui.dry.run"), I18n.t("ui.analyze.only.no.artifact.is.written"), s.dryRun));
        body.add(switchRow(I18n.t("ui.verify.output"), I18n.t("ui.link.every.class.after.writing"), s.verify));
        body.add(switchRow(I18n.t("ui.fixed.timestamps"), I18n.t("ui.normalize.zip.entry.timestamps"), s.fixedTime));
        body.add(Ui.hline());
        body.add(switchRow(I18n.t("ui.incremental.protection"), I18n.t("ui.reuse.unchanged.classes.from.a.previous.output"),
                s.incremental));
        body.add(fieldRow(I18n.t("ui.previous.output"), s.previousOutput));
        body.add(fieldRow(I18n.t("ui.metadata.file"), s.incrementalMetadata));
        card.body(body);
        return card;
    }

    private Card buildBuildNotesCard() {
        Card card = new Card("info", I18n.t("ui.reproducibility.notes"),
                I18n.t("ui.how.to.keep.releases.stable"), () -> Theme.INFO);
        JPanel body = Ui.column();
        body.add(note("1", I18n.t("ui.keep.the.seed.and.mapping.file.next.to.the.output.artifact")));
        body.add(note("2", I18n.t("ui.feed.the.previous.mapping.back.to.keep.released.names.stable")));
        body.add(note("3", I18n.t("ui.incremental.runs.require.the.same.seed.and.mapping")));
        body.add(note("4", I18n.t("ui.attach.the.json.audit.report.to.the.release.ticket")));
        body.add(note("5", I18n.t("ui.verification.links.every.class.without.initializing.it")));
        card.body(body);
        return card;
    }

    private JComponent note(String index, String text) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(9), 0));
        row.setOpaque(false);
        JLabel number = Ui.colored(index, () -> Theme.ACCENT2, 12f, Font.BOLD);
        number.setPreferredSize(new Dimension(Theme.px(18), Theme.px(18)));
        row.add(number, BorderLayout.WEST);
        JLabel label = Ui.muted(text);
        label.setFont(Theme.font(Font.PLAIN, 12f));
        row.add(label, BorderLayout.CENTER);
        row.setBorder(BorderFactory.createEmptyBorder(Theme.px(4), 0, Theme.px(4), 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Theme.px(28)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    // ------------------------------------------------------------------
    // row helpers
    // ------------------------------------------------------------------

    private JComponent switchRow(String title, String description, JCheckBox checkBox) {
        return Ui.limitHeight(new SwitchRow(title, description, checkBox));
    }

    private JComponent controlRow(String title, JComponent control) {
        if (control instanceof javax.swing.JSpinner) {
            Ui.smallSpinner((javax.swing.JSpinner) control);
        } else if (control instanceof javax.swing.JComboBox) {
            control.setPreferredSize(new Dimension(Theme.px(132), Theme.px(30)));
            control.setMaximumSize(new Dimension(Theme.px(132), Theme.px(30)));
        } else if (control instanceof javax.swing.JTextField) {
            control.setPreferredSize(new Dimension(Theme.px(180), Theme.px(30)));
            control.setMaximumSize(new Dimension(Theme.px(180), Theme.px(30)));
        }
        return Ui.limitHeight(SwitchRow.control(title, null, control));
    }

    private JComponent fieldRow(String caption, JComponent field) {
        return fieldRowTall(caption, field, Theme.px(34));
    }

    private JComponent fieldRowTall(String caption, JComponent field, int height) {
        JPanel row = new JPanel(new BorderLayout(Theme.px(12), 0));
        row.setOpaque(false);
        JLabel label = Ui.muted(caption);
        label.setFont(Theme.font(Font.BOLD, 12f));
        label.setPreferredSize(new Dimension(Theme.px(132), Theme.px(28)));
        row.add(label, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(Theme.px(34), height)));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        return row;
    }

    private JScrollPane editorScroll(javax.swing.JTextArea area, int rows) {
        area.setRows(rows);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER, 1, true));
        scroll.getViewport().setBackground(Theme.CARD);
        scroll.setPreferredSize(new Dimension(Theme.px(200), Theme.px(52)));
        return scroll;
    }
}
