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
        stalledSocket();
        pace();
        wiring();
        uploadWiring();
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
        for (String bad : new String[] {stem + "|uploadx", stem + "|upload|upload", stem + "|", "|upload", "../" + stem + "|upload",
            stem + "|upload/../x", "C:\\x|upload", null}) {
            check(RspsBugShot.parse(bad) == null, "refused: " + bad);
        }
    }

    /** Every size, as the client puts it on the wire: opcode byte + RspsBugShot.writeChunk, each message within 1350 bytes. */
    private static void chunks() {
        final int writeBuffer = new ServerConnection().writePacket.data.length;
        check(writeBuffer == 1350, "ServerConnection.writePacket is 1350 bytes: " + writeBuffer);
        for (int size : new int[] {1, 8, RspsBugShot.CHUNK - 1, RspsBugShot.CHUNK, RspsBugShot.CHUNK + 1, 2 * RspsBugShot.CHUNK, 812_345, 3_000_017}) {
            final byte[] data = new byte[size];
            for (int i = 0; i < size; i++) {
                data[i] = (byte) (i * 31 + size);
            }
            final byte[] back = new byte[size];
            int offset = 0;
            int pieces = 0;
            boolean ok = true;
            while (offset < size && ok) {
                final com.jagex.core.io.Packet p = new com.jagex.core.io.Packet(writeBuffer);
                p.p1(94); // the opcode byte ClientMessage.create writes first
                final int n = RspsBugShot.writeChunk(p, data, offset);
                pieces++;
                final byte[] d = p.data;
                final int len = (d[1] & 255) << 8 | (d[2] & 255);
                final int total = (d[3] & 255) << 24 | (d[4] & 255) << 16 | (d[5] & 255) << 8 | (d[6] & 255);
                final int off = (d[7] & 255) << 24 | (d[8] & 255) << 16 | (d[9] & 255) << 8 | (d[10] & 255);
                ok = check(p.pos <= writeBuffer, size + " bytes: piece of " + p.pos + " bytes does not fit the write buffer")
                    && check(len == p.pos - 3 && len == 8 + n, size + " bytes: var-short size " + len + " for " + (p.pos - 3) + " body bytes")
                    && check(total == size && off == offset, size + " bytes: header total " + total + " offset " + off + " want " + offset)
                    && check(n > 0 && n <= RspsBugShot.CHUNK, size + " bytes: piece of " + n);
                if (ok) {
                    System.arraycopy(d, 11, back, offset, n);
                    offset += n;
                }
            }
            check(ok && java.util.Arrays.equals(data, back), size + " bytes: put back together byte-exact");
            check(pieces == (size + RspsBugShot.CHUNK - 1) / RspsBugShot.CHUNK, size + " bytes: " + pieces + " pieces");
        }
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
            final byte[] piece = new byte[RspsBugShot.CHUNK + 11];
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
            final int bytes = maxPerSecond * (RspsBugShot.CHUNK + 11);
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
        check(uploadBranch.contains("ownDir(home)") && uploadBranch.contains("outgoing.add(") && !uploadBranch.contains("SERVER_BUGS")
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
        }
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
