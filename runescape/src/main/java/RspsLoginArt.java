import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * Image work for {@link RspsLoginScreen}: resampling and the button/link states, all derived from the
 * approved artwork itself (no new drawing).
 *
 * <p>Images are kept premultiplied ({@link BufferedImage#TYPE_INT_ARGB_PRE}) so filtering never pulls dark
 * fringes out of transparent pixels. Downscaling halves with bilinear filtering (a 2x2 box) until less than
 * a halving remains, then one bicubic step; upscaling is one bicubic step. Output is straight ARGB for the
 * toolkit sprites.
 */
final class RspsLoginArt {

    enum State { HOVER, PRESSED, DISABLED }

    private RspsLoginArt() {
    }

    static BufferedImage premultiplied(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    /** Keep the approved screenshot's actual foreground pixels; the generated matte supplies alpha only. */
    static BufferedImage approvedForeground(BufferedImage approved, BufferedImage matte) {
        if (approved == null || matte == null || approved.getWidth() != matte.getWidth()
            || approved.getHeight() != matte.getHeight()) {
            return null;
        }
        int w = approved.getWidth(), h = approved.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int a = matte.getRGB(x, y) >>> 24;
                // Remove the matte's faint exterior haze; keep antialiasing at the object edge.
                a = a <= 48 ? 0 : a >= 224 ? 255 : (a - 48) * 255 / 176;
                out.setRGB(x, y, a << 24 | (approved.getRGB(x, y) & 0xFFFFFF));
            }
        }
        return out;
    }

    /** {@code src} (premultiplied) resampled to w x h, as straight ARGB pixels. */
    static int[] scale(BufferedImage src, int w, int h) {
        if (src == null || w <= 0 || h <= 0) {
            return null;
        }
        BufferedImage cur = src;
        int cw = src.getWidth();
        int ch = src.getHeight();
        while (cw / 2 >= w && ch / 2 >= h) {
            cw /= 2;
            ch /= 2;
            cur = resample(cur, cw, ch, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (cw != w || ch != h) {
            cur = resample(cur, w, h, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        return cur.getRGB(0, 0, w, h, null, 0, w);
    }

    private static BufferedImage resample(BufferedImage src, int w, int h, Object interpolation) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** The reference rectangle {x0, y0, x1, y1} of a layer whose origin is (originX, originY), copied. */
    static BufferedImage crop(BufferedImage layer, int[] ref, int originX, int originY) {
        int w = ref[2] - ref[0];
        int h = ref[3] - ref[1];
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        g.drawImage(layer, -(ref[0] - originX), -(ref[1] - originY), null);
        g.dispose();
        return out;
    }

    /**
     * A state of the Log In button: the button face (x, y, w, h inside {@code art}) lit, darkened or greyed;
     * the ornaments and the panel around it stay as they are. Hover lifts the gold and the lettering,
     * pressed darkens (and is drawn a pixel lower), disabled drains the colour while a login runs.
     */
    static BufferedImage buttonState(BufferedImage art, int x, int y, int w, int h, State state) {
        BufferedImage out = copy(art);
        for (int py = Math.max(0, y); py < Math.min(out.getHeight(), y + h); py++) {
            for (int px = Math.max(0, x); px < Math.min(out.getWidth(), x + w); px++) {
                int argb = out.getRGB(px, py);
                int a = argb >>> 24;
                int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                switch (state) {
                    case HOVER:
                        r = (int) (r * 1.22 + 6);
                        g = (int) (g * 1.14 + 2);
                        b = (int) (b * 1.22 + 8);
                        break;
                    case PRESSED:
                        r = (int) (r * 0.78);
                        g = (int) (g * 0.78);
                        b = (int) (b * 0.80);
                        break;
                    case DISABLED:
                        r = (int) ((lum * 0.75 + r * 0.25) * 0.62);
                        g = (int) ((lum * 0.75 + g * 0.25) * 0.62);
                        b = (int) ((lum * 0.75 + b * 0.25) * 0.66);
                        break;
                }
                out.setRGB(px, py, a << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b));
            }
        }
        return out;
    }

    /** Link hover: the ivory lettering and underline take a warm gold glow; dark pixels stay. */
    static BufferedImage linkHover(BufferedImage art) {
        BufferedImage out = copy(art);
        for (int py = 0; py < out.getHeight(); py++) {
            for (int px = 0; px < out.getWidth(); px++) {
                int argb = out.getRGB(px, py);
                int a = argb >>> 24;
                int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
                double lum = 0.299 * r + 0.587 * g + 0.114 * b;
                double t = Math.max(0, Math.min(1, (lum - 55) / 120.0)) * 0.8;
                double k = Math.min(1.25, lum / 200.0 + 0.1);
                r = (int) (r + (255 * k - r) * t);
                g = (int) (g + (221 * k - g) * t);
                b = (int) (b + (112 * k - b) * t);
                out.setRGB(px, py, a << 24 | clamp(r) << 16 | clamp(g) << 8 | clamp(b));
            }
        }
        return out;
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : Math.min(255, v);
    }
}
