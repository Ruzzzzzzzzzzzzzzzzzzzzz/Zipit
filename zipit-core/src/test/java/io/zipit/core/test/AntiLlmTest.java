package io.zipit.core.test;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.util.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AntiLlmTest {

    @TempDir
    Path temp;

    @Test
    void decoysWallsAndPayloadShieldingPreserveSemantics() throws Exception {
        Path input = SampleJarBuilder.buildVirtualizationJar(temp);
        Path output = temp.resolve("anti-llm-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(51)
                .methodVirtualization(new ZipitConfig.VirtualizationOptions(true, 2000, 0))
                .antiLlm(new ZipitConfig.AntiLlmOptions(true, 2, 2, 1, 12, true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals(runJar(input), runJar(output));
        assertTrue(ZipitVerifier.verify(output).isEmpty());

        Map<String, Long> stats = result.stats().snapshot();
        assertTrue(stats.get("antiLlm.decoys").longValue() >= 2, stats.toString());
        assertTrue(stats.get("antiLlm.callsites").longValue() >= 1, stats.toString());
        assertTrue(stats.get("antiLlm.wrapperLayers").longValue() >= 1, stats.toString());
        assertTrue(stats.get("antiLlm.seeds").longValue() >= 1, stats.toString());
        assertTrue(stats.get("antiLlm.shieldedPayloads").longValue() >= 1, stats.toString());
        assertTrue(stats.get("antiLlm.keyHolders").longValue() >= 1, stats.toString());
        assertTrue(stats.get("virtualization.methods").longValue() >= 1, stats.toString());
    }

    @Test
    void multipleWrapperLayersPreserveSemantics() throws Exception {
        Path input = SampleJarBuilder.buildVirtualizationJar(temp);
        Path output = temp.resolve("anti-llm-layers-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(52)
                .methodVirtualization(new ZipitConfig.VirtualizationOptions(true, 2000, 0))
                .antiLlm(new ZipitConfig.AntiLlmOptions(true, 3, 3, 3, 12, true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals(runJar(input), runJar(output));
        assertTrue(ZipitVerifier.verify(output).isEmpty());
        assertTrue(result.stats().snapshot().get("antiLlm.wrapperLayers").longValue() >= 3,
                result.stats().snapshot().toString());
    }

    private static String runJar(Path jar) throws Exception {
        String javaBin = System.getProperty("java.home") + java.io.File.separator
                + "bin" + java.io.File.separator + "java";
        ProcessBuilder builder = new ProcessBuilder(javaBin, "-jar", jar.toString());
        builder.redirectErrorStream(true);
        Process process = builder.start();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.InputStream in = process.getInputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out running " + jar);
        }
        String text = new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        assertEquals(0, process.exitValue(), text);
        return text;
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
