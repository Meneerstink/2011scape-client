import java.util.ArrayList;
import java.util.List;

/**
 * A2 round 6 (ROOT_CAUSES class 206, Keurmeester A2 round 5 FAIL): no frame may draw a window mode with the other mode's
 * gameframe - fixed (1) with the resizable gameframe 746, resizable (2) / fullscreen (3) with the fixed gameframe 548 - at any
 * window-mode switch the player makes in game.
 *
 * Runs the real GameframeSwitch with a recording host (what InterfaceManager.ClientHost does: apply = changeWindowMode, the
 * WINDOW_STATUS it sends, the resize loop's relayout) and a server that answers like ours (set_window_status_logic ->
 * toggleDisplayInterface: on the next server tick, only when the reported mode needs the other gameframe, IF_OPENTOP of that
 * gameframe; then the client's packet loop calls GameframeSwitch.gameframeOpened before it sets the top level). Each frame
 * of 20 ms: client tick, packets due, the drawn frame is checked. Whole class: every start mode x every target x 30 server
 * tick phases x 7 latencies (0 .. 3.5 s, the Keurmeester's slowest) - plus double clicks, back-and-forth, three choices in a
 * row, a window resize / Stretched relayout while waiting, a logout while waiting, other top levels (world map, store) while
 * waiting, the fullscreen confirm running out, and a server that never answers.
 */
public final class GameframeSwitchRegressionTest {

    private static final int FRAME_MS = 20;
    private static final int SERVER_TICK_FRAMES = 30; // 600 ms
    private static final int[] LATENCIES = {0, 1, 5, 25, 60, 120, 175};
    private static final int FIXED = 1, RESIZABLE = 2, FULLSCREEN = 3;
    private static final int PANES = 14; // IF_OPENSUBs of a gameframe (chatbox, tabs, orbs, minimap ...)

    private static int checks;
    private static final List<String> failures = new ArrayList<>();

    /** The client side: what is drawn, what it sends. */
    static final class Sim implements GameframeSwitch.Host {
        int frame;
        int applied;
        int top;
        boolean inGame = true;
        int serverClassTop; // the gameframe the server has open
        boolean serverAnswers = true;
        final int phase;
        final int latency;
        final List<int[]> pending = new ArrayList<>(); // {deliverFrame, topId}
        final List<String> log = new ArrayList<>();
        int windowStatusSent;
        int modeChanges;
        String name;
        int mismatchedFrames;
        String firstMismatch;
        int gameframesSent; // IF_OPENTOP of a gameframe the server sent
        int serverMode = -1, serverWidth = -1, serverHeight = -1; // the last WINDOW_STATUS the server got (mode + canvas size)
        // A2 round 7: the window itself. canvasMode = the mode whose canvas size the AWT canvas has; with Stretched Mode OFF a resize
        // of that canvas WIPES the OpenGL window (black) until the next drawn frame; with Stretched ON the canvas is the window in
        // both modes. The screen = the last drawn frame (what PrintWindow / the player sees). Panels = the IF_OPENSUBs the server
        // sends behind a gameframe in the same tick, followed by that tick's PLAYER_INFO.
        boolean stretched;
        int canvasMode;
        boolean wiped;
        int panesOpen = PANES;
        int wipedFrames, bareFrames, wrongCanvasFrames;
        String firstWiped, firstBare, firstWrongCanvas;
        int screenMode; // the mode of the picture on screen
        int splitFrames; // the panels + PLAYER_INFO of a landing tick arrive this many frames after its IF_OPENTOP (split reads)
        int fillers; // other packets of the same tick in front of the gameframe (the client decodes 100 a cycle)
        int freezeFrames; // the client logic freezes this long in the cycle the gameframe lands (its scripts; Keurmeester 474-998 ms)
        int frozenUntil;
        long seq;
        final List<long[]> inbound = new ArrayList<>(); // {deliverFrame, seq, kind, id}: kind 0 IF_OPENTOP, 1 PLAYER_INFO, 2 IF_OPENSUB, 3 other
        final List<long[]> presses = new ArrayList<>(); // {madeAtMs, processFrame, screenModeWhenMade}
        int wrongLayoutClicks, droppedClicks, clicksActed;
        String firstWrongClick;

        Sim(String name, int start, int phase, int latency) {
            this.name = name;
            this.applied = start;
            this.top = GameframeSwitch.gameframeFor(start);
            this.serverClassTop = top;
            this.phase = phase;
            this.latency = latency;
            this.canvasMode = start;
            this.screenMode = start;
        }

        boolean canvasDiffers(int a, int b) {
            return !stretched && (canvasWidth(a) != canvasWidth(b) || canvasHeight(a) != canvasHeight(b));
        }

