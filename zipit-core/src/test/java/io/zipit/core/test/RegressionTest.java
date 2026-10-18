package io.zipit.core.test;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.model.ClassPool;
import io.zipit.core.util.AsmUtil;
import io.zipit.core.util.Jars;
import io.zipit.core.util.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressions for the class-version / invokedynamic / maxStack defects found by
 * the SnakeGame repro:
 *
 * <ul>
 *   <li>invokedynamic (and its MethodHandle bootstrap) must never be written into
 *       a class file older than major 51;</li>
 *   <li>generated methods must carry realistic maxStack so the control-flow
 *       flattening Analyzer does not fail with "Insufficient maximum stack size";</li>
 *   <li>the pre-write version safety net upgrades classes whose constants need a
 *       newer class file version.</li>
 * </ul>
 */
class RegressionTest {

    private static final String LAMBDA_METAFACTORY_DESC =
            "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;"
                    + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;"
                    + "Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;";

    @TempDir
    Path temp;

    /**
     * A field access in a legacy major-45 class must still be rewritten to
     * invokedynamic, and the class version must be bumped to >= 51 so the
     * class links and verifies (baseline: ClassFormatError tag 15/18).
     */
    @Test
    void legacyMajor45FieldReferenceIsVersionBumpedAndVerifies() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("old/Legacy.java",
                "package old;\n" +
                "public class Legacy {\n" +
                "    public int value;\n" +
                "    public static int COUNT;\n" +
                "    public int read() { return value + COUNT; }\n" +
                "}\n");
        sources.put("old/LegacyMain.java",
                "package old;\n" +
                "public class LegacyMain {\n" +
                "    public static void main(String[] args) {\n" +
                "        Legacy legacy = new Legacy();\n" +
                "        legacy.value = 40;\n" +
                "        Legacy.COUNT = 2;\n" +
                "        System.out.println(legacy.read());\n" +
                "    }\n" +
                "}\n");
        Path input = SampleJarBuilder.buildSources(temp, "legacy", "old.LegacyMain", sources);
        lowerClassVersions(input, Opcodes.V1_1, false);
        assertEquals(45, classVersion(input, "old/Legacy.class"));

        Path output = temp.resolve("legacy-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x4C45)
                .renamer(noRename())
                .referenceObfuscation(new ZipitConfig.ReferenceOptions(true, true, true, true, false))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        Map<String, Long> stats = result.stats().snapshot();
        assertTrue(stat(stats, "references.fields") >= 1, "expected rewritten field references: " + stats);
        assertTrue(stat(stats, "references.versionUpgraded") >= 1,
                "expected legacy class version upgrade: " + stats);

        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);
        assertEquals("42\n", runJar(output));

