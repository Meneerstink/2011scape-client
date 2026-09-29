import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Sprite;

/**
 * Boosts Information (RuneLite BoostsPlugin / BoostsOverlay / BoostIndicator / StatChangeIndicator):
 *
 *  - Display boosts: none / combat (Attack, Strength, Defence, Ranged, Magic) / non-combat / both. Like RuneLite, Prayer and
 *    Constitution are never boosts here; nor are Summoning (points, drained by familiars) and Dungeoneering.
 *  - Panel ("Display panel"): one line per changed stat, the name (or its icon when "Compact display" is on) and the
 *    boosted/base level, or with "Show relative boosts" the difference; then "Next + restore in" / "Next - restore in"
 *    (seconds) per "Next debuff change" / "Next buff change" (Always / Boosted / Never).
 *  - Infoboxes ("Display infoboxes"): per changed stat its skill icon with the level (or the difference), plus the arrows
 *    for the next changes.
 *  - Colours: below base RGB 238/51/51, boosted up to the "Boost threshold" yellow, above it green (RuneLite's).
 *  - "Notify on boost threshold": a game message "<skill> level is getting low!" when a boost drains to the threshold.
 *
 * Levels are the client's own skill state (UPDATE_STAT); the change times are the server's restoration cycles
 * (`boost|combatDecay|restore|nonCombatDecay`, server BoostedCombatStats / RestorationRates), so Berserker and Rapid
 * Restore are in them.
 */
final class RspsHudBoosts {

    static final String ID = "boosts";

    private static final int[] COMBAT = {0, 2, 1, 4, 6};
    private static final int[] NON_COMBAT = {14, 16, 13, 15, 10, 17, 7, 12, 11, 9, 8, 20, 18, 19, 22, 21};
    private static final String[] SKILL_NAMES = {
        "Attack", "Defence", "Strength", "Constitution", "Ranged", "Prayer", "Magic", "Cooking", "Woodcutting", "Fletching",
        "Fishing", "Firemaking", "Crafting", "Smithing", "Mining", "Herblore", "Agility", "Thieving", "Slayer", "Farming",
        "Runecrafting", "Hunter", "Construction", "Summoning", "Dungeoneering",
    };

    static final int DRAINED = 0xFFEE3333;
    static final int BOOSTED = 0xFF00FF00;
    static final int NEAR_THRESHOLD = 0xFFFFFF00;
    private static final int NORMAL = 0xFFFFFFFF;
    private static final int LINE = RspsHud.LINE;
    private static final int BORDER = RspsHud.PANEL_BORDER;
    private static final int COMPACT_WIDTH = 64;

    private static long decayEnd = -1L;
    private static long restoreEnd = -1L;
    private static long nonCombatDecayEnd = -1L;

    /** Last seen boost per skill, for the threshold notification. */
    private static final int[] lastBoost = new int[25];
    private static boolean lastBoostKnown;

    private RspsHudBoosts() {
    }

    static void update(String rest) {
        String[] parts = rest.split("\\|");
        decayEnd = RspsHud.endTime(Integer.parseInt(parts[0]));
        restoreEnd = RspsHud.endTime(Integer.parseInt(parts[1]));
        nonCombatDecayEnd = parts.length > 2 ? RspsHud.endTime(Integer.parseInt(parts[2])) : -1L;
    }

    static void reset() {
        decayEnd = -1L;
        restoreEnd = -1L;
        nonCombatDecayEnd = -1L;
        lastBoostKnown = false;
    }

    static int[] displayed() {
        String mode = RspsHudConfig.get("boosts.displayBoosts");
        if (mode.equals("NONE")) {
            return new int[0];
        }
        if (mode.equals("NON_COMBAT")) {
            return NON_COMBAT;
        }
        if (mode.equals("BOTH")) {
            int[] both = new int[COMBAT.length + NON_COMBAT.length];
            System.arraycopy(COMBAT, 0, both, 0, COMBAT.length);
            System.arraycopy(NON_COMBAT, 0, both, COMBAT.length, NON_COMBAT.length);
            return both;
        }
        return COMBAT;
    }

    static int boost(int skill) {
        return Static581.statLevels[skill] - Static498.statBaseLevels[skill];
    }

    /** BoostIndicator.getTextColor. */
    static int colour(int boost) {
        if (boost < 0) {
            return DRAINED;
        }
        return boost <= RspsHudConfig.integer("boosts.boostThreshold") ? NEAR_THRESHOLD : BOOSTED;
    }

    /** BoostIndicator.getText / BoostsOverlay: the level, the level/base, or the relative boost. */
    static String text(int skill, boolean panel) {
        int boost = boost(skill);
        if (RspsHudConfig.bool("boosts.relativeBoost")) {
            return boost > 0 ? "+" + boost : Integer.toString(boost);
        }
        return panel ? Static581.statLevels[skill] + "/" + Static498.statBaseLevels[skill] : Integer.toString(Static581.statLevels[skill]);
    }

    private static boolean isCombat(int skill) {
        for (int s : COMBAT) {
            if (s == skill) {
                return true;
            }
        }
        return false;
    }

