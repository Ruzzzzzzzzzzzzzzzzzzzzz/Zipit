package io.zipit.gui;

import io.zipit.core.config.Presets;
import io.zipit.core.config.ZipitConfig;

import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable form state for every Zipit option plus conversion to and from
 * {@link ZipitConfig}.
 *
 * <p>The class only uses plain Swing controls so it can be constructed in a
 * headless unit test; the visual layer is applied by the pages.</p>
 */
final class GuiState {

    private static final String DEFAULT_DICTIONARY =
            "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static final String PRESET_NONE = "(none)";
    static final String[] PRESETS = {
            PRESET_NONE, "library", "spring-boot", "jackson",
            "service-loader", "micronaut", "aggressive"
    };

    private final List<JComponent> tracked = new ArrayList<JComponent>();
    private final List<Runnable> listeners = new ArrayList<Runnable>();
    private boolean loading;

    // ---- Project ----
    final JTextField input = track(new JTextField(42));
    final JTextField output = track(new JTextField(42));
    final JTextArea libraries = track(new JTextArea(3, 42));
    final JTextField seed = track(new JTextField("0x5A17", 12));

    // ---- Global policy ----
    final JComboBox<String> preset = track(new JComboBox<String>(PRESETS));
    final JTextArea keep = track(new JTextArea(7, 42));
    final JTextArea softKeep = track(new JTextArea(3, 42));
    final JCheckBox keepPublicApi = track(new JCheckBox(I18n.t("ui.keep.public.api.completely")));
    final JCheckBox softKeepPublicApi = track(new JCheckBox(I18n.t("ui.keep.public.api.names.only")));
    final JCheckBox verify = track(new JCheckBox(I18n.t("ui.verify.output.classes"), true));
    final JCheckBox dryRun = track(new JCheckBox(I18n.t("ui.dry.run.analyze.only")));
    final JCheckBox fixedTime = track(new JCheckBox(I18n.t("ui.fixed.zip.timestamps")));

    // ---- Renaming ----
    final JCheckBox rename = track(new JCheckBox(I18n.t("ui.enable.renaming"), true));
    final JCheckBox renameClasses = track(new JCheckBox(I18n.t("ui.classes"), true));
    final JCheckBox renameMethods = track(new JCheckBox(I18n.t("ui.methods.3"), true));
    final JCheckBox renameFields = track(new JCheckBox(I18n.t("ui.fields.3"), true));
    final JCheckBox repackage = track(new JCheckBox(I18n.t("ui.repackage")));
    final JTextField prefix = track(new JTextField("", 10));
    final JTextField repackagePackage = track(new JTextField("", 20));
    final JTextField dictionary = track(new JTextField(DEFAULT_DICTIONARY, 34));
    final JSpinner minNameLength = track(new JSpinner(new SpinnerNumberModel(1, 1, 16, 1)));
    final JTextField mappingFile = track(new JTextField("zipit-mapping.txt", 16));
    final JTextField inputMapping = track(new JTextField("", 20));

    // ---- Constants, flow, members ----
    final JCheckBox stringEnc = track(new JCheckBox(I18n.t("ui.encrypt.string.constants"), true));
    final JSpinner stringMinLength = track(new JSpinner(new SpinnerNumberModel(2, 1, 64, 1)));
    final JCheckBox intEnc = track(new JCheckBox(I18n.t("ui.encrypt.integer.constants"), true));
    final JCheckBox includeLong = track(new JCheckBox(I18n.t("ui.include.long.constants"), true));
    final JCheckBox flow = track(new JCheckBox(I18n.t("ui.insert.opaque.control.flow.predicates"), true));
    final JComboBox<String> flowLevel = track(new JComboBox<String>(
            new String[]{"light", "normal", "aggressive"}));
    final JSpinner flowMaxInstructions = track(new JSpinner(
            new SpinnerNumberModel(8000, 0, 200000, 500)));
    final JCheckBox flatten = track(new JCheckBox(
            I18n.t("ui.flatten.methods.into.a.dispatcher.state.machine")));
    final JTextArea flattenExclude = track(new JTextArea(2, 30));
    final JCheckBox shuffle = track(new JCheckBox(I18n.t("ui.shuffle.member.declarations"), true));
    final JCheckBox junk = track(new JCheckBox(I18n.t("ui.add.junk.members"), true));
    final JSpinner junkMethods = track(new JSpinner(new SpinnerNumberModel(2, 0, 50, 1)));
    final JSpinner junkFields = track(new JSpinner(new SpinnerNumberModel(2, 0, 50, 1)));
    final JCheckBox wrapper = track(new JCheckBox(I18n.t("ui.wrap.static.calls")));
    final JSpinner wrapperMax = track(new JSpinner(new SpinnerNumberModel(200, 0, 5000, 10)));
    final JCheckBox parameter = track(new JCheckBox(I18n.t("ui.obfuscate.private.method.parameters")));
    final JCheckBox trim = track(new JCheckBox(I18n.t("ui.remove.unused.private.members")));
    final JCheckBox trimMethods = track(new JCheckBox(I18n.t("ui.remove.unused.private.methods"), true));
    final JCheckBox trimFields = track(new JCheckBox(I18n.t("ui.remove.unused.private.fields"), true));

