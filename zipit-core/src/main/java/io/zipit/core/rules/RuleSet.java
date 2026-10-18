package io.zipit.core.rules;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Keep/transform policy consumed by every pass.
 *
 * <p>Three levels are supported:</p>
 * <ul>
 *   <li><b>keep</b> — no transforms and no renaming</li>
 *   <li><b>soft keep</b> — transforms may run, but names are preserved</li>
 *   <li><b>normal</b> — fully transformable</li>
 * </ul>
 */
public interface RuleSet {

    boolean keepClass(ClassNode node);

    boolean keepClass(String internalName);

    boolean softKeepClass(ClassNode node);

    boolean transformClass(ClassNode node);

    boolean allowClassRename(ClassNode node);

    boolean keepMethod(String owner, MethodNode method);

    boolean keepField(String owner, FieldNode field);

    boolean allowMethodRename(String owner, MethodNode method);

    boolean allowFieldRename(String owner, FieldNode field);

    /** True when the class or member carries {@code io.zipit.annotations.Keep}. */
    boolean hasKeepAnnotation(ClassNode node);

    boolean hasKeepAnnotation(MethodNode method);

    boolean hasKeepAnnotation(FieldNode field);
}
