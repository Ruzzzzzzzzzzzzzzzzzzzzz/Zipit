package io.zipit.gui;

import io.zipit.core.config.Presets;
import io.zipit.core.config.ZipitConfig;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Mutable form state and conversion to/from {@link ZipitConfig}. */
final class GuiState {

    private static final String DEFAULT_DICTIONARY =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    final JTextField input = new JTextField(42);
    final JTextField output = new JTextField(42);
    final JTextArea libraries = new JTextArea(3, 42);
    final JTextArea keep = new JTextArea(7, 42);
    final JTextField seed = new JTextField("23303", 12);
    final JTextField watermarkId = new JTextField("zipit", 12);
    final JTextField mappingFile = new JTextField("zipit-mapping.txt", 16);
    final JTextField inputMapping = new JTextField("", 20);
    final JTextField reportHtml = new JTextField("", 24);
    final JTextField reportJson = new JTextField("", 24);
    final JTextField dictionary = new JTextField(DEFAULT_DICTIONARY, 34);
    final JTextField licenseFile = new JTextField("", 28);
    final JTextField licenseKey = new JTextField("", 28);
    final JTextField licenseServer = new JTextField("", 28);

    final JSpinner minNameLength = new JSpinner(new SpinnerNumberModel(1, 1, 16, 1));
    final JSpinner junkMethods = new JSpinner(new SpinnerNumberModel(2, 0, 50, 1));
    final JSpinner junkFields = new JSpinner(new SpinnerNumberModel(2, 0, 50, 1));
    final JSpinner wrapperMax = new JSpinner(new SpinnerNumberModel(200, 0, 5000, 10));
    final JSpinner virtualizeMaxInstructions = new JSpinner(new SpinnerNumberModel(2000, 16, 20000, 100));
    final JSpinner virtualizeMaxMethods = new JSpinner(new SpinnerNumberModel(0, 0, 1000, 1));
    final JSpinner antiLlmDecoys = new JSpinner(new SpinnerNumberModel(2, 0, 20, 1));
    final JSpinner antiLlmLayers = new JSpinner(new SpinnerNumberModel(1, 0, 4, 1));

    final JComboBox<String> flowLevel = new JComboBox<String>(
            new String[]{"light", "normal", "aggressive"});
    final JComboBox<String> preset = new JComboBox<String>(
            new String[]{"(none)", "library", "spring-boot", "jackson",
                    "service-loader", "micronaut", "aggressive"});

    final JCheckBox rename = new JCheckBox("Rename classes, methods and fields", true);
    final JCheckBox stringEnc = new JCheckBox("Encrypt string constants", true);
    final JCheckBox intEnc = new JCheckBox("Obfuscate integer/long constants", true);
    final JCheckBox flow = new JCheckBox("Insert control-flow predicates", true);
    final JCheckBox flatten = new JCheckBox("Flatten to a state-machine dispatcher", false);
    final JCheckBox shuffle = new JCheckBox("Shuffle member declarations", true);
    final JCheckBox junk = new JCheckBox("Add junk members", true);
    final JCheckBox wrapper = new JCheckBox("Wrap static calls", false);
    final JCheckBox watermark = new JCheckBox("Embed watermark", true);
    final JCheckBox reflection = new JCheckBox("Auto-keep reflection targets", true);
    final JCheckBox trim = new JCheckBox("Remove unused private members", false);
    final JCheckBox parameter = new JCheckBox("Obfuscate private method parameters", false);
    final JCheckBox rewriteResources = new JCheckBox("Rewrite class names in text resources", false);
    final JCheckBox referenceObfuscation = new JCheckBox("Hide static calls behind invokedynamic", false);
    final JCheckBox referenceVirtual = new JCheckBox("Hide virtual/interface calls", false);
    final JCheckBox referenceFields = new JCheckBox("Hide field access", false);
    final JCheckBox referenceConstructors = new JCheckBox("Hide new/constructor sequences", false);
    final JCheckBox exceptionObfuscation = new JCheckBox("Add synthetic catch-all handlers", false);
    final JCheckBox virtualize = new JCheckBox("Virtualize eligible method bodies (custom VM)", false);
    final JCheckBox antiLlm = new JCheckBox("Add anti-LLM decoys and wrapper walls", false);
    final JCheckBox antiDebug = new JCheckBox("Add startup debug-agent guard", false);
    final JCheckBox incremental = new JCheckBox("Reuse unchanged classes from a previous output", false);
    final JTextField previousOutput = new JTextField("", 28);
    final JTextField incrementalMetadata = new JTextField("zipit-incremental.tsv", 24);
    final JCheckBox keepPublicApi = new JCheckBox("Keep public API completely (no transforms)", false);
    final JCheckBox softKeepPublicApi = new JCheckBox("Keep public API names only", false);
    final JCheckBox dryRun = new JCheckBox("Dry run (analyze only)", false);
    final JCheckBox verify = new JCheckBox("Verify output classes", true);
    final JCheckBox fixedTime = new JCheckBox("Fixed ZIP timestamps", false);
    final JCheckBox licenseRequire = new JCheckBox("Require valid license", false);

