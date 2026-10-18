package io.zipit.licenseserver.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Persistent multi-software authorization store.
 *
 * <p>Data lives under {@code <dataDir>/auth/} in tab-separated files exactly as
 * specified in {@code tools/auth/DESIGN.md} section 1:</p>
 *
 * <ul>
 *   <li>{@code software.tsv}: id, name, group, description, enabled, createdAt</li>
 *   <li>{@code users.tsv}: id, username, passwordHash, createdAt, disabled</li>
 *   <li>{@code memberships.tsv}: userId, username, softwareId, group, cardCode,
 *       activatedAt, expiresAt, revoked</li>
 *   <li>{@code cards.tsv}: code, softwareId, durationDays, note, createdAt,
 *       status, usedByUserId, usedByUsername, usedAt, batchId</li>
 *   <li>{@code token.key}: 32 random bytes, generated on first start</li>
 * </ul>
 *
 * <p>Every mutation writes the affected file atomically (tmp file + move) while
 * the in-memory maps are updated first, so a restart always observes the last
 * successfully persisted state. All public methods are synchronized: the server
 * executor is small and the state is tiny, so correctness beats lock sharding.</p>
 */
public final class AuthStore {

    /** Client auth attempts allowed per IP inside {@link #RATE_WINDOW_SECONDS}. */
    public static final int RATE_LIMIT = 30;
    /** Sliding window for the client auth rate limit. */
    public static final int RATE_WINDOW_SECONDS = 60;
    /** Number of audit events kept in memory. */
    public static final int AUDIT_CAPACITY = 200;

