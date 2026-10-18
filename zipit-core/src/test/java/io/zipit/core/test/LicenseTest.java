package io.zipit.core.test;

import io.zipit.core.enterprise.LicenseInfo;
import io.zipit.core.enterprise.LicenseTool;
import io.zipit.core.enterprise.LicenseValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseTest {

    @TempDir
    Path temp;

    @Test
    void signsAndValidatesLicense() throws Exception {
        KeyPair pair = keyPair();
        Path privateKey = temp.resolve("private.pem");
        Path publicKey = temp.resolve("public.pem");
        Files.write(privateKey, LicenseTool.pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.write(publicKey, LicenseTool.pem("PUBLIC KEY", pair.getPublic().getEncoded()));

        Path unsigned = temp.resolve("license.yml");
        Files.write(unsigned, license("2030-12-31").getBytes(StandardCharsets.UTF_8));
        Path signed = temp.resolve("license-signed.yml");
        LicenseTool.sign(unsigned, privateKey, signed);

        LicenseInfo info = LicenseValidator.validate(signed, publicKey);
        assertTrue(info.isValid(), info.error());
        assertTrue(info.isSignatureVerified());
        assertTrue(info.hasFeature("audit"));
        assertTrue(info.hasFeature("policy"));

        LicenseInfo withoutKey = LicenseValidator.validate(signed);
        assertFalse(withoutKey.isValid());
        assertTrue(withoutKey.error().contains("Public key"));
    }

    @Test
    void rejectsExpiredAndTamperedLicenses() throws Exception {
        KeyPair pair = keyPair();
        Path privateKey = temp.resolve("private.pem");
        Path publicKey = temp.resolve("public.pem");
        Files.write(privateKey, LicenseTool.pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
        Files.write(publicKey, LicenseTool.pem("PUBLIC KEY", pair.getPublic().getEncoded()));

        Path expired = temp.resolve("expired.yml");
        Files.write(expired, license("2000-01-01").getBytes(StandardCharsets.UTF_8));
        Path expiredSigned = temp.resolve("expired-signed.yml");
        LicenseTool.sign(expired, privateKey, expiredSigned);
        LicenseInfo expiredInfo = LicenseValidator.validate(expiredSigned, publicKey);
        assertFalse(expiredInfo.isValid());
        assertTrue(expiredInfo.error().contains("expired"));

        Path unsigned = temp.resolve("tamper.yml");
        Files.write(unsigned, license("2030-12-31").getBytes(StandardCharsets.UTF_8));
        Path signed = temp.resolve("tamper-signed.yml");
        LicenseTool.sign(unsigned, privateKey, signed);
        String text = new String(Files.readAllBytes(signed), StandardCharsets.UTF_8);
        Files.write(signed, text.replace("Acme", "Evil").getBytes(StandardCharsets.UTF_8));
        LicenseInfo tampered = LicenseValidator.validate(signed, publicKey);
        assertFalse(tampered.isValid());
        assertTrue(tampered.error().contains("Signature"));
    }

    private static KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String license(String expires) {
        return "licensee: \"Acme\"\n"
                + "edition: \"enterprise\"\n"
                + "expires: \"" + expires + "\"\n"
                + "features:\n"
                + "  - audit\n"
                + "  - policy\n";
    }
}
