package io.zipit.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.LinearGradientPaint;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * Zipit "restrained classic" visual identity: parchment/ink palette, antique
 * bronze accents, serif display type and hairline rules.
 *
 * <p>All custom-painted components read the mutable static palette fields at
 * paint time, so a theme switch only needs to refresh the look and feel defaults
 * plus a repaint.</p>
 */
final class Theme {

    // ---- Mutable palette, per DESIGN 1.1 ----
    static Color BG = new Color(0x14110D);
    static Color BG2 = new Color(0x14110D);
    static Color CARD = new Color(0x1C1813);
    static Color CARD_ALT = new Color(0x241F18);
    static Color CARD_HOVER = new Color(0x2A241C);
    static Color BORDER = new Color(0x322B22);
    static Color BORDER_STRONG = new Color(0x453B2E);
    static Color TEXT = new Color(0xE9E1D2);
    static Color MUTED = new Color(0xA29682);
    static Color FAINT = new Color(0x7A7061);
    static Color ACCENT = new Color(0xC7A76C);
    static Color ACCENT2 = new Color(0xD8BC85);
    static Color ACCENT_SOFT = new Color(199, 167, 108, 34);
    static Color SUCCESS = new Color(0x8AA67F);
    static Color WARNING = new Color(0xC4A45E);
    static Color DANGER = new Color(0xC8695C);
    static Color INFO = new Color(0x9BAEBB);
    static Color TERMINAL = new Color(0x0F0C09);
    static Color SHADOW = new Color(0, 0, 0, 90);
    static Color NAV_MUTED = new Color(0xB0A48E);

    static boolean dark = true;

    /** Explicit UI font sizes (integer pt, never below 12). */
    static final int BASE_SIZE = 14;
    static final int MONO_SIZE = 13;
    static final int MIN_SIZE = 12;

    private static Font baseFont;
    private static Font monoFont;
    private static Font serifFont;
    private static final List<Runnable> LISTENERS = new ArrayList<Runnable>();

    private Theme() {
    }

    // ------------------------------------------------------------------
    // theme lifecycle
    // ------------------------------------------------------------------

    static void install() {
        dark = !"light".equalsIgnoreCase(System.getProperty("zipit.gui.theme", "dark"));
        updatePalette();
        applyLookAndFeel();
    }

    static void toggle() {
        setDark(!dark);
    }

    static void setDark(boolean value) {
        if (dark == value) {
            repaintAll();
            return;
        }
        dark = value;
        updatePalette();
        applyLookAndFeel();
        for (Window window : Window.getWindows()) {
            if (window != null) {
                SwingUtilities.updateComponentTreeUI(window);
                window.repaint();
            }
        }
        for (Runnable listener : new ArrayList<Runnable>(LISTENERS)) {
            try {
                listener.run();
            } catch (RuntimeException ignored) {
                // A stale listener must never break the theme switch.
            }
        }
        repaintAll();
    }

    static void addChangeListener(Runnable listener) {
        if (listener != null) {
            LISTENERS.add(listener);
        }
    }

    static void removeChangeListener(Runnable listener) {
        LISTENERS.remove(listener);
    }

    /** Re-resolves the localized fonts (used after a language switch). */
    static void refreshFonts() {
        serifFont = pickSerifFont();
    }

