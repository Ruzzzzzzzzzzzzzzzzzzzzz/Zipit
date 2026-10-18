package io.zipit.core.pass.impl;

import io.zipit.core.api.Pass;
import io.zipit.core.api.PassContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;

/**
 * Lightweight exception obfuscation.
 *
 * <p>Wraps methods in a synthetic catch-all handler that rethrows the original
 * throwable. The handler is appended at the end of the exception table, so
 * existing handlers (including finally) keep precedence and semantics are
 * preserved.</p>
 */
public final class ExceptionObfuscationPass implements Pass {

    @Override
    public String name() {
        return "exception-obfuscation";
    }

    @Override
    public int order() {
        return 95;
    }

    @Override
    public boolean enabled(PassContext context) {
        return context.config().exceptionObfuscation().enabled();
    }

    @Override
    public void run(PassContext context) {
        int wrapped = 0;
        int max = context.config().flow().maxInstructions();
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
            for (MethodNode method : node.methods) {
                if (!eligible(context, method, max)) {
                    continue;
                }
                wrap(method);
                wrapped++;
            }
        }
        context.stats().add("exceptions.wrapped", wrapped);
        context.log().info("exception-obfuscation: added synthetic catch-all handlers to %d methods", wrapped);
    }

    private static boolean eligible(PassContext context, MethodNode method, int max) {
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return false;
        }
        if (method.name.startsWith("<")) {
            return false;
        }
        if (method.instructions == null || method.instructions.size() == 0
                || method.instructions.size() > max) {
            return false;
        }
        if (context.rules().hasKeepAnnotation(method)) {
            return false;
        }
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode == Opcodes.JSR || opcode == Opcodes.RET) {
                return false;
            }
        }
        return true;
    }

    private static void wrap(MethodNode method) {
        LabelNode start = new LabelNode();
        LabelNode end = new LabelNode();
        LabelNode handler = new LabelNode();
        method.instructions.insert(start);
        method.instructions.add(end);
        method.instructions.add(handler);

        int exceptionLocal = Math.max(method.maxLocals, argumentSlots(method));
        method.maxLocals = exceptionLocal + 1;
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, exceptionLocal));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, exceptionLocal));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));

        if (method.tryCatchBlocks == null) {
            method.tryCatchBlocks = new ArrayList<TryCatchBlockNode>();
        }
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
    }

    private static int argumentSlots(MethodNode method) {
        int slots = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type type : Type.getArgumentTypes(method.desc)) {
            slots += type.getSize();
        }
        return slots;
    }
}
