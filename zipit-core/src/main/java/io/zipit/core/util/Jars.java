package io.zipit.core.util;

import io.zipit.core.model.ClassPool;
import io.zipit.core.model.Hierarchy;
import io.zipit.core.model.ZipitClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** JAR input/output helpers. */
public final class Jars {

    private static final long FIXED_TIME = 315532800000L; // 1980-01-01, minimum valid ZIP time

    private Jars() {
    }

    /** Reads a JAR into the pool. Unparseable classes are preserved as resources. */
    public static void read(Path input, ClassPool pool, Log log) throws IOException {
        try (ZipFile zip = new ZipFile(input.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                byte[] bytes = readAll(zip.getInputStream(entry));
                String name = entry.getName();
                if (name.endsWith(".class")) {
                    try {
                        ClassReader reader = new ClassReader(bytes);
                        ClassNode node = new ClassNode();
                        reader.accept(node, 0);
                        if (pool.getClass(node.name) != null) {
                            // Duplicate internal name (multi-release or embedded template):
                            // keep the extra bytes untouched and freeze the class.
                            pool.putResource(name, bytes, entry.getTime(), entry.getMethod());
                            pool.freeze(node.name);
                            log.warn("Duplicate class name %s at %s preserved as resource; class frozen",
                                    node.name, name);
                        } else {
                            pool.putClass(name, bytes, node);
                            pool.setClassTime(node.name, entry.getTime());
                        }
                        continue;
                    } catch (RuntimeException e) {
                        log.warn("Unparseable class entry %s preserved as resource: %s", name, e.getMessage());
                    }
                }
                pool.putResource(name, bytes, entry.getTime(), entry.getMethod());
                if ("META-INF/MANIFEST.MF".equalsIgnoreCase(name)) {
                    String main = parseMainClass(bytes);
                    if (main != null) {
                        pool.setMainClass(main.replace('.', '/'));
                    }
                }
            }
        }
    }

    /** Writes the pool to a JAR, recomputing frames through the hierarchy. */
    public static void write(Path output, ClassPool pool, Hierarchy hierarchy,
                             boolean preserveTimestamps, Log log) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(output))) {
            java.util.Set<String> directories = new java.util.HashSet<String>();
            List<String> classNames = new ArrayList<String>(pool.classes().keySet());
            Collections.sort(classNames);
            for (String className : classNames) {
                ClassNode node = pool.getClass(className);
                if (node == null) {
                    continue;
                }
                String entryName = pool.entryPrefix(className) + node.name + ".class";
                byte[] bytes = pool.rawOutput(className);
                if (bytes == null) {
                    try {
                        bytes = ZipitClassWriter.toBytes(node, hierarchy);
                    } catch (RuntimeException e) {
                        // Writing the original bytes of a renamed class would produce
                        // a jar whose references no longer match. Fail loudly instead.
                        throw new IOException("Failed to serialize " + className + ": " + e.getMessage(), e);
                    }
                }
                long classTime = preserveTimestamps ? pool.classTime(className) : FIXED_TIME;
                classTime = classTime <= 0L ? FIXED_TIME : classTime;
                ensureDirectories(zos, entryName, directories, classTime);
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(classTime);
                zos.putNextEntry(entry);
                zos.write(bytes);
                zos.closeEntry();
            }

            List<String> resourceNames = new ArrayList<String>(pool.resources().keySet());
            Collections.sort(resourceNames);
            // Write the manifest first for tooling compatibility.
            if (resourceNames.remove("META-INF/MANIFEST.MF")) {
                resourceNames.add(0, "META-INF/MANIFEST.MF");
            }
            for (String resourceName : resourceNames) {
                ClassPool.Resource resource = pool.resource(resourceName);
                if (resource == null) {
                    continue;
                }
                long resourceTime = preserveTimestamps ? resource.time() : FIXED_TIME;
                resourceTime = resourceTime <= 0L ? FIXED_TIME : resourceTime;
                ensureDirectories(zos, resourceName, directories, resourceTime);
                ZipEntry entry = new ZipEntry(resourceName);
                entry.setTime(resourceTime);
                zos.putNextEntry(entry);
                zos.write(resource.bytes());
                zos.closeEntry();
            }
        }
    }

    private static void ensureDirectories(ZipOutputStream zos, String entryName,
                                          java.util.Set<String> directories, long time) throws IOException {
        int slash = entryName.lastIndexOf('/');
        while (slash > 0) {
            String directory = entryName.substring(0, slash + 1);
            if (directories.add(directory)) {
                ZipEntry entry = new ZipEntry(directory);
                entry.setTime(time);
                zos.putNextEntry(entry);
                zos.closeEntry();
            }
            slash = entryName.lastIndexOf('/', slash - 1);
        }
    }

    /** Parses the {@code Main-Class} manifest attribute (continuation lines included). */
    public static String parseMainClass(byte[] manifest) {
        Map<String, String> attributes = parseManifest(manifest);
        return attributes.get("Main-Class");
    }

    /** Parses a manifest into case-sensitive attribute names. */
    public static Map<String, String> parseManifest(byte[] manifest) {
        Map<String, String> attributes = new LinkedHashMap<String, String>();
        String text = new String(manifest, StandardCharsets.UTF_8);
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        String currentKey = null;
        StringBuilder currentValue = new StringBuilder();
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            if (line.charAt(0) == ' ') {
                if (currentKey != null) {
                    currentValue.append(line.substring(1));
                }
                continue;
            }
            if (currentKey != null) {
                attributes.put(currentKey, currentValue.toString());
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                currentKey = null;
                currentValue.setLength(0);
                continue;
            }
            currentKey = line.substring(0, colon).trim();
            currentValue.setLength(0);
            currentValue.append(line.substring(colon + 1).trim());
        }
        if (currentKey != null) {
            attributes.put(currentKey, currentValue.toString());
        }
        return attributes;
    }

    /** Serializes manifest attributes with proper 72-byte line wrapping. */
    public static byte[] writeManifest(Map<String, String> attributes) {
        java.util.jar.Manifest manifest = new java.util.jar.Manifest();
        if (!attributes.containsKey("Manifest-Version")) {
            manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        }
        for (Map.Entry<String, String> e : attributes.entrySet()) {
            manifest.getMainAttributes().putValue(e.getKey(), e.getValue());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            manifest.write(out);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to serialize manifest", e);
        }
        return out.toByteArray();
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
}
