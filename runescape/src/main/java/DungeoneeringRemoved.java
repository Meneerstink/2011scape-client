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
     * Owner 2026-10-30 (bug-20261030-201754: "de onderkant waar totallevel staat mist achtergrond"; design A chosen): the tab has no
     * background of its own - only the cells cover the panel - so beside one middle cell the bare panel showed. The total's frame now
     * spans the whole bottom row (the three columns, 3..187), drawn by [renderWide] with the frame's own corners and edges.
     */
    static final int TOTAL = 201;
    static final int CELL_FRAME = 152;
    static final int CELL_X = 3;
    static final int CELL_Y = 229;
    static final int CELL_WIDTH = 184;
    static final int CELL_HEIGHT = 27;

    /** Native columns of the frame sprite kept as they are on each side; the part between them is stretched. */
    static final int FRAME_EDGE = 10;

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
            component.originalWidth = CELL_WIDTH;
        }
        if (slot == CELL_FRAME) {
            component.originalWidth = CELL_WIDTH;
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

    /** InterfaceManager: whether [component] is the total level's frame, drawn by [renderWide]. */
    static boolean wideFrame(Component component) {
        return component.slot == ((STATS_INTERFACE << 16) | CELL_FRAME);
    }

    /**
     * The skill cell frame [sprite] drawn [width] wide: its left and right [FRAME_EDGE] columns at their own size (the corners), the
     * columns between stretched over the rest. Clipped to [clipX1]..[clipY2], the parent's clip, which is restored afterwards.
     */
    static void renderWide(com.jagex.graphics.Sprite sprite, int x, int y, int width, int height, int clipX1, int clipY1, int clipX2, int clipY2) {
        int own = sprite.scaleWidth();
        if (width <= own || own <= 2 * FRAME_EDGE) {
            sprite.render(x, y, width, height);
            return;
        }
        int inner = own - 2 * FRAME_EDGE;
        int span = width - 2 * FRAME_EDGE;
        int stretched = own * span / inner;
        clip(x, y, x + FRAME_EDGE, y + height, clipX1, clipY1, clipX2, clipY2);
        sprite.render(x, y, own, height);
        clip(x + width - FRAME_EDGE, y, x + width, y + height, clipX1, clipY1, clipX2, clipY2);
        sprite.render(x + width - own, y, own, height);
        clip(x + FRAME_EDGE, y, x + width - FRAME_EDGE, y + height, clipX1, clipY1, clipX2, clipY2);
        sprite.render(x + FRAME_EDGE - FRAME_EDGE * span / inner, y, stretched, height);
        com.jagex.graphics.Toolkit.active.KA(clipX1, clipY1, clipX2, clipY2);
        // N356 (owner 2026-10-31 23:2x: "zwarte achtergrond zoals OSRS, nu grijs"): the frame's grey inside becomes black, its 1 px
        // border and rounded corners stay - a plus of two fills keeps 3 px off each corner (sprite 3030: border row 0 / column 0,
        // the corner curve within 3 px, the bottom border on the last 2 rows).
        if (width > 8 && height > 8) {
            com.jagex.graphics.Toolkit.active.aa(x + 4, y + 1, width - 8, height - 3, TOTAL_BACKGROUND, 0);
            com.jagex.graphics.Toolkit.active.aa(x + 1, y + 4, width - 2, height - 8, TOTAL_BACKGROUND, 0);
        }
    }

    /** N356: the total level box's background (OSRS: black). */
    static final int TOTAL_BACKGROUND = 0x000000;

    private static void clip(int x1, int y1, int x2, int y2, int clipX1, int clipY1, int clipX2, int clipY2) {
        com.jagex.graphics.Toolkit.active.KA(Math.max(x1, clipX1), Math.max(y1, clipY1), Math.min(x2, clipX2), Math.min(y2, clipY2));
    }

    /** STAT_BASE in a client script. */
    static int scriptBaseLevel(int skill, int level) {
        return skill == SKILL ? 0 : level;
    }
}