        // --- GameframeSwitch.Host ---
        @Override public int appliedMode() { return applied; }
        @Override public boolean inGame() { return inGame; }
        @Override public void apply(int mode, int width, int height, boolean sendStatus) {
            if (mode != applied) {
                modeChanges++;
            }
            applied = mode;
            // InterfaceManager.windowModeChanged: the AWT canvas takes the new size now - unless a landed switch defers it to the
            // first frame of the new mode (GameframeSwitch.deferringCanvas -> applyPendingCanvas from mayPresent).
            if (!GameframeSwitch.deferringCanvas()) {
                if (canvasDiffers(canvasMode, mode)) {
                    wiped = true;
                }
                canvasMode = mode;
            }
            // The real InterfaceManager.getWindowMode() (ScriptRunner GETWINDOWMODE) follows the drawn mode (no fullscreen frame here).
            InterfaceManager.resizableScreen = mode >= RESIZABLE;
            // InterfaceManager.windowModeChanged ends with sendWindowStatus() at the game screen (unless already sent).
            if (sendStatus && inGame) {
                server(GameframeSwitch.reportedMode(applied));
            }
        }
        @Override public void sendWindowStatus(int mode) { server(mode); }
        @Override public void presentCanvas() {
            if (canvasDiffers(canvasMode, applied)) {
                wiped = true; // wiped and drawn in the same frame (mayPresent runs right before the frame is drawn)
            }
            canvasMode = applied;
        }
        @Override public long now() { return (long) frame * FRAME_MS; }
        @Override public void log(String line) { log.add(frame + ": " + line); }

        /** The resize loop / a Stretched Mode relayout: changeWindowMode(getWindowMode()) - never through GameframeSwitch.request. */
        void relayout() {
            apply(applied, -1, -1, true);
        }

        /** WINDOW_STATUS reaches the server; it answers on its next tick (+ latency) only when the gameframe changes. */
        void server(int reportedMode) {
            windowStatusSent++;
            // ServerConnectionReader.sendWindowStatus writes the canvas size of the moment it is sent.
            serverMode = reportedMode;
            serverWidth = canvasWidth(applied);
            serverHeight = canvasHeight(applied);
            if (!serverAnswers) {
                return;
            }
            int wanted = GameframeSwitch.gameframeFor(reportedMode);
            int tick = frame + 1;
            while ((tick - phase) % SERVER_TICK_FRAMES != 0) {
                tick++;
            }
            // The server compares with ITS current gameframe at the tick it processes this status (statuses in order).
            pending.add(new int[] {tick, wanted, -1});
        }

        void serverTop(int id, int delayFrames) {
            pending.add(new int[] {frame + delayFrames, id, 0});
        }

        void deliver(long frameAt, int kind, int id) {
            inbound.add(new long[] {frameAt, seq++, kind, id});
        }

