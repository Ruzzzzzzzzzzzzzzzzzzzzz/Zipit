package io.zipit.core.pass.impl;

import io.zipit.core.api.Pass;
import io.zipit.core.api.PassContext;
import io.zipit.core.rules.Glob;
import io.zipit.core.util.AsmUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicInterpreter;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * State-machine control-flow flattening.
 *
 * <p>Eligible methods are converted into a {@code state -> tableswitch}
 * dispatcher. Only methods whose block entries have an empty operand stack and
 * consistent local types are flattened; exception handlers, switches and
 * JSR/RET are rejected. Non-parameter locals are initialized before the
 * dispatcher so the loop can merge local types safely.</p>
 */
public final class ControlFlowFlatteningPass implements Pass {

    private static final String FLOW_ANNOTATION = "Lio/zipit/annotations/FlowObfuscation;";
    private static final int KIND_FALLTHROUGH = 0;
    private static final int KIND_GOTO = 1;
    private static final int KIND_CONDITIONAL = 2;
    private static final int KIND_RETURN = 3;

    @Override
    public String name() {
        return "control-flow-flattening";
    }

    @Override
    public int order() {
        return 92;
    }

    @Override
    public boolean enabled(PassContext context) {
        return context.config().flow().enabled() && context.config().flow().flattening();
    }

    @Override
    public void run(PassContext context) {
        int flattened = 0;
        int blocks = 0;
        int max = context.config().flow().maxInstructions();
        Set<String> excluded = excludedNames(context);
        for (ClassNode node : context.classes().classes().values()) {
            if (context.classes().isGenerated(node.name) || context.classes().isFrozen(node.name)) {
                continue;
            }
            if (excluded.contains(node.name)) {
                continue;
            }
            if (!context.rules().transformClass(node)) {
                continue;
            }
            if ((node.access & (Opcodes.ACC_INTERFACE | Opcodes.ACC_ANNOTATION)) != 0
                    || "module-info".equals(node.name)) {
                continue;
            }
            Boolean classEnabled = AsmUtil.annotationBoolean(node.invisibleAnnotations, FLOW_ANNOTATION, "enabled");
            if (classEnabled == null) {
                classEnabled = AsmUtil.annotationBoolean(node.visibleAnnotations, FLOW_ANNOTATION, "enabled");
            }
            if (Boolean.FALSE.equals(classEnabled)) {
                continue;
            }
            for (MethodNode method : node.methods) {
                if (!eligible(context, node, method, max)) {
                    continue;
                }
                try {
                    int count = flatten(node, method);
                    if (count > 0) {
                        flattened++;
                        blocks += count;
                    }
                } catch (Exception e) {
                    context.log().debug("flattening skipped %s.%s: %s",
                            node.name, method.name, e.getMessage());
                }
            }
        }
        context.stats().add("flow.flattened.methods", flattened);
        context.stats().add("flow.flattened.blocks", blocks);
        context.log().info("control-flow-flattening: flattened %d methods (%d blocks)", flattened, blocks);
    }