    final JCheckBox debugSource = new JCheckBox("SourceFile", true);
    final JCheckBox debugLines = new JCheckBox("Line numbers", true);
    final JCheckBox debugLocals = new JCheckBox("Local variables", true);
    final JCheckBox debugSignatures = new JCheckBox("Generic signatures", true);
    final JCheckBox debugInner = new JCheckBox("InnerClasses", false);
    final JCheckBox debugKotlin = new JCheckBox("Kotlin metadata", true);

    private ZipitConfig loadedConfig;

    GuiState() {
        libraries.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        keep.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        Ui.styleField(input);
        Ui.styleField(output);
        Ui.styleField(seed);
        Ui.styleField(watermarkId);
        Ui.styleField(mappingFile);
        Ui.styleField(inputMapping);
        Ui.styleField(reportHtml);
        Ui.styleField(reportJson);
        Ui.styleField(dictionary);
        Ui.styleField(licenseFile);
        Ui.styleField(licenseKey);
        Ui.styleField(licenseServer);
        Ui.styleField(previousOutput);
        Ui.styleField(incrementalMetadata);
        String environmentServer = System.getenv("ZIPIT_LICENSE_SERVER");
        if (environmentServer != null && !environmentServer.trim().isEmpty()) {
            licenseServer.setText(environmentServer.trim());
        }
    }