        /** One client cycle: tick, the server's ticks due, presses, the packet loop (100 a cycle like MainLogicManager), the frame. */
        void step() {
            frame++;
            GameframeSwitch.tick();
            // Server ticks due: process statuses (server side) - a new gameframe goes out with its panels, and every server tick
            // ends with PLAYER_INFO (rsmod: MessageHandlerTask -> ... -> SequentialSynchronizationTask, one flush).
            boolean landingTick = false;
            for (int[] p : new ArrayList<>(pending)) {
                if (p[2] == -1 && p[0] <= frame) {
                    pending.remove(p);
                    if (p[1] != serverClassTop) {
                        serverClassTop = p[1];
                        gameframesSent++;
                        landingTick = true;
                        int at = p[0] + latency;
                        for (int i = 0; i < fillers; i++) {
                            deliver(at, 3, 0);
                        }
                        deliver(at, 0, p[1]);
                        for (int i = 0; i < PANES; i++) {
                            deliver(at + splitFrames, 2, p[1]);
                        }
                        deliver(at + splitFrames, 1, 0);
                    }
                }
            }
            if ((frame - phase) % SERVER_TICK_FRAMES == 0 && inGame && !landingTick) {
                deliver(frame + latency, 1, 0);
            }
            for (int[] p : new ArrayList<>(pending)) {
                if (p[2] == 0 && p[0] <= frame) {
                    pending.remove(p);
                    deliver(p[0], 0, p[1]);
                    for (int i = 0; i < PANES; i++) {
                        deliver(p[0], 2, p[1]);
                    }
                }
            }
            if (frame < frozenUntil) {
                capture();
                return; // the client is stuck in the landing cycle's scripts: nothing read, nothing drawn
            }
            // Presses the logic takes this cycle (client.mainloop mouse loop: GameframeSwitch.dropPress), before the packets.
            List<long[]> taken = new ArrayList<>();
            for (long[] pr : new ArrayList<>(presses)) {
                if (pr[1] <= frame) {
                    presses.remove(pr);
                    if (GameframeSwitch.dropPress(pr[0])) {
                        droppedClicks++;
                    } else {
                        taken.add(pr);
                    }
                }
            }
            // MainLogicManager packet loop: 100 packets a cycle, more in the cycle an awaited gameframe lands.
            inbound.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
            boolean landed = false;
            for (int i = 0; i < 100 || GameframeSwitch.landedThisCycle(); i++) {
                if (inbound.isEmpty() || inbound.get(0)[0] > frame) {
                    break;
                }
                long[] q = inbound.remove(0);
                if (q[2] == 0) {
                    int id = (int) q[3];
                    int before = applied;
                    boolean awaited = GameframeSwitch.awaitedMode() != -1 && GameframeSwitch.gameframeFor(GameframeSwitch.awaitedMode()) == id;
                    // ServerConnectionReader IF_OPENTOP
                    GameframeSwitch.gameframeOpened(id);
                    top = id;
                    panesOpen = (id == GameframeSwitch.FIXED_GAMEFRAME || id == GameframeSwitch.RESIZABLE_GAMEFRAME) ? 0 : PANES;
                    if (awaited) {
                        landed = true;
                        checks++;
                        if (!GameframeSwitch.landedThisCycle() || applied == before && GameframeSwitch.gameframeFor(before) != id) {
                            failures.add(name + ": frame " + frame + " the awaited gameframe " + id + " landed without the same-cycle batch flag");
                        }
                    }
                } else if (q[2] == 2) {
                    if (top == q[3]) {
                        panesOpen++;
                    }
                } else if (q[2] == 1) {
                    // ServerConnectionReader PLAYER_INFO
                    GameframeSwitch.serverTickEnded();
                }
            }
            GameframeSwitch.endPacketCycle();
            // MainLogicManager after the packet loop: presses taken before a landing were made on the old picture.
            if (!GameframeSwitch.keepTakenPresses()) {
                droppedClicks += taken.size();
                taken.clear();
            }
            // The presses act on the layout the logic has now; the player made them on the picture that was on screen.
            for (long[] pr : taken) {
                checks++;
                clicksActed++;
                if (pr[2] != applied) {
                    wrongLayoutClicks++;
                    if (firstWrongClick == null) {
                        firstWrongClick = "a press made at " + pr[0] + " ms on the mode-" + pr[2] + " picture acted on the mode-" + applied + " layout at frame " + frame;
                    }
                }
            }
            if (landed && freezeFrames > 0) {
                frozenUntil = frame + freezeFrames;
                capture();
                return;
            }
            // The frame (client.mainredraw): only when GameframeSwitch lets it.
            if (GameframeSwitch.mayPresent()) {
                draw();
            }
            capture();
        }

        void draw() {
            wiped = false;
            screenMode = applied;
            if (!inGame || !(top == GameframeSwitch.FIXED_GAMEFRAME || top == GameframeSwitch.RESIZABLE_GAMEFRAME)) {
                return;
            }
            checks += 3;
            if (GameframeSwitch.gameframeFor(applied) != top) {
                mismatchedFrames++;
                if (firstMismatch == null) {
                    firstMismatch = "frame " + frame + " (" + (frame * FRAME_MS) + " ms): mode=" + applied + " drawn with top=" + top;
                }
            }
            if (canvasDiffers(canvasMode, applied)) {
                wrongCanvasFrames++;
                if (firstWrongCanvas == null) {
                    firstWrongCanvas = "frame " + frame + ": mode " + applied + " drawn in the mode-" + canvasMode + " canvas";
                }
            }
            if (panesOpen < PANES) {
                bareFrames++;
                if (firstBare == null) {
                    firstBare = "frame " + frame + " (" + (frame * FRAME_MS) + " ms): gameframe " + top + " drawn with " + panesOpen + " of " + PANES + " panels";
                }
            }
        }

        /** What a screenshot of the window shows at the end of this frame (the Keurmeester's PrintWindow loop). */
        void capture() {
            if (!inGame) {
                return;
            }
            checks++;
            if (wiped) {
                wipedFrames++;
                if (firstWiped == null) {
                    firstWiped = "frame " + frame + " (" + (frame * FRAME_MS) + " ms): the window is black (canvas resized to mode " + canvasMode + ", nothing drawn in it yet)";
                }
            }
        }

        /** The player presses the mouse now (on the picture on screen); the logic takes the press [afterFrames] frames later. */
        void press(int afterFrames) {
            presses.add(new long[] {now(), frame + afterFrames, screenMode});
        }

        void run(int frames) {
            for (int i = 0; i < frames; i++) {
                step();
            }
        }

