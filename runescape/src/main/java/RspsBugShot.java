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
 *
 * <p>Round 3 (Keurmeester FAIL round 2: 'bug A', 15 s later 'bug B' -> the server saved NEITHER picture, the client said
 * "sent" for both: the pieces carried no report, the server's newer request swallowed the rest of A). Every piece now names
 * its report stem ({@link #writeChunk}); the uploads go one after the other ({@link Uploads}); the server answers each report
 * with "&lt;stem&gt;|saved|&lt;detail&gt;" (after the PNG is on its disk) or "&lt;stem&gt;|refused|&lt;why&gt;" - a refused
 * report stops at once (sending, waiting, or not even made yet) and the others go on. The player sees only that answer,
 * never a "sent" of the client's own. Reports asked for before the next frame (two `bug` lines in one frame, a minimised
 * window) all wait for that frame instead of only the newest.
 *
 * <p>Round 4 (AUDIT T2-93: in the owner's 1874x970 window the server kicked the player mid-upload - its decoder could not
 * wait for the second byte of a piece's length, fixed on the server - and after the kick this client went on sending
 * teamtest2's PNG over teamtest3's new login, silently). Every request and upload now belongs to the game connection it was
 * asked on ({@link Request#session}, {@link Upload#session}); {@link #checkSession} runs every frame and drops what belongs to
 * a connection that is gone (kick, logout, reconnect, another account) and tells the player that the server did NOT get it
 * (the own copy stays in 78legacy\bugs). RuneLite does the same: an upload belongs to the session that made it.
 */
public final class RspsBugShot {

    /** Console marker from the server (gg.rsmod.plugins.content.cmd.BugShot.PREFIX). */
    public static final String PREFIX = "__RSPS_BUGSHOT__:";

    /** The server's report stems (BugShot.reportFile, BugShotTests): bug-yyyyMMdd-HHmmss[-n]-&lt;username&gt;. */
    static final Pattern STEM = Pattern.compile("bug-\\d{8}-\\d{6}(-\\d{1,3})?-[A-Za-z0-9 _-]{1,20}");

    /** Server BugShot.UPLOAD: the server takes the PNG of this report. */
    static final String UPLOAD = "|upload";

    /** Server BugShot.replyLine: "&lt;stem&gt;|saved|&lt;detail&gt;" / "&lt;stem&gt;|refused|&lt;why&gt;". */
    static final String SAVED = "saved";
    static final String REFUSED = "refused";

    /** Longest stem {@link #STEM} allows: bug-yyyyMMdd-HHmmss-nnn- (24) + 20 letters = 44 (server BugShotUploads.MAX_STEM 48). */
    static final int MAX_STEM = 44;

    /** Bytes of data per piece (server BugShotUploads.MAX_CHUNK = 1350). */
    static final int CHUNK = 1250;

    /**
     * The biggest piece on the wire: 1 opcode + 2 size + 1 stem length + {@link #MAX_STEM} + 8 header + {@link #CHUNK} = 1306.
     * A message must fit ServerConnection.writePacket (1350 bytes) whole - a bigger one is never flushed and stalls every
     * packet behind it.
     */
    static final int MAX_PIECE = 3 + 1 + MAX_STEM + 8 + CHUNK;

    /**
     * Free bytes the socket writer (ClientStreamWriter, 15000-byte ring) must have before a piece is added: the piece plus
     * room for the player's own packets. Without it the pieces filled the ring faster than the socket drained it and the
     * client dropped the connection ("Buffer overflow 1311 579", test server 2026-10-15 01:53).
     */
    static final int WRITER_RESERVE = MAX_PIECE + 6000;

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

    /** Lines for the player, printed in the dev console by the game thread (from the writer thread and the server's answers). */
    private static final ConcurrentLinkedQueue<String> results = new ConcurrentLinkedQueue<>();

    /**
     * A line from the server: a screenshot request (the report stem and whether the server takes the PNG) or the server's
     * answer about one report ({@link #reply} {@link #SAVED} / {@link #REFUSED} with its {@link #detail}).
     */
    static final class Request {
        final String stem;
        final boolean upload;
        final String reply;
        final String detail;
        /** The game connection this request came in on (set by {@link #request}); null in tests. */
        Object session;

        Request(String stem, boolean upload, String reply, String detail) {
            this.stem = stem;
            this.upload = upload;
            this.reply = reply;
            this.detail = detail;
        }
    }

    /** A finished PNG on its way to the server (sent by the game thread, see {@link #pump}). */
    static final class Upload {
        final String stem;
        final byte[] data;
        /** The game connection the server asked for it on: only that connection may carry its pieces. */
        final Object session;
        int offset;
        long started;
        long nextPieceAt;

        Upload(String stem, byte[] data, Object session) {
            this.stem = stem;
            this.data = data;
            this.session = session;
        }
    }

    /**
     * The PNGs on their way to the server, one after the other: the writer thread adds, the game thread sends ({@link #current},
     * {@link #advance}) and handles the server's refusals ({@link #refuse}). A refused report is never sent again, also when
     * its PNG is only made after the refusal.
     */
    static final class Uploads {
        private final ConcurrentLinkedQueue<Upload> waiting = new ConcurrentLinkedQueue<>();
        private final java.util.Set<String> refused = java.util.Collections.synchronizedSet(new java.util.LinkedHashSet<>());
        private Upload sending;

        /** Writer thread: a finished PNG; false when the server already refused this report. */
        boolean add(String stem, byte[] png) {
            return add(stem, png, null);
        }

        /** Writer thread: a finished PNG asked for on game connection [session]; false when the server already refused it. */
        boolean add(String stem, byte[] png, Object session) {
            if (refused.contains(stem)) {
                return false;
            }
            waiting.add(new Upload(stem, png, session));
            return true;
        }

        /**
         * Game thread: drops every upload (sending or waiting) that does not belong to game connection [live] - the connection
         * it was asked on is gone (kicked, logged out, reconnected, another account). Returns "stem offset/size" of each.
         */
        java.util.List<String> dropOtherSessions(Object live) {
            final java.util.List<String> dropped = new java.util.ArrayList<>();
            if (sending != null && sending.session != live) {
                dropped.add(sending.stem + " " + sending.offset + "/" + sending.data.length);
                sending = null;
            }
            for (java.util.Iterator<Upload> it = waiting.iterator(); it.hasNext(); ) {
                final Upload u = it.next();
                if (u.session != live) {
                    dropped.add(u.stem + " 0/" + u.data.length);
                    it.remove();
                }
            }
            return dropped;
        }

        /** Game thread: the upload whose next piece goes out, or null. */
        Upload current() {
            while (sending == null) {
                final Upload next = waiting.poll();
                if (next == null) {
                    return null;
                }
                if (!refused.contains(next.stem)) {
                    sending = next;
                }
            }
            return sending;
        }

        /** Game thread: [bytes] of [up] went out; true when that was its last piece. */
        boolean advance(Upload up, int bytes) {
            up.offset += bytes;
            if (up.offset >= up.data.length) {
                if (sending == up) {
                    sending = null;
                }
                return true;
            }
            return false;
        }

        /** Game thread, the server refused [stem]: stop it (sending, waiting or still to be made). True when it was stopped mid-way. */
        boolean refuse(String stem) {
            synchronized (refused) {
                refused.add(stem);
                while (refused.size() > 64) {
                    refused.remove(refused.iterator().next());
                }
            }
            waiting.removeIf(u -> u.stem.equals(stem));
            if (sending != null && sending.stem.equals(stem)) {
                sending = null;
                return true;
            }
            return false;
        }

        /** Game thread, the connection is gone: drops every upload; returns how many. */
        int dropAll() {
            int n = sending != null ? 1 : 0;
            sending = null;
            while (waiting.poll() != null) {
                n++;
            }
            return n;
        }
    }

    static final Uploads UPLOADS = new Uploads();

    /** Requests waiting for the next frame (all of them get it). */
    private static final ConcurrentLinkedQueue<Request> waitingShots = new ConcurrentLinkedQueue<>();

    private RspsBugShot() {
    }

    public static boolean accepts(String stem) {
        return stem != null && STEM.matcher(stem).matches();
    }

    /**
     * "&lt;stem&gt;", "&lt;stem&gt;|upload", "&lt;stem&gt;|saved|&lt;detail&gt;" or "&lt;stem&gt;|refused|&lt;why&gt;" from the
     * server; null for anything else (no paths, no other word).
     */
    static Request parse(String payload) {
        if (payload == null) {
            return null;
        }
        final int bar = payload.indexOf('|');
        final String stem = bar < 0 ? payload : payload.substring(0, bar);
        if (!accepts(stem)) {
            return null;
        }
        if (bar < 0) {
            return new Request(stem, false, null, null);
        }
        final String rest = payload.substring(bar + 1);
        if (rest.equals(UPLOAD.substring(1))) {
            return new Request(stem, true, null, null);
        }
        for (String word : new String[] {SAVED, REFUSED}) {
            if (rest.startsWith(word + "|")) {
                final String detail = rest.substring(word.length() + 1);
                return new Request(stem, false, word, detail.length() > 120 ? detail.substring(0, 120) : detail);
            }
        }
        return null;
    }

    /**
     * ServerConnectionReader (game thread), console line from the server: a request waits for the next frame (taken in
     * beforeConsole); the server's answer about a report is shown to the player, a refusal also stops that upload.
     */
    public static void request(String payload) {
        final Request request = parse(payload);
        if (request == null) {
            System.out.println("[bugshot] PROBLEM refused a line that is not about a bug report: " + payload);
            return;
        }
        if (SAVED.equals(request.reply)) {
            System.out.println("[bugshot] the server saved " + request.stem + ": " + request.detail);
            results.add("Bug screenshot saved on the server: " + request.detail);
        } else if (REFUSED.equals(request.reply)) {
            final boolean stopped = UPLOADS.refuse(request.stem);
            System.out.println("[bugshot] PROBLEM the server refused " + request.stem + ": " + request.detail + (stopped ? " - stopped sending it" : ""));
            results.add("Bug screenshot NOT saved on the server: " + request.detail + " (your copy is in 78legacy\\bugs).");
        } else {
            request.session = ServerConnection.GAME.connection;
            waitingShots.add(request);
        }
    }

    /**
     * Every frame (beforeConsole, game thread) and before every piece (pump): a report whose game connection is gone (kicked,
     * logged out, reconnected, another account logged in) is never sent over another connection - the server would refuse
     * it piece after piece and the new account's own report would wait behind it. It is dropped and the player is told once;
     * the own copy stays in 78legacy\bugs.
     */
    static void checkSession(Object live) {
        final java.util.List<String> dropped = UPLOADS.dropOtherSessions(live);
        for (java.util.Iterator<Request> it = waitingShots.iterator(); it.hasNext(); ) {
            final Request r = it.next();
            if (r.session != live) {
                dropped.add(r.stem + " (no picture yet)");
                it.remove();
            }
        }
        if (dropped.isEmpty()) {
            return;
        }
        System.out.println("[bugshot] PROBLEM the game connection " + (live == null ? "was lost" : "changed (new login)") + " - not sent to the server: " + String.join(", ", dropped));
        results.add("Bug screenshot NOT sent to the server: the connection was lost (" + dropped.size() + " report(s); your copy is in 78legacy\\bugs).");
    }

    /** Requests waiting for the next frame (tests). */
    static int waitingShots() {
        return waitingShots.size();
    }

    /** Takes the lines waiting for the dev console (tests). */
    static java.util.List<String> takeResults() {
        final java.util.List<String> out = new java.util.ArrayList<>();
        String line;
        while ((line = results.poll()) != null) {
            out.add(line);
        }
        return out;
    }

    /** Tests: forget every waiting request, line and upload. */
    static void clearForTests() {
        waitingShots.clear();
        results.clear();
        UPLOADS.dropAll();
    }

    /**
     * client.mainredraw, game thread, after the game and the interfaces are drawn and before the dev console: hands a waiting
     * request this frame. Only the pixel read-back happens here; the PNG is made on the writer thread.
     */
    public static void beforeConsole() {
        checkSession(ServerConnection.GAME.connection);
        String line;
        while ((line = results.poll()) != null) {
            debugconsole.addline(line);
        }
        if (waitingShots.isEmpty()) {
            return;
        }
        final java.util.List<Request> requests = new java.util.ArrayList<>();
        Request r;
        String last = null;
        while ((r = waitingShots.poll()) != null) {
            requests.add(r);
            last = r.stem;
        }
        final String stem = last;
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
        // every report asked for before this frame gets this frame
        for (Request request : requests) {
            WRITER.execute(() -> write(request, pixels, w, h, canvas));
        }
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
                if (UPLOADS.add(stem, png, request.session)) {
                    System.out.println("[bugshot] made " + stem + " " + size + " " + png.length + " bytes - sending it to the server (about " + uploadSeconds(png.length) + " s); own copy " + copy);
                    results.add("Bug screenshot made (" + kb(png.length) + " KB) - sending it to the server, about " + uploadSeconds(png.length) + " s. Keep the game open until it says saved.");
                } else {
                    System.out.println("[bugshot] made " + stem + " " + size + " - the server already refused it, not sent; own copy " + copy);
                }
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

    /** How long [bytes] take at the upload pace (one {@link #CHUNK} per {@link #PIECE_INTERVAL_MS}), rounded up. */
    static int uploadSeconds(int bytes) {
        final long pieces = (bytes + CHUNK - 1) / CHUNK;
        return (int) ((pieces * PIECE_INTERVAL_MS + 999) / 1000);
    }

    /**
     * MainLogicManager, game thread, every logic cycle right before ServerConnection.GAME.flush(): sends the next piece of a
     * finished screenshot - at the upload pace and only when no other packet waits, so the player's own packets never wait
     * behind a screenshot. A lost connection drops the uploads (the player keeps the local copies). The last piece is not
     * "sent" for the player: only the server's answer (saved / refused, see {@link #request}) is shown.
     */
    public static void pump() {
        final Upload up = UPLOADS.current();
        if (up == null) {
            return;
        }
        final ServerConnection connection = ServerConnection.GAME;
        if (connection.connection == null || connection.isaac == null || up.session != connection.connection) {
            // never a piece over another connection than the one the server asked on (checkSession tells the player)
            checkSession(connection.connection);
            return;
        }
        final long now = System.currentTimeMillis();
        if (!canSendPiece(connection.buffered, writerFree(connection.connection), now, up.nextPieceAt)) {
            return;
        }
        up.nextPieceAt = now + PIECE_INTERVAL_MS;
        final ClientMessage message = ClientMessage.create(ClientProt.RSPS_BUGSHOT_CHUNK, connection.isaac);
        if (up.offset == 0) {
            up.started = now;
            System.out.println("[bugshot] sending " + up.stem + ".png to the server: " + up.data.length + " bytes in pieces of " + CHUNK);
        }
        final int bytes = writeChunk(message.bitPacket, up.stem, up.data, up.offset);
        connection.send(message);
        if (UPLOADS.advance(up, bytes)) {
            System.out.println("[bugshot] all " + up.data.length + " bytes of " + up.stem + ".png handed to the connection in " + (now - up.started) + " ms - waiting for the server's answer");
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
     * One piece as the server reads it (BugShotChunkDecoder): p2 size (var-short), p1 stem length, the report [stem] (ASCII,
     * {@link #STEM} only), p4 total, p4 offset, then at most {@link #CHUNK} bytes of data from offset. Returns the number of
     * bytes of data written.
     */
    static int writeChunk(Packet packet, String stem, byte[] data, int offset) {
        final int len = Math.min(CHUNK, data.length - offset);
        final byte[] name = stem.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        packet.p2(0);
        final int start = packet.pos;
        packet.p1(name.length);
        packet.pdata(name.length, name, 0);
        packet.p4(data.length);
        packet.p4(offset);
        packet.pdata(len, data, offset);
        packet.psize2(packet.pos - start);
        return len;
    }
}
