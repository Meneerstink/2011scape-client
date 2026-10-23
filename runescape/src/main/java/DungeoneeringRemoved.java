/**
 * DGN (owner 2026-10-11 / 10-13: "Dungeoneering volledig weg ... skill weg (stats-tab, totaal ...)"): the client half. The server
 * no longer gives Dungeoneering experience and keeps it at level 1; here its cell in the stats tab (interface 320, layer 151 at
 * 3,229 with its icon, levels and View / Set target ops) is hidden when the interface loads, and its base level reads 0 in
 * client scripts so the "Total level" line (clientscript 544 -> 2761, the sum of every STAT_BASE) no longer counts it.
 */
final class DungeoneeringRemoved {

    static final int SKILL = 24;
    static final int STATS_INTERFACE = 320;
    static final int CELL = 151;

    /**
     * N236 (owner 2026-10-23: "nu Dungeoneering weg is moet 'Total level:' netjes in het midden staan"): the centred total line
     * (320:201, 65,231 122 wide, beside the old Dungeoneering cell) spans the whole bottom row - from the cell's left edge (x 3) to
     * the right edge of the third column (187) - so it sits in the middle of the tab.
     */
    static final int TOTAL = 201;
    static final int TOTAL_X = 3;
    static final int TOTAL_WIDTH = 184;

    private DungeoneeringRemoved() {
    }

    /** InterfaceList: a cache component was just decoded. */
    static void apply(int id, int slot, Component component) {
        if (id == STATS_INTERFACE && slot == CELL) {
            component.hidden = true;
        }
        if (id == STATS_INTERFACE && slot == TOTAL) {
            component.originalX = TOTAL_X;
            component.originalWidth = TOTAL_WIDTH;
        }
    }

    /** STAT_BASE in a client script. */
    static int scriptBaseLevel(int skill, int level) {
        return skill == SKILL ? 0 : level;
    }
}