        ClassNode legacy = readClass(output, "old/Legacy.class");
        assertNotNull(legacy, "legacy class missing from output");
        assertTrue((legacy.version & 0xFFFF) >= Opcodes.V1_7,
                "legacy class version must be >= 51, was " + (legacy.version & 0xFFFF));
        assertTrue(containsInvokeDynamic(legacy), "expected invokedynamic in the version-upgraded legacy class");
    }

    /**
     * Anti-LLM decoys/wrapper walls and virtualization stubs used to leave
     * maxStack at 0, which made every flattening attempt fail. Flattening must
     * produce methods now, without "Insufficient maximum stack size" skips.
     */
    @Test
    void antiLlmAndFlatteningFlattenMethodsWithoutMaxStackFailures() throws Exception {
        Path input = SampleJarBuilder.buildFlatJar(temp);
        Path output = temp.resolve("flat-antillm-out.jar");
        final List<String> messages = new ArrayList<String>();
        Log log = new Log(new Log.Sink() {
            @Override
            public void line(String level, String message) {
                messages.add(level + " " + message);
            }
        }, true);

        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x5A17)
                .antiLlm(new ZipitConfig.AntiLlmOptions(true, 2, 2, 3, 8, true))
                .methodVirtualization(new ZipitConfig.VirtualizationOptions(true, 2000, 0))
                .flow(new ZipitConfig.FlowOptions(true, "light", 8000, true))
                .build();
        EngineResult result = new ZipitEngine(log).run(config, ProgressListener.NONE);

        Map<String, Long> stats = result.stats().snapshot();
        assertTrue(stat(stats, "flow.flattened.methods") >= 1, "expected flattened methods: " + stats);
        assertTrue(stat(stats, "flow.flattened.blocks") >= 1, "expected flattened blocks: " + stats);
        assertTrue(stat(stats, "antiLlm.wrapperLayers") >= 3, "expected wrapper layers: " + stats);
        assertTrue(stat(stats, "antiLlm.classes") >= 1, "expected touched class count: " + stats);

        assertTrue(ZipitVerifier.verify(output).isEmpty());
        assertEquals(runJar(input), runJar(output));

        for (String message : messages) {
            assertFalse(message.contains("Insufficient maximum stack size"),
                    "flattening must not skip generated methods for maxStack=0: " + message);
        }
    }

    /**
     * The pre-write safety net must bump a class whose constants require a newer
     * class file version: 51 for invokedynamic/MethodHandle/MethodType, 55 for
     * ConstantDynamic.
     */
    @Test
    void versionSafetyScanBumpsClassesForInvokeDynamicAndCondy() {
        Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory",
                "metafactory", LAMBDA_METAFACTORY_DESC, false);

        ClassNode indyClass = plainClass("legacy/IndyOnly", Opcodes.V1_5);
        MethodNode indyMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "call", "()V", null, null);
        indyMethod.instructions.add(new InvokeDynamicInsnNode("bootstrap", "()V", bootstrap));
        indyMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        indyClass.methods.add(indyMethod);

        ClassNode condyClass = plainClass("legacy/CondyOnly", Opcodes.V1_8);
        MethodNode condyMethod = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "constant", "()V", null, null);
        condyMethod.instructions.add(new LdcInsnNode(new ConstantDynamic("c", "I", bootstrap)));
        condyMethod.instructions.add(new InsnNode(Opcodes.POP));
        condyMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        condyClass.methods.add(condyMethod);

        ClassNode untouchedClass = plainClass("legacy/Plain", Opcodes.V1_5);

        ClassPool pool = new ClassPool();
        pool.addGenerated(indyClass);
        pool.addGenerated(condyClass);
        pool.addGenerated(untouchedClass);

        int upgraded = AsmUtil.ensureConstantCompatibleVersions(pool, null);
        assertEquals(2, upgraded);
        assertTrue((indyClass.version & 0xFFFF) >= Opcodes.V1_7,
                "invokedynamic class must be >= 51, was " + (indyClass.version & 0xFFFF));
        assertTrue((condyClass.version & 0xFFFF) >= Opcodes.V11,
                "ConstantDynamic class must be >= 55, was " + (condyClass.version & 0xFFFF));
        assertEquals(Opcodes.V1_5, untouchedClass.version, "classes without such constants must be untouched");
        assertEquals(0, AsmUtil.ensureConstantCompatibleVersions(pool, null), "scan must be idempotent");
    }

    /**
     * End-to-end safety net: an input class that already contains invokedynamic
     * while claiming major 45 must be upgraded before writing; otherwise the JVM
     * rejects the output with "Class file version does not support constant tag".
     */
    @Test
    void engineSafetyNetUpgradesLegacyInvokeDynamicClass() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        // Keep an unmodified copy: the lowered input is intentionally not runnable
        // by the JVM (it is the exact illegal class-version/constant combination).
        Path original = temp.resolve("sample-original.jar");
        Files.copy(input, original, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        lowerClassVersions(input, Opcodes.V1_1, true);

        Path output = temp.resolve("legacy-indy-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x1D1)
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertTrue(stat(result.stats().snapshot(), "output.version.bumped") >= 1,
                "safety net should report upgraded classes: " + result.stats().snapshot());
        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);
        assertEquals(runJar(original), runJar(output));

        boolean foundInvokeDynamic = false;
        for (ClassNode node : readClasses(output).values()) {
            if (containsInvokeDynamic(node)) {
                foundInvokeDynamic = true;
                assertTrue((node.version & 0xFFFF) >= Opcodes.V1_7,
                        node.name + " contains invokedynamic but has class version "
                                + (node.version & 0xFFFF));
            }
        }
        assertTrue(foundInvokeDynamic, "expected the lambda's invokedynamic to survive the pipeline");
    }

    /**
     * Invoke-wrapper stubs, anti-LLM wrapper walls and virtualization stubs all
     * used to leave maxLocals/maxStack at 0. The combo must verify and preserve
     * semantics (the audit reproduced "Bad local variable type" on some seeds).
     */
    @Test
    void invokeWrapperAntiLlmVirtualizationComboVerifiesAcrossSeeds() throws Exception {
        Path input = SampleJarBuilder.buildVirtualizationJar(temp);
        String expected = runJar(input);
        for (long seed : new long[]{1L, 777L}) {
            Path output = temp.resolve("combo-" + seed + ".jar");
            ZipitConfig config = new ZipitConfig.Builder()
                    .input(input).output(output).seed(seed)
                    .invokeWrapper(new ZipitConfig.WrapperOptions(true, 200))
                    .antiLlm(new ZipitConfig.AntiLlmOptions(true, 2, 2, 3, 8, true))
                    .methodVirtualization(new ZipitConfig.VirtualizationOptions(true, 2000, 0))
                    .build();
            EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);
            Map<String, Long> stats = result.stats().snapshot();
            assertTrue(stat(stats, "wrapper.methods") >= 1, "seed " + seed + ": " + stats);
            assertTrue(stat(stats, "antiLlm.wrapperLayers") >= 3, "seed " + seed + ": " + stats);
            assertTrue(stat(stats, "virtualization.methods") >= 1, "seed " + seed + ": " + stats);
            List<String> errors = ZipitVerifier.verify(output);
            assertTrue(errors.isEmpty(), "seed " + seed + " verification errors: " + errors);
            assertEquals(expected, runJar(output), "seed " + seed);
        }
    }

    /**
     * Class names embedded in launcher-related manifest attributes must follow
     * the renaming, otherwise jar-in-jar loaders (Eclipse JIJ uses
     * {@code Rsrc-Main-Class}) or Spring Boot report ClassNotFoundException.
     */
    @Test
    void rewritesLauncherManifestAttributesAfterRenaming() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        addManifestAttribute(input, "Rsrc-Main-Class", "sample.App");
        addManifestAttribute(input, "Start-Class", "sample.App");

        Path output = temp.resolve("manifest-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x4D46)
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        Map<String, String> manifest = readManifest(output);
        String rsrcMain = manifest.get("Rsrc-Main-Class");
        String startClass = manifest.get("Start-Class");
        assertNotNull(rsrcMain, "Rsrc-Main-Class must be preserved: " + manifest);
        assertNotNull(startClass, "Start-Class must be preserved: " + manifest);
        assertFalse("sample.App".equals(rsrcMain), "Rsrc-Main-Class must be renamed: " + manifest);
        assertFalse("sample.App".equals(startClass), "Start-Class must be renamed: " + manifest);
        assertTrue(containsEntry(output, rsrcMain.trim().replace('.', '/') + ".class"),
                "Rsrc-Main-Class points to a missing class: " + rsrcMain);
        assertTrue(containsEntry(output, startClass.trim().replace('.', '/') + ".class"),
                "Start-Class points to a missing class: " + startClass);
    }

    /**
     * Legacy classes containing JSR/RET cannot be upgraded to class file version
     * 51 (JSR is illegal there), so invokedynamic rewriting must be skipped for
     * them while the class stays writable through the pre-1.6 frame fallback.
     */
    @Test
    void legacyJsrClassSkipsInvokeDynamicButStillVerifies() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("host/Host.java",
                "package host;\n" +
                "public class Host {\n" +
                "    public static int VALUE = 7;\n" +
                "    public static int read() { return VALUE; }\n" +
                "}\n");
        sources.put("old/JsrMain.java",
                "package old;\n" +
                "public class JsrMain {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(host.Host.read());\n" +
                "    }\n" +
                "}\n");
        Path input = SampleJarBuilder.buildSources(temp, "jsr", "old.JsrMain", sources);
        addLegacyJsrMethod(input, "old/JsrMain.class");
        assertEquals(45, classVersion(input, "old/JsrMain.class"));

        Path output = temp.resolve("jsr-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x4A53)
                .renamer(noRename())
                .referenceObfuscation(new ZipitConfig.ReferenceOptions(true, true, true, true, false))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        Map<String, Long> stats = result.stats().snapshot();
        assertEquals(1L, stat(stats, "references.skippedLegacyClasses"),
                "the legacy JSR class must skip invokedynamic rewriting: " + stats);
        assertTrue(stat(stats, "references.indy") >= 1, "other classes must still be rewritten: " + stats);

        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);
        assertEquals("7\n", runJar(output));

        ClassNode jsrClass = readClass(output, "old/JsrMain.class");
        assertNotNull(jsrClass);
        assertEquals(45, jsrClass.version & 0xFFFF, "JSR class must keep its legacy class file version");
        assertFalse(containsInvokeDynamic(jsrClass), "JSR class must keep direct references");
    }

    /**
     * Virtualized methods must treat JVM booleans as ints (0/1). A boolean
     * method parameter or return value used with IFEQ/IAND/IF_ICMP must not be
     * unboxed as java.lang.Boolean. Regression for a real Forge 1.8.9 crash:
     * {@code ClassCastException: java.lang.Boolean cannot be cast to
     * java.lang.Number at ZipitVM.i}.
     */
    @Test
    void virtualizedBooleanParametersAndReturnsMatchJvmSemantics() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("vb/BoolMain.java",
                "package vb;\n" +
                "public class BoolMain {\n" +
                "    private static boolean both(boolean a, boolean b) { return a & b; }\n" +
                "    private static int score(boolean value) { return value ? 7 : 9; }\n" +
                "    public static void main(String[] args) {\n" +
                "        boolean t = both(true, true);\n" +
                "        boolean f = both(true, false);\n" +
                "        int a = score(t);\n" +
                "        int b = score(f);\n" +
                "        boolean mixed = (t & !f) | false;\n" +
                "        System.out.println(a + \":\" + b + \":\" + (mixed ? \"T\" : \"F\"));\n" +
                "    }\n" +
                "}\n");
        Path input = SampleJarBuilder.buildSources(temp, "vbool", "vb.BoolMain", sources);
        Path output = temp.resolve("vbool-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x0B001)
                .renamer(noRename())
                .methodVirtualization(new ZipitConfig.VirtualizationOptions(true, 2000, 0))
                .flow(new ZipitConfig.FlowOptions(false, "light", 8000, false))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        Map<String, Long> stats = result.stats().snapshot();
        assertTrue(stat(stats, "virtualization.methods") >= 2,
                "expected virtualized boolean methods: " + stats);
        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);
        assertEquals("7:9:T\n", runJar(output));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static ZipitConfig.RenamerOptions noRename() {
        return new ZipitConfig.RenamerOptions(false, "", false, "", true, true, true,
                "zipit-mapping.txt", "abcdefghijklmnopqrstuvwxyz", 1);
    }

    private static long stat(Map<String, Long> stats, String key) {
        Long value = stats.get(key);
        return value == null ? 0L : value.longValue();
    }

    private static ClassNode plainClass(String name, int version) {
        ClassNode node = new ClassNode();
        node.version = version;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER;
        node.name = name;
        node.superName = "java/lang/Object";
        return node;
    }

    private static boolean containsInvokeDynamic(ClassNode node) {
        for (MethodNode method : node.methods) {
            if (method.instructions == null) {
                continue;
            }
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof InvokeDynamicInsnNode) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int classVersion(Path jar, String entryName) throws IOException {
        ClassNode node = readClass(jar, entryName);
        if (node == null) {
            throw new IOException("missing entry " + entryName + " in " + jar);
        }
        return node.version & 0xFFFF;
    }

    private static ClassNode readClass(Path jar, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                return null;
            }
            ClassNode node = new ClassNode();
            new ClassReader(readAll(zip.getInputStream(entry))).accept(node, 0);
            return node;
        }
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

    /**
     * Rewrites a JAR so selected classes declare a legacy class file version and
     * carry no StackMapTable, mimicking JDK 1.1-era input.
     *
     * @param jar              JAR to rewrite in place
     * @param version          target class file version (for example {@link Opcodes#V1_1})
     * @param onlyConstantUsers when true only classes already containing
     *                          invokedynamic are lowered
     */
    private static void lowerClassVersions(Path jar, int version, boolean onlyConstantUsers) throws IOException {
        Map<String, byte[]> entries = readEntries(jar);
        boolean changed = false;
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (!entry.getKey().endsWith(".class")) {
                continue;
            }
            ClassNode node = new ClassNode();
            new ClassReader(entry.getValue()).accept(node, 0);
            if (onlyConstantUsers && !containsInvokeDynamic(node)) {
                continue;
            }
            node.version = version;
            for (MethodNode method : node.methods) {
                if (method.instructions == null) {
                    continue;
                }
                List<AbstractInsnNode> frames = new ArrayList<AbstractInsnNode>();
                for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                    if (insn instanceof FrameNode) {
                        frames.add(insn);
                    }
                }
                for (AbstractInsnNode frame : frames) {
                    method.instructions.remove(frame);
                }
            }
            ClassWriter writer = new ClassWriter(0);
            node.accept(writer);
            entry.setValue(writer.toByteArray());
            changed = true;
        }
        if (!changed) {
            throw new IllegalStateException("no class was lowered in " + jar);
        }
        writeEntries(jar, entries);
    }

    private static boolean containsEntry(Path jar, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(entryName) != null;
        }
    }

    private static Map<String, String> readManifest(Path jar) throws IOException {
        Map<String, byte[]> entries = readEntries(jar);
        byte[] manifest = entries.get("META-INF/MANIFEST.MF");
        if (manifest == null) {
            return new LinkedHashMap<String, String>();
        }
        return Jars.parseManifest(manifest);
    }

    private static void addManifestAttribute(Path jar, String key, String value) throws IOException {
        Map<String, byte[]> entries = readEntries(jar);
        byte[] manifest = entries.get("META-INF/MANIFEST.MF");
        Map<String, String> attributes = manifest == null
                ? new LinkedHashMap<String, String>()
                : new LinkedHashMap<String, String>(Jars.parseManifest(manifest));
        attributes.put(key, value);
        entries.put("META-INF/MANIFEST.MF", Jars.writeManifest(attributes));
        writeEntries(jar, entries);
    }

    /**
     * Lowers a class to major 45, strips its StackMapTable and appends a
     * JSR/RET subroutine method, mimicking JDK 1.1-era bytecode.
     */
    private static void addLegacyJsrMethod(Path jar, String entryName) throws IOException {
        Map<String, byte[]> entries = readEntries(jar);
        byte[] bytes = entries.get(entryName);
        if (bytes == null) {
            throw new IOException("missing entry " + entryName + " in " + jar);
        }
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        node.version = Opcodes.V1_1;
        for (MethodNode method : node.methods) {
            if (method.instructions == null) {
                continue;
            }
            List<AbstractInsnNode> frames = new ArrayList<AbstractInsnNode>();
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof FrameNode) {
                    frames.add(insn);
                }
            }
            for (AbstractInsnNode frame : frames) {
                method.instructions.remove(frame);
            }
        }
        MethodNode subroutineMethod = new MethodNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "spin", "()V", null, null);
        LabelNode subroutine = new LabelNode();
        subroutineMethod.instructions.add(new JumpInsnNode(Opcodes.JSR, subroutine));
        subroutineMethod.instructions.add(new InsnNode(Opcodes.RETURN));
        subroutineMethod.instructions.add(subroutine);
        subroutineMethod.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        subroutineMethod.instructions.add(new VarInsnNode(Opcodes.RET, 0));
        subroutineMethod.maxStack = 1;
        subroutineMethod.maxLocals = 1;
        node.methods.add(subroutineMethod);
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        entries.put(entryName, writer.toByteArray());
        writeEntries(jar, entries);
    }

    private static Map<String, byte[]> readEntries(Path jar) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zip.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                entries.put(entry.getName(), readAll(zip.getInputStream(entry)));
            }
        }
        return entries;
    }

    private static void writeEntries(Path jar, Map<String, byte[]> entries) throws IOException {
        Path temporary = jar.resolveSibling(jar.getFileName() + ".lowered");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temporary))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry copy = new ZipEntry(entry.getKey());
                out.putNextEntry(copy);
                out.write(entry.getValue());
                out.closeEntry();
            }
        }
        Files.move(temporary, jar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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

    private static String runJar(Path jar) throws Exception {
        String javaBin = System.getProperty("java.home") + File.separator
                + "bin" + File.separator + "java";
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
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out running " + jar);
        }
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
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
