package io.zipit.core.test;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ConfigException;
import io.zipit.core.config.ConfigLoader;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.util.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressions for the independently audited latent defects fixed during the
 * hardening round (watermark on interfaces, anti-debug on abstract/native main,
 * member-level {@code @DoNotRename}, string interning, prefixed fat-jar
 * verification, multi-release fail-loud and CLI config overrides).
 */
class HardeningTest {

    @TempDir
    Path temp;

    /** P0: watermark field injection must skip interface/annotation types. */
    @Test
    void watermarkSkipsInterfaceAndAnnotationTypes() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("wm/A.java",
                "package wm;\n" +
                "public interface A { void m(); }\n");
        sources.put("wm/Anno.java",
                "package wm;\n" +
                "public @interface Anno { int value() default 0; }\n");
        sources.put("wm/B.java",
                "package wm;\n" +
                "public class B implements A {\n" +
                "    public void m() { System.out.println(\"wm-ok\"); }\n" +
                "    public static void main(String[] args) { new B().m(); }\n" +
                "}\n");
        Path input = SampleJarBuilder.buildSources(temp, "watermark", "wm.B", sources);
        Path output = temp.resolve("watermark-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x5701)
                .renamer(noRename())
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals(1L, stat(result.stats().snapshot(), "watermark.embedded"));
        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);
        assertEquals("wm-ok\n", runJar(output));

