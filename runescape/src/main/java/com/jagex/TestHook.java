package com.jagex;

import com.jagex.game.runetek6.client.GameShell;
import com.jagex.graphics.Toolkit;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Canvas;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Agent team 2026-10-14 (owner: "ik wil mijn computer kunnen gebruiken, youtube enzo"): a test client started with
 * {@code -Drsps.testHook=<port>} (tools\live\RestartClient.ps1) takes clicks, keys and screenshots over a local socket
 * instead of the real mouse, keyboard and screen. Input is dispatched straight to the AWT components, screenshots are read
 * back from the toolkit's own frame, so the window may sit behind the owner's other windows. Only the agent's test client
 * passes the property; the owner's and the players' clients never open the port.
 *
 * <p>One command per line, coordinates in the window's client area (the same ones tools\live\ClientDriver.ps1 Click-At
 * always used): {@code ping | move x y | click x y [left|right|middle] | press x y [b] | release x y [b] | wheel x y n |
 * keys <SendKeys text> | keydown vk|name | keyup vk|name | state | shot <png path>}. Every answer is one line, {@code ok ...} or {@code err ...}.
 */
public final class TestHook {

    /**
     * The test client plays no sound through the speakers (owner 2026-10-14: "ik hoor runescape geluiden"); the mixer, the
     * sound queue and [avtrace] run unchanged, only the finished samples are zeroed. {@code -Drsps.testSound=true} = audible.
     */
    public static final boolean SILENT = System.getProperty("rsps.testHook") != null && !Boolean.getBoolean("rsps.testSound");

    private static volatile CompletableFuture<int[]> frameRequest;

    /** "detail min|mid" waiting for the game thread ({@link #processPending}). */
    private static final java.util.concurrent.atomic.AtomicReference<CompletableFuture<String>> detailRequest =
        new java.util.concurrent.atomic.AtomicReference<>();

    private static volatile String detailWanted;

    private static int frameWidth;

    private static int frameHeight;

    private TestHook() {
    }

