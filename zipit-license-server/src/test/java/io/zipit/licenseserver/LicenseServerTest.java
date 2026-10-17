package io.zipit.licenseserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LicenseServerTest {

    @TempDir
    Path temp;

    @Test
    void issuesValidatesAndRevokesLicenses() throws Exception {
        LicenseStore store = new LicenseStore(temp);
        LicenseServer server = new LicenseServer("127.0.0.1", 0, store);
        int port = server.start();
        try {
            String base = "http://127.0.0.1:" + port;
            assertTrue(get(base + "/api/health").contains("ok"));

            String form = "licensee=" + enc("Acme Corp")
                    + "&edition=enterprise&expires=2030-12-31&features=audit,policy";
            String created = post(base + "/api/licenses", form);
            String id = jsonString(created, "id");
            String license = jsonString(created, "license");
            assertNotNull(id);
            assertTrue(id.startsWith("lic-"), created);
            assertTrue(license.contains("signature:"), license);
            assertTrue(license.contains("id:"), license);

            String validated = post(base + "/api/validate",
                    "license=" + URLEncoder.encode(license, "UTF-8"));
            assertTrue(validated.contains("\"valid\":true"), validated);

            String revoke = post(base + "/api/licenses/" + enc(id) + "/revoke", "");
            assertTrue(revoke.contains("\"revoked\":true"), revoke);

            String revoked = post(base + "/api/validate",
                    "license=" + URLEncoder.encode(license, "UTF-8"));
            assertTrue(revoked.contains("\"valid\":false"), revoked);
            assertTrue(revoked.contains("revoked"), revoked);

            String byId = post(base + "/api/validate", "id=" + enc(id));
            assertTrue(byId.contains("\"valid\":false"), byId);

            String list = get(base + "/api/licenses");
            assertTrue(list.contains(id), list);
            String publicKey = get(base + "/api/public-key");
            assertTrue(publicKey.contains("BEGIN PUBLIC KEY"), publicKey);
            String page = get(base + "/");
            assertTrue(page.contains("ZIPIT LICENSE SERVER"), page);
        } finally {
            server.stop();
        }
    }

    @Test
    void exposesConfigurablePublicUrlForClients() throws Exception {
        LicenseStore store = new LicenseStore(temp.resolve("public-url"));
        LicenseServer server = new LicenseServer("127.0.0.1", 0, store, "", "", "",
                "https://licenses.example.com/");
        int port = server.start();
        try {
            String base = "http://127.0.0.1:" + port;
            String info = get(base + "/api/server-info");
            assertTrue(info.contains("https://licenses.example.com"), info);
            assertTrue(info.contains("/api/validate"), info);

            String page = get(base + "/");
            assertTrue(page.contains("https://licenses.example.com"), page);
            assertFalse(page.contains("{{PUBLIC_URL}}"), page);
            assertTrue(page.contains("Client setup"), page);
        } finally {
            server.stop();
        }
    }

    @Test
    void autoRenewsExpiringLicenses() throws Exception {
        LicenseStore store = new LicenseStore(temp.resolve("autorenew"));
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd");
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.add(java.util.Calendar.DAY_OF_MONTH, 2);
        String soon = format.format(calendar.getTime());
        LicenseStore.Created created = store.create("Acme", "enterprise", soon,
                java.util.Arrays.asList("audit"));
        int renewed = store.autoRenew(30, 365);
        assertTrue(renewed >= 1);
        LicenseStore.Validation validation = store.validateById(created.record().id());
        assertTrue(validation.isValid(), validation.reason());
        assertFalse(soon.equals(store.find(created.record().id()).expires()));
    }

    @Test
    void tokenAuthAndRenewal() throws Exception {
        LicenseStore store = new LicenseStore(temp.resolve("tokens"));
        LicenseServer server = new LicenseServer("127.0.0.1", 0, store, "s3cret");
        int port = server.start();
        try {
            String base = "http://127.0.0.1:" + port;
            assertEquals(401, request("POST", base + "/api/licenses",
                    "licensee=Acme&expires=2030-01-01", "").status);
            assertEquals(401, request("GET", base + "/api/licenses", null, "").status);

            Response created = request("POST", base + "/api/licenses",
                    "licensee=Acme&expires=2030-01-01&features=audit", "s3cret");
            assertEquals(200, created.status);
            String id = jsonString(created.body, "id");
            assertNotNull(id);

            Response listed = request("GET", base + "/api/licenses", null, "s3cret");
            assertEquals(200, listed.status);
            assertTrue(listed.body.contains(id), listed.body);

            Response renewed = request("POST", base + "/api/licenses/" + enc(id) + "/renew",
                    "expires=2035-01-01", "s3cret");
            assertEquals(200, renewed.status);
            assertTrue(renewed.body.contains("2035-01-01"), renewed.body);
            String renewedLicense = jsonString(renewed.body, "license");
            Response valid = request("POST", base + "/api/validate",
                    "license=" + URLEncoder.encode(renewedLicense, "UTF-8"), "");
            assertEquals(200, valid.status);
            assertTrue(valid.body.contains("\"valid\":true"), valid.body);

            Response revoked = request("POST", base + "/api/licenses/" + enc(id) + "/revoke",
                    "", "s3cret");
            assertTrue(revoked.body.contains("true"), revoked.body);
        } finally {
            server.stop();
        }
    }

    private static Response request(String method, String url, String form, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        if (token != null && !token.isEmpty()) {
            connection.setRequestProperty("X-Admin-Token", token);
        }
        if (form != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream out = connection.getOutputStream()) {
                out.write(form.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        String body = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        return new Response(status, body);
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        int status = connection.getResponseCode();
        String body = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        assertTrue(status == 200, "HTTP " + status + ": " + body);
        return body;
    }

    private static String post(String url, String form) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        try (OutputStream out = connection.getOutputStream()) {
            out.write(form.getBytes(StandardCharsets.UTF_8));
        }
        int status = connection.getResponseCode();
        String body = read(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        assertTrue(status == 200, "HTTP " + status + ": " + body);
        return body;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String jsonString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        StringBuilder out = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) {
                switch (c) {
                    case 'n': out.append('\n'); break;
                    case 'r': out.append('\r'); break;
                    case 't': out.append('\t'); break;
                    case '"': out.append('"'); break;
                    case '\\': out.append('\\'); break;
                    default: out.append(c);
                }
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return null;
    }
}
