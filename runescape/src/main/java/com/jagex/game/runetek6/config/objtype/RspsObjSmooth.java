package com.jagex.game.runetek6.config.objtype;

import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * N319 (owner 2026-10-24: the Drop Viewer's item pictures "wazig / low quality"): an item picture in a component smaller than the
 * item sprite (36 x 32) was drawn by {@code Sprite.render(x, y, w, h)}, which steps through the source pixels and drops whole rows
 * and columns - thin outlines vanished, edges went ragged. Here the item's own 36 x 32 pixels ({@link #remember}, kept when
 * {@code ObjTypeList.sprite} builds the sprite) are shrunk once per size by averaging every source pixel a target pixel covers (an
 * area filter, like a browser or the OSRS Wiki shrinking a picture), and that sprite is drawn at its own size. Only shrinking:
 * a component of 36 x 32 or larger keeps the client's own drawing.
 */
public final class RspsObjSmooth {

    private static final int W = 36;
    private static final int H = 32;
    private static final int MAX_IMAGES = 2048;
    private static final int MAX_SPRITES = 1024;

    /** The 36 x 32 ARGB pixels of each built item sprite, by its sprite cache key. */
    private static final Map<String, int[]> IMAGES = new LinkedHashMap<String, int[]>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest) {
            return size() > MAX_IMAGES;
        }
    };

    /** The shrunk sprites, by sprite cache key + size + toolkit. */
    private static final Map<String, Sprite> SPRITES = new LinkedHashMap<String, Sprite>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Sprite> eldest) {
            return size() > MAX_SPRITES;
        }
    };

    private RspsObjSmooth() {
    }

    private static String key(int objId, int outline, int invCount, int graphicShadow, int objNumMode, boolean wearCol) {
        return objId + "," + outline + "," + invCount + "," + graphicShadow + "," + objNumMode + "," + (wearCol ? 1 : 0);
    }

    /** {@code ObjTypeList.sprite}: the pixels of a freshly built (cached) item sprite. */
    public static synchronized void remember(int objId, int outline, int invCount, int graphicShadow, int objNumMode, boolean wearCol, int[] image) {
        if (image == null || image.length < W * H) {
            return;
        }
        String k = key(objId, outline, invCount, graphicShadow, objNumMode, wearCol);
        IMAGES.put(k, image.clone());
        // A rebuilt sprite (new count, new toolkit) replaces the shrunk ones made from the old pixels.
        SPRITES.keySet().removeIf(s -> s.startsWith(k + "|"));
    }

    /**
     * The shrunk sprite for a component of {@code width} x {@code height}, or null to draw the client's own way (not smaller, or
     * the pixels are not known yet).
     */
    public static synchronized Sprite get(Toolkit toolkit, int objId, int outline, int invCount, int graphicShadow, int objNumMode, boolean wearCol, int width, int height) {
        if (width <= 0 || height <= 0 || width > W || height > H || (width == W && height == H)) {
            return null;
        }
        String k = key(objId, outline, invCount, graphicShadow, objNumMode, wearCol);
        String sk = k + "|" + width + "x" + height + "@" + toolkit.index;
        Sprite cached = SPRITES.get(sk);
        if (cached != null) {
            return cached;
        }
        int[] image = IMAGES.get(k);
        if (image == null) {
            return null;
        }
        Sprite sprite = toolkit.createSprite(width, width, height, shrink(image, width, height));
        SPRITES.put(sk, sprite);
        return sprite;
    }

    /**
     * Area-average shrink of the 36 x 32 item pixels to {@code tw} x {@code th}. Colour is averaged over the opaque part each target
     * pixel covers; a target pixel is drawn (fully) when at least half of it is covered - the 667 item sprites have no partial alpha.
     */
    static int[] shrink(int[] src, int tw, int th) {
        int[] out = new int[tw * th];
        double sx = (double) W / tw;
        double sy = (double) H / th;
        for (int ty = 0; ty < th; ty++) {
            double y0 = ty * sy;
            double y1 = y0 + sy;
            for (int tx = 0; tx < tw; tx++) {
                double x0 = tx * sx;
                double x1 = x0 + sx;
                double r = 0, g = 0, b = 0, covered = 0;
                for (int y = (int) y0; y < Math.min(H, (int) Math.ceil(y1)); y++) {
                    double wy = Math.min(y1, y + 1) - Math.max(y0, y);
                    if (wy <= 0) {
                        continue;
                    }
                    for (int x = (int) x0; x < Math.min(W, (int) Math.ceil(x1)); x++) {
                        double wx = Math.min(x1, x + 1) - Math.max(x0, x);
                        if (wx <= 0) {
                            continue;
                        }
                        int p = src[y * W + x];
                        if (p == 0) {
                            continue;
                        }
                        double w = wx * wy;
                        r += ((p >> 16) & 0xFF) * w;
                        g += ((p >> 8) & 0xFF) * w;
                        b += (p & 0xFF) * w;
                        covered += w;
                    }
                }
                if (covered >= sx * sy * 0.5) {
                    int ri = (int) Math.round(r / covered);
                    int gi = (int) Math.round(g / covered);
                    int bi = (int) Math.round(b / covered);
                    int rgb = (ri << 16) | (gi << 8) | bi;
                    // 0 means "transparent" to the 667 sprites: a pure black pixel stays black as 1.
                    out[ty * tw + tx] = 0xFF000000 | (rgb == 0 ? 1 : rgb);
                }
            }
        }
        return out;
    }
}
