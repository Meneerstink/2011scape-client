import javax.imageio.ImageIO;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * T2 (owner 2026-10-13: "client maakt automatisch een screenshot bij elk bug-commando"). After `bug <text>` the server sends
 * one console line "__RSPS_BUGSHOT__:<report stem>" (server BugShot, BugShotTests); RspsBugShot saves the client's own next
 * frame as <stem>.png - RuneLite's ScreenshotPlugin route (drawManager.requestNextFrameListener -> executor -> file), never a
 * screen grab, so a covered window or the open dev console never ends up in the report. The class covered:
 * - every stem the server makes is accepted, anything that could be a path is refused (the server names the file, not a path);
 * - the frame is composed into the client area at the canvas's place and size: fixed, resizable, Stretched Mode (scaled up),
 *   with the 78 Plugins sidebar beside it;
 * - C:\RSPS\bugs (server PC, next to the report) when it exists, else the player's own folder; a shot never overwrites one;
 * - wiring: the console prefix reaches RspsBugShot, the frame is taken in mainredraw BEFORE the dev console is drawn, and
 *   the game thread never waits (no file / Swing work in beforeConsole).
 * Round 2 (Keurmeester FAIL round 1: the PNG stayed on the reporter's PC; friends / VPS): the PNG goes to the server.
 * - "<stem>|upload" = the server takes it, "<stem>" = local only, any other suffix refused;
 * - every PNG size is cut into pieces that each fit the client's 1350-byte write buffer, in the exact layout the server
 *   decodes (var-short size, p4 total, p4 offset, bytes) and put back together byte-exact;
 * - with an upload the client never writes C:\RSPS\bugs itself (the server does), its own copy goes to 78legacy\bugs;
 * - pump runs in the logic cycle right before the flush, sends only when no other packet waits, never blocks; opcode 94 is
 *   var-short on both sides (server data/packets.yml, BugShot.UPLOAD).
 * Round 3 (Keurmeester FAIL round 2: 'bug A', 15 s later 'bug B' -> no PNG for either on the server, the client said "sent"
 * for both): every piece names its report stem; uploads go one after the other and a piece never carries another report's
 * stem; the server answers per report "<stem>|saved|<detail>" / "<stem>|refused|<why>" - refused stops that upload (sending or
 * queued, even before the PNG is made) without touching the others; the player is told only what the server said (no
 * "sent" of the client's own); reports asked for before the next frame each get that frame.
 * Round 4 (AUDIT T2-93: after a kick in the owner's window the client went on sending teamtest2's PNG over teamtest3's new
 * login, silently, and teamtest3's own report waited 4.5 minutes behind it): every request and upload belongs to the game
 * connection it was asked on; a lost or new connection (kick, logout, reconnect, another account) drops them - at every
 * moment: waiting for the frame, waiting in the queue, half sent - never a piece over another connection, the player is
 * told once, the new session's own reports go first and whole; the player hears how long the upload takes.
 */
public final class BugShotRegressionTest {
    private static int assertions;

    private static final List<String> failures = new ArrayList<>();

    private static final int SIDEBAR = 0x2B2B2B;

    public static void main(String[] args) throws Exception {
        stems();
        compose(765, 503, new Rectangle(0, 0, 765, 503), 795, 503, "fixed 765x503 + sidebar");
        compose(1000, 700, new Rectangle(0, 0, 1000, 700), 1030, 700, "resizable 1000x700 + sidebar");
        compose(765, 503, new Rectangle(0, 0, 1530, 1006), 1570, 1006, "Stretched fixed x2 + sidebar");
        compose(765, 503, new Rectangle(117, 0, 1148, 755), 1382, 755, "Stretched keep-aspect, centred");
        compose(1366, 768, new Rectangle(0, 0, 1366, 768), 1366, 768, "fullscreen, no sidebar");
        folders();
        requests();
        chunks();
        queue();
        replies();
        stalledSocket();
        pace();
        wiring();
        uploadWiring();
        sessions();
        if (!failures.isEmpty()) {
            failures.forEach(f -> System.out.println("FAILED: " + f));
            throw new AssertionError(failures.size() + " of " + assertions + " bug screenshot checks failed");
        }
        System.out.println("Bug screenshot regression checks passed: " + assertions);
    }

    private static void stems() {
        for (String ok : new String[] {"bug-20261014-234610-teamtest1", "bug-20261014-234610-2-teamtest1", "bug-20261014-234610-mod ash",
            "bug-20261014-234610-a", "bug-20261014-234610-abcdefghijkl", "bug-20261014-234610-pk3r_99", "bug-20261014-234610-12-Zezima-2"}) {
            check(RspsBugShot.accepts(ok), "server stem refused: " + ok);
        }
        for (String bad : new String[] {null, "", "x", "../bug-20261014-234610-a", "bug-20261014-234610-..\\..\\evil", "C:\\Windows\\x",
            "bug-20261014-234610-a/b", "bug-20261014-234610-a\nb", "bug-20261014-234610-a.png", "bug-2026-1014-a", "shot-20261014-234610-a",
            "bug-20261014-234610-abcdefghijklmnopqrstuvwxyz", "bug-20261014-234610-a:b", "bug-20261014-234610-"}) {
            check(!RspsBugShot.accepts(bad), "not a server stem, must be refused: " + bad);
        }
    }

    /** A frame with a distinct colour per pixel row/column and a sidebar-coloured client area. */
    private static void compose(int fw, int fh, Rectangle canvas, int aw, int ah, String mode) {
        final int[] frame = new int[fw * fh];
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < fw; x++) {
                frame[y * fw + x] = 0xFF000000 | (x * 255 / fw) << 16 | (y * 255 / fh) << 8 | 0x80;
            }
        }
        final BufferedImage area = new BufferedImage(aw, ah, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < ah; y++) {
            for (int x = 0; x < aw; x++) {
                area.setRGB(x, y, SIDEBAR);
            }
        }
        final BufferedImage out;
        try {
            out = RspsBugShot.compose(frame, fw, fh, area, canvas);
        } catch (Throwable t) {
            check(false, mode + ": compose threw " + t);
            return;
        }
        if (!check(out != null && out.getWidth() == aw && out.getHeight() == ah, mode + ": image is the client area " + aw + "x" + ah)) {
            return;
        }
        // the frame's corners land on the canvas's corners (scaled with it), the sidebar stays
        checkColour(out, canvas.x + 2, canvas.y + 2, frame[0], mode + " top-left");
        checkColour(out, canvas.x + canvas.width - 2, canvas.y + canvas.height - 2, frame[(fh - 1) * fw + fw - 1], mode + " bottom-right");
        checkColour(out, canvas.x + canvas.width / 2, canvas.y + canvas.height / 2, frame[(fh / 2) * fw + fw / 2], mode + " centre");
        if (aw > canvas.x + canvas.width + 5) {
            checkColour(out, aw - 3, ah / 2, SIDEBAR, mode + " sidebar beside the game");
        }
        if (canvas.x > 5) {
            checkColour(out, 2, 2, SIDEBAR, mode + " left border");
        }
    }

    private static void checkColour(BufferedImage image, int x, int y, int want, String what) {
        final int got = image.getRGB(x, y) & 0xFFFFFF;
        final int w = want & 0xFFFFFF;
        final boolean near = Math.abs((got >> 16 & 255) - (w >> 16 & 255)) <= 4 && Math.abs((got >> 8 & 255) - (w >> 8 & 255)) <= 4
            && Math.abs((got & 255) - (w & 255)) <= 4;
        check(near, what + " at " + x + "," + y + ": " + Integer.toHexString(got) + " want " + Integer.toHexString(w));
    }

    private static void folders() throws Exception {
        final File root = Files.createTempDirectory("bugshot").toFile();
        try {
            final File server = new File(root, "RSPS/bugs");
            final File home = new File(root, "home");
            check(new File(home, "78legacy/bugs").equals(RspsBugShot.targetDir(server, home)), "no server bugs folder -> the player's own 78legacy\\bugs");
            check(server.mkdirs(), "test setup");
            check(server.equals(RspsBugShot.targetDir(server, home)), "server PC: next to the report in bugs\\");

            final BufferedImage a = new BufferedImage(795, 503, BufferedImage.TYPE_INT_RGB);
            a.setRGB(1, 1, 0x123456);
            final File first = RspsBugShot.save(a, server, "bug-20261014-234610-teamtest1");
            if (check(first != null && first.isFile(), "shot written")) {
                check(first.getName().equals("bug-20261014-234610-teamtest1.png") && first.getParentFile().equals(server), "named after the report: " + first);
                final BufferedImage back = ImageIO.read(first);
                check(back != null && back.getWidth() == 795 && back.getHeight() == 503 && (back.getRGB(1, 1) & 0xFFFFFF) == 0x123456, "PNG reads back");
                final long size = first.length();
                final File second = RspsBugShot.save(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB), server, "bug-20261014-234610-teamtest1");
                check(second != null && second.isFile() && !second.equals(first), "a second shot for the same stem gets its own file: " + second);
                check(first.length() == size && ImageIO.read(first).getWidth() == 795, "the first shot is never overwritten");
            }
            final File own = RspsBugShot.save(a, RspsBugShot.targetDir(new File(root, "nope"), home), "bug-20261014-234611-mod ash");
            check(own != null && own.isFile() && own.getParentFile().equals(new File(home, "78legacy/bugs")), "player PC: folder made, shot saved: " + own);
        } finally {
            deleteAll(root);
        }
    }

    private static void wiring() throws Exception {
        final String reader = read("src/main/java/ServerConnectionReader.java");
        check(reader.contains("message.startsWith(RspsBugShot.PREFIX)") && reader.contains("RspsBugShot.request(message.substring(RspsBugShot.PREFIX.length()))"),
            "ServerConnectionReader sends the __RSPS_BUGSHOT__ console line to RspsBugShot.request");
        final String client = read("src/main/java/client.java");
        final int shot = client.indexOf("RspsBugShot.beforeConsole();");
        final int console = client.indexOf("debugconsole.draw(Toolkit.active);");
        check(shot > 0 && console > 0 && shot < console && client.substring(shot, console).contains("if (debugconsole.isOpen())")
            && !client.substring(shot, console).contains("drawInterface"), "mainredraw takes the frame after the game + interfaces, before the dev console");
        final String bugShot = read("src/main/java/RspsBugShot.java");
        check(bugShot.contains("PREFIX = \"__RSPS_BUGSHOT__:\""), "prefix matches the server's BugShot.PREFIX");
        final int start = bugShot.indexOf("public static void beforeConsole()");
        final int end = start < 0 ? -1 : bugShot.indexOf("\n    }\n", start);
        final String body = start < 0 || end < 0 ? "" : bugShot.substring(start, end);
        check(body.contains("Toolkit.active.na("), "beforeConsole reads the toolkit's own frame (no screen grab)");
        for (String blocking : new String[] {"invokeAndWait", "ImageIO", ".get(", "Thread.sleep", "Robot", ".join("}) {
            check(!body.isEmpty() && !body.contains(blocking), "the game thread never waits in beforeConsole: " + blocking);
        }
    }

    private static void requests() {
        final String stem = "bug-20261015-010631-teamtest1";
        RspsBugShot.Request up = RspsBugShot.parse(stem + "|upload");
        check(up != null && up.upload && stem.equals(up.stem), "'<stem>|upload' = upload of that report");
        RspsBugShot.Request local = RspsBugShot.parse(stem);
        check(local != null && !local.upload && stem.equals(local.stem), "'<stem>' = local copy only (older server / hourly cap)");
        RspsBugShot.Request saved = RspsBugShot.parse(stem + "|saved|" + stem + ".png 974x581 690 KB");
        check(saved != null && "saved".equals(saved.reply) && stem.equals(saved.stem) && !saved.upload && (stem + ".png 974x581 690 KB").equals(saved.detail),
            "'<stem>|saved|<detail>' = the server saved that report's picture");
        RspsBugShot.Request refused = RspsBugShot.parse(stem + "|refused|a piece at 2000, expected 1000");
        check(refused != null && "refused".equals(refused.reply) && stem.equals(refused.stem) && "a piece at 2000, expected 1000".equals(refused.detail),
            "'<stem>|refused|<why>' = the server will not take that report's picture");
        check(up != null && local != null && up.reply == null && local.reply == null, "a request is not a reply");
        for (String bad : new String[] {stem + "|uploadx", stem + "|upload|upload", stem + "|", "|upload", "../" + stem + "|upload",
            stem + "|upload/../x", "C:\\x|upload", null, stem + "|saved", stem + "|refused", stem + "|savedx|y", "|saved|x",
            "../" + stem + "|refused|x", stem + "|done|x"}) {
            check(RspsBugShot.parse(bad) == null, "refused: " + bad);
        }
    }

    /**
     * Every size and every stem length, as the client puts it on the wire: opcode byte + RspsBugShot.writeChunk (var-short
     * size, p1 stem length, stem, p4 total, p4 offset, bytes - server BugShotChunkDecoder), each message within 1350 bytes.
     */
    private static void chunks() {
        final int writeBuffer = new ServerConnection().writePacket.data.length;
        check(writeBuffer == 1350, "ServerConnection.writePacket is 1350 bytes: " + writeBuffer);
        final String[] stems = {"bug-20261015-010631-a", "bug-20261015-010631-teamtest1", "bug-20261015-010631-999-abcdefghijklmnopqrst"};
        for (String stem : stems) {
            check(RspsBugShot.accepts(stem), "test stem " + stem);
            check(stem.length() <= RspsBugShot.MAX_STEM, "the longest server stem fits MAX_STEM: " + stem);
        }
        check(RspsBugShot.MAX_PIECE <= writeBuffer, "the biggest piece " + RspsBugShot.MAX_PIECE + " fits the write buffer");
        for (String stem : stems) {
            for (int size : new int[] {1, 8, RspsBugShot.CHUNK - 1, RspsBugShot.CHUNK, RspsBugShot.CHUNK + 1, 2 * RspsBugShot.CHUNK, 812_345, 3_000_017}) {
                final byte[] data = new byte[size];
                for (int i = 0; i < size; i++) {
                    data[i] = (byte) (i * 31 + size);
                }
                final byte[] back = new byte[size];
                int offset = 0;
                int pieces = 0;
                boolean ok = true;
                final String what = stem + " " + size + " bytes: ";
                while (offset < size && ok) {
                    final com.jagex.core.io.Packet p = new com.jagex.core.io.Packet(writeBuffer);
                    p.p1(94); // the opcode byte ClientMessage.create writes first
                    final int n = RspsBugShot.writeChunk(p, stem, data, offset);
                    pieces++;
                    final byte[] d = p.data;
                    final int len = (d[1] & 255) << 8 | (d[2] & 255);
                    final int stemLen = d[3] & 255;
                    final String gotStem = new String(d, 4, Math.min(stemLen, writeBuffer - 4), StandardCharsets.ISO_8859_1);
                    final int h = Math.min(4 + stemLen, writeBuffer - 8);
                    final int total = (d[h] & 255) << 24 | (d[h + 1] & 255) << 16 | (d[h + 2] & 255) << 8 | (d[h + 3] & 255);
                    final int off = (d[h + 4] & 255) << 24 | (d[h + 5] & 255) << 16 | (d[h + 6] & 255) << 8 | (d[h + 7] & 255);
                    ok = check(p.pos <= writeBuffer && p.pos <= RspsBugShot.MAX_PIECE, what + "piece of " + p.pos + " bytes does not fit the write buffer")
                        && check(stem.equals(gotStem), what + "piece names '" + gotStem + "'")
                        && check(len == p.pos - 3 && len == 1 + stemLen + 8 + n, what + "var-short size " + len + " for " + (p.pos - 3) + " body bytes")
                        && check(total == size && off == offset, what + "header total " + total + " offset " + off + " want " + offset)
                        && check(n > 0 && n <= RspsBugShot.CHUNK, what + "piece of " + n);
                    if (ok) {
                        System.arraycopy(d, h + 8, back, offset, n);
                        offset += n;
                    }
                }
                check(ok && java.util.Arrays.equals(data, back), what + "put back together byte-exact");
                check(pieces == (size + RspsBugShot.CHUNK - 1) / RspsBugShot.CHUNK, what + pieces + " pieces");
            }
        }
    }

    private static byte[] bytes(int n, int seed) {
        final byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            b[i] = (byte) (i * 13 + seed);
        }
        return b;
    }

    /**
     * Drains the upload queue the way pump does (one piece per cycle) and returns "stem@offset" of every piece. [addAt] maps a
     * piece number to a report whose PNG is made right then, [refuseAt] a piece number to a stem the server refuses right then.
     */
    private static List<String> drain(RspsBugShot.Uploads q, java.util.Map<Integer, String> refuseAt, java.util.Map<Integer, String> addAt) {
        final List<String> sent = new ArrayList<>();
        for (int cycle = 0; cycle < 10_000; cycle++) {
            if (addAt.containsKey(cycle)) {
                q.add(addAt.get(cycle), bytes(5 * RspsBugShot.CHUNK + 7, cycle));
            }
            if (refuseAt.containsKey(cycle)) {
                q.refuse(refuseAt.get(cycle));
            }
            final RspsBugShot.Upload up = q.current();
            if (up == null) {
                final int now = cycle;
                if (addAt.keySet().stream().anyMatch(k -> k > now)) {
                    continue;
                }
                break;
            }
            final com.jagex.core.io.Packet p = new com.jagex.core.io.Packet(1350);
            p.p1(94);
            sent.add(up.stem + "@" + up.offset);
            q.advance(up, RspsBugShot.writeChunk(p, up.stem, up.data, up.offset));
        }
        return sent;
    }

    /** Uploads go one after the other; every piece names its own report; a refusal stops only that report. */
    private static void queue() {
        final String a = "bug-20261015-022906-teamtest1";
        final String b = "bug-20261015-022921-teamtest1";
        final String c = "bug-20261015-022930-teamtest1";
        final int per = 6; // 5 * CHUNK + 7 bytes = 6 pieces
        // Keurmeester round 2: B made while A is half sent -> A finishes, then all of B from 0, every piece named right
        RspsBugShot.Uploads q = new RspsBugShot.Uploads();
        q.add(a, bytes(5 * RspsBugShot.CHUNK + 7, 1));
        List<String> sent = drain(q, java.util.Map.of(), java.util.Map.of(3, b));
        final List<String> want = new ArrayList<>();
        for (String s : new String[] {a, b}) {
            for (int i = 0; i < per; i++) {
                want.add(s + "@" + i * RspsBugShot.CHUNK);
            }
        }
        check(want.equals(sent), "A then B, each whole and named: " + sent);
        // three in a row before any piece went out
        q = new RspsBugShot.Uploads();
        q.add(a, bytes(5 * RspsBugShot.CHUNK + 7, 1));
        q.add(b, bytes(5 * RspsBugShot.CHUNK + 7, 2));
        q.add(c, bytes(5 * RspsBugShot.CHUNK + 7, 3));
        sent = drain(q, java.util.Map.of(), java.util.Map.of());
        check(sent.size() == 3 * per && sent.get(0).equals(a + "@0") && sent.get(per).equals(b + "@0") && sent.get(2 * per).equals(c + "@0")
            && sent.get(3 * per - 1).equals(c + "@" + (per - 1) * RspsBugShot.CHUNK), "three reports in order, each from 0: " + sent);
        // the server refuses A in the middle: A stops at once, B and C are sent whole
        q = new RspsBugShot.Uploads();
        q.add(a, bytes(5 * RspsBugShot.CHUNK + 7, 1));
        q.add(b, bytes(5 * RspsBugShot.CHUNK + 7, 2));
        q.add(c, bytes(5 * RspsBugShot.CHUNK + 7, 3));
        sent = drain(q, java.util.Map.of(2, a), java.util.Map.of());
        check(sent.size() == 2 + 2 * per && sent.get(1).equals(a + "@" + RspsBugShot.CHUNK) && sent.get(2).equals(b + "@0")
            && sent.stream().filter(x -> x.startsWith(a)).count() == 2, "refused A stops after 2 pieces, then B and C whole: " + sent);
        // a waiting report refused (the server's open-upload cap) is skipped; one refused before its PNG was made is never sent
        q = new RspsBugShot.Uploads();
        q.add(a, bytes(5 * RspsBugShot.CHUNK + 7, 1));
        q.add(b, bytes(5 * RspsBugShot.CHUNK + 7, 2));
        q.refuse(b);
        q.refuse(c);
        q.add(c, bytes(5 * RspsBugShot.CHUNK + 7, 3));
        sent = drain(q, java.util.Map.of(), java.util.Map.of());
        check(sent.size() == per && sent.stream().allMatch(x -> x.startsWith(a + "@")), "refused B (waiting) and C (not made yet) are never sent: " + sent);
        // a lost connection drops everything (the player keeps the own copies)
        q = new RspsBugShot.Uploads();
        q.add(a, bytes(100, 1));
        q.add(b, bytes(100, 2));
        q.current();
        check(q.dropAll() == 2 && q.current() == null, "a lost connection drops the sending and the waiting uploads");
    }

    /** The player is told what the server did - never "sent" on the client's own word; a reply is not a new screenshot. */
    private static void replies() {
        final String a = "bug-20261015-022906-teamtest1";
        final String b = "bug-20261015-022921-teamtest1";
        RspsBugShot.clearForTests();
        // two requests before the next frame: both get that frame (round 2 kept only the newest)
        RspsBugShot.request(a + "|upload");
        RspsBugShot.request(b + "|upload");
        check(RspsBugShot.waitingShots() == 2, "two reports before the next frame both wait for it: " + RspsBugShot.waitingShots());
        RspsBugShot.request(a + "|saved|" + a + ".png 974x581 690 KB");
        RspsBugShot.request(b + "|refused|the request expired");
        check(RspsBugShot.waitingShots() == 2, "a reply never asks for a screenshot");
        final List<String> lines = RspsBugShot.takeResults();
        check(lines.size() == 2 && lines.get(0).contains("saved on the server") && lines.get(0).contains(a + ".png")
            && lines.get(1).contains("NOT saved on the server") && lines.get(1).contains("the request expired"),
            "the player sees the server's answer per report: " + lines);
        String src = "";
        try {
            src = read("src/main/java/RspsBugShot.java");
        } catch (Exception e) {
            check(false, "source " + e);
        }
        final String pump = method(src, "public static void pump()");
        check(!pump.isEmpty() && !pump.contains("addline(\"Bug screenshot sent") && !pump.contains("results.add(\"Bug screenshot sent"),
            "pump never tells the player 'sent' - only the server's answer does");
        RspsBugShot.clearForTests();
    }

    /**
     * Test server 2026-10-15 01:53: the pieces filled the client's 15000-byte socket ring faster than the socket drained it,
     * ClientStreamWriter threw "Buffer overflow 1311 579" and the client dropped the game connection. A socket that does not
     * drain at all: pump's gate must stop before the ring is full, and the player's own packets must still fit after it.
     */
    private static void stalledSocket() throws Exception {
        final java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        final java.io.OutputStream stalled = new java.io.OutputStream() {
            @Override
            public void write(int b) throws java.io.IOException {
                await();
            }

            @Override
            public void write(byte[] b, int off, int len) throws java.io.IOException {
                await();
            }

            private void await() throws java.io.IOException {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    throw new java.io.IOException(e);
                }
            }
        };
        final com.jagex.core.io.connection.ClientStreamWriter writer = new com.jagex.core.io.connection.ClientStreamWriter(stalled, 15000);
        try {
            check(writer.free() == 15000, "an empty ring has 15000 bytes free: " + writer.free());
            final byte[] piece = new byte[RspsBugShot.MAX_PIECE];
            int sent = 0;
            try {
                for (int cycle = 0; cycle < 1000; cycle++) {
                    if (RspsBugShot.canSendPiece(0, writer.free(), cycle * 1000L, 0)) {
                        writer.write(piece, piece.length, 0);
                        sent++;
                    }
                }
                check(true, "no overflow");
            } catch (java.io.IOException e) {
                check(false, "a stalled socket overflows the ring after " + sent + " pieces: " + e.getMessage());
            }
            check(sent > 0 && sent < 1000, "pieces stop when the socket does not drain: " + sent + " sent");
            try {
                writer.write(new byte[5000], 5000, 0);
                check(true, "player packets still fit");
            } catch (java.io.IOException e) {
                check(false, "after the screenshot pieces a 5000-byte burst of the player's own packets overflows: " + e.getMessage());
            }
            check(!RspsBugShot.canSendPiece(1, 15000, 0, 0), "never while another packet waits to be flushed");
            check(RspsBugShot.canSendPiece(0, 15000, 0, 0), "an idle connection sends");
        } finally {
            release.countDown();
            writer.stop();
        }
    }

    /**
     * The upload never takes more than half of what the server reads per player per second: logic cycles of 20 ms (and a
     * burst of catch-up cycles at the same millisecond), an idle connection, the pace gate as pump uses it.
     */
    private static void pace() throws Exception {
        final int serverRate = 16 * 1024; // server BugShotUploads.CLIENT_RATE
        for (int cycleMs : new int[] {0, 1, 20, 50}) {
            long next = 0;
            int maxPerSecond = 0;
            final java.util.ArrayDeque<Long> window = new java.util.ArrayDeque<>();
            for (long now = 0; now < 30_000; now += Math.max(1, cycleMs)) {
                for (int burst = 0; burst < (cycleMs == 0 ? 10 : 1); burst++) {
                    if (RspsBugShot.canSendPiece(0, 15000, now, next)) {
                        next = now + RspsBugShot.PIECE_INTERVAL_MS;
                        window.addLast(now);
                    }
                }
                while (!window.isEmpty() && window.peekFirst() <= now - 1000) {
                    window.pollFirst();
                }
                maxPerSecond = Math.max(maxPerSecond, window.size());
            }
            final int bytes = maxPerSecond * RspsBugShot.MAX_PIECE;
            check(bytes <= serverRate, "cycles of " + cycleMs + " ms: " + bytes + " bytes in one second, more than the " + serverRate + " the server allows for an upload");
            check(maxPerSecond >= 8, "cycles of " + cycleMs + " ms: the upload still moves (" + maxPerSecond + " pieces per second)");
        }
        final File init = new File("C:/RSPS/game/game/game/src/main/kotlin/gg/rsmod/game/protocol/ClientChannelInitializer.kt");
        if (init.isFile()) {
            final String src = read(init.getPath());
            final java.util.regex.Matcher m = java.util.regex.Pattern.compile("CHANNEL_READ_LIMIT = (\\d+)L \\* 1024").matcher(src);
            final long limit = m.find() ? Long.parseLong(m.group(1)) * 1024 : -1;
            check(limit >= 2L * serverRate, "server reads " + limit + " bytes/s per player: at least twice the upload pace " + serverRate);
            check(src.contains("ChannelTrafficShapingHandler(0, CHANNEL_READ_LIMIT, 1000)"), "the server pipeline uses CHANNEL_READ_LIMIT");
        }
    }

    private static void uploadWiring() throws Exception {
        final String prot = read("src/main/java/com/jagex/ClientProt.java");
        check(prot.contains("RSPS_BUGSHOT_CHUNK = new ClientProt(94, -2);"), "ClientProt RSPS_BUGSHOT_CHUNK is opcode 94, var-short");
        final String logic = read("src/main/java/MainLogicManager.java");
        final int pump = logic.indexOf("RspsBugShot.pump();");
        final int flush = logic.indexOf("ServerConnection.GAME.flush();");
        final int noTimeout = logic.indexOf("ClientMessage.create(ClientProt.NO_TIMEOUT");
        check(pump > 0 && flush > pump && noTimeout > 0 && noTimeout < pump && logic.indexOf("RspsBugShot.pump();", pump + 1) < 0,
            "MainLogicManager calls RspsBugShot.pump() once per logic cycle, right before the flush");
        final String bugShot = read("src/main/java/RspsBugShot.java");
        final String body = method(bugShot, "public static void pump()");
        final int gate = body.indexOf("if (!canSendPiece(connection.buffered, writerFree(connection.connection), now, up.nextPieceAt))");
        check(body.contains("up.nextPieceAt = now + PIECE_INTERVAL_MS;"), "pump sets the next piece time after the gate");
        check(gate > 0 && gate < body.indexOf("ClientMessage.create("),
            "pump sends a piece only when no other packet waits and the socket writer has room (canSendPiece before the message)");
        check(body.contains("ClientMessage.create(ClientProt.RSPS_BUGSHOT_CHUNK, connection.isaac)") && body.contains("connection.connection == null"),
            "pump uses opcode 94 with the game connection's ISAAC and stops on a lost connection");
        for (String blocking : new String[] {"invokeAndWait", "ImageIO", "Thread.sleep", ".join(", "flush()", "while ("}) {
            check(!body.isEmpty() && !body.contains(blocking), "pump never blocks the game thread: " + blocking);
        }
        final String write = method(bugShot, "private static void write(Request request");
        final int upload = write.indexOf("if (request.upload)");
        final int other = upload < 0 ? -1 : write.indexOf("} else {", upload);
        final String uploadBranch = upload < 0 || other < 0 ? "" : write.substring(upload, other);
        check(uploadBranch.contains("ownDir(home)") && uploadBranch.contains("UPLOADS.add(") && !uploadBranch.contains("SERVER_BUGS")
            && !uploadBranch.contains("targetDir("), "with an upload the client queues the PNG and keeps its own copy only in 78legacy\\bugs");
        final File home = new File("x");
        check(new File(home, "78legacy/bugs").equals(RspsBugShot.ownDir(home)), "own copy folder");
        // the server side of the same packet (when the server repo sits next to the client, as on the team PC)
        final File packets = new File("C:/RSPS/game/game/data/packets.yml");
        if (packets.isFile()) {
            final String yml = read(packets.getPath());
            final int at = yml.indexOf("gg.rsmod.game.message.impl.BugShotChunkMessage");
            final String entry = at < 0 ? "" : yml.substring(at, Math.min(yml.length(), at + 200));
            check(entry.contains("type: VARIABLE_SHORT") && entry.contains("opcode: 94"), "server packets.yml: opcode 94 VARIABLE_SHORT BugShotChunkMessage");
            final String server = read("C:/RSPS/game/game/game/plugins/src/main/kotlin/gg/rsmod/plugins/content/cmd/BugShot.kt");
            check(server.contains("const val UPLOAD = \"" + RspsBugShot.UPLOAD + "\""), "server BugShot.UPLOAD matches the client's");
            check(server.contains("if (outcome == BugShotUploads.Outcome.SAVED) \"saved\" else \"refused\"") && server.contains("|$word|$clean"),
                "server replies '<stem>|saved|..' / '<stem>|refused|..' as the client parses them");
            final String decoder = read("C:/RSPS/game/game/game/src/main/kotlin/gg/rsmod/game/message/decoder/BugShotChunkDecoder.kt");
            check(decoder.contains("val length = reader.getUnsigned(DataType.BYTE).toInt() and 0xFF") && decoder.indexOf("stemBytes") < decoder.indexOf("val total"),
                "server decoder reads p1 stem length + stem before total/offset (RspsBugShot.writeChunk)");
        }
    }

    /**
     * pump with the session rule: every cycle the uploads of another connection than live(cycle) are dropped first, a piece
     * goes out only over the connection its upload belongs to. Returns "stem@offset@connection" per piece.
     */
    private static List<String> drainLive(RspsBugShot.Uploads q, java.util.function.IntFunction<Object> live, java.util.Map<Integer, Runnable> at,
                                          java.util.Map<Object, String> names) {
        final List<String> sent = new ArrayList<>();
        for (int cycle = 0; cycle < 10_000; cycle++) {
            if (at.containsKey(cycle)) {
                at.get(cycle).run();
            }
            final Object conn = live.apply(cycle);
            q.dropOtherSessions(conn);
            final RspsBugShot.Upload up = q.current();
            if (up == null) {
                final int now = cycle;
                if (at.keySet().stream().anyMatch(k -> k > now)) {
                    continue;
                }
                break;
            }
            if (conn == null) {
                continue;
            }
            final com.jagex.core.io.Packet p = new com.jagex.core.io.Packet(1350);
            p.p1(94);
            sent.add(up.stem + "@" + up.offset + "@" + names.get(conn) + (up.session == conn ? "" : "!WRONG-CONNECTION"));
            q.advance(up, RspsBugShot.writeChunk(p, up.stem, up.data, up.offset));
        }
        return sent;
    }

    /** Round 4: an upload never outlives the game connection it was asked on, at any moment, and the player is told. */
    private static void sessions() {
        final String a = "bug-20261015-052119-teamtest2";
        final String b = "bug-20261015-052130-teamtest2";
        final String c = "bug-20261015-052830-teamtest3";
        final int per = 6; // 5 * CHUNK + 7 bytes
        final Object tt2 = new Object();
        final Object tt3 = new Object();
        final java.util.Map<Object, String> names = new java.util.IdentityHashMap<>();
        names.put(tt2, "tt2");
        names.put(tt3, "tt3");
        // the same connection: nothing is dropped
        RspsBugShot.Uploads q = new RspsBugShot.Uploads();
        q.add(a, bytes(5 * RspsBugShot.CHUNK + 7, 1), tt2);
        q.add(b, bytes(5 * RspsBugShot.CHUNK + 7, 2), tt2);
        check(q.dropOtherSessions(tt2).isEmpty() && q.current() != null && a.equals(q.current().stem), "the upload's own connection keeps every upload");
        // the AUDIT case at every moment of A: kicked after k pieces (no connection), teamtest3 logs in 5 cycles later, reports C
        for (int kick = 0; kick <= per; kick++) {
            final int k = kick;
            final RspsBugShot.Uploads qq = new RspsBugShot.Uploads();
            qq.add(a, bytes(5 * RspsBugShot.CHUNK + 7, 1), tt2);
            qq.add(b, bytes(5 * RspsBugShot.CHUNK + 7, 2), tt2);
            final List<String> sent = drainLive(qq, cycle -> cycle < k ? tt2 : cycle < k + 5 ? null : tt3,
                java.util.Map.of(k + 6, () -> qq.add(c, bytes(5 * RspsBugShot.CHUNK + 7, 3), tt3)), names);
            final List<String> want = new ArrayList<>();
            for (int i = 0; i < Math.min(k, per); i++) {
                want.add(a + "@" + i * RspsBugShot.CHUNK + "@tt2");
            }
            for (int i = 0; i < per; i++) {
                want.add(c + "@" + i * RspsBugShot.CHUNK + "@tt3");
            }
            if (k < per) {
                check(want.equals(sent), "kicked after " + k + " pieces of A, then teamtest3's report C: A only before the kick, then all of C over tt3, never A or B over tt3: " + sent);
            } else {
                check(sent.size() == 2 * per && sent.subList(0, per).stream().allMatch(x -> x.startsWith(a + "@") && x.endsWith("@tt2"))
                        && sent.subList(per, 2 * per).equals(want.subList(per, 2 * per)),
                    "kicked right after A's last piece: B (waiting, old connection) is never sent, C goes whole over tt3: " + sent);
            }
        }
        // a reconnect straight onto another connection (no gap): same
        final RspsBugShot.Uploads q2 = new RspsBugShot.Uploads();
        q2.add(a, bytes(5 * RspsBugShot.CHUNK + 7, 1), tt2);
        List<String> sent = drainLive(q2, cycle -> cycle < 2 ? tt2 : tt3, java.util.Map.of(), names);
        check(sent.size() == 2 && sent.stream().allMatch(x -> x.endsWith("@tt2")), "a new connection right after 2 pieces: nothing more of A: " + sent);
        // dropped uploads are listed once each, with how far they got
        q = new RspsBugShot.Uploads();
        q.add(a, bytes(5 * RspsBugShot.CHUNK + 7, 1), tt2);
        q.add(b, bytes(5 * RspsBugShot.CHUNK + 7, 2), tt2);
        q.add(c, bytes(5 * RspsBugShot.CHUNK + 7, 3), tt3);
        q.advance(q.current(), RspsBugShot.CHUNK);
        final List<String> dropped = q.dropOtherSessions(tt3);
        check(dropped.size() == 2 && dropped.get(0).startsWith(a + " " + RspsBugShot.CHUNK + "/") && dropped.get(1).startsWith(b + " 0/"),
            "A (half sent) and B (waiting) of the old connection dropped, C of the new one kept: " + dropped);
        check(q.current() != null && c.equals(q.current().stem) && q.dropOtherSessions(tt3).isEmpty(), "the new connection's report goes next");
        // requests still waiting for their frame, then the connection changes: no picture for the old connection, told ONCE
        RspsBugShot.clearForTests();
        RspsBugShot.request(a + "|upload");
        RspsBugShot.request(b + "|upload");
        RspsBugShot.checkSession(null);
        check(RspsBugShot.waitingShots() == 2, "the same connection keeps waiting requests: " + RspsBugShot.waitingShots());
        RspsBugShot.takeResults();
        RspsBugShot.checkSession(tt3);
        check(RspsBugShot.waitingShots() == 0, "a new connection drops the old one's requests before their frame: " + RspsBugShot.waitingShots());
        final List<String> lines = RspsBugShot.takeResults();
        check(lines.size() == 1 && lines.get(0).contains("NOT sent to the server") && lines.get(0).contains("connection was lost") && lines.get(0).contains("78legacy"),
            "the player is told once that the server did not get it: " + lines);
        RspsBugShot.checkSession(tt3);
        check(RspsBugShot.takeResults().isEmpty(), "told once, not every frame");
        RspsBugShot.clearForTests();
        // how long it takes is said up front (the owner's window: ~2.18 MB = about 2.5 minutes)
        check(RspsBugShot.uploadSeconds(1) == 1 && RspsBugShot.uploadSeconds(RspsBugShot.CHUNK) == 1,
            "a tiny upload is about 1 s: " + RspsBugShot.uploadSeconds(1));
        final int owner = RspsBugShot.uploadSeconds(2_176_000);
        check(owner == (int) Math.ceil(((2_176_000 + RspsBugShot.CHUNK - 1) / RspsBugShot.CHUNK) * RspsBugShot.PIECE_INTERVAL_MS / 1000.0),
            "a 2.18 MB screenshot is announced at the pump pace: " + owner + " s");
        // wiring: every frame and before every piece; the request remembers its connection, the PNG keeps it
        String src = "";
        try {
            src = read("src/main/java/RspsBugShot.java");
        } catch (Exception e) {
            check(false, "source " + e);
        }
        final String before = method(src, "public static void beforeConsole()");
        check(before.indexOf('{') > 0 && before.substring(before.indexOf('{') + 1).trim().startsWith("checkSession(ServerConnection.GAME.connection);"), "beforeConsole checks the connection first, every frame");
        final String pump = method(src, "public static void pump()");
        final int guard = pump.indexOf("up.session != connection.connection");
        check(guard > 0 && guard < pump.indexOf("ClientMessage.create("), "pump never sends a piece over another connection than its own");
        final String request = method(src, "public static void request(String payload)");
        check(request.contains("request.session = ServerConnection.GAME.connection;"), "a request remembers the connection it came in on");
        final String write = method(src, "private static void write(Request request");
        check(write.contains("UPLOADS.add(stem, png, request.session)") && write.contains("Keep the game open"),
            "the PNG keeps its request's connection and the player hears how long to keep the game open");
    }

    private static String method(String source, String signature) {
        final int start = source.indexOf(signature);
        final int end = start < 0 ? -1 : source.indexOf("\n    }\n", start);
        return start < 0 || end < 0 ? "" : source.substring(start, end);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void deleteAll(File f) {
        final File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteAll(k);
            }
        }
        f.delete();
    }

    private static boolean check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            failures.add(message);
        }
        return condition;
    }
}
