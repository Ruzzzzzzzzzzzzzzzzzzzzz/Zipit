package io.zipit.gui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Live estimate of the protection pipeline derived from the current form
 * state. It powers the dashboard plan summary and the strength meter.
 */
final class ProtectionPlan {

    static final class Stage {
        final String icon;
        final String label;
        final String detail;
        final boolean enabled;
        final int weight;

        Stage(String icon, String label, String detail, boolean enabled, int weight) {
            this.icon = icon;
            this.label = label;
            this.detail = detail;
            this.enabled = enabled;
            this.weight = weight;
        }
    }

    private static final int MAX_WEIGHT = 118;

    private final List<Stage> stages;
    private final int score;

    private ProtectionPlan(List<Stage> stages, int score) {
        this.stages = Collections.unmodifiableList(stages);
        this.score = score;
    }

    static ProtectionPlan compute(GuiState s) {
        List<Stage> stages = new ArrayList<Stage>();
        stages.add(new Stage("hash", I18n.t("ui.renaming"),
                s.rename.isSelected() ? namingDetail(s) : I18n.t("ui.disabled.symbols.stay.readable"),
                s.rename.isSelected(), 14));
        stages.add(new Stage("lock", I18n.t("ui.string.encryption"),
                s.stringEnc.isSelected() ? I18n.t("plan.constants", s.stringMinLength.getValue())
                        : I18n.t("ui.plain.text.constants.remain"),
                s.stringEnc.isSelected(), 10));
        stages.add(new Stage("hash", I18n.t("ui.numeric.encryption"),
                s.intEnc.isSelected() ? (s.includeLong.isSelected() ? I18n.t("ui.int.long") : I18n.t("ui.int.only"))
                        : I18n.t("ui.literal.numbers.remain"),
                s.intEnc.isSelected(), 6));
        stages.add(new Stage("activity", I18n.t("ui.control.flow"),
                s.flow.isSelected() ? I18n.t("plan.flowDetail", s.flowLevel.getSelectedItem(), s.flowMaxInstructions.getValue())
                        : I18n.t("ui.straight.line.flow"),
                s.flow.isSelected(), 10));
        stages.add(new Stage("layers", I18n.t("ui.flattening"),
                s.flatten.isSelected() ? I18n.t("ui.dispatcher.state.machine") : I18n.t("ui.not.flattened"),
                s.flatten.isSelected() && s.flow.isSelected(), 8));
        stages.add(new Stage("shuffle", I18n.t("ui.member.shuffle"),
                s.shuffle.isSelected() ? I18n.t("ui.declaration.order.randomized") : I18n.t("ui.original.order"),
                s.shuffle.isSelected(), 4));
        stages.add(new Stage("box", I18n.t("ui.junk.members"),
                s.junk.isSelected() ? I18n.t("plan.junkDetail", s.junkMethods.getValue(), s.junkFields.getValue())
                        : I18n.t("ui.no.synthetic.members"),
                s.junk.isSelected(), 3));
        stages.add(new Stage("layers", I18n.t("ui.call.wrappers"),
                s.wrapper.isSelected() ? I18n.t("plan.wrapDetail", s.wrapperMax.getValue())
                        : I18n.t("ui.direct.calls"),
                s.wrapper.isSelected(), 4));
        stages.add(new Stage("code", I18n.t("ui.parameter.mutation"),
                s.parameter.isSelected() ? I18n.t("ui.private.methods.only") : I18n.t("ui.original.signatures"),
                s.parameter.isSelected(), 3));
        stages.add(new Stage("cpu", I18n.t("ui.virtualization"),
                s.virtualize.isSelected() ? I18n.t("plan.vmDetail", s.virtualizeMaxInstructions.getValue(),
                        isZero(s.virtualizeMaxMethods) ? I18n.t("plan.allMethods")
                                : I18n.t("plan.atMost", s.virtualizeMaxMethods.getValue()))
                        : I18n.t("ui.native.bytecode.only"),
                s.virtualize.isSelected(), 12));
        stages.add(new Stage("ghost", I18n.t("ui.anti.llm.walls"),
                s.antiLlm.isSelected() ? I18n.t("plan.antiLlmDetail", s.antiLlmDecoys.getValue(), s.antiLlmLayers.getValue())
                        : I18n.t("ui.straightforward.semantics"),
                s.antiLlm.isSelected(), 10));
        boolean references = s.referenceObfuscation.isSelected() || s.referenceVirtual.isSelected()
                || s.referenceFields.isSelected() || s.referenceConstructors.isSelected();
        stages.add(new Stage("sparkles", I18n.t("ui.reference.hiding"),
                references ? referenceDetail(s) : I18n.t("ui.symbolic.references.visible"),
                references, 8));
        stages.add(new Stage("alert", I18n.t("ui.exception.noise"),
                s.exceptionObfuscation.isSelected() ? I18n.t("ui.synthetic.catch.all.handlers")
                        : I18n.t("ui.clean.exception.tables"),
                s.exceptionObfuscation.isSelected(), 2));
        stages.add(new Stage("fingerprint", I18n.t("ui.anti.debug.guard"),
                s.antiDebug.isSelected() ? I18n.t("ui.startup.jdwp.check") : I18n.t("ui.none"),
                s.antiDebug.isSelected(), 2));
        stages.add(new Stage("gauge", I18n.t("ui.watermark"),
                s.watermark.isSelected() ? I18n.t("plan.watermarkDetail", safe(s.watermarkId.getText(), "zipit"))
                        : I18n.t("ui.no.forensic.mark"),
                s.watermark.isSelected(), 3));
        boolean debug = s.debugSource.isSelected() || s.debugLines.isSelected()
                || s.debugLocals.isSelected() || s.debugSignatures.isSelected()
                || s.debugInner.isSelected() || s.debugKotlin.isSelected();
        stages.add(new Stage("file", I18n.t("ui.debug.stripping"), debug ? I18n.t("ui.metadata.removal") : I18n.t("ui.debug.info.kept"),
                debug, 4));
        stages.add(new Stage("scissors", I18n.t("ui.trimming"), s.trim.isSelected() ? I18n.t("ui.unused.private.members")
                : I18n.t("ui.dead.code.kept"), s.trim.isSelected(), 2));
        boolean resources = s.rewriteResources.isSelected() || s.resourcesObfuscatePaths.isSelected();
        stages.add(new Stage("database", I18n.t("ui.resource.protection"),
                resources ? I18n.t("ui.content.path.rewriting") : I18n.t("ui.resources.untouched"), resources, 3));

        int enabledWeight = 0;
        for (Stage stage : stages) {
            if (stage.enabled) {
                enabledWeight += stage.weight;
            }
        }
        int score = Math.min(100, Math.round(100f * enabledWeight / MAX_WEIGHT));
        return new ProtectionPlan(stages, score);
    }

