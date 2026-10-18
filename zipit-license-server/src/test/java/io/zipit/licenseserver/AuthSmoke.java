package io.zipit.licenseserver;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import io.zipit.licenseserver.auth.AuthCrypto;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Plain {@code main()} smoke harness for the multi-software auth server.
 *
 * <p>Starts a real {@link LicenseServer} on an ephemeral port, exercises the
 * complete DESIGN.md section 2 surface over HTTP (client + admin + legacy page),
 * verifies cross-software isolation, restarts the server on the same data
 * directory to prove persistence, and prints {@code PASS}/{@code FAIL} lines.
 * Exits with status 1 when any check fails.</p>
 *
 * <p>This intentionally avoids JUnit so it can run from a plain javac build
 * while other teammates own the Gradle build.</p>
 */
public final class AuthSmoke {

    /** 64-hex HWID of the default smoke-test machine (DESIGN appendix B). */
    private static final String HWID_A =
            "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90";
    /** A third machine used by the admin manual-set check. */
    private static final String HWID_C =
            "1111222233334444555566667777888899990000aaaabbbbccccddddeeeeffff";
    /** A different machine. */
    private static final String HWID_B =
            "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0";

    private static int passed;
    private static final List<String> failures = new ArrayList<String>();
    private static String base;
    private static String adminToken = "smoke-secret";

    private AuthSmoke() {
    }