    /** Resolves class-glob exclusions (written with original names) to current names. */
    private static Set<String> excludedNames(PassContext context) {
        Set<String> result = new LinkedHashSet<String>();
        List<Glob> globs = new ArrayList<Glob>();
        for (String pattern : context.config().flow().flatteningExclude()) {
            if (pattern != null && !pattern.trim().isEmpty()) {
                globs.add(new Glob(pattern.trim()));
            }
        }
        if (globs.isEmpty()) {
            return result;
        }
        for (Glob glob : globs) {
            for (Map.Entry<String, String> entry : context.mappings().classes().entrySet()) {
                if (glob.matches(entry.getKey()) || glob.matches(entry.getValue())) {
                    result.add(entry.getValue());
                }
            }
            for (String name : context.classes().classes().keySet()) {
                if (glob.matches(name)) {
                    result.add(name);
                }
            }
        }
        return result;
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
        if (method.instructions == null || method.instructions.size() < 4 || method.instructions.size() > max) {
            return false;
        }
        if (context.rules().keepMethod(owner.name, method)) {
            return false;
        }
        Boolean methodEnabled = AsmUtil.annotationBoolean(method.invisibleAnnotations, FLOW_ANNOTATION, "enabled");
        if (methodEnabled == null) {
            methodEnabled = AsmUtil.annotationBoolean(method.visibleAnnotations, FLOW_ANNOTATION, "enabled");
        }
        if (Boolean.FALSE.equals(methodEnabled)) {
            return false;
        }
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            int opcode = insn.getOpcode();
            if (opcode == Opcodes.JSR || opcode == Opcodes.RET
                    || opcode == Opcodes.TABLESWITCH || opcode == Opcodes.LOOKUPSWITCH) {
                return false;
            }
        }
        return true;
    }

    private static int flatten(ClassNode owner, MethodNode method) throws AnalyzerException {
        // analyzeAndComputeMaxs derives maxStack/maxLocals from the code itself
        // instead of trusting method.maxStack. Generated methods (decoy methods,
        // forwarding wrappers, virtualization stubs) may have maxStack == 0, which
        // makes the plain analyze(...) fail with "Insufficient maximum stack size".
        Analyzer<BasicValue> analyzer = new Analyzer<BasicValue>(new BasicInterpreter());
        int previousMaxStack = method.maxStack;
        Frame<BasicValue>[] frames;
        try {
            frames = analyzer.analyzeAndComputeMaxs(owner.name, method);
        } catch (AnalyzerException e) {
            // analyzeAndComputeMaxs resets maxStack to -1 before analysing: keep the
            // method writable when this method is skipped.
            method.maxStack = Math.max(1, previousMaxStack);
            throw e;
        }
        int analyzedMaxStack = Math.max(1, method.maxStack);
        InsnList instructions = method.instructions;

        Set<AbstractInsnNode> leaderSet = new LinkedHashSet<AbstractInsnNode>();
        leaderSet.add(instructions.getFirst());
        for (AbstractInsnNode insn = instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof JumpInsnNode) {
                leaderSet.add(((JumpInsnNode) insn).label);
                if (insn.getNext() != null) {
                    leaderSet.add(insn.getNext());
                }
            } else if (isReturnOrThrow(insn.getOpcode()) && insn.getNext() != null) {
                leaderSet.add(insn.getNext());
            }
        }

        List<AbstractInsnNode> leaders = new ArrayList<AbstractInsnNode>();
        for (AbstractInsnNode leader : leaderSet) {
            if (instructions.indexOf(leader) >= 0) {
                leaders.add(leader);
            }
        }
        leaders.sort((a, b) -> Integer.compare(instructions.indexOf(a), instructions.indexOf(b)));
        if (leaders.size() < 2) {
            return 0;
        }

        int parameterSlots = argumentSlots(method);
        int localSlots = Math.max(1, Math.max(method.maxLocals, parameterSlots));
        String[] slotTypes = new String[localSlots];
        for (AbstractInsnNode leader : leaders) {
            int index = instructions.indexOf(leader);
            if (index < 0 || frames[index] == null || frames[index].getStackSize() != 0) {
                return 0;
            }
            Frame<BasicValue> frame = frames[index];
            // Frames can describe fewer locals than the method's slot budget (for
            // example static methods with no local variables); only inspect slots
            // that actually exist, or Frame#getLocal throws.
            int frameLocals = frame.getLocals();
            int localLimit = Math.min(slotTypes.length, frameLocals);
            for (int slot = 0; slot < localLimit; slot++) {
                BasicValue value = frame.getLocal(slot);
                if (value == null || value == BasicValue.UNINITIALIZED_VALUE) {
                    continue;
                }
                Type type = value.getType();
                if (type == null) {
                    continue;
                }
                String descriptor = type.getDescriptor();
                if (slotTypes[slot] == null) {
                    slotTypes[slot] = descriptor;
                } else if (!slotTypes[slot].equals(descriptor)) {
                    return 0;
                }
            }
        }

        int instructionCount = instructions.size();
        int[] blockOf = new int[instructionCount];
        List<Block> blocks = new ArrayList<Block>();
        for (int i = 0; i < leaders.size(); i++) {
            AbstractInsnNode leader = leaders.get(i);
            int start = instructions.indexOf(leader);
            int end = i + 1 < leaders.size() ? instructions.indexOf(leaders.get(i + 1)) : instructionCount;
            LabelNode label = leader instanceof LabelNode ? (LabelNode) leader : new LabelNode();
            Block block = new Block(i, label, leader);
            for (int j = start; j < end && j < instructionCount; j++) {
                block.insns.add(instructions.get(j));
                blockOf[j] = i;
            }
            blocks.add(block);
        }

        // Pre-compute every transition before moving instruction nodes into the
        // new list. Moving a node mutates its prev/next links, which would make
        // instructions.indexOf(...) unreliable.
        for (Block block : blocks) {
            block.terminator = findTerminator(block.insns);
            if (block.terminator == null) {
                block.kind = KIND_FALLTHROUGH;
                block.target = block.index + 1;
                if (block.target >= blocks.size()) {
                    return 0;
                }
            } else if (block.terminator.getOpcode() == Opcodes.GOTO) {
                block.kind = KIND_GOTO;
                block.target = targetBlock(instructions, blockOf,
                        ((JumpInsnNode) block.terminator).label);
                if (block.target < 0) {
                    return 0;
                }
            } else if (isConditional(block.terminator.getOpcode())) {
                block.kind = KIND_CONDITIONAL;
                block.trueTarget = targetBlock(instructions, blockOf,
                        ((JumpInsnNode) block.terminator).label);
                block.falseTarget = block.index + 1;
                if (block.trueTarget < 0 || block.falseTarget >= blocks.size()) {
                    return 0;
                }
            } else {
                block.kind = KIND_RETURN;
            }
        }

        if (System.getProperty("zipit.debug.flatten") != null) {
            System.err.println("FLAT " + owner.name + "." + method.name + " blocks=" + blocks.size());
            for (Block block : blocks) {
                System.err.println("  block " + block.index + " kind=" + block.kind
                        + " target=" + block.target
                        + " true=" + block.trueTarget + " false=" + block.falseTarget);
            }
        }

        InsnList out = new InsnList();
        int stateLocal = localSlots;
        method.maxLocals = stateLocal + 1;

        AsmUtil.pushInt(out, 0);
        out.add(new VarInsnNode(Opcodes.ISTORE, stateLocal));
        for (int slot = parameterSlots; slot < localSlots; slot++) {
            String descriptor = slotTypes[slot];
            if (descriptor == null || "V".equals(descriptor)) {
                continue;
            }
            Type type = Type.getType(descriptor);
            AsmUtil.pushDefault(out, type);
            out.add(new VarInsnNode(AsmUtil.storeOpcode(type), slot));
            if (type.getSize() == 2) {
                slot++;
            }
        }

        LabelNode loop = new LabelNode();
        out.add(loop);
        out.add(new VarInsnNode(Opcodes.ILOAD, stateLocal));
        LabelNode[] caseLabels = new LabelNode[blocks.size()];
        for (int i = 0; i < blocks.size(); i++) {
            caseLabels[i] = blocks.get(i).label;
        }
        LabelNode defaultLabel = new LabelNode();
        out.add(new TableSwitchInsnNode(0, blocks.size() - 1, defaultLabel, caseLabels));
        out.add(defaultLabel);
        out.add(new TypeInsnNode(Opcodes.NEW, "java/lang/IllegalStateException"));
        out.add(new InsnNode(Opcodes.DUP));
        out.add(new LdcInsnNode("zipit state"));
        out.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/IllegalStateException",
                "<init>", "(Ljava/lang/String;)V", false));
        out.add(new InsnNode(Opcodes.ATHROW));

        for (Block block : blocks) {
            out.add(block.label);
            for (AbstractInsnNode insn : block.insns) {
                if (insn == block.label || insn == block.terminator) {
                    continue;
                }
                out.add(insn);
            }
            switch (block.kind) {
                case KIND_GOTO:
                    emitState(out, stateLocal, loop, block.target);
                    break;
                case KIND_CONDITIONAL:
                    LabelNode trueLabel = new LabelNode();
                    out.add(new JumpInsnNode(block.terminator.getOpcode(), trueLabel));
                    emitState(out, stateLocal, loop, block.falseTarget);
                    out.add(trueLabel);
                    emitState(out, stateLocal, loop, block.trueTarget);
                    break;
                case KIND_RETURN:
                    out.add(block.terminator);
                    break;
                default:
                    emitState(out, stateLocal, loop, block.target);
                    break;
            }
        }

        method.instructions = out;
        // Dispatcher prologue (state store + tableswitch) and the default
        // handler (NEW/DUP/LDC) bound the new stack usage at 3 slots.
        method.maxStack = Math.max(analyzedMaxStack, 3);
        method.tryCatchBlocks = new ArrayList<org.objectweb.asm.tree.TryCatchBlockNode>();
        method.localVariables = null;
        method.visibleLocalVariableAnnotations = null;
        method.invisibleLocalVariableAnnotations = null;
        return blocks.size();
    }

    private static AbstractInsnNode findTerminator(List<AbstractInsnNode> insns) {
        for (int i = insns.size() - 1; i >= 0; i--) {
            AbstractInsnNode insn = insns.get(i);
            int opcode = insn.getOpcode();
            if (opcode == Opcodes.GOTO || isConditional(opcode) || isReturnOrThrow(opcode)) {
                return insn;
            }
            if (opcode >= 0) {
                return null;
            }
        }
        return null;
    }

    private static boolean isReturnOrThrow(int opcode) {
        return opcode >= Opcodes.IRETURN && opcode <= Opcodes.RETURN || opcode == Opcodes.ATHROW;
    }

    private static int argumentSlots(MethodNode method) {
        int slots = (method.access & Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for (Type type : Type.getArgumentTypes(method.desc)) {
            slots += type.getSize();
        }
        return slots;
    }

    private static void emitState(InsnList out, int stateLocal, LabelNode loop, int state) {
        AsmUtil.pushInt(out, state);
        out.add(new VarInsnNode(Opcodes.ISTORE, stateLocal));
        out.add(new JumpInsnNode(Opcodes.GOTO, loop));
    }

    private static int targetBlock(InsnList instructions, int[] blockOf, LabelNode label) {
        int index = instructions.indexOf(label);
        if (index < 0 || index >= blockOf.length) {
            return -1;
        }
        return blockOf[index];
    }

    private static boolean isConditional(int opcode) {
        return opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE
                || opcode == Opcodes.IFNULL || opcode == Opcodes.IFNONNULL;
    }

    private static final class Block {
        final int index;
        final LabelNode label;
        final List<AbstractInsnNode> insns = new ArrayList<AbstractInsnNode>();
        AbstractInsnNode terminator;
        int kind;
        int target = -1;
        int trueTarget = -1;
        int falseTarget = -1;

        Block(int index, LabelNode label, AbstractInsnNode leader) {
            this.index = index;
            this.label = label;
        }
    }
}
