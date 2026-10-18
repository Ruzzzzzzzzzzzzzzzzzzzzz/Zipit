package io.zipit.licenseserver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Server-side acceptance tests for the multi-software authorization system.
 *
 * <p>Covers the DESIGN.md section 4.4 server list: registration/duplicate
 * username, login without authorization, activation, login after activation,
 * session validation, token expiry, membership revocation, user disabling, used
 * cards, cross-software card mismatch, card revocation, admin token enforcement,
 * batch card generation, overview statistics, cross-software token isolation,
 * rate limiting and restart persistence.</p>
 */
class AuthServerTest {

    /** 64-hex HWID of the "default" test machine (DESIGN appendix B). */
    private static final String HWID_A =
            "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
    /** A different machine. */
    private static final String HWID_B =
            "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0";
    /** A third machine used by the admin rebind test. */
    private static final String HWID_C =
            "1111222233334444555566667777888899990000aaaabbbbccccddddeeeeffff";

    @TempDir
    Path temp;

    private LicenseServer server;
    private String base;
    private String adminToken = "";

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    // ------------------------------------------------------------------
    // lifecycle helpers
    // ------------------------------------------------------------------

    private void start() throws Exception {
        start("", LicenseServer.DEFAULT_TOKEN_DAYS);
    }

    private void start(String token) throws Exception {
        start(token, LicenseServer.DEFAULT_TOKEN_DAYS);
    }

    private void start(String token, int tokenDays) throws Exception {
        adminToken = token;
        LicenseStore store = new LicenseStore(temp.resolve("data"));
        server = new LicenseServer("127.0.0.1", 0, store, token, "", "", "",
                tokenDays, true);
        int port = server.start();
        base = "http://127.0.0.1:" + port;
    }

    private void restart() throws Exception {
        restartAt(temp.resolve("data"));
    }

    private void restartAt(Path dataDir) throws Exception {
        server.stop();
        server = null;
        adminToken = "";
        LicenseStore store = new LicenseStore(dataDir);
        server = new LicenseServer("127.0.0.1", 0, store);
        base = "http://127.0.0.1:" + server.start();
    }

    // ------------------------------------------------------------------
    // tests
    // ------------------------------------------------------------------

    @Test
    void registersLogsInActivatesAndValidatesSession() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        Response registered = post("/api/auth/register", "username=alice&password=secret1");
        assertEquals(200, registered.status, registered.body);
        assertTrue(registered.body.contains("\"status\":\"REGISTERED\""), registered.body);
        String aliceId = jsonString(registered.body, "userId");
        assertNotNull(aliceId);
        assertTrue(aliceId.startsWith("u-"), aliceId);

        Response loginWithoutAuth = post("/api/auth/login",
                "username=alice&password=secret1&software=alpha");
        assertEquals(200, loginWithoutAuth.status, loginWithoutAuth.body);
        assertTrue(loginWithoutAuth.body.contains("\"status\":\"ACTIVATION_REQUIRED\""),
                loginWithoutAuth.body);
        assertEquals("alpha", jsonString(loginWithoutAuth.body, "software"));

        String card = generateCard("alpha");
        Response activated = post("/api/auth/activate",
                "username=alice&password=secret1&card=" + enc(card) + "&software=alpha");
        assertEquals(200, activated.status, activated.body);
        assertEquals("OK", jsonString(activated.body, "status"));
        assertEquals("grp-alpha", jsonString(activated.body, "group"));
        String token = jsonString(activated.body, "token");
        assertNotNull(token);
        long expiresAt = jsonLong(activated.body, "expiresAt");
        assertTrue(expiresAt > System.currentTimeMillis(), "expiresAt=" + expiresAt);

        Response login = post("/api/auth/login",
                "username=alice&password=secret1&software=alpha");
        assertEquals(200, login.status, login.body);
        assertEquals("OK", jsonString(login.body, "status"));
        token = jsonString(login.body, "token");
        assertNotNull(token);

        Response session = post("/api/auth/session", "token=" + enc(token) + "&software=alpha");
        assertEquals(200, session.status, session.body);
        assertTrue(jsonBool(session.body, "valid"), session.body);
        assertEquals(aliceId, jsonString(session.body, "userId"));
        assertEquals("grp-alpha", jsonString(session.body, "group"));

        // A token for software A must be rejected for software B (isolation).
        createSoftware("beta", "Beta");
        Response isolation = post("/api/auth/session", "token=" + enc(token) + "&software=beta");
        assertEquals(200, isolation.status, isolation.body);
        assertFalse(jsonBool(isolation.body, "valid"), isolation.body);
        assertEquals("TOKEN_INVALID", jsonString(isolation.body, "status"));

