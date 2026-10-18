package io.zipit.core.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Invokedynamic bootstrap used to hide member references.
 *
 * <p>Owner and member names are stored as encrypted bootstrap arguments and
 * resolved lazily on the first invocation.</p>
 */
public final class ZipitIndyBootstrap {

    private ZipitIndyBootstrap() {
    }

    /** Static method call sites. */
    public static CallSite bootstrap(MethodHandles.Lookup caller, String name, MethodType type,
                                     String encOwner, long ownerKey,
                                     String encMethod, long methodKey) throws Exception {
        Class<?> owner = owner(caller, encOwner, ownerKey);
        String method = decrypt(encMethod, methodKey);
        MethodHandle handle = caller.findStatic(owner, method, type);
        return new ConstantCallSite(handle);
    }

    /** Virtual / interface call sites; the receiver is the first argument. */
    public static CallSite bootstrapVirtual(MethodHandles.Lookup caller, String name, MethodType type,
                                            String encOwner, long ownerKey,
                                            String encMethod, long methodKey) throws Exception {
        Class<?> owner = owner(caller, encOwner, ownerKey);
        String method = decrypt(encMethod, methodKey);
        MethodType targetType = type.dropParameterTypes(0, 1);
        MethodHandle handle = caller.findVirtual(owner, method, targetType);
        return new ConstantCallSite(handle.asType(type));
    }

    /** Static field read. */
    public static CallSite bootstrapFieldGetStatic(MethodHandles.Lookup caller, String name,
                                                   MethodType type, String encOwner, long ownerKey,
                                                   String encField, long fieldKey) throws Exception {
        Class<?> owner = owner(caller, encOwner, ownerKey);
        String field = decrypt(encField, fieldKey);
        MethodHandle handle = caller.findStaticGetter(owner, field, type.returnType());
        return new ConstantCallSite(handle.asType(type));
    }

    /** Instance field read; the receiver is the first argument. */
    public static CallSite bootstrapFieldGet(MethodHandles.Lookup caller, String name,
                                             MethodType type, String encOwner, long ownerKey,
                                             String encField, long fieldKey) throws Exception {
        Class<?> owner = owner(caller, encOwner, ownerKey);
        String field = decrypt(encField, fieldKey);
        MethodHandle handle = caller.findGetter(owner, field, type.returnType());
        return new ConstantCallSite(handle.asType(type));
    }

    /** Static field write. */
    public static CallSite bootstrapFieldSetStatic(MethodHandles.Lookup caller, String name,
                                                   MethodType type, String encOwner, long ownerKey,
                                                   String encField, long fieldKey) throws Exception {
        Class<?> owner = owner(caller, encOwner, ownerKey);
        String field = decrypt(encField, fieldKey);
        MethodHandle handle = caller.findStaticSetter(owner, field, type.parameterType(0));
        return new ConstantCallSite(handle.asType(type));
    }

    /** Instance field write; the receiver is the first argument. */
    public static CallSite bootstrapFieldSet(MethodHandles.Lookup caller, String name,
                                             MethodType type, String encOwner, long ownerKey,
                                             String encField, long fieldKey) throws Exception {
        Class<?> owner = owner(caller, encOwner, ownerKey);
        String field = decrypt(encField, fieldKey);
        MethodHandle handle = caller.findSetter(owner, field, type.parameterType(1));
        return new ConstantCallSite(handle.asType(type));
    }

    private static Class<?> owner(MethodHandles.Lookup caller, String encOwner, long key) throws Exception {
        String owner = decrypt(encOwner, key);
        return Class.forName(owner.replace('/', '.'), true, caller.lookupClass().getClassLoader());
    }

    private static String decrypt(String payload, long key) {
        byte[] data = Base64.getDecoder().decode(payload);
        long state = key == 0L ? 0x9E3779B97F4A7C15L : key;
        for (int i = 0; i < data.length; i++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            data[i] = (byte) (data[i] ^ (byte) (state >>> 24));
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