    public static void start() {
        final String value = System.getProperty("rsps.testHook");
        if (value == null || value.isEmpty()) {
            return;
        }
        final int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.out.println("[testhook] bad port " + value);
            return;
        }
        final Thread thread = new Thread(() -> serve(port), "rsps-testhook");
        thread.setDaemon(true);
        thread.start();
    }

    private static void serve(int port) {
        try (ServerSocket server = new ServerSocket(port, 4, InetAddress.getLoopbackAddress())) {
            System.out.println("[testhook] listening on 127.0.0.1:" + port);
            while (true) {
                try (Socket socket = server.accept();
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        String answer;
                        try {
                            answer = handle(line.trim());
                        } catch (Exception e) {
                            answer = "err " + e;
                        }
                        out.println(answer);
                    }
                } catch (Exception e) {
                    System.out.println("[testhook] connection: " + e);
                }
            }
        } catch (Exception e) {
            System.out.println("[testhook] cannot listen on " + port + ": " + e);
        }
    }

    private static String handle(String line) throws Exception {
        if (line.isEmpty()) {
            return "err empty";
        }
        final String[] a = line.split(" ", 2);
        final String cmd = a[0].toLowerCase();
        final String rest = a.length > 1 ? a[1] : "";
        final String[] p = rest.isEmpty() ? new String[0] : rest.trim().split("\\s+");
        final Canvas canvas = GameShell.canvas;
        if (canvas == null || !canvas.isShowing()) {
            return "err canvas not showing (window minimised?)";
        }
        // The game only draws the console caret and sends focus to the server when it thinks it has focus.
        GameShell.focus_in = true;
        // N620 view-only: every hook command keeps the 'alleen kijken' window alive for another viewMinutes.
        if (viewOnly) {
            viewUntil = System.currentTimeMillis() + viewMinutes * 60_000L;
        }
        switch (cmd) {
            case "view": {
                // N620 (owner 14-11 23:58: a click in the bedekte client walked teamtest1 out of the Porazdir fight): 'view on [min]'
                // drops the REAL mouse and keyboard on this window while a test script runs (hook input still works, it does not go
                // through the event queue); the title says so. Off again with 'view off', or by itself after [min] (default 30)
                // without any hook command - a crashed script never leaves the client deaf.
                final String v = p.length > 0 ? p[0].toLowerCase() : "";
                if (v.equals("on")) {
                    viewMinutes = p.length > 1 ? Math.max(1, Integer.parseInt(p[1])) : 30;
                    setView(true);
                } else if (v.equals("off")) {
                    setView(false);
                } else if (!v.isEmpty()) {
                    return "err view on [minutes] | off";
                }
                return "ok view " + (viewOnly ? "on (" + viewMinutes + " min)" : "off");
            }
            case "ping": {
                final Point o = canvasOffset(canvas);
                final Window w = SwingUtilities.getWindowAncestor(canvas);
                final Insets in = w.getInsets();
                return "ok canvas " + canvas.getWidth() + "x" + canvas.getHeight() + " at " + o.x + "," + o.y + " client "
                    + (w.getWidth() - in.left - in.right) + "x" + (w.getHeight() - in.top - in.bottom);
            }
            case "move":
                mouse(MouseEvent.MOUSE_MOVED, i(p, 0), i(p, 1), 0, 0);
                return "ok";
            case "click": {
                final int button = button(p, 2);
                final int x = i(p, 0);
                final int y = i(p, 1);
                mouse(MouseEvent.MOUSE_MOVED, x, y, 0, 0);
                Thread.sleep(40);
                mouse(MouseEvent.MOUSE_PRESSED, x, y, button, 1);
                Thread.sleep(60);
                mouse(MouseEvent.MOUSE_RELEASED, x, y, button, 1);
                mouse(MouseEvent.MOUSE_CLICKED, x, y, button, 1);
                return "ok";
            }
            case "press":
                mouse(MouseEvent.MOUSE_PRESSED, i(p, 0), i(p, 1), button(p, 2), 1);
                return "ok";
            case "release":
                mouse(MouseEvent.MOUSE_RELEASED, i(p, 0), i(p, 1), button(p, 2), 1);
                return "ok";
            case "drag":
                mouse(MouseEvent.MOUSE_DRAGGED, i(p, 0), i(p, 1), button(p, 2), 0);
                return "ok";
            case "wheel":
                wheel(i(p, 0), i(p, 1), i(p, 2));
                return "ok";
            case "keys":
                keys(rest);
                return "ok";
            case "keydown":
                key(KeyEvent.KEY_PRESSED, vk(p, 0), KeyEvent.CHAR_UNDEFINED, 0);
                return "ok";
            case "keyup":
                key(KeyEvent.KEY_RELEASED, vk(p, 0), KeyEvent.CHAR_UNDEFINED, 0);
                return "ok";
            case "postclick": {
                // N620 proof of 'view on': a click POSTED to the system event queue = the same road as a real OS mouse click (the
                // normal hook 'click' dispatches straight to the component and is never filtered).
                final Object[] t = target(i(p, 0), i(p, 1));
                final Component c = (Component) t[0];
                final Point at = (Point) t[1];
                final EventQueue q = java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue();
                final long now = System.currentTimeMillis();
                q.postEvent(new MouseEvent(c, MouseEvent.MOUSE_MOVED, now, 0, at.x, at.y, 0, false, 0));
                q.postEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED, now + 40, InputEvent.BUTTON1_DOWN_MASK, at.x, at.y, 1, false, MouseEvent.BUTTON1));
                q.postEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED, now + 100, 0, at.x, at.y, 1, false, MouseEvent.BUTTON1));
                q.postEvent(new MouseEvent(c, MouseEvent.MOUSE_CLICKED, now + 100, 0, at.x, at.y, 1, false, MouseEvent.BUTTON1));
                return "ok posted" + (viewOnly ? " (view on: should be dropped)" : "");
            }
            case "state":
                // N620 (15-11): where the client is, so a script waits for 'step=11 GAME_SCREEN' instead of guessing (after a cache
                // change the client loads ~6 min while the hook port is already open; login1 typing too early made a NEW account).
                return "ok state " + step() + " fps " + com.jagex.game.runetek6.client.GameShell.currentFps
                    + " canvas " + canvas.getWidth() + "x" + canvas.getHeight();
            case "detail": {
                // Owner 2026-10-14 ("trage laptop"): the test client plays on Min (the Graphics Options "Min" button, CPU
                // usage lowest) unless an item is about what players see; "mid" = the 78 default profile every player starts on.
                final String level = rest.trim().toLowerCase();
                if (!level.equals("min") && !level.equals("mid") && !level.equals("gl") && !level.equals("java")
                    && !level.equals("dx") && !level.equals("sse")) {
                    return "err detail min|mid|gl|java|dx|sse";
                }
                final CompletableFuture<String> done = new CompletableFuture<>();
                detailWanted = level;
                detailRequest.set(done);
                return done.get(15, TimeUnit.SECONDS);
            }
            case "shot":
                return shot(rest.trim());
            case "fps":
                // N462 skeletal performance check: the client's own frame counter (the same number 'displayfps' shows).
                return "ok fps " + com.jagex.game.runetek6.client.GameShell.currentFps;
            case "skeletal": {
                // N462 skeletal proof: switch the real skeletal player on/off to compare it with the baked classic frames.
                final String v = rest.trim().toLowerCase();
                if (v.equals("on") || v.equals("off")) {
                    com.jagex.graphics.skeletal.SkeletalAnimation.enabled = v.equals("on");
                }
                return "ok skeletal " + (com.jagex.graphics.skeletal.SkeletalAnimation.enabled ? "on" : "off");
            }
            default:
                return "err unknown command " + cmd;
        }
    }

    private static volatile boolean viewOnly;
    private static volatile long viewUntil;
    private static volatile int viewMinutes = 30;
    private static String viewTitle;
    private static boolean viewQueuePushed;

    /** Drops real (OS) mouse/keyboard events while [viewOnly]; hook events are dispatched directly and never pass here. */
    private static final class ViewOnlyQueue extends EventQueue {
        @Override
        protected void dispatchEvent(java.awt.AWTEvent event) {
            if (viewOnly && event instanceof InputEvent) {
                if (System.currentTimeMillis() > viewUntil) {
                    setView(false);
                } else {
                    ((InputEvent) event).consume();
                    return;
                }
            }
            super.dispatchEvent(event);
        }
    }

    private static synchronized void setView(boolean on) {
        if (on && !viewQueuePushed) {
            java.awt.Toolkit.getDefaultToolkit().getSystemEventQueue().push(new ViewOnlyQueue());
            viewQueuePushed = true;
        }
        viewUntil = System.currentTimeMillis() + viewMinutes * 60_000L;
        viewOnly = on;
        final Canvas canvas = GameShell.canvas;
        final Window w = canvas == null ? null : SwingUtilities.getWindowAncestor(canvas);
        if (w instanceof java.awt.Frame) {
            final java.awt.Frame f = (java.awt.Frame) w;
            EventQueue.invokeLater(() -> {
                if (on) {
                    if (viewTitle == null) {
                        viewTitle = f.getTitle();
                    }
                    f.setTitle("TESTSCRIPT LOOPT - niet klikken (alleen kijken) - " + viewTitle);
                } else if (viewTitle != null) {
                    f.setTitle(viewTitle);
                    viewTitle = null;
                }
            });
        }
    }

    /** A key code: a number (37) or a name (LEFT, F1, ENTER, A ... = KeyEvent.VK_<name>); 15-11 'keydown LEFT' was refused. */
    private static int vk(String[] p, int index) {
        if (index >= p.length) {
            throw new IllegalArgumentException("missing key");
        }
        final String k = p[index];
        if (k.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(k);
        }
        try {
            return KeyEvent.class.getField("VK_" + k.toUpperCase()).getInt(null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException("unknown key " + k + " (number or KeyEvent name: LEFT, F1, ENTER ...)");
        }
    }

    /** MainLogicManager.step (default package, so by reflection) with its MainLogicStep name. */
    private static String step() {
        try {
            final int step = Class.forName("MainLogicManager").getField("step").getInt(null);
            for (java.lang.reflect.Field f : com.jagex.core.constants.MainLogicStep.class.getFields()) {
                if (f.getType() == int.class && f.getInt(null) == step) {
                    return "step=" + step + " " + f.getName().replace("STEP_", "");
                }
            }
            return "step=" + step;
        } catch (Exception e) {
            return "step=? (" + e + ")";
        }
    }

    private static int i(String[] p, int index) {
        if (index >= p.length) {
            throw new IllegalArgumentException("missing argument " + (index + 1));
        }
        return Integer.parseInt(p[index]);
    }

    private static int button(String[] p, int index) {
        final String b = index < p.length ? p[index].toLowerCase() : "left";
        switch (b) {
            case "right":
                return MouseEvent.BUTTON3;
            case "middle":
                return MouseEvent.BUTTON2;
            default:
                return MouseEvent.BUTTON1;
        }
    }

    /** Where the game canvas sits inside the window's client area. */
    private static Point canvasOffset(Canvas canvas) {
        final Window w = SwingUtilities.getWindowAncestor(canvas);
        final Point c = canvas.getLocationOnScreen();
        final Point ws = w.getLocationOnScreen();
        final Insets in = w.getInsets();
        return new Point(c.x - ws.x - in.left, c.y - ws.y - in.top);
    }

    /** The component under client-area point x,y (the game canvas or a sidebar control) and the point in its own space. */
    private static Object[] target(int x, int y) {
        final Canvas canvas = GameShell.canvas;
        final Window w = SwingUtilities.getWindowAncestor(canvas);
        final Insets in = w.getInsets();
        final int wx = x + in.left;
        final int wy = y + in.top;
        Component target = SwingUtilities.getDeepestComponentAt(w, wx, wy);
        if (target == null) {
            target = canvas;
        }
        final Point local = SwingUtilities.convertPoint(w, wx, wy, target);
        return new Object[] {target, local};
    }

    private static void mouse(int id, int x, int y, int button, int clicks) throws Exception {
        final Object[] t = target(x, y);
        final Component c = (Component) t[0];
        final Point at = (Point) t[1];
        int mods = 0;
        if (button == MouseEvent.BUTTON1) {
            mods = InputEvent.BUTTON1_DOWN_MASK;
        } else if (button == MouseEvent.BUTTON2) {
            mods = InputEvent.BUTTON2_DOWN_MASK;
        } else if (button == MouseEvent.BUTTON3) {
            mods = InputEvent.BUTTON3_DOWN_MASK;
        }
        if (id == MouseEvent.MOUSE_MOVED || id == MouseEvent.MOUSE_RELEASED || id == MouseEvent.MOUSE_CLICKED) {
            mods = 0;
        }
        final MouseEvent e = new MouseEvent(c, id, System.currentTimeMillis(), mods, at.x, at.y, clicks,
            id == MouseEvent.MOUSE_PRESSED && button == MouseEvent.BUTTON3, button);
        EventQueue.invokeAndWait(() -> c.dispatchEvent(e));
    }

    private static void wheel(int x, int y, int notches) throws Exception {
        final Object[] t = target(x, y);
        final Component c = (Component) t[0];
        final Point at = (Point) t[1];
        final MouseWheelEvent e = new MouseWheelEvent(c, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), 0, at.x, at.y,
            0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3, notches);
        EventQueue.invokeAndWait(() -> c.dispatchEvent(e));
    }

    private static void key(int id, int code, char ch, int mods) throws Exception {
        final Canvas c = GameShell.canvas;
        final KeyEvent e = new KeyEvent(c, id, System.currentTimeMillis(), mods, id == KeyEvent.KEY_TYPED ? KeyEvent.VK_UNDEFINED : code, ch);
        EventQueue.invokeAndWait(() -> c.dispatchEvent(e));
    }

    private static void stroke(int code, char ch, int mods) throws Exception {
        key(KeyEvent.KEY_PRESSED, code, ch, mods);
        if (ch != KeyEvent.CHAR_UNDEFINED) {
            key(KeyEvent.KEY_TYPED, code, ch, mods);
        }
        Thread.sleep(25);
        key(KeyEvent.KEY_RELEASED, code, ch, mods);
        Thread.sleep(25);
    }

    /** The subset of System.Windows.Forms.SendKeys the tools\live scripts use: text, ~, {ENTER}, {BACKSPACE 40}, + ^ %. */
    private static void keys(String text) throws Exception {
        int mods = 0;
        for (int k = 0; k < text.length(); k++) {
            final char ch = text.charAt(k);
            if (ch == '+' || ch == '^' || ch == '%') {
                mods |= ch == '+' ? InputEvent.SHIFT_DOWN_MASK : ch == '^' ? InputEvent.CTRL_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
                continue;
            }
            if (ch == '~') {
                stroke(KeyEvent.VK_ENTER, '\n', mods);
            } else if (ch == '{') {
                // "{}}" is a literal brace, so the closing brace is searched from the second character on
                final int end = text.indexOf('}', k + 2);
                if (end < 0) {
                    throw new IllegalArgumentException("unclosed { in keys");
                }
                final String[] token = text.substring(k + 1, end).split(" ");
                final int times = token.length > 1 ? Integer.parseInt(token[1]) : 1;
                for (int n = 0; n < times; n++) {
                    special(token[0], mods);
                }
                k = end;
            } else {
                typeChar(ch, mods);
            }
            mods = 0;
        }
    }

    private static void special(String name, int mods) throws Exception {
        switch (name.toUpperCase()) {
            case "ENTER":
                stroke(KeyEvent.VK_ENTER, '\n', mods);
                return;
            case "ESC":
            case "ESCAPE":
                stroke(KeyEvent.VK_ESCAPE, (char) 27, mods);
                return;
            case "TAB":
                stroke(KeyEvent.VK_TAB, '\t', mods);
                return;
            case "BACKSPACE":
            case "BS":
            case "BKSP":
                stroke(KeyEvent.VK_BACK_SPACE, '\b', mods);
                return;
            case "DELETE":
            case "DEL":
                stroke(KeyEvent.VK_DELETE, (char) 127, mods);
                return;
            case "UP":
                stroke(KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED, mods);
                return;
            case "DOWN":
                stroke(KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED, mods);
                return;
            case "LEFT":
                stroke(KeyEvent.VK_LEFT, KeyEvent.CHAR_UNDEFINED, mods);
                return;
            case "RIGHT":
                stroke(KeyEvent.VK_RIGHT, KeyEvent.CHAR_UNDEFINED, mods);
                return;
            case "HOME":
                stroke(KeyEvent.VK_HOME, KeyEvent.CHAR_UNDEFINED, mods);
                return;
            case "END":
                stroke(KeyEvent.VK_END, KeyEvent.CHAR_UNDEFINED, mods);
                return;
            case "PGUP":
                stroke(KeyEvent.VK_PAGE_UP, KeyEvent.CHAR_UNDEFINED, mods);
                return;
            case "PGDN":
                stroke(KeyEvent.VK_PAGE_DOWN, KeyEvent.CHAR_UNDEFINED, mods);
                return;
            default:
                if (name.length() == 1) {
                    typeChar(name.charAt(0), mods);
                    return;
                }
                if (name.toUpperCase().matches("F([1-9]|1[0-2])")) {
                    stroke(KeyEvent.VK_F1 + Integer.parseInt(name.substring(1)) - 1, KeyEvent.CHAR_UNDEFINED, mods);
                    return;
                }
                throw new IllegalArgumentException("unknown key {" + name + "}");
        }
    }

    private static final String SHIFTED = "~!@#$%^&*()_+{}|:\"<>?";

    private static void typeChar(char ch, int mods) throws Exception {
        int code = KeyEvent.getExtendedKeyCodeForChar(ch);
        if (Character.isUpperCase(ch) || SHIFTED.indexOf(ch) >= 0) {
            mods |= InputEvent.SHIFT_DOWN_MASK;
        }
        if (ch == ' ') {
            code = KeyEvent.VK_SPACE;
        }
        stroke(code, ch, mods);
    }

    /** The detail level waiting for the game thread (RspsTestDetail.process), or null. */
    public static String pendingDetail() {
        return detailRequest.get() == null ? null : detailWanted;
    }

    public static void detailDone(String answer) {
        final CompletableFuture<String> done = detailRequest.getAndSet(null);
        if (done != null) {
            done.complete(answer);
        }
    }

    /**
     * Called by client.mainredraw on the render thread just before the frame is flipped: hands a waiting screenshot request
     * the finished frame straight from the toolkit (also OpenGL), so nothing on top of the window ends up in it.
     */
    public static void beforeFlip() {
        final CompletableFuture<int[]> request = frameRequest;
        if (request == null || request.isDone()) {
            return;
        }
        try {
            final Toolkit toolkit = Toolkit.active;
            final int w = StretchedMode.active() ? StretchedMode.realWidth() : GameShell.canvasWid;
            final int h = StretchedMode.active() ? StretchedMode.realHeight() : GameShell.canvasHei;
            frameWidth = w;
            frameHeight = h;
            request.complete(toolkit.na(0, 0, w, h));
        } catch (Throwable t) {
            request.completeExceptionally(t);
        }
    }

    private static String shot(String path) throws Exception {
        if (path.isEmpty()) {
            throw new IllegalArgumentException("shot needs a png path");
        }
        final CompletableFuture<int[]> request = new CompletableFuture<>();
        frameRequest = request;
        final int[] pixels;
        try {
            pixels = request.get(5, TimeUnit.SECONDS);
        } finally {
            frameRequest = null;
        }
        final Canvas canvas = GameShell.canvas;
        final Window w = SwingUtilities.getWindowAncestor(canvas);
        final Insets in = w.getInsets();
        final int cw = w.getWidth() - in.left - in.right;
        final int ch = w.getHeight() - in.top - in.bottom;

        final BufferedImage frame = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_RGB);
        frame.setRGB(0, 0, frameWidth, frameHeight, pixels, 0, frameWidth);

        // The whole client area, as the old screen capture took it: the window's Swing parts (78 Plugins sidebar) painted
        // by Swing, the game frame drawn over the canvas at the canvas's size (stretched like on screen).
        final BufferedImage image = new BufferedImage(Math.max(1, cw), Math.max(1, ch), BufferedImage.TYPE_INT_RGB);
        final Graphics2D g = image.createGraphics();
        EventQueue.invokeAndWait(() -> {
            g.translate(-in.left, -in.top);
            w.printAll(g);
            g.translate(in.left, in.top);
        });
        final Point o = canvasOffset(canvas);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (StretchedMode.windowCanvas()) {
            // A2 round 8: the canvas fills the window; the game frame sits in its drawn rectangle, the rest is black.
            g.setColor(java.awt.Color.BLACK);
            g.fillRect(o.x, o.y, canvas.getWidth(), canvas.getHeight());
            g.drawImage(frame, o.x + StretchedMode.drawX(), o.y + StretchedMode.drawY(), StretchedMode.stretchedWidth(), StretchedMode.stretchedHeight(), null);
        } else {
            g.drawImage(frame, o.x, o.y, canvas.getWidth(), canvas.getHeight(), null);
        }
        g.dispose();

        final File out = new File(path);
        if (out.getParentFile() != null) {
            out.getParentFile().mkdirs();
        }
        ImageIO.write(image, "png", out);
        return "ok " + out.getAbsolutePath() + " " + image.getWidth() + "x" + image.getHeight();
    }
}
