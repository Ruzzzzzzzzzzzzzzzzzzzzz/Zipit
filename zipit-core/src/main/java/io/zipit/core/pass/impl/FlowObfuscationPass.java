package io.zipit.core.pass.impl;

import io.zipit.core.api.Pass;
import io.zipit.core.api.PassContext;
import io.zipit.core.util.AsmUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Set;

/**
 * Lightweight control-flow obfuscation.
 *
 * <p>Each transformed method starts with an opaque predicate backed by a
 * per-class synthetic field. The predicate always jumps over an unreachable
 * junk block. The transformation preserves stack shape and leaves exception
 * ranges untouched.</p>
 */
public final class FlowObfuscationPass implements Pass {

    private static final String ANNOTATION = "Lio/zipit/annotations/FlowObfuscation;";

    @Override
    public String name() {
        return "flow-obfuscation";
    }

    @Override
    public int order() {
        return 90;
    }

    @Override
    public boolean enabled(PassContext context) {
        return context.config().flow().enabled();
    }

    @Override
    public void run(PassContext context) {
        int methods = 0;
        int classes = 0;
        int branches = 0;
        int maxInstructions = context.config().flow().maxInstructions();
        String level = context.config().flow().level().toLowerCase(java.util.Locale.ROOT);
        boolean normal = "normal".equals(level) || "aggressive".equals(level);
        boolean aggressive = "aggressive".equals(level);
        for (ClassNode node : context.classes().classes().values()) {
            if (context.classes().isGenerated(node.name) || context.classes().isFrozen(node.name)) {
                continue;
            }
            if (!context.rules().transformClass(node)) {
                continue;
            }
            if ((node.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION)) != 0
                    || "module-info".equals(node.name)) {
                continue;
            }
            Boolean classEnabled = AsmUtil.annotationBoolean(node.invisibleAnnotations, ANNOTATION, "enabled");
            if (classEnabled == null) {
                classEnabled = AsmUtil.annotationBoolean(node.visibleAnnotations, ANNOTATION, "enabled");
            }
            if (Boolean.FALSE.equals(classEnabled)) {
                continue;
            }

            String fieldName = uniqueFieldName(node);
            int expected = context.random().nextInt();
            if (expected == 0) {
                expected = 0x51ED;
            }

            int transformed = 0;
            for (MethodNode method : node.methods) {
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    continue;
                }
                if ("<init>".equals(method.name) || "<clinit>".equals(method.name)) {
                    continue;
                }
                if (method.instructions == null || method.instructions.size() == 0) {
                    continue;
                }
                if (method.instructions.size() > maxInstructions || containsJsrOrRet(method)) {
                    continue;
                }
                Boolean methodEnabled = AsmUtil.annotationBoolean(method.invisibleAnnotations, ANNOTATION, "enabled");
                if (methodEnabled == null) {
                    methodEnabled = AsmUtil.annotationBoolean(method.visibleAnnotations, ANNOTATION, "enabled");
                }
                if (Boolean.FALSE.equals(methodEnabled)) {
                    continue;
                }
                insertPredicate(node, method, fieldName, expected);
                if (aggressive) {
                    insertPredicate(node, method, fieldName, expected);
                }
                if (normal && method.instructions.size() < maxInstructions * 3 / 4) {
                    branches += invertBranches(method, aggressive);
                }
                transformed++;
            }

