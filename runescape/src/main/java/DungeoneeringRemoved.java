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

    private DungeoneeringRemoved() {
    }

    /** InterfaceList: a cache component was just decoded. */
    static void apply(int id, int slot, Component component) {
        if (id == STATS_INTERFACE && slot == CELL) {
            component.hidden = true;
        }
    }

    /** STAT_BASE in a client script. */
    static int scriptBaseLevel(int skill, int level) {
        return skill == SKILL ? 0 : level;
    }
}
