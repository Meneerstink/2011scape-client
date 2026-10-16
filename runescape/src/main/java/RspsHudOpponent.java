import com.jagex.graphics.Font;
import com.jagex.graphics.Toolkit;

/**
 * Opponent Information (RuneLite OpponentInfoPlugin / OpponentInfoOverlay): name, combat level and health of the local
 * player's opponent. The server decides who that is (its combat target, or the faced pawn after a manual cast), keeps the
 * last one for RuneLite's 5 seconds after the fight ends, and sends the health every time it changes - damage, food,
 * brews, regeneration, death - so nothing is guessed from hitsplats.
 *
 * Settings: "Display style" hitpoints (exact when known - monsters; players only have the hit-mask ratio) / percentage /
 * both; "Show opponent health overlay"; "Show opponents in menu" (a "*" before the name on the Attack option of an npc
 * that is your opponent or is attacking you, RuneLite's marker).
 */
final class RspsHudOpponent {

    /** RuneLite OpponentInfoOverlay bar colours (HP_GREEN 0,146,54 and HP_RED 102,15,16, both at alpha 230). */
    private static final int HP_GREEN = 0xE6009236;
    private static final int HP_RED = 0xE6660F10;
    private static final int TITLE = 0xFFFFFFFF;
    private static final int LEVEL = 0xFFFFFF00;
    /** RuneLite OpponentInfoOverlay: border 2, gap 2, a title line and a 16 px ProgressBarComponent. */
    private static final int BAR_HEIGHT = 16;
    private static final int BORDER = 2;
    private static final int GAP = 2;

    private static boolean active;
    private static boolean npc;
    private static int index;
    private static int ratio;
    private static int current;
    private static int maximum;

    private RspsHudOpponent() {
    }

    static void update(String rest) {
        String[] parts = rest.split("\\|");
        if (parts[0].equals("none")) {
            active = false;
            return;
        }
        npc = parts[0].equals("n");
        index = Integer.parseInt(parts[1]);
        ratio = Integer.parseInt(parts[2]);
        current = npc ? Integer.parseInt(parts[3]) : -1;
        maximum = npc ? Integer.parseInt(parts[4]) : -1;
        active = true;
    }

    static void reset() {
        active = false;
    }

    /** v5 PK HUD: the server's player opponent (high-resolution index), -1 = none or an npc. */
    static int playerIndex() {
        return active && !npc ? index : -1;
    }

    /** v5 PK HUD: the health ratio (0-255) when [p] is the server's opponent, else -1. */
    static int playerRatio(PlayerEntity p) {
        int i = playerIndex();
        return p != null && i >= 0 && i < PlayerList.highResolutionPlayers.length && PlayerList.highResolutionPlayers[i] == p ? ratio : -1;
    }

    /** RuneLite: marks the Attack option of an npc that is the opponent or is attacking the local player. */
    static String menuTarget(NPCEntity entity, String option, String target) {
        if (!RspsHudConfig.enabled("opponent") || !RspsHudConfig.bool("opponent.showOpponentsInMenu") || option == null || !option.equalsIgnoreCase("Attack")) {
            return target;
        }
        boolean opponent = active && npc && index == entity.slot;
        boolean attackingMe = entity.target == PlayerList.activePlayerSlot + 32768;
        return opponent || attackingMe ? "*" + target : target;
    }


    static final String ID = "opponent";

    private static final int HEIGHT = BORDER + RspsHud.LINE + GAP + BAR_HEIGHT + BORDER;

    private static String name;
    private static int level;

    /** Panel height for this frame; 0 = no opponent (or not in view). Resolves the name and level for {@link #draw}. */
    static int height() {
        if (!active || !RspsHudConfig.bool("opponent.showOpponentHealthOverlay")) {
            return 0;
        }
        name = null;
        if (npc) {
            NPCEntityNode node = (NPCEntityNode) NPCList.local.get(index);
            if (node == null || node.npc == null) {
                return 0;
            }
            NPCEntity entity = node.npc;
            name = entity.name != null ? entity.name : entity.type == null ? null : entity.type.name;
            level = entity.combatLevel;
        } else {
            PlayerEntity entity = index >= 0 && index < PlayerList.highResolutionPlayers.length ? PlayerList.highResolutionPlayers[index] : null;
            if (entity == null) {
                return 0;
            }
            name = entity.displayName;
            level = entity.combatLevel;
        }
        return name == null ? 0 : HEIGHT;
    }

    private static String title() {
        // The owner's specification adds the combat level to RuneLite's name-only title.
        return level > 0 ? name + " (level-" + level + ")" : name;
    }

    /** RuneLite: max(STANDARD_WIDTH, title width + 2 x STANDARD_BORDER). */
    static int width() {
        if (name == null) {
            return RspsHud.PANEL_WIDTH;
        }
        return Math.max(RspsHud.PANEL_WIDTH, RspsHud.width(RspsHud.metrics(), title()) + 2 * RspsHud.PANEL_BORDER);
    }

    static void draw(int x, int y) {
        Font font = RspsHud.font();
        int width = width();
        RspsHud.box(x, y, width, HEIGHT);

        // TitleComponent: centred, baseline at its top + the font height.
        String levelText = level > 0 ? " (level-" + level + ")" : "";
        int nameWidth = RspsHud.width(RspsHud.metrics(), name);
        int textX = x + (width - RspsHud.width(RspsHud.metrics(), title())) / 2;
        int titleBaseline = y + BORDER + RspsHud.LINE;
        RspsHud.text(font, name, textX, titleBaseline, TITLE, false);
        if (levelText.length() > 0) {
            RspsHud.text(font, levelText, textX + nameWidth, titleBaseline, LEVEL, false);
        }

        // ProgressBarComponent: 16 px, the width inside the border; label centred, baseline barY + (h - fontH) / 2 + fontH.
        int barX = x + BORDER;
        int barY = y + BORDER + RspsHud.LINE + GAP;
        int barWidth = width - 2 * BORDER;
        int filled = maximum > 0 ? (int) ((long) barWidth * Math.min(current, maximum) / maximum) : barWidth * ratio / 255;
        Toolkit.active.fillRect(barX, barY, barWidth, BAR_HEIGHT, HP_RED);
        if (filled > 0) {
            Toolkit.active.fillRect(barX, barY, filled, BAR_HEIGHT, HP_GREEN);
        }
        String label = healthText();
        int labelWidth = RspsHud.width(RspsHud.metrics(), label);
        RspsHud.text(font, label, barX + (barWidth - labelWidth) / 2, barY + (BAR_HEIGHT - RspsHud.LINE) / 2 + RspsHud.LINE, TITLE, false);
    }

    /**
     * RuneLite's progress bar label: "cur/max", "pct%" or both - the exact value only when it is known (monsters). The percentage is
     * ProgressBarComponent's DecimalFormat("0.0") (e.g. "57.3%"), with a decimal point like every number in this client.
     */
    static String healthText() {
        double fraction = maximum > 0 ? (double) Math.min(current, maximum) / maximum : ratio / 255.0;
        String percent = String.format(java.util.Locale.ENGLISH, "%.1f", fraction * 100.0);
        String style = RspsHudConfig.get("opponent.hitpointsDisplayStyle");
        if (maximum <= 0 || style.equals("PERCENTAGE")) {
            return percent + "%";
        }
        if (style.equals("BOTH")) {
            return current + "/" + maximum + " (" + percent + "%)";
        }
        return current + "/" + maximum;
    }
}
