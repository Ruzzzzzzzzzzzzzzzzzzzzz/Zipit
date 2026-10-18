package io.zipit.core.model;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

/**
 * {@link ClassWriter} that resolves common superclasses through Zipit's
 * lightweight {@link Hierarchy} instead of the tool's own class loader.
 */
public final class ZipitClassWriter extends ClassWriter {

    private final Hierarchy hierarchy;

    public ZipitClassWriter(int flags, Hierarchy hierarchy) {
        super(flags);
        this.hierarchy = hierarchy;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        return hierarchy.getCommonSuperClass(type1, type2);
    }

    /**
     * Serializes a class, preferring computed frames and falling back to
     * computed max stack/locals when the hierarchy is incomplete.
     *
     * @param node      class to serialize
     * @param hierarchy hierarchy resolver
     * @return class file bytes
     */
    public static byte[] toBytes(ClassNode node, Hierarchy hierarchy) {
        try {
            ZipitClassWriter writer = new ZipitClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS, hierarchy);
            node.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException first) {
            ZipitClassWriter writer = new ZipitClassWriter(ClassWriter.COMPUTE_MAXS, hierarchy);
            node.accept(writer);
            return writer.toByteArray();
        }
    }
}
