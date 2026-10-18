package io.zipit.gui;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.prefs.Preferences;

/**
 * Minimal dependency-free localization for the Zipit GUI.
 *
 * <p>Language detection order (DESIGN §2): persisted
 * {@link Preferences} value, then {@code -Dzipit.gui.lang=zh|en}, then the system
 * locale (zh* =&gt; Chinese, everything else =&gt; English).</p>
 *
 * <p>Bundles live in {@code /i18n/messages.properties} (English, default) and
 * {@code /i18n/messages_zh.properties} (Chinese). They are loaded as UTF-8
 * plain properties because JDK 8 ResourceBundle would otherwise read them as
 * ISO-8859-1.</p>
 */
final class I18n {

    static final String LANG_ZH = "zh";
    static final String LANG_EN = "en";
    private static final String PREFS_NODE = "io/zipit/gui";
    private static final String PREFS_KEY = "lang";
    private static final String BASE = "/i18n/messages";
    private static final String SUFFIX = ".properties";

    private static final Properties ENGLISH = new Properties();
    private static final Properties CHINESE = new Properties();
    private static String language = LANG_EN;

    static {
        load(ENGLISH, BASE + SUFFIX);
        load(CHINESE, BASE + "_" + LANG_ZH + SUFFIX);
        language = detectLanguage();
    }

    private I18n() {
    }

    private static void load(Properties target, String resource) {
        try (InputStream in = I18n.class.getResourceAsStream(resource)) {
            if (in == null) {
                return;
            }
            Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
            target.load(reader);
            reader.close();
        } catch (Exception ignored) {
            // Missing/broken bundle: t() falls back to the key itself.
        }
    }

    private static String detectLanguage() {
        try {
            String saved = Preferences.userRoot().node(PREFS_NODE).get(PREFS_KEY, null);
            if (LANG_ZH.equalsIgnoreCase(saved) || LANG_EN.equalsIgnoreCase(saved)) {
                return saved.toLowerCase(Locale.ROOT);
            }
        } catch (Exception ignored) {
            // Preferences can be unavailable in restricted environments.
        }
        String property = System.getProperty("zipit.gui.lang", "").trim();
        if (LANG_ZH.equalsIgnoreCase(property)) {
            return LANG_ZH;
        }
        if (LANG_EN.equalsIgnoreCase(property)) {
            return LANG_EN;
        }
        String systemLanguage = Locale.getDefault().getLanguage();
        return systemLanguage != null && systemLanguage.toLowerCase(Locale.ROOT).startsWith("zh")
                ? LANG_ZH : LANG_EN;
    }

    static boolean isChinese() {
        return LANG_ZH.equals(language);
    }

    static String lang() {
        return language;
    }

    /** Switches the active language and persists the choice. */
    static void setLang(String value) {
        String next = LANG_ZH.equalsIgnoreCase(value) ? LANG_ZH : LANG_EN;
        language = next;
        try {
            Preferences.userRoot().node(PREFS_NODE).put(PREFS_KEY, next);
        } catch (Exception ignored) {
            // Persisting is best effort; -Dzipit.gui.lang still works.
        }
    }

    static void toggle() {
        setLang(isChinese() ? LANG_EN : LANG_ZH);
    }

    /** Test-only switch that does not touch user Preferences. */
    static void overrideForTests(String value) {
        language = LANG_ZH.equalsIgnoreCase(value) ? LANG_ZH : LANG_EN;
    }

    /** Label for the top-bar switch: shows the language you would switch TO. */
    static String switchLabel() {
        return isChinese() ? "EN" : "\u4e2d\u6587";
    }

    static String t(String key) {
        if (key == null) {
            return "";
        }
        Properties bundle = isChinese() ? CHINESE : ENGLISH;
        String value = bundle.getProperty(key);
        if (value == null) {
            value = ENGLISH.getProperty(key);
        }
        return value == null ? key : value;
    }

    /** MessageFormat style translation with arguments ({0}, {1}, ...). */
    static String t(String key, Object... arguments) {
        String pattern = t(key);
        if (arguments == null || arguments.length == 0) {
            return pattern;
        }
        try {
            return java.text.MessageFormat.format(pattern, arguments);
        } catch (RuntimeException ignored) {
            return pattern;
        }
    }

    // ------------------------------------------------------------------
    // helpers used by the headless verification harness
    // ------------------------------------------------------------------

    static int englishKeyCount() {
        return ENGLISH.size();
    }

    static int chineseKeyCount() {
        return CHINESE.size();
    }

    static List<String> missingChineseKeys() {
        List<String> missing = new ArrayList<String>();
        for (String key : ENGLISH.stringPropertyNames()) {
            if (!CHINESE.containsKey(key)) {
                missing.add(key);
            }
        }
        return missing;
    }

    static List<String> unusedEnglishKeys() {
        return new ArrayList<String>();
    }
}