    // ---- Hardening ----
    final JCheckBox virtualize = track(new JCheckBox(I18n.t("ui.virtualize.eligible.method.bodies")));
    final JSpinner virtualizeMaxInstructions = track(new JSpinner(
            new SpinnerNumberModel(2000, 16, 20000, 100)));
    final JSpinner virtualizeMaxMethods = track(new JSpinner(
            new SpinnerNumberModel(0, 0, 1000, 1)));
    final JCheckBox antiLlm = track(new JCheckBox(I18n.t("ui.add.anti.llm.decoys.and.wrapper.walls")));
    final JSpinner antiLlmDecoys = track(new JSpinner(new SpinnerNumberModel(2, 0, 20, 1)));
    final JSpinner antiLlmCallSites = track(new JSpinner(new SpinnerNumberModel(2, 0, 50, 1)));
    final JSpinner antiLlmLayers = track(new JSpinner(new SpinnerNumberModel(1, 0, 4, 1)));
    final JSpinner antiLlmMaxWrapped = track(new JSpinner(new SpinnerNumberModel(12, 0, 500, 1)));
    final JCheckBox antiLlmShield = track(new JCheckBox(
            I18n.t("ui.shield.virtualized.payloads"), true));
    final JCheckBox antiDebug = track(new JCheckBox(I18n.t("ui.add.startup.debug.agent.guard")));
    final JCheckBox exceptionObfuscation = track(new JCheckBox(
            I18n.t("ui.add.synthetic.catch.all.handlers")));
    final JCheckBox referenceObfuscation = track(new JCheckBox(I18n.t("ui.static.calls")));
    final JCheckBox referenceVirtual = track(new JCheckBox(I18n.t("ui.virtual.interface.calls")));
    final JCheckBox referenceFields = track(new JCheckBox(I18n.t("ui.field.access")));
    final JCheckBox referenceConstructors = track(new JCheckBox(I18n.t("ui.constructors.2")));
    final JCheckBox watermark = track(new JCheckBox(I18n.t("ui.embed.watermark"), true));
    final JTextField watermarkId = track(new JTextField("zipit", 12));

    // ---- Metadata, reflection, resources ----
    final JCheckBox debugSource = track(new JCheckBox(I18n.t("ui.sourcefile"), true));
    final JCheckBox debugLines = track(new JCheckBox(I18n.t("ui.line.numbers"), true));
    final JCheckBox debugLocals = track(new JCheckBox(I18n.t("ui.local.variables"), true));
    final JCheckBox debugSignatures = track(new JCheckBox(I18n.t("ui.generic.signatures"), true));
    final JCheckBox debugInner = track(new JCheckBox(I18n.t("ui.innerclasses")));
    final JCheckBox debugKotlin = track(new JCheckBox(I18n.t("ui.kotlin.metadata"), true));
    final JCheckBox reflection = track(new JCheckBox(
            I18n.t("ui.auto.keep.reflection.targets"), true));
    final JCheckBox reflectionRewrite = track(new JCheckBox(
            I18n.t("ui.rewrite.class.forname.strings")));
    final JCheckBox rewriteResources = track(new JCheckBox(
            I18n.t("ui.rewrite.class.names.in.text.resources")));
    final JCheckBox resourcesObfuscatePaths = track(new JCheckBox(
            I18n.t("ui.obfuscate.resource.paths")));
    final JTextArea resourceDomains = track(new JTextArea(2, 28));

