import com.jagex.graphics.Toolkit;

/**
 * The player's own gravestone on the world map (owner 2026-10-04 031637: "when you still have a grave and a timer it must
 * always show the exact location in the worldmap even when you log out"). The server sends {@code __RSPS_HUD__:grave|x|z|plane|
 * seconds} whenever the grave appears, on every login while it stands and when it changes, and {@code grave|} when it is gone;
 * the remaining time counts down here. Drawn after the map and the Deadman zones (WorldMap.draw), only when the grave lies in
 * the map area being shown.
 */
public final class GraveWorldMapMarker {

    private static volatile int x = -1;
    private static volatile int z = -1;
    private static volatile int plane = -1;
    /** System time the grave collapses, 0 when unknown. */
    private static volatile long endsAt;

    private static final int FILL = 0xFFB0B0B0;
    private static final int EDGE = 0xFF202020;
    private static final int LABEL = 0xFFFF40;

    private GraveWorldMapMarker() {
    }

    static void update(String rest) {
        if (rest.isEmpty()) {
            reset();
            return;
        }
        String[] parts = rest.split("[|]");
        x = Integer.parseInt(parts[0]);
        z = Integer.parseInt(parts[1]);
        plane = Integer.parseInt(parts[2]);
        endsAt = parts.length > 3 ? System.currentTimeMillis() + Long.parseLong(parts[3]) * 1000L : 0L;
    }

    static void reset() {
        x = -1;
        z = -1;
        plane = -1;
        endsAt = 0L;
    }

    static boolean active() {
        return x >= 0 && (endsAt == 0L || System.currentTimeMillis() < endsAt);
    }

    /** Label under the marker: "Your grave" and the time left (m:ss). */
    static String label(long now) {
        if (endsAt == 0L) {
            return "Your grave";
        }
        long left = Math.max(0L, (endsAt - now) / 1000L);
        return "Your grave (" + left / 60 + ":" + (left % 60 < 10 ? "0" : "") + left % 60 + ")";
    }

    /** Same projection as DeadmanWorldMapOverlay.draw. */
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
        // A small headstone: rounded top, cross on it.
        toolkit.aa(sx - 5, sy - 6, 11, 13, EDGE, 1);
        toolkit.aa(sx - 4, sy - 5, 9, 11, FILL, 1);
        toolkit.aa(sx - 1, sy - 4, 3, 8, EDGE, 1);
        toolkit.aa(sx - 3, sy - 2, 7, 2, EDGE, 1);
        WorldMapFont font = WorldMap.font12;
        if (font != null) {
            String text = label(System.currentTimeMillis());
            font.render(toolkit, text, null, sx - 32, sy + 9, LABEL, true);
        }
    }
}
