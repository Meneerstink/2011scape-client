/**
 * Routes mouse-wheel input to the 3D camera only after interface hit-testing has completed, and owns the player's zoom.
 *
 * Owner 2026-09-30: RuneLite's Camera plugin (config group "zoom", CameraConfig / CameraPlugin, runelite master) imported.
 * The zoom is kept in OSRS / RuneLite units - bigger is closer, {@link #DEFAULT_ZOOM} 512 is the default and the reset
 * position, the standard limits are {@link #OSRS_MIN} 128 .. {@link #OSRS_MAX} 896 - and every step follows OSRS's own
 * scroll-wheel script (cs2 39 ScrollWheelZoomHandler: {@code zoom -= rotation * increment}, increment 25, then script 42
 * clamps to the limits). RuneLite's settings then act exactly where they act on OSRS:
 *  - "Expand inner zoom limit" raises the upper limit to {@link #INNER_ZOOM_LIMIT} 1400 (CameraPlugin.applyConfigs);
 *  - "Expand outer zoom limit" lowers the lower limit by -400..400 (CAMERA_ZOOM_*_MIN - outerLimitAdj);
 *  - "Zoom speed" replaces the 25 of "scrollWheelZoomIncrement";
 *  - "Control function": Hold to zoom (the wheel only zooms while Ctrl is down, "scrollWheelZoom") / Reset zoom (releasing
 *    Ctrl zooms to "Reset zoom position", CameraPlugin.keyReleased -> CAMERA_DO_ZOOM, clamped to the limits).
 *
 * The 667 camera zooms by distance, not by projection scale, so {@link #scaleDistance} turns the OSRS value into a
 * distance factor: 512 is this client's normal distance and the standard OSRS range 128..896 lands on this client's old
 * validated range (x2.06 out .. x0.48 in, the former 150..660 of 320), each 25 step the same relative change.
 */
public final class CameraZoomController {
    static final int DEFAULT_ZOOM = 512;
    static final int OSRS_MIN = 128;
    static final int OSRS_MAX = 896;
    static final int INNER_ZOOM_LIMIT = 1400;
    static final int OUTER_LIMIT_MIN = -400;
    static final int OUTER_LIMIT_MAX = 400;
    static final int DEFAULT_ZOOM_INCREMENT = 25;
    /** Furthest out any setting allows: 128 - 400. */
    static final int ABSOLUTE_MIN = OSRS_MIN - OUTER_LIMIT_MAX;

    /** The debug console's script zoom (Camera.zoom, a projection value of its own) keeps its old clamp. */
    static final int SCRIPT_ZOOM_MIN = 150;
    static final int SCRIPT_ZOOM_MAX = 660;

    /** Distance factor per OSRS zoom unit: 2^((512 - zoom) / 368) keeps 128 -> x2.06 and 896 -> x0.48. */
    private static final double UNITS_PER_DOUBLING = 368.0;

    private static int userZoom = DEFAULT_ZOOM;

    /** Ctrl as the game's keyboard monitor sees it (RuneLite CameraPlugin.controlDown). */
    private static boolean controlDown;

    /**
     * The chatbox pane interface. Server dialogues (npc / player chat heads, message boxes, option and item boxes) are mounted
     * into 752:12 / 752:13 as MODAL sub-interfaces (server QueueTaskExt `openInterface(parent = 752, ...)`, InterfaceDestination
     * CHAT_BOX_PANE), but they sit in the chatbox and never cover the 3D scene.
     */
    static final int CHATBOX_PANE_INTERFACE = 752;

    /**
     * The zoom in effect: the saved value inside the current limits. The limits are applied here, where the zoom is used, and
     * not written back when the settings change: the login push arrives in several packets ("zoom.enabled" in the first,
     * the limits in a later one), so writing back would clamp an expanded zoom to the defaults before the account's own
     * limits arrive. Switching the plugin off and on again gives the old zoom back.
     */
    static int getZoom() { return clamp(userZoom); }

    /** Camera plugin switched on (the 78 Plugins sidebar); off = OSRS's own standard limits and step. */
    private static boolean pluginOn() {
        return RspsHudConfig.enabled("zoom");
    }

    static int minZoom() {
        if (!pluginOn()) {
            return OSRS_MIN;
        }
        int adjust = Math.max(OUTER_LIMIT_MIN, Math.min(OUTER_LIMIT_MAX, RspsHudConfig.integer("zoom.outerLimit")));
        return OSRS_MIN - adjust;
    }

    static int maxZoom() {
        return pluginOn() && RspsHudConfig.bool("zoom.inner") ? INNER_ZOOM_LIMIT : OSRS_MAX;
    }

