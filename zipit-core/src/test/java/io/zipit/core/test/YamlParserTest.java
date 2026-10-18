package io.zipit.core.test;

import io.zipit.core.config.ConfigException;
import io.zipit.core.config.ConfigLoader;
import io.zipit.core.config.ConfigWriter;
import io.zipit.core.config.YamlParser;
import io.zipit.core.config.ZipitConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlParserTest {

    @Test
    @SuppressWarnings("unchecked")
    void parsesNestedMapsAndLists() throws Exception {
        String yaml =
                "# comment\n" +
                "input: in.jar\n" +
                "keep:\n" +
                "  - com.example.Main\n" +
                "  - \"com.example.api.**\"\n" +
                "renamer:\n" +
                "  enabled: true\n" +
                "  prefix: \"z\"\n" +
                "libraries: [a.jar, b.jar]\n";
        Map<String, Object> root = YamlParser.parse(yaml);
        assertEquals("in.jar", root.get("input"));
        assertEquals(2, ((List<?>) root.get("keep")).size());
        Map<String, Object> renamer = (Map<String, Object>) root.get("renamer");
        assertEquals(Boolean.TRUE, renamer.get("enabled"));
        assertEquals("z", renamer.get("prefix"));
        assertEquals(2, ((List<?>) root.get("libraries")).size());
    }

    @Test
    void loadsFullConfigurationAndRoundTrips() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("zipit-config");
        String input = dir.resolve("in.jar").toString().replace('\\', '/');
        String output = dir.resolve("out.jar").toString().replace('\\', '/');
        String yaml =
                "input: " + input + "\n" +
                "output: " + output + "\n" +
                "seed: 123\n" +
                "keep:\n" +
                "  - com.example.Main\n" +
                "renamer:\n" +
                "  enabled: true\n" +
                "  prefix: \"x\"\n" +
                "stringEncryption:\n" +
                "  enabled: true\n" +
                "  minLength: 3\n" +
                "flowObfuscation:\n" +
                "  enabled: false\n" +
                "  level: normal\n" +
                "watermark:\n" +
                "  enabled: true\n" +
                "  id: \"acme\"\n" +
                "methodVirtualization:\n" +
                "  enabled: true\n" +
                "  maxInstructions: 1500\n" +
                "  maxMethods: 2\n" +
                "antiLlm:\n" +
                "  enabled: true\n" +
                "  decoyMethodsPerClass: 3\n" +
                "  wrapperLayers: 2\n";
        java.nio.file.Path file = dir.resolve("zipit.yml");
        java.nio.file.Files.write(file, yaml.getBytes("UTF-8"));
        ZipitConfig config = ConfigLoader.load(file);
        assertEquals(123L, config.seed());
        assertEquals("x", config.renamer().prefix());
        assertEquals(3, config.stringEncryption().minLength());
        assertFalse(config.flow().enabled());
        assertEquals("normal", config.flow().level());
        assertEquals("acme", config.watermark().id());
        assertTrue(config.methodVirtualization().enabled());
        assertEquals(1500, config.methodVirtualization().maxInstructions());
        assertEquals(2, config.methodVirtualization().maxMethods());
        assertTrue(config.antiLlm().enabled());
        assertEquals(3, config.antiLlm().decoyMethodsPerClass());
        assertEquals(2, config.antiLlm().wrapperLayers());

        String written = ConfigWriter.toYaml(config);
        Map<String, Object> reparsed = YamlParser.parse(written);
        assertEquals(input.replace('\\', '/'), String.valueOf(reparsed.get("input")).replace('\\', '/'));
        assertTrue(reparsed.containsKey("stringEncryption"));
        assertTrue(reparsed.containsKey("methodVirtualization"));
        assertTrue(reparsed.containsKey("antiLlm"));
    }

    @Test
    void rejectsMissingInput() throws Exception {
        String yaml = "output: /tmp/out.jar\n";
        boolean failed = false;
        try {
            ConfigLoader.fromMap(YamlParser.parse(yaml), Paths.get("/tmp"));
        } catch (ConfigException e) {
            failed = true;
        }
        assertTrue(failed);
    }
}
