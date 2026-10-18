package io.zipit.core.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Symmetric stream obfuscation used by the string-encryption pass.
 *
 * <p>This is a xorshift64 keystream combined with Base64. It is designed to
 * defeat naive string search and decompiler constant folding, not to provide
 * cryptographic secrecy; the key is embedded in the calling class.</p>
 */
public final class Cipher {

    private static final long FALLBACK_SEED = 0x9E3779B97F4A7C15L;

    private Cipher() {
    }

    /** Encrypts a UTF-8 string into a Base64 payload. */
    public static String encrypt(String plain, long key) {
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        long state = key == 0 ? FALLBACK_SEED : key;
        for (int i = 0; i < data.length; i++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            data[i] = (byte) (data[i] ^ (byte) (state >>> 24));
        }
        return Base64.getEncoder().encodeToString(data);
    }

    /** Decrypts a payload produced by {@link #encrypt(String, long)}. */
    public static String decrypt(String payload, long key) {
        byte[] data = Base64.getDecoder().decode(payload);
        long state = key == 0 ? FALLBACK_SEED : key;
        for (int i = 0; i < data.length; i++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            data[i] = (byte) (data[i] ^ (byte) (state >>> 24));
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