    /**
     * Re-applies palette colors to standard controls whose colors were baked in
     * when they were first created (BasicTextUI keeps a non-null component
     * background across LAF switches). Called after every theme change.
     */
    static void restyle(java.awt.Component component) {
        if (component instanceof javax.swing.JTextPane) {
            return; // console keeps its fixed ink surface in both themes
        }
        if (component instanceof javax.swing.JTextArea) {
            component.setBackground(UIManager.getColor("TextArea.background"));
            component.setForeground(UIManager.getColor("TextArea.foreground"));
        } else if (component instanceof javax.swing.text.JTextComponent) {
            component.setBackground(UIManager.getColor("TextField.background"));
            component.setForeground(UIManager.getColor("TextField.foreground"));
        } else if (component instanceof javax.swing.JComboBox) {
            component.setBackground(UIManager.getColor("ComboBox.background"));
            component.setForeground(UIManager.getColor("ComboBox.foreground"));
        } else if (component instanceof javax.swing.JSpinner) {
            component.setBackground(UIManager.getColor("Spinner.background"));
            component.setForeground(UIManager.getColor("Spinner.foreground"));
        } else if (component instanceof javax.swing.JTree) {
            component.setBackground(UIManager.getColor("Tree.background"));
            component.setForeground(UIManager.getColor("Tree.foreground"));
        } else if (component instanceof javax.swing.JScrollPane) {
            javax.swing.JViewport viewport = ((javax.swing.JScrollPane) component).getViewport();
            if (viewport != null && viewport.getView() instanceof javax.swing.JTree) {
                viewport.setBackground(UIManager.getColor("Tree.background"));
            }
        }
        if (component instanceof java.awt.Container) {
            for (java.awt.Component child : ((java.awt.Container) component).getComponents()) {
                restyle(child);
            }
        }
    }

    private static void repaintAll() {
        for (Window window : Window.getWindows()) {
            if (window != null) {
                window.repaint();
            }
        }
    }

    private static void applyLookAndFeel() {
        try {
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception ignored) {
            // Fall through with whatever look and feel is installed.
        }
        baseFont = pickFont(Font.PLAIN, BASE_SIZE);
        monoFont = new Font(Font.MONOSPACED, Font.PLAIN, MONO_SIZE);
        serifFont = pickSerifFont();
        UIManager.put("defaultFont", new FontUIResource(baseFont));
        UIManager.put("Component.focusWidth", 1);
        UIManager.put("Component.innerFocusWidth", 0);
        UIManager.put("Component.focusColor", ACCENT);
        UIManager.put("Component.arc", px(4));
        UIManager.put("Button.arc", px(4));
        UIManager.put("TextComponent.arc", px(4));
        UIManager.put("ProgressBar.arc", px(2));
        UIManager.put("ScrollBar.width", px(11));
        UIManager.put("ScrollBar.thumbArc", px(3));
        UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(px(2), px(2), px(2), px(2)));
        UIManager.put("ScrollBar.trackArc", 999);
        UIManager.put("ScrollBar.showButtons", false);
        UIManager.put("ScrollBar.hoverThumbColor", dark ? new Color(0x574A38) : new Color(0xBFB09A));
        UIManager.put("ScrollBar.thumbColor", dark ? new Color(0x453B2E) : new Color(0xCFC2AC));
        UIManager.put("ScrollBar.track", new Color(0, 0, 0, 0));
        UIManager.put("Tree.rowHeight", px(26));
        UIManager.put("Tree.paintLines", false);
        UIManager.put("Tree.showHorizontalLines", false);
        UIManager.put("Tree.showVerticalLines", false);
        UIManager.put("Tree.selectionArc", px(2));
        UIManager.put("Table.showHorizontalLines", true);
        UIManager.put("ToolTip.background", dark ? new Color(0x241F18) : new Color(0xFBF8F1));
        UIManager.put("ToolTip.foreground", dark ? new Color(0xE9E1D2) : new Color(0x2B2620));
        UIManager.put("ToolTip.borderColor", currentBorder());
        UIManager.put("ToolTip.border", new javax.swing.border.LineBorder(currentBorder(), 1, true));
        UIManager.put("Button.default.boldText", true);

        UIManager.put("Panel.background", BG);
        UIManager.put("Label.foreground", TEXT);
        UIManager.put("Separator.foreground", BORDER);
        UIManager.put("OptionPane.background", BG);
        UIManager.put("OptionPane.messageForeground", TEXT);

