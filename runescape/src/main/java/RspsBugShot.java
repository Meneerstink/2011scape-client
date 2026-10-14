import com.jagex.StretchedMode;
import com.jagex.game.runetek6.client.GameShell;
import com.jagex.graphics.Toolkit;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Canvas;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * T2 (owner 2026-10-13: "client maakt automatisch een screenshot bij elk bug-commando (bewijs bij de melding)").
 *
 * <p>Donor: RuneLite ScreenshotPlugin.takeScreenshot / saveScreenshot (runelite-client plugins/screenshot): on an event the
 * client asks for its own NEXT rendered frame (drawManager.requestNextFrameListener), an executor composes it with the client
 * frame and writes the PNG off the game thread, named after the event. Here the event is the server's console line
 * "__RSPS_BUGSHOT__:&lt;report stem&gt;" (server BugShot, sent once after every `bug` report). The frame is read back from the
 * toolkit itself (Toolkit.na - also OpenGL/DirectX, as com.jagex.TestHook does), never a screen grab, so another window on top
 * or a covered client never ends up in the report. It is taken in mainredraw after the game and the interfaces and BEFORE
 * the dev console is drawn: the player types `bug` in the console, the shot shows the game behind it.
 *
 * <p>Saved as &lt;stem&gt;.png in C:\RSPS\bugs (the server PC: next to the report) when that folder exists, else in the
 * player's own &lt;home&gt;\78legacy\bugs. Only stems the server makes are accepted (no paths, no extensions); a shot never
 * overwrites another file. Regression: :runescape:bugShotRegression.
 */
public final class RspsBugShot {

    /** Console marker from the server (gg.rsmod.plugins.content.cmd.BugShot.PREFIX). */
    public static final String PREFIX = "__RSPS_BUGSHOT__:";

    /** The server's report stems (BugShot.reportFile, BugShotTests): bug-yyyyMMdd-HHmmss[-n]-&lt;username&gt;. */
    static final Pattern STEM = Pattern.compile("bug-\\d{8}-\\d{6}(-\\d{1,3})?-[A-Za-z0-9 _-]{1,20}");

    private static final File SERVER_BUGS = new File("C:/RSPS/bugs");

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "rsps-bugshot");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Results from the writer thread, printed in the dev console by the game thread. */
    private static final ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();

    private static volatile String pending;

    private RspsBugShot() {
    }

    public static boolean accepts(String stem) {
        return stem != null && STEM.matcher(stem).matches();
    }

    /** ServerConnectionReader (game thread), console line from the server: the next frame is taken in beforeConsole. */
    public static void request(String stem) {
        if (!accepts(stem)) {
            System.out.println("[bugshot] PROBLEM refused a stem that is not a bug report: " + stem);
            return;
        }
        pending = stem;
    }

    /**
     * client.mainredraw, game thread, after the game and the interfaces are drawn and before the dev console: hands a waiting
     * request this frame. Only the pixel read-back happens here; the PNG is made on the writer thread.
     */
    public static void beforeConsole() {
        String line;
        while ((line = results.poll()) != null) {
            debugconsole.addline(line);
        }
        final String stem = pending;
        if (stem == null) {
            return;
        }
        pending = null;
        final Canvas canvas = GameShell.canvas;
        final int w = StretchedMode.active() ? StretchedMode.realWidth() : GameShell.canvasWid;
        final int h = StretchedMode.active() ? StretchedMode.realHeight() : GameShell.canvasHei;
        final int[] pixels;
        try {
            pixels = Toolkit.active.na(0, 0, w, h);
        } catch (Throwable t) {
            fail(stem, "the frame could not be read (" + t + ")");
            return;
        }
        if (pixels == null || pixels.length < w * h || canvas == null) {
            fail(stem, "no frame (" + (pixels == null ? "null" : pixels.length + " px") + " for " + w + "x" + h + ")");
            return;
        }
        WRITER.execute(() -> write(stem, pixels, w, h, canvas));
    }

    private static void fail(String stem, String why) {
        System.out.println("[bugshot] PROBLEM " + stem + ": " + why);
        results.add("Bug screenshot failed: " + why);
    }

    /** Writer thread: the whole client area like the hook's screenshot (78 Plugins sidebar by Swing, the frame on the canvas). */
    private static void write(String stem, int[] pixels, int w, int h, Canvas canvas) {
        try {
            final Window window = SwingUtilities.getWindowAncestor(canvas);
            final BufferedImage area;
            final Rectangle at;
            if (window != null && window.isShowing() && canvas.isShowing()) {
                final Insets in = window.getInsets();
                final int cw = Math.max(1, window.getWidth() - in.left - in.right);
                final int ch = Math.max(1, window.getHeight() - in.top - in.bottom);
                area = new BufferedImage(cw, ch, BufferedImage.TYPE_INT_RGB);
                final Graphics2D g = area.createGraphics();
                try {
                    EventQueue.invokeAndWait(() -> {
                        g.translate(-in.left, -in.top);
                        window.printAll(g);
                    });
                } finally {
                    g.dispose();
                }
                final Point c = canvas.getLocationOnScreen();
                final Point ws = window.getLocationOnScreen();
                at = new Rectangle(c.x - ws.x - in.left, c.y - ws.y - in.top, canvas.getWidth(), canvas.getHeight());
            } else {
                area = new BufferedImage(Math.max(1, canvas.getWidth()), Math.max(1, canvas.getHeight()), BufferedImage.TYPE_INT_RGB);
                at = new Rectangle(0, 0, area.getWidth(), area.getHeight());
            }
            final File out = save(compose(pixels, w, h, area, at), targetDir(SERVER_BUGS, new File(System.getProperty("user.home"))), stem);
            System.out.println("[bugshot] saved " + out.getAbsolutePath() + " " + area.getWidth() + "x" + area.getHeight() + " frame " + w + "x" + h
                + (StretchedMode.active() ? " stretched" : ""));
            results.add("Bug screenshot saved: " + out.getAbsolutePath());
        } catch (Throwable t) {
            fail(stem, t.toString());
        }
    }

    /** Draws the game frame over the canvas's place in the client area, at the canvas's size (stretched like on screen). */
    static BufferedImage compose(int[] pixels, int frameWidth, int frameHeight, BufferedImage area, Rectangle canvas) {
        final BufferedImage frame = new BufferedImage(frameWidth, frameHeight, BufferedImage.TYPE_INT_RGB);
        frame.setRGB(0, 0, frameWidth, frameHeight, pixels, 0, frameWidth);
        final Graphics2D g = area.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(frame, canvas.x, canvas.y, canvas.width, canvas.height, null);
        } finally {
            g.dispose();
        }
        return area;
    }

    /** C:\RSPS\bugs on the server PC (next to the report), else the player's own folder. */
    static File targetDir(File serverBugs, File home) {
        return serverBugs.isDirectory() ? serverBugs : new File(home, "78legacy/bugs");
    }

    /** Writes &lt;stem&gt;.png; when that file already exists &lt;stem&gt;-shot2.png, -shot3 ... - never overwrites. */
    static File save(BufferedImage image, File dir, String stem) throws java.io.IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new java.io.IOException("cannot make " + dir);
        }
        File out = new File(dir, stem + ".png");
        for (int n = 2; out.exists(); n++) {
            out = new File(dir, stem + "-shot" + n + ".png");
        }
        if (!ImageIO.write(image, "png", out)) {
            throw new java.io.IOException("no png writer");
        }
        return out;
    }
}
