import com.jagex.core.constants.MainLogicStep;
import com.jagex.graphics.Toolkit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local preset version; deliberately independent of the server's preference packet version. */
public final class RspsGraphicsProfile {
    public static final String NAME = "MID";
    public static final int VERSION = 5;
    private static final AtomicBoolean restoreRequested = new AtomicBoolean();

    /**
     * Owner 2026-09-26: a selectively hidden roof disappears only when the player walks in under it, never because it
     * stands between the camera and the player (Static276). Owner 2026-10-09 (Q1 = A): back to normal RS - Jagex's
     * camera-to-player line test is on again, so a roof between the camera and the player hides; this flag is off.
     */
    public static final boolean ROOFS_HIDE_ONLY_UNDER_PLAYER = false;

    /** Last {@link #cameraBuildingTile()} result (inside or not), for the enter/leave trace. */
    private static boolean cameraWasInside;

    /** The camera tile first, then its eight neighbours (the wall ring). */
    private static final int[] RING_DX = {0, -1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] RING_DZ = {0, -1, -1, -1, 0, 0, 1, 1, 1};

    /**
     * Jagex's camera-in-building test (Static276, non-follow camera branch): the camera is less than 3200 units above the
     * ground and its tile on the rendered level is roofed (0x4). A camera in the wall ring counts too: the walls stand on the
     * unflagged ring around the roofed floor (Lumbridge castle, the Royal Hall), which the roof flood (Static409) hides with
     * the building; the flood then starts from the roofed floor tile beside it. Returns that start tile as x << 16 | z
     * (scene tiles), or -1 when the camera is outside every building. Traced as {@code [camtrace]} on every change with
     * C:/RSPS/trace.on (owner 2026-10-09: mansion camera).
     */
    public static int cameraBuildingTile() {
        int level = Camera.renderingLevel;
        int tx = Camera.x >> 9;
        int tz = Camera.z >> 9;
        int start = -1;
        if (level >= 0 && level < 4 && Static102.averageHeight(level, Camera.x, Camera.z) - Camera.y < 3200) {
            for (int d = 0; d < RING_DX.length && start < 0; d++) {
                int x = tx + RING_DX[d];
                int z = tz + RING_DZ[d];
                if (x >= 0 && z >= 0 && x < Static720.mapWidth && z < Static501.mapLength && (Static280.tileFlags[level][x][z] & 0x4) != 0) {
                    start = x << 16 | z;
                }
            }
        }
        boolean inside = start >= 0;
        if (inside != cameraWasInside) {
            cameraWasInside = inside;
            if (com.jagex.core.util.RspsTrace.ENABLED) {
                System.out.println("[camtrace " + System.currentTimeMillis() + "] camera " + (inside ? "entered" : "left") + " a building at "
                    + (tx + WorldMap.areaBaseX) + "," + (tz + WorldMap.areaBaseZ) + "," + level + " height " + (Static102.averageHeight(level, Camera.x, Camera.z) - Camera.y)
                    + (inside ? " - levels above hidden from " + ((start >> 16) + WorldMap.areaBaseX) + "," + ((start & 0xFFFF) + WorldMap.areaBaseZ) : ""));
            }
        }
        return start;
    }

    /**
     * First map z north of the surface world (map square row 65): every underground map - dungeons at z 4400..10000, God Wars
     * at 5184, Barrows tunnels at 9664 - lies above it, the surface (Weiss, Lunar Isle, the Wilderness) below 4100.
     */
    public static final int UNDERGROUND_Z = 4160;

    /**
     * RSPS map tile flag 0x20 on the level above the player: "part of the building below" (the Royal Hall's balconies and
     * upper doorways, RoyalHallMapTool.BELOW_BUILDING). Owner 2026-09-27: from inside the hall no balcony may show through
     * the upper doorways, yet a player on or under a balcony is outside and keeps the roof. Such a tile has no roof flag of
     * its own; the roof flood (Static409) only runs on over it once it started from a player inside.
     */
    public static final int BELOW_BUILDING = 0x20;

    public static boolean belongsToBuildingBelow(int x, int z) {
        int level = Camera.renderingLevel + 1;
        return level <= 3 && (Static280.tileFlags[level][x][z] & BELOW_BUILDING) != 0;
    }

    /**
     * Outside test for the roof flood's diagonal steps (Static409): a BELOW_BUILDING tile counts as roofed there too, like in
     * its straight steps. Otherwise the flood runs out over a balcony row and steps diagonally into a walled ring tile from
     * outside, Jagex's edge-wall check keeps that tile's upper walls, and whole wall columns float inside the hall (owner
     * 2026-10-09 17:47, every storey). Only the Royal Hall has 0x20, so every other building is unchanged.
     */
    public static boolean outsideBuilding(int x, int z) {
        return (Static280.tileFlags[Camera.renderingLevel][x][z] & 0x4) == 0 && !belongsToBuildingBelow(x, z);
    }

    private RspsGraphicsProfile() { }

    public static void apply(ClientOptions options) {
        options.antialiasingMode.setSafeValue(0);
        options.antialiasingQuality.setSafeValue(0);
        options.bloom.setSafeValue(0);
        options.brightness.setSafeValue(3);
        options.buildArea.setSafeValue(0);
        // Owner 2026-09-25 (Royal Hall roof): selectively hidden (2), as in RuneScape - roofs show from outside and vanish
        // when the player walks in under them. Version 5 changed this from always hidden (1).
        options.removeRoofs.setSafeValue(2);
        options.removeRoofsOverride.setSafeValue(2);
        options.orthographic.setSafeValue(0);
        options.spotShadows.setSafeValue(0);
        options.hardShadows.setSafeValue(0);
        options.lightDetail.setSafeValue(0);
        options.waterDetail.setSafeValue(0);
        options.fog.setSafeValue(0);
        options.idleAnimations.setSafeValue(0); // Few, verified against the in-game dropdown.
        options.groundDecor.setSafeValue(0);
        options.groundBlending.setSafeValue(0);
        options.textures.setSafeValue(1);
        options.flickeringEffects.setSafeValue(1);
        options.particles.setSafeValue(1);
        options.cpuUsage.setSafeValue(4);
        options.customCursors.setSafeValue(0); // Owner requirement: custom cursor defaults to Off.
        options.maxScreenSize.setSafeValue(com.jagex.core.constants.MaxScreenSize.ANY);
        options.validate();
        options.graphicsQuality.setSafeValue(3); // Rev-667 MID.
    }

    /** Called by the AWT menu; actual changes belong on the game thread. */
    public static void requestRestore() {
        restoreRequested.set(true);
    }

    static void processPendingRestore() {
        int step = MainLogicManager.step;
        if (ClientOptions.instance == null || Toolkit.active == null
                || !(MainLogicStep.isAtGameScreen(step)
                || step == MainLogicStep.STEP_LOGIN_SCREEN
                || step == MainLogicStep.STEP_LOBBY_SCREEN)) {
            return;
        }
        if (!restoreRequested.getAndSet(false)) {
            return;
        }
        ClientOptions options = ClientOptions.instance;
        apply(options);
        // Recreate AA/bloom resources and rebuild the map using the existing renderer path.
        Static32.setToolkit(options.toolkit.getValue(), false);
        Static77.method1561();
        ClientOptions.save();
        Static503.sentPreferences = false;
    }
}