        UIManager.put("TextField.background", dark ? new Color(0x241F18) : new Color(0xFBF8F1));
        UIManager.put("TextField.foreground", TEXT);
        UIManager.put("TextField.placeholderForeground", FAINT);
        UIManager.put("TextField.focusedBackground", dark ? new Color(0x2A241C) : new Color(0xFFFFFF));
        UIManager.put("TextField.borderColor", BORDER);
        UIManager.put("TextField.focusedBorderColor", ACCENT);
        UIManager.put("FormattedTextField.background", dark ? new Color(0x241F18) : new Color(0xFBF8F1));
        UIManager.put("FormattedTextField.foreground", TEXT);
        UIManager.put("FormattedTextField.focusedBackground", dark ? new Color(0x2A241C) : new Color(0xFFFFFF));
        UIManager.put("TextArea.background", dark ? new Color(0x1C1813) : new Color(0xFBF8F1));
        UIManager.put("Spinner.background", dark ? new Color(0x241F18) : new Color(0xF1EADB));
        UIManager.put("Spinner.foreground", TEXT);
        UIManager.put("Spinner.buttonBackground", dark ? new Color(0x2A241C) : new Color(0xECE4D6));
        UIManager.put("Spinner.buttonArrowColor", MUTED);
        UIManager.put("ComboBox.background", dark ? new Color(0x241F18) : new Color(0xFBF8F1));
        UIManager.put("ComboBox.foreground", TEXT);
        UIManager.put("ComboBox.buttonBackground", dark ? new Color(0x241F18) : new Color(0xFBF8F1));
        UIManager.put("ComboBox.buttonArrowColor", MUTED);
        UIManager.put("ComboBox.borderColor", BORDER);
        UIManager.put("ComboBox.focusedBorderColor", ACCENT);
        UIManager.put("ComboBox.selectionBackground", ACCENT_SOFT);
        UIManager.put("ComboBox.selectionForeground", TEXT);

        UIManager.put("Button.background", dark ? CARD_ALT : new Color(0xF1EADB));
        UIManager.put("Button.foreground", TEXT);
        UIManager.put("Button.hoverBackground", dark ? CARD_HOVER : new Color(0xE8DFCE));
        UIManager.put("Button.pressedBackground", dark ? new Color(0x322B22) : new Color(0xDED3C0));
        UIManager.put("Button.borderColor", BORDER);
        UIManager.put("Button.focusedBorderColor", ACCENT);

        UIManager.put("PopupMenu.background", dark ? CARD_ALT : new Color(0xFBF8F1));
        UIManager.put("PopupMenu.foreground", TEXT);
        UIManager.put("PopupMenu.borderColor", BORDER);
        UIManager.put("PopupMenu.border", new javax.swing.border.LineBorder(BORDER, 1, true));
        UIManager.put("MenuItem.background", dark ? CARD_ALT : new Color(0xFBF8F1));
        UIManager.put("MenuItem.foreground", TEXT);
        UIManager.put("MenuItem.selectionBackground", ACCENT_SOFT);
        UIManager.put("MenuItem.selectionForeground", TEXT);
        UIManager.put("MenuItem.acceleratorForeground", FAINT);
        UIManager.put("MenuItem.disabledForeground", FAINT);
        UIManager.put("Menu.background", dark ? CARD_ALT : new Color(0xFBF8F1));
        UIManager.put("Menu.foreground", TEXT);
        UIManager.put("Menu.selectionBackground", ACCENT_SOFT);
        UIManager.put("Menu.selectionForeground", TEXT);

        UIManager.put("Tree.background", CARD);
        UIManager.put("Tree.foreground", TEXT);
        UIManager.put("Tree.selectionBackground", ACCENT_SOFT);
        UIManager.put("Tree.selectionForeground", TEXT);
        UIManager.put("Tree.selectionInactiveBackground", ACCENT_SOFT);
        UIManager.put("Tree.selectionInactiveForeground", TEXT);
        UIManager.put("Tree.line", BORDER);
        UIManager.put("Tree.hash", BORDER);

