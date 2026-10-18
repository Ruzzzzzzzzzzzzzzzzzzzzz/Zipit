package io.zipit.core.test;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ConfigLoader;
import io.zipit.core.config.ConfigWriter;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.util.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Client-side auth gate: configuration round-trip, injection pass and template substitution. */
class AuthGateTest {

    @TempDir
    Path temp;

    @Test
    void yamlRoundTripPreservesAuthGateOptions() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(4242)
                .authGate(new ZipitConfig.AuthGateOptions(true, "http://127.0.0.1:18090", "demo-app",
                        "Demo 授权", false, "/tmp/zipit-cache.token", 4321,
                        Arrays.asList("auth.Service#run", "auth.Service#poll()V")))
                .build();
        Path yaml = temp.resolve("auth-gate.yml");
        Files.write(yaml, ConfigWriter.toYaml(config).getBytes(StandardCharsets.UTF_8));

        ZipitConfig loaded = ConfigLoader.load(yaml);
        assertTrue(loaded.authGate().enabled());
        assertEquals("http://127.0.0.1:18090", loaded.authGate().server());
        assertEquals("demo-app", loaded.authGate().software());
        assertEquals("Demo 授权", loaded.authGate().title());
        assertFalse(loaded.authGate().allowRegister());
        assertEquals("/tmp/zipit-cache.token", loaded.authGate().cacheFile());
        assertEquals(4321, loaded.authGate().timeoutMs());
        assertEquals(Arrays.asList("auth.Service#run", "auth.Service#poll()V"),
                loaded.authGate().entryPoints());