    /** Seconds to the next boost drop (-1 = hidden), per "Next buff change". */
    static int nextBuffSeconds() {
        String mode = RspsHudConfig.get("boosts.displayNextBuffChange");
        if (mode.equals("NEVER")) {
            return -1;
        }
        boolean combatBoosted = false;
        boolean otherBoosted = false;
        boolean showsCombat = false;
        int[] shown = displayed();
        for (int skill : shown) {
            showsCombat |= isCombat(skill);
            if (boost(skill) > 0) {
                if (isCombat(skill)) {
                    combatBoosted = true;
                } else {
                    otherBoosted = true;
                }
            }
        }
        long end;
        if (combatBoosted) {
            end = decayEnd;
        } else if (otherBoosted) {
            end = nonCombatDecayEnd;
        } else if (mode.equals("ALWAYS") && shown.length > 0) {
            end = showsCombat ? decayEnd : nonCombatDecayEnd;
        } else {
            return -1;
        }
        return seconds(end);
    }

    /** Seconds to the next restore of a lowered stat (-1 = hidden), per "Next debuff change". */
    static int nextDebuffSeconds() {
        String mode = RspsHudConfig.get("boosts.displayNextDebuffChange");
        if (mode.equals("NEVER")) {
            return -1;
        }
        boolean drained = false;
        int[] shown = displayed();
        for (int skill : shown) {
            drained |= boost(skill) < 0;
        }
        if (!drained && !(mode.equals("ALWAYS") && shown.length > 0)) {
            return -1;
        }
        return seconds(restoreEnd);
    }

    private static int seconds(long end) {
        if (end <= 0L) {
            return -1;
        }
        return (int) ((Math.max(0L, end - RspsHud.now()) + 999L) / 1000L);
    }

    /** The threshold notification (once per frame, from the render pass). */
    static void checkThreshold() {
        int threshold = RspsHudConfig.integer("boosts.boostThreshold");
        boolean notify = RspsHudConfig.bool("boosts.notifyOnBoost");
        for (int skill : displayed()) {
            int boost = boost(skill);
            if (lastBoostKnown && notify && boost <= threshold && lastBoost[skill] > threshold) {
                RspsHud.notify(SKILL_NAMES[skill] + " level is getting low!");
            }
            lastBoost[skill] = boost;
        }
        lastBoostKnown = true;
    }

    /** Infoboxes of this frame (when "Display infoboxes" is on). */
    static void addInfoboxes() {
        if (!RspsHudConfig.bool("boosts.displayInfoboxes")) {
            return;
        }
        for (int skill : displayed()) {
            int boost = boost(skill);
            if (boost != 0) {
                RspsInfoBoxes.add(RspsSprites.skillIcon(skill), text(skill, false), colour(boost));
            }
        }
        int up = nextDebuffSeconds();
        if (up >= 0) {
            RspsInfoBoxes.add(RspsSprites.ARROW_UP, Integer.toString(up), NORMAL);
        }
        int down = nextBuffSeconds();
        if (down >= 0) {
            RspsInfoBoxes.add(RspsSprites.ARROW_DOWN, Integer.toString(down), NORMAL);
        }
    }

    private static int changedRows() {
        int rows = 0;
        for (int skill : displayed()) {
            if (boost(skill) != 0) {
                rows++;
            }
        }
        return rows;
    }

    static int width() {
        return RspsHudConfig.bool("boosts.compactDisplay") ? COMPACT_WIDTH : RspsHud.PANEL_WIDTH;
    }

    /** Panel height for this frame; 0 = nothing to show. */
    static int height() {
        if (!RspsHudConfig.bool("boosts.displayPanel")) {
            return 0;
        }
        int lines = changedRows() + (nextDebuffSeconds() >= 0 ? 1 : 0) + (nextBuffSeconds() >= 0 ? 1 : 0);
        // RuneLite PanelComponent: a 4 px border around lines of one font height each.
        return lines == 0 ? 0 : BORDER + lines * LINE + BORDER;
    }

    static void draw(int x, int y, int height) {
        int width = width();
        RspsHud.box(x, y, width, height);
        Font font = RspsHud.font();
        FontMetrics metrics = RspsHud.metrics();
        boolean compact = RspsHudConfig.bool("boosts.compactDisplay");
        // RuneLite LineComponent: text at the line's full font height, left at the border, right-aligned at the border.
        int baseline = y + BORDER + LINE;
        int right = x + width - BORDER;
        for (int skill : displayed()) {
            int boost = boost(skill);
            if (boost == 0) {
                continue;
            }
            if (compact) {
                Sprite icon = RspsSprites.sprite(RspsSprites.skillIcon(skill));
                if (icon != null) {
                    icon.render(x + 3, baseline - 12, 14, 14);
                }
            } else {
                RspsHud.text(font, SKILL_NAMES[skill], x + BORDER, baseline, NORMAL, false);
            }
            String value = text(skill, true);
            RspsHud.text(font, value, right - RspsHud.width(metrics, value), baseline, colour(boost), false);
            baseline += LINE;
        }
        int up = nextDebuffSeconds();
        if (up >= 0) {
            line(font, metrics, compact ? "+" : "Next + restore in", Integer.toString(up), x, right, baseline);
            baseline += LINE;
        }
        int down = nextBuffSeconds();
        if (down >= 0) {
            line(font, metrics, compact ? "-" : "Next - restore in", Integer.toString(down), x, right, baseline);
        }
    }

    private static void line(Font font, FontMetrics metrics, String label, String value, int x, int right, int baseline) {
        RspsHud.text(font, label, x + BORDER, baseline, NORMAL, false);
        RspsHud.text(font, value, right - RspsHud.width(metrics, value), baseline, NORMAL, false);
    }
}
