import com.jagex.graphics.Toolkit;

/**
 * N273 (owner 2026-10-24: "in de deadman tab staat deadman chest maar wanneer je open worldmap doet laat die niet zien waar die
 * staat met een blauw pijltje"): a blue arrow on the world map over the Deadman's Chest that players can loot, with its name under
 * the spot. The server sends {@code __RSPS_HUD__:chest|x|z|plane} whenever the timed chest appears (and at login while it stands)
 * and {@code chest|} once it is looted or gone. Drawn after the map, the Deadman zones and the grave (WorldMap.draw), only when the
 * chest lies in the map area being shown; the arrow bobs gently like the game's hint arrows.
 */
public final class ChestWorldMapMarker {

    private static volatile int x = -1;
    private static volatile int z = -1;
    private static volatile int plane = -1;

    private static final int EDGE = 0xFF08142E;
    private static final int FILL = 0xFF2F7BFF;
    private static final int SHINE = 0xFF8EC0FF;
    private static final int LABEL = 0x8EC0FF;
    private static final String NAME = "Deadman's Chest";

    /** Arrow head: [HEAD_HALF] px either side of the tip, [HEAD_HEIGHT] rows; shaft [SHAFT_HALF] px either side, [SHAFT_HEIGHT] rows. */
    private static final int HEAD_HALF = 7;
    private static final int HEAD_HEIGHT = 8;
    private static final int SHAFT_HALF = 3;
    private static final int SHAFT_HEIGHT = 8;

    private ChestWorldMapMarker() {
    }

    static void update(String rest) {
        if (rest.isEmpty()) {
            reset();
            return;
        }
        String[] parts = rest.split("[|]");
        x = Integer.parseInt(parts[0]);
        z = Integer.parseInt(parts[1]);
        plane = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
    }

    static void reset() {
        x = -1;
        z = -1;
        plane = -1;
    }

    static boolean active() {
        return x >= 0;
    }

    /** Same projection as GraveWorldMapMarker / DeadmanWorldMapOverlay. */
    static void draw(Toolkit toolkit) {
        if (!active()) {
            return;
        }
        int sourceWidth = WorldMap.anInt5647 - WorldMap.anInt5652;
        int sourceHeight = WorldMap.anInt5645 - WorldMap.anInt5654;
        if (sourceWidth <= 0 || sourceHeight <= 0 || WorldMap.areaWidth <= 0 || WorldMap.areaHeight <= 0) {
            return;
        }
        if (x < WorldMap.areaX || x >= WorldMap.areaX + WorldMap.areaWidth || z < WorldMap.areaZ || z >= WorldMap.areaZ + WorldMap.areaHeight) {
            return;
        }
        int left = WorldMap.anInt5649;
        int top = WorldMap.anInt5653;
        int right = WorldMap.anInt5651;
        int bottom = WorldMap.anInt5646;
        double scaleX = (right - left) / (double) sourceWidth;
        double scaleZ = (bottom - top) / (double) sourceHeight;
        double originX = left - (WorldMap.areaX + WorldMap.anInt5652) * scaleX;
        double originZ = bottom + (WorldMap.areaZ + WorldMap.anInt5654) * scaleZ;
        int sx = (int) Math.round(originX + (x + 0.5) * scaleX);
        int sy = (int) Math.round(originZ - (z + 0.5) * scaleZ);
        if (sx < left || sx > right || sy < top || sy > bottom) {
            return;
        }
        // The tip sits just above the spot and bobs 0-3 px up (a 1.2 s cycle).
        double phase = (System.currentTimeMillis() % 1200L) / 1200.0 * Math.PI * 2.0;
        int tip = sy - 2 - (int) Math.round(1.5 + 1.5 * Math.sin(phase));
        drawArrow(toolkit, sx, tip, EDGE, 1);
        drawArrow(toolkit, sx, tip - 1, FILL, 0);
        // A light edge down the left of the shaft and head, so it reads as a solid arrow on any map colour.
        toolkit.aa(sx - SHAFT_HALF + 1, tip - 1 - HEAD_HEIGHT - SHAFT_HEIGHT + 2, 1, SHAFT_HEIGHT - 1, SHINE, 1);
        WorldMapFont font = WorldMap.font12;
        if (font != null) {
            int width = font.totalWidth(NAME);
            font.render(toolkit, NAME, null, sx - width / 2, sy + 4, LABEL, true);
        }
    }

    /** A down-pointing arrow whose tip is at ([cx], [tipY]); [grow] px larger on every side for the outline. */
    private static void drawArrow(Toolkit toolkit, int cx, int tipY, int colour, int grow) {
        // Head: row 0 is the tip, widest at the top.
        for (int row = 0; row < HEAD_HEIGHT + grow; row++) {
            int half = Math.min(HEAD_HALF + grow, row * (HEAD_HALF + grow) / Math.max(1, HEAD_HEIGHT - 1));
            toolkit.aa(cx - half, tipY + grow - row, half * 2 + 1, 1, colour, 1);
        }
        int shaftBottom = tipY - HEAD_HEIGHT + 1;
        toolkit.aa(cx - SHAFT_HALF - grow, shaftBottom - SHAFT_HEIGHT - grow, (SHAFT_HALF + grow) * 2 + 1, SHAFT_HEIGHT + grow, colour, 1);
    }
}
