package io.zipit.core.test;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ConfigException;
import io.zipit.core.config.Presets;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.MappingSet;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.enterprise.MappingDiff;
import io.zipit.core.enterprise.MappingReader;
import io.zipit.core.util.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnterpriseTest {

    @TempDir
    Path temp;

    @Test
    void writesAuditReports() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("audit-out.jar");
        Path html = temp.resolve("report/audit.html");
        Path json = temp.resolve("report/audit.json");

        ZipitConfig config = new ZipitConfig.Builder()
                .input(input)
                .output(output)
                .seed(321)
                .report(new ZipitConfig.ReportOptions(true, html.toString(), json.toString()))
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertTrue(Files.exists(html));
        assertTrue(Files.exists(json));
        String jsonText = new String(Files.readAllBytes(json), StandardCharsets.UTF_8);
        assertTrue(jsonText.contains("\"risk\""));
        assertTrue(jsonText.contains("\"stats\""));
        assertTrue(jsonText.contains("\"classes\""));
        String htmlText = new String(Files.readAllBytes(html), StandardCharsets.UTF_8);
        assertTrue(htmlText.contains("Zipit Enterprise Audit"));
        assertTrue(htmlText.contains("Recommendations"));
    }

    @Test
    void inputMappingKeepsNamesStableAcrossSeeds() throws Exception {
        Path input = SampleJarBuilder.build(temp);

        Path firstOut = temp.resolve("first.jar");
        ZipitConfig first = new ZipitConfig.Builder()
                .input(input)
                .output(firstOut)
                .seed(111)
                .build();
        new ZipitEngine(discardLog()).run(first, ProgressListener.NONE);
        Path mappingFile = temp.resolve("zipit-mapping.txt");
        MappingSet firstMapping = MappingReader.read(mappingFile);

        Path secondOut = temp.resolve("second.jar");
        ZipitConfig second = first.toBuilder()
                .output(secondOut)
                .seed(999)
                .mapping(new ZipitConfig.MappingOptions(true, "zipit-mapping.txt", mappingFile.toString()))
                .build();
        new ZipitEngine(discardLog()).run(second, ProgressListener.NONE);
        MappingSet secondMapping = MappingReader.read(mappingFile);

        assertEquals(firstMapping.classes(), secondMapping.classes());
        assertEquals(firstMapping.methods(), secondMapping.methods());
        assertEquals(firstMapping.fields(), secondMapping.fields());
    }

    @Test
    void keepPublicApiPreservesClassNames() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("api.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input)
                .output(output)
                .seed(55)
                .keepPublicApi(true)
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertTrue(zip.getEntry("sample/App.class") != null, "public class should be kept");
        }
        MappingSet mapping = MappingReader.read(temp.resolve("zipit-mapping.txt"));
        assertFalse(mapping.classes().containsKey("sample/App"));
        assertEquals("ok\n", runJar(output));
    }

    @Test
    void presetsAddKeepRules() throws Exception {
        ZipitConfig base = new ZipitConfig.Builder()
                .input(temp.resolve("in.jar"))
                .output(temp.resolve("out.jar"))
                .build();
        ZipitConfig spring = Presets.apply(base, "spring-boot");
        assertTrue(spring.keep().contains("@org.springframework.**"));
        assertTrue(spring.keep().contains("**Controller"));
        ZipitConfig library = Presets.apply(base, "library");
        assertTrue(library.softKeepPublicApi());
        assertThrows(ConfigException.class, () -> Presets.apply(base, "does-not-exist"));
    }

    @Test
    void mappingDiffReportsChanges() throws Exception {
        Path oldMap = temp.resolve("old.txt");
        Path newMap = temp.resolve("new.txt");
        Files.write(oldMap, ("a/A -> x/X:\n"
                + "    foo()V -> a\n"
                + "    count:I -> b\n").getBytes(StandardCharsets.UTF_8));
        Files.write(newMap, ("a/A -> x/X:\n"
                + "    foo()V -> c\n"
                + "    count:I -> b\n"
                + "    bar()V -> d\n").getBytes(StandardCharsets.UTF_8));
        String diff = MappingDiff.compare(oldMap, newMap);
        assertTrue(diff.contains("changed: 0, removed: 0, changed: 1")
                || diff.contains("added: 1, removed: 0, changed: 1"), diff);
        assertTrue(diff.contains("~ a/A#foo#()V"), diff);
        assertTrue(diff.contains("+ a/A#bar#()V"), diff);
    }

    private static String runJar(Path jar) throws Exception {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder builder = new ProcessBuilder(javaBin, "-jar", jar.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = process.getInputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        process.waitFor(60, TimeUnit.SECONDS);
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        return text.contains("Hi zipit") ? "ok\n" : text;
    }

    private static Log discardLog() {
        return new Log(new Log.Sink() {
            @Override
            public void line(String level, String message) {
                // discard
            }
        }, false);
    }
}
