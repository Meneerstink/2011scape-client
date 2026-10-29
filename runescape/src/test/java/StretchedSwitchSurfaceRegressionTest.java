import com.jagex.D3DPresent;
import com.jagex.StretchedMode;
import com.jagex.game.runetek6.client.GameShell;

import java.awt.Canvas;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A2 round 8 (Keurmeester A2 round 7 FAIL, ROOT_CAUSES 209/211): with Stretched Mode OFF an in-game fixed <-> resizable switch
 * showed a fully BLACK game area on the real screen (CopyFromScreen + PrintWindow). Measured on the Keurmeester's frames: the
 * black frames are EXACTLY the frames where the window or the AWT canvas changed size (944x581 window 1216x581 -> 1037x503 and
 * back; maximised 1920x989: the canvas 765x503 at 441,0 -> 1648x989). Windows shows a resized OpenGL surface black until the
 * next SwapBuffers, so every deferral of that resize (rounds 7 / 7b) only moved the black. With plain Stretched ON the canvas
 * and window keep their size in both modes, and that path was clean in every run.
 *
 * Donor: RuneLite ClientUI never sizes the frame on a mode change (applyGameSize only from the config; reference\runelite\
 * stretched\ClientUI.java l.1245-1262), and the canvas fills its parent; the fixed 765x503 game is drawn inside it.
 *
 * Invariant (whole class, OpenGL with framebuffer objects = the owner's renderer): for every window, Stretched OFF and ON, every
 * Keep aspect ratio / Integer scaling combination and several Resizable scaling values, the AWT canvas has the SAME size and
 * place in fixed and in resizable (= the window's game area), the client does not fit or restore the window at the switch
 * (StretchedMode.fillsWindow), and a click lands on the game pixel drawn under it. Other toolkits keep their old canvas sizes.
 */
public final class StretchedSwitchSurfaceRegressionTest {
    private static int assertions;
    private static final Map<String, Integer> kinds = new LinkedHashMap<String, Integer>();
    private static final List<String> examples = new ArrayList<String>();
    private static final java.io.PrintStream OUT = System.out;

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
            }
        }));
        GameShell.canvas = new Canvas();
        StretchedMode.toolkitChanged(true);

        List<int[]> windows = new ArrayList<int[]>();
        for (int w = 765; w <= 1920; w += 61) {
            for (int h = 503; h <= 1080; h += 47) {
                windows.add(new int[] {w, h});
            }
        }
        int[][] named = {{944, 581}, {1007, 581}, {1216, 581}, {765, 503}, {795, 503}, {1648, 989}, {1890, 989}, {1920, 989}, {1648, 1080}, {978, 709}};
        for (int[] w : named) {
            windows.add(w);
        }
        boolean[][] options = {{false, false}, {true, false}, {false, true}, {true, true}}; // keep aspect, integer
        int[] scalings = {0, 16, 50};
        int cases = 0;

        // 1. OpenGL with framebuffer objects: a switch never changes the canvas or the window.
        glToolkit(true);
        for (int[] w : windows) {
            for (boolean on : new boolean[] {false, true}) {
                for (boolean[] o : options) {
                    if (!on && (o[0] || o[1])) {
                        continue; // the options only act while stretching
                    }
                    for (int s : scalings) {
                        if (!on && s != 0) {
                            continue;
                        }
                        cases++;
                        String name = (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                            + (on ? " " + s + "%" : "") + " window " + w[0] + "x" + w[1];
                        Layout fixed = layout(w, true, on, o[0], o[1], s);
                        clicks(name + " fixed", fixed);
                        Layout resizable = layout(w, false, on, o[0], o[1], s);
                        clicks(name + " resizable", resizable);
                        check(fixed.canvasW == resizable.canvasW && fixed.canvasH == resizable.canvasH,
                            "canvas changes size at a fixed <-> resizable switch (black on the real screen until the next swap)",
                            name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " vs resizable " + resizable.canvasW + "x" + resizable.canvasH);
                        check(fixed.canvasX == resizable.canvasX && fixed.canvasY == resizable.canvasY,
                            "canvas moves at a fixed <-> resizable switch",
                            name + ": fixed canvas at " + fixed.canvasX + "," + fixed.canvasY + " vs resizable " + resizable.canvasX + "," + resizable.canvasY);
                        check(fixed.canvasW == w[0] && fixed.canvasH == w[1] && fixed.canvasX == 0 && fixed.canvasY == 0,
                            "the canvas does not fill the window's game area (RuneLite: the canvas fills its parent)",
                            name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " at " + fixed.canvasX + "," + fixed.canvasY);
                        check(fixed.fills && resizable.fills,
                            "the client fits / restores the WINDOW at a switch (InterfaceManager.changeWindowMode: RuneLite never sizes the frame)",
                            name + ": fillsWindow fixed=" + fixed.fills + " resizable=" + resizable.fills);
                    }
                }
            }
        }

        // 1b. The blit (StretchedMode.glPresent through the GL seam): a frame that is not 1:1 over the whole canvas is drawn offscreen
        //     at its real size and blitted exactly into its drawn rectangle (GL rows from the bottom), the margins cleared black.
        for (int[] w : new int[][] {{944, 581}, {1648, 989}, {1890, 989}, {765, 503}, {1216, 581}}) {
            for (boolean on : new boolean[] {false, true}) {
                for (boolean[] o : options) {
                    if (!on && (o[0] || o[1])) {
                        continue;
                    }
                    for (boolean fixedMode : new boolean[] {true, false}) {
                        cases++;
                        String name = "blit " + (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                            + (fixedMode ? " fixed" : " resizable") + " window " + w[0] + "x" + w[1];
                        RecordingGl gl = new RecordingGl();
                        StretchedMode.useGlForTest(gl);
                        Layout l = layout(w, fixedMode, on, o[0], o[1], 16);
                        java.awt.Dimension render = StretchedMode.renderSize(GameShell.canvas);
                        StretchedMode.glSync(true, render.width, render.height);
                        gl.events.clear();
                        StretchedMode.glPresent(false, l.canvasW, l.canvasH);
                        boolean oneToOne = l.drawX == 0 && l.drawY == 0 && l.realW == l.canvasW && l.realH == l.canvasH && l.drawnW == l.canvasW && l.drawnH == l.canvasH;
                        if (oneToOne) {
                            check(gl.blit == null, "a 1:1 frame over the whole canvas goes through the offscreen buffer", name);
                            continue;
                        }
                        check(render.width == l.realW && render.height == l.realH, "the toolkit does not render the blitted frame at its real size",
                            name + ": renders " + render.width + "x" + render.height + ", real " + l.realW + "x" + l.realH);
                        int[] want = {0, 0, l.realW, l.realH, l.drawX, l.canvasH - l.drawY - l.drawnH, l.drawX + l.drawnW, l.canvasH - l.drawY};
                        check(gl.blit != null && java.util.Arrays.equals(gl.blit, want), "the blit does not land on the drawn rectangle",
                            name + ": blit " + (gl.blit == null ? "none" : java.util.Arrays.toString(gl.blit)) + ", want " + java.util.Arrays.toString(want));
                        boolean margins = l.drawX > 0 || l.drawY > 0 || l.drawX + l.drawnW < l.canvasW || l.drawY + l.drawnH < l.canvasH;
                        if (margins) {
                            check(gl.clearedWindowBeforeBlit, "the black margins around the drawn game are not cleared before the blit", name);
                        }
                    }
                }
            }
        }
        StretchedMode.useGlForTest(new RecordingGl());

        // 1c. A2 round 8 review (nakijker FOUTEN 1): OpenGL with the player's Anti-aliasing on (x2 / x4 in Graphics Options). The
        //     window is made the way Static595.createWithHeap makes it (StretchedMode.glWindowSamples; the old code: plain only
        //     while stretching) and the toolkit is reported the way Static595 reports it. Whole class again: every window x
        //     Stretched OFF / ON x Keep aspect / Integer x scaling - the canvas never changes size or place at a switch and fills
        //     the window, the client never fits the window, clicks land; switching Stretched Mode on/off never needs another
        //     window (a toolkit rebuild = a new canvas); and the frame keeps the player's anti-aliasing: drawn into a multisampled
        //     offscreen buffer, resolved 1:1 (a multisampled buffer cannot be blitted scaled / moved or read with glReadPixels),
        //     then blitted onto its drawn rectangle.
        for (int aa : new int[] {2, 4}) {
            StretchedMode.configure(false, false, false, false, 0);
            int samplesOff = windowSamples(aa);
            StretchedMode.configure(true, false, false, false, 0);
            int samplesOn = windowSamples(aa);
            cases++;
            check(samplesOff == samplesOn, "AA: switching Stretched Mode on/off needs another OpenGL window (toolkit rebuild = new canvas)",
                "AA x" + aa + ": window samples Stretched OFF " + samplesOff + ", ON " + samplesOn);
            for (int[] w : windows) {
                for (boolean on : new boolean[] {false, true}) {
                    for (boolean[] o : options) {
                        if (!on && (o[0] || o[1])) {
                            continue;
                        }
                        for (int s : scalings) {
                            if (!on && s != 0) {
                                continue;
                            }
                            cases++;
                            String name = "AA x" + aa + " " + (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                                + (on ? " " + s + "%" : "") + " window " + w[0] + "x" + w[1];
                            StretchedMode.configure(on, o[0], false, o[1], s);
                            int windowSamples = windowSamples(aa);
                            glToolkitAa(windowSamples <= 1, windowSamples <= 1 ? aa : 0);
                            Layout fixed = layout(w, true, on, o[0], o[1], s);
                            clicks(name + " fixed", fixed);
                            Layout resizable = layout(w, false, on, o[0], o[1], s);
                            clicks(name + " resizable", resizable);
                            check(fixed.canvasW == resizable.canvasW && fixed.canvasH == resizable.canvasH,
                                "AA: canvas changes size at a fixed <-> resizable switch (black on the real screen until the next swap)",
                                name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " vs resizable " + resizable.canvasW + "x" + resizable.canvasH);
                            check(fixed.canvasX == resizable.canvasX && fixed.canvasY == resizable.canvasY,
                                "AA: canvas moves at a fixed <-> resizable switch",
                                name + ": fixed canvas at " + fixed.canvasX + "," + fixed.canvasY + " vs resizable " + resizable.canvasX + "," + resizable.canvasY);
                            check(fixed.canvasW == w[0] && fixed.canvasH == w[1] && fixed.canvasX == 0 && fixed.canvasY == 0,
                                "AA: the canvas does not fill the window's game area",
                                name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " at " + fixed.canvasX + "," + fixed.canvasY);
                            check(fixed.fills && resizable.fills, "AA: the client fits / restores the WINDOW at a switch",
                                name + ": fillsWindow fixed=" + fixed.fills + " resizable=" + resizable.fills);
                        }
                    }
                }
            }
            // The frame through the real glSync / glPresent / glReadBegin (GL seam that models multisampled renderbuffers).
            for (int[] w : new int[][] {{944, 581}, {1648, 989}, {1890, 989}, {765, 503}, {1216, 581}}) {
                for (boolean on : new boolean[] {false, true}) {
                    for (boolean[] o : options) {
                        if (!on && (o[0] || o[1])) {
                            continue;
                        }
                        for (boolean fixedMode : new boolean[] {true, false}) {
                            cases++;
                            String name = "AA x" + aa + " frame " + (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                                + (fixedMode ? " fixed" : " resizable") + " window " + w[0] + "x" + w[1];
                            StretchedMode.configure(on, o[0], false, o[1], 16);
                            int windowSamples = windowSamples(aa);
                            RecordingGl gl = new RecordingGl();
                            StretchedMode.useGlForTest(gl);
                            glToolkitAa(windowSamples <= 1, windowSamples <= 1 ? aa : 0);
                            Layout l = layout(w, fixedMode, on, o[0], o[1], 16);
                            java.awt.Dimension render = StretchedMode.renderSize(GameShell.canvas);
                            StretchedMode.glSync(true, render.width, render.height);
                            int drawSamples = gl.samplesOf(gl.drawFb, windowSamples);
                            check(drawSamples == aa, "AA: the frame is drawn without the player's anti-aliasing",
                                name + ": the game draws into " + (gl.drawFb == 0 ? "the window" : "framebuffer " + gl.drawFb) + " with " + drawSamples + " samples, wanted " + aa);
                            gl.events.clear();
                            gl.badBlits.clear();
                            StretchedMode.glPresent(false, l.canvasW, l.canvasH);
                            for (String bad : gl.badBlits) {
                                check(false, "AA: a multisampled buffer is blitted scaled / moved or into a multisampled window (INVALID_OPERATION = black)", name + ": " + bad);
                            }
                            boolean oneToOne = l.drawX == 0 && l.drawY == 0 && l.realW == l.canvasW && l.realH == l.canvasH && l.drawnW == l.canvasW && l.drawnH == l.canvasH;
                            if (gl.drawFb == 0 && oneToOne && windowSamples > 1) {
                                continue; // drawn straight into a multisampled window (the old path) - judged by the canvas rules above
                            }
                            int[] want = {0, 0, l.realW, l.realH, l.drawX, l.canvasH - l.drawY - l.drawnH, l.drawX + l.drawnW, l.canvasH - l.drawY};
                            check(gl.blit != null && java.util.Arrays.equals(gl.blit, want), "AA: the frame is not blitted onto its drawn rectangle",
                                name + ": blit " + (gl.blit == null ? "none" : java.util.Arrays.toString(gl.blit)) + ", want " + java.util.Arrays.toString(want));
                            if (gl.blit != null) {
                                check(gl.blitReadSamples <= 1, "AA: the window blit reads a multisampled buffer (must be resolved 1:1 first)",
                                    name + ": read framebuffer has " + gl.blitReadSamples + " samples");
                            }
                            // The minimap / sprites are copied from the frame (glCopyTexSubImage2D) while the toolkit's scissor test is on.
                            gl.scissorOn = true;
                            Boolean resolved = readBegin();
                            if (resolved != null) {
                                int readSamples = gl.samplesOf(gl.readFb, windowSamples);
                                check(readSamples <= 1, "AA: a screenshot (glReadPixels) reads a multisampled buffer",
                                    name + ": read framebuffer " + gl.readFb + " has " + readSamples + " samples");
                                check(gl.scissorOn, "AA: the toolkit's scissor test is left off after a read", name);
                                if (resolved) {
                                    readEnd();
                                    check(gl.drawFb != 0 && gl.samplesOf(gl.drawFb, windowSamples) == aa, "AA: after a screenshot the game no longer draws into its buffer",
                                        name + ": draw framebuffer " + gl.drawFb);
                                }
                            }
                        }
                    }
                }
            }
        }
        glToolkitAa(true, 0);
        StretchedMode.useGlForTest(new RecordingGl());

        // 2. A2 round 9 (Keurmeester A2 round 8 FAIL: DirectX switches fully black, 5 of 5 runs): DirectX gets the OpenGL rule. Whole
        //    class: every window x Stretched OFF / ON x Keep aspect / Integer x scaling x Increased performance - the canvas has the
        //    same size and place in fixed and resizable and fills the window, the client never fits the window, clicks land on the
        //    drawn pixel; the toolkit renders a frame that is not 1:1 at its real size and flip moves exactly that real-size corner
        //    of the back buffer onto the drawn rectangle (jagdx StretchRect: x, y, width, height), 1:1 without a filter, stretched linear / point;
        //    2b (round 9 review): every frame also through D3DToolkit's present (D3DPresent) on a fake jagdx device, with and without AA.
        glToolkit(false);
        d3dToolkit(true);
        for (int[] w : windows) {
            for (boolean on : new boolean[] {false, true}) {
                for (boolean[] o : options) {
                    if (!on && (o[0] || o[1])) {
                        continue;
                    }
                    for (int s : scalings) {
                        for (boolean fast : new boolean[] {false, true}) {
                            if (!on && (s != 0 || fast)) {
                                continue;
                            }
                            cases++;
                            String name = "DirectX " + (on ? "Stretched ON" : "Stretched OFF") + (o[0] ? " +KeepAspect" : "") + (o[1] ? " +Integer" : "")
                                + (fast ? " +IncreasedPerformance" : "") + (on ? " " + s + "%" : "") + " window " + w[0] + "x" + w[1];
                            Layout[] both = new Layout[2];
                            for (int m = 0; m < 2; m++) {
                                boolean fixedMode = m == 0;
                                Layout l = layoutFast(w, fixedMode, on, o[0], o[1], s, fast);
                                both[m] = l;
                                String mode = name + (fixedMode ? " fixed" : " resizable");
                                clicks(mode, l);
                                java.awt.Dimension render = StretchedMode.renderSize(GameShell.canvas);
                                int[] plan = d3dPlan();
                                boolean oneToOne = l.drawX == 0 && l.drawY == 0 && l.realW == l.canvasW && l.realH == l.canvasH && l.drawnW == l.canvasW && l.drawnH == l.canvasH;
                                // 2b: the frame through D3DToolkit's present (fake jagdx device), without and with anti-aliasing.
                                d3dFrame(mode, l, plan, false);
                                d3dFrame(mode, l, plan, true);
                                if (oneToOne) {
                                    check(plan == null, "DirectX moves a frame that is already 1:1 over the whole canvas", mode);
                                    continue;
                                }
                                check(render.width == l.realW && render.height == l.realH, "DirectX does not render the placed frame at its real size",
                                    mode + ": renders " + render.width + "x" + render.height + ", real " + l.realW + "x" + l.realH);
                                int filter = l.drawnW == l.realW && l.drawnH == l.realH ? 0 : fast ? 1 : 2;
                                int[] want = {l.realW, l.realH, l.drawX, l.drawY, l.drawnW, l.drawnH, filter}; // jagdx StretchRect: x, y, width, height
                                check(plan != null && java.util.Arrays.equals(plan, want), "DirectX flip does not place the frame on its drawn rectangle",
                                    mode + ": plan " + (plan == null ? "none (the canvas is resized instead)" : java.util.Arrays.toString(plan)) + ", want " + java.util.Arrays.toString(want));
                            }
                            Layout fixed = both[0];
                            Layout resizable = both[1];
                            check(fixed.canvasW == resizable.canvasW && fixed.canvasH == resizable.canvasH,
                                "DirectX: canvas changes size at a fixed <-> resizable switch (black on the real screen until the first Present)",
                                name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " vs resizable " + resizable.canvasW + "x" + resizable.canvasH);
                            check(fixed.canvasX == resizable.canvasX && fixed.canvasY == resizable.canvasY,
                                "DirectX: canvas moves at a fixed <-> resizable switch",
                                name + ": fixed canvas at " + fixed.canvasX + "," + fixed.canvasY + " vs resizable " + resizable.canvasX + "," + resizable.canvasY);
                            check(fixed.canvasW == w[0] && fixed.canvasH == w[1] && fixed.canvasX == 0 && fixed.canvasY == 0,
                                "DirectX: the canvas does not fill the window's game area",
                                name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " at " + fixed.canvasX + "," + fixed.canvasY);
                            check(fixed.fills && resizable.fills,
                                "DirectX: the client fits / restores the WINDOW at a switch",
                                name + ": fillsWindow fixed=" + fixed.fills + " resizable=" + resizable.fills);
                        }
                    }
                }
            }
        }
        // A window smaller than the 765x503 fixed game cannot hold the real-size frame in its back buffer: the old path (the
        // client fits the window to the game), never a cut-off frame.
        for (int[] w : new int[][] {{700, 480}, {764, 503}, {765, 502}}) {
            cases++;
            Layout l = layout(w, true, false, false, false, 0);
            int[] plan = d3dPlan();
            check(plan == null && l.canvasW == 765 && l.canvasH == 503 && !l.fills,
                "DirectX: a window smaller than the fixed game cuts the frame off instead of fitting the window",
                "window " + w[0] + "x" + w[1] + ": canvas " + l.canvasW + "x" + l.canvasH + " fills=" + l.fills + " plan=" + java.util.Arrays.toString(plan));
        }
        d3dToolkit(false);

        // 3. Other toolkits (software / Java): unchanged - the canvas is the drawn size, placed by the game's margins.
        glToolkit(false);
        for (int[] w : new int[][] {{944, 581}, {1648, 989}, {765, 503}}) {
            for (boolean on : new boolean[] {false, true}) {
                cases++;
                String name = "non-GL " + (on ? "Stretched ON" : "Stretched OFF") + " window " + w[0] + "x" + w[1];
                // N187/N188: in fixed the stretch is 765x503 times the scaling factor, at most the window - 200% fills every window here.
                Layout fixed = layout(w, true, on, false, false, on ? 200 : 0);
                int expectW = on ? w[0] : 765;
                int expectH = on ? w[1] : 503;
                check(fixed.canvasW == expectW && fixed.canvasH == expectH,
                    "non-GL toolkit: fixed canvas size changed by this fix",
                    name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + ", expected " + expectW + "x" + expectH);
                clicks(name + " fixed", fixed);
            }
        }

        // 3b. A2 round 9 review (nakijker FOUT 2): the software renderers (SSE / Java), Stretched OFF: the canvas fills the window in
        //    fixed and resizable (no window fit, no canvas resize at a switch), the real-size surface is flipped at the drawn rectangle
        //    (667 OrthoMode: resizeCanvas smaller + flip at an offset) with black margins - checked through the real Java present
        //    (JavaDefaultSurface.clip into a window-size image, as JavaToolkit.flip does); Stretched ON keeps the Java stand-in's path.
        swToolkit(true);
        for (int[] w : windows) {
            cases++;
            String name = "Software Stretched OFF window " + w[0] + "x" + w[1];
            Layout fixed = layout(w, true, false, false, false, 0);
            boolean placedFixed = swPlaced();
            clicks(name + " fixed", fixed);
            boolean isNamed = false;
            for (int[] n : named) {
                isNamed |= n[0] == w[0] && n[1] == w[1];
            }
            if (isNamed) {
                swPicture(name + " fixed", fixed, placedFixed);
            }
            Layout resizable = layout(w, false, false, false, false, 0);
            clicks(name + " resizable", resizable);
            check(fixed.canvasW == resizable.canvasW && fixed.canvasH == resizable.canvasH,
                "Software: canvas changes size at a fixed <-> resizable switch (the window is fitted: a black PrintWindow frame)",
                name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " vs resizable " + resizable.canvasW + "x" + resizable.canvasH);
            check(fixed.canvasW == w[0] && fixed.canvasH == w[1] && fixed.canvasX == 0 && fixed.canvasY == 0,
                "Software: the canvas does not fill the window's game area", name + ": fixed canvas " + fixed.canvasW + "x" + fixed.canvasH + " at " + fixed.canvasX + "," + fixed.canvasY);
            check(fixed.fills && resizable.fills, "Software: the client fits / restores the WINDOW at a switch", name + ": fillsWindow fixed=" + fixed.fills + " resizable=" + resizable.fills);
            check(placedFixed && fixed.drawX == (w[0] - 765) / 2 && fixed.drawY == 0,
                "Software: the fixed frame is not flipped at its drawn rectangle", name + ": placed=" + placedFixed + " at " + fixed.drawX + "," + fixed.drawY);
            java.awt.Dimension render = StretchedMode.renderSize(GameShell.canvas);
            check(render.width == resizable.realW && render.height == resizable.realH, "Software: the surface is not the real game size", name);
        }
        for (int[] w : new int[][] {{944, 581}, {1648, 989}}) {
            cases++;
            Layout on = layout(w, true, true, false, false, 200);
            check(on.canvasW == w[0] && on.canvasH == w[1] && !swPlaced(), "Software Stretched ON: the Java stand-in's stretched path changed",
                "window " + w[0] + "x" + w[1] + ": canvas " + on.canvasW + "x" + on.canvasH + " placed=" + swPlaced());
        }
        swToolkit(false);

        // 2b (end): D3DToolkit releases its render target before Reset / at stop, and a failing StretchRect falls back (last:
        // d3dFailed holds for the rest of the run).
        d3dResetReleases();
        d3dFallback();

        System.setOut(OUT);
        int failed = 0;
        for (int n : kinds.values()) {
            failed += n;
        }
        if (failed > 0) {
            System.out.println("StretchedSwitchSurfaceRegressionTest: " + cases + " cases, " + assertions + " checks, " + failed + " failed");
            for (Map.Entry<String, Integer> e : kinds.entrySet()) {
                System.out.println("  " + e.getValue() + "x " + e.getKey());
            }
            for (String line : examples) {
                System.out.println("  e.g. " + line);
            }
            System.exit(1);
        }
        System.out.println("StretchedSwitchSurfaceRegressionTest: " + cases + " cases, " + assertions + " checks, all passed");
    }

    /** What the AWT canvas gets after the game's resize loop (Static712 / InterfaceManager.windowModeChanged) for one mode. */
    private static final class Layout {
        int canvasW, canvasH, canvasX, canvasY, realW, realH, drawnW, drawnH, drawX, drawY;
        boolean fills;
    }

    private static Layout layout(int[] w, boolean fixed, boolean on, boolean keepAspect, boolean integer, int scaling) throws Exception {
        return layout(w, fixed, on, keepAspect, integer, scaling, false);
    }

    private static Layout layout(int[] w, boolean fixed, boolean on, boolean keepAspect, boolean integer, int scaling, boolean fast) throws Exception {
        StretchedMode.configure(on, keepAspect, fast, integer, scaling);
        GameShell.frameWid = w[0];
        GameShell.frameHei = w[1];
        // The game's own layout for the mode (Static712.method9329): fixed = 765x503 centred at the top, resizable = the window.
        if (fixed) {
            GameShell.canvasWid = 765;
            GameShell.canvasHei = 503;
            GameShell.leftMargin = Math.max(0, (w[0] - 765) / 2);
            GameShell.topMargin = 0;
        } else {
            GameShell.canvasWid = w[0];
            GameShell.canvasHei = w[1];
            GameShell.leftMargin = 0;
            GameShell.topMargin = 0;
        }
        StretchedMode.layout(fixed);
        Layout l = new Layout();
        l.canvasW = StretchedMode.canvasWidth();
        l.canvasH = StretchedMode.canvasHeight();
        l.canvasX = GameShell.leftMargin;
        l.canvasY = GameShell.topMargin;
        l.realW = GameShell.canvasWid;
        l.realH = GameShell.canvasHei;
        l.drawnW = StretchedMode.stretchedWidth();
        l.drawnH = StretchedMode.stretchedHeight();
        l.drawX = intOr("drawX", 0);
        l.drawY = intOr("drawY", 0);
        l.fills = StretchedMode.fillsWindow();
        GameShell.canvas.setSize(l.canvasW, l.canvasH); // StretchedMode.drawnWidth / mouse use the canvas's own size
        return l;
    }

    /** A click on the window where game pixel (gx, gy) is drawn must reach the game as (gx, gy). */
    private static void clicks(String name, Layout l) throws Exception {
        check(l.drawX >= 0 && l.drawY >= 0 && l.drawX + l.drawnW <= l.canvasW && l.drawY + l.drawnH <= l.canvasH,
            "the drawn game lies outside the canvas", name + ": drawn " + l.drawnW + "x" + l.drawnH + " at " + l.drawX + "," + l.drawY
                + " in canvas " + l.canvasW + "x" + l.canvasH);
        int[][] pixels = {{0, 0}, {l.realW / 2, l.realH / 2}, {l.realW - 1, l.realH - 1}, {l.realW - 30, 20}, {20, l.realH - 40}};
        for (int[] p : pixels) {
            // Window position of the centre of that game pixel, relative to the canvas.
            double sx = l.drawX + (p[0] + 0.5) * l.drawnW / (double) l.realW;
            double sy = l.drawY + (p[1] + 0.5) * l.drawnH / (double) l.realH;
            int gx = StretchedMode.mouseX((int) sx);
            int gy = StretchedMode.mouseY((int) sy);
            check(Math.abs(gx - p[0]) <= 1 && Math.abs(gy - p[1]) <= 1, "a click lands on another game pixel than the one drawn under it",
                name + ": game pixel " + p[0] + "," + p[1] + " drawn at canvas " + (int) sx + "," + (int) sy + " -> mouse " + gx + "," + gy);
        }
    }

    /**
     * A GL that records the window clear and the blit of one present (framebuffer objects always complete). Models
     * multisampled renderbuffers: which renderbuffer is attached to which framebuffer, with how many samples; a blit out of a
     * multisampled framebuffer must be 1:1 into a single-sample one (else INVALID_OPERATION).
     */
    private static final class RecordingGl implements StretchedMode.Gl {
        final List<String> events = new ArrayList<String>();
        final List<String> badBlits = new ArrayList<String>();
        final Map<Integer, Integer> rbSamples = new java.util.HashMap<Integer, Integer>();
        final Map<Integer, Integer> fbColour = new java.util.HashMap<Integer, Integer>();
        int drawFb;
        int readFb;
        int boundRb;
        int nextId = 1;
        int[] blit;
        int blitReadSamples;
        boolean scissorOn;
        boolean clearedWindowBeforeBlit;

        /** Samples of framebuffer [fb]'s colour buffer; 0 = the window, which has [windowSamples]. */
        int samplesOf(int fb, int windowSamples) {
            if (fb == 0) {
                return windowSamples;
            }
            Integer rb = fbColour.get(fb);
            Integer n = rb == null ? null : rbSamples.get(rb);
            return n == null ? 0 : n;
        }

        public void glBindFramebufferEXT(int target, int framebuffer) {
            if (target == 0x8D40 || target == 0x8CA9) {
                drawFb = framebuffer;
            }
            if (target == 0x8D40 || target == 0x8CA8) {
                readFb = framebuffer;
            }
        }

        public void glBlitFramebufferEXT(int a, int b, int c, int d, int e, int f, int g, int h, int mask, int filter) {
            int rs = samplesOf(readFb, 0);
            int ds = samplesOf(drawFb, 0);
            if (scissorOn) {
                badBlits.add("blit from framebuffer " + readFb + " -> " + drawFb + " with the scissor test on (clipped: part of the frame stays old/black)");
            }
            if (rs > 1 && (a != e || b != f || c != g || d != h || ds > 1)) {
                badBlits.add("blit from framebuffer " + readFb + " (" + rs + " samples) " + a + "," + b + "-" + c + "," + d + " -> " + drawFb + " " + e + "," + f + "-" + g + "," + h);
            }
            if (drawFb == 0) {
                blit = new int[] {a, b, c, d, e, f, g, h};
                blitReadSamples = rs;
                clearedWindowBeforeBlit = events.contains("clear0");
            }
            events.add("blit" + drawFb);
        }

        public void glRenderbufferStorageMultisampleEXT(int target, int samples, int format, int width, int height) {
            rbSamples.put(boundRb, samples);
        }

        public void glDisable(int cap) {
            if (cap == 0x0C11) {
                scissorOn = false;
            }
        }

        public void glEnable(int cap) {
            if (cap == 0x0C11) {
                scissorOn = true;
            }
        }

        public void glGenFramebuffersEXT(int n, int[] ids, int offset) {
            ids[offset] = nextId++;
        }

        public void glGenRenderbuffersEXT(int n, int[] ids, int offset) {
            ids[offset] = nextId++;
        }

        public void glBindRenderbufferEXT(int target, int renderbuffer) {
            boundRb = renderbuffer;
        }

        public void glRenderbufferStorageEXT(int target, int format, int width, int height) {
            rbSamples.put(boundRb, 0);
        }

        public void glFramebufferRenderbufferEXT(int target, int attachment, int renderbufferTarget, int renderbuffer) {
            if (attachment == 0x8CE0) {
                fbColour.put(drawFb, renderbuffer);
            }
        }

        public int glCheckFramebufferStatusEXT(int target) {
            return 0x8CD5;
        }

        public void glDeleteFramebuffersEXT(int n, int[] ids, int offset) {
        }

        public void glDeleteRenderbuffersEXT(int n, int[] ids, int offset) {
        }

        public void glClearColor(float red, float green, float blue, float alpha) {
        }

        public void glClear(int mask) {
            events.add("clear" + drawFb);
        }
    }

    /** Layout with Increased performance (the point filter of a stretched DirectX frame). */
    private static Layout layoutFast(int[] w, boolean fixed, boolean on, boolean keepAspect, boolean integer, int scaling, boolean fast) throws Exception {
        return layout(w, fixed, on, keepAspect, integer, scaling, fast);
    }

    /** Static595's report of the DirectX toolkit (absent on the old code: DirectX = "not OpenGL", the old canvas sizes). */
    private static void d3dToolkit(boolean on) {
        try {
            StretchedMode.class.getMethod("d3dToolkit", boolean.class).invoke(null, on);
        } catch (NoSuchMethodException absent) {
            // old code
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** StretchedMode.d3dPresentPlan (null when absent: the old code never moved a DirectX frame). */
    private static int[] d3dPlan() {
        try {
            return (int[]) StretchedMode.class.getMethod("d3dPresentPlan").invoke(null);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The GL-toolkit seam of the fix (absent on the old code: then the old layout is what the test sees). */
    private static void glToolkit(boolean on) {
        try {
            Method m = StretchedMode.class.getMethod("glToolkit", boolean.class);
            m.invoke(null, on);
        } catch (NoSuchMethodException absent) {
            // old code: no notion of the toolkit; every layout is the one the client used for OpenGL
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** The window samples Static595.createWithHeap asks for (old code: plain only while stretching - glPlainWindow). */
    private static int windowSamples(int playerSamples) {
        try {
            return (Integer) StretchedMode.class.getMethod("glWindowSamples", int.class).invoke(null, playerSamples);
        } catch (NoSuchMethodException absent) {
            return StretchedMode.glPlainWindow() ? 0 : playerSamples;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Static595's report of the OpenGL toolkit: plain window or not, offscreen anti-aliasing samples (old code: no samples). */
    private static void glToolkitAa(boolean plain, int samples) {
        try {
            StretchedMode.class.getMethod("glToolkit", boolean.class, boolean.class, int.class).invoke(null, true, plain, samples);
        } catch (NoSuchMethodException absent) {
            try {
                StretchedMode.class.getMethod("glToolkit", boolean.class, boolean.class).invoke(null, true, plain);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** StretchedMode.glReadBegin (null when absent: the old code never had a multisampled offscreen frame to resolve). */
    private static Boolean readBegin() {
        try {
            return (Boolean) StretchedMode.class.getMethod("glReadBegin", boolean.class).invoke(null, true);
        } catch (NoSuchMethodException absent) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void readEnd() {
        try {
            StretchedMode.class.getMethod("glReadEnd").invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static int intOr(String method, int fallback) throws Exception {
        try {
            return (Integer) StretchedMode.class.getMethod(method).invoke(null);
        } catch (NoSuchMethodException absent) {
            return fallback;
        }
    }

    private static void check(boolean ok, String kind, String detail) {
        assertions++;
        if (ok) {
            return;
        }
        Integer n = kinds.get(kind);
        kinds.put(kind, n == null ? 1 : n + 1);
        if (n == null || examples.size() < 14) {
            examples.add(detail);
        }
    }

    // ---- 2b. A2 round 9 review (nakijker FOUTEN 1 + 3): D3DToolkit's present through the jagdx seam (com.jagex.D3DPresent) ----
    //
    // A fake jagdx device that keeps every operation on the window-size back buffer and the real-size copy, and refuses what
    // Direct3D 9 refuses (StretchRect INTO a multisampled surface, a rectangle outside its surface, source = destination). The
    // toolkit draws the real-size frame at D3DPresent.origin (viewport + scissor offset), then flip runs D3DPresent.place; the
    // picture is then read back at sample points: inside the drawn rectangle the right game pixel, outside it black.

    static final int D3DERR_INVALIDCALL = -2005530516;

    static final class FakeD3D implements D3DPresent.Device {
        final int width, height;
        final boolean multisampled;
        final String back = "back";
        Object copy;
        int copyW, copyH;
        final List<int[]> backOps = new ArrayList<int[]>();
        final List<int[]> copyOps = new ArrayList<int[]>();
        final List<String> calls = new ArrayList<String>();
        final List<String> invalid = new ArrayList<String>();
        final List<Object> released = new ArrayList<Object>();
        int[] viewport;
        boolean scissor = true;
        boolean stateDirty;
        boolean failStretch;

        FakeD3D(int width, int height, boolean multisampled) {
            this.width = width;
            this.height = height;
            this.multisampled = multisampled;
            this.viewport = new int[] {0, 0, width, height};
        }

        /** The toolkit draws the real-size game with its viewport at x, y. Op kinds: 0 draw, 1 clear, 2 stretch from copy, 3 stretch from back. */
        void drawGame(int x, int y, int w, int h) {
            backOps.add(new int[] {0, x, y, w, h});
        }

        @Override
        public Object backBuffer() {
            return back;
        }

        @Override
        public boolean multisampled() {
            return multisampled;
        }

        @Override
        public Object createRenderTarget(int w, int h) {
            calls.add("create " + w + "x" + h);
            copy = new Object();
            copyW = w;
            copyH = h;
            copyOps.clear();
            return copy;
        }

        @Override
        public void release(Object surface) {
            calls.add("release");
            released.add(surface);
        }

        @Override
        public int stretchRect(Object source, int x, int y, int w, int h, Object destination, int dx, int dy, int dw, int dh, int filter) {
            calls.add("stretch " + (source == back ? "back" : "copy") + " " + x + "," + y + " " + w + "x" + h + " -> " + (destination == back ? "back" : "copy") + " " + dx + "," + dy + " " + dw + "x" + dh + " f" + filter);
            if (failStretch) {
                return D3DERR_INVALIDCALL;
            }
            if (source == destination) {
                invalid.add("StretchRect source = destination");
                return D3DERR_INVALIDCALL;
            }
            if (destination == back && multisampled) {
                invalid.add("StretchRect INTO the multisampled back buffer (Direct3D 9 refuses it)");
                return D3DERR_INVALIDCALL;
            }
            int sw = source == back ? width : copyW, sh = source == back ? height : copyH;
            int tw = destination == back ? width : copyW, th = destination == back ? height : copyH;
            if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > sw || y + h > sh || dx < 0 || dy < 0 || dw <= 0 || dh <= 0 || dx + dw > tw || dy + dh > th) {
                invalid.add("StretchRect rectangle outside its surface: " + calls.get(calls.size() - 1) + " (source " + sw + "x" + sh + ", destination " + tw + "x" + th + ")");
                return D3DERR_INVALIDCALL;
            }
            boolean fromCopy = source != back;
            int sourceOps = fromCopy ? copyOps.size() : backOps.size();
            int[] op = {fromCopy ? 2 : 3, dx, dy, dw, dh, x, y, w, h, sourceOps};
            (destination == back ? backOps : copyOps).add(op);
            return 0;
        }

        @Override
        public void setViewport(int x, int y, int w, int h) {
            if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > width || y + h > height) {
                invalid.add("viewport outside the back buffer: " + x + "," + y + " " + w + "x" + h + " in " + width + "x" + height);
            }
            viewport = new int[] {x, y, w, h};
            stateDirty = true;
        }

        @Override
        public void scissorTest(boolean on) {
            scissor = on;
            stateDirty = true;
        }

        @Override
        public int clearBlack() {
            if (scissor) {
                invalid.add("Clear with the toolkit's scissor test on (clears only the game's clip rectangle)");
            }
            backOps.add(new int[] {1, viewport[0], viewport[1], viewport[2], viewport[3]});
            return 0;
        }

        @Override
        public void restoreState() {
            stateDirty = false;
            scissor = true;
        }

        /** {kind, gx, gy}: kind 0 = game pixel gx, gy; 1 = black; 2 = undefined (DISCARD back buffer, never written). */
        int[] at(boolean onBack, int px, int py, int upTo) {
            List<int[]> ops = onBack ? backOps : copyOps;
            for (int i = upTo - 1; i >= 0; i--) {
                int[] op = ops.get(i);
                if (px < op[1] || py < op[2] || px >= op[1] + op[3] || py >= op[2] + op[4]) {
                    continue;
                }
                if (op[0] == 0) {
                    return new int[] {0, px - op[1], py - op[2]};
                }
                if (op[0] == 1) {
                    return new int[] {1, 0, 0};
                }
                int sx = op[5] + (int) ((long) (px - op[1]) * op[7] / op[3]);
                int sy = op[6] + (int) ((long) (py - op[2]) * op[8] / op[4]);
                return at(op[0] == 3, sx, sy, op[9]);
            }
            return new int[] {2, 0, 0};
        }
    }

    /** One DirectX frame of layout [l] through D3DPresent with the player's anti-aliasing [aa], checked at sample points. */
    private static void d3dFrame(String mode, Layout l, int[] plan, boolean aa) {
        int playerSamples = aa ? 4 : 0;
        int samples = d3dWindowSamples(playerSamples);
        String name = mode + (aa ? " AA4" : "") + " (back buffer samples " + samples + ")";
        if (aa && !StretchedMode.enabled()) {
            check(samples == playerSamples, "DirectX drops the player's anti-aliasing with Stretched Mode off", name);
        }
        FakeD3D dev = new FakeD3D(l.canvasW, l.canvasH, samples > 0);
        int[] origin = D3DPresent.origin(plan);
        dev.drawGame(origin[0], origin[1], l.realW, l.realH);
        D3DPresent present = new D3DPresent();
        int result = present.place(plan, l.canvasW, l.canvasH, dev);
        for (String bad : dev.invalid) {
            check(false, "DirectX present makes a call Direct3D 9 refuses (the frame is not placed / the run falls back to the black canvas path)", name + ": " + bad + " calls=" + dev.calls);
        }
        check(result != D3DPresent.RESET, "DirectX keeps a multisampled back buffer while the frame must be scaled (device reset at every frame)", name + " calls=" + dev.calls);
        check(!dev.stateDirty, "DirectX present leaves its own viewport / scissor test behind (the next frame draws in the wrong place)", name + " calls=" + dev.calls);
        int wrong = 0;
        String first = null;
        int[] xs = samplePoints(l.drawX, l.drawnW, l.canvasW);
        int[] ys = samplePoints(l.drawY, l.drawnH, l.canvasH);
        for (int px : xs) {
            for (int py : ys) {
                int[] got = dev.at(true, px, py, dev.backOps.size());
                boolean inside = px >= l.drawX && py >= l.drawY && px < l.drawX + l.drawnW && py < l.drawY + l.drawnH;
                boolean ok;
                String want;
                if (inside) {
                    int gx = (int) ((long) (px - l.drawX) * l.realW / l.drawnW);
                    int gy = (int) ((long) (py - l.drawY) * l.realH / l.drawnH);
                    ok = got[0] == 0 && Math.abs(got[1] - gx) <= 1 && Math.abs(got[2] - gy) <= 1;
                    want = "game " + gx + "," + gy;
                } else {
                    ok = got[0] == 1;
                    want = "black";
                }
                if (!ok) {
                    wrong++;
                    if (first == null) {
                        first = px + "," + py + " is " + (got[0] == 0 ? "game " + got[1] + "," + got[2] : got[0] == 1 ? "black" : "undefined") + ", want " + want;
                    }
                }
            }
        }
        check(wrong == 0, "DirectX present: wrong picture (the frame is not exactly on its drawn rectangle / the margins are not black)",
            name + ": " + wrong + " sample points wrong, e.g. " + first + "; drawn " + l.drawnW + "x" + l.drawnH + " at " + l.drawX + "," + l.drawY + " plan=" + java.util.Arrays.toString(plan) + " calls=" + dev.calls);
        if (plan != null && plan[6] != 0 && samples == 0) {
            // The real-size copy is kept between frames and released before every device Reset (D3DPOOL_DEFAULT).
            check(present.holdsCopy(), "DirectX does not keep its real-size copy between frames", name);
            Object copy = dev.copy;
            present.release(dev);
            check(!present.holdsCopy() && dev.released.size() == 1 && dev.released.get(0) == copy,
                "DirectX does not release its render target (a Reset with a D3DPOOL_DEFAULT surface alive fails: device lost)", name + " released=" + dev.released.size());
        }
    }

    /** Edges of the drawn rectangle (each side and its neighbour pixel) plus an even spread over the canvas. */
    private static int[] samplePoints(int start, int size, int canvas) {
        java.util.TreeSet<Integer> points = new java.util.TreeSet<Integer>();
        for (int p : new int[] {0, start - 1, start, start + 1, start + size / 2, start + size - 2, start + size - 1, start + size, canvas - 1}) {
            if (p >= 0 && p < canvas) {
                points.add(p);
            }
        }
        for (int i = 0; i < 24; i++) {
            points.add((int) ((long) canvas * i / 24));
        }
        int[] out = new int[points.size()];
        int i = 0;
        for (int p : points) {
            out[i++] = p;
        }
        return out;
    }

    /** StretchedMode.d3dWindowSamples (absent on the old code: the back buffer always had the player's samples). */
    private static int d3dWindowSamples(int playerSamples) {
        try {
            return (Integer) StretchedMode.class.getMethod("d3dWindowSamples", int.class).invoke(null, playerSamples);
        } catch (NoSuchMethodException absent) {
            return playerSamples;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** D3DToolkit.method4861 releases the copy before it calls Reset (source guard: the native device cannot run here). */
    private static void d3dResetReleases() {
        java.io.File source = null;
        for (String p : new String[] {"runescape/src/main/java/D3DToolkit.java", "src/main/java/D3DToolkit.java", "../runescape/src/main/java/D3DToolkit.java"}) {
            java.io.File f = new java.io.File(p);
            if (f.isFile()) {
                source = f;
                break;
            }
        }
        if (source == null) {
            check(false, "D3DToolkit source not found for the Reset-release guard", new java.io.File(".").getAbsolutePath());
            return;
        }
        try {
            String text = new String(java.nio.file.Files.readAllBytes(source.toPath()), "UTF-8");
            int method = text.indexOf("public boolean method4861()");
            int reset = text.indexOf(".Reset(", method);
            int release = text.toLowerCase().indexOf("release", method);
            check(method >= 0 && release > method && release < reset,
                "D3DToolkit.method4861 calls Reset before it released the real-size render target", "method4861 at " + method + ", release at " + release + ", Reset at " + reset);
            int stop = text.indexOf("protected void stop()");
            int stopRelease = text.toLowerCase().indexOf("release", stop);
            check(stop >= 0 && stopRelease > stop && stopRelease < text.indexOf("super.stop()", stop),
                "D3DToolkit.stop does not release the real-size render target", "stop at " + stop);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** A StretchRect that fails sends this run back to the old canvas sizes (one PROBLEM line), never a frame left unplaced. */
    private static void d3dFallback() throws Exception {
        d3dToolkit(true);
        Layout l = layoutFast(new int[] {944, 581}, true, true, false, false, 200, false);
        int[] plan = d3dPlan();
        check(plan != null && plan[6] != 0, "DirectX fallback case: Stretched ON fixed 944x581 is not a scaled plan", java.util.Arrays.toString(plan));
        FakeD3D dev = new FakeD3D(l.canvasW, l.canvasH, false);
        dev.failStretch = true;
        int result = new D3DPresent().place(plan, l.canvasW, l.canvasH, dev);
        check(result == D3DPresent.FAILED, "DirectX present does not report a failing StretchRect", "result " + result + " calls=" + dev.calls);
        check(!StretchedMode.windowCanvas(), "DirectX: after a failing StretchRect the run keeps the window canvas (the frame stays unplaced)", "windowCanvas still on");
        Layout after = layout(new int[] {944, 581}, true, false, false, false, 0);
        check(after.canvasW == 765 && after.canvasH == 503, "DirectX: after a failing StretchRect the canvas is not sized to the game again",
            "canvas " + after.canvasW + "x" + after.canvasH);
    }

    // ---- 3b helpers (software renderers) ----

    private static void swToolkit(boolean on) {
        try {
            StretchedMode.class.getMethod("swToolkit", boolean.class).invoke(null, on);
        } catch (NoSuchMethodException absent) {
            // old code: software kept the window fit
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean swPlaced() {
        try {
            return (Boolean) StretchedMode.class.getMethod("swPlaced", java.awt.Canvas.class).invoke(null, GameShell.canvas);
        } catch (NoSuchMethodException absent) {
            return false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** JavaToolkit.flip on a window-size image: margins (StretchedMode.swClearMargins) + JavaDefaultSurface.clip at the place. */
    private static void swPicture(String name, Layout l, boolean placed) {
        JavaDefaultSurface surface = new JavaDefaultSurface();
        surface.initialize(GameShell.canvas, l.realW, l.realH);
        for (int i = 0; i < surface.raster.length; i++) {
            surface.raster[i] = 0x100000 | i % 0xFFFFF; // never black: game pixel i
        }
        java.awt.image.BufferedImage window = new java.awt.image.BufferedImage(l.canvasW, l.canvasH, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = window.createGraphics();
        g.setColor(new java.awt.Color(0x7F007F)); // the previous frame (resizable) still in the canvas
        g.fillRect(0, 0, l.canvasW, l.canvasH);
        int cx = 0, cy = 0;
        if (placed) {
            try {
                StretchedMode.class.getMethod("swClearMargins", java.awt.Canvas.class, java.awt.Graphics.class).invoke(null, GameShell.canvas, g);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            cx = l.drawX;
            cy = l.drawY;
        }
        surface.clip(cx, cy, 0, 0, l.realW, l.realH, g); // JavaToolkit.flip(0, 0) with canvasWidth/Height = the render size
        g.dispose();
        int wrong = 0;
        String first = null;
        for (int py : samplePoints(l.drawY, l.realH, l.canvasH)) {
            for (int px : samplePoints(l.drawX, l.realW, l.canvasW)) {
                int got = window.getRGB(px, py) & 0xFFFFFF;
                boolean inside = px >= l.drawX && py >= l.drawY && px < l.drawX + l.realW && py < l.drawY + l.realH;
                int want = inside ? 0x100000 | ((py - l.drawY) * l.realW + (px - l.drawX)) % 0xFFFFF : 0;
                if (got != want) {
                    wrong++;
                    if (first == null) {
                        first = px + "," + py + " is " + Integer.toHexString(got) + ", want " + Integer.toHexString(want);
                    }
                }
            }
        }
        check(wrong == 0, "Software present: wrong picture (the frame is not at its drawn rectangle / the margins are not black)",
            name + ": " + wrong + " sample points wrong, e.g. " + first + "; drawn " + l.realW + "x" + l.realH + " at " + l.drawX + "," + l.drawY + " canvas " + l.canvasW + "x" + l.canvasH);
    }
}