            if (transformed > 0) {
                addFieldInit(node, fieldName, expected);
                methods += transformed;
                classes++;
            }
        }
        context.stats().add("flow.methods", methods);
        context.stats().add("flow.classes", classes);
        context.stats().add("flow.branches.inverted", branches);
        context.log().info("flow-obfuscation[%s]: %d methods across %d classes, %d branches inverted",
                level, methods, classes, branches);
    }

    private static void insertPredicate(ClassNode owner, MethodNode method, String fieldName, int expected) {
        LabelNode real = new LabelNode();
        InsnList prelude = new InsnList();
        prelude.add(new FieldInsnNode(Opcodes.GETSTATIC, owner.name, fieldName, "I"));
        AsmUtil.pushInt(prelude, expected);
        prelude.add(new JumpInsnNode(Opcodes.IF_ICMPEQ, real));
        // Junk path: only reachable if the (constant) field value differs.
        AsmUtil.pushInt(prelude, expected ^ 0x2F);
        prelude.add(new InsnNode(Opcodes.POP));
        prelude.add(new JumpInsnNode(Opcodes.GOTO, real));
        prelude.add(real);
        method.instructions.insert(prelude);
        // The predicate pushes two values (field + constant) on the stack; keep
        // maxStack consistent for passes that analyze before frames are computed.
        method.maxStack = Math.max(method.maxStack, 2);
    }

    private static int invertBranches(MethodNode method, boolean all) {
        int count = 0;
        int seen = 0;
        for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            org.objectweb.asm.tree.AbstractInsnNode next = insn.getNext();
            if (insn instanceof JumpInsnNode) {
                int inverse = inverseOpcode(insn.getOpcode());
                if (inverse != -1 && (all || (seen++ % 3 == 0))) {
                    JumpInsnNode jump = (JumpInsnNode) insn;
                    LabelNode skip = new LabelNode();
                    InsnList replacement = new InsnList();
                    replacement.add(new JumpInsnNode(inverse, skip));
                    replacement.add(new JumpInsnNode(Opcodes.GOTO, jump.label));
                    replacement.add(skip);
                    method.instructions.insertBefore(insn, replacement);
                    method.instructions.remove(insn);
                    count++;
                }
            }
            insn = next;
        }
        return count;
    }

    private static int inverseOpcode(int opcode) {
        switch (opcode) {
            case Opcodes.IFEQ: return Opcodes.IFNE;
            case Opcodes.IFNE: return Opcodes.IFEQ;
            case Opcodes.IFLT: return Opcodes.IFGE;
            case Opcodes.IFGE: return Opcodes.IFLT;
            case Opcodes.IFGT: return Opcodes.IFLE;
            case Opcodes.IFLE: return Opcodes.IFGT;
            case Opcodes.IF_ICMPEQ: return Opcodes.IF_ICMPNE;
            case Opcodes.IF_ICMPNE: return Opcodes.IF_ICMPEQ;
            case Opcodes.IF_ICMPLT: return Opcodes.IF_ICMPGE;
            case Opcodes.IF_ICMPGE: return Opcodes.IF_ICMPLT;
            case Opcodes.IF_ICMPGT: return Opcodes.IF_ICMPLE;
            case Opcodes.IF_ICMPLE: return Opcodes.IF_ICMPGT;
            case Opcodes.IF_ACMPEQ: return Opcodes.IF_ACMPNE;
            case Opcodes.IF_ACMPNE: return Opcodes.IF_ACMPEQ;
            case Opcodes.IFNULL: return Opcodes.IFNONNULL;
            case Opcodes.IFNONNULL: return Opcodes.IFNULL;
            default: return -1;
        }
    }

    private static void addFieldInit(ClassNode node, String fieldName, int expected) {
        FieldNode field = new FieldNode(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL | Opcodes.ACC_SYNTHETIC,
                fieldName, "I", null, null);
        node.fields.add(field);

        MethodNode clinit = null;
        for (MethodNode method : node.methods) {
            if ("<clinit>".equals(method.name) && "()V".equals(method.desc)) {
                clinit = method;
                break;
            }
        }
        if (clinit == null) {
            clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions.add(new InsnNode(Opcodes.RETURN));
            clinit.maxLocals = 0;
            clinit.maxStack = 1;
            node.methods.add(clinit);
        }
        InsnList init = new InsnList();
        AsmUtil.pushInt(init, expected);
        init.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, fieldName, "I"));
        clinit.instructions.insert(init);
        clinit.maxStack = Math.max(clinit.maxStack, 1);
    }

    private static boolean containsJsrOrRet(MethodNode method) {
        for (org.objectweb.asm.tree.AbstractInsnNode insn = method.instructions.getFirst();
             insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode == Opcodes.JSR || opcode == Opcodes.RET) {
                return true;
            }
        }
        return false;
    }

    private static String uniqueFieldName(ClassNode node) {
        Set<String> names = new HashSet<String>();
        for (FieldNode field : node.fields) {
            names.add(field.name);
        }
        int counter = 0;
        String candidate;
        do {
            candidate = "z$f" + Integer.toHexString(counter++);
        } while (names.contains(candidate));
        return candidate;
    }
}
