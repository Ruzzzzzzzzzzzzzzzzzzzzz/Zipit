package io.zipit.gradle;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import javax.inject.Inject;

/**
 * Gradle DSL for Zipit.
 *
 * <pre>
 * zipit {
 *     input = file("build/libs/app.jar")
 *     output = file("build/libs/app-obfuscated.jar")
 *     libraries.from(configurations.runtimeClasspath)
 *     keep = ["com.example.Main", "com.example.api.**"]
 *     seed = 23303
 *     verify = true
 * }
 * </pre>
 */
public class ZipitExtension {

    private final RegularFileProperty input;
    private final RegularFileProperty output;
    private final ConfigurableFileCollection libraries;
    private final ListProperty<String> keep;
    private final Property<String> configFile;
    private final Property<Long> seed;
    private final Property<Boolean> verify;
    private final Property<Boolean> keepPublicApi;
    private final Property<String> preset;
    private final Property<String> inputMapping;
    private final RegularFileProperty reportHtml;
    private final RegularFileProperty reportJson;

    /**
     * @param objects Gradle object factory used to create managed properties
     */
    @Inject
    public ZipitExtension(ObjectFactory objects) {
        this.input = objects.fileProperty();
        this.output = objects.fileProperty();
        this.libraries = objects.fileCollection();
        this.keep = objects.listProperty(String.class);
        this.configFile = objects.property(String.class);
        this.seed = objects.property(Long.class);
        this.verify = objects.property(Boolean.class);
        this.keepPublicApi = objects.property(Boolean.class);
        this.preset = objects.property(String.class);
        this.inputMapping = objects.property(String.class);
        this.reportHtml = objects.fileProperty();
        this.reportJson = objects.fileProperty();
    }

    public RegularFileProperty getInput() {
        return input;
    }

    public RegularFileProperty getOutput() {
        return output;
    }

    public ConfigurableFileCollection getLibraries() {
        return libraries;
    }

    public ListProperty<String> getKeep() {
        return keep;
    }

    /** Optional YAML configuration; explicit DSL values override it. */
    public Property<String> getConfigFile() {
        return configFile;
    }

    public Property<Long> getSeed() {
        return seed;
    }

    public Property<Boolean> getVerify() {
        return verify;
    }

    public Property<Boolean> getKeepPublicApi() {
        return keepPublicApi;
    }

    /** Optional preset: library, spring-boot, jackson, service-loader, aggressive. */
    public Property<String> getPreset() {
        return preset;
    }

    /** Optional previous mapping for stable builds. */
    public Property<String> getInputMapping() {
        return inputMapping;
    }

    public RegularFileProperty getReportHtml() {
        return reportHtml;
    }

    public RegularFileProperty getReportJson() {
        return reportJson;
    }
}