        UIManager.put("ScrollPane.background", BG);
        UIManager.put("ScrollBar.background", new Color(0, 0, 0, 0));
        UIManager.put("ScrollBar.thumb", dark ? new Color(0x453B2E) : new Color(0xCFC2AC));

        UIManager.put("TitlePane.background", dark ? BG2 : new Color(0xF6F1E7));
        UIManager.put("TitlePane.foreground", TEXT);
        UIManager.put("TitlePane.inactiveBackground", dark ? BG2 : new Color(0xF1EADB));
        UIManager.put("TitlePane.inactiveForeground", MUTED);
        UIManager.put("TitlePane.borderColor", BORDER);
        UIManager.put("TitlePane.buttonHoverBackground", dark ? CARD_HOVER : new Color(0xECE4D6));
        UIManager.put("TitlePane.buttonPressedBackground", dark ? new Color(0x322B22) : new Color(0xDED3C0));
        UIManager.put("TitlePane.closeButtonHoverBackground", new Color(0xA03D32));
        UIManager.put("TitlePane.closeButtonPressedBackground", new Color(0x8A2F26));
    }

    private static Color currentBorder() {
        return dark ? new Color(0x322B22) : new Color(0xDED3C0);
    }

    private static void updatePalette() {
        if (dark) {
            BG = new Color(0x14110D);
            BG2 = new Color(0x14110D);
            CARD = new Color(0x1C1813);
            CARD_ALT = new Color(0x241F18);
            CARD_HOVER = new Color(0x2A241C);
            BORDER = new Color(0x322B22);
            BORDER_STRONG = new Color(0x453B2E);
            TEXT = new Color(0xE9E1D2);
            MUTED = new Color(0xA29682);
            FAINT = new Color(0x7A7061);
            ACCENT = new Color(0xC7A76C);
            ACCENT2 = new Color(0xD8BC85);
            ACCENT_SOFT = new Color(199, 167, 108, 34);
            SUCCESS = new Color(0x8AA67F);
            WARNING = new Color(0xC4A45E);
            DANGER = new Color(0xC8695C);
            INFO = new Color(0x9BAEBB);
            TERMINAL = new Color(0x0F0C09);
            SHADOW = new Color(0, 0, 0, 90);
            NAV_MUTED = new Color(0xB0A48E);
        } else {
            BG = new Color(0xF6F1E7);
            BG2 = new Color(0xF6F1E7);
            CARD = new Color(0xFBF8F1);
            CARD_ALT = new Color(0xF1EADB);
            CARD_HOVER = new Color(0xF1EADB);
            BORDER = new Color(0xDED3C0);
            BORDER_STRONG = new Color(0xC9BCA4);
            TEXT = new Color(0x2B2620);
            MUTED = new Color(0x7C7163);
            FAINT = new Color(0x9A8E7C);
            ACCENT = new Color(0x8A6A3B);
            ACCENT2 = new Color(0x6E5329);
            ACCENT_SOFT = new Color(138, 106, 59, 30);
            SUCCESS = new Color(0x4F6B4A);
            WARNING = new Color(0x8A6D2F);
            DANGER = new Color(0xA03D32);
            INFO = new Color(0x5F7280);
            TERMINAL = new Color(0x14110D);
            SHADOW = new Color(43, 38, 32, 26);
            NAV_MUTED = new Color(0x6F6455);
        }
    }

    // ------------------------------------------------------------------
    // typography
    // ------------------------------------------------------------------

    static Font pickFont(int style, int size) {
        int pointSize = Math.max(MIN_SIZE, size);
        Font laf = UIManager.getFont("defaultFont");
        Font candidate = null;
        if (laf != null) {
            candidate = new Font(laf.getFamily(), style, pointSize);
        }
        if (candidate == null || candidate.canDisplayUpTo("\u6df7\u6dc6\u5668\u4e2d\u6587") >= 0) {
            candidate = new Font("Tahoma", style, pointSize);
        }
        if (candidate.canDisplayUpTo("\u6df7\u6dc6\u5668\u4e2d\u6587") >= 0) {
            candidate = new Font(Font.DIALOG, style, pointSize);
        }
        return candidate;
    }

    /** Serif display font: Georgia (en) / SimSun (zh), with graceful fallbacks. */
    static Font serifFont(int style, float size) {
        float snapped = Math.max(MIN_SIZE, Math.round(size));
        Font resolved = serifFont;
        if (resolved == null) {
            resolved = pickSerifFont();
        }
        return resolved.deriveFont(style, snapped);
    }

    static Font font(int style, float size) {
        float snapped = Math.max(MIN_SIZE, Math.round(size));
        return baseFont == null ? new Font("SansSerif", style, Math.round(snapped))
                : baseFont.deriveFont(style, snapped);
    }

    static Font mono(float size) {
        float snapped = Math.max(MIN_SIZE, Math.round(size));
        return monoFont == null ? new Font(Font.MONOSPACED, Font.PLAIN, Math.round(snapped))
                : monoFont.deriveFont(Font.PLAIN, snapped);
    }

    private static Font pickSerifFont() {
        String[] preferred = I18n.isChinese()
                ? new String[]{"SimSun", "Songti SC", "STSong", "Serif"}
                : new String[]{"Georgia", "Palatino Linotype", "Times New Roman", "Serif"};
        try {
            List<String> installed = new ArrayList<String>();
            for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()) {
                installed.add(name);
            }
            for (String candidate : preferred) {
                for (String name : installed) {
                    if (name.equalsIgnoreCase(candidate)) {
                        return new Font(name, Font.PLAIN, 24);
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // Headless or restricted environment: fall back to the logical serif.
        }
        return new Font(Font.SERIF, Font.PLAIN, 24);
    }

    // ------------------------------------------------------------------
    // painting helpers
    // ------------------------------------------------------------------

    static int px(int value) {
        return UIScale.scale(value);
    }

    static float px(float value) {
        return UIScale.scale(value);
    }

    static Color alpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    static Color blend(Color a, Color b, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        return new Color(
                Math.round(a.getRed() + (b.getRed() - a.getRed()) * clamped),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * clamped),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * clamped));
    }

    /** Classic style: solid accent, no gradients. */
    static Paint accentPaint(Rectangle bounds, boolean horizontal) {
        return ACCENT;
    }

    static Paint accentHoverPaint(Rectangle bounds) {
        return ACCENT2;
    }

    /** Antique bronze hairline used under serif display titles. */
    static Paint rulePaint(Rectangle bounds) {
        if (bounds.width <= 0) {
            return ACCENT;
        }
        return new LinearGradientPaint(bounds.x, bounds.y, bounds.x + bounds.width, bounds.y,
                new float[]{0f, 0.72f, 1f},
                new Color[]{ACCENT, ACCENT, alpha(ACCENT, 0)});
    }

    // ------------------------------------------------------------------
    // classic surface helpers (previous frosted-glass API, now flat paper/ink)
    // ------------------------------------------------------------------

    static Color glassTint() {
        return dark ? new Color(28, 24, 19, 248) : new Color(250, 246, 237, 248);
    }

    static Color glassTintTop() {
        return dark ? new Color(36, 31, 24, 248) : new Color(251, 248, 241, 248);
    }

    static Color glassHighlight() {
        return dark ? new Color(255, 240, 214, 14) : new Color(255, 255, 255, 130);
    }

    static Color glassBorder() {
        return BORDER;
    }

    static Color dialogGlassTint() {
        return dark ? CARD : new Color(0xFBF8F1);
    }

    static boolean isDark() {
        return dark;
    }
}
