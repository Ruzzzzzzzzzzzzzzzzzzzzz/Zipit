package io.zipit.licenseserver.auth;

import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographic helpers for the multi-software auth server.
 *
 * <p>All primitives are JDK-only (Java 8): PBKDF2WithHmacSHA256 for password
 * hashes, HMAC-SHA256 for stateless tokens, SecureRandom for every secret and
 * {@link MessageDigest#isEqual(byte[], byte[])} for constant-time comparisons.</p>
 */
public final class AuthCrypto {

    /** PBKDF2 iteration count mandated by tools/auth/DESIGN.md section 1. */
    public static final int PBKDF2_ITERATIONS = 120000;
    /** PBKDF2 salt size in bytes. */
    public static final int SALT_BYTES = 16;
    /** PBKDF2 derived key size in bytes. */
    public static final int KEY_BYTES = 32;
    /** Size of the HMAC token secret in bytes. */
    public static final int TOKEN_KEY_BYTES = 32;

    private static final String HASH_SCHEME = "pbkdf2";
    private static final char[] BASE32 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthCrypto() {
    }

    /** Creates a password hash string: {@code pbkdf2$<iterations>$<saltB64>$<hashB64>}. */
    public static String hashPassword(String password) {
        byte[] salt = randomBytes(SALT_BYTES);
        byte[] hash = pbkdf2(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BYTES);
        return HASH_SCHEME + "$" + PBKDF2_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Verifies a password against a stored hash. Never throws for malformed
     * hashes: returns {@code false} instead.
     */
    public static boolean verifyPassword(String password, String storedHash) {
        if (password == null || storedHash == null) {
            return false;
        }
        String[] parts = storedHash.split("\\$", -1);
        if (parts.length != 4 || !HASH_SCHEME.equals(parts[0])) {
            return false;
        }
        int iterations;
        try {
            iterations = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }
        if (iterations < 1) {
            return false;
        }
        byte[] salt;
        byte[] expected;
        try {
            salt = Base64.getDecoder().decode(parts[2]);
            expected = Base64.getDecoder().decode(parts[3]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (salt.length == 0 || expected.length == 0) {
            return false;
        }
        byte[] actual = pbkdf2(password.toCharArray(), salt, iterations, expected.length);
        return MessageDigest.isEqual(expected, actual);
    }

    /**
     * Burns the same amount of work as a real verification for unknown users,
     * avoiding a user-enumeration timing side channel.
     */
    public static void burnPasswordCheck(String password) {
        byte[] salt = new byte[SALT_BYTES];
        pbkdf2((password == null ? "" : password).toCharArray(), salt,
                PBKDF2_ITERATIONS, KEY_BYTES);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyBytes) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, keyBytes * 8);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** Generates {@code count} random bytes from a {@link SecureRandom}. */
    public static byte[] randomBytes(int count) {
        byte[] out = new byte[count];
        RANDOM.nextBytes(out);
        return out;
    }

    /** Lower-case hex encoding of random bytes. */
    public static String randomHex(int bytes) {
        byte[] raw = randomBytes(bytes);
        StringBuilder out = new StringBuilder(raw.length * 2);
        for (byte b : raw) {
            out.append(Character.forDigit((b >> 4) & 0xF, 16));
            out.append(Character.forDigit(b & 0xF, 16));
        }
        return out.toString();
    }

    /** Base64url without padding, as mandated for the token payload/signature parts. */
    public static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /** Decodes base64url without padding; throws IllegalArgumentException if malformed. */
    public static byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    /** HMAC-SHA256 over {@code data}, returned as base64url (no padding). */
    public static String hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return base64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HmacSHA256 is unavailable", e);
        }
    }

    /** Constant-time comparison of two strings (UTF-8 bytes). */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Creates a card code in the DESIGN format {@code PREFIX-XXXX-XXXX-XXXX},
     * using a Base32-ish alphabet without easily confused characters
     * ({@code I}, {@code O}, {@code 0}, {@code 1} are excluded). 12 random
     * characters give ~57 bits of entropy per code.
     */
    public static String newCardCode(String prefix) {
        String cleanPrefix = normalizePrefix(prefix);
        StringBuilder out = new StringBuilder(cleanPrefix.length() + 15);
        out.append(cleanPrefix);
        for (int group = 0; group < 3; group++) {
            out.append('-');
            for (int i = 0; i < 4; i++) {
                out.append(BASE32[RANDOM.nextInt(BASE32.length)]);
            }
        }
        return out.toString();
    }

    /** Uppercases the alphanumeric characters of a software id to form a card prefix. */
    public static String normalizePrefix(String value) {
        StringBuilder out = new StringBuilder();
        if (value != null) {
            for (int i = 0; i < value.length() && out.length() < 8; i++) {
                char c = Character.toUpperCase(value.charAt(i));
                if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                    out.append(c);
                }
            }
        }
        return out.length() == 0 ? "ZIPIT" : out.toString();
    }

    /** Generates a random identifier such as {@code u-1a2b3c4d5e6f}. */
    public static String newId(String prefix) {
        return prefix + "-" + randomHex(6);
    }
}
