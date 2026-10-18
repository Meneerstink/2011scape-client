import com.jagex.StretchedMode;
import com.jagex.game.runetek6.client.GameShell;

import java.awt.Canvas;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A2 round 4 / N93 (Keurmeester A2 round 3 reason 1): switching Stretched Mode OFF during the intro left a frozen ghost of the
 * last stretched frame in the part of the fixed game the intro does not draw (the hidden side panel, x 516..765): a second
 * Deadman label, crossed swords and a copy of the scene, still there after 10 s. With Stretched never on that area is black.
 *
 * The class: every time the buffer the game draws into changes - stretching on (a new offscreen framebuffer, its contents
 * undefined), off (back to the window, which still holds the last stretched blit), a new real size (the renderbuffers are
 * stored again = undefined), the window drawable resized, a new GL context - the pixels the client does not draw itself must
 * never show an older picture or undefined memory. The 667 client redraws its interfaces every frame on OpenGL but never
 * clears areas no interface covers (the intro hides the side panel / minimap / chat tabs).
 *
 * The test runs StretchedMode's real OpenGL code (glSync / glPresent, the same calls GlToolkit.flip and method7013 make)
 * against a recording GL: every buffer is a pixel array of "which frame drew this pixel"; a new renderbuffer is UNDEFINED,
 * a resized window drawable keeps its old pixels, swapBuffers exchanges or copies (both driver behaviours), and the client
 * draws only its own region. After every change the screen (front buffer) may show only black or pixels of the frames drawn
 * since the change.
 */
public final class StretchedFrameRegressionTest {
    private static int assertions;
    private static final List<String> failures = new ArrayList<String>();
    private static final java.io.PrintStream OUT = System.out;

    static final int BLACK = 0;
    static final int UNDEFINED = -1;

    /** Pixels of one buffer: the frame number that drew each one (BLACK / UNDEFINED otherwise). */
    static final class Buffer {
        int width;
        int height;
        int[] px;

        Buffer(int width, int height, int fill) {
            this.width = width;
            this.height = height;
            px = new int[Math.max(0, width * height)];
            Arrays.fill(px, fill);
        }

        /** A drawable resized by the window system: the overlap keeps its old pixels, new ones are undefined. */
        void resize(int w, int h) {
            if (w == width && h == height) {
                return;
            }
            int[] next = new int[w * h];
            Arrays.fill(next, UNDEFINED);
            for (int y = 0; y < Math.min(h, height); y++) {
                System.arraycopy(px, y * width, next, y * w, Math.min(w, width));
            }
            width = w;
            height = h;
            px = next;
        }

        void fill(int x0, int y0, int x1, int y1, int value) {
            for (int y = Math.max(0, y0); y < Math.min(height, y1); y++) {
                Arrays.fill(px, y * width + Math.max(0, x0), y * width + Math.min(width, x1), value);
            }
        }
    }

    /** A GL that keeps every buffer as pixels. */
    static final class FakeGl implements StretchedMode.Gl {
        final boolean exchangeOnSwap;
        Buffer back;
        Buffer front;
        final Map<Integer, Buffer> renderbuffers = new HashMap<Integer, Buffer>();
        final Map<Integer, Integer> colourOf = new HashMap<Integer, Integer>();
        int nextId = 1;
        int drawFbo;
        int readFbo;
        int boundRenderbuffer;
        boolean scissor;
        float[] clearColour = {0, 0, 0, 0};

        FakeGl(boolean exchangeOnSwap, int w, int h) {
            this.exchangeOnSwap = exchangeOnSwap;
            back = new Buffer(w, h, BLACK);
            front = new Buffer(w, h, BLACK);
        }

        Buffer target(int fbo) {
            if (fbo == 0) {
                return back;
            }
            Integer rb = colourOf.get(fbo);
            return rb == null ? null : renderbuffers.get(rb);
        }

