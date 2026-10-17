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

        Sim(String name, int start, int phase, int latency) {
            this.name = name;
            this.applied = start;
            this.top = GameframeSwitch.gameframeFor(start);
            this.serverClassTop = top;
            this.phase = phase;
            this.latency = latency;
        }

        // --- GameframeSwitch.Host ---
        @Override public int appliedMode() { return applied; }
        @Override public boolean inGame() { return inGame; }
        @Override public void apply(int mode, int width, int height, boolean sendStatus) {
            if (mode != applied) {
                modeChanges++;
            }
            applied = mode;
            // InterfaceManager.windowModeChanged ends with sendWindowStatus() at the game screen (unless already sent).
            if (sendStatus && inGame) {
                server(GameframeSwitch.reportedMode(applied));
            }
        }
        @Override public void sendWindowStatus(int mode) { server(mode); }
        @Override public long now() { return (long) frame * FRAME_MS; }
        @Override public void log(String line) { log.add(frame + ": " + line); }

        /** The resize loop / a Stretched Mode relayout: changeWindowMode(getWindowMode()) - never through GameframeSwitch.request. */
        void relayout() {
            apply(applied, -1, -1, true);
        }

        /** WINDOW_STATUS reaches the server; it answers on its next tick (+ latency) only when the gameframe changes. */
        void server(int reportedMode) {
            windowStatusSent++;
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

        /** One client cycle. */
        void step() {
            frame++;
            GameframeSwitch.tick();
            // Server ticks due: process statuses (server side), then deliver packets due (client side) - in order.
            for (int[] p : new ArrayList<>(pending)) {
                if (p[2] == -1 && p[0] <= frame) {
                    pending.remove(p);
                    if (p[1] != serverClassTop) {
                        serverClassTop = p[1];
                        pending.add(new int[] {p[0] + latency, p[1], 0});
                    }
                }
            }
            for (int[] p : new ArrayList<>(pending)) {
                if (p[2] == 0 && p[0] <= frame) {
                    pending.remove(p);
                    // ServerConnectionReader IF_OPENTOP
                    int before = applied;
                    boolean awaited = GameframeSwitch.awaitedMode() != -1 && GameframeSwitch.gameframeFor(GameframeSwitch.awaitedMode()) == p[1];
                    GameframeSwitch.gameframeOpened(p[1]);
                    top = p[1];
                    if (awaited) {
                        // MainLogicManager keeps decoding this batch (the new gameframe's panels) in the same cycle.
                        checks++;
                        if (!GameframeSwitch.landedThisCycle() || applied == before && GameframeSwitch.gameframeFor(before) != p[1]) {
                            failures.add(name + ": frame " + frame + " the awaited gameframe " + p[1] + " landed without the same-cycle batch flag");
                        }
                    }
                }
            }
            GameframeSwitch.endPacketCycle();
            // The drawn frame.
            if (inGame && (top == GameframeSwitch.FIXED_GAMEFRAME || top == GameframeSwitch.RESIZABLE_GAMEFRAME)) {
                checks++;
                if (GameframeSwitch.gameframeFor(applied) != top) {
                    mismatchedFrames++;
                    if (firstMismatch == null) {
                        firstMismatch = "frame " + frame + " (" + (frame * FRAME_MS) + " ms): mode=" + applied + " drawn with top=" + top;
                    }
                }
            }
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
        return sim;
    }

    private static void expect(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures.add(what);
        }
    }

    private static void expectClean(Sim sim, int finalMode) {
        expect(sim.mismatchedFrames == 0, sim.name + ": " + sim.mismatchedFrames + " frames draw a mode with the other mode's gameframe, first at " + sim.firstMismatch);
        expect(sim.applied == finalMode, sim.name + ": ends in mode " + sim.applied + ", the player chose " + finalMode);
        expect(sim.top == GameframeSwitch.gameframeFor(finalMode), sim.name + ": ends with gameframe " + sim.top + " for mode " + finalMode);
        expect(sim.serverClassTop == GameframeSwitch.gameframeFor(finalMode), sim.name + ": server ends with gameframe " + sim.serverClassTop);
        expect(GameframeSwitch.awaitedMode() == -1, sim.name + ": still waiting for mode " + GameframeSwitch.awaitedMode());
    }

    private static String mode(int m) {
        return m == FIXED ? "FIXED" : m == RESIZABLE ? "RESIZABLE" : "FULLSCREEN";
    }

    private static int fsWidth(int m) {
        return m == FULLSCREEN ? 1920 : -1;
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
                        expect(sim.windowStatusSent == 1, sim.name + ": " + sim.windowStatusSent + " WINDOW_STATUS sent (one per switch)");
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