        // toBuilder() must copy the section too.
        assertEquals("demo-app", config.toBuilder().build().authGate().software());
    }

    @Test
    void passInjectsGuardIntoMainAndEntryPoints() throws Exception {
        Path input = buildSampleApp(temp);
        Path output = temp.resolve("gate-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(77)
                .antiDebug(true)
                .authGate(new ZipitConfig.AuthGateOptions(true, "http://127.0.0.1:18090", "demo-app",
                        "Demo 授权", true, "", 3000, Arrays.asList("auth.Service#run")))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);
        Map<String, Long> stats = result.stats().snapshot();
        assertTrue(stat(stats, "authGate.helper") >= 1, stats.toString());
        assertTrue(stat(stats, "authGate.entrypoints") >= 1, stats.toString());
        assertTrue(stat(stats, "authGate.classes") >= 2, stats.toString());
        assertTrue(stat(stats, "authGate.methods") >= 2, stats.toString());
        // Both guards cooperate in the main class.
        assertTrue(stat(stats, "protection.antiDebug") >= 1, stats.toString());

        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);

        Map<String, ClassNode> classes = readClasses(output);
        ClassNode gate = findGate(classes);
        assertNotNull(gate, "expected an injected zipit/runtime/A* gate class");
        assertEquals(52, gate.version & 0xFFFF, "gate class must be class-file version 52");
        MethodNode guard = findMethod(gate, "guard", "()V");
        assertNotNull(guard, "gate must expose guard()V");
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, guard.access & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC));

        int guardedMethods = 0;
        boolean guardedMain = false;
        for (ClassNode node : classes.values()) {
            if (node.name.startsWith("zipit/runtime/A")) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if (!callsGuard(method)) {
                    continue;
                }
                guardedMethods++;
                if ("main".equals(method.name) && "([Ljava/lang/String;)V".equals(method.desc)) {
                    guardedMain = true;
                }
            }
        }
        assertTrue(guardedMain, "main(String[]) must call the gate");
        assertTrue(guardedMethods >= 2, "main + configured entry point should be guarded, got " + guardedMethods);
    }

    @Test
    void templatePlaceholdersAreReplacedWithConfiguration() throws Exception {
        Path input = buildSampleApp(temp);
        Path cache = temp.resolve("cache/token.txt");
        Path output = temp.resolve("gate-config-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(99)
                .authGate(new ZipitConfig.AuthGateOptions(true, "http://127.0.0.1:19999", "software-x",
                        "X 授权验证", false, cache.toString(), 2345,
                        Arrays.asList("auth.Service#run()V")))
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        Map<String, ClassNode> classes = readClasses(output);
        ClassNode gate = findGate(classes);
        assertNotNull(gate, "expected an injected gate class");
        List<String> constants = allStrings(gate);
        assertTrue(constants.contains("http://127.0.0.1:19999"), constants.toString());
        assertTrue(constants.contains("software-x"), constants.toString());
        assertTrue(constants.contains("X 授权验证"), constants.toString());
        assertTrue(constants.contains(cache.toString()), constants.toString());
        assertTrue(constants.contains("false"), "registration flag placeholder: " + constants);
        assertTrue(constants.contains("2345"), "timeout placeholder: " + constants);
        assertTrue(constants.contains("auth.Service#run()V"), "entry points placeholder: " + constants);
        for (String constant : constants) {
            assertFalse(constant.contains("@@"), "unreplaced placeholder in gate: " + constant);
        }
        assertTrue(ZipitVerifier.verify(output).isEmpty());
    }

    @Test
    void disabledAuthGateInjectsNothing() throws Exception {
        Path input = buildSampleApp(temp);
        Path output = temp.resolve("gate-off-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(55)
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);
        assertFalse(result.stats().snapshot().containsKey("authGate.helper"),
                result.stats().snapshot().toString());
        Map<String, ClassNode> classes = readClasses(output);
        assertFalse(classes.containsKey("zipit/runtime/A"),
                "disabled gate must not inject a runtime class");
        for (ClassNode node : classes.values()) {
            for (MethodNode method : node.methods) {
                assertFalse(callsGuard(method), node.name + "." + method.name + " must not call the gate");
            }
        }
        assertTrue(ZipitVerifier.verify(output).isEmpty());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Demo app with a concrete main and an entry point used to test injection. */
    private static Path buildSampleApp(Path workDir) throws IOException {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("auth/Service.java",
                "package auth;\n" +
                "public class Service {\n" +
                "    public static void run() { System.out.println(\"service-run\"); }\n" +
                "    public void poll() { System.out.println(\"service-poll\"); }\n" +
                "}\n");
        sources.put("auth/App.java",
                "package auth;\n" +
                "public class App {\n" +
                "    public static void main(String[] args) {\n" +
                "        Service.run();\n" +
                "        new Service().poll();\n" +
                "    }\n" +
                "}\n");
        return SampleJarBuilder.buildSources(workDir, "auth-gate-demo", "auth.App", sources);
    }

    private static ClassNode findGate(Map<String, ClassNode> classes) {
        for (ClassNode node : classes.values()) {
            if (node.name.startsWith("zipit/runtime/A")) {
                return node;
            }
        }
        return null;
    }

    private static MethodNode findMethod(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                return method;
            }
        }
        return null;
    }

    private static boolean callsGuard(MethodNode method) {
        if (method.instructions == null) {
            return false;
        }
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKESTATIC
                        && "guard".equals(call.name) && "()V".equals(call.desc)
                        && call.owner.startsWith("zipit/runtime/A")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> allStrings(ClassNode node) {
        List<String> out = new ArrayList<String>();
        for (MethodNode method : node.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof String) {
                    out.add((String) ((LdcInsnNode) insn).cst);
                }
            }
        }
        return out;
    }

    private static long stat(Map<String, Long> stats, String key) {
        Long value = stats.get(key);
        return value == null ? 0L : value.longValue();
    }

    private static Map<String, ClassNode> readClasses(Path jar) throws IOException {
        Map<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                ClassNode node = new ClassNode();
                new ClassReader(readAll(zip.getInputStream(entry))).accept(node, 0);
                classes.put(node.name, node);
            }
        }
        return classes;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
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
