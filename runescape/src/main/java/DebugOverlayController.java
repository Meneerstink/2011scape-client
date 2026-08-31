import com.jagex.Client;

public final class DebugOverlayController {
    private DebugOverlayController() {
    }

    public static void resetForStartup() {
        Client.displayFps = false;
        Static354.showProfiling = false;
    }

    public static boolean toggleFps() {
        setFps(!Client.displayFps);
        return Client.displayFps;
    }

    public static void setFps(boolean enabled) {
        Client.displayFps = enabled;
        if (!enabled) {
            Static354.showProfiling = false;
        }
    }
}
