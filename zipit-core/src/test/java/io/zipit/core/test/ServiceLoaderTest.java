package io.zipit.core.test;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.util.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceLoaderTest {

    @TempDir
    Path temp;

    @Test
    void rewritesServiceDescriptors() throws Exception {
        Path input = SampleJarBuilder.buildServiceJar(temp);
        Path output = temp.resolve("service-out.jar");

        ZipitConfig config = new ZipitConfig.Builder()
                .input(input)
                .output(output)
                .seed(123)
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);

        assertEquals("impl-one\n", runJar(input));
        assertEquals("impl-one\n", runJar(output));

        List<String> serviceEntries = new ArrayList<String>();
        try (ZipFile zip = new ZipFile(output.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith("META-INF/services/") && !entry.isDirectory()) {
                    serviceEntries.add(entry.getName());
                    String content = new String(readAll(zip.getInputStream(entry)), StandardCharsets.UTF_8).trim();
                    assertFalse(content.equals("svc.Impl"), "service implementation should be renamed");
                }
            }
        }
        assertEquals(1, serviceEntries.size());
        assertFalse(serviceEntries.get(0).equals("META-INF/services/svc.Service"),
                "service interface file name should be renamed");
        assertTrue(Files.exists(temp.resolve("zipit-mapping.txt")));
    }

    private static Log discardLog() {
        return new Log(new Log.Sink() {
            @Override
            public void line(String level, String message) {
                // discard
            }
        }, false);
    }

    private static String runJar(Path jar) throws Exception {
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
        assertEquals(0, process.exitValue(), new String(out.toByteArray(), StandardCharsets.UTF_8));
        return new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
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
}
