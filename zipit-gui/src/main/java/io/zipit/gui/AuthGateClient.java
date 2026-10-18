package io.zipit.gui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tiny HTTP client used by the Enterprise page to validate an Auth Gate server.
 *
 * <p>Deliberately dependency free (HttpURLConnection + a counting regex instead
 * of a JSON library), mirroring the runtime Auth Gate implementation.</p>
 */
final class AuthGateClient {

    private static final Pattern ID_FIELD = Pattern.compile("\"id\"\\s*:");
    private static final Pattern OK_TRUE = Pattern.compile("\"ok\"\\s*:\\s*true");

    private AuthGateClient() {
    }

    static final class Result {
        final boolean ok;
        final int httpCode;
        final int softwareCount;
        final String message;
        final String raw;

        Result(boolean ok, int httpCode, int softwareCount, String message, String raw) {
            this.ok = ok;
            this.httpCode = httpCode;
            this.softwareCount = softwareCount;
            this.message = message;
            this.raw = raw;
        }
    }

    /** Normalizes a server URL: trims, adds a scheme when missing and a trailing slash. */
    static String normalize(String server) {
        String value = server == null ? "" : server.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    /**
     * Probes {@code /api/health} (optional) and {@code /api/auth/software}.
     *
     * @return a result with HTTP status, number of registered software entries and
     *         a human readable inline message
     */
    static Result test(String server, int timeoutMs) {
        String base = normalize(server);
        if (base.isEmpty()) {
            return new Result(false, 0, 0, I18n.t("auth.msg.emptyServer"), "");
        }
        int timeout = Math.max(500, timeoutMs);
        int healthCode;
        try {
            healthCode = request(base + "/api/health", timeout)[0];
        } catch (Exception ignored) {
            healthCode = -1;
        }
        try {
            Object[] response = requestWithBody(base + "/api/auth/software", timeout);
            int code = ((Integer) response[0]).intValue();
            String body = (String) response[1];
            int count = 0;
            Matcher matcher = ID_FIELD.matcher(body);
            while (matcher.find()) {
                count++;
            }
            boolean ok = code == 200 && (OK_TRUE.matcher(body).find() || count > 0);
            String connected = I18n.t("auth.msg.connected", code, count);
            String message = ok
                    ? (healthCode > 0
                    ? I18n.t("auth.msg.healthOk", connected, healthCode)
                    : I18n.t("auth.msg.healthMissing", connected))
                    : I18n.t("auth.msg.unexpected", code);
            return new Result(ok, code, count, message, abbreviate(body));
        } catch (Exception e) {
            String reason = e.getMessage();
            if (reason == null || reason.trim().isEmpty()) {
                reason = e.getClass().getSimpleName();
            }
            return new Result(false, -1, 0, I18n.t("auth.msg.unreachable", base, reason), "");
        }
    }

    private static int[] request(String url, int timeoutMs) throws Exception {
        Object[] result = requestWithBody(url, timeoutMs);
        return new int[]{((Integer) result[0]).intValue()};
    }

    private static Object[] requestWithBody(String url, int timeoutMs) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Zipit-GUI/1.0");
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            return new Object[]{Integer.valueOf(code), body};
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readAll(InputStream stream) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[2048];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            out.append(buffer, 0, read);
            if (out.length() > 8192) {
                break;
            }
        }
        reader.close();
        return out.toString();
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        String singleLine = body.replace('\n', ' ').replace('\r', ' ').trim();
        return singleLine.length() <= 150 ? singleLine : singleLine.substring(0, 150) + "...";
    }
}