        void settle() {
            run(SERVER_TICK_FRAMES * 2 + latency + 10 + (int) (GameframeSwitch.TIMEOUT_MS / FRAME_MS));
        }
    }

    private static Sim start(String name, int mode, int phase, int latency) {
        Sim sim = new Sim(name, mode, phase, latency);
        GameframeSwitch.reset(sim);
        InterfaceManager.resizableScreen = mode >= RESIZABLE;
        return sim;
    }

    private static void expect(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures.add(what);
        }
    }

    private static void expectClean(Sim sim, int finalMode) {
        expect(sim.wipedFrames == 0, sim.name + ": " + sim.wipedFrames + " screenshots show a BLACK window, first at " + sim.firstWiped);
        expect(sim.bareFrames == 0, sim.name + ": " + sim.bareFrames + " frames draw the gameframe without its panels, first at " + sim.firstBare);
        expect(sim.wrongCanvasFrames == 0, sim.name + ": " + sim.wrongCanvasFrames + " frames draw a mode in the other mode's canvas, first at " + sim.firstWrongCanvas);
        expect(sim.wrongLayoutClicks == 0, sim.name + ": " + sim.wrongLayoutClicks + " presses acted on another layout than the picture they were made on - " + sim.firstWrongClick);
        expect(!GameframeSwitch.held(), sim.name + ": the switch is still held (never shown)");
        expect(sim.screenMode == finalMode, sim.name + ": the screen ends showing mode " + sim.screenMode + ", the player chose " + finalMode);
        expect(sim.mismatchedFrames == 0, sim.name + ": " + sim.mismatchedFrames + " frames draw a mode with the other mode's gameframe, first at " + sim.firstMismatch);
        expect(sim.applied == finalMode, sim.name + ": ends in mode " + sim.applied + ", the player chose " + finalMode);
        expect(sim.top == GameframeSwitch.gameframeFor(finalMode), sim.name + ": ends with gameframe " + sim.top + " for mode " + finalMode);
        expect(sim.serverClassTop == GameframeSwitch.gameframeFor(finalMode), sim.name + ": server ends with gameframe " + sim.serverClassTop);
        expect(GameframeSwitch.awaitedMode() == -1, sim.name + ": still waiting for mode " + GameframeSwitch.awaitedMode());
        // Nakijker A2 round 6 (class 208) point 3: the server must end with the drawn mode AND its canvas size - the status sent at the
        // choice carries the OLD canvas, so the client reports the new size once the new mode is drawn.
        expect(sim.serverMode == finalMode && sim.serverWidth == canvasWidth(finalMode) && sim.serverHeight == canvasHeight(finalMode),
            sim.name + ": server's last WINDOW_STATUS = mode " + sim.serverMode + " " + sim.serverWidth + "x" + sim.serverHeight
                + ", the client draws mode " + finalMode + " " + canvasWidth(finalMode) + "x" + canvasHeight(finalMode));
    }

    /** Canvas size per mode (the owner's 944x581 window; fixed 765x503 canvas; fullscreen 1920x1080). */
    static int canvasWidth(int mode) {
        return mode == FIXED ? 765 : mode == RESIZABLE ? 944 : 1920;
    }

    static int canvasHeight(int mode) {
        return mode == FIXED ? 503 : mode == RESIZABLE ? 581 : 1080;
    }

    private static String mode(int m) {
        return m == FIXED ? "FIXED" : m == RESIZABLE ? "RESIZABLE" : "FULLSCREEN";
    }

    private static int fsWidth(int m) {
        return m == FULLSCREEN ? 1920 : -1;
    }

    private static final int PUSH_CONSTANT_INT = 0, RETURN = 21, GETWINDOWMODE = 5306, SETWINDOWMODE = 5307;

    /** Runs a script of [opcode, operand] pairs through the real ScriptRunner.executeScript; returns the int it left on the stack. */
    private static int runScript(int id, int... opsAndOperands) {
        ClientScript script = new ClientScript();
        script.id = id;
        script.opcodes = new int[opsAndOperands.length / 2];
        script.intOperands = new int[opsAndOperands.length / 2];
        script.stringOperands = new String[opsAndOperands.length / 2];
        for (int i = 0; i < script.opcodes.length; i++) {
            script.opcodes[i] = opsAndOperands[i * 2];
            script.intOperands[i] = opsAndOperands[i * 2 + 1];
        }
        ScriptRunner.executeScript(script, 200000);
        return ScriptRunner.intStackPointer == 1 ? ScriptRunner.intStack[0] : -100 - ScriptRunner.intStackPointer;
    }

    private static final int PUSH_INT_LOCAL = 33, BRANCH = 6, BRANCH_EQUALS = 8;

    /**
     * The Graphics Options button script 2698 (arg0 = the mode), its first 14 ops as in the 667 cache (tools\cache667\cs2dis.py
     * 2698): "iload 0; getwindowmode; if_icmpeq -> return; iload 0; setwindowmode; getwindowmode" (then istore 5 + the "unable to
     * enter" check). Returns the mode it read after SETWINDOWMODE, or -100 when it returned early ("already in that mode").
     */
    private static int graphicsOptions(int mode) {
        ScriptRunner.intVars = new int[] {mode, 0, 0, 0, 0, 0};
        return runScript(2698,
            PUSH_INT_LOCAL, 0, GETWINDOWMODE, 0, BRANCH_EQUALS, 1, BRANCH, 1, RETURN, 0,
            PUSH_INT_LOCAL, 0, SETWINDOWMODE, 0, GETWINDOWMODE, 0, RETURN, 0);
    }

    public static void main(String[] args) {
        int[] modes = {FIXED, RESIZABLE, FULLSCREEN};
        int cases = 0;

        // 1. One switch: every start x every target x every server tick phase x every latency.
        for (int from : modes) {
            for (int to : modes) {
                if (from == to) continue;
                for (int phase = 0; phase < SERVER_TICK_FRAMES; phase++) {
                    for (int latency : LATENCIES) {
                        Sim sim = start(mode(from) + " -> " + mode(to) + " phase " + phase + " latency " + (latency * FRAME_MS) + " ms", from, phase, latency);
                        sim.run(3);
                        GameframeSwitch.request(to, fsWidth(to), fsWidth(to));
                        sim.settle();
                        expectClean(sim, to);
                        expect(sim.modeChanges == 1, sim.name + ": the drawn mode changed " + sim.modeChanges + " times");
                        boolean newGameframe = GameframeSwitch.gameframeFor(from) != GameframeSwitch.gameframeFor(to);
                        expect(sim.gameframesSent == (newGameframe ? 1 : 0), sim.name + ": the server opened " + sim.gameframesSent + " gameframes");
                        expect(sim.windowStatusSent <= 2, sim.name + ": " + sim.windowStatusSent + " WINDOW_STATUS sent (the choice + the new size)");
                        cases++;
                    }
                }
            }
        }

        // 2. Several choices: double click, back again, three in a row - at every gap.
        int[][] sequences = {
            {FIXED, RESIZABLE, RESIZABLE}, {FIXED, RESIZABLE, FIXED}, {RESIZABLE, FIXED, RESIZABLE}, {RESIZABLE, FIXED, FIXED},
            {FIXED, RESIZABLE, FIXED, RESIZABLE}, {RESIZABLE, FIXED, RESIZABLE, FIXED}, {FIXED, FULLSCREEN, FIXED},
            {FIXED, FULLSCREEN, RESIZABLE}, {RESIZABLE, FIXED, FULLSCREEN}, {FULLSCREEN, FIXED, RESIZABLE}, {FULLSCREEN, FIXED, FULLSCREEN},
        };
        int[] gaps = {1, 3, 10, 29, 31, 45, 90};
        for (int[] seq : sequences) {
            for (int gap : gaps) {
                for (int phase = 0; phase < SERVER_TICK_FRAMES; phase += 7) {
                    for (int latency : new int[] {0, 5, 60, 175}) {
                        StringBuilder name = new StringBuilder();
                        for (int m : seq) name.append(name.length() == 0 ? "" : " -> ").append(mode(m));
                        Sim sim = start(name + " every " + (gap * FRAME_MS) + " ms phase " + phase + " latency " + (latency * FRAME_MS) + " ms", seq[0], phase, latency);
                        sim.run(2);
                        for (int i = 1; i < seq.length; i++) {
                            GameframeSwitch.request(seq[i], fsWidth(seq[i]), fsWidth(seq[i]));
                            sim.run(gap);
                        }
                        sim.settle();
                        expectClean(sim, seq[seq.length - 1]);
                        cases++;
                    }
                }
            }
        }

        // 3. A window resize / Stretched relayout while the switch waits (the resize loop sends WINDOW_STATUS too).
        for (int from : modes) {
            for (int to : modes) {
                if (from == to) continue;
                for (int at : new int[] {1, 5, 20, 40}) {
                    for (int latency : new int[] {0, 25, 120}) {
                        Sim sim = start(mode(from) + " -> " + mode(to) + " with a relayout " + (at * FRAME_MS) + " ms later, latency " + (latency * FRAME_MS), from, 11, latency);
                        GameframeSwitch.request(to, fsWidth(to), fsWidth(to));
                        sim.run(at);
                        sim.relayout();
                        sim.run(3);
                        sim.relayout();
                        sim.settle();
                        expectClean(sim, to);
                        cases++;
                    }
                }
            }
        }

        // 4. Logout while waiting: the login screen gets the chosen mode at once.
        for (int from : modes) {
            for (int to : modes) {
                if (from == to) continue;
                Sim sim = start(mode(from) + " -> " + mode(to) + " then Exit to Login before the gameframe", from, 0, 120);
                GameframeSwitch.request(to, fsWidth(to), fsWidth(to));
                sim.run(2);
                sim.inGame = false;
                sim.pending.clear();
                sim.run(2);
                expect(sim.applied == to, sim.name + ": login screen in mode " + sim.applied + ", chose " + to);
                expect(GameframeSwitch.awaitedMode() == -1, sim.name + ": still waiting after the logout");
                cases++;
            }
        }

        // 5. Other top levels while waiting (world map 755, a fullscreen store) and the server re-sending the OLD gameframe
        //    (closeFullscreenInterface before it processed the status): no switch on those.
        for (int from : modes) {
            for (int to : modes) {
                if (from == to || GameframeSwitch.gameframeFor(from) == GameframeSwitch.gameframeFor(to)) continue;
                Sim sim = start(mode(from) + " -> " + mode(to) + " with world map / store / old gameframe tops in between", from, 29, 60);
                GameframeSwitch.request(to, fsWidth(to), fsWidth(to));
                sim.serverTop(755, 1);
                sim.serverTop(GameframeSwitch.gameframeFor(from), 3);
                sim.run(5);
                expect(sim.applied == from, sim.name + ": switched on a top level that is not its gameframe (mode " + sim.applied + ")");
                sim.settle();
                expectClean(sim, to);
                cases++;
            }
        }

        // 6. A server that never answers: switch after the timeout, logged as a PROBLEM.
        {
            Sim sim = start("FIXED -> RESIZABLE, server never answers", FIXED, 0, 0);
            sim.serverAnswers = false;
            GameframeSwitch.request(RESIZABLE, -1, -1);
            sim.run((int) (GameframeSwitch.TIMEOUT_MS / FRAME_MS) - 5);
            expect(sim.applied == FIXED, sim.name + ": switched before the timeout");
            sim.run(20);
            expect(sim.applied == RESIZABLE, sim.name + ": never switched after the timeout");
            boolean logged = false;
            for (String line : sim.log) logged |= line.contains("PROBLEM");
            expect(logged, sim.name + ": no PROBLEM line");
            cases++;
        }

        // 7. Not in game (login screen wrench) and inside one gameframe (resizable <-> fullscreen): at once, as before.
        for (int from : modes) {
            for (int to : modes) {
                if (from == to) continue;
                Sim sim = start(mode(from) + " -> " + mode(to) + " on the login screen", from, 0, 0);
                sim.inGame = false;
                GameframeSwitch.request(to, fsWidth(to), fsWidth(to));
                expect(sim.applied == to, sim.name + ": not switched at once (" + sim.applied + ")");
                cases++;
            }
        }
        for (int[] pair : new int[][] {{RESIZABLE, FULLSCREEN}, {FULLSCREEN, RESIZABLE}}) {
            Sim sim = start(mode(pair[0]) + " -> " + mode(pair[1]) + " in game (same gameframe 746)", pair[0], 0, 60);
            GameframeSwitch.request(pair[1], fsWidth(pair[1]), fsWidth(pair[1]));
            expect(sim.applied == pair[1], sim.name + ": not switched at once (" + sim.applied + ")");
            sim.settle();
            expectClean(sim, pair[1]);
            cases++;
        }

        // 8. CS2 GETWINDOWMODE while a switch waits (nakijker A2 round 6 / ROOT_CAUSES class 208), through the REAL ScriptRunner:
        //    a. the Graphics Options script 2698 does SETWINDOWMODE then GETWINDOWMODE in the same run (l.11-14: iload 0,
        //       setwindowmode, getwindowmode, istore 5, then compares) - it must read the mode it chose, else it prints
        //       "RuneScape was unable to enter that display mode";
        //    b. every OTHER script that runs while the old mode is still drawn (gameframe layout scripts 8/103/2729 here; also the
        //       chatbox look scripts 84/1301/1362/1558 - 99 scripts read GETWINDOWMODE) must read the DRAWN mode, or it lays out the new mode inside the old
        //       gameframe (the wrong-frame class this round removes);
        //    c. after the gameframe landed every script reads the new mode; on the login screen the switch is at once.
        for (int from : new int[] {FIXED, RESIZABLE}) {
            int to = from == FIXED ? RESIZABLE : FIXED;
            for (int latency : new int[] {0, 5, 60, 175}) {
                for (boolean inGame : new boolean[] {true, false}) {
                    String name = mode(from) + " -> " + mode(to) + " via Graphics Options (CS2 SETWINDOWMODE) latency " + (latency * FRAME_MS) + " ms" + (inGame ? "" : " on the login screen");
                    Sim sim = start(name, from, 13, latency);
                    sim.inGame = inGame;
                    int chosen = runScript(2698, PUSH_CONSTANT_INT, to, SETWINDOWMODE, 0, GETWINDOWMODE, 0, RETURN, 0);
                    expect(chosen == to, name + ": the script that chose mode " + to + " reads GETWINDOWMODE " + chosen + " right after SETWINDOWMODE (\"unable to enter that display mode\")");
                    int other = runScript(103, GETWINDOWMODE, 0, RETURN, 0);
                    int drawn = inGame ? from : to;
                    expect(other == drawn, name + ": another script run while mode " + sim.applied + " is drawn reads GETWINDOWMODE " + other + " (must be the drawn mode " + drawn + ")");
                    sim.run(3);
                    other = runScript(8, GETWINDOWMODE, 0, RETURN, 0);
                    expect(other == sim.applied, name + ": a gameframe layout script 3 frames later reads " + other + ", drawn " + sim.applied);
                    if (inGame) {
                        sim.settle();
                        expectClean(sim, to);
                    }
                    other = runScript(2729, GETWINDOWMODE, 0, RETURN, 0);
                    expect(other == to, name + ": after the gameframe landed a script reads " + other + ", drawn " + to);
                    cases++;
                }
            }
        }

        // 9. Graphics Options clicked AGAIN while a switch waits (class 208 follow-up). Script 2698 starts with "if GETWINDOWMODE ==
        //    arg0 return" (l.0-4) in a NEW run, before it chooses. A script that chooses a window mode (it holds SETWINDOWMODE /
        //    FULLSCREEN_ENTER / FULLSCREEN_EXIT: 2582, 2698, 2699, 2705, 2710) must read the mode the player chose last there - with
        //    the drawn mode the click back to the still-drawn mode returns early, the switch in flight runs on and the client ends
        //    in the mode the player left. Click back / click the same button again, at 3 gaps x 3 latencies; a gameframe layout
        //    script in the same wait still reads the drawn mode.
        for (int from : new int[] {FIXED, RESIZABLE}) {
            int to = from == FIXED ? RESIZABLE : FIXED;
            for (int latency : new int[] {5, 60, 175}) {
                for (int gap : new int[] {1, 3, 10}) {
                    for (boolean back : new boolean[] {true, false}) {
                        int second = back ? from : to;
                        String name = mode(from) + " -> " + mode(to) + " -> " + mode(second) + " via Graphics Options clicked " + (gap * FRAME_MS)
                            + " ms apart, latency " + (latency * FRAME_MS) + " ms";
                        Sim sim = start(name, from, 13, latency);
                        int first = graphicsOptions(to);
                        expect(first == to, name + ": the first click reads GETWINDOWMODE " + first + " after SETWINDOWMODE " + to);
                        sim.run(gap);
                        boolean waiting = GameframeSwitch.awaitedMode() != -1;
                        int layout = runScript(103, GETWINDOWMODE, 0, RETURN, 0);
                        expect(layout == sim.applied, name + ": a layout script during the wait reads " + layout + ", drawn " + sim.applied);
                        int again = graphicsOptions(second);
                        if (!back) {
                            expect(again == -100, name + ": the same button again (" + (waiting ? "its switch still waits" : "already drawn")
                                + ") must return early - it must read the chosen mode, got " + again);
                        } else {
                            expect(again == second, name + ": the second click (to " + mode(second) + ") was dropped - script 2698 read GETWINDOWMODE "
                                + "and returned early (" + again + ")");
                        }
                        sim.settle();
                        expectClean(sim, second);
                        expect(sim.applied == second, name + ": ends drawing mode " + sim.applied + ", the player's last choice was " + second);
                        cases++;
                    }
                }
            }
        }

        // 10. A2 round 7 (Keurmeester A2 round 6 FAIL + nakijker FOUTEN 1-2 + verdict OPEN 2): the WINDOW, not only mode == top.
        //     Stretched Mode OFF (the fixed canvas is 765x503, resizable 944x581, fullscreen 1920x1080: a canvas resize wipes the GL
        //     window) and ON (canvas = window), every direction between the two gameframes, the gameframe's panels split over later
        //     reads (0-10 frames), 150 other packets of the same tick in front of it (the 100-packet cycle cap), a client freeze of
        //     0-1 s in the landing cycle (the new gameframe's scripts; Keurmeester 474-998 ms), 3 latencies x 2 tick phases; the
        //     player clicks every other frame during the switch, the logic taking each press 0-25 frames later. Every screenshot must
        //     show a picture (never the black wiped window), every frame its mode in its own canvas with the complete gameframe,
        //     every press must act on the layout of the picture it was made on (or be dropped).
        int[][] pairs = {{RESIZABLE, FIXED}, {FIXED, RESIZABLE}, {FULLSCREEN, FIXED}, {FIXED, FULLSCREEN}};
        int[] delays = {0, 1, 3, 25};
        for (boolean stretchedOn : new boolean[] {false, true}) {
            for (int[] pair : pairs) {
                for (int split : new int[] {0, 1, 3, 10}) {
                    for (int freeze : new int[] {0, 12, 25, 50}) {
                        for (int fill : new int[] {0, 150}) {
                            for (int latency : new int[] {0, 5, 25}) {
                                for (int phase : new int[] {0, 13}) {
                                    Sim sim = start("Stretched " + (stretchedOn ? "ON" : "OFF") + " " + mode(pair[0]) + " -> " + mode(pair[1]) + " panels +" + split
                                        + " frames, freeze " + (freeze * FRAME_MS) + " ms, " + fill + " packets in front, phase " + phase + " latency " + (latency * FRAME_MS) + " ms", pair[0], phase, latency);
                                    sim.stretched = stretchedOn;
                                    sim.splitFrames = split;
                                    sim.freezeFrames = freeze;
                                    sim.fillers = fill;
                                    sim.run(3);
                                    GameframeSwitch.request(pair[1], fsWidth(pair[1]), fsWidth(pair[1]));
                                    for (int f = 0; f < 80; f++) {
                                        if (f % 2 == 0) {
                                            sim.press(delays[(f / 2) % delays.length]);
                                        }
                                        sim.step();
                                    }
                                    sim.settle();
                                    expectClean(sim, pair[1]);
                                    expect(sim.modeChanges == 1, sim.name + ": the drawn mode changed " + sim.modeChanges + " times");
                                    expect(sim.clicksActed > 0, sim.name + ": every press was dropped (" + sim.droppedClicks + ")");
                                    cases++;
                                }
                            }
                        }
                    }
                }
            }
        }
        // 10b. No switch: a press is never dropped (the new rule only touches presses made on a picture that is gone).
        for (int mode : modes) {
            for (int delay : delays) {
                Sim sim = start("no switch, " + mode(mode) + ", press taken " + delay + " frames later", mode, 4, 5);
                sim.run(40);
                sim.press(delay);
                sim.run(3);
                sim.press(delay);
                sim.settle();
                expect(sim.droppedClicks == 0 && sim.clicksActed == 2, sim.name + ": " + sim.droppedClicks + " presses dropped, " + sim.clicksActed + " acted (expected 0 / 2)");
                expect(sim.wipedFrames == 0 && sim.wrongLayoutClicks == 0 && sim.screenMode == mode && !GameframeSwitch.held(), sim.name + ": not a quiet steady state");
                cases++;
            }
        }
        // 10c. After a switch is on screen, presses on the new picture act (none dropped once it is shown).
        for (boolean stretchedOn : new boolean[] {false, true}) {
            for (int[] pair : pairs) {
                Sim sim = start("Stretched " + (stretchedOn ? "ON" : "OFF") + " " + mode(pair[0]) + " -> " + mode(pair[1]) + ", presses after it is shown", pair[0], 7, 5);
                sim.stretched = stretchedOn;
                sim.freezeFrames = 25;
                GameframeSwitch.request(pair[1], fsWidth(pair[1]), fsWidth(pair[1]));
                sim.settle();
                int before = sim.droppedClicks;
                for (int delay : delays) {
                    sim.press(delay);
                    sim.run(2);
                }
                sim.settle();
                expect(sim.droppedClicks == before, sim.name + ": " + (sim.droppedClicks - before) + " presses on the shown new picture were dropped");
                expectClean(sim, pair[1]);
                cases++;
            }
        }

        System.out.println("GameframeSwitchRegressionTest: " + cases + " switch cases, " + checks + " checks"
            + (failures.isEmpty() ? ", all passed" : ", " + failures.size() + " failed"));
        int shown = 0;
        java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
        for (String f : failures) {
            String kind = f.replaceAll(" phase \\d+", "").replaceAll("latency \\d+ ms", "latency").replaceAll("\\d+ ms", "N ms").replaceAll("\\d+ frames", "N frames").replaceAll("frame \\d+ \\(N ms\\)", "frame");
            kinds.merge(kind.length() > 160 ? kind.substring(0, 160) : kind, 1, Integer::sum);
            if (shown++ < 40) {
                System.out.println("FAIL " + f);
            }
        }
        if (!failures.isEmpty()) {
            System.out.println("Failure kinds:");
            for (java.util.Map.Entry<String, Integer> e : kinds.entrySet()) {
                System.out.println("  " + e.getValue() + "x " + e.getKey());
            }
            System.exit(1);
        }
    }
}
