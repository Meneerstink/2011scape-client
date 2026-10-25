/**
 * DGN (owner 2026-10-11 / 10-13: "Dungeoneering volledig weg ... skill weg (stats-tab, totaal ...)"): the client half. The server
 * no longer gives Dungeoneering experience and keeps it at level 1; here its cell in the stats tab (interface 320, layer 151 at
 * 3,229 with its icon, levels and View / Set target ops) no longer shows the skill, and its base level reads 0 in client scripts so
 * the "Total level" line (clientscript 544 -> 2761, the sum of every STAT_BASE) no longer counts it.
 */
final class DungeoneeringRemoved {

    static final int SKILL = 24;
    static final int STATS_INTERFACE = 320;
    static final int CELL = 151;

    /**
     * N356 (owner 2026-10-24: "onderaan bij Total level ziet het er raar uit de achtergrond"): with the Dungeoneering cell gone the
     * bottom row was a bare strip with "Total level: N" floating on it (N236 stretched the line over the whole row). Now, as OSRS
     * shows it, the total sits in a cell of its own - the old Dungeoneering cell's frame (sprite 3030, 60x27, the frame of every
     * skill cell, drawn at its own size) moved to the middle column - on two lines: "Total level:" over the number. The cell's
     * skill parts (icon, levels, slash lines and the View / Set target ops) stay hidden.
     */
    static final int TOTAL = 201;
    static final int CELL_FRAME = 152;
    static final int CELL_X = 65;
    static final int CELL_Y = 229;
    static final int CELL_WIDTH = 60;
    static final int CELL_HEIGHT = 27;

    /** The font of the skill cells' level numbers (320:154, decoded before 320:201), used for the two total lines. */
    private static int levelFont = -1;

    private DungeoneeringRemoved() {
    }

    /** The Dungeoneering cell's skill parts (icon, current/base level, slash lines, the View / Set target op layer). */
    static boolean hiddenPart(int slot) {
        return slot > CELL_FRAME && slot <= CELL_FRAME + 6;
    }

    /** InterfaceList: a cache component was just decoded. */
    static void apply(int id, int slot, Component component) {
        if (id != STATS_INTERFACE) {
            return;
        }
        if (slot == CELL) {
            component.originalX = CELL_X;
            component.originalY = CELL_Y;
        }
        if (hiddenPart(slot)) {
            component.hidden = true;
        }
        if (slot == CELL_FRAME + 2) {
            levelFont = component.fontGraphic;
        }
        if (slot == TOTAL) {
            component.originalX = CELL_X;
            component.originalY = CELL_Y;
            component.originalWidth = CELL_WIDTH;
            component.originalHeight = CELL_HEIGHT;
            component.textAlignX = 1;
            component.textAlignY = 1;
            if (levelFont != -1) {
                component.fontGraphic = levelFont;
            }
        }
    }

    /** CC_IF_SETTEXT on 320:201: "Total level: 2376" becomes "Total level:" over "2376", the two lines of the total cell. */
    static String text(int slot, String text) {
        if (slot != ((STATS_INTERFACE << 16) | TOTAL) || text == null) {
            return text;
        }
        return text.replace(": ", ":<br>");
    }

    /** STAT_BASE in a client script. */
    static int scriptBaseLevel(int skill, int level) {
        return skill == SKILL ? 0 : level;
    }
}
