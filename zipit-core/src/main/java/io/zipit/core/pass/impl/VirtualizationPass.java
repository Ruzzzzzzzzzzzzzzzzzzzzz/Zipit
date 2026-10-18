package io.zipit.core.pass.impl;

import io.zipit.core.api.Pass;
import io.zipit.core.api.PassContext;
import io.zipit.core.util.AsmUtil;
import io.zipit.core.util.Cipher;
import io.zipit.core.util.NameGenerator;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Method virtualization.
 *
 * <p>Eligible method bodies are translated into a compact custom instruction
 * stream and replaced with a call to an injected stack machine. The payload
 * (code, constants and references) lives in a synthetic holder class, so each
 * virtualized method pays only the allocation of its local-variable array.
 * Constant strings are encrypted with the same per-call-site scheme used by the
 * string-encryption pass.</p>
 *
 * <p>This is a safe subset: constructors, exception handlers, {@code NEW},
 * monitors and {@code invokedynamic} are rejected. Reflection-based invocation
 * keeps normal virtual dispatch, and argument/return values are coerced at the
 * boundary.</p>
 */
public final class VirtualizationPass implements Pass {

    private static final String ANNOTATION = "Lio/zipit/annotations/MethodVirtualization;";
    private static final int MAX_LOCALS = 4096;
    private static final int MAX_PAYLOAD_SLOTS = 4000;
    private static final String VM_DESC =
            "([I[Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;";

    private String decryptor;
    private String virtualMachine;

