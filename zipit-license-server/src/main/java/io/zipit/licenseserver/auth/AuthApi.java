package io.zipit.licenseserver.auth;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import io.zipit.licenseserver.Json;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HTTP handlers for the multi-software authorization API.
 *
 * <p>{@link ClientHandler} serves the {@code /api/auth/*} endpoints used by the
 * Zipit Auth Gate; {@link AdminHandler} serves {@code /api/admin/*} and requires
 * the {@code X-Admin-Token} header whenever the server was started with
 * {@code --admin-token} (an empty admin token keeps the localhost/testing
 * behaviour of the legacy server: no authentication).</p>
 *
 * <p>Requests are {@code application/x-www-form-urlencoded}; responses are
 * flattened JSON so the injected client can parse them without a JSON library.
 * The shapes follow {@code tools/auth/DESIGN.md} section 2.</p>
 */
public final class AuthApi {

    private static final int MAX_FORM_BYTES = 1024 * 1024;

    private AuthApi() {
    }

    // ==================================================================
    // Client API: /api/auth/*
    // ==================================================================

    /** Public endpoints used by the injected Auth Gate. */
    public static final class ClientHandler implements HttpHandler {

        private final AuthStore store;

        public ClientHandler(AuthStore store) {
            this.store = store;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (preflight(exchange)) {
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                if ("/api/auth/software".equals(path)) {
                    if (!"GET".equals(method)) {
                        methodNotAllowed(exchange);
                        return;
                    }
                    handleSoftware(exchange);
                    return;
                }
                if ("POST".equals(method)) {
                    if ("/api/auth/register".equals(path)) {
                        if (rateLimited(exchange)) {
                            return;
                        }
                        handleRegister(exchange);
                        return;
                    }
                    if ("/api/auth/login".equals(path)) {
                        if (rateLimited(exchange)) {
                            return;
                        }
                        handleLogin(exchange);
                        return;
                    }
                    if ("/api/auth/activate".equals(path)) {
                        if (rateLimited(exchange)) {
                            return;
                        }
                        handleActivate(exchange);
                        return;
                    }
                    if ("/api/auth/session".equals(path)) {
                        handleSession(exchange);
                        return;
                    }
                }
                if (isKnownPath(path)) {
                    methodNotAllowed(exchange);
                } else {
                    sendError(exchange, 404, "BAD_REQUEST", "Unknown endpoint");
                }
            } catch (AuthStore.AuthException e) {
                sendError(exchange, e.httpStatus, e.status, e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 500, "SERVER_ERROR", "Internal server error: " + e.getMessage());
            } finally {
                exchange.close();
            }
        }

        private void handleSoftware(HttpExchange exchange) throws IOException, AuthStore.AuthException {
            JsonOut list = JsonOut.array();
            for (AuthStore.Software item : store.enabledSoftware()) {
                list.item(JsonOut.object()
                        .add("id", item.id)
                        .add("name", item.name)
                        .add("group", item.group));
            }
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .raw("software", list.toString())
                    .toString());
        }

        private void handleRegister(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            Map<String, String> form = form(exchange.getRequestBody());
            AuthStore.Registered registered = store.register(form.get("username"),
                    form.get("password"));
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .add("status", "REGISTERED")
                    .add("userId", registered.user.id)
                    .add("username", registered.user.username)
                    .toString());
        }

        private void handleLogin(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            Map<String, String> form = form(exchange.getRequestBody());
            AuthStore.LoginResult result = store.login(form.get("username"),
                    form.get("password"), form.get("software"), form.get("hwid"));
            sendJson(exchange, 200, tokenResponse(result).toString());
        }

        private void handleActivate(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            Map<String, String> form = form(exchange.getRequestBody());
            AuthStore.LoginResult result = store.activate(form.get("username"),
                    form.get("password"), form.get("card"), form.get("software"),
                    form.get("hwid"));
            sendJson(exchange, 200, tokenResponse(result).toString());
        }

        private void handleSession(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            Map<String, String> form = form(exchange.getRequestBody());
            String software = form.get("software");
            if (software == null || software.trim().isEmpty()) {
                throw new AuthStore.AuthException("BAD_REQUEST", 400, "software is required");
            }
            AuthStore.SessionResult result = store.session(form.get("token"), software,
                    form.get("hwid"));
            JsonOut out = JsonOut.object().add("valid", result.valid);
            if (result.valid) {
                out.add("status", "OK")
                        .add("username", result.user.username)
                        .add("userId", result.user.id)
                        .add("software", result.software.id)
                        .add("softwareName", result.software.name)
                        .add("group", result.membership.group)
                        .add("expiresAt", result.expiresAt);
            } else {
                out.add("status", result.status).add("reason", result.reason);
            }
            sendJson(exchange, 200, out.toString());
        }

        private JsonOut tokenResponse(AuthStore.LoginResult result) {
            JsonOut out = JsonOut.object()
                    .add("ok", true)
                    .add("status", result.status)
                    .add("software", result.software.id)
                    .add("softwareName", result.software.name)
                    .add("username", result.user.username)
                    .add("userId", result.user.id);
            if (result.isOk()) {
                out.add("token", result.token)
                        .add("group", result.membership.group)
                        .add("expiresAt", result.expiresAt);
            }
            return out;
        }

        private boolean rateLimited(HttpExchange exchange) throws IOException {
            if (!store.allowAuthAttempt(remoteIp(exchange))) {
                sendError(exchange, 429, "RATE_LIMITED",
                        "Too many authorization attempts from this IP; try again later");
                return true;
            }
            return false;
        }

        private static boolean isKnownPath(String path) {
            return "/api/auth/software".equals(path) || "/api/auth/register".equals(path)
                    || "/api/auth/login".equals(path) || "/api/auth/activate".equals(path)
                    || "/api/auth/session".equals(path);
        }
    }

    // ==================================================================
    // Admin API: /api/admin/*
    // ==================================================================

    /** Admin endpoints; all of them honour {@code X-Admin-Token}. */
    public static final class AdminHandler implements HttpHandler {

        private final AuthStore store;
        private final String adminToken;

        public AdminHandler(AuthStore store, String adminToken) {
            this.store = store;
            this.adminToken = adminToken == null ? "" : adminToken;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (preflight(exchange)) {
                    return;
                }
                if (!authorized(exchange)) {
                    store.recordAudit("admin.unauthorized", "", "",
                            "path=" + exchange.getRequestURI().getPath());
                    exchange.getResponseHeaders().set("WWW-Authenticate", "X-Admin-Token");
                    sendError(exchange, 401, "UNAUTHORIZED", "Admin token required");
                    return;
                }
                String path = exchange.getRequestURI().getPath();
                String prefix = "/api/admin";
                String rest = path.length() > prefix.length()
                        ? path.substring(prefix.length()) : "";
                String[] segments = rest.isEmpty()
                        ? new String[0] : rest.substring(1).split("/", -1);
                String[] decoded = new String[segments.length];
                for (int i = 0; i < segments.length; i++) {
                    decoded[i] = URLDecoder.decode(segments[i], "UTF-8");
                }
                String method = exchange.getRequestMethod();
                if (decoded.length == 1 && "overview".equals(decoded[0]) && "GET".equals(method)) {
                    handleOverview(exchange);
                    return;
                }
                if (decoded.length == 1 && "audit".equals(decoded[0]) && "GET".equals(method)) {
                    handleAudit(exchange);
                    return;
                }
                if (decoded.length >= 1 && "software".equals(decoded[0])) {
                    if (decoded.length == 1) {
                        if ("GET".equals(method)) {
                            handleSoftwareList(exchange);
                            return;
                        }
                        if ("POST".equals(method)) {
                            handleSoftwareCreate(exchange);
                            return;
                        }
                    } else if (decoded.length == 3 && "toggle".equals(decoded[2])
                            && "POST".equals(method)) {
                        handleSoftwareToggle(exchange, decoded[1]);
                        return;
                    }
                }
                if (decoded.length >= 1 && "cards".equals(decoded[0])) {
                    if (decoded.length == 1) {
                        if ("GET".equals(method)) {
                            handleCardsList(exchange);
                            return;
                        }
                        if ("POST".equals(method)) {
                            handleCardsCreate(exchange);
                            return;
                        }
                    } else if (decoded.length == 3 && "revoke".equals(decoded[2])
                            && "POST".equals(method)) {
                        handleCardRevoke(exchange, decoded[1]);
                        return;
                    }
                }
                if (decoded.length >= 1 && "users".equals(decoded[0])) {
                    if (decoded.length == 1 && "GET".equals(method)) {
                        handleUsersList(exchange);
                        return;
                    }
                    if (decoded.length == 3 && "toggle".equals(decoded[2])
                            && "POST".equals(method)) {
                        handleUserToggle(exchange, decoded[1]);
                        return;
                    }
                    if (decoded.length == 3 && "hwid".equals(decoded[2])
                            && "POST".equals(method)) {
                        handleUserHwidSet(exchange, decoded[1]);
                        return;
                    }
                    if (decoded.length == 4 && "hwid".equals(decoded[2])
                            && "reset".equals(decoded[3]) && "POST".equals(method)) {
                        handleUserHwidReset(exchange, decoded[1]);
                        return;
                    }
                    if (decoded.length == 5 && "memberships".equals(decoded[2])
                            && "revoke".equals(decoded[4]) && "POST".equals(method)) {
                        handleMembershipRevoke(exchange, decoded[1], decoded[3]);
                        return;
                    }
                }
                if (isKnownAdminPath(decoded)) {
                    methodNotAllowed(exchange);
                } else {
                    sendError(exchange, 404, "BAD_REQUEST", "Unknown admin endpoint");
                }
            } catch (AuthStore.AuthException e) {
                sendError(exchange, e.httpStatus, e.status, e.getMessage());
            } catch (Exception e) {
                sendError(exchange, 500, "SERVER_ERROR", "Internal server error: " + e.getMessage());
            } finally {
                exchange.close();
            }
        }

        private boolean authorized(HttpExchange exchange) {
            if (adminToken.isEmpty()) {
                return true;
            }
            String provided = exchange.getRequestHeaders().getFirst("X-Admin-Token");
            return AuthCrypto.constantTimeEquals(adminToken, provided);
        }

        private void handleOverview(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            AuthStore.Overview overview = store.overview();
            Map<String, String> softwareNames = new LinkedHashMap<String, String>();
            for (AuthStore.Software item : store.softwareList()) {
                softwareNames.put(item.id, item.name);
            }
            JsonOut cards = JsonOut.object()
                    .add("unused", overview.cardsUnused)
                    .add("used", overview.cardsUsed)
                    .add("revoked", overview.cardsRevoked)
                    .add("disabled", overview.cardsDisabled)
                    .add("total", overview.cardsTotal);
            JsonOut recent = JsonOut.array();
            for (AuthStore.Membership membership : overview.recentActivations) {
                String softwareName = softwareNames.get(membership.softwareId);
                recent.item(JsonOut.object()
                        .add("username", membership.username)
                        .add("software", membership.softwareId)
                        .add("softwareName", softwareName == null ? membership.softwareId : softwareName)
                        .add("group", membership.group)
                        .add("activatedAt", membership.activatedAt));
            }
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .add("software", overview.software)
                    .add("users", overview.users)
                    .raw("cards", cards.toString())
                    .add("memberships", overview.memberships)
                    .add("activeMemberships", overview.activeMemberships)
                    .raw("recent", recent.toString())
                    .toString());
        }

        private void handleSoftwareList(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            List<AuthStore.Software> software = store.softwareList();
            List<AuthStore.Membership> memberships = store.memberships();
            List<AuthStore.Card> cards = store.cards();
            JsonOut list = JsonOut.array();
            for (AuthStore.Software item : software) {
                int userCount = 0;
                for (AuthStore.Membership membership : memberships) {
                    if (membership.softwareId.equals(item.id)) {
                        userCount++;
                    }
                }
                int cardCount = 0;
                int unusedCards = 0;
                for (AuthStore.Card card : cards) {
                    if (card.softwareId.equals(item.id)) {
                        cardCount++;
                        if (AuthStore.CARD_UNUSED.equals(card.status)) {
                            unusedCards++;
                        }
                    }
                }
                list.item(softwareJson(item, userCount, cardCount, unusedCards));
            }
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .raw("software", list.toString())
                    .toString());
        }

        private void handleSoftwareCreate(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            Map<String, String> form = form(exchange.getRequestBody());
            AuthStore.Software created = store.createSoftware(form.get("id"), form.get("name"),
                    form.get("group"), form.get("description"));
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .raw("software", softwareJson(created, 0, 0, 0))
                    .toString());
        }

        private void handleSoftwareToggle(HttpExchange exchange, String id)
                throws IOException, AuthStore.AuthException {
            AuthStore.Software updated = store.toggleSoftware(id);
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .raw("software", softwareJson(updated, 0, 0, 0))
                    .toString());
        }

        private void handleCardsList(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            Map<String, String> query = query(exchange);
            String software = query.get("software");
            String status = query.get("status");
            JsonOut list = JsonOut.array();
            for (AuthStore.Card card : store.cards(software, status)) {
                list.item(cardJson(card));
            }
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .add("count", store.cards(software, status).size())
                    .raw("cards", list.toString())
                    .toString());
        }

        private void handleCardsCreate(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            Map<String, String> form = form(exchange.getRequestBody());
            String software = form.get("software");
            int count = parseInt(form.get("count"), 1);
            int durationDays = parseInt(form.get("durationDays"), 0);
            AuthStore.CardsGenerated generated = store.generateCards(software, count,
                    durationDays, form.get("note"));
            JsonOut codes = JsonOut.array();
            for (String code : generated.codes) {
                codes.item(Json.quote(code));
            }
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .add("software", software == null ? "" : software.trim())
                    .add("batchId", generated.batchId)
                    .add("count", generated.codes.size())
                    .raw("cards", codes.toString())
                    .toString());
        }

        private void handleCardRevoke(HttpExchange exchange, String code)
                throws IOException, AuthStore.AuthException {
            AuthStore.Card revoked = store.revokeCard(code);
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .add("code", revoked.code)
                    .add("status", revoked.status)
                    .add("software", revoked.softwareId)
                    .toString());
        }

        private void handleUsersList(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            Map<String, String> query = query(exchange);
            String softwareFilter = trimToEmpty(query.get("software"));
            String search = trimToEmpty(query.get("q")).toLowerCase(Locale.ROOT);
            long now = System.currentTimeMillis();
            List<AuthStore.Software> software = store.softwareList();
            Map<String, String> softwareNames = new LinkedHashMap<String, String>();
            for (AuthStore.Software item : software) {
                softwareNames.put(item.id, item.name);
            }
            List<AuthStore.Membership> memberships = store.memberships();
            JsonOut users = JsonOut.array();
            for (AuthStore.User user : store.users()) {
                List<AuthStore.Membership> own = new ArrayList<AuthStore.Membership>();
                for (AuthStore.Membership membership : memberships) {
                    if (membership.userId.equals(user.id)) {
                        own.add(membership);
                    }
                }
                if (!softwareFilter.isEmpty() && !hasSoftware(own, softwareFilter)) {
                    continue;
                }
                if (!search.isEmpty()
                        && !user.username.toLowerCase(Locale.ROOT).contains(search)
                        && !user.id.toLowerCase(Locale.ROOT).contains(search)) {
                    continue;
                }
                users.item(userJson(user, own, softwareNames, now));
            }
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .raw("users", users.toString())
                    .toString());
        }

        private void handleUserToggle(HttpExchange exchange, String userId)
                throws IOException, AuthStore.AuthException {
            AuthStore.User updated = store.toggleUser(userId);
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .raw("user", userJson(updated, store.membershipsOf(updated.id),
                            softwareNameMap(), System.currentTimeMillis()))
                    .toString());
        }

        private void handleUserHwidReset(HttpExchange exchange, String userId)
                throws IOException, AuthStore.AuthException {
            AuthStore.User updated = store.resetHwid(userId);
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .add("userId", updated.id)
                    .add("username", updated.username)
                    .add("hwid", updated.hwid)
                    .raw("user", userJson(updated, store.membershipsOf(updated.id),
                            softwareNameMap(), System.currentTimeMillis()))
                    .toString());
        }

        private void handleUserHwidSet(HttpExchange exchange, String userId)
                throws IOException, AuthStore.AuthException {
            Map<String, String> form = form(exchange.getRequestBody());
            AuthStore.User updated = store.setHwid(userId, form.get("hwid"));
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .add("userId", updated.id)
                    .add("username", updated.username)
                    .add("hwid", updated.hwid)
                    .raw("user", userJson(updated, store.membershipsOf(updated.id),
                            softwareNameMap(), System.currentTimeMillis()))
                    .toString());
        }

        private void handleMembershipRevoke(HttpExchange exchange, String userId,
                                              String softwareId)
                throws IOException, AuthStore.AuthException {
            AuthStore.Membership revoked = store.revokeMembership(userId, softwareId);
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .add("userId", userId)
                    .add("software", softwareId)
                    .raw("membership", membershipJson(revoked,
                            softwareNameMap(), System.currentTimeMillis()))
                    .toString());
        }

        private void handleAudit(HttpExchange exchange)
                throws IOException, AuthStore.AuthException {
            JsonOut events = JsonOut.array();
            List<AuthStore.AuditEvent> audit = store.auditEvents();
            for (AuthStore.AuditEvent event : audit) {
                events.item(JsonOut.object()
                        .add("ts", event.ts)
                        .add("type", event.type)
                        .add("user", event.user)
                        .add("software", event.software)
                        .add("detail", event.detail));
            }
            sendJson(exchange, 200, JsonOut.object()
                    .add("ok", true)
                    .add("count", audit.size())
                    .raw("events", events.toString())
                    .toString());
        }

        private Map<String, String> softwareNameMap() {
            Map<String, String> names = new LinkedHashMap<String, String>();
            for (AuthStore.Software item : store.softwareList()) {
                names.put(item.id, item.name);
            }
            return names;
        }

        private static boolean hasSoftware(List<AuthStore.Membership> memberships, String softwareId) {
            for (AuthStore.Membership membership : memberships) {
                if (membership.softwareId.equals(softwareId)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean isKnownAdminPath(String[] segments) {
            if (segments.length == 0) {
                return false;
            }
            String head = segments[0];
            if ("overview".equals(head) || "audit".equals(head)) {
                return segments.length == 1;
            }
            if ("software".equals(head)) {
                return segments.length == 1
                        || (segments.length == 3 && "toggle".equals(segments[2]));
            }
            if ("cards".equals(head)) {
                return segments.length == 1
                        || (segments.length == 3 && "revoke".equals(segments[2]));
            }
            if ("users".equals(head)) {
                return segments.length == 1
                        || (segments.length == 3 && "toggle".equals(segments[2]))
                        || (segments.length == 3 && "hwid".equals(segments[2]))
                        || (segments.length == 4 && "hwid".equals(segments[2])
                        && "reset".equals(segments[3]))
                        || (segments.length == 5 && "memberships".equals(segments[2])
                        && "revoke".equals(segments[4]));
            }
            return false;
        }
    }

    // ==================================================================
    // JSON serialisation helpers
    // ==================================================================

    private static String softwareJson(AuthStore.Software item, int userCount, int cardCount,
                                       int unusedCards) {
        return JsonOut.object()
                .add("id", item.id)
                .add("name", item.name)
                .add("group", item.group)
                .add("description", item.description)
                .add("enabled", item.enabled)
                .add("createdAt", item.createdAt)
                .add("userCount", userCount)
                .add("cardCount", cardCount)
                .add("unusedCards", unusedCards)
                .toString();
    }

    private static String cardJson(AuthStore.Card card) {
        return JsonOut.object()
                .add("code", card.code)
                .add("software", card.softwareId)
                .add("durationDays", card.durationDays)
                .add("note", card.note)
                .add("createdAt", card.createdAt)
                .add("status", card.status)
                .add("usedByUserId", card.usedByUserId)
                .add("usedByUsername", card.usedByUsername)
                .add("usedAt", card.usedAt)
                .add("batchId", card.batchId)
                .toString();
    }

    private static String userJson(AuthStore.User user, List<AuthStore.Membership> memberships,
                                   Map<String, String> softwareNames, long now) {
        StringBuilder ids = new StringBuilder();
        StringBuilder summary = new StringBuilder();
        JsonOut details = JsonOut.array();
        for (AuthStore.Membership membership : memberships) {
            if (ids.length() > 0) {
                ids.append(',');
                summary.append(';');
            }
            ids.append(membership.softwareId);
            summary.append(membership.softwareId).append(':').append(membership.group)
                    .append(':').append(membership.status(now));
            details.item(membershipJson(membership, softwareNames, now));
        }
        return JsonOut.object()
                .add("id", user.id)
                .add("username", user.username)
                .add("disabled", user.disabled)
                .add("createdAt", user.createdAt)
                .add("hwid", user.hwid)
                .add("hwidBound", !user.hwid.isEmpty())
                .add("softwareIds", ids.toString())
                .add("memberships", summary.toString())
                .raw("membershipDetails", details.toString())
                .toString();
    }

    private static String membershipJson(AuthStore.Membership membership,
                                         Map<String, String> softwareNames, long now) {
        String softwareName = softwareNames.get(membership.softwareId);
        return JsonOut.object()
                .add("software", membership.softwareId)
                .add("softwareName", softwareName == null ? membership.softwareId : softwareName)
                .add("group", membership.group)
                .add("status", membership.status(now))
                .add("cardCode", membership.cardCode)
                .add("activatedAt", membership.activatedAt)
                .add("expiresAt", membership.expiresAt)
                .add("revoked", membership.revoked)
                .toString();
    }

    // ==================================================================
    // HTTP plumbing
    // ==================================================================

    /** Handles CORS preflight; returns {@code true} when the request was fully served. */
    private static boolean preflight(HttpExchange exchange) throws IOException {
        if (!"OPTIONS".equals(exchange.getRequestMethod())) {
            return false;
        }
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers",
                "Content-Type, X-Admin-Token");
        exchange.getResponseHeaders().set("Access-Control-Max-Age", "600");
        exchange.sendResponseHeaders(204, -1);
        return true;
    }

    private static void methodNotAllowed(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Allow", "GET, POST, OPTIONS");
        sendError(exchange, 405, "BAD_REQUEST", "Method not allowed");
    }

    private static void sendError(HttpExchange exchange, int httpStatus, String status,
                                  String message) throws IOException {
        String body = JsonOut.object()
                .add("ok", false)
                .add("status", status)
                .add("error", message)
                .toString();
        sendJson(exchange, httpStatus, body);
    }

    private static void sendJson(HttpExchange exchange, int httpStatus, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        if (exchange.getRequestMethod().equals("HEAD")) {
            exchange.sendResponseHeaders(httpStatus, -1);
            return;
        }
        exchange.sendResponseHeaders(httpStatus, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String remoteIp(HttpExchange exchange) {
        try {
            return exchange.getRemoteAddress().getAddress().getHostAddress();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static Map<String, String> form(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        int total = 0;
        while ((read = in.read(buffer)) >= 0) {
            total += read;
            if (total > MAX_FORM_BYTES) {
                throw new IOException("Request body too large");
            }
            out.write(buffer, 0, read);
        }
        return decodeForm(new String(out.toByteArray(), StandardCharsets.UTF_8));
    }

    private static Map<String, String> query(HttpExchange exchange) throws IOException {
        String raw = exchange.getRequestURI().getRawQuery();
        return decodeForm(raw == null ? "" : raw);
    }

    private static Map<String, String> decodeForm(String body) throws IOException {
        Map<String, String> values = new LinkedHashMap<String, String>();
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

    private static int parseInt(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return Integer.MIN_VALUE;
        }
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
