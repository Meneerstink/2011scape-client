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
    private static final int BAR_HEIGHT = 15;

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

    private static final int HEIGHT = 4 + 14 + 3 + BAR_HEIGHT + 4;

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

    static void draw(int x, int y) {
        Font font = RspsHud.font();
        int width = RspsHud.PANEL_WIDTH;
        RspsHud.box(x, y, width, HEIGHT);

        String levelText = level > 0 ? " (level-" + level + ")" : "";
        int nameWidth = RspsHud.width(RspsHud.metrics(), name);
        int totalWidth = nameWidth + RspsHud.width(RspsHud.metrics(), levelText);
        int textX = x + Math.max(4, (width - totalWidth) / 2);
        RspsHud.text(font, name, textX, y + 15, TITLE, false);
        if (levelText.length() > 0) {
            RspsHud.text(font, levelText, textX + nameWidth, y + 15, LEVEL, false);
        }

        int barX = x + 4;
        int barY = y + 4 + 14 + 3;
        int barWidth = width - 8;
        int filled = maximum > 0 ? (int) ((long) barWidth * Math.min(current, maximum) / maximum) : barWidth * ratio / 255;
        Toolkit.active.fillRect(barX, barY, barWidth, BAR_HEIGHT, HP_RED);
        if (filled > 0) {
            Toolkit.active.fillRect(barX, barY, filled, BAR_HEIGHT, HP_GREEN);
        }
        font.renderCentre(healthText(), barX + barWidth / 2, barY + 12, TITLE, RspsHud.SHADOW);
    }

    /** RuneLite's progress bar label: "cur/max", "pct%" or both - the exact value only when it is known (monsters). */
    static String healthText() {
        int percent;
        if (maximum > 0) {
            percent = (int) Math.round(100.0 * Math.min(current, maximum) / maximum);
            if (percent == 0 && current > 0) {
                percent = 1;
            } else if (percent == 100 && current < maximum) {
                percent = 99;
            }
        } else {
            percent = (int) Math.round(100.0 * ratio / 255.0);
            if (percent == 0 && ratio > 0) {
                percent = 1;
            } else if (percent == 100 && ratio < 255) {
                percent = 99;
            }
        }
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
