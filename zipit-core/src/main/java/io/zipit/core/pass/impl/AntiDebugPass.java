package io.zipit.core.pass.impl;

import io.zipit.core.api.Pass;
import io.zipit.core.api.PassContext;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Basic anti-debug guard.
 *
 * <p>Generates a guard class and calls it at the start of every {@code main}
 * method. The guard inspects the JVM input arguments for common debug agent
 * flags. This is a deterrent, not a security boundary; it is opt-in and does
 * not detect debuggers attached after startup.</p>
 */
public final class AntiDebugPass implements Pass {

    @Override
    public String name() {
        return "anti-debug";
    }

    @Override
    public int order() {
        return 115;
    }

    @Override
    public boolean enabled(PassContext context) {
        return context.config().antiDebug();
    }

    @Override
    public void run(PassContext context) {
        String guard = "zipit/runtime/D" + Integer.toHexString(context.random().nextInt());
        while (context.classes().getClass(guard) != null) {
            guard = "zipit/runtime/D" + Integer.toHexString(context.random().nextInt());
        }
        int instrumented = 0;
        for (ClassNode node : context.classes().classes().values()) {
            if (context.classes().isGenerated(node.name) || context.classes().isFrozen(node.name)) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if (!"main".equals(method.name) || !"([Ljava/lang/String;)V".equals(method.desc)) {
                    continue;
                }
                // Abstract/native methods have no Code attribute; inserting
                // instructions would produce an unloadable class.
                if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    continue;
                }
                method.instructions.insert(new MethodInsnNode(Opcodes.INVOKESTATIC,
                        guard, "check", "()V", false));
                instrumented++;
            }
        }
        if (instrumented == 0) {
            context.log().warn("anti-debug: no main(String[]) method found; guard not referenced");
            return;
        }
        context.classes().addGenerated(createGuard(guard));
        context.stats().add("protection.antiDebug", instrumented);
        context.log().info("anti-debug: guard %s called from %d main methods", guard, instrumented);
    }

    private static ClassNode createGuard(String name) {
        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER;
        node.name = name;
        node.superName = "java/lang/Object";
        node.sourceFile = "ZipitGuard.java";

        MethodNode constructor = new MethodNode(Opcodes.ACC_PRIVATE, "<init>", "()V", null, null);
        constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        constructor.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false));
        constructor.instructions.add(new InsnNode(Opcodes.RETURN));
        constructor.maxLocals = 1;
        constructor.maxStack = 1;
        node.methods.add(constructor);

        MethodNode check = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "check", "()V", null, null);
        LabelNode detected = new LabelNode();
        LabelNode end = new LabelNode();
        check.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
                "java/lang/management/ManagementFactory", "getRuntimeMXBean",
                "()Ljava/lang/management/RuntimeMXBean;", false));
        check.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/lang/management/RuntimeMXBean", "getInputArguments",
                "()Ljava/util/List;", true));
        check.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "java/util/List", "toString", "()Ljava/lang/String;", true));
        check.instructions.add(new VarInsnNode(Opcodes.ASTORE, 0));
        check.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        check.instructions.add(new LdcInsnNode("jdwp"));
        check.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false));
        check.instructions.add(new JumpInsnNode(Opcodes.IFNE, detected));
        check.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        check.instructions.add(new LdcInsnNode("Xdebug"));
        check.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                "java/lang/String", "contains", "(Ljava/lang/CharSequence;)Z", false));
        check.instructions.add(new JumpInsnNode(Opcodes.IFEQ, end));
        check.instructions.add(detected);
        check.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW,
                "java/lang/IllegalStateException"));
        check.instructions.add(new InsnNode(Opcodes.DUP));
        check.instructions.add(new LdcInsnNode("Debugger detected"));
        check.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,
                "java/lang/IllegalStateException", "<init>", "(Ljava/lang/String;)V", false));
        check.instructions.add(new InsnNode(Opcodes.ATHROW));
        check.instructions.add(end);
        check.instructions.add(new InsnNode(Opcodes.RETURN));
        check.maxLocals = 1;
        check.maxStack = 3;
        node.methods.add(check);
        return node;
    }
}