    static int increment() {
        return pluginOn() ? RspsHudConfig.integer("zoom.zoomIncrement") : DEFAULT_ZOOM_INCREMENT;
    }

    /**
     * RCV-012.B15: whether an open sub-interface blocks world zoom. Only a MODAL sub-interface outside the chatbox pane does (the
     * bank-style screens the modal block was meant for); a dialogue in the chatbox left wheel zoom blocked for as long as the
     * player was talking to an npc.
     */
    static boolean blocksWorldZoom(boolean modal, int parentInterfaceId) {
        return modal && parentInterfaceId != CHATBOX_PANE_INTERFACE;
    }

    /**
     * A saved zoom back at start-up. Only the absolute range applies here: the account's own limits arrive with the
     * settings push after login and {@link #getZoom} applies them, so an expanded zoom is not lost to the defaults first.
     */
    static void restoreZoom(int zoom) {
        userZoom = Math.max(ABSOLUTE_MIN, Math.min(INNER_ZOOM_LIMIT, zoom));
    }

    static int clamp(int zoom) {
        return Math.max(minZoom(), Math.min(maxZoom(), zoom));
    }

    static int scaleDistance(int distance) {
        return (int) Math.round(distance * Math.pow(2.0, (DEFAULT_ZOOM - getZoom()) / UNITS_PER_DOUBLING));
    }

    public static boolean applyWheelRotation(
        int rotation,
        boolean overScene,
        boolean claimedByInterface,
        boolean modalOpen,
        boolean menuOpen,
        boolean debugConsoleOpen,
        boolean staffPlaneShortcut
    ) {
        if (!shouldConsume(rotation, overScene, claimedByInterface, modalOpen, menuOpen, debugConsoleOpen, staffPlaneShortcut)) {
            return false;
        }
        // "Hold to zoom": without Ctrl the wheel does nothing to the camera (script 39 returns before zooming).
        if (pluginOn() && RspsHudConfig.is("zoom.controlFunction", "CONTROL_TO_ZOOM") && !controlDown) {
            return true;
        }
        setZoom(adjustedZoom(getZoom(), rotation));
        return true;
    }

    private static void setZoom(int zoom) {
        userZoom = zoom;
        if (ClientOptions.instance != null) {
            ClientOptions.instance.markCustom();
            ClientOptions.instance.cameraZoom = userZoom;
            ClientOptions.save();
        }
    }

    /**
     * Once per client cycle (game thread) with the Ctrl key state: RuneLite CameraPlugin.keyPressed / keyReleased. Releasing
     * Ctrl with "Reset zoom" zooms to "Reset zoom position" (clamped to -400..1400, then to the limits by script 42). Without
     * window focus Ctrl simply counts as up and nothing resets (RuneLite CameraPlugin.onFocusChanged).
     */
    static void tickControl(boolean ctrl, boolean focused) {
        if (!focused) {
            controlDown = false;
            return;
        }
        boolean released = controlDown && !ctrl;
        controlDown = ctrl;
        if (released && pluginOn() && RspsHudConfig.is("zoom.controlFunction", "CONTROL_TO_RESET")) {
            int value = Math.max(OUTER_LIMIT_MIN, Math.min(INNER_ZOOM_LIMIT, RspsHudConfig.integer("zoom.ctrlZoomValue")));
            setZoom(clamp(value));
        }
    }

    static boolean shouldConsume(
        int rotation,
        boolean overScene,
        boolean claimedByInterface,
        boolean modalOpen,
        boolean menuOpen,
        boolean debugConsoleOpen,
        boolean staffPlaneShortcut
    ) {
        return rotation != 0
            && overScene
            && !claimedByInterface
            && !modalOpen
            && !menuOpen
            && !debugConsoleOpen
            && !staffPlaneShortcut;
    }

    /** Script 39: wheel down (positive rotation) zooms out, by the increment per notch, then script 42's clamp. */
    static int adjustedZoom(int current, int rotation) {
        long requested = (long) current - (long) rotation * increment();
        if (requested < minZoom()) {
            return minZoom();
        }
        if (requested > maxZoom()) {
            return maxZoom();
        }
        return (int) requested;
    }

    /** Old saved zoom (a 667 distance of 150..660 around 320) in OSRS units, so an upgrade keeps the player's view. */
    static int fromLegacyDistance(int distance) {
        double factor = distance / 320.0;
        return (int) Math.round(DEFAULT_ZOOM - UNITS_PER_DOUBLING * Math.log(factor) / Math.log(2.0));
    }

    private CameraZoomController() {
        /* utility class */
    }
}
