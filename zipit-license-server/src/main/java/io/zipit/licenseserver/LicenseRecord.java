package io.zipit.licenseserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Metadata for one issued license. */
public final class LicenseRecord {

    private final String id;
    private final String licensee;
    private final String edition;
    private final String expires;
    private final List<String> features;
    private final boolean revoked;
    private final long createdAt;

    public LicenseRecord(String id, String licensee, String edition, String expires,
                         List<String> features, boolean revoked, long createdAt) {
        this.id = id;
        this.licensee = licensee;
        this.edition = edition;
        this.expires = expires;
        this.features = Collections.unmodifiableList(new ArrayList<String>(features));
        this.revoked = revoked;
        this.createdAt = createdAt;
    }

    public String id() { return id; }
    public String licensee() { return licensee; }
    public String edition() { return edition; }
    public String expires() { return expires; }
    public List<String> features() { return features; }
    public boolean revoked() { return revoked; }
    public long createdAt() { return createdAt; }

    LicenseRecord withRevoked(boolean value) {
        return new LicenseRecord(id, licensee, edition, expires, features, value, createdAt);
    }

    LicenseRecord withExpires(String value) {
        return new LicenseRecord(id, licensee, edition, value, features, revoked, createdAt);
    }

    String toJson() {
        StringBuilder out = new StringBuilder();
        out.append("{\"id\":").append(Json.quote(id))
                .append(",\"licensee\":").append(Json.quote(licensee))
                .append(",\"edition\":").append(Json.quote(edition))
                .append(",\"expires\":").append(Json.quote(expires))
                .append(",\"features\":[");
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(Json.quote(features.get(i)));
        }
        out.append("],\"revoked\":").append(Json.bool(revoked))
                .append(",\"createdAt\":").append(createdAt)
                .append('}');
        return out.toString();
    }

    String toTsv() {
        StringBuilder featuresValue = new StringBuilder();
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) {
                featuresValue.append(',');
            }
            featuresValue.append(features.get(i));
        }
        return clean(id) + '\t' + clean(licensee) + '\t' + clean(edition) + '\t'
                + clean(expires) + '\t' + clean(featuresValue.toString()) + '\t'
                + (revoked ? "1" : "0") + '\t' + createdAt;
    }

    static LicenseRecord fromTsv(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length < 7) {
            return null;
        }
        List<String> features = new ArrayList<String>();
        if (!parts[4].isEmpty()) {
            for (String feature : parts[4].split(",")) {
                if (!feature.trim().isEmpty()) {
                    features.add(feature.trim());
                }
            }
        }
        long created;
        try {
            created = Long.parseLong(parts[6]);
        } catch (NumberFormatException e) {
            created = 0L;
        }
        return new LicenseRecord(parts[0], parts[1], parts[2], parts[3], features,
                "1".equals(parts[5]), created);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