    ZipitConfig toConfig() throws Exception {
        String inputText = input.getText().trim();
        String outputText = output.getText().trim();
        if (inputText.isEmpty() || outputText.isEmpty()) {
            throw new IllegalArgumentException("Input and output JARs are required");
        }
        ZipitConfig.Builder builder = loadedConfig == null
                ? new ZipitConfig.Builder() : loadedConfig.toBuilder();
        builder.input(Paths.get(inputText));
        builder.output(Paths.get(outputText));
        builder.libraries(paths(libraries.getText()));
        builder.keep(lines(keep.getText()));

        long seedValue;
        String seedText = seed.getText().trim();
        try {
            seedValue = Long.decode(seedText).longValue();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Seed must be a decimal or 0x-prefixed number");
        }
        builder.seed(seedValue);
        builder.dryRun(dryRun.isSelected());
        builder.preserveTimestamps(!fixedTime.isSelected());

        ZipitConfig.RenamerOptions oldRenamer = loadedConfig == null
                ? new ZipitConfig.Builder().input(Paths.get(inputText)).output(Paths.get(outputText))
                .build().renamer()
                : loadedConfig.renamer();
        builder.renamer(new ZipitConfig.RenamerOptions(
                rename.isSelected(), oldRenamer.prefix(), oldRenamer.repackage(),
                oldRenamer.repackagePackage(), oldRenamer.renameClasses(),
                oldRenamer.renameMethods(), oldRenamer.renameFields(),
                mappingFile.getText().trim(), dictionary.getText().trim(),
                ((Number) minNameLength.getValue()).intValue()));

        int stringMinLength = loadedConfig == null ? 2 : loadedConfig.stringEncryption().minLength();
        boolean includeLong = loadedConfig == null || loadedConfig.integerEncryption().includeLong();
        int maxInstructions = loadedConfig == null ? 8000 : loadedConfig.flow().maxInstructions();
        builder.stringEncryption(new ZipitConfig.StringEncryptionOptions(stringEnc.isSelected(), stringMinLength));
        builder.integerEncryption(new ZipitConfig.IntegerEncryptionOptions(intEnc.isSelected(), includeLong));
        builder.flow(new ZipitConfig.FlowOptions(flow.isSelected(),
                String.valueOf(flowLevel.getSelectedItem()), maxInstructions, flatten.isSelected()));
        builder.shuffle(new ZipitConfig.ShuffleOptions(shuffle.isSelected()));
        builder.junk(new ZipitConfig.JunkOptions(junk.isSelected(),
                ((Number) junkMethods.getValue()).intValue(),
                ((Number) junkFields.getValue()).intValue()));
        builder.invokeWrapper(new ZipitConfig.WrapperOptions(wrapper.isSelected(),
                ((Number) wrapperMax.getValue()).intValue()));
        builder.watermark(new ZipitConfig.WatermarkOptions(watermark.isSelected(),
                watermarkId.getText().trim().isEmpty() ? "zipit" : watermarkId.getText().trim()));

        boolean debugEnabled = debugSource.isSelected() || debugLines.isSelected()
                || debugLocals.isSelected() || debugSignatures.isSelected()
                || debugInner.isSelected() || debugKotlin.isSelected();
        builder.debug(new ZipitConfig.DebugOptions(debugEnabled,
                debugSource.isSelected(), debugLines.isSelected(), debugLocals.isSelected(),
                debugSignatures.isSelected(), debugInner.isSelected(), debugKotlin.isSelected()));

        builder.reflection(new ZipitConfig.ReflectionOptions(reflection.isSelected()));
        builder.trim(new ZipitConfig.TrimOptions(trim.isSelected(), true, true));
        builder.parameterObfuscation(new ZipitConfig.ParameterOptions(parameter.isSelected()));
        builder.resources(new ZipitConfig.ResourceOptions(rewriteResources.isSelected()));
        boolean referencesEnabled = referenceObfuscation.isSelected()
                || referenceVirtual.isSelected() || referenceFields.isSelected()
                || referenceConstructors.isSelected();
        builder.referenceObfuscation(new ZipitConfig.ReferenceOptions(referencesEnabled,
                referenceObfuscation.isSelected(), referenceVirtual.isSelected(),
                referenceFields.isSelected(), referenceConstructors.isSelected()));
        builder.exceptionObfuscation(new ZipitConfig.ExceptionOptions(exceptionObfuscation.isSelected()));
        builder.methodVirtualization(new ZipitConfig.VirtualizationOptions(
                virtualize.isSelected(),
                ((Number) virtualizeMaxInstructions.getValue()).intValue(),
                ((Number) virtualizeMaxMethods.getValue()).intValue()));
        ZipitConfig.AntiLlmOptions oldAntiLlm = loadedConfig == null
                ? new ZipitConfig.AntiLlmOptions(false, 2, 2, 1, 12, true)
                : loadedConfig.antiLlm();
        builder.antiLlm(new ZipitConfig.AntiLlmOptions(
                antiLlm.isSelected(),
                ((Number) antiLlmDecoys.getValue()).intValue(),
                oldAntiLlm.decoyCallSitesPerClass(),
                ((Number) antiLlmLayers.getValue()).intValue(),
                oldAntiLlm.maxWrappedMethodsPerClass(),
                oldAntiLlm.shieldVirtualization()));
        builder.antiDebug(antiDebug.isSelected());
        builder.incremental(new ZipitConfig.IncrementalOptions(incremental.isSelected(),
                previousOutput.getText().trim(), incrementalMetadata.getText().trim()));
        builder.keepPublicApi(keepPublicApi.isSelected());
        builder.softKeepPublicApi(softKeepPublicApi.isSelected());

        builder.mapping(new ZipitConfig.MappingOptions(rename.isSelected(),
                mappingFile.getText().trim().isEmpty() ? "zipit-mapping.txt" : mappingFile.getText().trim(),
                inputMapping.getText().trim()));

        String html = reportHtml.getText().trim();
        String json = reportJson.getText().trim();
        builder.report(new ZipitConfig.ReportOptions(!html.isEmpty() || !json.isEmpty(), html, json));

        String license = licenseFile.getText().trim();
        java.util.List<String> licenseFeatures = loadedConfig == null
                ? new ArrayList<String>() : loadedConfig.license().requiredFeatures();
        builder.license(new ZipitConfig.LicenseOptions(!license.isEmpty(), license,
                licenseKey.getText().trim(), licenseRequire.isSelected(), licenseFeatures,
                licenseServer.getText().trim()));

        ZipitConfig config = builder.build();
        String selectedPreset = String.valueOf(preset.getSelectedItem());
        if (!"(none)".equals(selectedPreset) && !selectedPreset.isEmpty()) {
            config = Presets.apply(config, selectedPreset);
        }
        return config;
    }

