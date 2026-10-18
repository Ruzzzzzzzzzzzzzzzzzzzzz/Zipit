package io.zipit.core.test;

import io.zipit.core.util.Cipher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class CipherTest {

    @Test
    void roundTripsUtf8() {
        String[] values = {"", "hello", "中文-测试", "\u0000ÿ", "a longer string with spaces"};
        for (String value : values) {
            String encrypted = Cipher.encrypt(value, 0x123456789ABCDEFL);
            assertEquals(value, Cipher.decrypt(encrypted, 0x123456789ABCDEFL));
        }
    }

    @Test
    void differentKeysProduceDifferentPayloads() {
        String a = Cipher.encrypt("payload", 1L);
        String b = Cipher.encrypt("payload", 2L);
        assertNotEquals(a, b);
    }

    @Test
    void zeroKeyFallsBackToConstant() {
        String encrypted = Cipher.encrypt("payload", 0L);
        assertEquals("payload", Cipher.decrypt(encrypted, 0L));
    }
}