    /** Card status: generated, not yet activated. */
    public static final String CARD_UNUSED = "unused";
    /** Card status: consumed by an activation. */
    public static final String CARD_USED = "used";
    /** Card status: revoked by an admin before use. */
    public static final String CARD_REVOKED = "revoked";
    /** Card status: disabled by an admin. */
    public static final String CARD_DISABLED = "disabled";

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_.-]{3,32}");
    private static final Pattern SOFTWARE_ID_PATTERN = Pattern.compile("[a-z0-9_-]{2,32}");
    private static final Pattern GROUP_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,64}");
    /** HWID format from DESIGN appendix B.1: 16-128 chars of [A-Za-z0-9_-]. */
    private static final Pattern HWID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{16,128}");
    private static final String HWID_REQUIRED_MESSAGE =
            "hwid is required (16-128 characters of [A-Za-z0-9_-])";
    private static final String HWID_MISMATCH_MESSAGE = "This account is bound to another machine";
    private static final long DAY_MILLIS = 24L * 60L * 60L * 1000L;

    private final Path authDir;
    private final Path softwareFile;
    private final Path usersFile;
    private final Path membershipsFile;
    private final Path cardsFile;
    private final Path tokenKeyFile;
    private final Path auditFile;
    private final byte[] tokenKey;
    private final int tokenDays;

    private final Map<String, Software> software = new LinkedHashMap<String, Software>();
    private final Map<String, User> users = new LinkedHashMap<String, User>();
    private final Map<String, User> usersByName = new HashMap<String, User>();
    private final Map<String, Membership> memberships = new LinkedHashMap<String, Membership>();
    private final Map<String, Card> cards = new LinkedHashMap<String, Card>();
    private final Deque<AuditEvent> audit = new ArrayDeque<AuditEvent>();
    private final Map<String, Deque<Long>> rateWindows = new HashMap<String, Deque<Long>>();

    /**
     * Creates the store and loads (or initialises) the {@code auth/} directory.
     *
     * @param dataDir   license-server data directory; auth data goes in {@code auth/}
     * @param tokenDays token lifetime in days (0 = tokens expire immediately,
     *                  useful for tests; production default is 7)
     */
    public AuthStore(Path dataDir, int tokenDays) throws IOException {
        this.authDir = dataDir.toAbsolutePath().resolve("auth");
        this.softwareFile = authDir.resolve("software.tsv");
        this.usersFile = authDir.resolve("users.tsv");
        this.membershipsFile = authDir.resolve("memberships.tsv");
        this.cardsFile = authDir.resolve("cards.tsv");
        this.tokenKeyFile = authDir.resolve("token.key");
        this.auditFile = authDir.resolve("audit.log");
        this.tokenDays = tokenDays;
        Files.createDirectories(authDir);
        this.tokenKey = loadOrCreateTokenKey();
        loadSoftware();
        loadUsers();
        loadMemberships();
        loadCards();
        loadAudit();
    }

    // ------------------------------------------------------------------
    // Data records
    // ------------------------------------------------------------------

    /** A software product managed by this auth server. */
    public static final class Software {
        public final String id;
        public final String name;
        public final String group;
        public final String description;
        public final boolean enabled;
        public final long createdAt;

        Software(String id, String name, String group, String description,
                 boolean enabled, long createdAt) {
            this.id = id;
            this.name = name;
            this.group = group;
            this.description = description;
            this.enabled = enabled;
            this.createdAt = createdAt;
        }

        Software withEnabled(boolean value) {
            return new Software(id, name, group, description, value, createdAt);
        }
    }

    /** A registered end user. */
    public static final class User {
        public final String id;
        public final String username;
        public final String passwordHash;
        public final long createdAt;
        public final boolean disabled;
        /** Bound machine hardware id; empty means "not bound yet" (DESIGN appendix B). */
        public final String hwid;

        User(String id, String username, String passwordHash, long createdAt, boolean disabled,
             String hwid) {
            this.id = id;
            this.username = username;
            this.passwordHash = passwordHash;
            this.createdAt = createdAt;
            this.disabled = disabled;
            this.hwid = hwid == null ? "" : hwid;
        }

        User withDisabled(boolean value) {
            return new User(id, username, passwordHash, createdAt, value, hwid);
        }

        User withHwid(String value) {
            return new User(id, username, passwordHash, createdAt, disabled, value);
        }
    }

    /** An authorization of one user for one software product. */
    public static final class Membership {
        public final String userId;
        public final String username;
        public final String softwareId;
        public final String group;
        public final String cardCode;
        public final long activatedAt;
        /** Expiry in epoch millis; {@code 0} means permanent. */
        public final long expiresAt;
        public final boolean revoked;

        Membership(String userId, String username, String softwareId, String group,
                   String cardCode, long activatedAt, long expiresAt, boolean revoked) {
            this.userId = userId;
            this.username = username;
            this.softwareId = softwareId;
            this.group = group;
            this.cardCode = cardCode;
            this.activatedAt = activatedAt;
            this.expiresAt = expiresAt;
            this.revoked = revoked;
        }

        /** @return {@code active}, {@code expired} or {@code revoked} at {@code now}. */
        public String status(long now) {
            if (revoked) {
                return "revoked";
            }
            if (expiresAt > 0 && expiresAt <= now) {
                return "expired";
            }
            return "active";
        }

        public boolean active(long now) {
            return "active".equals(status(now));
        }

        Membership withRevoked(boolean value) {
            return new Membership(userId, username, softwareId, group, cardCode,
                    activatedAt, expiresAt, value);
        }
    }

    /** A one-time activation code bound to one software. */
    public static final class Card {
        public final String code;
        public final String softwareId;
        public final int durationDays;
        public final String note;
        public final long createdAt;
        public final String status;
        public final String usedByUserId;
        public final String usedByUsername;
        public final long usedAt;
        public final String batchId;

        Card(String code, String softwareId, int durationDays, String note, long createdAt,
             String status, String usedByUserId, String usedByUsername, long usedAt, String batchId) {
            this.code = code;
            this.softwareId = softwareId;
            this.durationDays = durationDays;
            this.note = note;
            this.createdAt = createdAt;
            this.status = status;
            this.usedByUserId = usedByUserId;
            this.usedByUsername = usedByUsername;
            this.usedAt = usedAt;
            this.batchId = batchId;
        }

        Card withStatus(String newStatus, String userId, String username, long at) {
            return new Card(code, softwareId, durationDays, note, createdAt, newStatus,
                    userId, username, at, batchId);
        }
    }

    /** One audit trail entry (also appended to {@code auth/audit.log}). */
    public static final class AuditEvent {
        public final long ts;
        public final String type;
        public final String user;
        public final String software;
        public final String detail;

        AuditEvent(long ts, String type, String user, String software, String detail) {
            this.ts = ts;
            this.type = type;
            this.user = user;
            this.software = software;
            this.detail = detail;
        }
    }

    /** Result of a successful registration. */
    public static final class Registered {
        public final User user;

        Registered(User user) {
            this.user = user;
        }
    }

    /**
     * Result of {@code /api/auth/login} and {@code /api/auth/activate}:
     * either {@code OK} with a token or {@code ACTIVATION_REQUIRED}.
     */
    public static final class LoginResult {
        public final String status;
        public final String token;
        public final long expiresAt;
        public final User user;
        public final Software software;
        public final Membership membership;

        private LoginResult(String status, String token, long expiresAt,
                            User user, Software software, Membership membership) {
            this.status = status;
            this.token = token;
            this.expiresAt = expiresAt;
            this.user = user;
            this.software = software;
            this.membership = membership;
        }

        public boolean isOk() {
            return "OK".equals(status);
        }
    }

    /** Result of {@code /api/auth/session}. */
    public static final class SessionResult {
        public final boolean valid;
        /** {@code OK}, {@code TOKEN_INVALID}, {@code HWID_REQUIRED} or {@code HWID_MISMATCH}. */
        public final String status;
        public final String reason;
        public final User user;
        public final Software software;
        public final Membership membership;
        public final long expiresAt;

        private SessionResult(boolean valid, String status, String reason, User user,
                              Software software, Membership membership, long expiresAt) {
            this.valid = valid;
            this.status = status;
            this.reason = reason;
            this.user = user;
            this.software = software;
            this.membership = membership;
            this.expiresAt = expiresAt;
        }

        static SessionResult invalid(String reason) {
            return new SessionResult(false, "TOKEN_INVALID", reason, null, null, null, 0L);
        }

        static SessionResult invalid(String status, String reason) {
            return new SessionResult(false, status, reason, null, null, null, 0L);
        }
    }

    /** Result of a batch card generation. */
    public static final class CardsGenerated {
        public final List<String> codes;
        public final String batchId;

        CardsGenerated(List<String> codes, String batchId) {
            this.codes = Collections.unmodifiableList(codes);
            this.batchId = batchId;
        }
    }

    /** Aggregate counters for the admin overview tab. */
    public static final class Overview {
        public final int software;
        public final int users;
        public final int cardsUnused;
        public final int cardsUsed;
        public final int cardsRevoked;
        public final int cardsDisabled;
        public final int cardsTotal;
        public final int memberships;
        public final int activeMemberships;
        public final List<Membership> recentActivations;

        Overview(int software, int users, int cardsUnused, int cardsUsed, int cardsRevoked,
                 int cardsDisabled, int cardsTotal, int memberships, int activeMemberships,
                 List<Membership> recentActivations) {
            this.software = software;
            this.users = users;
            this.cardsUnused = cardsUnused;
            this.cardsUsed = cardsUsed;
            this.cardsRevoked = cardsRevoked;
            this.cardsDisabled = cardsDisabled;
            this.cardsTotal = cardsTotal;
            this.memberships = memberships;
            this.activeMemberships = activeMemberships;
            this.recentActivations = recentActivations;
        }
    }

    /** Auth failure carrying the DESIGN error code and matching HTTP status. */
    public static final class AuthException extends Exception {
        private static final long serialVersionUID = 1L;
        public final String status;
        public final int httpStatus;

        public AuthException(String status, int httpStatus, String message) {
            super(message);
            this.status = status;
            this.httpStatus = httpStatus;
        }
    }

    // ------------------------------------------------------------------
    // Read access
    // ------------------------------------------------------------------

    public Path authDir() {
        return authDir;
    }

    public int tokenDays() {
        return tokenDays;
    }

    public synchronized List<Software> softwareList() {
        List<Software> out = new ArrayList<Software>(software.values());
        Collections.sort(out, new Comparator<Software>() {
            @Override
            public int compare(Software a, Software b) {
                return Long.compare(a.createdAt, b.createdAt);
            }
        });
        return out;
    }

    public synchronized List<Software> enabledSoftware() {
        List<Software> out = new ArrayList<Software>();
        for (Software item : softwareList()) {
            if (item.enabled) {
                out.add(item);
            }
        }
        return out;
    }

    public synchronized Software software(String id) {
        return id == null ? null : software.get(id);
    }

    public synchronized List<User> users() {
        List<User> out = new ArrayList<User>(users.values());
        Collections.sort(out, new Comparator<User>() {
            @Override
            public int compare(User a, User b) {
                return Long.compare(a.createdAt, b.createdAt);
            }
        });
        return out;
    }

    public synchronized User user(String id) {
        return id == null ? null : users.get(id);
    }

    public synchronized User userByUsername(String username) {
        return username == null ? null : usersByName.get(username.toLowerCase(Locale.ROOT));
    }

    public synchronized List<Membership> memberships() {
        List<Membership> out = new ArrayList<Membership>(memberships.values());
        Collections.sort(out, new Comparator<Membership>() {
            @Override
            public int compare(Membership a, Membership b) {
                return Long.compare(b.activatedAt, a.activatedAt);
            }
        });
        return out;
    }

    public synchronized Membership membership(String userId, String softwareId) {
        if (userId == null || softwareId == null) {
            return null;
        }
        return memberships.get(userId + "|" + softwareId);
    }

    public synchronized List<Membership> membershipsOf(String userId) {
        List<Membership> out = new ArrayList<Membership>();
        for (Membership item : memberships.values()) {
            if (item.userId.equals(userId)) {
                out.add(item);
            }
        }
        Collections.sort(out, new Comparator<Membership>() {
            @Override
            public int compare(Membership a, Membership b) {
                return Long.compare(a.activatedAt, b.activatedAt);
            }
        });
        return out;
    }

    public synchronized List<Card> cards() {
        List<Card> out = new ArrayList<Card>(cards.values());
        Collections.sort(out, new Comparator<Card>() {
            @Override
            public int compare(Card a, Card b) {
                int byCreated = Long.compare(b.createdAt, a.createdAt);
                return byCreated != 0 ? byCreated : a.code.compareTo(b.code);
            }
        });
        return out;
    }

    /** @param softwareId optional filter; @param status optional filter. */
    public synchronized List<Card> cards(String softwareId, String status) {
        List<Card> out = new ArrayList<Card>();
        for (Card card : cards()) {
            if (softwareId != null && !softwareId.isEmpty() && !softwareId.equals(card.softwareId)) {
                continue;
            }
            if (status != null && !status.isEmpty() && !status.equals(card.status)) {
                continue;
            }
            out.add(card);
        }
        return out;
    }

    public synchronized Card card(String code) {
        return code == null ? null : cards.get(code);
    }

    public synchronized List<AuditEvent> auditEvents() {
        return Collections.unmodifiableList(new ArrayList<AuditEvent>(audit));
    }

    public synchronized Overview overview() {
        long now = System.currentTimeMillis();
        int unused = 0;
        int used = 0;
        int revoked = 0;
        int disabled = 0;
        for (Card card : cards.values()) {
            if (CARD_UNUSED.equals(card.status)) {
                unused++;
            } else if (CARD_USED.equals(card.status)) {
                used++;
            } else if (CARD_REVOKED.equals(card.status)) {
                revoked++;
            } else if (CARD_DISABLED.equals(card.status)) {
                disabled++;
            }
        }
        int activeMemberships = 0;
        for (Membership item : memberships.values()) {
            if (item.active(now)) {
                activeMemberships++;
            }
        }
        List<Membership> recent = new ArrayList<Membership>(memberships.values());
        Collections.sort(recent, new Comparator<Membership>() {
            @Override
            public int compare(Membership a, Membership b) {
                return Long.compare(b.activatedAt, a.activatedAt);
            }
        });
        if (recent.size() > 10) {
            recent = new ArrayList<Membership>(recent.subList(0, 10));
        }
        return new Overview(software.size(), users.size(), unused, used, revoked, disabled,
                cards.size(), memberships.size(), activeMemberships, recent);
    }

    // ------------------------------------------------------------------
    // Rate limiting
    // ------------------------------------------------------------------

    /**
     * Records one client auth attempt for {@code ip}. Returns {@code false}
     * when the caller must answer {@code 429 RATE_LIMITED}.
     */
    public synchronized boolean allowAuthAttempt(String ip) {
        String key = ip == null || ip.isEmpty() ? "unknown" : ip;
        long now = System.currentTimeMillis();
        Deque<Long> window = rateWindows.get(key);
        if (window == null) {
            window = new ArrayDeque<Long>();
            rateWindows.put(key, window);
        }
        long cutoff = now - (RATE_WINDOW_SECONDS * 1000L);
        while (!window.isEmpty() && window.peekFirst() < cutoff) {
            window.pollFirst();
        }
        if (window.size() >= RATE_LIMIT) {
            return false;
        }
        window.addLast(now);
        if (rateWindows.size() > 10000) {
            pruneRateWindows(cutoff);
        }
        return true;
    }

    private void pruneRateWindows(long cutoff) {
        List<String> stale = new ArrayList<String>();
        for (Map.Entry<String, Deque<Long>> entry : rateWindows.entrySet()) {
            Deque<Long> window = entry.getValue();
            while (!window.isEmpty() && window.peekFirst() < cutoff) {
                window.pollFirst();
            }
            if (window.isEmpty()) {
                stale.add(entry.getKey());
            }
        }
        for (String key : stale) {
            rateWindows.remove(key);
        }
    }

    // ------------------------------------------------------------------
    // Client operations
    // ------------------------------------------------------------------

    /** Registers a new user. */
    public synchronized Registered register(String username, String password) throws AuthException {
        String cleanUsername = requireUsername(username);
        requirePassword(password);
        if (usersByName.containsKey(cleanUsername.toLowerCase(Locale.ROOT))) {
            throw new AuthException("USERNAME_TAKEN", 409, "Username is already registered");
        }
        String id = uniqueUserId();
        User user = new User(id, cleanUsername, AuthCrypto.hashPassword(password),
                System.currentTimeMillis(), false, "");
        users.put(id, user);
        usersByName.put(cleanUsername.toLowerCase(Locale.ROOT), user);
        persistUsers();
        recordAudit("register", cleanUsername, "", "userId=" + id);
        return new Registered(user);
    }

    /**
     * Authenticates a user and issues a token when the software authorization is active.
     *
     * <p>HWID binding (DESIGN appendix B): the {@code hwid} parameter is mandatory;
     * a different value than the stored binding is rejected with
     * {@code 403 HWID_MISMATCH}; the first successful login (token issued) binds an
     * unbound account to the requesting machine.</p>
     */
    public synchronized LoginResult login(String username, String password, String softwareId,
                                          String hwid) throws AuthException {
        String cleanUsername = requireUsername(username);
        if (password == null) {
            throw new AuthException("BAD_REQUEST", 400, "password is required");
        }
        String cleanHwid = requireHwid(hwid);
        Software sw = requireSoftware(softwareId);
        User user = usersByName.get(cleanUsername.toLowerCase(Locale.ROOT));
        if (user == null) {
            // Burn the same PBKDF2 work as a real verification to avoid user enumeration.
            AuthCrypto.burnPasswordCheck(password);
            throw new AuthException("INVALID_CREDENTIALS", 401, "Invalid username or password");
        }
        if (!AuthCrypto.verifyPassword(password, user.passwordHash)) {
            throw new AuthException("INVALID_CREDENTIALS", 401, "Invalid username or password");
        }
        if (user.disabled) {
            throw new AuthException("USER_DISABLED", 403, "User account is disabled");
        }
        requireHwidMatch(user, cleanHwid);
        long now = System.currentTimeMillis();
        Membership membership = memberships.get(user.id + "|" + sw.id);
        if (membership == null || !membership.active(now)) {
            recordAudit("login", user.username, sw.id, "activation required");
            return new LoginResult("ACTIVATION_REQUIRED", null, 0L, user, sw, null);
        }
        User effective = user;
        if (user.hwid.isEmpty()) {
            // First successful login binds the account to this machine.
            effective = bindHwid(user, sw.id, cleanHwid);
        }
        recordAudit("login", user.username, sw.id, "token issued");
        return issueToken(effective, sw, membership, now);
    }

    /** Activates a card for a user and issues a token. */
    public synchronized LoginResult activate(String username, String password, String cardCode,
                                             String softwareId, String hwid) throws AuthException {
        String cleanUsername = requireUsername(username);
        if (password == null) {
            throw new AuthException("BAD_REQUEST", 400, "password is required");
        }
        if (cardCode == null || cardCode.trim().isEmpty()) {
            throw new AuthException("BAD_REQUEST", 400, "card is required");
        }
        String cleanCode = cardCode.trim().toUpperCase(Locale.ROOT);
        String cleanHwid = requireHwid(hwid);
        Software sw = requireSoftware(softwareId);
        User user = usersByName.get(cleanUsername.toLowerCase(Locale.ROOT));
        if (user == null) {
            AuthCrypto.burnPasswordCheck(password);
            throw new AuthException("INVALID_CREDENTIALS", 401, "Invalid username or password");
        }
        if (!AuthCrypto.verifyPassword(password, user.passwordHash)) {
            throw new AuthException("INVALID_CREDENTIALS", 401, "Invalid username or password");
        }
        if (user.disabled) {
            throw new AuthException("USER_DISABLED", 403, "User account is disabled");
        }
        requireHwidMatch(user, cleanHwid);
        Card card = cards.get(cleanCode);
        if (card == null) {
            throw new AuthException("CARD_INVALID", 404, "Card code is not valid");
        }
        if (!sw.id.equals(card.softwareId)) {
            throw new AuthException("CARD_SOFTWARE_MISMATCH", 409,
                    "Card belongs to software '" + card.softwareId + "', not '" + sw.id + "'");
        }
        if (CARD_USED.equals(card.status)) {
            throw new AuthException("CARD_USED", 409, "Card code was already used");
        }
        if (CARD_REVOKED.equals(card.status)) {
            throw new AuthException("CARD_REVOKED", 409, "Card code was revoked");
        }
        if (CARD_DISABLED.equals(card.status)) {
            throw new AuthException("CARD_DISABLED", 409, "Card code is disabled");
        }
        if (!CARD_UNUSED.equals(card.status)) {
            throw new AuthException("CARD_INVALID", 409, "Card code cannot be activated");
        }
        long now = System.currentTimeMillis();
        User effective = user;
        if (user.hwid.isEmpty()) {
            // First successful activation binds the account to this machine.
            effective = bindHwid(user, sw.id, cleanHwid);
        }
        long expiresAt = card.durationDays > 0 ? now + card.durationDays * DAY_MILLIS : 0L;
        Membership existing = memberships.get(effective.id + "|" + sw.id);
        Membership membership = new Membership(effective.id, effective.username, sw.id, sw.group,
                card.code, now, expiresAt, false);
        memberships.put(effective.id + "|" + sw.id, membership);
        Card usedCard = card.withStatus(CARD_USED, effective.id, effective.username, now);
        cards.put(card.code, usedCard);
        persistMemberships();
        persistCards();
        recordAudit("activate", effective.username, sw.id,
                "card=" + card.code + (existing == null ? "" : " (renewed)"));
        return issueToken(effective, sw, membership, now);
    }

    /**
     * Stateless token verification for {@code /api/auth/session}.
     *
     * <p>Checks the mandatory {@code hwid} (DESIGN appendix B), signature
     * (HMAC-SHA256), expiry, token/software binding, user disabled flag, HWID
     * binding, membership revocation/expiry and software enabled flag.</p>
     */
    public synchronized SessionResult session(String token, String softwareId, String hwid) {
        String cleanHwid = hwid == null ? "" : hwid.trim();
        if (!HWID_PATTERN.matcher(cleanHwid).matches()) {
            return SessionResult.invalid("HWID_REQUIRED", HWID_REQUIRED_MESSAGE);
        }
        String expectedSoftware = softwareId == null ? "" : softwareId.trim();
        if (token == null || token.trim().isEmpty()) {
            return SessionResult.invalid("Missing token");
        }
        String[] parts = token.trim().split("\\.", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return SessionResult.invalid("Malformed token");
        }
        byte[] providedSignature;
        try {
            providedSignature = AuthCrypto.base64UrlDecode(parts[1]);
        } catch (IllegalArgumentException e) {
            return SessionResult.invalid("Malformed token signature");
        }
        String expectedSignature = AuthCrypto.hmacSha256(tokenKey, parts[0]);
        byte[] expected;
        try {
            expected = AuthCrypto.base64UrlDecode(expectedSignature);
        } catch (IllegalArgumentException e) {
            return SessionResult.invalid("Token verification failed");
        }
        if (!MessageDigest.isEqual(expected, providedSignature)) {
            return SessionResult.invalid("Bad token signature");
        }
        String payload;
        try {
            payload = new String(AuthCrypto.base64UrlDecode(parts[0]), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return SessionResult.invalid("Malformed token payload");
        }
        String[] fields = payload.split("\\|", -1);
        if (fields.length != 7) {
            return SessionResult.invalid("Malformed token payload");
        }
        String userId = fields[0];
        String username = fields[1];
        String softwareTokenId = fields[2];
        String group = fields[3];
        long expiresAt;
        try {
            Long.parseLong(fields[4]);
            expiresAt = Long.parseLong(fields[5]);
        } catch (NumberFormatException e) {
            return SessionResult.invalid("Malformed token times");
        }
        long now = System.currentTimeMillis();
        if (expiresAt <= now) {
            return SessionResult.invalid("Token expired");
        }
        if (!expectedSoftware.isEmpty() && !expectedSoftware.equals(softwareTokenId)) {
            return SessionResult.invalid("Token does not belong to software '" + expectedSoftware + "'");
        }
        Software sw = software.get(softwareTokenId);
        if (sw == null) {
            return SessionResult.invalid("Unknown software");
        }
        if (!sw.enabled) {
            return SessionResult.invalid("Software disabled");
        }
        User user = users.get(userId);
        if (user == null) {
            return SessionResult.invalid("Unknown user");
        }
        if (!user.username.equals(username)) {
            return SessionResult.invalid("Token user mismatch");
        }
        if (user.disabled) {
            return SessionResult.invalid("User disabled");
        }
        Membership membership = memberships.get(userId + "|" + softwareTokenId);
        if (membership == null) {
            return SessionResult.invalid("Authorization missing");
        }
        if (membership.revoked) {
            return SessionResult.invalid("Authorization revoked");
        }
        if (membership.expiresAt > 0 && membership.expiresAt <= now) {
            return SessionResult.invalid("Authorization expired");
        }
        if (!membership.group.equals(group)) {
            return SessionResult.invalid("Authorization group changed");
        }
        if (!user.hwid.isEmpty() && !user.hwid.equals(cleanHwid)) {
            return SessionResult.invalid("HWID_MISMATCH", HWID_MISMATCH_MESSAGE);
        }
        return new SessionResult(true, "OK", "OK", user, sw, membership, expiresAt);
    }

    // ------------------------------------------------------------------
    // Admin operations
    // ------------------------------------------------------------------

    public synchronized Software createSoftware(String id, String name, String group,
                                                String description) throws AuthException {
        String cleanId = id == null ? "" : id.trim();
        if (!SOFTWARE_ID_PATTERN.matcher(cleanId).matches()) {
            throw new AuthException("BAD_REQUEST", 400,
                    "Software id must match [a-z0-9_-]{2,32}");
        }
        if (software.containsKey(cleanId)) {
            throw new AuthException("BAD_REQUEST", 409, "Software id already exists");
        }
        String cleanName = cleanField(name, 64);
        if (cleanName.isEmpty()) {
            throw new AuthException("BAD_REQUEST", 400, "name is required");
        }
        String cleanGroup = group == null ? "" : group.trim();
        if (cleanGroup.isEmpty()) {
            cleanGroup = "grp-" + cleanId;
        }
        if (!GROUP_PATTERN.matcher(cleanGroup).matches()) {
            throw new AuthException("BAD_REQUEST", 400,
                    "group must match [A-Za-z0-9_.:-]{1,64} (no '|')");
        }
        Software created = new Software(cleanId, cleanName, cleanGroup,
                cleanField(description, 200), true, System.currentTimeMillis());
        software.put(cleanId, created);
        persistSoftware();
        recordAudit("software.create", "", cleanId, "group=" + cleanGroup + " name=" + cleanName);
        return created;
    }

    public synchronized Software toggleSoftware(String id) throws AuthException {
        Software current = software.get(id);
        if (current == null) {
            throw new AuthException("SOFTWARE_UNKNOWN", 404, "Unknown software id");
        }
        Software updated = current.withEnabled(!current.enabled);
        software.put(id, updated);
        persistSoftware();
        recordAudit("software.toggle", "", id, updated.enabled ? "enabled" : "disabled");
        return updated;
    }

    public synchronized CardsGenerated generateCards(String softwareId, int count,
                                                     int durationDays, String note)
            throws AuthException {
        Software sw = software.get(softwareId);
        if (sw == null) {
            throw new AuthException("SOFTWARE_UNKNOWN", 404, "Unknown software id");
        }
        if (count < 1 || count > 200) {
            throw new AuthException("BAD_REQUEST", 400, "count must be between 1 and 200");
        }
        if (durationDays < 0 || durationDays > 36500) {
            throw new AuthException("BAD_REQUEST", 400,
                    "durationDays must be between 0 and 36500");
        }
        String cleanNote = cleanField(note, 200);
        long now = System.currentTimeMillis();
        String batchId = "b-" + Long.toString(now, 36) + "-" + AuthCrypto.randomHex(2);
        List<String> codes = new ArrayList<String>();
        String prefix = AuthCrypto.normalizePrefix(sw.id);
        while (codes.size() < count) {
            String code = AuthCrypto.newCardCode(prefix);
            if (cards.containsKey(code)) {
                continue;
            }
            Card card = new Card(code, sw.id, durationDays, cleanNote, now,
                    CARD_UNUSED, null, null, 0L, batchId);
            cards.put(code, card);
            codes.add(code);
        }
        persistCards();
        recordAudit("cards.generate", "", sw.id,
                "batch=" + batchId + " count=" + count + " days=" + durationDays);
        return new CardsGenerated(codes, batchId);
    }

    public synchronized Card revokeCard(String code) throws AuthException {
        Card card = cards.get(code == null ? null : code.trim().toUpperCase(Locale.ROOT));
        if (card == null) {
            throw new AuthException("CARD_INVALID", 404, "Card code is not valid");
        }
        if (CARD_USED.equals(card.status)) {
            throw new AuthException("CARD_USED", 409, "Used cards cannot be revoked");
        }
        if (CARD_REVOKED.equals(card.status)) {
            throw new AuthException("CARD_REVOKED", 409, "Card code is already revoked");
        }
        if (CARD_DISABLED.equals(card.status)) {
            throw new AuthException("CARD_DISABLED", 409, "Card code is disabled");
        }
        Card revoked = card.withStatus(CARD_REVOKED, card.usedByUserId, card.usedByUsername, 0L);
        cards.put(card.code, revoked);
        persistCards();
        recordAudit("card.revoke", "", card.softwareId, "card=" + card.code);
        return revoked;
    }

    public synchronized User toggleUser(String userId) throws AuthException {
        User current = users.get(userId);
        if (current == null) {
            throw new AuthException("USER_UNKNOWN", 404, "Unknown user id");
        }
        User updated = current.withDisabled(!current.disabled);
        users.put(userId, updated);
        usersByName.put(updated.username.toLowerCase(Locale.ROOT), updated);
        persistUsers();
        recordAudit("user.toggle", updated.username, "", updated.disabled ? "disabled" : "enabled");
        return updated;
    }

    public synchronized Membership revokeMembership(String userId, String softwareId)
            throws AuthException {
        User user = users.get(userId);
        if (user == null) {
            throw new AuthException("USER_UNKNOWN", 404, "Unknown user id");
        }
        if (software.get(softwareId) == null) {
            throw new AuthException("SOFTWARE_UNKNOWN", 404, "Unknown software id");
        }
        Membership membership = memberships.get(userId + "|" + softwareId);
        if (membership == null) {
            throw new AuthException("BAD_REQUEST", 404, "User has no authorization for this software");
        }
        Membership revoked = membership.withRevoked(true);
        memberships.put(userId + "|" + softwareId, revoked);
        persistMemberships();
        recordAudit("membership.revoke", user.username, softwareId, "group=" + membership.group);
        return revoked;
    }

    /**
     * Clears the account HWID binding (admin action, DESIGN appendix B.1).
     * The next successful login/activate from any machine binds again.
     */
    public synchronized User resetHwid(String userId) throws AuthException {
        User user = users.get(userId);
        if (user == null) {
            throw new AuthException("USER_UNKNOWN", 404, "Unknown user id");
        }
        String previous = user.hwid;
        User updated = user.withHwid("");
        users.put(userId, updated);
        usersByName.put(updated.username.toLowerCase(Locale.ROOT), updated);
        persistUsers();
        recordAudit("hwid_reset", user.username, "", "previous=" + (previous.isEmpty() ? "" : previous));
        return updated;
    }

    /** Manually sets/rebinds the account HWID (admin action, DESIGN appendix B.1). */
    public synchronized User setHwid(String userId, String hwid) throws AuthException {
        User user = users.get(userId);
        if (user == null) {
            throw new AuthException("USER_UNKNOWN", 404, "Unknown user id");
        }
        String clean = hwid == null ? "" : hwid.trim();
        if (!HWID_PATTERN.matcher(clean).matches()) {
            throw new AuthException("BAD_REQUEST", 400, HWID_REQUIRED_MESSAGE);
        }
        User updated = user.withHwid(clean);
        users.put(userId, updated);
        usersByName.put(updated.username.toLowerCase(Locale.ROOT), updated);
        persistUsers();
        recordAudit("hwid_set", user.username, "", "hwid=" + clean);
        return updated;
    }

    /** Appends an event to the in-memory audit ring and {@code auth/audit.log}. */
    public synchronized void recordAudit(String type, String user, String software, String detail) {
        AuditEvent event = new AuditEvent(System.currentTimeMillis(), cleanField(type, 40),
                cleanField(user, 64), cleanField(software, 32), cleanField(detail, 300));
        audit.addFirst(event);
        while (audit.size() > AUDIT_CAPACITY) {
            audit.pollLast();
        }
        appendAuditFile(event);
    }

    // ------------------------------------------------------------------
    // Token issuing
    // ------------------------------------------------------------------

    private LoginResult issueToken(User user, Software sw, Membership membership, long now) {
        long ttl = tokenDays <= 0 ? 0L : tokenDays * DAY_MILLIS;
        long expiresAt = now + ttl;
        String jti = AuthCrypto.randomHex(8);
        String payload = user.id + "|" + user.username + "|" + sw.id + "|"
                + membership.group + "|" + now + "|" + expiresAt + "|" + jti;
        String payloadPart = AuthCrypto.base64Url(payload.getBytes(StandardCharsets.UTF_8));
        String signature = AuthCrypto.hmacSha256(tokenKey, payloadPart);
        return new LoginResult("OK", payloadPart + "." + signature, expiresAt,
                user, sw, membership);
    }

    // ------------------------------------------------------------------
    // Validation helpers
    // ------------------------------------------------------------------

    private static String requireUsername(String username) throws AuthException {
        String clean = username == null ? "" : username.trim();
        if (!USERNAME_PATTERN.matcher(clean).matches()) {
            throw new AuthException("BAD_REQUEST", 400,
                    "username must match [A-Za-z0-9_.-]{3,32}");
        }
        return clean;
    }

    private static void requirePassword(String password) throws AuthException {
        if (password == null || password.length() < 6) {
            throw new AuthException("BAD_REQUEST", 400, "password must be at least 6 characters");
        }
        if (password.length() > 1024) {
            throw new AuthException("BAD_REQUEST", 400, "password is too long");
        }
    }

    /** Validates the mandatory client HWID parameter (DESIGN appendix B.1). */
    private static String requireHwid(String hwid) throws AuthException {
        String clean = hwid == null ? "" : hwid.trim();
        if (!HWID_PATTERN.matcher(clean).matches()) {
            throw new AuthException("HWID_REQUIRED", 400, HWID_REQUIRED_MESSAGE);
        }
        return clean;
    }

    /** Rejects a request from a machine other than the account's bound HWID. */
    private void requireHwidMatch(User user, String cleanHwid) throws AuthException {
        if (!user.hwid.isEmpty() && !user.hwid.equals(cleanHwid)) {
            throw new AuthException("HWID_MISMATCH", 403, HWID_MISMATCH_MESSAGE);
        }
    }

    /** Binds an unbound account to the requesting machine and persists it. */
    private User bindHwid(User user, String softwareId, String hwid) throws AuthException {
        User bound = user.withHwid(hwid);
        users.put(bound.id, bound);
        usersByName.put(bound.username.toLowerCase(Locale.ROOT), bound);
        persistUsers();
        recordAudit("hwid_bind", bound.username, softwareId, "hwid=" + hwid);
        return bound;
    }

    private Software requireSoftware(String softwareId) throws AuthException {
        String clean = softwareId == null ? "" : softwareId.trim();
        if (clean.isEmpty()) {
            throw new AuthException("BAD_REQUEST", 400, "software is required");
        }
        Software sw = software.get(clean);
        if (sw == null) {
            throw new AuthException("SOFTWARE_UNKNOWN", 404, "Unknown software id");
        }
        if (!sw.enabled) {
            throw new AuthException("SOFTWARE_DISABLED", 403, "Software is disabled");
        }
        return sw;
    }

    /** Strips control characters and tabs/newlines, trims and truncates a free-text field. */
    private static String cleanField(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(Math.min(value.length(), maxLength));
        for (int i = 0; i < value.length() && out.length() < maxLength; i++) {
            char c = value.charAt(i);
            if (c == '\t' || c == '\r' || c == '\n' || c < 0x20) {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString().trim();
    }

    private String uniqueUserId() {
        String id;
        do {
            id = AuthCrypto.newId("u");
        } while (users.containsKey(id));
        return id;
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    private byte[] loadOrCreateTokenKey() throws IOException {
        if (Files.exists(tokenKeyFile)) {
            byte[] existing = Files.readAllBytes(tokenKeyFile);
            if (existing.length == AuthCrypto.TOKEN_KEY_BYTES) {
                return existing;
            }
        }
        byte[] generated = AuthCrypto.randomBytes(AuthCrypto.TOKEN_KEY_BYTES);
        writeAtomic(tokenKeyFile, generated);
        return generated;
    }

    private void loadSoftware() throws IOException {
        if (!Files.exists(softwareFile)) {
            return;
        }
        for (String line : Files.readAllLines(softwareFile, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 6) {
                continue;
            }
            software.put(parts[0], new Software(parts[0], parts[1], parts[2], parts[3],
                    parseBool(parts[4]), parseLong(parts[5], 0L)));
        }
    }

    private void loadUsers() throws IOException {
        if (!Files.exists(usersFile)) {
            return;
        }
        for (String line : Files.readAllLines(usersFile, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 5) {
                continue;
            }
            // Backward compatible with v1 rows (5 columns); the 6th hwid column
            // was added by DESIGN appendix B and defaults to "not bound".
            String hwid = parts.length >= 6 ? parts[5] : "";
            User user = new User(parts[0], parts[1], parts[2],
                    parseLong(parts[3], 0L), parseBool(parts[4]), hwid);
            users.put(user.id, user);
            usersByName.put(user.username.toLowerCase(Locale.ROOT), user);
        }
    }

    private void loadMemberships() throws IOException {
        if (!Files.exists(membershipsFile)) {
            return;
        }
        for (String line : Files.readAllLines(membershipsFile, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 8) {
                continue;
            }
            Membership membership = new Membership(parts[0], parts[1], parts[2], parts[3],
                    parts[4], parseLong(parts[5], 0L), parseLong(parts[6], 0L),
                    parseBool(parts[7]));
            memberships.put(membership.userId + "|" + membership.softwareId, membership);
        }
    }

    private void loadCards() throws IOException {
        if (!Files.exists(cardsFile)) {
            return;
        }
        for (String line : Files.readAllLines(cardsFile, StandardCharsets.UTF_8)) {
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 10) {
                continue;
            }
            Card card = new Card(parts[0], parts[1], parseInt(parts[2], 0), parts[3],
                    parseLong(parts[4], 0L), parts[5],
                    emptyToNull(parts[6]), emptyToNull(parts[7]), parseLong(parts[8], 0L),
                    parts[9]);
            cards.put(card.code, card);
        }
    }

    private void loadAudit() throws IOException {
        if (!Files.exists(auditFile)) {
            return;
        }
        List<String> lines = Files.readAllLines(auditFile, StandardCharsets.UTF_8);
        int start = Math.max(0, lines.size() - AUDIT_CAPACITY);
        for (int i = start; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            String[] parts = line.split("\t", -1);
            if (parts.length < 5) {
                continue;
            }
            AuditEvent event = new AuditEvent(parseLong(parts[0], 0L), parts[1], parts[2],
                    parts[3], parts[4]);
            audit.addFirst(event);
        }
    }

    private void persistSoftware() throws AuthException {
        StringBuilder out = new StringBuilder();
        for (Software item : software.values()) {
            out.append(item.id).append('\t').append(item.name).append('\t')
                    .append(item.group).append('\t').append(item.description).append('\t')
                    .append(item.enabled ? '1' : '0').append('\t')
                    .append(item.createdAt).append('\n');
        }
        writeOrFail(softwareFile, out.toString());
    }

    private void persistUsers() throws AuthException {
        StringBuilder out = new StringBuilder();
        for (User user : users.values()) {
            out.append(user.id).append('\t').append(user.username).append('\t')
                    .append(user.passwordHash).append('\t').append(user.createdAt).append('\t')
                    .append(user.disabled ? '1' : '0').append('\t').append(user.hwid)
                    .append('\n');
        }
        writeOrFail(usersFile, out.toString());
    }

    private void persistMemberships() throws AuthException {
        StringBuilder out = new StringBuilder();
        for (Membership item : memberships.values()) {
            out.append(item.userId).append('\t').append(item.username).append('\t')
                    .append(item.softwareId).append('\t').append(item.group).append('\t')
                    .append(item.cardCode).append('\t').append(item.activatedAt).append('\t')
                    .append(item.expiresAt).append('\t').append(item.revoked ? '1' : '0')
                    .append('\n');
        }
        writeOrFail(membershipsFile, out.toString());
    }

    private void persistCards() throws AuthException {
        StringBuilder out = new StringBuilder();
        for (Card card : cards.values()) {
            out.append(card.code).append('\t').append(card.softwareId).append('\t')
                    .append(card.durationDays).append('\t').append(card.note).append('\t')
                    .append(card.createdAt).append('\t').append(card.status).append('\t')
                    .append(card.usedByUserId == null ? "" : card.usedByUserId).append('\t')
                    .append(card.usedByUsername == null ? "" : card.usedByUsername).append('\t')
                    .append(card.usedAt).append('\t').append(card.batchId).append('\n');
        }
        writeOrFail(cardsFile, out.toString());
    }

    private void writeOrFail(Path file, String content) throws AuthException {
        try {
            writeAtomic(file, content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AuthException("SERVER_ERROR", 500,
                    "Unable to persist " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private void appendAuditFile(AuditEvent event) {
        String line = event.ts + "\t" + event.type + "\t" + event.user + "\t"
                + event.software + "\t" + event.detail + "\n";
        try {
            Files.write(auditFile, line.getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            // Audit logging is best-effort; never fail an operation because of it.
        }
    }

    /** Atomic file replacement: write to a sibling tmp file, then move it into place. */
    private static void writeAtomic(Path file, byte[] content) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Files.write(tmp, content);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static boolean parseBool(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
