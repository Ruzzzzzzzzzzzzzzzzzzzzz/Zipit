package io.zipit.core.pass.impl;

import io.zipit.core.api.Pass;
import io.zipit.core.api.PassContext;
import io.zipit.core.model.ClassPool;
import io.zipit.core.util.AsmUtil;
import io.zipit.core.util.Cipher;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Replaces string constants with encrypted payloads and a call to an injected
 * decryptor.
 *
 * <p>Each call site carries its own 64-bit key. The decryptor is a single
 * synthetic class shared by the whole output JAR. Strings appearing as
 * bootstrap arguments (for example invokedynamic string-concat recipes) are
 * left untouched.</p>
 */
public final class StringEncryptionPass implements Pass {

    private static final String ANNOTATION = "Lio/zipit/annotations/StringEncryption;";

    @Override
    public String name() {
        return "string-encryption";
    }

    @Override
    public int order() {
        return 70;
    }

    @Override
    public boolean enabled(PassContext context) {
        return context.config().stringEncryption().enabled();
    }

    @Override
    public void run(PassContext context) {
        ZipitConfigAccess cfg = new ZipitConfigAccess(context);
        String helper = context.runtime().ensureStringDecryptor(context.classes(), context.config().seed());
        int encrypted = 0;

        for (ClassNode node : new java.util.ArrayList<ClassNode>(context.classes().classes().values())) {
            ClassPool pool = context.classes();
            if (pool.isGenerated(node.name) || pool.isFrozen(node.name)) {
                continue;
            }
            if (!context.rules().transformClass(node)) {
                continue;
            }
            Boolean classEnabled = AsmUtil.annotationBoolean(node.invisibleAnnotations, ANNOTATION, "enabled");
            if (classEnabled == null) {
                classEnabled = AsmUtil.annotationBoolean(node.visibleAnnotations, ANNOTATION, "enabled");
            }
            if (Boolean.FALSE.equals(classEnabled)) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if (method.instructions == null || method.instructions.size() == 0) {
                    continue;
                }
                Boolean methodEnabled = AsmUtil.annotationBoolean(method.invisibleAnnotations, ANNOTATION, "enabled");
                if (methodEnabled == null) {
                    methodEnabled = AsmUtil.annotationBoolean(method.visibleAnnotations, ANNOTATION, "enabled");
                }
                if (Boolean.FALSE.equals(methodEnabled)) {
                    continue;
                }
                encrypted += encryptMethod(context, method, helper, cfg.minLength());
            }
        }
        context.stats().add("strings.encrypted", encrypted);
        context.log().info("string-encryption: encrypted %d string constants with helper %s", encrypted, helper);
    }

    private int encryptMethod(PassContext context, MethodNode method, String helper, int minLength) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (insn instanceof LdcInsnNode) {
                Object constant = ((LdcInsnNode) insn).cst;
                if (constant instanceof String) {
                    String value = (String) constant;
                    if (value.length() >= minLength) {
                        long key = context.random().nextLong();
                        if (key == 0L) {
                            key = 0x5A17L;
                        }
                        String payload = Cipher.encrypt(value, key);
                        InsnList replacement = new InsnList();
                        replacement.add(new LdcInsnNode(payload));
                        replacement.add(new LdcInsnNode(Long.valueOf(key)));
                        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, helper, "d",
                                "(Ljava/lang/String;J)Ljava/lang/String;", false));
                        method.instructions.insertBefore(insn, replacement);
                        method.instructions.remove(insn);
                        count++;
                    }
                }
            }
            insn = next;
        }
        return count;
    }

    /** Small adapter to keep the pass readable. */
    private static final class ZipitConfigAccess {
        private final PassContext context;

        ZipitConfigAccess(PassContext context) {
            this.context = context;
        }

        int minLength() {
            return context.config().stringEncryption().minLength();
        }
    }
}