    private static boolean isZero(javax.swing.JSpinner spinner) {
        Object value = spinner.getValue();
        return value instanceof Number && ((Number) value).intValue() == 0;
    }

    private static String namingDetail(GuiState s) {
        StringBuilder out = new StringBuilder();
        if (s.renameClasses.isSelected()) {
            out.append(I18n.t("plan.unit.classes"));
        }
        if (s.renameMethods.isSelected()) {
            out.append(out.length() > 0 ? I18n.t("plan.listSeparator") : "");
            out.append(I18n.t("plan.unit.methods"));
        }
        if (s.renameFields.isSelected()) {
            out.append(out.length() > 0 ? I18n.t("plan.listSeparator") : "");
            out.append(I18n.t("plan.unit.fields"));
        }
        if (s.repackage.isSelected()) {
            out.append(I18n.t("ui.repackaged"));
        }
        return out.length() == 0 ? I18n.t("ui.enabled.no.symbol.kinds.selected") : out.toString();
    }

    private static String referenceDetail(GuiState s) {
        StringBuilder out = new StringBuilder();
        if (s.referenceObfuscation.isSelected()) {
            out.append(I18n.t("plan.unit.static"));
        }
        if (s.referenceVirtual.isSelected()) {
            out.append(out.length() > 0 ? I18n.t("plan.listSeparator") : "");
            out.append(I18n.t("plan.unit.virtual"));
        }
        if (s.referenceFields.isSelected()) {
            out.append(out.length() > 0 ? I18n.t("plan.listSeparator") : "");
            out.append(I18n.t("plan.unit.fields"));
        }
        if (s.referenceConstructors.isSelected()) {
            out.append(out.length() > 0 ? I18n.t("plan.listSeparator") : "");
            out.append(I18n.t("plan.unit.constructors"));
        }
        return out.length() == 0 ? I18n.t("ui.enabled") : out.toString();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    List<Stage> stages() {
        return stages;
    }

    int score() {
        return score;
    }

    int activeCount() {
        int count = 0;
        for (Stage stage : stages) {
            if (stage.enabled) {
                count++;
            }
        }
        return count;
    }

    int totalCount() {
        return stages.size();
    }

    String grade() {
        if (score < 20) {
            return I18n.t("ui.minimal");
        }
        if (score < 45) {
            return I18n.t("ui.standard");
        }
        if (score < 70) {
            return I18n.t("ui.hardened");
        }
        return I18n.t("ui.extreme");
    }

    Supplier<Color> gradeColor() {
        if (score < 20) {
            return () -> Theme.WARNING;
        }
        if (score < 45) {
            return () -> Theme.INFO;
        }
        if (score < 70) {
            return () -> Theme.SUCCESS;
        }
        return () -> Theme.ACCENT2;
    }

    /** Up to three high-impact stages that are currently disabled. */
    List<Stage> suggestions() {
        List<Stage> disabled = new ArrayList<Stage>();
        for (Stage stage : stages) {
            if (!stage.enabled && stage.weight >= 8) {
                disabled.add(stage);
            }
        }
        Collections.sort(disabled, new Comparator<Stage>() {
            @Override
            public int compare(Stage a, Stage b) {
                return Integer.compare(b.weight, a.weight);
            }
        });
        return disabled.size() > 3 ? disabled.subList(0, 3) : disabled;
    }
}
