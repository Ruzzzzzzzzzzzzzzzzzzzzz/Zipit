package io.zipit.core.test;

import io.zipit.core.api.ProgressListener;
import io.zipit.core.config.ZipitConfig;
import io.zipit.core.engine.EngineResult;
import io.zipit.core.engine.MappingSet;
import io.zipit.core.engine.ZipitEngine;
import io.zipit.core.engine.ZipitVerifier;
import io.zipit.core.enterprise.LicenseException;
import io.zipit.core.enterprise.LicenseInfo;
import io.zipit.core.enterprise.LicenseTool;
import io.zipit.core.enterprise.MappingReader;
import io.zipit.core.enterprise.WatermarkTracer;
import io.zipit.core.util.Log;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedTest {

    @TempDir
    Path temp;

    @Test
    void reflectionAnalysisKeepsReferencedNames() throws Exception {
        Path input = SampleJarBuilder.buildReflectionJar(temp);
        Path output = temp.resolve("reflection-out.jar");
        ZipitConfig config = new ZipitConfig.Builder().input(input).output(output).seed(11).build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertTrue(result.stats().snapshot().get("reflection.autoKeep.classes").longValue() >= 1);
        assertTrue(result.stats().snapshot().get("reflection.autoKeep.members").longValue() >= 2);
        assertEquals("reflected-secret\n7\n", runJar(output));
        assertTrue(ZipitVerifier.verify(output).isEmpty());

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertNotNull(zip.getEntry("reflect/ReflectTarget.class"), "reflected class must keep its name");
        }
        MappingSet mapping = MappingReader.read(temp.resolve("zipit-mapping.txt"));
        assertFalse(mapping.classes().containsKey("reflect/ReflectTarget"));
        assertFalse(mapping.methods().containsKey("reflect/ReflectTarget#secret#()Ljava/lang/String;"));
        assertFalse(mapping.fields().containsKey("reflect/ReflectTarget#hiddenField#I"));
    }

    @Test
    void trimRemovesUnusedPrivateMembers() throws Exception {
        Path input = SampleJarBuilder.buildTrimJar(temp);
        Path output = temp.resolve("trim-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(12)
                .trim(new ZipitConfig.TrimOptions(true, true, true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("42\n", runJar(output));
        assertTrue(result.stats().snapshot().get("trim.methods").longValue() >= 1);
        assertTrue(result.stats().snapshot().get("trim.fields").longValue() >= 2);

        boolean unusedMethod = false;
        boolean unusedField = false;
        for (ClassNode node : readClasses(output).values()) {
            for (MethodNode method : node.methods) {
                if ("unusedHelper".equals(method.name)) {
                    unusedMethod = true;
                }
            }
            for (FieldNode field : node.fields) {
                if ("unusedField".equals(field.name) || "unusedInstance".equals(field.name)) {
                    unusedField = true;
                }
            }
        }
        assertFalse(unusedMethod, "unused private method should be removed");
        assertFalse(unusedField, "unused private fields should be removed");
    }

    @Test
    void parameterObfuscationUpdatesCallSites() throws Exception {
        Path input = SampleJarBuilder.buildParameterJar(temp);
        Path output = temp.resolve("parameter-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(13)
                .parameterObfuscation(new ZipitConfig.ParameterOptions(true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("42\n42\n", runJar(output));
        assertTrue(result.stats().snapshot().get("parameters.changed").longValue() >= 2);
        assertTrue(result.stats().snapshot().get("parameters.callsites").longValue() >= 2);
        assertTrue(ZipitVerifier.verify(output).isEmpty());
    }

    @Test
    void flowLevelsPreserveSemantics() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        String expected = runJar(input);
        for (String level : new String[]{"normal", "aggressive"}) {
            Path output = temp.resolve("flow-" + level + ".jar");
            ZipitConfig config = new ZipitConfig.Builder()
                    .input(input).output(output).seed(14)
                    .flow(new ZipitConfig.FlowOptions(true, level, 8000))
                    .build();
            EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);
            assertEquals(expected, runJar(output), level);
            assertTrue(ZipitVerifier.verify(output).isEmpty(), level);
            assertTrue(result.stats().snapshot().get("flow.branches.inverted").longValue() > 0, level);
        }
    }

    @Test
    void flattensEligibleMethods() throws Exception {
        Path input = SampleJarBuilder.buildFlatJar(temp);
        Path output = temp.resolve("flat-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(20)
                .flow(new ZipitConfig.FlowOptions(true, "light", 8000, true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertTrue(result.stats().snapshot().get("flow.flattened.methods").longValue() >= 2,
                result.stats().snapshot().toString());
        assertTrue(ZipitVerifier.verify(output).isEmpty());
        assertEquals("2\n45\n", runJar(output));
    }

    @Test
    void virtualizesMethodsAndPreservesSemantics() throws Exception {
        Path input = SampleJarBuilder.buildVirtualizationJar(temp);
        Path output = temp.resolve("virtualization-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(40)
                .methodVirtualization(new ZipitConfig.VirtualizationOptions(true, 2000, 0))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals(runJar(input), runJar(output));
        assertTrue(result.stats().snapshot().get("virtualization.methods").longValue() >= 4,
                result.stats().snapshot().toString());
        assertTrue(result.stats().snapshot().get("virtualization.instructions").longValue() > 0,
                result.stats().snapshot().toString());
        assertTrue(ZipitVerifier.verify(output).isEmpty());

        boolean holder = false;
        for (String name : readClasses(output).keySet()) {
            if (name.startsWith("zipit/runtime/V")) {
                holder = true;
            }
        }
        assertTrue(holder, "expected a virtualization payload holder class");
    }

    @Test
    void repackageMovesRenamedClassesAndPreservesSemantics() throws Exception {
        Path input = SampleJarBuilder.buildVirtualizationJar(temp);
        Path output = temp.resolve("repackage-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(60)
                .renamer(new ZipitConfig.RenamerOptions(true, "", true, "*",
                        true, true, true, "zipit-mapping.txt",
                        "abcdefghijklmnopqrstuvwxyz", 1))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals(runJar(input), runJar(output));
        assertTrue(ZipitVerifier.verify(output).isEmpty());
        assertTrue(result.stats().snapshot().get("classes.renamed").longValue() > 0);

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertTrue(zip.getEntry("vm/VmMain.class") == null,
                    "repackage should move the main class out of its original package");
            boolean moved = false;
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(".class") && name.indexOf('/') > 0
                        && !name.startsWith("vm/") && !name.startsWith("zipit/")) {
                    moved = true;
                }
            }
            assertTrue(moved, "expected classes under generated package names");
        }
    }

    @Test
    void flatteningExcludeSkipsClassesAndPreservesSemantics() throws Exception {
        Path input = SampleJarBuilder.buildFlatJar(temp);
        Path output = temp.resolve("flat-exclude-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(61)
                .flow(new ZipitConfig.FlowOptions(true, "light", 8000, true,
                        java.util.Collections.singletonList("flat.FlatMain")))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("2\n45\n", runJar(output));
        assertEquals(0L, result.stats().snapshot().get("flow.flattened.methods").longValue(),
                result.stats().snapshot().toString());
        assertTrue(ZipitVerifier.verify(output).isEmpty());
    }

    @Test
    void rewritesReflectionClassStringsInsteadOfKeeping() throws Exception {
        Path input = SampleJarBuilder.buildReflectionStringJar(temp);
        Path output = temp.resolve("reflection-string-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(62)
                .reflection(new ZipitConfig.ReflectionOptions(true, true))
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("rs-ok\n", runJar(output));
        assertTrue(ZipitVerifier.verify(output).isEmpty());
        MappingSet mapping = MappingReader.read(temp.resolve("zipit-mapping.txt"));
        assertTrue(mapping.classes().containsKey("rs/Target"));
        assertFalse("rs/Target".equals(mapping.classes().get("rs/Target")));
    }

    @Test
    void repackageKeptClassesPreservesBytecodeAndSemantics() throws Exception {
        Path input = SampleJarBuilder.buildReferenceCallJar(temp);
        Path output = temp.resolve("repackage-kept-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(63)
                .keep(java.util.Collections.singletonList("ref.**"))
                .renamer(new ZipitConfig.RenamerOptions(true, "", true, "*",
                        true, true, true, "zipit-mapping.txt",
                        "abcdefghijklmnopqrstuvwxyz", 1,
                        java.util.Collections.singletonList("ref.**")))
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("42\n", runJar(output));
        assertTrue(ZipitVerifier.verify(output).isEmpty());
        boolean moved = false;
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertTrue(zip.getEntry("ref/RefMain.class") == null, "kept class package should move");
            assertTrue(zip.getEntry("ref/MathUtil.class") == null, "kept class package should move");
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith("/RefMain.class") && !name.startsWith("ref/")) {
                    moved = true;
                }
            }
        }
        assertTrue(moved, "expected the kept main class under a generated package");
    }

    @Test
    void obfuscatesAssetPathsAndRewritesReferences() throws Exception {
        Path input = SampleJarBuilder.buildAssetJar(temp);
        Path output = temp.resolve("asset-path-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(64)
                .resources(new ZipitConfig.ResourceOptions(false, true,
                        java.util.Collections.<String>emptyList()))
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("asset-ok\n", runJar(output));
        assertTrue(ZipitVerifier.verify(output).isEmpty());
        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertTrue(zip.getEntry("assets/demo/data.txt") == null,
                    "asset directory should be renamed");
            boolean found = false;
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.startsWith("assets/") && name.endsWith("/data.txt")
                        && !name.equals("assets/demo/data.txt")) {
                    found = true;
                }
            }
            assertTrue(found, "expected the asset under a generated domain");
        }
    }

    @Test
    void referenceObfuscationHidesStaticCalls() throws Exception {
        Path input = SampleJarBuilder.buildReferenceCallJar(temp);
        Path output = temp.resolve("refcall-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(21)
                .referenceObfuscation(new ZipitConfig.ReferenceOptions(true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("42\n", runJar(output));
        assertTrue(result.stats().snapshot().get("references.indy").longValue() >= 1);
        assertTrue(ZipitVerifier.verify(output).isEmpty());

        boolean found = false;
        for (ClassNode node : readClasses(output).values()) {
            for (MethodNode method : node.methods) {
                for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst();
                     insn != null; insn = insn.getNext()) {
                    if (insn instanceof org.objectweb.asm.tree.InvokeDynamicInsnNode) {
                        found = true;
                    }
                }
            }
        }
        assertTrue(found, "expected an invokedynamic call site in the output");
    }

    @Test
    void referenceObfuscationHidesVirtualCallsAndFields() throws Exception {
        Path input = SampleJarBuilder.buildReferenceMembersJar(temp);
        Path output = temp.resolve("refmembers-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(22)
                .referenceObfuscation(new ZipitConfig.ReferenceOptions(true, true, true, true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("42\n", runJar(output));
        assertTrue(result.stats().snapshot().get("references.virtual").longValue() >= 1,
                result.stats().snapshot().toString());
        assertTrue(result.stats().snapshot().get("references.fields").longValue() >= 4,
                result.stats().snapshot().toString());
        assertTrue(ZipitVerifier.verify(output).isEmpty());
    }

    @Test
    void referenceObfuscationWrapsConstructors() throws Exception {
        Path input = SampleJarBuilder.buildConstructorJar(temp);
        Path output = temp.resolve("constructor-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(26)
                .referenceObfuscation(new ZipitConfig.ReferenceOptions(true, false, false, false, true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("42\n", runJar(output));
        assertTrue(result.stats().snapshot().get("references.constructors").longValue() >= 1,
                result.stats().snapshot().toString());
        assertTrue(ZipitVerifier.verify(output).isEmpty());
    }

    @Test
    void exceptionObfuscationPreservesSemantics() throws Exception {
        Path input = SampleJarBuilder.buildExceptionJar(temp);
        Path output = temp.resolve("exception-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(23)
                .exceptionObfuscation(new ZipitConfig.ExceptionOptions(true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertEquals("5\ncaught\n", runJar(output));
        assertTrue(result.stats().snapshot().get("exceptions.wrapped").longValue() >= 2,
                result.stats().snapshot().toString());
        assertTrue(ZipitVerifier.verify(output).isEmpty());
    }

    @Test
    void incrementalReusesUnchangedClasses() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path firstOut = temp.resolve("incremental-first.jar");
        Path metadata = temp.resolve("zipit-incremental.tsv");

        ZipitConfig first = new ZipitConfig.Builder()
                .input(input).output(firstOut).seed(30)
                .incremental(new ZipitConfig.IncrementalOptions(true, "", metadata.toString()))
                .build();
        new ZipitEngine(discardLog()).run(first, ProgressListener.NONE);
        assertTrue(Files.exists(metadata));

        Path secondOut = temp.resolve("incremental-second.jar");
        ZipitConfig second = first.toBuilder()
                .output(secondOut)
                .mapping(new ZipitConfig.MappingOptions(true, "zipit-mapping.txt",
                        temp.resolve("zipit-mapping.txt").toString()))
                .incremental(new ZipitConfig.IncrementalOptions(true,
                        firstOut.toAbsolutePath().toString(), metadata.toString()))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(second, ProgressListener.NONE);

        assertTrue(result.stats().snapshot().get("incremental.reused").longValue() >= 5,
                result.stats().snapshot().toString());
        assertEquals("ok\n", runJar(secondOut).contains("Hi zipit") ? "ok\n" : runJar(secondOut));
        assertArrayEquals(Files.readAllBytes(firstOut), Files.readAllBytes(secondOut));
    }

    @Test
    void antiDebugGuardAllowsNormalRuns() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("antidebug-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(25)
                .antiDebug(true)
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        assertTrue(result.stats().snapshot().get("protection.antiDebug").longValue() >= 1);
        assertEquals("ok\n", runJar(output).contains("Hi zipit") ? "ok\n" : runJar(output));
        assertTrue(ZipitVerifier.verify(output).isEmpty());
    }

    @Test
    void springPresetKeepsAnnotatedClasses() throws Exception {
        Path input = SampleJarBuilder.buildFrameworkJar(temp);
        Path output = temp.resolve("framework-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(24)
                .preset("spring-boot")
                .build();
        ZipitConfig applied = io.zipit.core.config.Presets.apply(config);
        new ZipitEngine(discardLog()).run(applied, ProgressListener.NONE);

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertNotNull(zip.getEntry("demo/UserService.class"));
        }
        assertEquals("pong\n", runJar(output));
        assertTrue(ZipitVerifier.verify(output).isEmpty());
    }

    @Test
    void softKeepPublicApiKeepsNamesButStillTransforms() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("soft-api.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(15)
                .softKeepPublicApi(true)
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        try (ZipFile zip = new ZipFile(output.toFile())) {
            assertNotNull(zip.getEntry("sample/App.class"));
        }
        assertTrue(result.stats().snapshot().get("strings.encrypted").longValue() > 0);
        assertEquals("ok\n", runJar(output).contains("Hi zipit") ? "ok\n" : runJar(output));
    }

    @Test
    void readsProGuardStyleMemberMappings() throws Exception {
        Path file = temp.resolve("proguard.txt");
        Files.write(file, ("com.example.Foo -> a.b:\n"
                + "    int count -> c\n"
                + "    12:34:void doWork(java.lang.String,int) -> d\n"
                + "    java.util.List items -> e\n").getBytes(StandardCharsets.UTF_8));
        MappingSet mapping = MappingReader.read(file);
        assertEquals("a/b", mapping.classes().get("com/example/Foo"));
        assertEquals("c", mapping.fields().get("com/example/Foo#count#I"));
        assertEquals("d", mapping.methods().get("com/example/Foo#doWork#(Ljava/lang/String;I)V"));
        assertEquals("e", mapping.fields().get("com/example/Foo#items#Ljava/util/List;"));
    }

    @Test
    void tracesWatermark() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("watermark-out.jar");
        ZipitConfig config = new ZipitConfig.Builder().input(input).output(output).seed(16).build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        List<String> marks = WatermarkTracer.trace(output);
        assertFalse(marks.isEmpty());
        boolean found = false;
        for (String mark : marks) {
            if (mark.contains("ZIPIT|zipit")) {
                found = true;
            }
        }
        assertTrue(found, marks.toString());
    }

    @Test
    void licenseConfigIsValidatedAndReported() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("licensed-out.jar");
        Path privateKey = temp.resolve("private.pem");
        Path publicKey = temp.resolve("public.pem");
        KeyPair pair = keyPair();
        Files.write(privateKey, LicenseTool.pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.write(publicKey, LicenseTool.pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        Path unsigned = temp.resolve("license.yml");
        Files.write(unsigned, ("licensee: \"Acme\"\n"
                + "edition: \"enterprise\"\n"
                + "expires: \"2030-12-31\"\n"
                + "features:\n"
                + "  - audit\n").getBytes(StandardCharsets.UTF_8));
        Path signed = temp.resolve("license-signed.yml");
        LicenseTool.sign(unsigned, privateKey, signed);

        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(17)
                .report(new ZipitConfig.ReportOptions(true,
                        temp.resolve("license-audit.html").toString(),
                        temp.resolve("license-audit.json").toString()))
                .license(new ZipitConfig.LicenseOptions(true, signed.toAbsolutePath().toString(),
                        publicKey.toAbsolutePath().toString(), true))
                .build();
        EngineResult result = new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);
        LicenseInfo license = result.license();
        assertNotNull(license);
        assertTrue(license.isValid());
        assertEquals("Acme", license.licensee());
        String json = new String(Files.readAllBytes(temp.resolve("license-audit.json")), StandardCharsets.UTF_8);
        assertTrue(json.contains("\"license\""));
        assertTrue(json.contains("\"Acme\""));

        String tampered = new String(Files.readAllBytes(signed), StandardCharsets.UTF_8)
                .replace("Acme", "Evil");
        Path bad = temp.resolve("license-bad.yml");
        Files.write(bad, tampered.getBytes(StandardCharsets.UTF_8));
        ZipitConfig badConfig = config.toBuilder()
                .output(temp.resolve("bad-out.jar"))
                .license(new ZipitConfig.LicenseOptions(true, bad.toAbsolutePath().toString(),
                        publicKey.toAbsolutePath().toString(), true))
                .build();
        assertThrows(LicenseException.class, () ->
                new ZipitEngine(discardLog()).run(badConfig, ProgressListener.NONE));
    }

    @Test
    void customDictionaryAndMinimumLength() throws Exception {
        Path input = SampleJarBuilder.build(temp);
        Path output = temp.resolve("dictionary-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(18)
                .renamer(new ZipitConfig.RenamerOptions(true, "", false, "",
                        true, true, true, "zipit-mapping.txt", "ab", 4))
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);
        MappingSet mapping = MappingReader.read(temp.resolve("zipit-mapping.txt"));
        int checked = 0;
        for (String mapped : mapping.classes().values()) {
            if (mapped.contains("$")) {
                continue;
            }
            String simple = mapped.substring(mapped.lastIndexOf('/') + 1);
            assertTrue(simple.matches("[ab][ab0-9]{3,}"), simple);
            checked++;
        }
        assertTrue(checked > 0);
    }

    @Test
    void rewritesClassNamesInTextResources() throws Exception {
        Path input = SampleJarBuilder.buildResourceJar(temp);
        Path output = temp.resolve("resource-out.jar");
        ZipitConfig config = new ZipitConfig.Builder()
                .input(input).output(output).seed(19)
                .resources(new ZipitConfig.ResourceOptions(true))
                .build();
        new ZipitEngine(discardLog()).run(config, ProgressListener.NONE);

        String content = readEntry(output, "config/app.xml");
        assertFalse(content.contains("res.ResMain"), content);
        assertTrue(content.contains("res."), content);
        assertEquals("res-ok\n", runJar(output));
    }

    private static String readEntry(Path jar, String name) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(name);
            assertNotNull(entry, name);
            return new String(readAll(zip.getInputStream(entry)), StandardCharsets.UTF_8);
        }
    }

    private static Map<String, ClassNode> readClasses(Path jar) throws Exception {
        Map<String, ClassNode> classes = new LinkedHashMap<String, ClassNode>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")
                        || entry.getName().startsWith("META-INF/versions/")) {
                    continue;
                }
                ClassNode node = new ClassNode();
                new ClassReader(readAll(zip.getInputStream(entry))).accept(node, 0);
                classes.put(node.name, node);
            }
        }
        return classes;
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
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Timed out running " + jar);
        }
        String text = new String(out.toByteArray(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        assertEquals(0, process.exitValue(), text);
        return text;
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

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
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
