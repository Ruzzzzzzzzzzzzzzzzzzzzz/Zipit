package io.zipit.maven;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ConfigLoader;
import io.zipit.core.config.Presets;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.util.Log;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven goal that obfuscates the project JAR.
 *
 * <p>Configured from the plugin descriptor
 * ({@code META-INF/maven/plugin.xml}) and typically bound to {@code package}:</p>
 *
 * <pre>
 * &lt;plugin&gt;
 *   &lt;groupId&gt;io.zipit&lt;/groupId&gt;
 *   &lt;artifactId&gt;zipit-maven-plugin&lt;/artifactId&gt;
 *   &lt;version&gt;1.0.0&lt;/version&gt;
 *   &lt;executions&gt;&lt;execution&gt;&lt;goals&gt;&lt;goal&gt;obfuscate&lt;/goal&gt;&lt;/goals&gt;&lt;/execution&gt;&lt;/executions&gt;
 *   &lt;configuration&gt;
 *     &lt;keep&gt;&lt;keep&gt;com.example.Main&lt;/keep&gt;&lt;/keep&gt;
 *     &lt;verify&gt;true&lt;/verify&gt;
 *   &lt;/configuration&gt;
 * &lt;/plugin&gt;
 * </pre>
 */
public class ZipitMojo extends AbstractMojo {

    /** Input JAR; defaults to the project artifact. */
    private File input;

    /** Output JAR; defaults to {@code <finalName>-obfuscated.jar}. */
    private File output;

    /** Optional YAML configuration file. */
    private String configFile;

    /** Keep rules (globs, owner#member, or @annotation). */
    private List<String> keep;

    /** Library JARs/directories used for hierarchy resolution. */
    private List<File> libraries;

    /** Deterministic seed. */
    private Long seed;

    /** Force JVM verification of every output class. */
    private boolean verify;

    /** Optional preset name. */
    private String preset;

    /** Optional previous mapping for stable builds. */
    private String inputMapping;

    /** Optional HTML audit report path. */
    private String reportHtml;

    /** Optional JSON audit report path. */
    private String reportJson;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            ZipitConfig config = buildConfig();
            Log log = new Log(new Log.Sink() {
                @Override
                public void line(String level, String message) {
                    if ("ERROR".equals(level)) {
                        getLog().error("[" + level + "] " + message);
                    } else if ("WARN".equals(level)) {
                        getLog().warn("[" + level + "] " + message);
                    } else {
                        getLog().info("[" + level + "] " + message);
                    }
                }
            }, true);

            EngineResult result = new ZipitEngine(log).run(config, ProgressListener.NONE);
            getLog().info("Zipit produced " + result.output() + " (" + result.classes()
                    + " classes, " + result.resources() + " resources, " + result.millis() + " ms)");

            if (verify) {
                List<String> errors = ZipitVerifier.verify(config.output(), config.libraries());
                if (!errors.isEmpty()) {
                    StringBuilder message = new StringBuilder("Zipit verification failed:");
                    for (String error : errors) {
                        message.append(System.lineSeparator()).append("  ").append(error);
                    }
                    throw new MojoFailureException(message.toString());
                }
                getLog().info("Zipit verification: OK");
            }
        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Zipit failed: " + e.getMessage(), e);
        }
    }

    private ZipitConfig buildConfig() throws Exception {
        ZipitConfig.Builder builder;
        ZipitConfig base = null;
        if (configFile != null && !configFile.trim().isEmpty()) {
            base = ConfigLoader.load(new File(configFile.trim()).toPath());
            builder = base.toBuilder();
        } else {
            builder = new ZipitConfig.Builder();
        }
        if (input != null) {
            builder.input(input.toPath());
        }
        if (output != null) {
            builder.output(output.toPath());
        }
        if (libraries != null && !libraries.isEmpty()) {
            List<java.nio.file.Path> paths = new ArrayList<java.nio.file.Path>();
            for (File library : libraries) {
                paths.add(library.toPath());
            }
            builder.libraries(paths);
        }
        if (keep != null && !keep.isEmpty()) {
            List<String> merged = new ArrayList<String>(base == null ? new ArrayList<String>() : base.keep());
            merged.addAll(keep);
            builder.keep(merged);
        }
        if (seed != null) {
            builder.seed(seed.longValue());
        }
        if (inputMapping != null && !inputMapping.trim().isEmpty()) {
            ZipitConfig.MappingOptions mapping = base == null
                    ? new ZipitConfig.MappingOptions(true, "zipit-mapping.txt", "")
                    : base.mapping();
            builder.mapping(new ZipitConfig.MappingOptions(mapping.enabled(), mapping.file(), inputMapping.trim()));
        }
        String html = reportHtml == null ? "" : reportHtml.trim();
        String json = reportJson == null ? "" : reportJson.trim();
        if (!html.isEmpty() || !json.isEmpty()) {
            builder.report(new ZipitConfig.ReportOptions(true, html, json));
        }

        ZipitConfig config = builder.build();
        if (preset != null && !preset.trim().isEmpty()) {
            config = Presets.apply(config, preset);
        }
        return config;
    }
}
