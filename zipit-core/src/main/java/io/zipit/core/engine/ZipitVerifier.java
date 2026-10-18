package io.zipit.core.engine;

import org.objectweb.asm.ClassReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Loads every class from an output JAR without initializing it, forcing the
 * JVM to link (and therefore verify) the bytecode.
 *
 * <p>Class names are derived from the class file itself rather than from the
 * ZIP entry path, so fat-jar layouts such as Spring Boot
 * {@code BOOT-INF/classes/} or WAR {@code WEB-INF/classes/} are verified under
 * their real binary names. Their bytes are registered with a child-first
 * loader because a plain {@link URLClassLoader} only looks at the JAR root.</p>
 */
public final class ZipitVerifier {

    /** Directory prefixes whose contents are still classes on the runtime classpath. */
    private static final String[] CLASS_ROOT_PREFIXES = {
            "BOOT-INF/classes/", "WEB-INF/classes/"
    };

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
        Map<String, byte[]> prefixedClasses = new LinkedHashMap<String, byte[]>();
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
                byte[] bytes = readAll(zip.getInputStream(entry));
                String className = className(bytes, name);
                if (className == null) {
                    continue;
                }
                classNames.add(className);
                String expectedEntry = className.replace('.', '/') + ".class";
                if (!expectedEntry.equals(name)) {
                    // Stored under BOOT-INF/classes/ or a similar prefix: keep the
                    // bytes so the loader below can still define the class.
                    prefixedClasses.put(className, bytes);
                }
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
        final Map<String, byte[]> extra = prefixedClasses;
        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[0]),
                ZipitVerifier.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String className) throws ClassNotFoundException {
                byte[] bytes = extra.get(className);
                if (bytes != null) {
                    return defineClass(className, bytes, 0, bytes.length);
                }
                return super.findClass(className);
            }
        };
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

    /** Reads the real binary name (dotted) from the class file bytes. */
    private static String className(byte[] bytes, String entryName) {
        try {
            return new ClassReader(bytes).getClassName().replace('/', '.');
        } catch (RuntimeException malformed) {
            // Still report unparseable classes under a best-effort name.
            String stem = entryName.substring(0, entryName.length() - ".class".length());
            for (String prefix : CLASS_ROOT_PREFIXES) {
                if (stem.startsWith(prefix)) {
                    stem = stem.substring(prefix.length());
                    break;
                }
            }
            return stem.replace('/', '.');
        }
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
