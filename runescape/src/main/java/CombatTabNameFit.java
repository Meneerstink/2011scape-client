import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;

import java.util.function.IntUnaryOperator;

/**
 * Owner 2026-10-03 (bug 211227): "harmonised nightmare staff is written a little bit too long in combat styles ... make it a
 * little bit tighter, check every item with a long name".
 *
 * Cause: the weapon name on the combat tab is 884:0, a 180x17 text component in the cache's large quill font 497 (q8_full)
 * with no line limit; CS2 1133 writes the worn weapon's name into it. 29 wieldable names are wider than 180 px in that font
 * (Harmonised Nightmare staff 190, Sanguinesti staff (uncharged) 194, Tonalztics of Ralos (uncharged) 211, ...), so the
 * renderer wrapped them onto a second line that ran into "Combat Lvl" (884:1) below.
 *
 * Fix: the name always stays on one line (the OSRS look). The cache font is used while the name fits; a longer name steps
 * down to the next smaller cache font that fits: b12_full 496, then p12_full 495, then p11_full 494. Every wieldable name
 * in the cache fits p12_full (widest 178 px), measured with the client's own FontMetrics over the whole item table.
 */
public final class CombatTabNameFit {

    static final int COMBAT_TAB = 884;
    static final int WEAPON_NAME = 0;
    static final int WEAPON_NAME_ID = COMBAT_TAB << 16 | WEAPON_NAME;

    /** Smaller cache fonts tried in order once the cache font (q8_full 497) is too wide: b12_full, p12_full, p11_full. */
    static final int[] FALLBACK_FONTS = {496, 495, 494};

    private CombatTabNameFit() {
    }

    /** The font group to draw [text] with: [cacheFont] while it fits [width], else the first fallback that does. */
    static int fontFor(int cacheFont, String text, int width, IntUnaryOperator widthInFont) {
        if (text == null || widthInFont.applyAsInt(cacheFont) <= width) {
            return cacheFont;
        }
        int last = cacheFont;
        for (int font : FALLBACK_FONTS) {
            if (font == cacheFont) {
                continue;
            }
            last = font;
            if (widthInFont.applyAsInt(font) <= width) {
                return font;
            }
        }
        return last;
    }

    /** Whether [child] is the cache component 884:0 (not a script-created child under it). */
    static boolean isWeaponName(Component child) {
        return child != null && child.slot == WEAPON_NAME_ID && child.id == -1;
    }

    /** The font to draw [child] with: its own font unless it is the combat tab weapon name and the name is too wide. */
    static Font fit(Component child, Font cacheFont, String text, Toolkit toolkit, Sprite[] icons) {
        // Component.slot is the packed (interface << 16 | component) id (InterfaceList); Component.id is only the CC_CREATE child
        // index (-1 for a cache component), so matching on id never hit 884:0.
        if (!isWeaponName(child) || cacheFont == null || text == null) {
            return cacheFont;
        }
        try {
            int chosen = fontFor(child.fontGraphic, text, child.width, group -> {
                FontMetrics metrics = Fonts.metrics(group, toolkit);
                return metrics == null ? Integer.MAX_VALUE : metrics.stringWidth(icons, text);
            });
            if (chosen == child.fontGraphic) {
                return cacheFont;
            }
            Font font = Fonts.font(child.fontMonospaced, false, chosen, toolkit);
            return font == null ? cacheFont : font;
        } catch (RuntimeException ignored) {
            return cacheFont;
        }
    }
}