    // ---- Incremental ----
    final JCheckBox incremental = track(new JCheckBox(I18n.t("ui.reuse.unchanged.classes")));
    final JTextField previousOutput = track(new JTextField("", 28));
    final JTextField incrementalMetadata = track(new JTextField(
            "zipit-incremental.tsv", 24));

    // ---- Reports and license ----
    final JTextField reportHtml = track(new JTextField("", 24));
    final JTextField reportJson = track(new JTextField("", 24));
    final JTextField licenseFile = track(new JTextField("", 28));
    final JTextField licenseKey = track(new JTextField("", 28));
    final JTextField licenseServer = track(new JTextField("", 28));
    final JCheckBox licenseRequire = track(new JCheckBox(I18n.t("ui.require.a.valid.license")));

    // ---- Auth Gate (multi-software server-side authorization) ----
    final JCheckBox authEnabled = track(new JCheckBox(I18n.t("ui.enable.auth.gate")));
    final JTextField authServer = track(new JTextField("http://127.0.0.1:8090", 28));
    final JTextField authSoftware = track(new JTextField("", 20));
    final JTextField authTitle = track(new JTextField("", 28));
    final JCheckBox authRegister = track(new JCheckBox(I18n.t("ui.allow.in.window.registration"), true));
    final JTextField authCache = track(new JTextField("", 28));
    final JSpinner authTimeout = track(new JSpinner(
            new SpinnerNumberModel(5000, 500, 120000, 500)));
    final JTextArea authEntryPoints = track(new JTextArea(3, 28));

    private ZipitConfig loadedConfig;

    GuiState() {
        libraries.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        keep.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        softKeep.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        flattenExclude.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        resourceDomains.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
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
        Ui.styleField(prefix);
        dictionary.setCaretPosition(0);
        Ui.styleField(repackagePackage);
        Ui.placeholder(input, "C:\\path\\to\\application.jar");
        Ui.placeholder(output, "C:\\path\\to\\application-protected.jar");
        Ui.placeholder(prefix, I18n.t("ui.optional.prefix"));
        Ui.placeholder(repackagePackage, "com.example.internal");
        Ui.placeholder(inputMapping, I18n.t("ui.optional.previous.mapping.file"));
        Ui.placeholder(resourceDomains, I18n.t("ui.one.bare.domain.per.line.e.g.minecraft"));
        Ui.placeholder(flattenExclude, I18n.t("ui.class.globs.excluded.from.flattening"));
        Ui.placeholder(previousOutput, I18n.t("ui.previous.output.jar.for.incremental.mode"));
        Ui.placeholder(reportHtml, "audit-report.html");
        Ui.placeholder(reportJson, "audit-report.json");
        Ui.styleField(authServer);
        Ui.styleField(authSoftware);
        Ui.styleField(authTitle);
        Ui.styleField(authCache);
        Ui.placeholder(authServer, "http://127.0.0.1:8090");
        Ui.placeholder(authSoftware, I18n.t("ui.my.app.software.id.on.the.license.server"));
        Ui.placeholder(authTitle, I18n.t("ui.login.window.title.shown.by.the.protected.app"));
        Ui.placeholder(authCache, I18n.t("ui.default.zipit.auth.software.token"));
        Ui.placeholder(authEntryPoints, I18n.t("ui.extra.entry.points.one.per.line.com.app.main.run"));
        Ui.placeholder(licenseFile, "license.yml");
        Ui.placeholder(licenseKey, "public-key.pem");
        Ui.placeholder(licenseServer, "https://license.example.com");
        installChangeTracking();
        String environmentServer = System.getenv("ZIPIT_LICENSE_SERVER");
        if (environmentServer != null && !environmentServer.trim().isEmpty()) {
            licenseServer.setText(environmentServer.trim());
        }
    }

    // ------------------------------------------------------------------
    // change notification (drives the live protection plan / summaries)
    // ------------------------------------------------------------------

    <T extends JComponent> T track(T component) {
        tracked.add(component);
        return component;
    }

    void addChangeListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /** Drops UI listeners when the frame is rebuilt for a language switch. */
    void clearChangeListeners() {
        listeners.clear();
    }

