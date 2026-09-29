import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;

/**
 * Boosts Information: Attack, Strength, Defence, Ranged and Magic as current/base with the relative boost or drain, plus
 * the time to the next natural change. Levels are the client's own skill state (UPDATE_STAT); the countdowns are the
 * server's restoration timers (server BoostedCombatStats / RestorationRates via `boost|decay|restore`), so Berserker,
 * Rapid Restore and Divine potions are already in them.
 */
final class RspsHudBoosts {

    private static final int[] SKILLS = {0, 2, 1, 4, 6};
    private static final String[] NAMES = {"Attack", "Strength", "Defence", "Ranged", "Magic"};

    private static final int BOOSTED = 0xFF00FF00;
    private static final int DRAINED = 0xFFFF0000;
    private static final int NORMAL = 0xFFFFFFFF;
    private static final int LINE = 15;

    /** When the next boosted stat drops / the next lowered stat returns; -1 = none. */
    private static long decayEnd = -1L;
    private static long restoreEnd = -1L;

    private RspsHudBoosts() {
    }

    static void update(String rest) {
        String[] parts = rest.split("\\|");
        decayEnd = RspsHud.endTime(Integer.parseInt(parts[0]));
        restoreEnd = RspsHud.endTime(Integer.parseInt(parts[1]));
    }

    static void reset() {
        decayEnd = -1L;
        restoreEnd = -1L;
    }

    static final String ID = "boosts";

    private static boolean decayLine;
    private static boolean restoreLine;

    /** Panel height for this frame; 0 = nothing to show. */
    static int height() {
        int rows = 0;
        boolean anyBoost = false;
        boolean anyDrain = false;
        for (int i = 0; i < SKILLS.length; i++) {
            int diff = Static581.statLevels[SKILLS[i]] - Static498.statBaseLevels[SKILLS[i]];
            anyBoost |= diff > 0;
            anyDrain |= diff < 0;
            if (diff != 0 || RspsHudConfig.boostsAll) {
                rows++;
            }
        }
        decayLine = RspsHudConfig.boostTimers && anyBoost && decayEnd > 0L;
        restoreLine = RspsHudConfig.boostTimers && anyDrain && restoreEnd > 0L;
        if (rows == 0) {
            return 0;
        }
        int lines = rows + (decayLine ? 1 : 0) + (restoreLine ? 1 : 0);
        return lines * LINE + 6;
    }

    static void draw(int x, int y, int height) {
        RspsHud.box(x, y, RspsHud.PANEL_WIDTH, height);
        Font font = RspsHud.font();
        FontMetrics metrics = RspsHud.metrics();
        int baseline = y + LINE;
        int right = x + RspsHud.PANEL_WIDTH - 4;
        int relativeWidth = RspsHud.width(metrics, "+99");
        for (int i = 0; i < SKILLS.length; i++) {
            int current = Static581.statLevels[SKILLS[i]];
            int base = Static498.statBaseLevels[SKILLS[i]];
            int diff = current - base;
            if (diff == 0 && !RspsHudConfig.boostsAll) {
                continue;
            }
            int colour = diff > 0 ? BOOSTED : diff < 0 ? DRAINED : NORMAL;
            font.render(NAMES[i], x + 4, baseline, RspsHud.SHADOW, NORMAL);
            String relative = diff > 0 ? "+" + diff : diff < 0 ? Integer.toString(diff) : "";
            font.renderRight(relative, right, baseline, colour, RspsHud.SHADOW);
            font.renderRight(current + "/" + base, right - relativeWidth - 4, baseline, colour, RspsHud.SHADOW);
            baseline += LINE;
        }
        if (decayLine) {
            font.render("Boost drop", x + 4, baseline, RspsHud.SHADOW, NORMAL);
            font.renderRight(RspsHud.countdown(decayEnd), right, baseline, NORMAL, RspsHud.SHADOW);
            baseline += LINE;
        }
        if (restoreLine) {
            font.render("Stat restore", x + 4, baseline, RspsHud.SHADOW, NORMAL);
            font.renderRight(RspsHud.countdown(restoreEnd), right, baseline, NORMAL, RspsHud.SHADOW);
        }
    }
}