        public void glBindFramebufferEXT(int target, int framebuffer) {
            if (target == 0x8D40) {
                drawFbo = readFbo = framebuffer;
            } else if (target == 0x8CA9) {
                drawFbo = framebuffer;
            } else if (target == 0x8CA8) {
                readFbo = framebuffer;
            }
        }

        public void glBlitFramebufferEXT(int sx0, int sy0, int sx1, int sy1, int dx0, int dy0, int dx1, int dy1, int mask, int filter) {
            Buffer src = target(readFbo);
            Buffer dst = target(drawFbo);
            if (src == null || dst == null || dx1 <= dx0 || dy1 <= dy0) {
                return;
            }
            for (int y = Math.max(0, dy0); y < Math.min(dst.height, dy1); y++) {
                int sy = sy0 + (int) ((long) (y - dy0) * (sy1 - sy0) / (dy1 - dy0));
                for (int x = Math.max(0, dx0); x < Math.min(dst.width, dx1); x++) {
                    int sx = sx0 + (int) ((long) (x - dx0) * (sx1 - sx0) / (dx1 - dx0));
                    dst.px[y * dst.width + x] = sx < src.width && sy < src.height ? src.px[sy * src.width + sx] : UNDEFINED;
                }
            }
        }

        public void glDisable(int cap) {
            if (cap == 0x0C11) {
                scissor = false;
            }
        }

        public void glEnable(int cap) {
            if (cap == 0x0C11) {
                scissor = true;
            }
        }

        public void glGenFramebuffersEXT(int n, int[] ids, int offset) {
            ids[offset] = nextId++;
        }

        public void glGenRenderbuffersEXT(int n, int[] ids, int offset) {
            ids[offset] = nextId++;
        }

        public void glBindRenderbufferEXT(int target, int renderbuffer) {
            boundRenderbuffer = renderbuffer;
        }

        public void glRenderbufferStorageEXT(int target, int format, int width, int height) {
            // GL: the contents of a newly stored renderbuffer are undefined (often the previous owner's memory).
            renderbuffers.put(boundRenderbuffer, new Buffer(width, height, UNDEFINED));
        }

        public void glFramebufferRenderbufferEXT(int target, int attachment, int rbTarget, int rb) {
            if (attachment == 0x8CE0) {
                colourOf.put(drawFbo, rb);
            }
        }

        public int glCheckFramebufferStatusEXT(int target) {
            return 0x8CD5;
        }

        public void glDeleteFramebuffersEXT(int n, int[] ids, int offset) {
            colourOf.remove(ids[offset]);
        }

        public void glDeleteRenderbuffersEXT(int n, int[] ids, int offset) {
            renderbuffers.remove(ids[offset]);
        }

        public void glClearColor(float red, float green, float blue, float alpha) {
            clearColour = new float[] {red, green, blue, alpha};
        }

        public void glClear(int mask) {
            Buffer dst = target(drawFbo);
            if (dst == null || (mask & 0x4000) == 0 || scissor) {
                // a scissored clear only reaches the clip; the worst case (nothing) is what a wrong clip would give
                return;
            }
            boolean black = clearColour[0] == 0 && clearColour[1] == 0 && clearColour[2] == 0;
            dst.fill(0, 0, dst.width, dst.height, black ? BLACK : -1000);
        }

        void swap() {
            if (exchangeOnSwap) {
                Buffer t = front;
                front = back;
                back = t;
            } else {
                front.resize(back.width, back.height);
                System.arraycopy(back.px, 0, front.px, 0, back.px.length);
            }
        }

        void resizeWindow(int w, int h) {
            back.resize(w, h);
            front.resize(w, h);
        }
    }

    /** One client: a window mode, a stretched state, the region the client draws itself. */
    static final class Sim {
        final FakeGl gl;
        int frame = 100;
        boolean fixed;
        int surfaceW;
        int surfaceH;
        /** The last layout / context change changed the buffer the game draws into or the window (else nothing to check). */
        boolean changed;

