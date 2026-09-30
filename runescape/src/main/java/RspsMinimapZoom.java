/**
 * RuneLite Minimap plugin's "Zoom" (owner 2026-09-30; MinimapConfig.zoom, default on; RuneLite 1.9.0 release notes: the mouse
 * wheel zooms the minimap, right-clicking the minimap resets it). RuneLite's API describes the value as the number of pixels
 * per tile on the minimap, default 4 (Client.getMinimapZoom / setMinimapZoom), and MinimapPlugin saves it as "zoomLevel";
 * here it is saved with the client's other local options (ClientOptions trailer v2).
 *
 * The minimap is drawn at 4 pixels per tile (Minimap.draw: the map sprite at scale 4096, every dot / element at tile * 4),
 * so the zoom is one factor, {@link #factor()} = zoom / 4, applied where the minimap already applies its own (anti-bot)
 * scale offset: the map sprite's scale, the dot / element / hint / label rotation vectors, and the inverse for a click.
 *
 * SOURCE_BLOCKED: the wheel step and the zoom limits live in RuneLite's closed injected client; the values below
 * (0.5 px per notch, 2..8 px per tile) are this client's, chosen to keep the minimap inside the loaded 104-tile scene.
 */
final class RspsMinimapZoom {

    static final double DEFAULT = 4.0;
    static final double MIN = 2.0;
    static final double MAX = 8.0;
    static final double STEP = 0.5;

    /** Client-only menu action of the minimap's "Reset zoom" (outside RspsDev's 1900.. range and the game's own ids). */
    static final int ACTION_RESET = 1950;

    static double zoom = DEFAULT;

    /** Set by the interface logic pass when the mouse is over the minimap's drawn (round) area this frame. */
    static boolean hovered;

    private RspsMinimapZoom() {
    }

    static boolean on() {
        return RspsHudConfig.enabled("minimap") && RspsHudConfig.bool("minimap.zoom");
    }

    /** Scale of the minimap against the standard 4 pixels per tile (1.0 when the zoom is off). */
    static double factor() {
        return on() ? zoom / DEFAULT : 1.0;
    }

    /** A 14-bit rotation component (sin / cos) with the zoom applied: the drawn distance of every dot and element. */
    static int scale(int trig) {
        return (int) Math.round(trig * factor());
    }

    /** The inverse, for a click on the minimap (screen pixels back to tiles). */
    static int unscale(int trig) {
        return (int) Math.round(trig / factor());
    }

    /** The map sprite's own scale (4096 = 1:1). */
    static int spriteScale(int scale) {
        return (int) Math.round(scale * factor());
    }

    /**
     * Mouse wheel over the minimap (game thread, before the camera gets it): zooms the minimap and takes the input.
     * False when the zoom is off or the mouse is elsewhere, so the wheel goes on to the camera as before.
     */
    static boolean wheel(int rotation) {
        if (rotation == 0 || !hovered || !on()) {
            return false;
        }
        set(zoom - rotation * STEP);
        return true;
    }

    static void reset() {
        set(DEFAULT);
    }

    static boolean isZoomed() {
        return on() && zoom != DEFAULT;
    }

    /** Start-up: the saved value, if it is a valid one. */
    static void restore(double saved) {
        if (saved >= MIN && saved <= MAX) {
            zoom = saved;
        }
    }

    private static void set(double value) {
        double clamped = Math.max(MIN, Math.min(MAX, value));
        if (clamped == zoom) {
            return;
        }
        zoom = clamped;
        if (ClientOptions.instance != null) {
            ClientOptions.save();
        }
    }
}
