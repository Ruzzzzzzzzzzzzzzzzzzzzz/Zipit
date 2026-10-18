package io.zipit.core.test;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.util.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndToEndTest {

    @TempDir
    Path temp;

    @Test
    void obfuscatesRunsAndVerifies() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("out.jar");

        final List<String> messages = new ArrayList<String>();
        Log log = new Log(new Log.Sink() {
            @Override
            public void line(String level, String message) {
                messages.add(level + " " + message);
            }
        }, true);

        ZipitConfig config = new ZipitConfig.Builder()
                .input(input)
                .output(output)
                .seed(0x51ED)
                .build();

        EngineResult result = new ZipitEngine(log).run(config, ProgressListener.NONE);
        assertFalse(result.dryRun());
        assertTrue(Files.exists(output));

        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);

        RunResult original = runJar(input);
        RunResult obfuscated = runJar(output);
        assertEquals(0, original.exitCode, original.output);
        assertEquals(0, obfuscated.exitCode, obfuscated.output);
        assertEquals(original.output, obfuscated.output);

        assertFalse(containsEntry(output, "sample/App.class"), "main class should be renamed");
        assertFalse(containsPlaintext(output, "hello-zipit-secret"), "secret string should be encrypted");

        Path mapping = temp.resolve("zipit-mapping.txt");
        assertTrue(Files.exists(mapping), "mapping file should be written");
        String mappingText = new String(Files.readAllBytes(mapping), StandardCharsets.UTF_8);
        assertTrue(mappingText.contains("sample/App ->"), mappingText);
    }

    @Test
    void sameSeedProducesIdenticalOutput() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path first = temp.resolve("first.jar");
        Path second = temp.resolve("second.jar");

        ZipitConfig base = new ZipitConfig.Builder()
                .input(input)
                .output(first)
                .seed(777)
                .build();
        new ZipitEngine(discardLog()).run(base, ProgressListener.NONE);
        ZipitConfig secondConfig = base.toBuilder().output(second).build();
        new ZipitEngine(discardLog()).run(secondConfig, ProgressListener.NONE);

        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void dryRunDoesNotWrite() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("dry.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input)
                .output(output)
                .dryRun(true)
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);
        assertTrue(result.dryRun());
        assertFalse(Files.exists(output));
    }

    private static Log discardLog() {
        return new Log(new Log.Sink() {
            @Override
            public void line(String level, String message) {
                // discard
            }
        }, false);
    }

    private static boolean containsEntry(Path jar, String name) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(name) != null;
        }
    }

    private static boolean containsPlaintext(Path jar, String needle) throws Exception {
        byte[] pattern = needle.getBytes(StandardCharsets.UTF_8);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                byte[] bytes = readAll(zip.getInputStream(entry));
                if (indexOf(bytes, pattern) >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static RunResult runJar(Path jar) throws Exception {
        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        ProcessBuilder builder = new ProcessBuilder(java, "-jar", jar.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InputStream in = process.getInputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out running " + jar);
        }
        return new RunResult(process.exitValue(),
                new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n"));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static final class RunResult {
        final int exitCode;
        final String output;

        RunResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }
}
