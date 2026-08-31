import com.jagex.Client;

public final class DebugOverlayRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        Client.displayFps = true;
        Static354.showProfiling = true;
        DebugOverlayController.resetForStartup();
        check(!Client.displayFps, "FPS/memory overlay defaults off");
        check(!Static354.showProfiling, "profiling overlay defaults off");

        check(DebugOverlayController.toggleFps(), "explicit toggle enables FPS overlay");
        check(Client.displayFps, "enabled state reaches renderer");

        Static354.showProfiling = true;
        DebugOverlayController.setFps(false);
        check(!Client.displayFps, "explicit disable hides FPS overlay");
        check(!Static354.showProfiling, "explicit disable hides the complete debug overlay");

        DebugOverlayController.setFps(true);
        check(Client.displayFps, "explicit command can re-enable overlay");
        DebugOverlayController.resetForStartup();
        check(!Client.displayFps && !Static354.showProfiling, "next startup always begins clean");

        System.out.println("Debug overlay regression passed: " + checks + " checks");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