    public static void main(String[] args) throws Exception {
        Path dataDir = Files.createTempDirectory("zipit-auth-smoke-");
        System.out.println("Zipit auth server smoke harness");
        System.out.println("data dir: " + dataDir);
        LicenseStore store = new LicenseStore(dataDir);
        LicenseServer server = new LicenseServer("127.0.0.1", 0, store, adminToken,
                "", "", "", LicenseServer.DEFAULT_TOKEN_DAYS, true);
        base = "http://127.0.0.1:" + server.start();
        System.out.println("server:   " + base);
        System.out.println();

        String userToken = null;
        try {
            // ---------------- health / info / admin auth ----------------
            check("GET /api/health returns ok", get("/api/health").body.contains("\"status\":\"ok\""));
            Response info = get("/api/server-info");
            check("server-info exposes adminTokenRequired=true",
                    info.body.contains("\"adminTokenRequired\":true"), info.body);
            check("server-info exposes auth=true", info.body.contains("\"auth\":true"));

            Response adminDenied = request("GET", base + "/api/admin/overview", null, "");
            check("admin overview without token -> 401", adminDenied.status == 401,
                    adminDenied.status + " " + adminDenied.body);
            Response adminWrong = request("GET", base + "/api/admin/overview", null, "wrong");
            check("admin overview with wrong token -> 401", adminWrong.status == 401);
            Response adminOk = adminGet("/api/admin/overview");
            check("admin overview with token -> 200", adminOk.status == 200, adminOk.body);

            Response softwareOpen = get("/api/auth/software");
            check("GET /api/auth/software (public) -> 200", softwareOpen.status == 200,
                    softwareOpen.body);

            // ---------------- software management ----------------
            Response swA = adminPost("/api/admin/software",
                    "id=alpha&name=Alpha%20App&description=demo%20A");
            check("create software alpha", swA.status == 200 && swA.body.contains("\"id\":\"alpha\""),
                    swA.body);
            Response swB = adminPost("/api/admin/software",
                    "id=beta&name=Beta%20App&description=demo%20B");
            check("create software beta", swB.status == 200 && swB.body.contains("\"id\":\"beta\""),
                    swB.body);
            Response swDup = adminPost("/api/admin/software", "id=alpha&name=Again");
            check("duplicate software id -> 409", swDup.status == 409
                    && swDup.body.contains("\"status\":\"BAD_REQUEST\""), swDup.body);
            Response swBadId = adminPost("/api/admin/software", "id=Bad!&name=X");
            check("invalid software id -> 400", swBadId.status == 400, swBadId.body);
            Response swList = adminGet("/api/admin/software");
            check("admin software list has alpha+beta",
                    swList.body.contains("\"alpha\"") && swList.body.contains("\"beta\""));
            Response enabledList = get("/api/auth/software");
            check("client software list exposes group grp-alpha",
                    enabledList.body.contains("\"group\":\"grp-alpha\""), enabledList.body);

            // ---------------- registration / login without auth ----------------
            Response registered = post("/api/auth/register", "username=bob&password=secret1");
            check("register bob -> REGISTERED", registered.status == 200
                    && registered.body.contains("\"status\":\"REGISTERED\""), registered.body);
            String bobId = fieldString(registered.body, "userId");
            Response registeredDup = post("/api/auth/register", "username=Bob&password=secret1");
            check("duplicate username (case-insensitive) -> 409 USERNAME_TAKEN",
                    registeredDup.status == 409
                            && registeredDup.body.contains("USERNAME_TAKEN"), registeredDup.body);
            Response badUser = post("/api/auth/register", "username=ab&password=secret1");
            check("short username -> 400", badUser.status == 400, badUser.body);
            Response badPass = post("/api/auth/register", "username=okname&password=12345");
            check("short password -> 400", badPass.status == 400, badPass.body);

            Response loginNoAuth = post("/api/auth/login",
                    "username=bob&password=secret1&software=alpha");
            check("login without authorization -> ACTIVATION_REQUIRED",
                    loginNoAuth.status == 200
                            && loginNoAuth.body.contains("\"status\":\"ACTIVATION_REQUIRED\""),
                    loginNoAuth.body);
            Response wrongSoftware = post("/api/auth/login",
                    "username=bob&password=secret1&software=missing");
            check("login unknown software -> 404 SOFTWARE_UNKNOWN",
                    wrongSoftware.status == 404
                            && wrongSoftware.body.contains("SOFTWARE_UNKNOWN"), wrongSoftware.body);
            Response wrongPassword = post("/api/auth/login",
                    "username=bob&password=nope-nope&software=alpha");
            check("wrong password -> 401 INVALID_CREDENTIALS",
                    wrongPassword.status == 401
                            && wrongPassword.body.contains("INVALID_CREDENTIALS"), wrongPassword.body);

            // ---------------- cards ----------------
            Response batchA = adminPost("/api/admin/cards",
                    "software=alpha&count=3&durationDays=30&note=smoke-batch-A");
            Set<String> cardsA = stringArray(batchA.body, "cards");
            check("generate 3 alpha cards", batchA.status == 200 && cardsA.size() == 3,
                    batchA.body);
            String batchId = fieldString(batchA.body, "batchId");
            check("batch id returned", batchId != null && !batchId.isEmpty());
            Response batchB = adminPost("/api/admin/cards",
                    "software=beta&count=2&durationDays=0&note=smoke-batch-B");
            Set<String> cardsB = stringArray(batchB.body, "cards");
            check("generate 2 beta cards", batchB.status == 200 && cardsB.size() == 2,
                    batchB.body);
            boolean formatOk = true;
            for (String code : cardsA) {
                formatOk &= code.matches("ALPHA-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}");
            }
            check("card code format PREFIX-XXXX-XXXX-XXXX", formatOk, cardsA.toString());
            Response unusedCards = adminGet("/api/admin/cards?software=alpha&status=unused");
            check("admin cards filter unused count=3",
                    unusedCards.body.contains("\"count\":3"), unusedCards.body);
            Response badCount = adminPost("/api/admin/cards", "software=alpha&count=201&durationDays=0");
            check("card count out of range -> 400", badCount.status == 400, badCount.body);

            // ---------------- activation + token / isolation ----------------
            String alphaCard = cardsA.iterator().next();
            Response activated = post("/api/auth/activate",
                    "username=bob&password=secret1&card=" + enc(alphaCard) + "&software=alpha");
            check("activate alpha card -> OK", activated.status == 200
                    && activated.body.contains("\"status\":\"OK\""), activated.body);
            userToken = fieldString(activated.body, "token");
            check("activation returns token+group", userToken != null
                    && activated.body.contains("\"group\":\"grp-alpha\""), activated.body);
            check("activation expiresAt in the future",
                    fieldLong(activated.body, "expiresAt") > System.currentTimeMillis());

            Response loginOk = post("/api/auth/login",
                    "username=bob&password=secret1&software=alpha");
            check("login after activation -> OK", loginOk.status == 200
                    && loginOk.body.contains("\"status\":\"OK\""), loginOk.body);
            userToken = fieldString(loginOk.body, "token");

            Response sessionOk = post("/api/auth/session",
                    "token=" + enc(userToken) + "&software=alpha");
            check("session alpha valid=true", sessionOk.status == 200
                    && fieldBool(sessionOk.body, "valid"), sessionOk.body);
            check("session returns username+group",
                    sessionOk.body.contains("\"username\":\"bob\"")
                            && sessionOk.body.contains("\"group\":\"grp-alpha\""), sessionOk.body);
            Response sessionIsolation = post("/api/auth/session",
                    "token=" + enc(userToken) + "&software=beta");
            check("ISOLATION: alpha token rejected by software beta",
                    sessionIsolation.status == 200 && !fieldBool(sessionIsolation.body, "valid"),
                    sessionIsolation.body);
            Response loginBeta = post("/api/auth/login",
                    "username=bob&password=secret1&software=beta");
            check("ISOLATION: bob has no beta authorization",
                    loginBeta.body.contains("ACTIVATION_REQUIRED"), loginBeta.body);

            String cardB = cardsB.iterator().next();
            Response mismatch = post("/api/auth/activate",
                    "username=bob&password=secret1&card=" + enc(cardB) + "&software=alpha");
            check("ISOLATION: beta card cannot activate alpha -> CARD_SOFTWARE_MISMATCH",
                    mismatch.status == 409
                            && mismatch.body.contains("CARD_SOFTWARE_MISMATCH"), mismatch.body);
            Response reuse = post("/api/auth/activate",
                    "username=bob&password=secret1&card=" + enc(alphaCard) + "&software=alpha");
            check("used card -> 409 CARD_USED", reuse.status == 409
                    && reuse.body.contains("CARD_USED"), reuse.body);

            String revocable = cardsA.toArray(new String[0])[1];
            Response revoked = adminPost("/api/admin/cards/" + enc(revocable) + "/revoke", "");
            check("revoke unused card", revoked.status == 200
                    && revoked.body.contains("\"status\":\"revoked\""), revoked.body);
            Response useRevoked = post("/api/auth/activate",
                    "username=bob&password=secret1&card=" + enc(revocable) + "&software=alpha");
            check("revoked card cannot activate -> CARD_REVOKED", useRevoked.status == 409
                    && useRevoked.body.contains("CARD_REVOKED"), useRevoked.body);
            Response revokeUsed = adminPost("/api/admin/cards/" + enc(alphaCard) + "/revoke", "");
            check("revoke used card -> 409 CARD_USED", revokeUsed.status == 409
                    && revokeUsed.body.contains("CARD_USED"), revokeUsed.body);

            // ---------------- membership revoke / user disable ----------------
            Response bogusSession = post("/api/auth/session", "token=bogus&software=alpha");
            check("bogus token -> valid=false TOKEN_INVALID", bogusSession.status == 200
                    && bogusSession.body.contains("TOKEN_INVALID"), bogusSession.body);

            Response usersList = adminGet("/api/admin/users");
            check("admin users list includes bob with membership summary",
                    usersList.body.contains("\"username\":\"bob\"")
                            && usersList.body.contains("alpha:grp-alpha:active"), usersList.body);
            Response audit = adminGet("/api/admin/audit");
            check("admin audit records events", fieldLong(audit.body, "count") > 0, audit.body);

            // ---------------- overview ----------------
            Response overview = adminGet("/api/admin/overview");
            check("overview software=2", fieldLong(overview.body, "software") == 2, overview.body);
            check("overview users=1", fieldLong(overview.body, "users") == 1, overview.body);
            check("overview unused=3 (1 alpha left + 2 beta)",
                    overview.body.contains("\"unused\":3"), overview.body);
            check("overview used=1", overview.body.contains("\"used\":1"), overview.body);
            check("overview memberships=1",
                    fieldLong(overview.body, "memberships") == 1, overview.body);

            // ---------------- admin page + legacy compatibility ----------------
            Response page = get("/");
            check("GET / serves admin page", page.status == 200
                    && page.body.contains("ZIPIT LICENSE SERVER"), "status=" + page.status);
            check("admin page contains Client setup section", page.body.contains("Client setup"));
            check("admin page contains all five tabs (Overview/Software/Cards/Users/License)",
                    page.body.contains("\u6982\u89c8") && page.body.contains("\u8f6f\u4ef6")
                            && page.body.contains("\u5361\u5bc6") && page.body.contains("\u7528\u6237")
                            && page.body.contains("\u8bb8\u53ef\u8bc1"));
            check("admin page has no unreplaced {{PUBLIC_URL}} placeholder",
                    !page.body.contains("{{PUBLIC_URL}}"));
            check("admin page contains server publicUrl",
                    page.body.contains("http://127.0.0.1:"), "publicUrl not found");
            check("admin page contains login screen", page.body.contains("Admin Token"));
            check("legacy /api/licenses route still works",
                    adminRequest("GET", base + "/api/licenses", null).status == 200);
            Response legacyCreate = adminRequest("POST", base + "/api/licenses",
                    "licensee=Smoke%20Corp&edition=enterprise&expires=2030-12-31&features=audit");
            check("legacy license creation still works", legacyCreate.status == 200
                    && legacyCreate.body.contains("signature:"), legacyCreate.body);
            Response legacyHealth = get("/api/public-key");
            check("legacy /api/public-key still works",
                    legacyHealth.status == 200 && legacyHealth.body.contains("BEGIN PUBLIC KEY"),
                    "status=" + legacyHealth.status);

            // ---------------- restart: persistence ----------------
            System.out.println();
            System.out.println("-- restarting server on the same data directory --");
            server.stop();
            LicenseStore store2 = new LicenseStore(dataDir);
            server = new LicenseServer("127.0.0.1", 0, store2, adminToken,
                    "", "", "", LicenseServer.DEFAULT_TOKEN_DAYS, true);
            base = "http://127.0.0.1:" + server.start();
            System.out.println("server restarted: " + base);

            Response loginAfterRestart = post("/api/auth/login",
                    "username=bob&password=secret1&software=alpha");
            check("PERSIST: bob can still login after restart", loginAfterRestart.status == 200
                    && loginAfterRestart.body.contains("\"status\":\"OK\""), loginAfterRestart.body);
            Response oldTokenSession = post("/api/auth/session",
                    "token=" + enc(userToken) + "&software=alpha");
            check("PERSIST: pre-restart token still valid (HMAC key persisted)",
                    fieldBool(oldTokenSession.body, "valid"), oldTokenSession.body);
            Response overviewAfterRestart = adminGet("/api/admin/overview");
            check("PERSIST: data counts survived restart",
                    fieldLong(overviewAfterRestart.body, "software") == 2
                            && fieldLong(overviewAfterRestart.body, "users") == 1,
                    overviewAfterRestart.body);

            // ---------------- disabled software / user ----------------
            Response toggleOff = adminPost("/api/admin/software/alpha/toggle", "");
            check("toggle software alpha off", toggleOff.body.contains("\"enabled\":false"),
                    toggleOff.body);
            Response loginDisabledSoftware = post("/api/auth/login",
                    "username=bob&password=secret1&software=alpha");
            check("disabled software blocks login -> SOFTWARE_DISABLED",
                    loginDisabledSoftware.status == 403
                            && loginDisabledSoftware.body.contains("SOFTWARE_DISABLED"),
                    loginDisabledSoftware.body);
            check("disabled software invalidates session",
                    !fieldBool(post("/api/auth/session",
                            "token=" + enc(userToken) + "&software=alpha").body, "valid"));
            Response softwareAfterDisable = get("/api/auth/software");
            check("disabled software hidden from /api/auth/software",
                    !softwareAfterDisable.body.contains("\"alpha\""), softwareAfterDisable.body);
            adminPost("/api/admin/software/alpha/toggle", "");

            Response toggleUser = adminPost("/api/admin/users/" + enc(bobId) + "/toggle", "");
            check("toggle user bob disabled", toggleUser.body.contains("\"disabled\":true"),
                    toggleUser.body);
            Response disabledLogin = post("/api/auth/login",
                    "username=bob&password=secret1&software=alpha");
            check("disabled user login -> 403 USER_DISABLED", disabledLogin.status == 403
                    && disabledLogin.body.contains("USER_DISABLED"), disabledLogin.body);
            check("disabled user token rejected by session",
                    !fieldBool(post("/api/auth/session",
                            "token=" + enc(userToken) + "&software=alpha").body, "valid"));
            adminPost("/api/admin/users/" + enc(bobId) + "/toggle", "");

            Response membershipRevoke = adminPost("/api/admin/users/" + enc(bobId)
                    + "/memberships/alpha/revoke", "");
            check("revoke membership alpha", membershipRevoke.status == 200,
                    membershipRevoke.body);
            check("revoked membership blocks login (ACTIVATION_REQUIRED)",
                    post("/api/auth/login", "username=bob&password=secret1&software=alpha")
                            .body.contains("ACTIVATION_REQUIRED"));
            check("revoked membership rejects old token",
                    !fieldBool(post("/api/auth/session",
                            "token=" + enc(userToken) + "&software=alpha").body, "valid"));

            // ---------------- HWID binding (DESIGN appendix B) ----------------
            Response registeredHw = post("/api/auth/register", "username=hwuser&password=secret1");
            check("register hwuser -> REGISTERED", registeredHw.status == 200
                    && registeredHw.body.contains("\"status\":\"REGISTERED\""), registeredHw.body);
            String hwUserId = fieldString(registeredHw.body, "userId");

            Response missingHwid = postRaw("/api/auth/login",
                    "username=hwuser&password=secret1&software=alpha");
            check("login without hwid -> 400 HWID_REQUIRED", missingHwid.status == 400
                    && missingHwid.body.contains("HWID_REQUIRED"), missingHwid.body);
            Response shortHwid = postRaw("/api/auth/login",
                    "username=hwuser&password=secret1&software=alpha&hwid=abc");
            check("login with malformed hwid -> 400 HWID_REQUIRED", shortHwid.status == 400
                    && shortHwid.body.contains("HWID_REQUIRED"), shortHwid.body);
            Response missingHwidSession = postRaw("/api/auth/session",
                    "token=bogus&software=alpha");
            check("session without hwid -> 200 valid:false HWID_REQUIRED",
                    missingHwidSession.status == 200
                            && !fieldBool(missingHwidSession.body, "valid")
                            && missingHwidSession.body.contains("HWID_REQUIRED"),
                    missingHwidSession.body);

            Response preActivationHw = post("/api/auth/login",
                    "username=hwuser&password=secret1&software=alpha");
            check("unactivated login with hwid -> ACTIVATION_REQUIRED (no bind yet)",
                    preActivationHw.body.contains("ACTIVATION_REQUIRED"), preActivationHw.body);

            String hwCard = stringArray(adminPost("/api/admin/cards",
                    "software=alpha&count=1&durationDays=0&note=hwid").body, "cards").iterator().next();
            Response hwActivate = post("/api/auth/activate",
                    "username=hwuser&password=secret1&card=" + enc(hwCard) + "&software=alpha");
            check("first activation binds HWID", hwActivate.status == 200
                    && hwActivate.body.contains("\"status\":\"OK\""), hwActivate.body);
            String hwToken = fieldString(hwActivate.body, "token");
            Response usersAfterBind = adminGet("/api/admin/users");
            check("admin users list exposes bound hwid",
                    usersAfterBind.body.contains("\"hwid\":\"" + HWID_A + "\"")
                            && usersAfterBind.body.contains("\"hwidBound\":true"),
                    usersAfterBind.body);

            Response loginSameMachine = postRaw("/api/auth/login",
                    "username=hwuser&password=secret1&software=alpha&hwid=" + HWID_A);
            check("same-HWID login -> OK", loginSameMachine.status == 200
                    && loginSameMachine.body.contains("\"status\":\"OK\""), loginSameMachine.body);
            Response loginOtherMachine = postRaw("/api/auth/login",
                    "username=hwuser&password=secret1&software=alpha&hwid=" + HWID_B);
            check("different-HWID login -> 403 HWID_MISMATCH", loginOtherMachine.status == 403
                    && loginOtherMachine.body.contains("HWID_MISMATCH"), loginOtherMachine.body);

            String spareCard = stringArray(adminPost("/api/admin/cards",
                    "software=alpha&count=1&durationDays=0&note=hwid-spare").body, "cards").iterator().next();
            Response activateOtherMachine = postRaw("/api/auth/activate",
                    "username=hwuser&password=secret1&card=" + enc(spareCard)
                            + "&software=alpha&hwid=" + HWID_B);
            check("different-HWID activation -> 403 HWID_MISMATCH",
                    activateOtherMachine.status == 403
                            && activateOtherMachine.body.contains("HWID_MISMATCH"),
                    activateOtherMachine.body);
            Response spareUnused = adminGet("/api/admin/cards?software=alpha&status=unused");
            check("rejected HWID activation did not consume the card",
                    spareUnused.body.contains("\"count\":2"), spareUnused.body);

            Response sessionSame = postRaw("/api/auth/session",
                    "token=" + enc(hwToken) + "&software=alpha&hwid=" + HWID_A);
            check("session same HWID -> valid:true", fieldBool(sessionSame.body, "valid"),
                    sessionSame.body);
            Response sessionOther = postRaw("/api/auth/session",
                    "token=" + enc(hwToken) + "&software=alpha&hwid=" + HWID_B);
            check("session different HWID -> 200 valid:false HWID_MISMATCH",
                    sessionOther.status == 200 && !fieldBool(sessionOther.body, "valid")
                            && sessionOther.body.contains("HWID_MISMATCH"), sessionOther.body);

            Response hwidReset = adminPost("/api/admin/users/" + enc(hwUserId) + "/hwid/reset", "");
            check("admin reset HWID -> ok", hwidReset.status == 200
                    && hwidReset.body.contains("\"hwid\":\"\""), hwidReset.body);
            Response loginAfterReset = postRaw("/api/auth/login",
                    "username=hwuser&password=secret1&software=alpha&hwid=" + HWID_B);
            check("after reset, new machine login binds HWID_B", loginAfterReset.status == 200
                    && loginAfterReset.body.contains("\"status\":\"OK\""), loginAfterReset.body);
            Response usersAfterRebind = adminGet("/api/admin/users");
            check("admin users list shows rebound HWID_B",
                    usersAfterRebind.body.contains("\"hwid\":\"" + HWID_B + "\""),
                    usersAfterRebind.body);
            Response oldMachineAfterRebind = postRaw("/api/auth/login",
                    "username=hwuser&password=secret1&software=alpha&hwid=" + HWID_A);
            check("old machine rejected after rebind -> HWID_MISMATCH",
                    oldMachineAfterRebind.status == 403
                            && oldMachineAfterRebind.body.contains("HWID_MISMATCH"),
                    oldMachineAfterRebind.body);
            Response manualSet = adminPost("/api/admin/users/" + enc(hwUserId) + "/hwid",
                    "hwid=" + HWID_C);
            check("admin manual set HWID -> ok", manualSet.status == 200
                    && manualSet.body.contains("\"hwid\":\"" + HWID_C + "\""), manualSet.body);
            String hwidAudit = adminGet("/api/admin/audit").body;
            check("audit contains hwid_bind/hwid_reset/hwid_set events",
                    hwidAudit.contains("hwid_bind") && hwidAudit.contains("hwid_reset")
                            && hwidAudit.contains("hwid_set"), hwidAudit);

            // ---------------- rate limiting (last: consumes the IP budget) ----------------
            System.out.println();
            System.out.println("-- restarting again for a clean rate-limit budget --");
            server.stop();
            LicenseStore store3 = new LicenseStore(dataDir);
            server = new LicenseServer("127.0.0.1", 0, store3, adminToken,
                    "", "", "", LicenseServer.DEFAULT_TOKEN_DAYS, true);
            base = "http://127.0.0.1:" + server.start();
            System.out.println("server restarted: " + base);

            int firstStatus = -1;
            int limitedAt = -1;
            for (int i = 1; i <= 31; i++) {
                Response attempt = post("/api/auth/login", "username=&password=");
                if (i == 1) {
                    firstStatus = attempt.status;
                }
                if (attempt.status == 429) {
                    limitedAt = i;
                    break;
                }
            }
            check("rate limit: normal invalid attempts -> 400", firstStatus == 400,
                    "first status " + firstStatus);
            check("rate limit: 31st auth attempt -> 429 RATE_LIMITED", limitedAt == 31,
                    "429 first seen at attempt " + limitedAt);
            Response limited = post("/api/auth/register", "username=&password=");
            check("rate limit shared across register/login/activate",
                    limited.status == 429 && limited.body.contains("RATE_LIMITED"), limited.body);
        } finally {
            server.stop();
            deleteRecursively(dataDir);
        }

        // ---------------- legacy 5-column users.tsv migration (DESIGN appendix B) ----------------
        System.out.println();
        System.out.println("-- legacy 5-column users.tsv migration --");
        Path legacyDir = Files.createTempDirectory("zipit-auth-legacy-");
        LicenseServer legacyServer = null;
        try {
            LicenseStore legacyStore = new LicenseStore(legacyDir);
            Path legacyAuth = Files.createDirectories(legacyDir.resolve("auth"));
            String legacyHash = AuthCrypto.hashPassword("secret1");
            Files.write(legacyAuth.resolve("users.tsv"),
                    ("u-legacy\tlegacyuser\t" + legacyHash + "\t"
                            + System.currentTimeMillis() + "\t0\n")
                            .getBytes(StandardCharsets.UTF_8));
            legacyServer = new LicenseServer("127.0.0.1", 0, legacyStore, adminToken,
                    "", "", "", LicenseServer.DEFAULT_TOKEN_DAYS, true);
            base = "http://127.0.0.1:" + legacyServer.start();
            adminPost("/api/admin/software", "id=legacyapp&name=Legacy%20App");
            String legacyCard = stringArray(adminPost("/api/admin/cards",
                    "software=legacyapp&count=1&durationDays=30&note=legacy").body,
                    "cards").iterator().next();
            Response legacyLogin = post("/api/auth/login",
                    "username=legacyuser&password=secret1&software=legacyapp");
            check("legacy 5-column user can login (hwid accepted)",
                    legacyLogin.status == 200
                            && legacyLogin.body.contains("ACTIVATION_REQUIRED"),
                    legacyLogin.body);
            Response legacyActivate = post("/api/auth/activate",
                    "username=legacyuser&password=secret1&card=" + enc(legacyCard)
                            + "&software=legacyapp");
            check("legacy user activation binds hwid and issues token",
                    legacyActivate.status == 200
                            && legacyActivate.body.contains("\"status\":\"OK\""),
                    legacyActivate.body);
            List<String> lines = Files.readAllLines(legacyAuth.resolve("users.tsv"),
                    StandardCharsets.UTF_8);
            String[] cols = lines.get(0).split("\t", -1);
            check("legacy users.tsv migrated to 6 columns with hwid",
                    cols.length == 6 && HWID_A.equals(cols[5]),
                    lines.get(0));
            Response legacyUsers = adminGet("/api/admin/users");
            check("migrated user exposes hwid in admin API",
                    legacyUsers.body.contains("\"hwid\":\"" + HWID_A + "\""), legacyUsers.body);
        } finally {
            if (legacyServer != null) {
                legacyServer.stop();
            }
            deleteRecursively(legacyDir);
        }

        System.out.println();
        System.out.println("---------------------------------------------");
        System.out.println("PASS=" + passed + "  FAIL=" + failures.size());
        if (!failures.isEmpty()) {
            for (String failure : failures) {
                System.out.println("FAILED: " + failure);
            }
            System.exit(1);
        }
        System.out.println("RESULT: ALL SMOKE CHECKS PASSED");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static void check(String name, boolean condition) {
        check(name, condition, "");
    }

    private static void check(String name, boolean condition, String detail) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + name);
        } else {
            failures.add(name + (detail == null || detail.isEmpty() ? "" : "  [" + detail + "]"));
            System.out.println("FAIL  " + name + (detail == null || detail.isEmpty()
                    ? "" : "  [" + detail + "]"));
        }
    }

    private static Response get(String path) throws Exception {
        return request("GET", base + path, null, "");
    }

    /**
     * Client auth calls automatically carry the default machine HWID so the
     * pre-HWID smoke checks stay focused on their original assertions. HWID
     * edge cases use {@link #postRaw(String, String)}.
     */
    private static Response post(String path, String form) throws Exception {
        String body = form;
        boolean authEndpoint = "/api/auth/login".equals(path)
                || "/api/auth/activate".equals(path) || "/api/auth/session".equals(path);
        if (body != null && authEndpoint && !body.contains("hwid=")) {
            body = body + "&hwid=" + HWID_A;
        }
        return request("POST", base + path, body, "");
    }

    private static Response postRaw(String path, String form) throws Exception {
        return request("POST", base + path, form, "");
    }

    private static Response adminGet(String path) throws Exception {
        return request("GET", base + path, null, adminToken);
    }

    private static Response adminPost(String path, String form) throws Exception {
        return request("POST", base + path, form, adminToken);
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
        InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String body = read(in);
        connection.disconnect();
        return new Response(status, body);
    }

    /** Overload used by the legacy license checks (admin token in the 4th position). */
    private static Response adminRequest(String method, String url, String form) throws Exception {
        return request(method, url, form, adminToken);
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = in.read(buffer)) >= 0) {
            out.write(buffer, 0, count);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    private static String fieldString(String json, String field) {
        String marker = "\"" + field + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = start;
        while (end < json.length() && json.charAt(end) != '"') {
            if (json.charAt(end) == '\\') {
                end++;
            }
            end++;
        }
        return json.substring(start, end);
    }

    private static long fieldLong(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return -1L;
        }
        start += marker.length();
        int end = start;
        while (end < json.length() && ("0123456789-".indexOf(json.charAt(end)) >= 0)) {
            end++;
        }
        try {
            return Long.parseLong(json.substring(start, end));
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static boolean fieldBool(String json, String field) {
        String marker = "\"" + field + "\":";
        int start = json.indexOf(marker);
        return start >= 0 && json.startsWith("true", start + marker.length());
    }

    private static Set<String> stringArray(String json, String field) {
        Set<String> values = new LinkedHashSet<String>();
        String marker = "\"" + field + "\":[";
        int start = json.indexOf(marker);
        if (start < 0) {
            return values;
        }
        start += marker.length();
        int index = json.indexOf('"', start);
        while (index >= 0) {
            int end = index + 1;
            while (end < json.length() && json.charAt(end) != '"') {
                if (json.charAt(end) == '\\') {
                    end++;
                }
                end++;
            }
            values.add(json.substring(index + 1, end));
            if (end + 1 >= json.length() || json.charAt(end + 1) == ']') {
                break;
            }
            index = json.indexOf('"', end + 1);
        }
        return values;
    }

    private static void deleteRecursively(Path path) {
        try {
            if (path == null || !Files.exists(path)) {
                return;
            }
            Files.walk(path)
                    .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // best effort
                        }
                    });
        } catch (Exception ignored) {
            // best effort
        }
    }

    private static final class Response {
        final int status;
        final String body;

        Response(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
