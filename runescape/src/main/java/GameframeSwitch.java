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

        /**
         * A2 round 7: the window part of a landed switch that waited for the first frame of the new mode - the AWT canvas size /
         * place, the toolkit surface and the client's own fixed-window fit (InterfaceManager.applyPendingCanvas). Runs right
         * before that frame is drawn.
         */
        void presentCanvas();

        /**
         * A2 round 7 part 2 (nakijker A2 round 7 FOUTEN 1): the hold ends - lay the new mode out for the window it is about to have
         * and, where the toolkit can (OpenGL with framebuffer objects, Stretched Mode off), draw its first frame OFFSCREEN while
         * the window keeps the old picture. True = the window part ({@link #presentCanvas}) waits until that frame is drawn and
         * runs right before its flip; false = nothing to draw offscreen (the window part runs before the frame, as in round 7).
         */
        boolean prepareCanvas();

        /** The first frame of a landed switch is on screen (flipped): the offscreen frame is no longer needed. */
        void presented();

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
        if ((held || presenting) && awaitedMode == -1) {
            // A2 round 7: the landed switch is not on screen yet - this choice runs right after its first frame.
            queuedMode = mode;
            queuedWidth = width;
            queuedHeight = height;
            return;
        }
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
        // A2 round 7 (Keurmeester round 6: Stretched OFF resizable -> fixed went BLACK 0.25-1 s; nakijker: the new gameframe was
        // drawn before its panels): the client's layout switches now, but the window keeps the last picture of the old mode until
        // the first complete frame of the new one is drawn - the canvas resize (which wipes a GL window) waits for that frame,
        // and that frame waits until this server tick's packets are in (PLAYER_INFO comes after the gameframe's IF_OPENSUBs).
        held = true;
        heldSince = host.now();
        tickEnded = false;
        deferringCanvas = true;
        try {
            // The WINDOW_STATUS of the choice carried the OLD canvas size: changeWindowMode reports the new mode's canvas now (same
            // mode as the server has, so the server opens nothing; it re-arms the HUD/summoning panes like after a window resize).
            host.apply(mode, awaitedWidth, awaitedHeight, true);
        } finally {
            deferringCanvas = false;
        }
        host.log("[78] window mode " + mode + " switched with its gameframe " + id + " (" + waited + " ms after the choice) - shown when its panels are in");
    }

    /** At most this long after the landing the old picture is kept for the new gameframe's panels (a server tick + a slow read). */
    public static final long HOLD_MAX_MS = 1200L;

    private static boolean held;
    private static long heldSince;
    private static boolean tickEnded;
    private static boolean deferringCanvas;
    private static long presentedAt = Long.MIN_VALUE;

    /** True while a landed switch applies its layout: the window part (canvas size, frame fit) waits for the first new frame. */
    public static boolean deferringCanvas() {
        return deferringCanvas;
    }

    /** True from the landing until the first frame of the new mode is drawn (the window still shows the old mode's picture). */
    public static boolean held() {
        return held;
    }

    /** PLAYER_INFO: the end of a server tick's packets - the gameframe and every IF_OPENSUB the server sent with it are in. */
    public static void serverTickEnded() {
        if (held) {
            tickEnded = true;
        }
    }

    /**
     * Client loop, before a frame is drawn: false = draw nothing, the window keeps the last picture (the old mode, complete).
     * The first frame of a landed switch is drawn once its panels are in (PLAYER_INFO after the landing), after HOLD_MAX_MS
     * at the latest, or at once when the game was left; right before it the canvas takes the new size, so no frame shows a
     * wiped (black) window or the bare gameframe.
     */
    public static boolean mayPresent() {
        if (!held || host == null) {
            return true;
        }
        long now = host.now();
        boolean timedOut = now - heldSince > HOLD_MAX_MS;
        if (!tickEnded && !timedOut && host.inGame()) {
            return false;
        }
        held = false;
        // A2 round 7 part 2 (nakijker A2 round 7 FOUTEN 1-2): the frame is drawn now, but it is on screen only after its flip - the
        // window part (canvas size, frame fit; a resize wipes an OpenGL window) waits for a frame drawn offscreen at the new size
        // (beforeFlip), and the press boundary is the flip itself (framePresented), not this moment.
        presenting = true;
        presentingSince = now;
        offscreen = host.prepareCanvas();
        if (!offscreen) {
            host.presentCanvas();
        }
        if (timedOut && !tickEnded) {
            host.log("[78] PROBLEM window mode " + host.appliedMode() + ": no end of the server tick in " + HOLD_MAX_MS + " ms after its gameframe - shown anyway");
        } else {
            host.log("[78] window mode " + host.appliedMode() + " drawn with its panels (" + (now - heldSince) + " ms after its gameframe)"
                + (offscreen ? " - offscreen, the window keeps the old picture until it is drawn" : ""));
        }
        return true;
    }

    /** From the end of the hold until the first frame of the new mode is flipped (it is being drawn). */
    private static boolean presenting;
    private static long presentingSince;
    /** That frame is drawn offscreen: the window part waits for its flip. */
    private static boolean offscreen;

    /** True while the first frame of a landed switch is drawn and not yet flipped (the window still shows the old picture). */
    public static boolean presenting() {
        return presenting;
    }

    /**
     * The first frame of a landed switch is drawn offscreen right now, laid out for the window it is about to have: the client's
     * resize loop must not lay it out again for the current window (that would resize the canvas before the frame is drawn).
     */
    public static boolean drawingOffscreen() {
        return presenting && offscreen;
    }

    /**
     * client.mainredraw, after the frame is drawn and right before its flip: the window part of a landed switch whose first frame
     * was drawn offscreen - the window and canvas take the new size only now, and the flip shows the finished frame at once
     * (nakijker A2 round 7 FOUTEN 1: the resize ran before a 1468 ms first draw, a black maximised window for that long).
     */
    public static void beforeFlip() {
        if (presenting && offscreen) {
            offscreen = false;
            host.presentCanvas();
        }
    }

    /** client.mainredraw, after the flip: the new mode's picture is on screen - from now on presses are made on it. */
    public static void framePresented() {
        if (!presenting || host == null) {
            return;
        }
        presenting = false;
        offscreen = false;
        presentedAt = host.now();
        host.presented();
        host.log("[78] window mode " + host.appliedMode() + " on screen (" + (presentedAt - presentingSince) + " ms of drawing)");
    }

    /**
     * A mouse press taken by the game logic: true = drop it. It was made on the OLD mode's picture (before the new mode was on
     * screen) but the logic now has the new layout, so it would hit whatever the new layout has there (Keurmeester round 6
     * OPEN 2: a tab click made 60 ms after FIXED opened Adventurers Log Settings in the fixed frame). Clicks before the landing
     * still reach the old layout (it is the one drawn); clicks after the new picture is shown reach the new one. "Shown" = its
     * flip is done (framePresented): a press made while that frame was still being drawn was made on the old picture (nakijker
     * A2 round 7 FOUTEN 2).
     */
    public static boolean dropPress(long pressTime) {
        return held || presenting || pressTime < presentedAt;
    }

    /** MainLogicManager after the packet loop: false = drop the presses taken this cycle (a switch landed and is not shown yet). */
    public static boolean keepTakenPresses() {
        return !held && !presenting;
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
        if (presenting && !host.inGame()) {
            // Left the game between the end of the hold and the flip: nothing waits for that frame any more.
            beforeFlip();
            framePresented();
        }
        if (awaitedMode == -1 && !held && !presenting && queuedMode != -1) {
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
        held = false;
        presenting = false;
        offscreen = false;
        tickEnded = false;
        deferringCanvas = false;
        presentedAt = Long.MIN_VALUE;
        landedThisCycle = false;
    }
}