        // Alice has no beta authorization, so login on beta requires activation.
        Response betaLogin = post("/api/auth/login",
                "username=alice&password=secret1&software=beta");
        assertEquals(200, betaLogin.status, betaLogin.body);
        assertEquals("ACTIVATION_REQUIRED", jsonString(betaLogin.body, "status"));

        Response overview = adminGet("/api/admin/overview");
        assertEquals(200, overview.status, overview.body);
        assertEquals(2, jsonLong(overview.body, "software"));
        assertEquals(1, jsonLong(overview.body, "users"));
        assertEquals(1, jsonLong(overview.body, "memberships"));
        assertTrue(overview.body.contains("\"unused\":0"), overview.body);
        assertTrue(overview.body.contains("\"used\":1"), overview.body);
    }

    @Test
    void rejectsDuplicateUsernameCaseInsensitively() throws Exception {
        start();
        assertEquals(200, post("/api/auth/register", "username=Bob&password=secret1").status);
        Response duplicate = post("/api/auth/register", "username=bob&password=secret2");
        assertEquals(409, duplicate.status, duplicate.body);
        assertEquals("USERNAME_TAKEN", jsonString(duplicate.body, "status"));
    }

    @Test
    void enforcesCardRules() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        createSoftware("beta", "Beta");
        assertEquals(200, post("/api/auth/register", "username=bob&password=secret1").status);
        String alphaCard = generateCard("alpha");

        // Card bound to alpha cannot activate beta.
        Response mismatch = post("/api/auth/activate",
                "username=bob&password=secret1&card=" + enc(alphaCard) + "&software=beta");
        assertEquals(409, mismatch.status, mismatch.body);
        assertEquals("CARD_SOFTWARE_MISMATCH", jsonString(mismatch.body, "status"));

        // Revoked card cannot be activated.
        String revocable = generateCard("alpha");
        assertEquals(200, adminPost("/api/admin/cards/" + enc(revocable) + "/revoke", "").status,
                "revoke should succeed");
        Response revoked = post("/api/auth/activate",
                "username=bob&password=secret1&card=" + enc(revocable) + "&software=alpha");
        assertEquals(409, revoked.status, revoked.body);
        assertEquals("CARD_REVOKED", jsonString(revoked.body, "status"));

        // Unknown card code.
        Response unknown = post("/api/auth/activate",
                "username=bob&password=secret1&card=ALPHA-AAAA-AAAA-AAAA&software=alpha");
        assertEquals(404, unknown.status, unknown.body);
        assertEquals("CARD_INVALID", jsonString(unknown.body, "status"));

        // Used card cannot be reused, even by another user.
        Response first = post("/api/auth/activate",
                "username=bob&password=secret1&card=" + enc(alphaCard) + "&software=alpha");
        assertEquals(200, first.status, first.body);
        assertEquals(200, post("/api/auth/register", "username=carol&password=secret1").status);
        Response reused = post("/api/auth/activate",
                "username=carol&password=secret1&card=" + enc(alphaCard) + "&software=alpha");
        assertEquals(409, reused.status, reused.body);
        assertEquals("CARD_USED", jsonString(reused.body, "status"));

        // Used cards cannot be revoked.
        Response revokeUsed = adminPost("/api/admin/cards/" + enc(alphaCard) + "/revoke", "");
        assertEquals(409, revokeUsed.status, revokeUsed.body);
        assertEquals("CARD_USED", jsonString(revokeUsed.body, "status"));
    }

    @Test
    void revokingMembershipAndDisablingUserInvalidateAccess() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        assertEquals(200, post("/api/auth/register", "username=dave&password=secret1").status);
        String token = activate("dave", "secret1", "alpha");
        String userId = jsonString(post("/api/auth/login",
                "username=dave&password=secret1&software=alpha").body, "userId");

        Response revoked = adminPost("/api/admin/users/" + enc(userId)
                + "/memberships/alpha/revoke", "");
        assertEquals(200, revoked.status, revoked.body);

        Response loginAfterRevoke = post("/api/auth/login",
                "username=dave&password=secret1&software=alpha");
        assertEquals(200, loginAfterRevoke.status, loginAfterRevoke.body);
        assertEquals("ACTIVATION_REQUIRED", jsonString(loginAfterRevoke.body, "status"));

        Response sessionAfterRevoke = post("/api/auth/session",
                "token=" + enc(token) + "&software=alpha");
        assertFalse(jsonBool(sessionAfterRevoke.body, "valid"), sessionAfterRevoke.body);

        // A fresh card restores the authorization.
        String card = generateCard("alpha");
        Response reactivated = post("/api/auth/activate",
                "username=dave&password=secret1&card=" + enc(card) + "&software=alpha");
        assertEquals(200, reactivated.status, reactivated.body);
        assertEquals("OK", jsonString(reactivated.body, "status"));
        String freshToken = jsonString(reactivated.body, "token");

        // Disabling the user invalidates both login and existing tokens.
        Response disabled = adminPost("/api/admin/users/" + enc(userId) + "/toggle", "");
        assertEquals(200, disabled.status, disabled.body);
        assertTrue(disabled.body.contains("\"disabled\":true"), disabled.body);

        Response loginDisabled = post("/api/auth/login",
                "username=dave&password=secret1&software=alpha");
        assertEquals(403, loginDisabled.status, loginDisabled.body);
        assertEquals("USER_DISABLED", jsonString(loginDisabled.body, "status"));

        Response sessionDisabled = post("/api/auth/session",
                "token=" + enc(freshToken) + "&software=alpha");
        assertFalse(jsonBool(sessionDisabled.body, "valid"), sessionDisabled.body);

        // Re-enabling restores login.
        assertEquals(200, adminPost("/api/admin/users/" + enc(userId) + "/toggle", "").status);
        Response loginEnabled = post("/api/auth/login",
                "username=dave&password=secret1&software=alpha");
        assertEquals("OK", jsonString(loginEnabled.body, "status"), loginEnabled.body);
    }

    @Test
    void disabledSoftwareBlocksLoginAndSessions() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        assertEquals(200, post("/api/auth/register", "username=erin&password=secret1").status);
        String token = activate("erin", "secret1", "alpha");

        Response toggle = adminPost("/api/admin/software/alpha/toggle", "");
        assertEquals(200, toggle.status, toggle.body);
        assertTrue(toggle.body.contains("\"enabled\":false"), toggle.body);

        Response login = post("/api/auth/login",
                "username=erin&password=secret1&software=alpha");
        assertEquals(403, login.status, login.body);
        assertEquals("SOFTWARE_DISABLED", jsonString(login.body, "status"));

        Response session = post("/api/auth/session", "token=" + enc(token) + "&software=alpha");
        assertFalse(jsonBool(session.body, "valid"), session.body);

        Response softwareList = get("/api/auth/software");
        assertTrue(jsonBool(softwareList.body, "ok"), softwareList.body);
        assertFalse(softwareList.body.contains("\"alpha\""), softwareList.body);

        assertEquals(200, adminPost("/api/admin/software/alpha/toggle", "").status);
        Response loginAgain = post("/api/auth/login",
                "username=erin&password=secret1&software=alpha");
        assertEquals("OK", jsonString(loginAgain.body, "status"), loginAgain.body);
    }

    @Test
    void adminEndpointsRequireToken() throws Exception {
        start("s3cret");
        Response overview = request("GET", base + "/api/admin/overview", null, "");
        assertEquals(401, overview.status, overview.body);

        Response userList = request("GET", base + "/api/admin/users", null, "wrong-token");
        assertEquals(401, userList.status, userList.body);

        Response unauthorizedCreate = request("POST", base + "/api/admin/software",
                "id=alpha&name=Alpha", "");
        assertEquals(401, unauthorizedCreate.status, unauthorizedCreate.body);

        Response authorized = authGet("/api/admin/overview", "s3cret");
        assertEquals(200, authorized.status, authorized.body);

        Response created = authPost("/api/admin/software", "id=alpha&name=Alpha", "s3cret");
        assertEquals(200, created.status, created.body);

        // Public client endpoints stay open.
        assertEquals(200, request("GET", base + "/api/auth/software", null, "").status);

        // Legacy admin endpoints keep their token behaviour too.
        assertEquals(401, request("GET", base + "/api/licenses", null, "").status);
        assertEquals(200, authGet("/api/licenses", "s3cret").status);
    }

    @Test
    void expiredTokensAreRejectedBySession() throws Exception {
        start("", 0);
        createSoftware("alpha", "Alpha");
        assertEquals(200, post("/api/auth/register", "username=frank&password=secret1").status);
        String token = activate("frank", "secret1", "alpha");
        Thread.sleep(10);
        Response session = post("/api/auth/session", "token=" + enc(token) + "&software=alpha");
        assertEquals(200, session.status, session.body);
        assertFalse(jsonBool(session.body, "valid"), session.body);
        String reason = jsonString(session.body, "reason");
        assertNotNull(reason);
        assertTrue(reason.toLowerCase().contains("expired"), reason);
    }

    @Test
    void generatesCardsInBatchesWithFilters() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        Response generated = adminPost("/api/admin/cards",
                "software=alpha&count=5&durationDays=30&note=batch-test");
        assertEquals(200, generated.status, generated.body);
        Set<String> codes = jsonStringArray(generated.body, "cards");
        assertEquals(5, codes.size());
        String batchId = jsonString(generated.body, "batchId");
        assertNotNull(batchId);
        for (String code : codes) {
            assertTrue(code.matches("ALPHA-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}"),
                    "unexpected card code format: " + code);
        }

        Response unused = adminGet("/api/admin/cards?software=alpha&status=unused");
        assertTrue(unused.body.contains("\"count\":5"), unused.body);
        assertTrue(unused.body.contains("\"batchId\":"), unused.body);

        Response used = adminGet("/api/admin/cards?software=alpha&status=used");
        assertTrue(used.body.contains("\"count\":0"), used.body);

        Response otherSoftware = adminGet("/api/admin/cards?software=beta");
        assertTrue(otherSoftware.body.contains("\"count\":0"), otherSoftware.body);

        assertEquals(400, adminPost("/api/admin/cards",
                "software=alpha&count=0&durationDays=0").status);
        assertEquals(400, adminPost("/api/admin/cards",
                "software=alpha&count=201&durationDays=0").status);
        assertEquals(404, adminPost("/api/admin/cards",
                "software=unknown&count=1&durationDays=0").status);
        assertEquals(400, adminPost("/api/admin/cards",
                "software=alpha&count=1&durationDays=-1").status);
    }

    @Test
    void persistsDataAndTokensAcrossRestart() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        assertEquals(200, post("/api/auth/register", "username=gina&password=secret1").status);
        String token = activate("gina", "secret1", "alpha");

        restart();

        Response login = post("/api/auth/login",
                "username=gina&password=secret1&software=alpha");
        assertEquals(200, login.status, login.body);
        assertEquals("OK", jsonString(login.body, "status"), login.body);

        // The token HMAC key is persisted, so previously issued tokens still verify.
        Response session = post("/api/auth/session", "token=" + enc(token) + "&software=alpha");
        assertTrue(jsonBool(session.body, "valid"), session.body);

        Response overview = adminGet("/api/admin/overview");
        assertEquals(1, jsonLong(overview.body, "software"));
        assertEquals(1, jsonLong(overview.body, "users"));
        assertTrue(overview.body.contains("\"used\":1"), overview.body);
        assertTrue(java.nio.file.Files.exists(temp.resolve("data").resolve("auth")
                .resolve("token.key")));
        assertTrue(java.nio.file.Files.exists(temp.resolve("data").resolve("auth")
                .resolve("software.tsv")));
        assertTrue(java.nio.file.Files.exists(temp.resolve("data").resolve("auth")
                .resolve("users.tsv")));
        assertTrue(java.nio.file.Files.exists(temp.resolve("data").resolve("auth")
                .resolve("cards.tsv")));
        assertTrue(java.nio.file.Files.exists(temp.resolve("data").resolve("auth")
                .resolve("memberships.tsv")));
    }

    @Test
    void rateLimitsClientAuthEndpoints() throws Exception {
        start();
        for (int i = 0; i < 30; i++) {
            Response attempt = post("/api/auth/login", "username=&password=");
            assertEquals(400, attempt.status, "attempt " + i + ": " + attempt.body);
        }
        Response limited = post("/api/auth/login", "username=&password=");
        assertEquals(429, limited.status, limited.body);
        assertEquals("RATE_LIMITED", jsonString(limited.body, "status"));

        // register and activate share the same per-IP budget.
        Response limitedRegister = post("/api/auth/register", "username=&password=");
        assertEquals(429, limitedRegister.status, limitedRegister.body);
    }

    @Test
    void validatesInputsAndErrorCodes() throws Exception {
        start();
        createSoftware("alpha", "Alpha");

        assertEquals(400, post("/api/auth/register", "username=ab&password=secret1").status);
        assertEquals(400, post("/api/auth/register", "username=bad name&password=secret1").status);
        assertEquals(400, post("/api/auth/register", "username=valid-name&password=12345").status);

        Response unknownSoftware = post("/api/auth/login",
                "username=nobody&password=secret1&software=missing");
        assertEquals(404, unknownSoftware.status, unknownSoftware.body);
        assertEquals("SOFTWARE_UNKNOWN", jsonString(unknownSoftware.body, "status"));

        Response unknownUser = post("/api/auth/login",
                "username=nobody&password=secret1&software=alpha");
        assertEquals(401, unknownUser.status, unknownUser.body);
        assertEquals("INVALID_CREDENTIALS", jsonString(unknownUser.body, "status"));

        assertEquals(200, post("/api/auth/register", "username=henry&password=secret1").status);
        Response wrongPassword = post("/api/auth/login",
                "username=henry&password=wrong-pass&software=alpha");
        assertEquals(401, wrongPassword.status, wrongPassword.body);
        assertEquals("INVALID_CREDENTIALS", jsonString(wrongPassword.body, "status"));

        Response badSession = post("/api/auth/session",
                "token=not-a-token&software=alpha");
        assertEquals(200, badSession.status, badSession.body);
        assertFalse(jsonBool(badSession.body, "valid"), badSession.body);
        assertEquals("TOKEN_INVALID", jsonString(badSession.body, "status"));

        Response missingSoftware = post("/api/auth/session", "token=x");
        assertEquals(400, missingSoftware.status, missingSoftware.body);

        Response duplicateSoftware = adminPost("/api/admin/software",
                "id=alpha&name=Another");
        assertEquals(409, duplicateSoftware.status, duplicateSoftware.body);

        assertEquals(400, adminPost("/api/admin/software",
                "id=BAD!&name=Bad").status);
        assertEquals(400, adminPost("/api/admin/software",
                "id=ok-id&name=").status);
        assertEquals(404, adminPost("/api/admin/software/missing/toggle", "").status);
        assertEquals(404, adminPost("/api/admin/cards/NOPE-XXXX-XXXX-XXXX/revoke", "").status);
        assertEquals(404, adminPost("/api/admin/users/u-missing/toggle", "").status);
    }

    @Test
    void listsUsersWithMembershipSummaryAndFilters() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        createSoftware("beta", "Beta");
        assertEquals(200, post("/api/auth/register", "username=ivy&password=secret1").status);
        assertEquals(200, post("/api/auth/register", "username=jack&password=secret1").status);
        activate("ivy", "secret1", "alpha");

        Response all = adminGet("/api/admin/users");
        assertEquals(200, all.status, all.body);
        assertTrue(all.body.contains("\"ivy\""), all.body);
        assertTrue(all.body.contains("\"jack\""), all.body);
        assertTrue(all.body.contains("alpha:grp-alpha:active"), all.body);

        Response search = adminGet("/api/admin/users?q=iv");
        assertEquals(200, search.status, search.body);
        assertTrue(search.body.contains("\"ivy\""), search.body);
        assertFalse(search.body.contains("\"jack\""), search.body);

        Response bySoftware = adminGet("/api/admin/users?software=alpha");
        assertTrue(bySoftware.body.contains("\"ivy\""), bySoftware.body);
        assertFalse(bySoftware.body.contains("\"jack\""), bySoftware.body);

        Response softwareList = adminGet("/api/admin/software");
        assertEquals(200, softwareList.status, softwareList.body);
        assertTrue(softwareList.body.contains("\"userCount\":1"), softwareList.body);
        assertTrue(softwareList.body.contains("\"cardCount\":0"), softwareList.body);

        Response audit = adminGet("/api/admin/audit");
        assertEquals(200, audit.status, audit.body);
        assertTrue(jsonLong(audit.body, "count") > 0);
        assertTrue(audit.body.contains("activate"), audit.body);
    }

    @Test
    void tamperedTokensAreRejected() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        assertEquals(200, post("/api/auth/register", "username=kim&password=secret1").status);
        String token = activate("kim", "secret1", "alpha");
        String tampered = token.substring(0, token.length() - 2)
                + (token.endsWith("AA") ? "BB" : "AA");
        Response session = post("/api/auth/session",
                "token=" + enc(tampered) + "&software=alpha");
        assertEquals(200, session.status, session.body);
        assertFalse(jsonBool(session.body, "valid"), session.body);
        String reason = jsonString(session.body, "reason");
        assertNotNull(reason);
        assertTrue(reason.toLowerCase().contains("signature"), reason);
    }

    // ------------------------------------------------------------------
    // HWID binding tests (DESIGN appendix B)
    // ------------------------------------------------------------------

    @Test
    void requiresValidHwidOnClientEndpoints() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        assertEquals(200, post("/api/auth/register", "username=owen&password=secret1").status);

        Response loginMissing = postRaw("/api/auth/login",
                "username=owen&password=secret1&software=alpha");
        assertEquals(400, loginMissing.status, loginMissing.body);
        assertEquals("HWID_REQUIRED", jsonString(loginMissing.body, "status"));

        Response loginShort = postRaw("/api/auth/login",
                "username=owen&password=secret1&software=alpha&hwid=abc123");
        assertEquals(400, loginShort.status, loginShort.body);
        assertEquals("HWID_REQUIRED", jsonString(loginShort.body, "status"));

        String badChars = HWID_A.substring(0, 63) + "!";
        Response loginBadChars = postRaw("/api/auth/login",
                "username=owen&password=secret1&software=alpha&hwid=" + enc(badChars));
        assertEquals(400, loginBadChars.status, loginBadChars.body);
        assertEquals("HWID_REQUIRED", jsonString(loginBadChars.body, "status"));

        String card = generateCard("alpha");
        Response activateMissing = postRaw("/api/auth/activate",
                "username=owen&password=secret1&card=" + enc(card) + "&software=alpha");
        assertEquals(400, activateMissing.status, activateMissing.body);
        assertEquals("HWID_REQUIRED", jsonString(activateMissing.body, "status"));

        // /api/auth/session keeps HTTP 200 and reports HWID_REQUIRED in the body.
        Response sessionMissing = postRaw("/api/auth/session", "token=x&software=alpha");
        assertEquals(200, sessionMissing.status, sessionMissing.body);
        assertFalse(jsonBool(sessionMissing.body, "valid"), sessionMissing.body);
        assertEquals("HWID_REQUIRED", jsonString(sessionMissing.body, "status"));

        Response sessionShort = postRaw("/api/auth/session",
                "token=x&software=alpha&hwid=zz");
        assertEquals(200, sessionShort.status, sessionShort.body);
        assertEquals("HWID_REQUIRED", jsonString(sessionShort.body, "status"));
    }

    @Test
    void bindsHwidOnFirstActivationAndRejectsOtherMachines() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        createSoftware("beta", "Beta");
        Response registered = post("/api/auth/register", "username=mike&password=secret1");
        assertEquals(200, registered.status, registered.body);

        // A login that does not issue a token (activation still required) does not bind yet.
        Response preActivation = post("/api/auth/login",
                "username=mike&password=secret1&software=alpha");
        assertEquals("ACTIVATION_REQUIRED", jsonString(preActivation.body, "status"),
                preActivation.body);
        Response usersBefore = adminGet("/api/admin/users");
        assertTrue(usersBefore.body.contains("\"username\":\"mike\""), usersBefore.body);
        assertTrue(usersBefore.body.contains("\"hwid\":\"\""), usersBefore.body);
        assertTrue(usersBefore.body.contains("\"hwidBound\":false"), usersBefore.body);

        // First successful activation binds the account to machine A.
        String token = activate("mike", "secret1", "alpha");
        Response usersBound = adminGet("/api/admin/users");
        assertTrue(usersBound.body.contains("\"hwid\":\"" + HWID_A + "\""), usersBound.body);
        assertTrue(usersBound.body.contains("\"hwidBound\":true"), usersBound.body);

        // Same machine passes.
        Response loginA = postRaw("/api/auth/login",
                "username=mike&password=secret1&software=alpha&hwid=" + HWID_A);
        assertEquals(200, loginA.status, loginA.body);
        assertEquals("OK", jsonString(loginA.body, "status"), loginA.body);
        String freshToken = jsonString(loginA.body, "token");
        assertNotNull(freshToken);

        // Different machine is rejected for login and activation.
        Response loginB = postRaw("/api/auth/login",
                "username=mike&password=secret1&software=alpha&hwid=" + HWID_B);
        assertEquals(403, loginB.status, loginB.body);
        assertEquals("HWID_MISMATCH", jsonString(loginB.body, "status"));
        assertTrue(loginB.body.contains("bound to another machine"), loginB.body);

        String unusedCard = generateCard("alpha");
        Response activateB = postRaw("/api/auth/activate",
                "username=mike&password=secret1&card=" + enc(unusedCard)
                        + "&software=alpha&hwid=" + HWID_B);
        assertEquals(403, activateB.status, activateB.body);
        assertEquals("HWID_MISMATCH", jsonString(activateB.body, "status"));
        // ...and the rejected activation must not consume the card.
        Response cards = adminGet("/api/admin/cards?software=alpha&status=unused");
        assertTrue(cards.body.contains("\"count\":1"), cards.body);

        // Session: same HWID valid, other HWID invalid with HWID_MISMATCH.
        Response sessionA = postRaw("/api/auth/session",
                "token=" + enc(freshToken) + "&software=alpha&hwid=" + HWID_A);
        assertTrue(jsonBool(sessionA.body, "valid"), sessionA.body);
        Response sessionB = postRaw("/api/auth/session",
                "token=" + enc(freshToken) + "&software=alpha&hwid=" + HWID_B);
        assertEquals(200, sessionB.status, sessionB.body);
        assertFalse(jsonBool(sessionB.body, "valid"), sessionB.body);
        assertEquals("HWID_MISMATCH", jsonString(sessionB.body, "status"));

        // Cross-software isolation still reports TOKEN_INVALID (not HWID_MISMATCH).
        Response sessionBeta = postRaw("/api/auth/session",
                "token=" + enc(freshToken) + "&software=beta&hwid=" + HWID_A);
        assertFalse(jsonBool(sessionBeta.body, "valid"), sessionBeta.body);
        assertEquals("TOKEN_INVALID", jsonString(sessionBeta.body, "status"));

        // One more sanity check: the original activation token still works on machine A.
        Response originalSession = postRaw("/api/auth/session",
                "token=" + enc(token) + "&software=alpha&hwid=" + HWID_A);
        assertTrue(jsonBool(originalSession.body, "valid"), originalSession.body);
    }

    @Test
    void adminResetsAndRebindsHwid() throws Exception {
        start();
        createSoftware("alpha", "Alpha");
        String userId = jsonString(post("/api/auth/register",
                "username=nina&password=secret1").body, "userId");
        assertNotNull(userId);
        String token = activate("nina", "secret1", "alpha");

        Response rejectedOnA = postRaw("/api/auth/session",
                "token=" + enc(token) + "&software=alpha&hwid=" + HWID_B);
        assertEquals("HWID_MISMATCH", jsonString(rejectedOnA.body, "status"));

        // Admin reset clears the binding.
        Response reset = adminPost("/api/admin/users/" + enc(userId) + "/hwid/reset", "");
        assertEquals(200, reset.status, reset.body);
        assertEquals("", jsonString(reset.body, "hwid"));
        assertTrue(adminGet("/api/admin/users").body.contains("\"hwid\":\"\""),
                adminGet("/api/admin/users").body);

        // Machine B can now log in and becomes the new binding.
        Response loginB = postRaw("/api/auth/login",
                "username=nina&password=secret1&software=alpha&hwid=" + HWID_B);
        assertEquals(200, loginB.status, loginB.body);
        assertEquals("OK", jsonString(loginB.body, "status"), loginB.body);
        Response usersBoundB = adminGet("/api/admin/users");
        assertTrue(usersBoundB.body.contains("\"hwid\":\"" + HWID_B + "\""), usersBoundB.body);

        // Machine A is now rejected.
        Response loginA = postRaw("/api/auth/login",
                "username=nina&password=secret1&software=alpha&hwid=" + HWID_A);
        assertEquals(403, loginA.status, loginA.body);
        assertEquals("HWID_MISMATCH", jsonString(loginA.body, "status"));

        // Admin can also set/rebind the HWID manually.
        Response set = adminPost("/api/admin/users/" + enc(userId) + "/hwid",
                "hwid=" + HWID_C);
        assertEquals(200, set.status, set.body);
        assertEquals(HWID_C, jsonString(set.body, "hwid"));
        Response loginC = postRaw("/api/auth/login",
                "username=nina&password=secret1&software=alpha&hwid=" + HWID_C);
        assertEquals("OK", jsonString(loginC.body, "status"), loginC.body);
        Response loginBOld = postRaw("/api/auth/login",
                "username=nina&password=secret1&software=alpha&hwid=" + HWID_B);
        assertEquals(403, loginBOld.status, loginBOld.body);

        // Invalid/incomplete admin requests.
        assertEquals(400, adminPost("/api/admin/users/" + enc(userId) + "/hwid",
                "hwid=short").status);
        assertEquals(404, adminPost("/api/admin/users/u-missing/hwid/reset", "").status);

        // Audit trail records the HWID lifecycle.
        String audit = adminGet("/api/admin/audit").body;
        assertTrue(audit.contains("hwid_bind"), audit);
        assertTrue(audit.contains("hwid_reset"), audit);
        assertTrue(audit.contains("hwid_set"), audit);
    }

    @Test
    void migratesLegacyUsersFileWithoutHwidColumn() throws Exception {
        Path dataDir = temp.resolve("legacy-data");
        LicenseStore legacyStore = new LicenseStore(dataDir);
        Path authDir = dataDir.resolve("auth");
        Files.createDirectories(authDir);
        String legacyHash = io.zipit.licenseserver.auth.AuthCrypto.hashPassword("secret1");
        String legacyLine = "u-legacy\tlegacyuser\t" + legacyHash + "\t"
                + System.currentTimeMillis() + "\t0\n";
        Files.write(authDir.resolve("users.tsv"),
                legacyLine.getBytes(StandardCharsets.UTF_8));

        server = new LicenseServer("127.0.0.1", 0, legacyStore, "", "", "", "",
                LicenseServer.DEFAULT_TOKEN_DAYS, true);
        base = "http://127.0.0.1:" + server.start();
        adminToken = "";

        createSoftware("alpha", "Alpha");
        Response login = post("/api/auth/login",
                "username=legacyuser&password=secret1&software=alpha");
        assertEquals(200, login.status, login.body);
        assertEquals("ACTIVATION_REQUIRED", jsonString(login.body, "status"), login.body);

        String token = activate("legacyuser", "secret1", "alpha");
        assertNotNull(token);

        // The old 5-column row is upgraded to the 6-column layout with the bound HWID.
        List<String> lines = Files.readAllLines(authDir.resolve("users.tsv"),
                StandardCharsets.UTF_8);
        assertEquals(1, lines.size(), lines.toString());
        String[] columns = lines.get(0).split("\t", -1);
        assertEquals(6, columns.length, "legacy users.tsv must gain the hwid column");
        assertEquals(HWID_A, columns[5]);

        Response users = adminGet("/api/admin/users");
        assertTrue(users.body.contains("\"hwid\":\"" + HWID_A + "\""), users.body);

        // Restart keeps the migrated binding.
        restartAt(dataDir);
        Response loginAfterRestart = post("/api/auth/login",
                "username=legacyuser&password=secret1&software=alpha");
        assertEquals("OK", jsonString(loginAfterRestart.body, "status"),
                loginAfterRestart.body);
        Response wrongMachine = postRaw("/api/auth/login",
                "username=legacyuser&password=secret1&software=alpha&hwid=" + HWID_B);
        assertEquals(403, wrongMachine.status, wrongMachine.body);
    }

    // ------------------------------------------------------------------
    // request helpers
    // ------------------------------------------------------------------

    private String createSoftware(String id, String name) throws Exception {
        Response response = adminPost("/api/admin/software",
                "id=" + enc(id) + "&name=" + enc(name) + "&description=test");
        assertEquals(200, response.status, response.body);
        return jsonString(response.body, "id");
    }

    /** Generates one card for the software and returns its code. */
    private String generateCard(String softwareId) throws Exception {
        Response response = adminPost("/api/admin/cards",
                "software=" + enc(softwareId) + "&count=1&durationDays=0&note=test");
        assertEquals(200, response.status, response.body);
        Set<String> codes = jsonStringArray(response.body, "cards");
        assertEquals(1, codes.size(), response.body);
        return codes.iterator().next();
    }

    private String activate(String username, String password, String software) throws Exception {
        return activateWithHwid(username, password, software, HWID_A);
    }

    private String activateWithHwid(String username, String password, String software,
                                    String hwid) throws Exception {
        String card = generateCard(software);
        Response response = postRaw("/api/auth/activate",
                "username=" + enc(username) + "&password=" + enc(password)
                        + "&card=" + enc(card) + "&software=" + enc(software)
                        + "&hwid=" + enc(hwid));
        assertEquals(200, response.status, response.body);
        assertEquals("OK", jsonString(response.body, "status"), response.body);
        String token = jsonString(response.body, "token");
        assertNotNull(token);
        return token;
    }

    private Response get(String path) throws Exception {
        return request("GET", base + path, null, "");
    }

    /**
     * Client auth calls automatically carry the default machine HWID, so the
     * pre-HWID tests stay focused on their original assertions. Tests for
     * missing/rejected HWIDs use {@link #postRaw(String, String)} directly.
     */
    private Response post(String path, String form) throws Exception {
        String body = form;
        boolean authEndpoint = "/api/auth/login".equals(path)
                || "/api/auth/activate".equals(path) || "/api/auth/session".equals(path);
        if (body != null && authEndpoint && !body.contains("hwid=")) {
            body = body + "&hwid=" + HWID_A;
        }
        return request("POST", base + path, body, "");
    }

    private Response postRaw(String path, String form) throws Exception {
        return request("POST", base + path, form, "");
    }

    private Response adminGet(String path) throws Exception {
        return request("GET", base + path, null, adminToken);
    }

    private Response adminPost(String path, String form) throws Exception {
        return request("POST", base + path, form, adminToken);
    }

    private Response authGet(String path, String token) throws Exception {
        return request("GET", base + path, null, token);
    }

    private Response authPost(String path, String form, String token) throws Exception {
        return request("POST", base + path, form, token);
    }

    private static Response request(String method, String url, String form, String token)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
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

    private static String read(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = in.read(buffer)) >= 0) {
            out.write(buffer, 0, count);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    // ------------------------------------------------------------------
    // flat JSON helpers (responses are deliberately small and flat)
    // ------------------------------------------------------------------

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }

    static String jsonString(String json, String field) {
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

    static long jsonLong(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return -1L;
        }
        start += marker.length();
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if ((c >= '0' && c <= '9') || c == '-') {
                end++;
            } else {
                break;
            }
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    static boolean jsonBool(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return false;
        }
        start += marker.length();
        return json.startsWith("true", start);
    }

    static Set<String> jsonStringArray(String json, String field) {
        Set<String> out = new HashSet<String>();
        String marker = "\"" + field + "\":[";
        int start = json.indexOf(marker);
        if (start < 0) {
            return out;
        }
        start += marker.length();
        int index = start;
        while (index < json.length()) {
            int open = json.indexOf('"', index);
            if (open < 0) {
                break;
            }
            StringBuilder value = new StringBuilder();
            boolean escaped = false;
            int i = open + 1;
            for (; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escaped) {
                    value.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    break;
                } else {
                    value.append(c);
                }
            }
            out.add(value.toString());
            index = i + 1;
            if (index < json.length() && json.charAt(index) == ']') {
                break;
            }
            int comma = json.indexOf(',', index);
            if (comma < 0) {
                break;
            }
            index = comma + 1;
            if (index < json.length() && json.charAt(index) == ']') {
                break;
            }
        }
        return out;
    }
}
