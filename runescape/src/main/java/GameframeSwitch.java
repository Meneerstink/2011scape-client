import com.jagex.core.constants.ClientScriptOpCode;
import com.jagex.core.constants.WindowMode;

/**
 * A2 round 6 (ROOT_CAUSES class 206, Keurmeester A2 round 5 FAIL): a window-mode switch the player makes in game (Graphics
 * Options FIXED / RESIZEABLE, fullscreen enter / leave) changes the gameframe as well - fixed = 548, resizable and fullscreen
 * = 746. Only the server opens the gameframe (IF_OPENTOP after the client's WINDOW_STATUS, one server tick or more later), so
 * the 667 client drew the OLD gameframe laid out in the NEW canvas for 0.4-3.5 s: the resizable frame squeezed into the fixed
 * 765x503 game (minimap/orbs over the Graphics Options window), or the fixed frame in the corner of a resizable window. With
 * Stretched Mode that wrong frame is also drawn over the whole window.
 *
 * Here the mode and its gameframe change in the same frame: the player's choice goes to the server at once (WINDOW_STATUS
 * with the NEW mode, the server answers with the new gameframe exactly as before), but the client keeps drawing the old mode
 * - a complete, correct picture - until that gameframe arrives, and switches the canvas right before it sets the new top
 * level. One switch is in flight at a time; a later choice waits and runs after it (each step is a whole, correct frame).
 * Switches inside one gameframe (resizable <-> fullscreen), every switch outside the game (login screen) and every
 * relayout of the current mode (window resize) happen at once, as before. Donor: the server side is unchanged (Darkan
 * InterfaceManager.switchDisplayModes / Novite WindowStatus: the server opens the gameframe for the reported mode); the client
 * only stops drawing a mode before its gameframe is there.
 */
public final class GameframeSwitch {

    public static final int FIXED_GAMEFRAME = 548;
    public static final int RESIZABLE_GAMEFRAME = 746;

    /** The server answers within one tick (600 ms); a busy client took up to 3.5 s (Keurmeester round 5). After this, switch anyway. */
    public static final long TIMEOUT_MS = 8000L;

    /** What the switch needs from the client (InterfaceManager.ClientHost; a recording host in GameframeSwitchRegressionTest). */
    public interface Host {
        /** The mode the client draws now (InterfaceManager.getWindowMode()). */
        int appliedMode();

        /** Logged in and at the game screen with a connection: the server answers WINDOW_STATUS with a gameframe. */
        boolean inGame();

        /** Switch the client to [mode] now (InterfaceManager.changeWindowMode); [sendStatus] false = the server already has it. */
        void apply(int mode, int width, int height, boolean sendStatus);

        /** WINDOW_STATUS with [mode] (ServerConnectionReader.sendWindowStatus). */
        void sendWindowStatus(int mode);

        long now();

        void log(String line);
    }

    static Host host;

    private static int awaitedMode = -1;
    private static int awaitedWidth;
    private static int awaitedHeight;
    private static long awaitedSince;

    private static int queuedMode = -1;
    private static int queuedWidth;
    private static int queuedHeight;

    private GameframeSwitch() {
    }

    public static int gameframeFor(int mode) {
        return mode == WindowMode.FIXED ? FIXED_GAMEFRAME : RESIZABLE_GAMEFRAME;
    }

    /** The mode whose gameframe the client waits for, or -1. */
    public static int awaitedMode() {
        return awaitedMode;
    }

    /**
     * The mode every WINDOW_STATUS reports: the awaited one while a switch waits - a window resize or a Stretched Mode relayout
     * of the still-drawn mode must not turn the server back to the old gameframe - else the drawn one.
     */
    public static int reportedMode(int appliedMode) {
        return awaitedMode != -1 ? awaitedMode : appliedMode;
    }

    /**
     * CS2 GETWINDOWMODE while a switch waits for its gameframe (nakijker A2 round 6, ROOT_CAUSES class 208). [chooser] = the
     * running script chooses window modes itself (it holds SETWINDOWMODE / FULLSCREEN_ENTER / FULLSCREEN_EXIT - the Graphics
     * Options scripts 2582/2698/2699/2705/2710) or this script run already chose one: it reads the mode the player chose last -
     * 2698 starts with "if GETWINDOWMODE == arg0 return" and checks GETWINDOWMODE right after SETWINDOWMODE ("unable to enter that
     * display mode"). Every other script reads the DRAWN mode: it lays out the gameframe that is still on screen.
     */
    public static int modeForScript(boolean chooser, int drawnMode) {
        if (!chooser || host == null) {
            return drawnMode;
        }
        int wanted = wantedMode();
        return wanted == -1 ? drawnMode : wanted;
    }

