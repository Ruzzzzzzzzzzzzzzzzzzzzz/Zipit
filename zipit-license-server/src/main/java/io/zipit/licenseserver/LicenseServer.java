package io.zipit.licenseserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import io.zipit.licenseserver.auth.AuthApi;
import io.zipit.licenseserver.auth.AuthStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.security.KeyStore;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Local HTTP license server with an admin page.
 *
 * <p>Endpoints:</p>
 * <ul>
 *   <li>{@code GET /} admin UI (enterprise licenses + multi-software auth console)</li>
 *   <li>{@code GET /api/health}</li>
 *   <li>{@code GET /api/public-key}</li>
 *   <li>{@code GET|POST /api/licenses}</li>
 *   <li>{@code GET /api/licenses/{id}} / {@code /download} / {@code POST /revoke}</li>
 *   <li>{@code POST /api/validate}</li>
 *   <li>{@code GET|POST /api/auth/*} client auth API (DESIGN.md section 2.1)</li>
 *   <li>{@code GET|POST /api/admin/*} admin auth API (DESIGN.md section 2.2)</li>
 * </ul>
 */
public final class LicenseServer {

    /** Default token lifetime in days (DESIGN.md section 1). */
    public static final int DEFAULT_TOKEN_DAYS = 7;

    private final String host;
    private final int port;
    private final LicenseStore store;
    private final String adminToken;
    private final String keystore;
    private final String storepass;
    private final String publicUrl;
    private final int tokenDays;
    private final boolean legacyEnabled;
    private final AuthStore authStore;
    private HttpServer server;
    private ExecutorService executor;

    public LicenseServer(String host, int port, LicenseStore store) {
        this(host, port, store, "");
    }

    public LicenseServer(String host, int port, LicenseStore store, String adminToken) {
        this(host, port, store, adminToken, "", "", "");
    }

    public LicenseServer(String host, int port, LicenseStore store, String adminToken,
                         String keystore, String storepass) {
        this(host, port, store, adminToken, keystore, storepass, "");
    }

    public LicenseServer(String host, int port, LicenseStore store, String adminToken,
                         String keystore, String storepass, String publicUrl) {
        this(host, port, store, adminToken, keystore, storepass, publicUrl,
                DEFAULT_TOKEN_DAYS, true);
    }

    /** Full constructor used by {@code Main} (and tests that need a token TTL). */
    public LicenseServer(String host, int port, LicenseStore store, String adminToken,
                         String keystore, String storepass, String publicUrl,
                         int tokenDays, boolean legacyEnabled) {
        this.host = host;
        this.port = port;
        this.store = store;
        this.adminToken = adminToken == null ? "" : adminToken;
        this.keystore = keystore == null ? "" : keystore;
        this.storepass = storepass == null ? "" : storepass;
        this.publicUrl = publicUrl == null ? "" : publicUrl;
        this.tokenDays = tokenDays;
        this.legacyEnabled = legacyEnabled;
        try {
            this.authStore = new AuthStore(store.dataDir(), tokenDays);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to initialize multi-software auth data: " + e.getMessage(), e);
        }
    }

    /** Public base URL used for client configuration snippets. */
    public String publicUrl() {
        if (!publicUrl.trim().isEmpty()) {
            String value = publicUrl.trim();
            while (value.endsWith("/")) {
                value = value.substring(0, value.length() - 1);
            }
            return value;
        }
        String scheme = keystore.isEmpty() ? "http" : "https";
        String displayHost = "0.0.0.0".equals(host) ? "127.0.0.1" : host;
        return scheme + "://" + displayHost + ":" + port();
    }

    /** Persistent multi-software authorization store (users/software/cards/memberships). */
    public AuthStore authStore() {
        return authStore;
    }

    /** Whether the legacy RSA enterprise license routes are enabled. */
    public boolean legacyEnabled() {
        return legacyEnabled;
    }

    /** Configured token lifetime in days. */
    public int tokenDays() {
        return tokenDays;
    }

    /** Starts the server and returns the bound port (useful with port 0). */
    public int start() throws IOException {
        if (!keystore.isEmpty()) {
            try (InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Paths.get(keystore))) {
                KeyStore keyStore = KeyStore.getInstance("JKS");
                keyStore.load(in, storepass.toCharArray());
                KeyManagerFactory factory = KeyManagerFactory.getInstance(
                        KeyManagerFactory.getDefaultAlgorithm());
                factory.init(keyStore, storepass.toCharArray());
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(factory.getKeyManagers(), null, null);
                HttpsServer https = HttpsServer.create(new InetSocketAddress(host, port), 0);
                https.setHttpsConfigurator(new HttpsConfigurator(context));
                server = https;
            } catch (Exception e) {
                throw new IOException("Unable to initialize TLS: " + e.getMessage(), e);
            }
        } else {
            server = HttpServer.create(new InetSocketAddress(host, port), 0);
        }
        executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);

        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                String path = exchange.getRequestURI().getPath();
                if ("/".equals(path) || "/index.html".equals(path)) {
                    send(exchange, 200, "text/html; charset=UTF-8", adminPage());
                } else {
                    send(exchange, 404, "text/plain; charset=UTF-8", "Not found");
                }
            }
        });
        server.createContext("/api/health", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                send(exchange, 200, "application/json", "{\"status\":\"ok\"}");
            }
        });
        server.createContext("/api/server-info", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                send(exchange, 200, "application/json",
                        "{\"publicUrl\":" + Json.quote(publicUrl())
                                + ",\"version\":\"1.0.0\""
                                + ",\"validateEndpoint\":" + Json.quote(publicUrl() + "/api/validate")
                                + ",\"adminTokenRequired\":" + Json.bool(!adminToken.isEmpty())
                                + ",\"auth\":true"
                                + ",\"tokenDays\":" + tokenDays
                                + ",\"legacy\":" + Json.bool(legacyEnabled)
                                + ",\"software\":" + authStore.softwareList().size()
                                + "}");
            }
        });
        server.createContext("/api/auth", new AuthApi.ClientHandler(authStore));
        server.createContext("/api/admin", new AuthApi.AdminHandler(authStore, adminToken));
        if (legacyEnabled) {
            server.createContext("/api/public-key", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    try {
                        send(exchange, 200, "text/plain; charset=UTF-8", store.publicKeyPem());
                    } catch (Exception e) {
                        send(exchange, 500, "text/plain; charset=UTF-8", e.getMessage());
                    }
                }
            });
            server.createContext("/api/licenses", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    handleLicenses(exchange);
                }
            });
            server.createContext("/api/validate", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    handleValidate(exchange);
                }
            });
        }
        server.start();
        return server.getAddress().getPort();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    public int port() {
        return server == null ? port : server.getAddress().getPort();
    }

    private void handleLicenses(HttpExchange exchange) throws IOException {
        if (!authorized(exchange)) {
            exchange.getResponseHeaders().set("WWW-Authenticate", "X-Admin-Token");
            send(exchange, 401, "application/json",
                    "{\"error\":\"Admin token required\"}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String base = "/api/licenses";
        String method = exchange.getRequestMethod();
        if (base.equals(path) || (base + "/").equals(path)) {
            if ("GET".equals(method)) {
                StringBuilder out = new StringBuilder("[");
                List<LicenseRecord> records = store.list();
                for (int i = 0; i < records.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    out.append(records.get(i).toJson());
                }
                out.append(']');
                send(exchange, 200, "application/json", out.toString());
                return;
            }
            if ("POST".equals(method)) {
                Map<String, String> form = form(exchange.getRequestBody());
                String licensee = form.get("licensee");
                String edition = form.get("edition");
                String expires = form.get("expires");
                if (licensee == null || licensee.trim().isEmpty()
                        || expires == null || expires.trim().isEmpty()) {
                    send(exchange, 400, "application/json",
                            "{\"error\":\"licensee and expires are required\"}");
                    return;
                }
                List<String> features = splitFeatures(form.get("features"));
                try {
                    LicenseStore.Created created = store.create(licensee.trim(),
                            edition == null || edition.trim().isEmpty() ? "enterprise" : edition.trim(),
                            expires.trim(), features);
                    send(exchange, 200, "application/json", creationResponse(created));
                } catch (Exception e) {
                    send(exchange, 500, "application/json",
                            "{\"error\":" + Json.quote(e.getMessage()) + "}");
                }
                return;
            }
            send(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
            return;
        }

        String rest = path.substring(base.length() + 1);
        String[] parts = rest.split("/");
        String id = URLDecoder.decode(parts[0], "UTF-8");
        String action = parts.length > 1 ? parts[1] : "";
        try {
            if ("download".equals(action) && "GET".equals(method)) {
                String yaml = store.licenseYaml(id);
                if (yaml == null) {
                    send(exchange, 404, "text/plain; charset=UTF-8", "Not found");
                    return;
                }
                exchange.getResponseHeaders().set("Content-Disposition",
                        "attachment; filename=\"" + id + ".yml\"");
                send(exchange, 200, "application/x-yaml; charset=UTF-8", yaml);
                return;
            }
            if ("revoke".equals(action) && "POST".equals(method)) {
                boolean revoked = store.revoke(id);
                send(exchange, revoked ? 200 : 404, "application/json",
                        "{\"revoked\":" + revoked + "}");
                return;
            }
            if ("renew".equals(action) && "POST".equals(method)) {
                Map<String, String> form = form(exchange.getRequestBody());
                String expires = form.get("expires");
                if (expires == null || expires.trim().isEmpty()) {
                    send(exchange, 400, "application/json",
                            "{\"error\":\"expires is required\"}");
                    return;
                }
                try {
                    LicenseStore.Created renewed = store.renew(id, expires.trim());
                    send(exchange, 200, "application/json", creationResponse(renewed));
                } catch (IllegalArgumentException e) {
                    send(exchange, 400, "application/json",
                            "{\"error\":" + Json.quote(e.getMessage()) + "}");
                }
                return;
            }
            if ("GET".equals(method)) {
                LicenseRecord record = store.find(id);
                if (record == null) {
                    send(exchange, 404, "application/json", "{\"error\":\"Not found\"}");
                    return;
                }
                send(exchange, 200, "application/json", record.toJson());
                return;
            }
        } catch (Exception e) {
            send(exchange, 500, "application/json",
                    "{\"error\":" + Json.quote(e.getMessage()) + "}");
            return;
        }
        send(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
    }

    private boolean authorized(HttpExchange exchange) {
        if (adminToken.isEmpty()) {
            return true;
        }
        String provided = exchange.getRequestHeaders().getFirst("X-Admin-Token");
        return adminToken.equals(provided);
    }

    private static String creationResponse(LicenseStore.Created created) {
        String record = created.record().toJson();
        return "{" + record.substring(1, record.length() - 1)
                + ",\"license\":" + Json.quote(created.yaml())
                + ",\"download\":" + Json.quote("/api/licenses/" + created.record().id() + "/download")
                + "}";
    }

    private void handleValidate(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            send(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
            return;
        }
        Map<String, String> form = form(exchange.getRequestBody());
        try {
            LicenseStore.Validation validation;
            String license = form.get("license");
            if (license != null && !license.trim().isEmpty()) {
                validation = store.validate(license);
            } else {
                String id = form.get("id");
                if (id == null || id.trim().isEmpty()) {
                    send(exchange, 400, "application/json",
                            "{\"valid\":false,\"reason\":\"license or id is required\"}");
                    return;
                }
                validation = store.validateById(id.trim());
            }
            StringBuilder out = new StringBuilder();
            out.append("{\"valid\":").append(Json.bool(validation.isValid()))
                    .append(",\"reason\":").append(Json.quote(validation.reason()));
            if (validation.info() != null) {
                out.append(",\"id\":").append(Json.quote(validation.info().id()))
                        .append(",\"licensee\":").append(Json.quote(validation.info().licensee()))
                        .append(",\"edition\":").append(Json.quote(validation.info().edition()))
                        .append(",\"expires\":").append(Json.quote(validation.info().expires()))
                        .append(",\"features\":[");
                List<String> features = validation.info().features();
                for (int i = 0; i < features.size(); i++) {
                    if (i > 0) {
                        out.append(',');
                    }
                    out.append(Json.quote(features.get(i)));
                }
                out.append(']');
            }
            out.append('}');
            send(exchange, 200, "application/json", out.toString());
        } catch (Exception e) {
            send(exchange, 500, "application/json",
                    "{\"valid\":false,\"reason\":" + Json.quote(e.getMessage()) + "}");
        }
    }

    private String adminPage() throws IOException {
        try (InputStream in = LicenseServer.class.getResourceAsStream("/admin.html")) {
            if (in == null) {
                return "<html><body><h1>Zipit License Server</h1>"
                        + "<p>admin.html is missing from the server jar.</p></body></html>";
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8)
                    .replace("{{PUBLIC_URL}}", escapeHtml(publicUrl()));
        }
    }

    private static String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static Map<String, String> form(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > 1024 * 1024) {
                throw new IOException("Request body too large");
            }
            out.write(buffer, 0, read);
        }
        Map<String, String> values = new LinkedHashMap<String, String>();
        String body = new String(out.toByteArray(), StandardCharsets.UTF_8);
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            if (equals < 0) {
                values.put(URLDecoder.decode(pair, "UTF-8"), "");
            } else {
                values.put(URLDecoder.decode(pair.substring(0, equals), "UTF-8"),
                        URLDecoder.decode(pair.substring(equals + 1), "UTF-8"));
            }
        }
        return values;
    }

    private static List<String> splitFeatures(String value) {
        List<String> out = new ArrayList<String>();
        if (value == null || value.trim().isEmpty()) {
            return out;
        }
        for (String feature : value.split(",")) {
            String trimmed = feature.trim();
            if (!trimmed.isEmpty() && !out.contains(trimmed)) {
                out.add(trimmed);
            }
        }
        return out;
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
