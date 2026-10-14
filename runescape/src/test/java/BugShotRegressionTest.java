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
        wiring();
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
