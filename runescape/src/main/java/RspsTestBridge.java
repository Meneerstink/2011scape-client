/**
 * Owner 2026-10-17 (BUGLIJST T18 / N17): the agent team's test hook ({@code com.jagex.TestHook}: clicks, keys and
 * screenshots over a local port, silent speakers) and {@link RspsTestDetail} are compiled into runescape.jar for the team's
 * test client, but left OUT of the player jar (client/build.gradle.kts jar excludes) - otherwise anyone could start the
 * player client with -Drsps.testHook and drive it like a bot (owner anti-cheat decision: only our client, no automation).
 *
 * The client never names those classes directly: every call comes through here, guarded by {@link #ENABLED}. In the player
 * jar the classes are missing, so ENABLED is false even with -Drsps.testHook and nothing of the hook ever loads.
 */
public final class RspsTestBridge {

    /** The test hook was asked for (-Drsps.testHook) AND is present in this build. */
    public static final boolean ENABLED = System.getProperty("rsps.testHook") != null && present();

    /** Test client without -Drsps.testSound: the speakers stay silent (the sound path and its [avtrace] still run). */
    public static final boolean SILENT = ENABLED && !Boolean.getBoolean("rsps.testSound");

    private RspsTestBridge() {
    }

    private static boolean present() {
        try {
            Class.forName("com.jagex.TestHook", false, RspsTestBridge.class.getClassLoader());
            return true;
        } catch (Throwable missing) {
            return false;
        }
    }

    public static void start() {
        if (ENABLED) {
            com.jagex.TestHook.start();
        }
    }

    public static void beforeFlip() {
        if (ENABLED) {
            com.jagex.TestHook.beforeFlip();
        }
    }

    public static void processDetail() {
        if (ENABLED) {
            RspsTestDetail.process();
        }
    }
}
