package io.zipit.gradle;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ConfigLoader;
import io.zipit.core.config.Presets;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.util.Log;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Gradle task that runs the Zipit pipeline on a JAR. */
public abstract class ZipitTask extends DefaultTask {

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getInputJar();

    @OutputFile
    public abstract RegularFileProperty getOutputJar();

    @InputFiles
    @Optional
    public abstract ConfigurableFileCollection getLibraries();

    @Input
    @Optional
    public abstract ListProperty<String> getKeepRules();

    @Input
    @Optional
    public abstract Property<String> getConfigFile();

    @Input
    @Optional
    public abstract Property<Long> getSeed();

    @Input
    @Optional
    public abstract Property<Boolean> getVerify();

    @Input
    @Optional
    public abstract Property<Boolean> getKeepPublicApi();

    @Input
    @Optional
    public abstract Property<String> getPreset();

    @Input
    @Optional
    public abstract Property<String> getInputMapping();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getReportHtml();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getReportJson();

    /** Runs the obfuscator, failing the build on verification errors. */
    @TaskAction
    public void obfuscate() throws Exception {
        ZipitConfig config = buildConfig();
        Log log = new Log(new Log.Sink() {
            @Override
            public void line(String level, String message) {
                getLogger().lifecycle("[" + level + "] " + message);
            }
        }, true);

        EngineResult result = new ZipitEngine(log).run(config, ProgressListener.NONE);
        getLogger().lifecycle("Zipit produced {} ({} classes, {} resources, {} ms)",
                result.output(), result.classes(), result.resources(), result.millis());

        if (getVerify().getOrElse(Boolean.FALSE)) {
            List<String> errors = ZipitVerifier.verify(config.output(), config.libraries());
            if (!errors.isEmpty()) {
                StringBuilder message = new StringBuilder("Zipit verification failed:");
                for (String error : errors) {
                    message.append(System.lineSeparator()).append("  ").append(error);
                }
                throw new GradleException(message.toString());
            }
            getLogger().lifecycle("Zipit verification: OK");
        }
    }

    private ZipitConfig buildConfig() throws Exception {
        ZipitConfig.Builder builder;
        ZipitConfig base = null;
        if (getConfigFile().isPresent()) {
            base = ConfigLoader.load(Paths.get(getConfigFile().get()));
            builder = base.toBuilder();
        } else {
            builder = new ZipitConfig.Builder();
        }
        builder.input(getInputJar().get().getAsFile().toPath());
        builder.output(getOutputJar().get().getAsFile().toPath());

        if (!getLibraries().isEmpty()) {
            List<Path> libraries = new ArrayList<Path>();
            for (File file : getLibraries().getFiles()) {
                libraries.add(file.toPath());
            }
            builder.libraries(libraries);
        }
        if (getKeepRules().isPresent() && !getKeepRules().get().isEmpty()) {
            List<String> keep = new ArrayList<String>(base == null ? new ArrayList<String>() : base.keep());
            keep.addAll(getKeepRules().get());
            builder.keep(keep);
        }
        if (getSeed().isPresent()) {
            builder.seed(getSeed().get());
        }
        if (getKeepPublicApi().isPresent()) {
            builder.keepPublicApi(getKeepPublicApi().get());
        }
        if (getInputMapping().isPresent()) {
            ZipitConfig.MappingOptions mapping = base == null
                    ? new ZipitConfig.MappingOptions(true, "zipit-mapping.txt", "")
                    : base.mapping();
            builder.mapping(new ZipitConfig.MappingOptions(mapping.enabled(), mapping.file(),
                    getInputMapping().get()));
        }
        String html = getReportHtml().isPresent() ? getReportHtml().get().getAsFile().getAbsolutePath() : "";
        String json = getReportJson().isPresent() ? getReportJson().get().getAsFile().getAbsolutePath() : "";
        if (!html.isEmpty() || !json.isEmpty()) {
            builder.report(new ZipitConfig.ReportOptions(true, html, json));
        }

        ZipitConfig config = builder.build();
        if (getPreset().isPresent()) {
            config = Presets.apply(config, getPreset().get());
        }
        return config;
    }
}
