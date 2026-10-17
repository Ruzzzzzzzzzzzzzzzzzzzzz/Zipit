package io.zipit.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

/**
 * Gradle plugin entry point.
 *
 * <p>Registers the {@code zipit} extension and task:</p>
 *
 * <pre>
 * plugins { id 'io.zipit.gradle' }
 * zipit {
 *     input = file("build/libs/app.jar")
 *     output = file("build/libs/app-obfuscated.jar")
 * }
 * </pre>
 */
public class ZipitPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        final ZipitExtension extension = project.getExtensions().create("zipit", ZipitExtension.class);

        TaskProvider<ZipitTask> task = project.getTasks().register("zipit", ZipitTask.class, zipit -> {
            zipit.setGroup("build");
            zipit.setDescription("Obfuscates a JAR with Zipit");
            zipit.getInputJar().set(extension.getInput());
            zipit.getOutputJar().set(extension.getOutput());
            zipit.getLibraries().from(extension.getLibraries());
            zipit.getKeepRules().set(extension.getKeep());
            zipit.getConfigFile().set(extension.getConfigFile());
            zipit.getSeed().set(extension.getSeed());
            zipit.getVerify().set(extension.getVerify());
            zipit.getKeepPublicApi().set(extension.getKeepPublicApi());
            zipit.getPreset().set(extension.getPreset());
            zipit.getInputMapping().set(extension.getInputMapping());
            zipit.getReportHtml().set(extension.getReportHtml());
            zipit.getReportJson().set(extension.getReportJson());
        });

        project.getTasks().configureEach(candidate -> {
            if ("build".equals(candidate.getName())) {
                candidate.dependsOn(task);
            }
        });
    }
}