    void load(ZipitConfig config) {
        loadedConfig = config;
        input.setText(config.input().toString());
        output.setText(config.output().toString());
        libraries.setText(joinPaths(config.libraries()));
        keep.setText(join(config.keep()));
        seed.setText(String.valueOf(config.seed()));
        watermarkId.setText(config.watermark().id());
        mappingFile.setText(config.renamer().mappingFile());
        inputMapping.setText(config.mapping().inputFile());
        reportHtml.setText(config.report().htmlFile());
        reportJson.setText(config.report().jsonFile());
        dictionary.setText(config.renamer().dictionary());
        minNameLength.setValue(Integer.valueOf(config.renamer().minimumNameLength()));

        rename.setSelected(config.renamer().enabled());
        stringEnc.setSelected(config.stringEncryption().enabled());
        intEnc.setSelected(config.integerEncryption().enabled());
        flow.setSelected(config.flow().enabled());
        flatten.setSelected(config.flow().flattening());
        flowLevel.setSelectedItem(config.flow().level());
        shuffle.setSelected(config.shuffle().enabled());
        junk.setSelected(config.junk().enabled());
        junkMethods.setValue(Integer.valueOf(config.junk().methods()));
        junkFields.setValue(Integer.valueOf(config.junk().fields()));
        wrapper.setSelected(config.invokeWrapper().enabled());
        wrapperMax.setValue(Integer.valueOf(config.invokeWrapper().maxPerClass()));
        watermark.setSelected(config.watermark().enabled());
        reflection.setSelected(config.reflection().enabled());
        trim.setSelected(config.trim().enabled());
        parameter.setSelected(config.parameterObfuscation().enabled());
        rewriteResources.setSelected(config.resources().rewriteContent());
        referenceObfuscation.setSelected(config.referenceObfuscation().enabled()
                && config.referenceObfuscation().staticCalls());
        referenceVirtual.setSelected(config.referenceObfuscation().enabled()
                && config.referenceObfuscation().virtualCalls());
        referenceFields.setSelected(config.referenceObfuscation().enabled()
                && config.referenceObfuscation().fields());
        referenceConstructors.setSelected(config.referenceObfuscation().enabled()
                && config.referenceObfuscation().constructors());
        exceptionObfuscation.setSelected(config.exceptionObfuscation().enabled());
        virtualize.setSelected(config.methodVirtualization().enabled());
        virtualizeMaxInstructions.setValue(Integer.valueOf(config.methodVirtualization().maxInstructions()));
        virtualizeMaxMethods.setValue(Integer.valueOf(config.methodVirtualization().maxMethods()));
        antiLlm.setSelected(config.antiLlm().enabled());
        antiLlmDecoys.setValue(Integer.valueOf(config.antiLlm().decoyMethodsPerClass()));
        antiLlmLayers.setValue(Integer.valueOf(config.antiLlm().wrapperLayers()));
        antiDebug.setSelected(config.antiDebug());
        incremental.setSelected(config.incremental().enabled());
        previousOutput.setText(config.incremental().previousOutput());
        incrementalMetadata.setText(config.incremental().metadataFile());
        keepPublicApi.setSelected(config.keepPublicApi());
        softKeepPublicApi.setSelected(config.softKeepPublicApi());
        dryRun.setSelected(config.dryRun());
        fixedTime.setSelected(!config.preserveTimestamps());

        debugSource.setSelected(config.debug().removeSourceFile());
        debugLines.setSelected(config.debug().removeLineNumbers());
        debugLocals.setSelected(config.debug().removeLocalVariables());
        debugSignatures.setSelected(config.debug().removeSignatures());
        debugInner.setSelected(config.debug().removeInnerClasses());
        debugKotlin.setSelected(config.debug().removeKotlin());

        String presetValue = config.preset();
        preset.setSelectedItem(presetValue == null || presetValue.isEmpty() ? "(none)" : presetValue);

        licenseFile.setText(config.license().file());
        licenseKey.setText(config.license().publicKey());
        licenseServer.setText(config.license().server());
        licenseRequire.setSelected(config.license().require());
    }

    void setInput(Path path) {
        input.setText(path.toString());
        if (output.getText().trim().isEmpty()) {
            String name = path.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            Path parent = path.toAbsolutePath().getParent();
            output.setText(parent == null ? base + "-obfuscated.jar"
                    : parent.resolve(base + "-obfuscated.jar").toString());
        }
    }

    static List<String> lines(String text) {
        List<String> out = new ArrayList<String>();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    static List<Path> paths(String text) {
        List<Path> out = new ArrayList<Path>();
        for (String line : lines(text)) {
            out.add(Paths.get(line));
        }
        return out;
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(value);
        }
        return out.toString();
    }

    private static String joinPaths(List<Path> values) {
        List<String> strings = new ArrayList<String>();
        for (Path value : values) {
            strings.add(value.toString());
        }
        return join(strings);
    }
}
