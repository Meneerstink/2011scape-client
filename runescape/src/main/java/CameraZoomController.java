/** Routes mouse-wheel input to the 3D camera only after interface hit-testing has completed. */
public final class CameraZoomController {
    static final int MIN_ZOOM = 150;
    static final int MAX_ZOOM = 660;
    static final int ZOOM_STEP = 15;
    static final int DEFAULT_ZOOM = 320;
    private static int userZoom = DEFAULT_ZOOM;

    static int getZoom() { return userZoom; }

    static void restoreZoom(int zoom) {
        userZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    static int scaleDistance(int distance) {
        return (int) ((long) distance * userZoom / DEFAULT_ZOOM);
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
        userZoom = adjustedZoom(userZoom, rotation);
        if (ClientOptions.instance != null) {
            ClientOptions.instance.markCustom();
            ClientOptions.instance.cameraZoom = userZoom;
            ClientOptions.save();
        }
        return true;
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

    static int adjustedZoom(int current, int rotation) {
        long requested = (long) current + (long) rotation * ZOOM_STEP;
        if (requested < MIN_ZOOM) {
            return MIN_ZOOM;
        }
        if (requested > MAX_ZOOM) {
            return MAX_ZOOM;
        }
        return (int) requested;
    }

    private CameraZoomController() {
        /* utility class */
    }
}
