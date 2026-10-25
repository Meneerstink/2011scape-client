/**
 * N358 (owner 2026-10-24: "rigour en augury staat bij mij gekleurd maar heb de scrolls nog niet gebruikt hoe kan dat"; owner 2026-10-25:
 * the client script may change): the prayer book (interface 271) builds one cell per prayer and colours it with clientscript 52 -
 * STAT_BASE(prayer) against the prayer's level. Rigour and Augury also need their prayer scroll here (OSRS: "Dexterous prayer scroll",
 * "Arcane prayer scroll"), so while their unlock varbit (5451 / 5452, sent by the server's PrayerScrolls.syncUnlocks) is 0 the
 * script sees Prayer level 0 for those two cells and draws them grey. Only the normal prayer book (varbit 6840 = 0); the curses
 * book has no cells 28/29.
 */
final class PrayerScrollUnlocks {

    static final int CELL_SCRIPT = 52;
    static final int PRAYER = 5;
    static final int BOOK_INTERFACE = 271;
    static final int RIGOUR_CELL = 28;
    static final int AUGURY_CELL = 29;
    static final int RIGOUR_VARBIT = 5451;
    static final int AUGURY_VARBIT = 5452;
    static final int CURSES_VARBIT = 6840;

    /** Script 52's arguments: int local 3 is the component, int local 4 the sub id (the prayer cell). */
    static final int COMPONENT_LOCAL = 3;
    static final int SUB_ID_LOCAL = 4;

    private PrayerScrollUnlocks() {
    }

    /** STAT_BASE in a client script: [level], or 0 for a locked Rigour/Augury cell of the prayer book. */
    static int baseLevel(int scriptId, int skill, int level, int[] locals, VarbitReader varbits) {
        if (scriptId != CELL_SCRIPT || skill != PRAYER || locals == null || locals.length <= SUB_ID_LOCAL) {
            return level;
        }
        if ((locals[COMPONENT_LOCAL] >>> 16) != BOOK_INTERFACE || varbits.get(CURSES_VARBIT) != 0) {
            return level;
        }
        int cell = locals[SUB_ID_LOCAL];
        if (cell == RIGOUR_CELL && varbits.get(RIGOUR_VARBIT) == 0) {
            return 0;
        }
        if (cell == AUGURY_CELL && varbits.get(AUGURY_VARBIT) == 0) {
            return 0;
        }
        return level;
    }

    interface VarbitReader {
        int get(int varbit);
    }
}