    private void installChangeTracking() {
        final Runnable notify = new Runnable() {
            @Override
            public void run() {
                fireChanged();
            }
        };
        for (JComponent component : tracked) {
            if (component instanceof AbstractButton) {
                ((AbstractButton) component).addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        notify.run();
                    }
                });
                if (component instanceof JCheckBox) {
                    ((JCheckBox) component).addItemListener(new ItemListener() {
                        @Override
                        public void itemStateChanged(ItemEvent e) {
                            notify.run();
                        }
                    });
                }
            } else if (component instanceof JComboBox) {
                ((JComboBox<?>) component).addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        notify.run();
                    }
                });
            } else if (component instanceof JSpinner) {
                ((JSpinner) component).addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        notify.run();
                    }
                });
            } else if (component instanceof JTextArea) {
                ((JTextArea) component).getDocument().addDocumentListener(docListener(notify));
            } else if (component instanceof JTextField) {
                ((JTextField) component).getDocument().addDocumentListener(docListener(notify));
            }
        }
    }

    private static DocumentListener docListener(final Runnable notify) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                notify.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                notify.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                notify.run();
            }
        };
    }

    private void fireChanged() {
        if (loading) {
            return;
        }
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    fireChanged();
                }
            });
            return;
        }
        for (Runnable listener : new ArrayList<Runnable>(listeners)) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // UI refresh issues must never break editing.
            }
        }
    }

    // ------------------------------------------------------------------
    // config conversion
    // ------------------------------------------------------------------

    ZipitConfig toConfig() throws Exception {
        String inputText = input.getText().trim();
        String outputText = output.getText().trim();
        if (inputText.isEmpty() || outputText.isEmpty()) {
            throw new IllegalArgumentException(I18n.t("ui.input.and.output.jars.are.required"));
        }
        ZipitConfig.Builder builder = loadedConfig == null
                ? new ZipitConfig.Builder() : loadedConfig.toBuilder();
        builder.input(Paths.get(inputText));
        builder.output(Paths.get(outputText));
        builder.libraries(paths(libraries.getText()));
        builder.keep(lines(keep.getText()));
        builder.softKeep(lines(softKeep.getText()));

        long seedValue;
        String seedText = seed.getText().trim();
        try {
            seedValue = Long.decode(seedText).longValue();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    I18n.t("ui.seed.must.be.a.decimal.or.0x.prefixed.number.for.example.23303"));
        }
        builder.seed(seedValue);
        builder.dryRun(dryRun.isSelected());
        builder.preserveTimestamps(!fixedTime.isSelected());

        List<String> repackageKept = loadedConfig == null
                ? Collections.<String>emptyList() : loadedConfig.renamer().repackageKept();
        builder.renamer(new ZipitConfig.RenamerOptions(
                rename.isSelected(), prefix.getText().trim(), repackage.isSelected(),
                repackagePackage.getText().trim(), renameClasses.isSelected(),
                renameMethods.isSelected(), renameFields.isSelected(),
                mappingFile.getText().trim(), dictionary.getText().trim(),
                ((Number) minNameLength.getValue()).intValue(), repackageKept));

        builder.stringEncryption(new ZipitConfig.StringEncryptionOptions(
                stringEnc.isSelected(), ((Number) stringMinLength.getValue()).intValue()));
        builder.integerEncryption(new ZipitConfig.IntegerEncryptionOptions(
                intEnc.isSelected(), includeLong.isSelected()));
        builder.flow(new ZipitConfig.FlowOptions(
                flow.isSelected(), String.valueOf(flowLevel.getSelectedItem()),
                ((Number) flowMaxInstructions.getValue()).intValue(), flatten.isSelected(),
                lines(flattenExclude.getText())));
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

        builder.reflection(new ZipitConfig.ReflectionOptions(
                reflection.isSelected(), reflectionRewrite.isSelected()));
        builder.trim(new ZipitConfig.TrimOptions(trim.isSelected(),
                trimMethods.isSelected(), trimFields.isSelected()));
        builder.parameterObfuscation(new ZipitConfig.ParameterOptions(parameter.isSelected()));
        builder.resources(new ZipitConfig.ResourceOptions(rewriteResources.isSelected(),
                resourcesObfuscatePaths.isSelected(), lines(resourceDomains.getText())));
        boolean referencesEnabled = referenceObfuscation.isSelected()
                || referenceVirtual.isSelected() || referenceFields.isSelected()
                || referenceConstructors.isSelected();
        builder.referenceObfuscation(new ZipitConfig.ReferenceOptions(referencesEnabled,
                referenceObfuscation.isSelected(), referenceVirtual.isSelected(),
                referenceFields.isSelected(), referenceConstructors.isSelected()));
        builder.exceptionObfuscation(new ZipitConfig.ExceptionOptions(
                exceptionObfuscation.isSelected()));
        builder.methodVirtualization(new ZipitConfig.VirtualizationOptions(
                virtualize.isSelected(),
                ((Number) virtualizeMaxInstructions.getValue()).intValue(),
                ((Number) virtualizeMaxMethods.getValue()).intValue()));
        builder.antiLlm(new ZipitConfig.AntiLlmOptions(
                antiLlm.isSelected(),
                ((Number) antiLlmDecoys.getValue()).intValue(),
                ((Number) antiLlmCallSites.getValue()).intValue(),
                ((Number) antiLlmLayers.getValue()).intValue(),
                ((Number) antiLlmMaxWrapped.getValue()).intValue(),
                antiLlmShield.isSelected()));
        builder.antiDebug(antiDebug.isSelected());
        builder.incremental(new ZipitConfig.IncrementalOptions(incremental.isSelected(),
                previousOutput.getText().trim(), incrementalMetadata.getText().trim()));
        builder.keepPublicApi(keepPublicApi.isSelected());
        builder.softKeepPublicApi(softKeepPublicApi.isSelected());

        String mapping = mappingFile.getText().trim();
        builder.mapping(new ZipitConfig.MappingOptions(rename.isSelected(),
                mapping.isEmpty() ? "zipit-mapping.txt" : mapping,
                inputMapping.getText().trim()));

        String html = reportHtml.getText().trim();
        String json = reportJson.getText().trim();
        builder.report(new ZipitConfig.ReportOptions(!html.isEmpty() || !json.isEmpty(), html, json));

        String license = licenseFile.getText().trim();
        List<String> licenseFeatures = loadedConfig == null
                ? new ArrayList<String>() : loadedConfig.license().requiredFeatures();
        builder.license(new ZipitConfig.LicenseOptions(!license.isEmpty(), license,
                licenseKey.getText().trim(), licenseRequire.isSelected(), licenseFeatures,
                licenseServer.getText().trim()));

        builder.authGate(new ZipitConfig.AuthGateOptions(
                authEnabled.isSelected(),
                authServer.getText().trim(),
                authSoftware.getText().trim(),
                authTitle.getText().trim(),
                authRegister.isSelected(),
                authCache.getText().trim(),
                ((Number) authTimeout.getValue()).intValue(),
                lines(authEntryPoints.getText())));

        ZipitConfig config = builder.build();
        String selectedPreset = String.valueOf(preset.getSelectedItem());
        if (!PRESET_NONE.equals(selectedPreset) && !selectedPreset.isEmpty()) {
            config = Presets.apply(config, selectedPreset);
        }
        return config;
    }

    void load(ZipitConfig config) {
        loading = true;
        try {
            loadedConfig = config;
            input.setText(config.input().toString());
            output.setText(config.output().toString());
            libraries.setText(joinPaths(config.libraries()));
            keep.setText(join(config.keep()));
            softKeep.setText(join(config.softKeep()));
            seed.setText(String.valueOf(config.seed()));
            watermarkId.setText(config.watermark().id());
            mappingFile.setText(config.renamer().mappingFile());
            inputMapping.setText(config.mapping().inputFile());
            reportHtml.setText(config.report().htmlFile());
            reportJson.setText(config.report().jsonFile());
            dictionary.setText(config.renamer().dictionary());
            minNameLength.setValue(Integer.valueOf(config.renamer().minimumNameLength()));

            rename.setSelected(config.renamer().enabled());
            renameClasses.setSelected(config.renamer().renameClasses());
            renameMethods.setSelected(config.renamer().renameMethods());
            renameFields.setSelected(config.renamer().renameFields());
            repackage.setSelected(config.renamer().repackage());
            prefix.setText(config.renamer().prefix());
            repackagePackage.setText(config.renamer().repackagePackage());
            stringEnc.setSelected(config.stringEncryption().enabled());
            stringMinLength.setValue(Integer.valueOf(config.stringEncryption().minLength()));
            intEnc.setSelected(config.integerEncryption().enabled());
            includeLong.setSelected(config.integerEncryption().includeLong());
            flow.setSelected(config.flow().enabled());
            flatten.setSelected(config.flow().flattening());
            flowLevel.setSelectedItem(config.flow().level());
            flowMaxInstructions.setValue(Integer.valueOf(config.flow().maxInstructions()));
            flattenExclude.setText(join(config.flow().flatteningExclude()));
            shuffle.setSelected(config.shuffle().enabled());
            junk.setSelected(config.junk().enabled());
            junkMethods.setValue(Integer.valueOf(config.junk().methods()));
            junkFields.setValue(Integer.valueOf(config.junk().fields()));
            wrapper.setSelected(config.invokeWrapper().enabled());
            wrapperMax.setValue(Integer.valueOf(config.invokeWrapper().maxPerClass()));
            watermark.setSelected(config.watermark().enabled());
            reflection.setSelected(config.reflection().enabled());
            reflectionRewrite.setSelected(config.reflection().rewriteClassStrings());
            trim.setSelected(config.trim().enabled());
            trimMethods.setSelected(config.trim().removeUnusedPrivateMethods());
            trimFields.setSelected(config.trim().removeUnusedPrivateFields());
            parameter.setSelected(config.parameterObfuscation().enabled());
            rewriteResources.setSelected(config.resources().rewriteContent());
            resourcesObfuscatePaths.setSelected(config.resources().obfuscatePaths());
            resourceDomains.setText(join(config.resources().rewriteDomains()));
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
            virtualizeMaxInstructions.setValue(
                    Integer.valueOf(config.methodVirtualization().maxInstructions()));
            virtualizeMaxMethods.setValue(Integer.valueOf(config.methodVirtualization().maxMethods()));
            antiLlm.setSelected(config.antiLlm().enabled());
            antiLlmDecoys.setValue(Integer.valueOf(config.antiLlm().decoyMethodsPerClass()));
            antiLlmCallSites.setValue(Integer.valueOf(config.antiLlm().decoyCallSitesPerClass()));
            antiLlmLayers.setValue(Integer.valueOf(config.antiLlm().wrapperLayers()));
            antiLlmMaxWrapped.setValue(Integer.valueOf(config.antiLlm().maxWrappedMethodsPerClass()));
            antiLlmShield.setSelected(config.antiLlm().shieldVirtualization());
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
            preset.setSelectedItem(presetValue == null || presetValue.isEmpty()
                    ? PRESET_NONE : presetValue);

            licenseFile.setText(config.license().file());
            licenseKey.setText(config.license().publicKey());
            licenseServer.setText(config.license().server());
            licenseRequire.setSelected(config.license().require());
            input.setCaretPosition(0);
            output.setCaretPosition(0);

            ZipitConfig.AuthGateOptions authGate = config.authGate();
            authEnabled.setSelected(authGate.enabled());
            authServer.setText(authGate.server());
            authSoftware.setText(authGate.software());
            authTitle.setText(authGate.title());
            authRegister.setSelected(authGate.allowRegister());
            authCache.setText(authGate.cacheFile());
            authTimeout.setValue(Integer.valueOf(authGate.timeoutMs()));
            authEntryPoints.setText(join(authGate.entryPoints()));
        } finally {
            loading = false;
            fireChanged();
        }
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    void setInput(Path path) {
        input.setText(path.toString());
        input.setCaretPosition(0);
        if (output.getText().trim().isEmpty()) {
            String name = path.getFileName().toString();
            int dot = name.lastIndexOf('.');
            String base = dot > 0 ? name.substring(0, dot) : name;
            Path parent = path.toAbsolutePath().getParent();
            output.setText(parent == null ? base + "-protected.jar"
                    : parent.resolve(base + "-protected.jar").toString());
        }
    }

    void setInputAndOutput(Path in, Path out) {
        input.setText(in.toString());
        input.setCaretPosition(0);
        if (out != null) {
            output.setText(out.toString());
            output.setCaretPosition(0);
        }
    }

    String presetName() {
        String value = String.valueOf(preset.getSelectedItem());
        return PRESET_NONE.equals(value) ? "" : value;
    }

    void selectPreset(String name) {
        preset.setSelectedItem(name == null || name.isEmpty() ? PRESET_NONE : name);
    }

    static List<String> lines(String text) {
        List<String> out = new ArrayList<String>();
        if (text == null) {
            return out;
        }
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

    static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(value);
            }
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
