package io.zipit.core.engine;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads every class from an output JAR without initializing it, forcing the
 * JVM to link (and therefore verify) the bytecode.
 */
public final class ZipitVerifier {

    private ZipitVerifier() {
    }

    /**
     * @param jar output JAR
     * @return human-readable verification failures; empty when all classes link
     * @throws IOException if the JAR cannot be read
     */
    public static List<String> verify(Path jar) throws IOException {
        return verify(jar, java.util.Collections.<Path>emptyList());
    }

    /**
     * @param jar       output JAR
     * @param libraries library JARs/directories added to the verification class path
     * @return human-readable verification failures; empty when all classes link
     * @throws IOException if the JAR cannot be read
     */
    public static List<String> verify(Path jar, List<Path> libraries) throws IOException {
        List<String> errors = new ArrayList<String>();
        Set<String> classNames = new LinkedHashSet<String>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.endsWith(".class") || entry.isDirectory()) {
                    continue;
                }
                if (name.startsWith("META-INF/versions/")) {
                    continue;
                }
                if (name.equals("module-info.class") || name.endsWith("/module-info.class")) {
                    continue;
                }
                classNames.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
            }
        }

        List<URL> urls = new ArrayList<URL>();
        urls.add(jar.toUri().toURL());
        if (libraries != null) {
            for (Path library : libraries) {
                if (library != null && java.nio.file.Files.exists(library)) {
                    urls.add(library.toUri().toURL());
                }
            }
        }
        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                ZipitVerifier.class.getClassLoader());
        try {
            for (String className : classNames) {
                try {
                    Class<?> type = Class.forName(className, false, loader);
                    type.getDeclaredMethods();
                    type.getDeclaredFields();
                } catch (Throwable t) {
                    errors.add(className + ": " + t.getClass().getName() + ": " + t.getMessage());
                }
            }
        } finally {
            loader.close();
        }
        return errors;
    }
}
