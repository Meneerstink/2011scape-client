import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;

/**
 * The 78 login screen layout at every supported viewport: fixed mode, common windows, 4:3, 5:4, 16:9, 16:10,
 * 21:9, 32:9, 4K and a portrait window. Checks the cover background, that the logo and panel are never
 * cropped and stay centred, that up to 16:9 they sit exactly on the reference composition, that the fields
 * stay usable and that the scale limits hold. With an output directory argument it also writes each
 * composed screen, hitboxes outlined, as PNG.
 */
public final class RspsLoginLayoutRegressionTest {

    private static final int[][] VIEWPORTS = {
        {765, 503}, {800, 600}, {1024, 768}, {1280, 1024}, {1280, 720}, {1366, 768}, {1600, 900},
        {1920, 1080}, {1680, 1050}, {1920, 1200}, {2560, 1440}, {2560, 1600}, {2560, 1080}, {3440, 1440},
        {3840, 2160}, {5120, 1440}, {800, 1000}, {1000, 560}, {640, 480},
    };

    public static void main(String[] args) throws Exception {
        File out = args.length > 0 ? new File(args[0]) : null;
        BufferedImage bg = null, logo = null, panel = null;
        if (out != null) {
            out.mkdirs();
            bg = read("background");
            BufferedImage foreground = read("foreground");
            check(bg.getWidth() == RspsLoginLayout.REF_W && bg.getHeight() == RspsLoginLayout.REF_H, "background size");
            check(foreground.getWidth() == RspsLoginLayout.REF_W && foreground.getHeight() == RspsLoginLayout.REF_H, "foreground size");
            logo = foreground.getSubimage(RspsLoginLayout.LOGO_X, RspsLoginLayout.LOGO_Y,
                RspsLoginLayout.LOGO_W, RspsLoginLayout.LOGO_H);
            panel = foreground.getSubimage(RspsLoginLayout.PANEL_X, RspsLoginLayout.PANEL_Y,
                RspsLoginLayout.PANEL_W, RspsLoginLayout.PANEL_H);
            check(logo.getWidth() == RspsLoginLayout.LOGO_W && logo.getHeight() == RspsLoginLayout.LOGO_H, "logo size");
            check(panel.getWidth() == RspsLoginLayout.PANEL_W && panel.getHeight() == RspsLoginLayout.PANEL_H, "panel size");
        }
        for (int[] v : VIEWPORTS) {
            int w = v[0], h = v[1];
            RspsLoginLayout l = new RspsLoginLayout(w, h);
            String at = w + "x" + h;

            check(l.bgX <= 0 && l.bgY <= 0 && l.bgX + l.bgW >= w && l.bgY + l.bgH >= h, at + ": background covers");
            check(Math.abs(l.bgW / (double) l.bgH - RspsLoginLayout.REF_W / (double) RspsLoginLayout.REF_H) < 0.01, at + ": background keeps its aspect");

            check(l.logoX() >= 0 && l.logoY() >= 0 && l.logoX() + l.logoW() <= w && l.logoY() + l.logoH() <= h, at + ": logo inside");
            check(l.panelX() >= 0 && l.panelY() >= 0 && l.panelX() + l.panelW() <= w && l.panelY() + l.panelH() <= h, at + ": panel inside");
            check(Math.abs(l.logoX() + l.logoW() / 2.0 - w / 2.0) <= 1.5, at + ": logo centred");
            check(Math.abs(l.panelX() + l.panelW() / 2.0 - w / 2.0) <= 1.5, at + ": panel centred");
            check(l.logoY() + l.logoH() >= l.panelY(), at + ": logo meets the panel");
            check(l.panelScale >= RspsLoginLayout.MIN_SCALE * 0.75 && l.panelScale <= RspsLoginLayout.MAX_SCALE, at + ": scale limits");

            if (!l.compact && w * RspsLoginLayout.REF_H <= h * RspsLoginLayout.REF_W && w >= RspsLoginLayout.LOGO_W * l.bgScale + 12) {
                check(l.locked(), at + ": reference composition up to 16:9");
                check(Math.abs(l.panelX() - (l.bgX + RspsLoginLayout.PANEL_X * l.bgScale)) <= 1.5
                    && Math.abs(l.panelY() - (l.bgY + RspsLoginLayout.PANEL_Y * l.bgScale)) <= 1.5, at + ": panel on the background's panel");
            }
            if (w == 765 && h == 503) {
                check(l.compact, "fixed mode uses the compact fit");
            }

            int[] user = l.panelRect(RspsLoginLayout.USERNAME);
            int[] button = l.panelRect(RspsLoginLayout.BUTTON);
            check(user[3] >= 21, at + ": field tall enough for the 19 px caret and 12 px text (" + user[3] + ")");
            check(user[2] - l.panelLength(RspsLoginLayout.FIELD_TEXT_INSET) >= 120, at + ": field wide enough for a username");
            check(button[2] >= 150 && button[3] >= 30, at + ": Log In button clickable");

            System.out.printf("%-10s %s scale bg %.3f logo %.3f panel %.3f  field %dx%d  button %dx%d%n", at,
                l.compact ? "compact" : (l.locked() ? "locked " : "fitted "), l.bgScale, l.logoScale, l.panelScale,
                user[2], user[3], button[2], button[3]);

            if (out != null) {
                BufferedImage img = RspsLoginScreen.compose(l, bg, logo, panel);
                ImageIO.write(img, "png", new File(out, "login_" + at + ".png"));
                Graphics2D g = img.createGraphics();
                g.setStroke(new BasicStroke(1));
                g.setColor(Color.GREEN);
                for (int[] ref : new int[][] {RspsLoginLayout.USERNAME, RspsLoginLayout.PASSWORD, RspsLoginLayout.BUTTON, RspsLoginLayout.RECOVER, RspsLoginLayout.CREATE}) {
                    int[] r = l.panelRect(ref);
                    g.drawRect(r[0], r[1], r[2] - 1, r[3] - 1);
                }
                g.setColor(Color.CYAN);
                int[] body = l.panelRect(RspsLoginLayout.BODY);
                g.drawRect(body[0], body[1], body[2] - 1, body[3] - 1);
                g.dispose();
                ImageIO.write(img, "png", new File(out, "hitbox_" + at + ".png"));
            }
        }
        System.out.println("78 login layout regression passed: " + VIEWPORTS.length + " viewports");
    }

    private static BufferedImage read(String name) throws Exception {
        try (InputStream in = RspsLoginLayoutRegressionTest.class.getResourceAsStream("/login78/" + name + ".png")) {
            if (in == null) {
                throw new AssertionError("missing /login78/" + name + ".png");
            }
            return RspsLoginArt.premultiplied(ImageIO.read(in));
        }
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }
}
