package io.zipit.licenseserver;

import io.zipit.core.enterprise.LicenseInfo;
import io.zipit.core.enterprise.LicenseTool;
import io.zipit.core.enterprise.LicenseValidator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * File-backed license registry and signing service.
 *
 * <p>Keys and issued licenses live under the data directory. The private key
 * never leaves the server; only the public key is exposed to clients.</p>
 */
public final class LicenseStore {

    private final Path dataDir;
    private final Path licensesDir;
    private final Path privateKeyFile;
    private final Path publicKeyFile;
    private final Map<String, LicenseRecord> records = new LinkedHashMap<String, LicenseRecord>();
    private final PublicKey publicKey;
    private final Random random = new Random();

    public LicenseStore(Path dataDir) throws Exception {
        this.dataDir = dataDir.toAbsolutePath();
        this.licensesDir = this.dataDir.resolve("licenses");
        this.privateKeyFile = this.dataDir.resolve("private.pem");
        this.publicKeyFile = this.dataDir.resolve("public.pem");
        Files.createDirectories(licensesDir);
        if (!Files.exists(privateKeyFile) || !Files.exists(publicKeyFile)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            Files.write(privateKeyFile, LicenseTool.pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
            Files.write(publicKeyFile, LicenseTool.pem("PUBLIC KEY", pair.getPublic().getEncoded()));
        }
        this.publicKey = LicenseValidator.readPublicKey(publicKeyFile);
        load();
    }

    public synchronized Created create(String licensee, String edition, String expires,
                                       List<String> features) throws Exception {
        String id = "lic-" + Long.toHexString(System.currentTimeMillis()) + "-"
                + Integer.toHexString(random.nextInt());
        List<String> cleanFeatures = new ArrayList<String>(new LinkedHashSet<String>(features));
        Path unsigned = dataDir.resolve(id + ".unsigned.yml");
        Path signed = licensesDir.resolve(id + ".yml");
        Files.write(unsigned, unsignedYaml(id, licensee, edition, expires, cleanFeatures)
                .getBytes(StandardCharsets.UTF_8));
        LicenseTool.sign(unsigned, privateKeyFile, signed);
        Files.deleteIfExists(unsigned);
        String yaml = new String(Files.readAllBytes(signed), StandardCharsets.UTF_8);
        LicenseRecord record = new LicenseRecord(id, licensee, edition, expires,
                cleanFeatures, false, System.currentTimeMillis());
        records.put(id, record);
        persist();
        return new Created(record, yaml);
    }

    public synchronized LicenseRecord find(String id) {
        return records.get(id);
    }

    public synchronized List<LicenseRecord> list() {
        List<LicenseRecord> out = new ArrayList<LicenseRecord>(records.values());
        Collections.sort(out, new Comparator<LicenseRecord>() {
            @Override
            public int compare(LicenseRecord a, LicenseRecord b) {
                return Long.compare(b.createdAt(), a.createdAt());
            }
        });
        return out;
    }

    public synchronized Created renew(String id, String newExpires) throws Exception {
        LicenseRecord record = records.get(id);
        if (record == null) {
            throw new IllegalArgumentException("Unknown license id");
        }
        if (record.revoked()) {
            throw new IllegalArgumentException("License is revoked");
        }
        Path unsigned = dataDir.resolve(id + ".renew.yml");
        Path signed = licensesDir.resolve(id + ".yml");
        Files.write(unsigned, unsignedYaml(id, record.licensee(), record.edition(),
                newExpires, record.features()).getBytes(StandardCharsets.UTF_8));
        LicenseTool.sign(unsigned, privateKeyFile, signed);
        Files.deleteIfExists(unsigned);
        String yaml = new String(Files.readAllBytes(signed), StandardCharsets.UTF_8);
        LicenseRecord updated = record.withExpires(newExpires);
        records.put(id, updated);
        persist();
        return new Created(updated, yaml);
    }

    /**
     * Renews active licenses expiring within {@code withinDays} by
     * {@code extensionDays}. Returns the number of renewed licenses.
     */
    public synchronized int autoRenew(int withinDays, int extensionDays) throws Exception {
        if (withinDays <= 0 || extensionDays <= 0) {
            return 0;
        }
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd");
        format.setLenient(false);
        java.util.Date now = new java.util.Date();
        java.util.Calendar threshold = java.util.Calendar.getInstance();
        threshold.setTime(now);
        threshold.add(java.util.Calendar.DAY_OF_MONTH, withinDays);
        java.util.List<String> renewIds = new ArrayList<String>();
        for (LicenseRecord record : records.values()) {
            if (record.revoked()) {
                continue;
            }
            java.util.Date expires;
            try {
                expires = format.parse(record.expires());
            } catch (Exception e) {
                continue;
            }
            if (expires.after(threshold.getTime())) {
                continue;
            }
            renewIds.add(record.id());
        }
        int renewed = 0;
        for (String id : renewIds) {
            LicenseRecord record = records.get(id);
            java.util.Date expires;
            try {
                expires = format.parse(record.expires());
            } catch (Exception e) {
                continue;
            }
            java.util.Calendar base = java.util.Calendar.getInstance();
            base.setTime(expires.after(now) ? expires : now);
            base.add(java.util.Calendar.DAY_OF_MONTH, extensionDays);
            renew(id, format.format(base.getTime()));
            renewed++;
        }
        return renewed;
    }

    public synchronized boolean revoke(String id) throws Exception {
        LicenseRecord record = records.get(id);
        if (record == null) {
            return false;
        }
        records.put(id, record.withRevoked(true));
        persist();
        return true;
    }

    public synchronized String licenseYaml(String id) throws Exception {
        Path file = licensesDir.resolve(id + ".yml");
        if (!Files.exists(file)) {
            return null;
        }
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    public synchronized Validation validate(String yaml) {
        LicenseInfo info = LicenseValidator.validateText(yaml, publicKey);
        if (!info.id().isEmpty()) {
            LicenseRecord record = records.get(info.id());
            if (record != null && record.revoked()) {
                return new Validation(false, "License revoked");
            }
        }
        String reason = info.isValid() ? "OK" : info.error();
        return new Validation(info.isValid(), reason, info);
    }

    public synchronized Validation validateById(String id) throws Exception {
        LicenseRecord record = records.get(id);
        if (record == null) {
            return new Validation(false, "Unknown license id");
        }
        if (record.revoked()) {
            return new Validation(false, "License revoked");
        }
        String yaml = licenseYaml(id);
        if (yaml == null) {
            return new Validation(false, "License file missing");
        }
        LicenseInfo info = LicenseValidator.validateText(yaml, publicKey);
        if (!id.equals(info.id())) {
            return new Validation(false, "License id mismatch");
        }
        return new Validation(info.isValid(), info.isValid() ? "OK" : info.error(), info);
    }

    public String publicKeyPem() throws Exception {
        return new String(Files.readAllBytes(publicKeyFile), StandardCharsets.UTF_8);
    }

    public Path dataDir() {
        return dataDir;
    }

    private void load() throws Exception {
        Path file = dataDir.resolve("records.tsv");
        if (!Files.exists(file)) {
            return;
        }
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            LicenseRecord record = LicenseRecord.fromTsv(line);
            if (record != null) {
                records.put(record.id(), record);
            }
        }
    }

    private void persist() throws Exception {
        StringBuilder out = new StringBuilder();
        for (LicenseRecord record : records.values()) {
            out.append(record.toTsv()).append('\n');
        }
        Path file = dataDir.resolve("records.tsv");
        Path tmp = dataDir.resolve("records.tsv.tmp");
        Files.write(tmp, out.toString().getBytes(StandardCharsets.UTF_8));
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String unsignedYaml(String id, String licensee, String edition,
                                       String expires, List<String> features) {
        StringBuilder out = new StringBuilder();
        out.append("id: ").append(quote(id)).append('\n');
        out.append("licensee: ").append(quote(licensee)).append('\n');
        out.append("edition: ").append(quote(edition)).append('\n');
        out.append("expires: ").append(quote(expires)).append('\n');
        out.append("features:\n");
        for (String feature : features) {
            out.append("  - ").append(quote(feature)).append('\n');
        }
        return out.toString();
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** Result of issuing a license. */
    public static final class Created {
        private final LicenseRecord record;
        private final String yaml;

        Created(LicenseRecord record, String yaml) {
            this.record = record;
            this.yaml = yaml;
        }

        public LicenseRecord record() { return record; }
        public String yaml() { return yaml; }
    }

    /** Result of an online validation. */
    public static final class Validation {
        private final boolean valid;
        private final String reason;
        private final LicenseInfo info;

        Validation(boolean valid, String reason) {
            this(valid, reason, null);
        }

        Validation(boolean valid, String reason, LicenseInfo info) {
            this.valid = valid;
            this.reason = reason == null ? "" : reason;
            this.info = info;
        }

        public boolean isValid() { return valid; }
        public String reason() { return reason; }
        public LicenseInfo info() { return info; }
    }
}
