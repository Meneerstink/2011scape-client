import com.jagex.ClientProt;
import com.jagex.StretchedMode;
import com.jagex.core.io.Packet;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
 * <p>Round 2 (Keurmeester FAIL round 1: the PNG stayed on the reporter's PC - friends play from other PCs, the server moves to
 * a VPS). Donor: RuneLite ImageCapture.uploadScreenshot - after the event the client sends its screenshot to a server. When
 * the server asks with "&lt;stem&gt;|upload" (it opened the upload for that report, server BugShotUploads) the PNG goes to the
 * server over the game connection: {@link #pump} sends one piece of {@link #CHUNK} bytes per logic cycle (ClientProt
 * RSPS_BUGSHOT_CHUNK, opcode 94), only when no other packet waits, so the player's own clicks never queue behind it; the
 * server writes bugs\&lt;stem&gt;.png next to the report. The player keeps a copy in &lt;home&gt;\78legacy\bugs. Without
 * "|upload" (older server, hourly cap) only the local copy is made, as in round 1: C:\RSPS\bugs when that folder exists,
 * else &lt;home&gt;\78legacy\bugs. Only stems the server makes are accepted (no paths, no extensions); a shot never
 * overwrites another file. Regression: :runescape:bugShotRegression.
 */
public final class RspsBugShot {

    /** Console marker from the server (gg.rsmod.plugins.content.cmd.BugShot.PREFIX). */
    public static final String PREFIX = "__RSPS_BUGSHOT__:";

    /** The server's report stems (BugShot.reportFile, BugShotTests): bug-yyyyMMdd-HHmmss[-n]-&lt;username&gt;. */
    static final Pattern STEM = Pattern.compile("bug-\\d{8}-\\d{6}(-\\d{1,3})?-[A-Za-z0-9 _-]{1,20}");

    /** Server BugShot.UPLOAD: the server takes the PNG of this report. */
    static final String UPLOAD = "|upload";

    /**
     * Bytes per piece. A message must fit ServerConnection.writePacket (1350 bytes) whole - a bigger one is never flushed and
     * stalls every packet behind it: 1 opcode + 2 size + 8 header + 1300 = 1311. Server BugShotUploads.MAX_CHUNK = 1350.
     */
    static final int CHUNK = 1300;

    /**
     * Free bytes the socket writer (ClientStreamWriter, 15000-byte ring) must have before a piece is added: the piece plus
     * room for the player's own packets. Without it the pieces filled the ring faster than the socket drained it and the
     * client dropped the connection ("Buffer overflow 1311 579", test server 2026-10-15 01:53).
     */
    static final int WRITER_RESERVE = CHUNK + 11 + 6000;

    /**
     * One piece per 85 ms = at most 15.6 KB/s (server BugShotUploads.CLIENT_RATE 16 KB/s): half of what the server reads per
     * player per second (ClientChannelInitializer.CHANNEL_READ_LIMIT, 32 KB/s), so the player's own packets are never read late.
     * At the old 5 KB/s server limit and one piece per logic cycle the reads were throttled and the ring overflowed.
     */
    static final int PIECE_INTERVAL_MS = 85;

