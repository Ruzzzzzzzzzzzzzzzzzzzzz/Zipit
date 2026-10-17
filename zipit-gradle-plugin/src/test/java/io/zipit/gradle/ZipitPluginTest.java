package io.zipit.gradle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ZipitPluginTest {

    @Test
    void registersExtensionAndTask() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("io.zipit.gradle");

        assertNotNull(project.getExtensions().findByType(ZipitExtension.class));
        assertNotNull(project.getTasks().findByName("zipit"));
    }
}