        ClassNode holder = readClass(output, "wm/B.class");
        assertNotNull(holder, "class wm/B missing from output");
        boolean marker = false;
        for (FieldNode field : holder.fields) {
            if (field.name.startsWith("zipit$")) {
                marker = true;
            }
        }
        assertTrue(marker, "watermark field should be embedded in the concrete class, not the interface");
        ClassNode interfaceNode = readClass(output, "wm/A.class");
        if (interfaceNode != null) {
            assertNull(watermarkField(interfaceNode), "interface wm/A must not receive a watermark field");
        }
    }

    /** P0: anti-debug must not insert code into abstract/native main methods. */
    @Test
    void antiDebugSkipsAbstractAndNativeMain() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("ad/AbsMain.java",
                "package ad;\n" +
                "public abstract class AbsMain { public abstract void main(String[] args); }\n");
        sources.put("ad/NatMain.java",
                "package ad;\n" +
                "public class NatMain { public static native void main(String[] args); }\n");
        sources.put("ad/ConcreteMain.java",
                "package ad;\n" +
                "public class ConcreteMain {\n" +
                "    public static void main(String[] args) { System.out.println(\"concrete-ok\"); }\n" +
                "}\n");
        Path input = SampleJarBuilder.buildSources(temp, "abmain", "ad.ConcreteMain", sources);
        Path output = temp.resolve("abmain-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x5702)
                .antiDebug(true)
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertTrue(stat(result.stats().snapshot(), "protection.antiDebug") >= 1,
                "concrete main should still be instrumented: " + result.stats().snapshot());
        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);
        assertEquals("concrete-ok\n", runJar(output));
    }

    /** P1: @DoNotRename on methods/fields must keep their names. */
    @Test
    void doNotRenameAnnotationKeepsMemberNames() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("io/zipit/annotations/DoNotRename.java",
                "package io.zipit.annotations;\n" +
                "import java.lang.annotation.Documented;\n" +
                "import java.lang.annotation.ElementType;\n" +
                "import java.lang.annotation.Retention;\n" +
                "import java.lang.annotation.RetentionPolicy;\n" +
                "import java.lang.annotation.Target;\n" +
                "@Documented\n" +
                "@Retention(RetentionPolicy.CLASS)\n" +
                "@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})\n" +
                "public @interface DoNotRename {}\n");
        sources.put("dr/Demo.java",
                "package dr;\n" +
                "public class Demo {\n" +
                "    @io.zipit.annotations.DoNotRename public int annotatedField = 1;\n" +
                "    @io.zipit.annotations.DoNotRename public void annotatedMethod() { }\n" +
                "    public void plainMethod() { }\n" +
                "    public static void main(String[] args) {\n" +
                "        Demo demo = new Demo();\n" +
                "        demo.annotatedMethod();\n" +
                "        demo.plainMethod();\n" +
                "        System.out.println(\"field=\" + demo.annotatedField);\n" +
                "    }\n" +
                "}\n");
        Path input = SampleJarBuilder.buildSources(temp, "donotrename", "dr.Demo", sources);
        Path output = temp.resolve("donotrename-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x5703)
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertTrue(ZipitVerifier.verify(output).isEmpty());
        assertEquals("field=1\n", runJar(output));

        boolean keptField = false;
        boolean keptMethod = false;
        boolean plainMethodRenamed = true;
        for (ClassNode node : readClasses(output).values()) {
            for (FieldNode field : node.fields) {
                if ("annotatedField".equals(field.name)) {
                    keptField = true;
                }
            }
            for (MethodNode method : node.methods) {
                if ("annotatedMethod".equals(method.name)) {
                    keptMethod = true;
                }
                if ("plainMethod".equals(method.name)) {
                    plainMethodRenamed = false;
                }
            }
        }
        assertTrue(keptField, "@DoNotRename field must not be renamed");
        assertTrue(keptMethod, "@DoNotRename method must not be renamed");
        assertTrue(plainMethodRenamed, "unannotated method should still be renamed");
    }

    /** P1: encrypted literals must keep Java string interning/identity semantics. */
    @Test
    void stringInterningSemanticsPreserved() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("intern/InternMain.java",
                "package intern;\n" +
                "public class InternMain {\n" +
                "    public static void main(String[] args) {\n" +
                "        String interned = new String(\"hello\").intern();\n" +
                "        System.out.println(\"literal-identity=\" + (interned == \"hello\"));\n" +
                "        String a = \"hello\";\n" +
                "        String b = \"hello\";\n" +
                "        System.out.println(\"literal-literal=\" + (a == b));\n" +
                "        System.out.println(\"intern-roundtrip=\" + (a == new String(\"hello\").intern()));\n" +
                "        Object lock1 = \"LOCK\";\n" +
                "        Object lock2 = \"LOCK\";\n" +
                "        System.out.println(\"lock-identity=\" + (lock1 == lock2));\n" +
                "        synchronized (\"LOCK\") { System.out.println(\"sync-ok=true\"); }\n" +
                "    }\n" +
                "}\n");
        Path input = SampleJarBuilder.buildSources(temp, "intern", "intern.InternMain", sources);
        Path output = temp.resolve("intern-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x5704)
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertTrue(stat(result.stats().snapshot(), "strings.encrypted") >= 3,
                "expected encrypted literals: " + result.stats().snapshot());
        assertFalse(containsPlaintext(output, "hello"), "literals should be encrypted in the output");
        assertTrue(ZipitVerifier.verify(output).isEmpty());
        String expected = "literal-identity=true\n"
                + "literal-literal=true\n"
                + "intern-roundtrip=true\n"
                + "lock-identity=true\n"
                + "sync-ok=true\n";
        assertEquals(expected, runJar(input));
        assertEquals(expected, runJar(output), "interning/identity semantics must survive string encryption");
    }

    /** P1: verifier must handle BOOT-INF/classes and WEB-INF/classes layouts. */
    @Test
    void verifierHandlesBootInfClassesPrefix() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("pfx/Foo.java",
                "package pfx;\n" +
                "public class Foo { public int value() { return 40; } }\n");
        sources.put("pfx/Bar.java",
                "package pfx;\n" +
                "public class Bar {\n" +
                "    public static void main(String[] args) { System.out.println(new Foo().value() + 2); }\n" +
                "}\n");
        Path input = SampleJarBuilder.buildSources(temp, "prefixed", "pfx.Bar", sources);
        moveEntry(input, "pfx/Foo.class", "BOOT-INF/classes/pfx/Foo.class");
        assertTrue(containsEntry(input, "BOOT-INF/classes/pfx/Foo.class"));

        Path output = temp.resolve("prefixed-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x5705)
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        List<String> errors = ZipitVerifier.verify(output);
        assertTrue(errors.isEmpty(), "verification errors: " + errors);
        for (String error : errors) {
            assertFalse(error.contains("BOOT-INF"), "bogus prefixed class name in verifier output: " + error);
        }
        boolean prefixedKept = false;
        try (ZipFile zip = new ZipFile(output.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                if (entries.nextElement().getName().startsWith("BOOT-INF/classes/")) {
                    prefixedKept = true;
                }
            }
        }
        assertTrue(prefixedKept, "BOOT-INF/classes/ entries should survive the round-trip");
    }

    /** P0: duplicate internal class names (multi-release jars) must fail loudly. */
    @Test
    void multiReleaseDuplicateFailsLoudly() throws Exception {
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("mr/Main.java",
                "package mr;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) { System.out.println(\"mr-ok\"); }\n" +
                "}\n");
        Path input = SampleJarBuilder.buildSources(temp, "mr", "mr.Main", sources);
        Map<String, byte[]> entries = readEntries(input);
        byte[] mainBytes = entries.get("mr/Main.class");
        assertNotNull(mainBytes);
        entries.put("META-INF/versions/9/mr/Main.class", mainBytes);
        writeEntries(input, entries);

        Path output = temp.resolve("mr-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(0x5706)
                .build();
        IOException failure = assertThrows(IOException.class,
                () -> new ZipitEngine(discardLog()).run(config, ProgressListener.NONE));
        String message = String.valueOf(failure.getMessage());
        assertTrue(message.contains("duplicate") && message.contains("not supported"),
                "expected a clear multi-release error, got: " + message);
        assertTrue(message.contains("mr/Main"), "error should name the duplicated class: " + message);
        assertFalse(Files.exists(output), "no corrupt output should be written on failure");
    }

    /** P1: a config file without input/output must work when the CLI overrides them. */
    @Test
    void configFileWithoutPathsCanBeOverridden() throws Exception {
        Path yaml = temp.resolve("partial.yml");
        Files.write(yaml, ("preset: spring-boot\nseed: 4242\n").getBytes(StandardCharsets.UTF_8));

        ZipitConfig partial = ConfigLoader.load(yaml, false);
        assertNull(partial.input(), "lenient load should allow a missing input");
        assertNull(partial.output(), "lenient load should allow a missing output");
        assertEquals(4242L, partial.seed());

        ConfigException strict = assertThrows(ConfigException.class, () -> ConfigLoader.load(yaml));
        assertTrue(String.valueOf(strict.getMessage()).contains("Missing required configuration key: input"),
                "strict load must keep the clear error: " + strict.getMessage());

        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("cli-out.jar");
        ZipitConfig effective = partial.toBuilder()
                .input(input)
                .output(output)
                .build();
        assertEquals(input, effective.input());
        assertEquals(output, effective.output());
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

    private static String watermarkField(ClassNode node) {
        for (FieldNode field : node.fields) {
            if (field.name.startsWith("zipit$")) {
                return field.name;
            }
        }
        return null;
    }

    private static boolean containsEntry(Path jar, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry(entryName) != null;
        }
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
        Path temporary = jar.resolveSibling(jar.getFileName() + ".tmp");
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

    private static void moveEntry(Path jar, String from, String to) throws IOException {
        Map<String, byte[]> entries = readEntries(jar);
        byte[] bytes = entries.remove(from);
        if (bytes == null) {
            throw new IOException("missing entry " + from + " in " + jar);
        }
        entries.put(to, bytes);
        writeEntries(jar, entries);
    }

    private static boolean containsPlaintext(Path jar, String needle) throws IOException {
        byte[] pattern = needle.getBytes(StandardCharsets.UTF_8);
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                if (indexOf(readAll(zip.getInputStream(entry)), pattern) >= 0) {
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
