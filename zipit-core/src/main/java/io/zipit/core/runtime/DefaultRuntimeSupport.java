package io.zipit.core.runtime;

import io.zipit.core.api.RuntimeSupport;
import io.zipit.core.model.ClassPool;
import io.zipit.core.util.NameGenerator;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Default runtime support: injected string decryptor, indy bootstrap and watermark. */
public final class DefaultRuntimeSupport implements RuntimeSupport {

    private String decryptorName;
    private String indyBootstrapName;
    private String virtualMachineName;

    @Override
    public synchronized String ensureStringDecryptor(ClassPool pool, long seed) {
        if (decryptorName == null) {
            decryptorName = injectTemplate(ZipitStringDecryptor.class, "zipit/runtime/", seed, pool);
        }
        return decryptorName;
    }

    @Override
    public synchronized String ensureIndyBootstrap(ClassPool pool, long seed) {
        if (indyBootstrapName == null) {
            indyBootstrapName = injectTemplate(ZipitIndyBootstrap.class, "zipit/runtime/I", seed, pool);
        }
        return indyBootstrapName;
    }

    @Override
    public synchronized String ensureVirtualMachine(ClassPool pool, long seed) {
        if (virtualMachineName == null) {
            virtualMachineName = injectTemplate(ZipitVM.class, "zipit/runtime/V", seed, pool);
        }
        return virtualMachineName;
    }

    @Override
    public String embedWatermark(ClassPool pool, String watermark) {
        List<String> names = new ArrayList<String>(pool.classes().keySet());
        Collections.sort(names);
        for (String name : names) {
            ClassNode node = pool.getClass(name);
            if (node == null || "module-info".equals(node.name)) {
                continue;
            }
            String fieldName = "zipit$" + Integer.toHexString(watermark.hashCode() & 0xFFFFFF);
            boolean exists = false;
            for (FieldNode field : node.fields) {
                if (field.name.equals(fieldName)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                FieldNode field = new FieldNode(
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                        fieldName, "Ljava/lang/String;", null, watermark);
                node.fields.add(0, field);
            }
            return node.name + "." + fieldName;
        }
        return null;
    }

    private static String injectTemplate(Class<?> template, String prefix, long seed, ClassPool pool) {
        try {
            byte[] bytes = readTemplate(template);
            ClassReader reader = new ClassReader(bytes);
            ClassNode node = new ClassNode();
            reader.accept(node, 0);

            Random random = new Random(seed ^ template.getName().hashCode());
            NameGenerator names = new NameGenerator(random, "");
            String candidate;
            do {
                candidate = prefix + names.random(10);
            } while (pool.getClass(candidate) != null);

            String originalName = node.name;
            // Remap self-references (for example the bootstrap helper calling
            // its own private decrypt method) as well as the class name.
            ClassNode remapped = new ClassNode();
            node.accept(new ClassRemapper(remapped, new SimpleRemapper(originalName, candidate)));
            remapped.access |= Opcodes.ACC_SYNTHETIC;
            pool.addGenerated(remapped);
            return candidate;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load runtime template " + template.getName(), e);
        }
    }

    private static byte[] readTemplate(Class<?> template) throws IOException {
        String resource = template.getSimpleName() + ".class";
        try (InputStream in = template.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException(resource + " resource is missing");
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
