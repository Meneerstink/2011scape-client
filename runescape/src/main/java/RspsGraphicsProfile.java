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
     * stands between the camera and the player (Static276).
     */
    public static final boolean ROOFS_HIDE_ONLY_UNDER_PLAYER = true;

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