        Sim(FakeGl gl) {
            this.gl = gl;
        }

        /** The window / canvas / Stretched state of [fixed] mode in a window of w x h (what Static712 + layout do). */
        void layout(boolean fixed, int w, int h, boolean stretched, int scalingPercent) {
            this.fixed = fixed;
            String before = signature();
            StretchedMode.configure(stretched, false, false, false, scalingPercent);
            if (fixed && !stretched) {
                // the client fits a fixed window to the 765x503 game when nothing stretches (InterfaceManager.changeWindowMode)
                w = 765;
                h = 503;
            }
            GameShell.frameWid = w;
            GameShell.frameHei = h;
            GameShell.canvasWid = fixed ? 765 : w;
            GameShell.canvasHei = fixed ? 503 : h;
            StretchedMode.layout(fixed);
            GameShell.canvas.setSize(StretchedMode.canvasWidth(), StretchedMode.canvasHeight());
            // GlToolkit.method7013: the surface follows the canvas (the real size while stretched), then rspsStretchSync.
            gl.resizeWindow(GameShell.canvas.getWidth(), GameShell.canvas.getHeight());
            java.awt.Dimension render = StretchedMode.renderSize(GameShell.canvas);
            surfaceW = render.width;
            surfaceH = render.height;
            StretchedMode.glSync(true, surfaceW, surfaceH);
            changed = !before.equals(signature());
        }

        String signature() {
            return GameShell.canvas.getWidth() + "x" + GameShell.canvas.getHeight() + " active=" + StretchedMode.active() + " real=" + StretchedMode.realWidth() + "x"
                + StretchedMode.realHeight() + " offscreen=" + StretchedMode.glOwnsFrame();
        }

        /** The new GL context of a toolkit change (GlToolkit.<init>): a fresh window, no framebuffers. */
        void newContext() {
            StretchedMode.glContextLost();
            gl.back = new Buffer(GameShell.canvas.getWidth(), GameShell.canvas.getHeight(), UNDEFINED);
            gl.front = new Buffer(GameShell.canvas.getWidth(), GameShell.canvas.getHeight(), UNDEFINED);
            gl.drawFbo = gl.readFbo = 0;
            StretchedMode.glSync(true, surfaceW, surfaceH);
            // only a stretched context is ours (a new offscreen framebuffer); an unstretched new window is the 2011 client's own
            changed = StretchedMode.active();
        }

        /**
         * One frame: the client draws its own region of the surface, then GlToolkit.flip (glPresent, swapBuffers,
         * rspsStretchSync). [region] = x0, y0, x1, y1 in real (game) pixels.
         */
        void frame(int[] region) {
            frame++;
            Buffer target = gl.target(gl.drawFbo);
            if (target != null) {
                target.fill(region[0], region[1], region[2], region[3], frame);
            }
            StretchedMode.glPresent(false, GameShell.canvas.getWidth(), GameShell.canvas.getHeight());
            gl.swap();
            StretchedMode.glSync(true, surfaceW, surfaceH);
        }
    }

    /** The intro in fixed mode hides the side panel, minimap and chat tabs: the client draws only x 0..515 (viewport + chat). */
    static final int[] INTRO_FIXED = {0, 0, 516, 503};

