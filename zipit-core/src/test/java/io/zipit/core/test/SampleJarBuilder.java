package io.zipit.core.test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Compiles a self-contained sample program into a runnable JAR. */
public final class SampleJarBuilder {

    private SampleJarBuilder() {
    }

    public static Path build(Path workDir) throws IOException {
        Path src = workDir.resolve("sample-src");
        Path classes = workDir.resolve("sample-classes");
        Files.createDirectories(src.resolve("sample"));
        Files.createDirectories(classes);

        Path app = src.resolve("sample/App.java");
        Files.write(app, SOURCE.getBytes(StandardCharsets.UTF_8));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK is required to compile the sample");
        }
        int result = compiler.run(null, null, null,
                "-source", "8", "-target", "8", "-encoding", "UTF-8",
                "-d", classes.toString(), app.toString());
        if (result != 0) {
            throw new IllegalStateException("Sample compilation failed with exit code " + result);
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", "sample.App");

        Path jar = workDir.resolve("sample.jar");
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(out, manifest)) {
            addDirectory(jos, classes, classes);
        }
        return jar;
    }

    /** Builds a JAR from a map of source paths to source text. */
    public static Path buildSources(Path workDir, String name, String mainClass,
                                    java.util.Map<String, String> sources) throws IOException {
        return buildSources(workDir, name, mainClass, sources, java.util.Collections.<String, byte[]>emptyMap());
    }

    public static Path buildSources(Path workDir, String name, String mainClass,
                                    java.util.Map<String, String> sources,
                                    java.util.Map<String, byte[]> resources) throws IOException {
        Path src = workDir.resolve(name + "-src");
        Path classes = workDir.resolve(name + "-classes");
        Files.createDirectories(classes);
        java.util.List<String> files = new ArrayList<String>();
        for (java.util.Map.Entry<String, String> entry : sources.entrySet()) {
            Path file = src.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.write(file, entry.getValue().getBytes(StandardCharsets.UTF_8));
            files.add(file.toString());
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK is required to compile the sample");
        }
        java.util.List<String> args = new ArrayList<String>();
        args.add("-source");
        args.add("8");
        args.add("-target");
        args.add("8");
        args.add("-encoding");
        args.add("UTF-8");
        args.add("-d");
        args.add(classes.toString());
        args.addAll(files);
        int result = compiler.run(null, null, null, args.toArray(new String[0]));
        if (result != 0) {
            throw new IllegalStateException("Sample compilation failed with exit code " + result);
        }
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", mainClass);
        Path jar = workDir.resolve(name + ".jar");
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(out, manifest)) {
            addDirectory(jos, classes, classes);
            for (java.util.Map.Entry<String, byte[]> entry : resources.entrySet()) {
                jos.putNextEntry(new JarEntry(entry.getKey()));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
        }
        return jar;
    }

    public static Path buildFlatJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("flat/FlatMain.java",
                "package flat;\n" +
                "public class FlatMain {\n" +
                "    static int classify(int x) {\n" +
                "        if (x > 10) { return 1; }\n" +
                "        if (x > 0) { return 2; }\n" +
                "        return 3;\n" +
                "    }\n" +
                "    static int sum(int n) {\n" +
                "        int s = 0;\n" +
                "        for (int i = 0; i < n; i++) { s += i; }\n" +
                "        return s;\n" +
                "    }\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(classify(5));\n" +
                "        System.out.println(sum(10));\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "flat", "flat.FlatMain", sources);
    }

    public static Path buildVirtualizationJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("vm/VmMain.java",
                "package vm;\n" +
                "public class VmMain {\n" +
                "    static class Base { int value() { return 10; } }\n" +
                "    static class Child extends Base { int value() { return 20; } }\n" +
                "    static int calc(Base b) { return b.value() + 1; }\n" +
                "    static int sum(int[] values) {\n" +
                "        int total = 0;\n" +
                "        for (int i = 0; i < values.length; i++) { total += values[i]; }\n" +
                "        return total;\n" +
                "    }\n" +
                "    static int classify(int x) {\n" +
                "        switch (x) {\n" +
                "            case 1: return 11;\n" +
                "            case 2: return 22;\n" +
                "            case 3: return 33;\n" +
                "            default: return -1;\n" +
                "        }\n" +
                "    }\n" +
                "    static long mix(long a, int b) { return (a << 2) ^ b; }\n" +
                "    static double average(double a, double b) { return (a + b) / 2.0; }\n" +
                "    static String secret() { return \"vm-secret-value\"; }\n" +
                "    static int counter = 5;\n" +
                "    int instanceCounter = 7;\n" +
                "    static int bump(int x) { counter += x; return counter; }\n" +
                "    int bumpInstance(int x) { instanceCounter += x; return instanceCounter; }\n" +
                "    static int fill(int[] values) {\n" +
                "        int total = 0;\n" +
                "        for (int i = 0; i < values.length; i++) { values[i] = i * i; total += values[i]; }\n" +
                "        return total;\n" +
                "    }\n" +
                "    static char firstChar(String value) { return value.charAt(0); }\n" +
                "    static boolean even(int value) { return (value & 1) == 0; }\n" +
                "    static byte narrow(int value) { return (byte) value; }\n" +
                "    static short widen(short value) { return (short) (value + 1); }\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(calc(new Child()));\n" +
                "        System.out.println(sum(new int[]{1, 2, 3, 4}));\n" +
                "        System.out.println(classify(2));\n" +
                "        System.out.println(classify(9));\n" +
                "        System.out.println(mix(7L, 3));\n" +
                "        System.out.println(average(3.0, 4.0));\n" +
                "        System.out.println(secret());\n" +
                "        System.out.println(bump(3));\n" +
                "        System.out.println(new VmMain().bumpInstance(4));\n" +
                "        System.out.println(fill(new int[4]));\n" +
                "        System.out.println(firstChar(\"vm\"));\n" +
                "        System.out.println(even(8));\n" +
                "        System.out.println(narrow(300));\n" +
                "        System.out.println(widen((short) 41));\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "virtualization", "vm.VmMain", sources);
    }

    public static Path buildReflectionStringJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("rs/Target.java",
                "package rs;\n" +
                "public class Target {\n" +
                "    public static String hello() { return \"rs-ok\"; }\n" +
                "}\n");
        sources.put("rs/Main.java",
                "package rs;\n" +
                "import java.lang.reflect.Method;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) throws Exception {\n" +
                "        Class<?> type = Class.forName(\"rs.Target\");\n" +
                "        Method method = type.getMethod(\"hello\");\n" +
                "        System.out.println(method.invoke(null));\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "reflection-string", "rs.Main", sources);
    }

    public static Path buildAssetJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("asset/AssetMain.java",
                "package asset;\n" +
                "import java.io.BufferedReader;\n" +
                "import java.io.InputStream;\n" +
                "import java.io.InputStreamReader;\n" +
                "public class AssetMain {\n" +
                "    public static void main(String[] args) throws Exception {\n" +
                "        InputStream in = AssetMain.class.getResourceAsStream(\"/assets/demo/data.txt\");\n" +
                "        BufferedReader reader = new BufferedReader(new InputStreamReader(in, \"UTF-8\"));\n" +
                "        System.out.println(reader.readLine());\n" +
                "        reader.close();\n" +
                "    }\n" +
                "}\n");
        java.util.Map<String, byte[]> resources = new java.util.LinkedHashMap<String, byte[]>();
        resources.put("assets/demo/data.txt", "asset-ok\n".getBytes(StandardCharsets.UTF_8));
        return buildSources(workDir, "asset", "asset.AssetMain", sources, resources);
    }

    public static Path buildReferenceCallJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("ref/MathUtil.java",
                "package ref;\n" +
                "public class MathUtil {\n" +
                "    public static int add(int a, int b) { return a + b; }\n" +
                "}\n");
        sources.put("ref/RefMain.java",
                "package ref;\n" +
                "public class RefMain {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(MathUtil.add(20, 22));\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "refcall", "ref.RefMain", sources);
    }

    public static Path buildReferenceMembersJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("ref2/Model.java",
                "package ref2;\n" +
                "public class Model {\n" +
                "    public int value;\n" +
                "    public static int COUNT;\n" +
                "    public int add(int x) { return value + x; }\n" +
                "}\n");
        sources.put("ref2/Ref2Main.java",
                "package ref2;\n" +
                "public class Ref2Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Model m = new Model();\n" +
                "        m.value = 40;\n" +
                "        Model.COUNT = 2;\n" +
                "        System.out.println(m.add(Model.COUNT));\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "refmembers", "ref2.Ref2Main", sources);
    }

    public static Path buildConstructorJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("ctor/Model.java",
                "package ctor;\n" +
                "public class Model {\n" +
                "    private final int value;\n" +
                "    public Model(int value) { this.value = value; }\n" +
                "    public int value() { return value; }\n" +
                "}\n");
        sources.put("ctor/Main.java",
                "package ctor;\n" +
                "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        Model m = new Model(42);\n" +
                "        System.out.println(m.value());\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "constructor", "ctor.Main", sources);
    }

    public static Path buildExceptionJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("exc/ExcMain.java",
                "package exc;\n" +
                "public class ExcMain {\n" +
                "    static int divide(int a, int b) { return a / b; }\n" +
                "    public static void main(String[] args) {\n" +
                "        try {\n" +
                "            System.out.println(divide(10, 2));\n" +
                "            System.out.println(divide(1, 0));\n" +
                "        } catch (ArithmeticException e) {\n" +
                "            System.out.println(\"caught\");\n" +
                "        }\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "exception", "exc.ExcMain", sources);
    }

    public static Path buildFrameworkJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("org/springframework/stereotype/Service.java",
                "package org.springframework.stereotype;\n" +
                "import java.lang.annotation.*;\n" +
                "@Retention(RetentionPolicy.CLASS)\n" +
                "@Target(ElementType.TYPE)\n" +
                "public @interface Service {}\n");
        sources.put("demo/UserService.java",
                "package demo;\n" +
                "@org.springframework.stereotype.Service\n" +
                "public class UserService {\n" +
                "    public String ping() { return \"pong\"; }\n" +
                "}\n");
        sources.put("demo/FrameworkMain.java",
                "package demo;\n" +
                "public class FrameworkMain {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(new UserService().ping());\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "framework", "demo.FrameworkMain", sources);
    }

    public static Path buildResourceJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("res/ResMain.java",
                "package res;\n" +
                "public class ResMain {\n" +
                "    public static void main(String[] args) { System.out.println(\"res-ok\"); }\n" +
                "}\n");
        java.util.Map<String, byte[]> resources = new java.util.LinkedHashMap<String, byte[]>();
        resources.put("config/app.xml", ("<config class=\"res.ResMain\"/>\n").getBytes(StandardCharsets.UTF_8));
        return buildSources(workDir, "resource", "res.ResMain", sources, resources);
    }

    public static Path buildReflectionJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("reflect/ReflectTarget.java",
                "package reflect;\n" +
                "public class ReflectTarget {\n" +
                "    private static int hiddenField = 7;\n" +
                "    private static String secret() { return \"reflected-secret\"; }\n" +
                "}\n");
        sources.put("reflect/ReflectMain.java",
                "package reflect;\n" +
                "public class ReflectMain {\n" +
                "    public static void main(String[] args) throws Exception {\n" +
                "        Class<?> c = Class.forName(\"reflect.ReflectTarget\");\n" +
                "        java.lang.reflect.Method m = c.getDeclaredMethod(\"secret\");\n" +
                "        m.setAccessible(true);\n" +
                "        System.out.println(m.invoke(null));\n" +
                "        java.lang.reflect.Field f = c.getDeclaredField(\"hiddenField\");\n" +
                "        f.setAccessible(true);\n" +
                "        System.out.println(f.getInt(null));\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "reflection", "reflect.ReflectMain", sources);
    }

    public static Path buildTrimJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("trim/TrimMain.java",
                "package trim;\n" +
                "public class TrimMain {\n" +
                "    private static int unusedField;\n" +
                "    private int unusedInstance;\n" +
                "    private static void unusedHelper() { System.out.println(\"unused\"); }\n" +
                "    private static int usedHelper(int x) { return x + 1; }\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(usedHelper(41));\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "trim", "trim.TrimMain", sources);
    }

    public static Path buildParameterJar(Path workDir) throws IOException {
        java.util.Map<String, String> sources = new java.util.LinkedHashMap<String, String>();
        sources.put("param/ParamMain.java",
                "package param;\n" +
                "public class ParamMain {\n" +
                "    private static int add(int a, int b) { return a + b; }\n" +
                "    private int mul(int a) { return a * 3; }\n" +
                "    public static void main(String[] args) {\n" +
                "        ParamMain p = new ParamMain();\n" +
                "        System.out.println(add(20, 22));\n" +
                "        System.out.println(p.mul(14));\n" +
                "    }\n" +
                "}\n");
        return buildSources(workDir, "parameter", "param.ParamMain", sources);
    }

    /** Builds a JAR that exposes an implementation through {@code META-INF/services}. */
    public static Path buildServiceJar(Path workDir) throws IOException {
        Path src = workDir.resolve("service-src");
        Path classes = workDir.resolve("service-classes");
        Files.createDirectories(src.resolve("svc"));
        Files.createDirectories(classes);
        Files.write(src.resolve("svc/Service.java"), SERVICE_SOURCE.getBytes(StandardCharsets.UTF_8));
        Files.write(src.resolve("svc/Impl.java"), IMPL_SOURCE.getBytes(StandardCharsets.UTF_8));
        Files.write(src.resolve("svc/Main.java"), SERVICE_MAIN_SOURCE.getBytes(StandardCharsets.UTF_8));

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK is required to compile the sample");
        }
        int result = compiler.run(null, null, null,
                "-source", "8", "-target", "8", "-encoding", "UTF-8",
                "-d", classes.toString(),
                src.resolve("svc/Service.java").toString(),
                src.resolve("svc/Impl.java").toString(),
                src.resolve("svc/Main.java").toString());
        if (result != 0) {
            throw new IllegalStateException("Service sample compilation failed with exit code " + result);
        }

        Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Main-Class", "svc.Main");

        Path jar = workDir.resolve("service.jar");
        try (OutputStream out = Files.newOutputStream(jar);
             JarOutputStream jos = new JarOutputStream(out, manifest)) {
            addDirectory(jos, classes, classes);
            jos.putNextEntry(new JarEntry("META-INF/services/svc.Service"));
            jos.write("svc.Impl\n".getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return jar;
    }

    private static void addDirectory(JarOutputStream jos, Path root, Path dir) throws IOException {
        List<Path> files = new ArrayList<Path>();
        Files.walk(dir).filter(Files::isRegularFile).forEach(files::add);
        java.util.Collections.sort(files);
        for (Path file : files) {
            String name = root.relativize(file).toString().replace('\\', '/');
            jos.putNextEntry(new JarEntry(name));
            jos.write(Files.readAllBytes(file));
            jos.closeEntry();
        }
    }

    private static final String SERVICE_SOURCE =
            "package svc;\n" +
            "public interface Service { String name(); }\n";

    private static final String IMPL_SOURCE =
            "package svc;\n" +
            "public class Impl implements Service {\n" +
            "    public String name() { return \"impl-one\"; }\n" +
            "}\n";

    private static final String SERVICE_MAIN_SOURCE =
            "package svc;\n" +
            "import java.util.ServiceLoader;\n" +
            "public class Main {\n" +
            "    public static void main(String[] args) {\n" +
            "        for (Service service : ServiceLoader.load(Service.class)) {\n" +
            "            System.out.println(service.name());\n" +
            "        }\n" +
            "    }\n" +
            "}\n";

    private static final String SOURCE =
            "package sample;\n" +
            "\n" +
            "import java.io.Serializable;\n" +
            "import java.util.ArrayList;\n" +
            "import java.util.List;\n" +
            "\n" +
            "public class App implements Serializable {\n" +
            "    private static final String SECRET = \"hello-zipit-secret\";\n" +
            "    private int counter = 42;\n" +
            "    private long big = 1234567890123L;\n" +
            "\n" +
            "    interface Greeter { String greet(String name); }\n" +
            "\n" +
            "    static class Base { String who() { return \"base\"; } }\n" +
            "    static class Child extends Base { @Override String who() { return \"child\"; } }\n" +
            "\n" +
            "    enum Color { RED, GREEN }\n" +
            "\n" +
            "    public static void main(String[] args) {\n" +
            "        App app = new App();\n" +
            "        Greeter greeter = name -> \"Hi \" + name + \" / \" + SECRET;\n" +
            "        System.out.println(greeter.greet(\"zipit\"));\n" +
            "        System.out.println(app.counter + 7);\n" +
            "        System.out.println(app.big ^ 0x55);\n" +
            "        List<String> list = new ArrayList<String>();\n" +
            "        for (int i = 0; i < 3; i++) { list.add(\"item-\" + i); }\n" +
            "        System.out.println(list);\n" +
            "        System.out.println(new Child().who());\n" +
            "        System.out.println(Color.valueOf(\"GREEN\").ordinal());\n" +
            "        System.out.println(Color.RED.name());\n" +
            "        try {\n" +
            "            System.out.println(100 / (args.length + 1));\n" +
            "        } catch (ArithmeticException e) {\n" +
            "            System.out.println(\"caught\");\n" +
            "        }\n" +
            "        StringBuilder sb = new StringBuilder();\n" +
            "        for (char c : \"abcdef\".toCharArray()) { sb.append(c); }\n" +
            "        System.out.println(sb.reverse());\n" +
            "    }\n" +
            "}\n";
}