    /** A script that chooses a window mode: it holds CS2 SETWINDOWMODE 5307, FULLSCREEN_ENTER 5300 or FULLSCREEN_EXIT 5301. */
    public static boolean choosesWindowMode(int[] opcodes) {
        if (opcodes == null) {
            return false;
        }
        for (int op : opcodes) {
            if (op == ClientScriptOpCode.SETWINDOWMODE || op == ClientScriptOpCode.FULLSCREEN_ENTER || op == ClientScriptOpCode.FULLSCREEN_EXIT) {
                return true;
            }
        }
        return false;
    }

    /** The mode the player chose last: queued, else awaited, else the drawn one. */
    public static int wantedMode() {
        if (queuedMode != -1) {
            return queuedMode;
        }
        if (awaitedMode != -1) {
            return awaitedMode;
        }
        return host == null ? -1 : host.appliedMode();
    }

    /** The player's choice (CS2 SETWINDOWMODE / FULLSCREEN_ENTER / FULLSCREEN_EXIT, dev console "wm"). */
    public static void request(int mode, int width, int height) {
        if (awaitedMode != -1) {
            if (mode == awaitedMode) {
                // Chosen again (or back after another choice): the switch in flight already goes there.
                awaitedWidth = width;
                awaitedHeight = height;
                queuedMode = -1;
                return;
            }
            queuedMode = mode;
            queuedWidth = width;
            queuedHeight = height;
            host.log("[78] window mode " + mode + " chosen while mode " + awaitedMode + " waits for its gameframe - it follows right after");
            return;
        }
        // The newest choice wins: one still queued behind a switch that just finished is replaced (class test: a choice in the very
        // frame the awaited gameframe arrived left the older queued one to run after it).
        queuedMode = -1;
        int applied = host.appliedMode();
        if (!host.inGame() || gameframeFor(mode) == gameframeFor(applied)) {
            host.apply(mode, width, height, true);
            return;
        }
        awaitedMode = mode;
        awaitedWidth = width;
        awaitedHeight = height;
        awaitedSince = host.now();
        host.sendWindowStatus(mode);
        host.log("[78] window mode " + mode + " waits for gameframe " + gameframeFor(mode) + " (mode " + applied + " stays drawn until it is there)");
    }

    /** IF_OPENTOP [id], right before the client sets it as the top level: the awaited gameframe switches the mode in the same frame. */
    public static void gameframeOpened(int id) {
        if (awaitedMode == -1 || id != gameframeFor(awaitedMode)) {
            return;
        }
        int mode = awaitedMode;
        long waited = host.now() - awaitedSince;
        awaitedMode = -1;
        landedThisCycle = true;
        // The WINDOW_STATUS of the choice carried the OLD canvas size: changeWindowMode reports the new mode's canvas now (same mode
        // as the server has, so the server opens nothing; it re-arms the HUD/summoning panes like after a window resize).
        host.apply(mode, awaitedWidth, awaitedHeight, true);
        host.log("[78] window mode " + mode + " drawn together with its gameframe " + id + " (" + waited + " ms after the choice)");
    }

    private static boolean landedThisCycle;

    /** True in the packet cycle in which an awaited gameframe switched the mode (MainLogicManager decodes the rest of that batch). */
    public static boolean landedThisCycle() {
        return landedThisCycle;
    }

    public static void endPacketCycle() {
        landedThisCycle = false;
    }

    /** Client loop, once per cycle: logged out / timed out while waiting, then the next queued choice. */
    public static void tick() {
        if (host == null) {
            return;
        }
        if (awaitedMode != -1) {
            if (!host.inGame()) {
                int mode = awaitedMode;
                awaitedMode = -1;
                host.log("[78] window mode " + mode + " applied without its gameframe: left the game while it waited");
                host.apply(mode, awaitedWidth, awaitedHeight, true);
            } else if (host.now() - awaitedSince > TIMEOUT_MS) {
                int mode = awaitedMode;
                awaitedMode = -1;
                host.log("[78] PROBLEM window mode " + mode + ": gameframe " + gameframeFor(mode) + " did not arrive in " + TIMEOUT_MS + " ms - switched without it");
                host.apply(mode, awaitedWidth, awaitedHeight, true);
            }
        }
        if (awaitedMode == -1 && queuedMode != -1) {
            int mode = queuedMode;
            queuedMode = -1;
            request(mode, queuedWidth, queuedHeight);
        }
    }

    /** Tests only. */
    static void reset(Host newHost) {
        host = newHost;
        awaitedMode = -1;
        queuedMode = -1;
    }
}