    @Override
    public String name() {
        return "method-virtualization";
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public boolean enabled(PassContext context) {
        return context.config().methodVirtualization().enabled();
    }

    @Override
    public void run(PassContext context) {
        int maxInstructions = context.config().methodVirtualization().maxInstructions();
        int maxMethods = context.config().methodVirtualization().maxMethods();
        int methods = 0;
        int instructions = 0;
        int skipped = 0;

        List<ClassNode> snapshot = new ArrayList<ClassNode>(context.classes().classes().values());
        for (ClassNode node : snapshot) {
            if (context.classes().isGenerated(node.name) || context.classes().isFrozen(node.name)) {
                continue;
            }
            if ("module-info".equals(node.name)) {
                continue;
            }
            if ((node.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION)) != 0) {
                // Interfaces have unusual invocation shapes (default/super calls);
                // keep them out of the safe subset.
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

            int perClass = 0;
            for (MethodNode method : node.methods) {
                if (maxMethods > 0 && perClass >= maxMethods) {
                    break;
                }
                if (!eligible(context, node, method, maxInstructions)) {
                    continue;
                }
                try {
                    Payload payload = translate(node, method);
                    if (payload == null) {
                        skipped++;
                        continue;
                    }
                    if (payload.slots() > MAX_PAYLOAD_SLOTS) {
                        skipped++;
                        context.log().debug("virtualization skipped %s.%s: payload too large",
                                node.name, method.name);
                        continue;
                    }
                    if (virtualMachine == null) {
                        virtualMachine = context.runtime().ensureVirtualMachine(
                                context.classes(), context.config().seed());
                    }
                    Holder holder = createHolder(context, payload);
                    rewrite(node, method, payload, holder);
                    methods++;
                    perClass++;
                    instructions += payload.code.size();
                } catch (SkipMethod e) {
                    skipped++;
                    context.log().debug("virtualization skipped %s.%s: %s",
                            node.name, method.name, e.getMessage());
                } catch (RuntimeException e) {
                    skipped++;
                    context.log().debug("virtualization failed %s.%s: %s",
                            node.name, method.name, e.getMessage());
                }
            }
        }

        context.stats().add("virtualization.methods", methods);
        context.stats().add("virtualization.instructions", instructions);
        context.stats().add("virtualization.skipped", skipped);
        context.log().info("method-virtualization: virtualized %d methods (%d instructions), %d skipped",
                methods, instructions, skipped);
    }

    private static boolean eligible(PassContext context, ClassNode owner, MethodNode method, int max) {
        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            return false;
        }
        if (method.name.startsWith("<")) {
            return false;
        }
        if (method.tryCatchBlocks != null && !method.tryCatchBlocks.isEmpty()) {
            return false;
        }
        if (method.instructions == null || method.instructions.size() < 3
                || method.instructions.size() > max) {
            return false;
        }
        if (method.maxLocals > MAX_LOCALS) {
            return false;
        }
        Boolean methodEnabled = AsmUtil.annotationBoolean(method.invisibleAnnotations, ANNOTATION, "enabled");
        if (methodEnabled == null) {
            methodEnabled = AsmUtil.annotationBoolean(method.visibleAnnotations, ANNOTATION, "enabled");
        }
        if (Boolean.FALSE.equals(methodEnabled)) {
            return false;
        }
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode < 0) {
                continue;
            }
            if (insn instanceof InvokeDynamicInsnNode) {
                return false;
            }
            if (opcode == Opcodes.NEW || opcode == Opcodes.JSR || opcode == Opcodes.RET
                    || opcode == Opcodes.MONITORENTER || opcode == Opcodes.MONITOREXIT) {
                return false;
            }
            if (insn instanceof LdcInsnNode) {
                Object constant = ((LdcInsnNode) insn).cst;
                if (constant instanceof Handle || constant instanceof ConstantDynamic) {
                    return false;
                }
            }
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                if (call.getOpcode() == Opcodes.INVOKESPECIAL
                        && !"<init>".equals(call.name)
                        && !owner.name.equals(call.owner)) {
                    // super.foo() would dispatch to the override through reflection.
                    return false;
                }
            }
        }
        return true;
    }

    private Payload translate(ClassNode owner, MethodNode method) {
        Map<LabelNode, Integer> labels = new IdentityHashMap<LabelNode, Integer>();
        int pc = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof LabelNode) {
                labels.put((LabelNode) insn, Integer.valueOf(pc));
                continue;
            }
            int opcode = insn.getOpcode();
            if (opcode < 0) {
                continue;
            }
            pc += length(insn);
        }

        Payload payload = new Payload();
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode < 0) {
                continue;
            }
            if (insn instanceof LdcInsnNode) {
                payload.code.add(Opcodes.LDC);
                payload.code.add(payload.constant(((LdcInsnNode) insn).cst));
            } else if (insn instanceof IntInsnNode) {
                IntInsnNode value = (IntInsnNode) insn;
                if (opcode == Opcodes.BIPUSH || opcode == Opcodes.SIPUSH) {
                    payload.code.add(Opcodes.LDC);
                    payload.code.add(payload.constant(Integer.valueOf(value.operand)));
                } else if (opcode == Opcodes.NEWARRAY) {
                    payload.code.add(opcode);
                    payload.code.add(value.operand);
                } else {
                    throw new SkipMethod("unsupported integer instruction " + opcode);
                }
            } else if (insn instanceof VarInsnNode) {
                payload.code.add(opcode);
                payload.code.add(((VarInsnNode) insn).var);
            } else if (insn instanceof IincInsnNode) {
                IincInsnNode iinc = (IincInsnNode) insn;
                payload.code.add(Opcodes.IINC);
                payload.code.add(iinc.var);
                payload.code.add(iinc.incr);
            } else if (insn instanceof FieldInsnNode) {
                FieldInsnNode field = (FieldInsnNode) insn;
                payload.code.add(opcode);
                payload.code.add(payload.ref("F|" + field.owner + "|" + field.name + "|" + field.desc));
            } else if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) insn;
                payload.code.add(opcode);
                payload.code.add(payload.ref("M|" + call.owner + "|" + call.name + "|" + call.desc));
            } else if (insn instanceof TypeInsnNode) {
                if (opcode == Opcodes.NEW) {
                    throw new SkipMethod("NEW");
                }
                payload.code.add(opcode);
                payload.code.add(payload.ref("T|" + ((TypeInsnNode) insn).desc));
            } else if (insn instanceof JumpInsnNode) {
                payload.code.add(opcode);
                payload.code.add(label(labels, ((JumpInsnNode) insn).label));
            } else if (insn instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode table = (TableSwitchInsnNode) insn;
                payload.code.add(Opcodes.TABLESWITCH);
                payload.code.add(label(labels, table.dflt));
                payload.code.add(table.min);
                payload.code.add(table.max);
                for (LabelNode target : table.labels) {
                    payload.code.add(label(labels, target));
                }
            } else if (insn instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode lookup = (LookupSwitchInsnNode) insn;
                payload.code.add(Opcodes.LOOKUPSWITCH);
                payload.code.add(label(labels, lookup.dflt));
                payload.code.add(lookup.keys.size());
                for (int i = 0; i < lookup.keys.size(); i++) {
                    payload.code.add(((Integer) lookup.keys.get(i)).intValue());
                    payload.code.add(label(labels, lookup.labels.get(i)));
                }
            } else if (insn instanceof MultiANewArrayInsnNode) {
                MultiANewArrayInsnNode multi = (MultiANewArrayInsnNode) insn;
                payload.code.add(Opcodes.MULTIANEWARRAY);
                payload.code.add(payload.ref("T|" + multi.desc));
                payload.code.add(multi.dims);
            } else if (insn instanceof InsnNode) {
                if (opcode == Opcodes.NEW || opcode == Opcodes.MONITORENTER
                        || opcode == Opcodes.MONITOREXIT) {
                    throw new SkipMethod("unsupported instruction " + opcode);
                }
                payload.code.add(opcode);
            } else {
                throw new SkipMethod("unsupported instruction " + insn.getClass().getSimpleName());
            }
        }
        payload.finish();
        return payload;
    }

    private static int length(AbstractInsnNode insn) {
        int opcode = insn.getOpcode();
        if (insn instanceof TableSwitchInsnNode) {
            TableSwitchInsnNode table = (TableSwitchInsnNode) insn;
            return 4 + table.labels.size();
        }
        if (insn instanceof LookupSwitchInsnNode) {
            return 3 + 2 * ((LookupSwitchInsnNode) insn).labels.size();
        }
        if (insn instanceof MultiANewArrayInsnNode) {
            return 3;
        }
        if (insn instanceof IincInsnNode) {
            return 3;
        }
        if (insn instanceof LdcInsnNode || insn instanceof IntInsnNode
                || insn instanceof VarInsnNode || insn instanceof JumpInsnNode
                || insn instanceof FieldInsnNode || insn instanceof MethodInsnNode
                || insn instanceof TypeInsnNode) {
            return 2;
        }
        if (insn instanceof LabelNode) {
            return 0;
        }
        if (opcode < 0) {
            return 0;
        }
        return 1;
    }

    private static int label(Map<LabelNode, Integer> labels, LabelNode label) {
        Integer pc = labels.get(label);
        if (pc == null) {
            throw new SkipMethod("unresolved label");
        }
        return pc.intValue();
    }

    private Holder createHolder(PassContext context, Payload payload) {
        String name;
        do {
            name = "zipit/runtime/V" + Long.toHexString(context.random().nextLong());
        } while (context.classes().getClass(name) != null);

        NameGenerator names = new NameGenerator(context.random(), "");
        Holder holder = new Holder(name, names.random(5), names.random(5), names.random(5));
        Shield shield = null;
        if (context.config().antiLlm().enabled()
                && context.config().antiLlm().shieldVirtualization()) {
            shield = createShield(context);
        }

        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
        node.name = name;
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_SYNTHETIC, holder.codeField, "[I", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_SYNTHETIC, holder.constantField, "[Ljava/lang/Object;", null, null));
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_SYNTHETIC, holder.refField, "[Ljava/lang/String;", null, null));

        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        InsnList code = clinit.instructions;
        AsmUtil.pushInt(code, payload.code.size());
        code.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));
        code.add(new FieldInsnNode(Opcodes.PUTSTATIC, name, holder.codeField, "[I"));
        AsmUtil.pushInt(code, payload.constants.size());
        code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        code.add(new FieldInsnNode(Opcodes.PUTSTATIC, name, holder.constantField, "[Ljava/lang/Object;"));
        AsmUtil.pushInt(code, payload.refs.length);
        code.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/String"));
        code.add(new FieldInsnNode(Opcodes.PUTSTATIC, name, holder.refField, "[Ljava/lang/String;"));

        for (int i = 0; i < payload.code.size(); i++) {
            code.add(new FieldInsnNode(Opcodes.GETSTATIC, name, holder.codeField, "[I"));
            AsmUtil.pushInt(code, i);
            int value = payload.code.get(i);
            if (shield != null) {
                value ^= shield.keys[i & (shield.keys.length - 1)];
            }
            AsmUtil.pushInt(code, value);
            code.add(new InsnNode(Opcodes.IASTORE));
        }
        for (int i = 0; i < payload.constants.size(); i++) {
            code.add(new FieldInsnNode(Opcodes.GETSTATIC, name, holder.constantField, "[Ljava/lang/Object;"));
            AsmUtil.pushInt(code, i);
            emitConstant(context, code, payload.constants.get(i));
            code.add(new InsnNode(Opcodes.AASTORE));
        }
        for (int i = 0; i < payload.refs.length; i++) {
            code.add(new FieldInsnNode(Opcodes.GETSTATIC, name, holder.refField, "[Ljava/lang/String;"));
            AsmUtil.pushInt(code, i);
            emitString(context, code, payload.refs[i]);
            code.add(new InsnNode(Opcodes.AASTORE));
        }
        if (shield != null) {
            emitDecryptLoop(code, name, holder.codeField, shield);
            clinit.maxLocals = Math.max(clinit.maxLocals, 1);
            context.stats().add("antiLlm.shieldedPayloads", 1);
        }
        code.add(new InsnNode(Opcodes.RETURN));
        // Deepest path: decrypt loop (array, index, array, index, key, mask) = 6.
        clinit.maxStack = 6;
        node.methods.add(clinit);

        context.classes().addGenerated(node);
        context.stats().add("virtualization.holders", 1);
        return holder;
    }

    /**
     * Generates a separate key-holder class. The payload holder stores the
     * code array XORed with this key and decrypts it in its own {@code <clinit>}.
     * A static analyzer must locate and execute both initializers instead of
     * reading a literal instruction array.
     */
    private Shield createShield(PassContext context) {
        String holderName;
        do {
            holderName = "zipit/runtime/K" + Long.toHexString(context.random().nextLong());
        } while (context.classes().getClass(holderName) != null);
        NameGenerator names = new NameGenerator(context.random(), "");
        String keyField = names.random(5);
        int length = 8;
        int seed = context.random().nextInt();
        int[] keys = new int[length];
        int state = seed;
        for (int i = 0; i < length; i++) {
            state = state * 1103515245 + 12345;
            keys[i] = state ^ (i * 0x9E3779B9);
        }

        ClassNode node = new ClassNode();
        node.version = Opcodes.V1_8;
        node.access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC;
        node.name = holderName;
        node.superName = "java/lang/Object";
        node.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_SYNTHETIC, keyField, "[I", null, null));

        MethodNode clinit = new MethodNode(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        InsnList code = clinit.instructions;
        AsmUtil.pushInt(code, length);
        code.add(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT));
        code.add(new FieldInsnNode(Opcodes.PUTSTATIC, holderName, keyField, "[I"));
        AsmUtil.pushInt(code, seed);
        code.add(new VarInsnNode(Opcodes.ISTORE, 0));
        AsmUtil.pushInt(code, 0);
        code.add(new VarInsnNode(Opcodes.ISTORE, 1));
        LabelNode loop = new LabelNode();
        LabelNode end = new LabelNode();
        code.add(loop);
        code.add(new VarInsnNode(Opcodes.ILOAD, 1));
        AsmUtil.pushInt(code, length);
        code.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        AsmUtil.pushInt(code, 1103515245);
        code.add(new InsnNode(Opcodes.IMUL));
        AsmUtil.pushInt(code, 12345);
        code.add(new InsnNode(Opcodes.IADD));
        code.add(new VarInsnNode(Opcodes.ISTORE, 0));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, holderName, keyField, "[I"));
        code.add(new VarInsnNode(Opcodes.ILOAD, 1));
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        code.add(new VarInsnNode(Opcodes.ILOAD, 1));
        AsmUtil.pushInt(code, 0x9E3779B9);
        code.add(new InsnNode(Opcodes.IMUL));
        code.add(new InsnNode(Opcodes.IXOR));
        code.add(new InsnNode(Opcodes.IASTORE));
        code.add(new IincInsnNode(1, 1));
        code.add(new JumpInsnNode(Opcodes.GOTO, loop));
        code.add(end);
        code.add(new InsnNode(Opcodes.RETURN));
        clinit.maxLocals = 2;
        // Stack: key array + loop index + state + index + constant = 5.
        clinit.maxStack = 5;
        node.methods.add(clinit);

        context.classes().addGenerated(node);
        context.stats().add("antiLlm.keyHolders", 1);
        return new Shield(holderName, keyField, keys);
    }

    private static void emitDecryptLoop(InsnList code, String holderName, String codeField, Shield shield) {
        AsmUtil.pushInt(code, 0);
        code.add(new VarInsnNode(Opcodes.ISTORE, 0));
        LabelNode loop = new LabelNode();
        LabelNode end = new LabelNode();
        code.add(loop);
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, holderName, codeField, "[I"));
        code.add(new InsnNode(Opcodes.ARRAYLENGTH));
        code.add(new JumpInsnNode(Opcodes.IF_ICMPGE, end));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, holderName, codeField, "[I"));
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, holderName, codeField, "[I"));
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        code.add(new InsnNode(Opcodes.IALOAD));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, shield.holderName, shield.keyField, "[I"));
        code.add(new VarInsnNode(Opcodes.ILOAD, 0));
        AsmUtil.pushInt(code, shield.keys.length - 1);
        code.add(new InsnNode(Opcodes.IAND));
        code.add(new InsnNode(Opcodes.IALOAD));
        code.add(new InsnNode(Opcodes.IXOR));
        code.add(new InsnNode(Opcodes.IASTORE));
        code.add(new IincInsnNode(0, 1));
        code.add(new JumpInsnNode(Opcodes.GOTO, loop));
        code.add(end);
    }

    private void emitConstant(PassContext context, InsnList code, Object value) {
        if (value instanceof String) {
            emitString(context, code, (String) value);
        } else if (value instanceof Integer) {
            AsmUtil.pushInt(code, ((Integer) value).intValue());
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                    "(I)Ljava/lang/Integer;", false));
        } else if (value instanceof Long) {
            code.add(new LdcInsnNode(value));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf",
                    "(J)Ljava/lang/Long;", false));
        } else if (value instanceof Float) {
            code.add(new LdcInsnNode(value));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf",
                    "(F)Ljava/lang/Float;", false));
        } else if (value instanceof Double) {
            code.add(new LdcInsnNode(value));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf",
                    "(D)Ljava/lang/Double;", false));
        } else {
            code.add(new LdcInsnNode(value));
        }
    }

    private void emitString(PassContext context, InsnList code, String value) {
        if (!context.config().stringEncryption().enabled()
                || value.length() < context.config().stringEncryption().minLength()) {
            code.add(new LdcInsnNode(value));
            return;
        }
        if (decryptor == null) {
            decryptor = context.runtime().ensureStringDecryptor(
                    context.classes(), context.config().seed());
        }
        long key = context.random().nextLong();
        if (key == 0L) {
            key = 0x5A17L;
        }
        code.add(new LdcInsnNode(Cipher.encrypt(value, key)));
        code.add(new LdcInsnNode(Long.valueOf(key)));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, decryptor, "d",
                "(Ljava/lang/String;J)Ljava/lang/String;", false));
    }

    private void rewrite(ClassNode owner, MethodNode method, Payload payload, Holder holder) {
        // Argument slots are part of the calling convention and must never be
        // reused: the interpreter array starts right after them.
        int localsSlot = Math.max(1, Math.max(method.maxLocals, argumentSlots(method)));
        int resultSlot = localsSlot + 1;
        InsnList out = new InsnList();
        AsmUtil.pushInt(out, localsSlot);
        out.add(new TypeInsnNode(Opcodes.ANEWARRAY, "java/lang/Object"));
        out.add(new VarInsnNode(Opcodes.ASTORE, localsSlot));

        boolean isStatic = (method.access & Opcodes.ACC_STATIC) != 0;
        if (!isStatic) {
            out.add(new VarInsnNode(Opcodes.ALOAD, localsSlot));
            AsmUtil.pushInt(out, 0);
            out.add(new VarInsnNode(Opcodes.ALOAD, 0));
            out.add(new InsnNode(Opcodes.AASTORE));
        }
        int slot = isStatic ? 0 : 1;
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            out.add(new VarInsnNode(Opcodes.ALOAD, localsSlot));
            AsmUtil.pushInt(out, slot);
            out.add(new VarInsnNode(AsmUtil.loadOpcode(argument), slot));
            box(out, argument);
            out.add(new InsnNode(Opcodes.AASTORE));
            slot += argument.getSize();
        }

        out.add(new FieldInsnNode(Opcodes.GETSTATIC, holder.name, holder.codeField, "[I"));
        out.add(new FieldInsnNode(Opcodes.GETSTATIC, holder.name, holder.constantField, "[Ljava/lang/Object;"));
        out.add(new FieldInsnNode(Opcodes.GETSTATIC, holder.name, holder.refField, "[Ljava/lang/String;"));
        out.add(new VarInsnNode(Opcodes.ALOAD, localsSlot));
        out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, virtualMachine, "run", VM_DESC, false));
        out.add(new VarInsnNode(Opcodes.ASTORE, resultSlot));

        Type returns = Type.getReturnType(method.desc);
        if (returns.getSort() == Type.VOID) {
            out.add(new InsnNode(Opcodes.RETURN));
        } else {
            out.add(new VarInsnNode(Opcodes.ALOAD, resultSlot));
            switch (returns.getSort()) {
                case Type.BOOLEAN: {
                    // Accept both representations: Integer 0/1 (normalized VM)
                    // and java.lang.Boolean (older payloads / reflection).
                    LabelNode numberPath = new LabelNode();
                    out.add(new InsnNode(Opcodes.DUP));
                    out.add(new TypeInsnNode(Opcodes.INSTANCEOF, "java/lang/Boolean"));
                    out.add(new JumpInsnNode(Opcodes.IFEQ, numberPath));
                    out.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Boolean"));
                    out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Boolean",
                            "booleanValue", "()Z", false));
                    out.add(new InsnNode(Opcodes.IRETURN));
                    out.add(numberPath);
                    out.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                    out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number",
                            "intValue", "()I", false));
                    out.add(new InsnNode(Opcodes.IRETURN));
                    break;
                }
                case Type.BYTE:
                case Type.CHAR:
                case Type.SHORT:
                case Type.INT:
                    out.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                    out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number",
                            "intValue", "()I", false));
                    out.add(new InsnNode(Opcodes.IRETURN));
                    break;
                case Type.LONG:
                    out.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                    out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number",
                            "longValue", "()J", false));
                    out.add(new InsnNode(Opcodes.LRETURN));
                    break;
                case Type.FLOAT:
                    out.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                    out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number",
                            "floatValue", "()F", false));
                    out.add(new InsnNode(Opcodes.FRETURN));
                    break;
                case Type.DOUBLE:
                    out.add(new TypeInsnNode(Opcodes.CHECKCAST, "java/lang/Number"));
                    out.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/Number",
                            "doubleValue", "()D", false));
                    out.add(new InsnNode(Opcodes.DRETURN));
                    break;
                default:
                    out.add(new TypeInsnNode(Opcodes.CHECKCAST,
                            returns.getSort() == Type.ARRAY ? returns.getDescriptor() : returns.getInternalName()));
                    out.add(new InsnNode(Opcodes.ARETURN));
                    break;
            }
        }

        method.instructions = out;
        method.maxLocals = localsSlot + 2;
        // Deepest path builds one boxed argument: array + index + widest load
        // (2 slots) = 4; the dispatch call site peaks at four references too.
        method.maxStack = 4;
        method.tryCatchBlocks = new ArrayList<org.objectweb.asm.tree.TryCatchBlockNode>();
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
    }

    private static int argumentSlots(MethodNode method) {
        int slots = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type argument : Type.getArgumentTypes(method.desc)) {
            slots += argument.getSize();
        }
        return slots;
    }

    private static void box(InsnList out, Type type) {
        switch (type.getSort()) {
                case Type.BOOLEAN:
                    // A JVM boolean operand is an int (0/1) on the stack. Store it
                    // as an Integer so the interpreter never has to unbox a
                    // java.lang.Boolean while executing virtualized bytecode
                    // (if/if_icmp/iand/... all use the int representation).
                    out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                            "(I)Ljava/lang/Integer;", false));
                    break;
            case Type.BYTE:
                out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Byte", "valueOf",
                        "(B)Ljava/lang/Byte;", false));
                break;
            case Type.CHAR:
                out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Character", "valueOf",
                        "(C)Ljava/lang/Character;", false));
                break;
            case Type.SHORT:
                out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Short", "valueOf",
                        "(S)Ljava/lang/Short;", false));
                break;
            case Type.INT:
                out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                        "(I)Ljava/lang/Integer;", false));
                break;
            case Type.LONG:
                out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf",
                        "(J)Ljava/lang/Long;", false));
                break;
            case Type.FLOAT:
                out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf",
                        "(F)Ljava/lang/Float;", false));
                break;
            case Type.DOUBLE:
                out.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf",
                        "(D)Ljava/lang/Double;", false));
                break;
            default:
                break;
        }
    }

    /** Growable int array used while translating one method. */
    private static final class IntList {
        private int[] values = new int[64];
        private int size;

        void add(int value) {
            if (size == values.length) {
                int[] grown = new int[values.length * 2];
                System.arraycopy(values, 0, grown, 0, values.length);
                values = grown;
            }
            values[size++] = value;
        }

        int get(int index) {
            return values[index];
        }

        int size() {
            return size;
        }

        int[] toArray() {
            int[] out = new int[size];
            System.arraycopy(values, 0, out, 0, size);
            return out;
        }
    }

    private static final class Payload {
        final IntList code = new IntList();
        final List<Object> constants = new ArrayList<Object>();
        final Map<Object, Integer> constantIndex = new LinkedHashMap<Object, Integer>();
        final Map<String, Integer> refIndex = new LinkedHashMap<String, Integer>();
        String[] refs = new String[0];

        int constant(Object value) {
            Integer index = constantIndex.get(value);
            if (index == null) {
                index = Integer.valueOf(constants.size());
                constants.add(value);
                constantIndex.put(value, index);
            }
            return index.intValue();
        }

        int ref(String value) {
            Integer index = refIndex.get(value);
            if (index == null) {
                index = Integer.valueOf(refIndex.size());
                refIndex.put(value, index);
            }
            return index.intValue();
        }

        void finish() {
            refs = refIndex.keySet().toArray(new String[0]);
        }

        int slots() {
            return code.size() + constants.size() + refs.length;
        }
    }

    private static final class Shield {
        final String holderName;
        final String keyField;
        final int[] keys;

        Shield(String holderName, String keyField, int[] keys) {
            this.holderName = holderName;
            this.keyField = keyField;
            this.keys = keys;
        }
    }

    private static final class Holder {
        final String name;
        final String codeField;
        final String constantField;
        final String refField;

        Holder(String name, String codeField, String constantField, String refField) {
            this.name = name;
            this.codeField = codeField;
            this.constantField = constantField;
            this.refField = refField;
        }
    }

    private static final class SkipMethod extends RuntimeException {
        SkipMethod(String message) {
            super(message);
        }
    }
}