    public static void main(String[] args) {
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
            }
        }));
        GameShell.canvas = new Canvas();
        StretchedMode.toolkitChanged(true);
        int cases = 0;
        for (boolean exchange : new boolean[] {true, false}) {
            String swap = exchange ? "swap=exchange" : "swap=copy";
            for (int[] window : new int[][] {{944, 581}, {1648, 989}, {1890, 989}, {765, 503}, {1007, 581}}) {
                int w = window[0];
                int h = window[1];
                String win = w + "x" + h;

                // 1. The Keurmeester's case: fixed + Stretched on, intro drawing only its left part; Stretched off -> 765x503.
                Sim c = start(exchange, true, w, h, true, 50);
                frames(c, INTRO_FIXED, 3);
                int changed = c.frame;
                c.layout(true, w, h, false, 50);
                frames(c, INTRO_FIXED, 4);
                checkScreen(c, changed, swap + " fixed " + win + ": Stretched on -> off during the intro");
                cases++;

                // 2. Stretched switched on during the intro: a new offscreen framebuffer (undefined contents).
                c = start(exchange, true, w, h, false, 50);
                frames(c, INTRO_FIXED, 3);
                changed = c.frame;
                c.layout(true, w, h, true, 50);
                frames(c, INTRO_FIXED, 4);
                checkScreen(c, changed, swap + " fixed " + win + ": Stretched off -> on during the intro");
                cases++;

                // 3. Resizable + stretched: every scaling step stores the renderbuffers again at the new real size.
                int max = StretchedMode.maxScalingPercent(w, h);
                c = start(exchange, false, w, h, true, 0);
                for (int p : new int[] {Math.min(5, max), Math.min(16, max), max / 2, max, 0}) {
                    changed = c.frame;
                    c.layout(false, w, h, true, p);
                    int[] part = {0, 0, StretchedMode.realWidth() * 2 / 3, StretchedMode.realHeight()};
                    frames(c, part, 4);
                    checkScreen(c, changed, swap + " resizable " + win + ": scaling -> " + p + "% (client draws 2/3)");
                    cases++;
                }

                // 4. Resizable: Stretched on -> off -> on (same window).
                c = start(exchange, false, w, h, true, 50);
                for (boolean on : new boolean[] {false, true, false}) {
                    changed = c.frame;
                    c.layout(false, w, h, on, 50);
                    int[] part = {0, 0, GameShell.canvasWid / 2, GameShell.canvasHei};
                    frames(c, part, 4);
                    checkScreen(c, changed, swap + " resizable " + win + ": Stretched -> " + (on ? "on" : "off") + " (client draws half)");
                    cases++;
                }

                // 5. Fixed <-> resizable with Stretched on during the intro.
                c = start(exchange, true, w, h, true, 50);
                frames(c, INTRO_FIXED, 2);
                changed = c.frame;
                c.layout(false, w, h, true, 50);
                frames(c, new int[] {0, 0, StretchedMode.realWidth() / 2, StretchedMode.realHeight()}, 4);
                checkScreen(c, changed, swap + " " + win + ": fixed -> resizable stretched");
                changed = c.frame;
                c.layout(true, w, h, true, 50);
                frames(c, INTRO_FIXED, 4);
                checkScreen(c, changed, swap + " " + win + ": resizable -> fixed stretched");
                cases += 2;

                // 6. A new GL context while stretched (Anti-aliasing / renderer change): new offscreen framebuffer.
                c = start(exchange, true, w, h, true, 50);
                frames(c, INTRO_FIXED, 2);
                changed = c.frame;
                c.newContext();
                frames(c, INTRO_FIXED, 4);
                checkScreen(c, changed, swap + " fixed " + win + ": new GL context while stretched");
                cases++;

                // 7. A2 round 7 part 2 (in game 04:08: one fully black maximised frame): a window-mode switch with Stretched OFF draws its
                //    first frame offscreen at the new size (InterfaceManager.prepareOffscreenCanvas -> GlToolkit.method7013), then the
                //    window and canvas take that size (Static712 -> resizeCanvas -> method7013 again) and the flip blits it. The frame
                //    in the offscreen buffer must survive that second sync: the screen after the flip shows the whole new frame.
                for (boolean toFixed : new boolean[] {false, true}) {
                    c = start(exchange, !toFixed, w, h, false, 50);
                    int tw = toFixed ? 765 : w;
                    int th = toFixed ? 503 : h;
                    StretchedMode.glPrerender(tw, th);
                    c.surfaceW = tw;
                    c.surfaceH = th;
                    StretchedMode.glSync(true, tw, th); // prepare: method7013 binds the offscreen buffer at the new size
                    c.frame++;
                    Buffer offscreen = c.gl.target(c.gl.drawFbo);
                    if (offscreen != null) {
                        offscreen.fill(0, 0, 4000, 4000, c.frame); // the client draws the whole first frame of the new mode
                    }
                    int drawn = c.frame;
                    GameShell.canvas.setSize(tw, th); // beforeFlip: the canvas takes the new size (Static712)
                    c.gl.resizeWindow(tw, th);
                    StretchedMode.glSync(true, StretchedMode.renderSize(GameShell.canvas).width, StretchedMode.renderSize(GameShell.canvas).height);
                    StretchedMode.glPresent(false, GameShell.canvas.getWidth(), GameShell.canvas.getHeight()); // the flip
                    c.gl.swap();
                    StretchedMode.glSync(true, tw, th);
                    int wrong = 0;
                    for (int v : c.gl.front.px) {
                        if (v != drawn) {
                            wrong++;
                        }
                    }
                    assertions++;
                    if (offscreen == null || wrong > 0) {
                        failures.add(swap + " " + win + ": Stretched OFF switch to " + (toFixed ? "fixed" : "resizable") + " - the screen after the flip of the offscreen first frame shows "
                            + wrong + " of " + c.gl.front.px.length + " px that are not that frame (" + (offscreen == null ? "no offscreen buffer" : "cleared before its flip") + ")");
                    }
                    // presented(): back to the window - the next frames draw into it again (and must not show anything older).
                    StretchedMode.glPrerenderEnd();
                    changed = c.frame;
                    c.layout(toFixed, w, h, false, 50);
                    c.changed = true;
                    frames(c, new int[] {0, 0, 4000, 4000}, 3);
                    checkScreen(c, changed, swap + " " + win + ": back to the window after the offscreen first frame (" + (toFixed ? "fixed" : "resizable") + ")");
                    cases++;
                }
            }
        }
        StretchedMode.useGlForTest(null);
        System.setOut(OUT);
        System.out.println("StretchedFrameRegressionTest: " + cases + " buffer changes, " + assertions + " checks");
        if (failures.isEmpty()) {
            System.out.println("all passed");
            return;
        }
        for (String failure : failures.subList(0, Math.min(60, failures.size()))) {
            System.out.println("FAIL " + failure);
        }
        System.out.println(failures.size() + " failed");
        System.exit(1);
    }

    /** A client that has drawn whole frames for a while in this state (the old picture the change must not leave behind). */
    static Sim start(boolean exchange, boolean fixed, int w, int h, boolean stretched, int p) {
        FakeGl gl = new FakeGl(exchange, w, h);
        StretchedMode.useGlForTest(gl);
        StretchedMode.configure(false, false, false, false, 50);
        Sim c = new Sim(gl);
        c.layout(fixed, w, h, stretched, p);
        frames(c, new int[] {0, 0, 4000, 4000}, 3);
        return c;
    }

    static void frames(Sim c, int[] region, int n) {
        for (int i = 0; i < n; i++) {
            c.frame(region);
        }
    }

    /** What the screen shows after the last frame: only black or pixels drawn after frame [changed]. */
    static void checkScreen(Sim c, int changed, String what) {
        if (!c.changed) {
            // the same layout as before (e.g. a 765x503 window cannot stretch): the client's own retained picture, nothing changed
            return;
        }
        Buffer screen = c.gl.front;
        int stale = 0;
        int undefined = 0;
        for (int v : screen.px) {
            if (v == UNDEFINED) {
                undefined++;
            } else if (v != BLACK && v <= changed) {
                stale++;
            }
        }
        assertions++;
        if (stale > 0 || undefined > 0) {
            failures.add(what + ": screen " + screen.width + "x" + screen.height + " shows " + stale + " px of an older frame and " + undefined
                + " px of undefined memory");
        }
    }
}
