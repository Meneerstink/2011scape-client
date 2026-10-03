import java.util.HashMap;
import java.util.Map;

/**
 * Guards CombatTabNameFit (owner 2026-10-03, bug 211227): the combat tab weapon name 884:0 (180 px, cache font q8_full 497)
 * must stay on one line. Widths below are the client's own FontMetrics.stringWidth over the 667 cache fonts, measured
 * 2026-10-03 for the longest wieldable names (q8_full 497 / b12_full 496 / p12_full 495 / p11_full 494).
 */
public final class CombatTabNameFitRegressionTest {

    private static final int WIDTH = 180;
    private static final int CACHE_FONT = 497;

    public static void main(String[] args) {
        require(CombatTabNameFit.WEAPON_NAME_ID == (884 << 16), "weapon name is 884:0");

        // The component is matched on its packed slot (InterfaceList: slot = component + (interface << 16)), never on
        // Component.id, which is only a CC_CREATE child index (-1 for a cache component). Matching on id never fired.
        Component name = new Component();
        name.slot = 884 << 16;
        require(CombatTabNameFit.isWeaponName(name), "cache component 884:0 is the weapon name");
        Component created = new Component();
        created.slot = 884 << 16;
        created.id = 0;
        require(!CombatTabNameFit.isWeaponName(created), "a script-created child of 884:0 is not");
        Component level = new Component();
        level.slot = (884 << 16) | 1;
        require(!CombatTabNameFit.isWeaponName(level), "884:1 (Combat Lvl) is not");
        Component unrelated = new Component();
        unrelated.id = 884 << 16;
        require(!CombatTabNameFit.isWeaponName(unrelated), "Component.id is not the packed id");

        // Short names keep the cache font.
        require(choose(new int[] {120, 118, 100, 90}) == CACHE_FONT, "short name keeps q8_full");
        require(choose(new int[] {180, 178, 150, 130}) == CACHE_FONT, "exactly 180 px still fits");

        // Harmonised Nightmare staff: 190 / 187 / 161 / 140 -> p12_full.
        require(choose(new int[] {190, 187, 161, 140}) == 495, "Harmonised Nightmare staff -> p12_full");
        // Tonalztics of Ralos (uncharged), the widest wieldable name: 211 / 209 / 178 / 156 -> p12_full.
        require(choose(new int[] {211, 209, 178, 156}) == 495, "Tonalztics of Ralos (uncharged) -> p12_full");
        // C. morrigan's throwing axe: 182 / 175 -> b12_full.
        require(choose(new int[] {182, 175, 149, 135}) == 496, "182 px name -> b12_full");
        // Nothing fits: the smallest fallback, never the oversized cache font.
        require(choose(new int[] {400, 390, 300, 200}) == 494, "too wide for all -> p11_full");

        // Null text is left alone.
        require(CombatTabNameFit.fontFor(CACHE_FONT, null, WIDTH, f -> 999) == CACHE_FONT, "null text keeps the cache font");
        System.out.println("CombatTabNameFitRegressionTest OK");
    }

    private static int choose(int[] widths) {
        Map<Integer, Integer> byFont = new HashMap<>();
        byFont.put(497, widths[0]);
        byFont.put(496, widths[1]);
        byFont.put(495, widths[2]);
        byFont.put(494, widths[3]);
        return CombatTabNameFit.fontFor(CACHE_FONT, "name", WIDTH, byFont::get);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