    private static final File SERVER_BUGS = new File("C:/RSPS/bugs");

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "rsps-bugshot");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** Results from the writer thread, printed in the dev console by the game thread. */
    private static final ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();

    /** A server request: the report stem and whether the server takes the PNG. */
    static final class Request {
        final String stem;
        final boolean upload;

        Request(String stem, boolean upload) {
            this.stem = stem;
            this.upload = upload;
        }
    }

    /** A finished PNG on its way to the server (sent by the game thread, see {@link #pump}). */
    private static final class Upload {
        final String stem;
        final byte[] data;
        int offset;
        long started;
        long nextPieceAt;

        Upload(String stem, byte[] data) {
            this.stem = stem;
            this.data = data;
        }
    }

    private static volatile Request pending;

    /** Made by the writer thread, sent by the game thread. */
    private static final ConcurrentLinkedQueue<Upload> outgoing = new ConcurrentLinkedQueue<>();

    /** Game thread only. */
    private static Upload sending;

    private RspsBugShot() {
    }

    public static boolean accepts(String stem) {
        return stem != null && STEM.matcher(stem).matches();
    }

    /** "&lt;stem&gt;" or "&lt;stem&gt;|upload" from the server; null for anything else (no paths, no other suffix). */
    static Request parse(String payload) {
        if (payload == null) {
            return null;
        }
        final boolean upload = payload.endsWith(UPLOAD);
        final String stem = upload ? payload.substring(0, payload.length() - UPLOAD.length()) : payload;
        return accepts(stem) ? new Request(stem, upload) : null;
    }

    /** ServerConnectionReader (game thread), console line from the server: the next frame is taken in beforeConsole. */
    public static void request(String payload) {
        final Request request = parse(payload);
        if (request == null) {
            System.out.println("[bugshot] PROBLEM refused a stem that is not a bug report: " + payload);
            return;
        }
        pending = request;
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
        final Request request = pending;
        if (request == null) {
            return;
        }
        pending = null;
        final String stem = request.stem;
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
        WRITER.execute(() -> write(request, pixels, w, h, canvas));
    }

    private static void fail(String stem, String why) {
        System.out.println("[bugshot] PROBLEM " + stem + ": " + why);
        results.add("Bug screenshot failed: " + why);
    }

    /** Writer thread: the whole client area like the hook's screenshot (78 Plugins sidebar by Swing, the frame on the canvas). */
    private static void write(Request request, int[] pixels, int w, int h, Canvas canvas) {
        final String stem = request.stem;
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
            final byte[] png = encode(compose(pixels, w, h, area, at));
            final File home = new File(System.getProperty("user.home"));
            final String size = area.getWidth() + "x" + area.getHeight() + " frame " + w + "x" + h + (StretchedMode.active() ? " stretched" : "");
            if (request.upload) {
                // the server's copy is the one that counts; the player's own copy must never stop it
                String copy;
                try {
                    copy = saveBytes(png, ownDir(home), stem).getAbsolutePath();
                } catch (Throwable t) {
                    copy = "none (" + t + ")";
                }
                outgoing.add(new Upload(stem, png));
                System.out.println("[bugshot] made " + stem + " " + size + " " + png.length + " bytes - sending it to the server; own copy " + copy);
                results.add("Bug screenshot made (" + kb(png.length) + " KB) - sending it to the server...");
            } else {
                final File out = saveBytes(png, targetDir(SERVER_BUGS, home), stem);
                System.out.println("[bugshot] saved " + out.getAbsolutePath() + " " + size);
                results.add("Bug screenshot saved: " + out.getAbsolutePath());
            }
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

    /** Without an upload (older server, hourly cap): C:\RSPS\bugs on the server PC (next to the report), else the player's own folder. */
    static File targetDir(File serverBugs, File home) {
        return serverBugs.isDirectory() ? serverBugs : ownDir(home);
    }

    /** The player's own copy: &lt;home&gt;\78legacy\bugs (with an upload the server writes bugs\, never this client). */
    static File ownDir(File home) {
        return new File(home, "78legacy/bugs");
    }

    static byte[] encode(BufferedImage image) throws IOException {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(1 << 20);
        if (!ImageIO.write(image, "png", bytes)) {
            throw new IOException("no png writer");
        }
        return bytes.toByteArray();
    }

    /** Writes &lt;stem&gt;.png; when that file already exists &lt;stem&gt;-shot2.png, -shot3 ... - never overwrites. */
    static File save(BufferedImage image, File dir, String stem) throws IOException {
        return saveBytes(encode(image), dir, stem);
    }

    static File saveBytes(byte[] png, File dir, String stem) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot make " + dir);
        }
        File out = new File(dir, stem + ".png");
        for (int n = 2; out.exists(); n++) {
            out = new File(dir, stem + "-shot" + n + ".png");
        }
        Files.write(out.toPath(), png);
        return out;
    }

    private static int kb(int bytes) {
        return (bytes + 1023) / 1024;
    }

    /**
     * MainLogicManager, game thread, every logic cycle right before ServerConnection.GAME.flush(): sends the next piece of a
     * finished screenshot - one piece per cycle and only when no other packet waits, so the player's own packets never wait
     * behind a screenshot (~65 KB/s). A lost connection drops the upload (the player keeps the local copy).
     */
    public static void pump() {
        Upload up = sending;
        if (up == null) {
            up = outgoing.poll();
            if (up == null) {
                return;
            }
            sending = up;
        }
        final ServerConnection connection = ServerConnection.GAME;
        if (connection.connection == null || connection.isaac == null) {
            sending = null;
            outgoing.clear();
            System.out.println("[bugshot] PROBLEM " + up.stem + ": connection lost after " + up.offset + "/" + up.data.length + " bytes - not sent");
            debugconsole.addline("Bug screenshot not sent: the connection was lost (your copy is in 78legacy\\bugs).");
            return;
        }
        final long now = System.currentTimeMillis();
        if (!canSendPiece(connection.buffered, writerFree(connection.connection), now, up.nextPieceAt)) {
            return;
        }
        up.nextPieceAt = now + PIECE_INTERVAL_MS;
        final ClientMessage message = ClientMessage.create(ClientProt.RSPS_BUGSHOT_CHUNK, connection.isaac);
        if (up.offset == 0) {
            up.started = System.currentTimeMillis();
            System.out.println("[bugshot] sending " + up.stem + ".png to the server: " + up.data.length + " bytes in pieces of " + CHUNK);
        }
        up.offset += writeChunk(message.bitPacket, up.data, up.offset);
        connection.send(message);
        if (up.offset >= up.data.length) {
            sending = null;
            System.out.println("[bugshot] sent " + up.stem + ".png to the server: " + up.data.length + " bytes in " + (System.currentTimeMillis() - up.started) + " ms");
            debugconsole.addline("Bug screenshot sent to the server (" + kb(up.data.length) + " KB).");
        }
    }

    /**
     * A piece goes out only at the upload pace, when no other packet waits and when the socket writer has room for it plus
     * the player's own packets.
     */
    static boolean canSendPiece(int buffered, int writerFree, long now, long notBefore) {
        return now >= notBefore && buffered == 0 && writerFree >= WRITER_RESERVE;
    }

    /** Free space in the game socket's write ring; a connection without one (not the async writer) never blocks a piece. */
    static int writerFree(com.jagex.core.io.connection.Connection connection) {
        if (connection instanceof com.jagex.core.io.connection.AsyncDuplexConnection) {
            return ((com.jagex.core.io.connection.AsyncDuplexConnection) connection).writer.free();
        }
        return Integer.MAX_VALUE;
    }

    /**
     * One piece as the server reads it (BugShotChunkDecoder): p2 size (var-short), p4 total, p4 offset, then at most
     * {@link #CHUNK} bytes of data from offset. Returns the number of bytes of data written.
     */
    static int writeChunk(Packet packet, byte[] data, int offset) {
        final int len = Math.min(CHUNK, data.length - offset);
        packet.p2(0);
        final int start = packet.pos;
        packet.p4(data.length);
        packet.p4(offset);
        packet.pdata(len, data, offset);
        packet.psize2(packet.pos - start);
        return len;
    }
}
