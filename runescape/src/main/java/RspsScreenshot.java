import com.jagex.game.runetek6.client.GameShell;

import javax.imageio.ImageIO;
import java.awt.Canvas;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * RSPS: the server's `screenshot` command moves the camera through a few viewpoints and after each one sends a console
 * line "__RSPS_SHOT__:<name>". This saves exactly what the game canvas shows at that moment to C:/RSPS/screens (or
 * ~/rsps-screens when that folder cannot be made), so layout work can be reviewed from real client renders.
 */
public final class RspsScreenshot {

    public static final String PREFIX = "__RSPS_SHOT__:";

    private static final String SESSION = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());

    private RspsScreenshot() {
    }

    public static void capture(String name) {
        final Canvas canvas = GameShell.canvas;
        if (canvas == null || !canvas.isShowing()) {
            debugconsole.addline("Screenshot failed: the game canvas is not visible.");
            return;
        }
        final String safe = name.replaceAll("[^A-Za-z0-9_.,-]", "_");
        try {
            Point at = canvas.getLocationOnScreen();
            Rectangle area = new Rectangle(at.x, at.y, canvas.getWidth(), canvas.getHeight());
            BufferedImage image = new Robot().createScreenCapture(area);
            File dir = new File("C:/RSPS/screens");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                dir = new File(System.getProperty("user.home"), "rsps-screens");
                dir.mkdirs();
            }
            File out = new File(dir, SESSION + "_" + safe + ".png");
            ImageIO.write(image, "png", out);
            debugconsole.addline("Screenshot saved: " + out.getAbsolutePath());
        } catch (Exception e) {
            debugconsole.addline("Screenshot failed: " + e);
        }
    }
}
