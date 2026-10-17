package io.zipit.gui;

import io.zipit.core.config.ZipitConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiStateTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void buildsConfigFromForm() throws Exception {
        GuiState state = new GuiState();
        state.input.setText("in.jar");
        state.output.setText("out.jar");
        state.keep.setText("com.example.Main\ncom.example.**");
        state.seed.setText("42");
        state.flowLevel.setSelectedItem("normal");
        state.flatten.setSelected(true);
        state.referenceObfuscation.setSelected(true);
        state.trim.setSelected(true);
        state.parameter.setSelected(true);
        state.licenseFile.setText("license.yml");
        state.licenseServer.setText("http://127.0.0.1:8080");
        state.reportJson.setText("audit.json");

        ZipitConfig config = state.toConfig();
        assertEquals("in.jar", config.input().toString());
        assertEquals("out.jar", config.output().toString());
        assertEquals(2, config.keep().size());
        assertEquals(42L, config.seed());
        assertEquals("normal", config.flow().level());
        assertTrue(config.flow().flattening());
        assertTrue(config.referenceObfuscation().enabled());
        assertTrue(config.trim().enabled());
        assertTrue(config.parameterObfuscation().enabled());
        assertEquals("license.yml", config.license().file());
        assertEquals("http://127.0.0.1:8080", config.license().server());
        assertEquals("audit.json", config.report().jsonFile());
        assertTrue(config.report().enabled());
    }

    @Test
    void rejectsMissingInput() {
        GuiState state = new GuiState();
        state.output.setText("out.jar");
        assertThrows(IllegalArgumentException.class, state::toConfig);
    }

    @Test
    void loadsConfigBackIntoForm() throws Exception {
        GuiState state = new GuiState();
        state.input.setText("in.jar");
        state.output.setText("out.jar");
        state.seed.setText("7");
        state.softKeepPublicApi.setSelected(true);
        state.flatten.setSelected(true);
        ZipitConfig first = state.toConfig();

        GuiState copy = new GuiState();
        copy.load(first);
        ZipitConfig second = copy.toConfig();
        assertEquals(first.input(), second.input());
        assertEquals(first.output(), second.output());
        assertEquals(first.seed(), second.seed());
        assertTrue(second.softKeepPublicApi());
        assertTrue(second.flow().flattening());
    }
}
