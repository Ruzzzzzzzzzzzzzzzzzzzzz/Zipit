package io.zipit.core.runtime;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * String decryptor template injected into protected programs.
 *
 * <p>This class is never referenced by the Zipit runtime itself; its bytecode
 * is copied into the output JAR under a randomized name. The algorithm must
 * stay in sync with {@code io.zipit.core.util.Cipher}.</p>
 */
public final class ZipitStringDecryptor {

    private ZipitStringDecryptor() {
    }

    /**
     * Decrypts a Base64 payload with the supplied 64-bit key.
     *
     * <p>The result is interned so that encrypted string literals keep the
     * identity semantics of the original constants: {@code "x" == "x"} and
     * {@code "x" == new String("x").intern()} must still hold in protected
     * code (also matters for {@code synchronized("lock")} and
     * identity-keyed caches). The cost is one permanent string-pool entry per
     * distinct literal.</p>
     *
     * @param payload encrypted string
     * @param key     per-string key embedded at the call site
     * @return decrypted, interned UTF-8 string
     */
    public static String d(String payload, long key) {
        byte[] data = Base64.getDecoder().decode(payload);
        long state = key == 0L ? 0x9E3779B97F4A7C15L : key;
        for (int i = 0; i < data.length; i++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            data[i] = (byte) (data[i] ^ (byte) (state >>> 24));
        }
        return new String(data, StandardCharsets.UTF_8).intern();
    }
}
