import com.jagex.IndexedImage;
import com.jagex.js5.js5;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * RSPS Deadman sprites that this 2011 cache does not carry, built as real toolkit sprites so they
 * behave like any cache sprite in the software, OpenGL and DirectX renderers.
 *
 * Every bundled {@code /deadman/*.png} is a lossless 1:1 export of an OSRS cache sprite (openrs2
 * cache 2686, sprite index 8), never a screenshot cut, so nothing here is scaled, smoothed or
 * redrawn:
 *
 * <ul>
 *   <li>{@code pk_keys_1..5}: headicons_pk (group 439) frames 8..12 - the skull with 1..5 loot
 *       keys, verified pixel-identical to the owner reference "skull and key design";
 *       {@code pk_keys_0} is frame 8 without its key rows (the bare skull).</li>
 *   <li>{@link #TIER_PALETTES}: the bone shades and eye colours of headicons_pk frames 28..32,
 *       verified pixel-identical to the owner reference "exact colors". The head skull is the
 *       key-design skull with exactly these colours substituted shade for shade. These are the
 *       YELLOW-EYED skulls of a really skulled player (OSRS Deadman: Annihilation).</li>
 *   <li>{@link #PLAIN_TIER_PALETTES}: the same tiers from headicons_pk frames 21..25 - identical
 *       bone shades, eyes in the outline colour 0x000001 (colour histogram of the OSRS frames,
 *       2026-09-26: the yellow/orange pixels of 21..25 are only the coin bars). Every player shows
 *       one of these; the yellow-eyed one only while really skulled.</li>
 *   <li>{@code hud_skull} (1046), {@code hud_nocircle} (1047), {@code hud_exclamation} (1239),
 *       {@code hud_multi} (442 frame 0): the Deadman zone HUD icons.</li>
 * </ul>
 *
 * The cache's own white/red headicons_pk frames are never drawn: every skull id the server can
 * send resolves to one of the five tier skulls.
 */
final class DeadmanIcons {

    static final int FIRST_TIER_ID = 8;
    static final int LAST_TIER_ID = 12;
    static final int MAX_KEYS = 5;

    /**
     * Server skull ids 24..28 are the yellow-eyed tiers 8..12 (the server adds this bit; on the
     * wire it travels as bit 7 of the pk-icon byte, see PlayerEntity).
     */
    static final int SKULLED_FLAG = 16;

    /** The key-design skull's own colours: mid, shadow, highlight, eye bright, eye dark. */
    private static final int[] BASE_PALETTE = { 0xA9AE9E, 0x868E72, 0xC4C7CA, 0xDB2F2C, 0x5B100D };

    /**
     * Index 0..4 = server ids 8..12 (red, blue, green, iron, bronze); per tier the replacement for
     * each {@link #BASE_PALETTE} entry, read from OSRS headicons_pk frames 32, 31, 30, 29, 28.
     */
    private static final int[][] TIER_PALETTES = {
        { 0xE63335, 0xA01F18, 0xFC475B, 0xFFC90E, 0xFF810E },
        { 0x5C83BB, 0x3E4A48, 0x6091EF, 0xFFC90E, 0xFF810E },
        { 0x40922D, 0x2D5816, 0x45AB47, 0xFFC90E, 0xFF810E },
        { 0xA9AE9E, 0x868E72, 0xC4C7CA, 0xFFC90E, 0xFF810E },
        { 0x9D5544, 0x61342A, 0xB86E5D, 0xFFC90E, 0xFF810E },
    };

    /** Index 0..4 as above, headicons_pk frames 25, 24, 23, 22, 21: dark eyes (not skulled). */
    private static final int[][] PLAIN_TIER_PALETTES = {
        { 0xE63335, 0xA01F18, 0xFC475B, 0x000001, 0x000001 },
        { 0x5C83BB, 0x3E4A48, 0x6091EF, 0x000001, 0x000001 },
        { 0x40922D, 0x2D5816, 0x45AB47, 0x000001, 0x000001 },
        { 0xA9AE9E, 0x868E72, 0xC4C7CA, 0x000001, 0x000001 },
        { 0x9D5544, 0x61342A, 0xB86E5D, 0x000001, 0x000001 },
    };

    private static final int BRONZE_TIER = LAST_TIER_ID - FIRST_TIER_ID;

    private static Toolkit builtFor;
    private static Sprite[][] headSkulls;
    private static Sprite[][] plainHeadSkulls;
    private static Sprite skullSprite;
    private static Sprite guardedSprite;
    private static Sprite exclamationSprite;
    private static Sprite multiSprite;
    private static Sprite singleSprite;

    /**
     * The gravestone timer icon: OSRS sprite 2507 ("mod_icons_interface" 21, drawn by OSRS gravestone_hud_init), imported
     * into the cache as sprite 8249 by the server's DeathsOfficeInterfaceImportTool.
     */
    static final int GRAVE_ICON_SPRITE = 8249;
    private static Sprite graveSprite;
    private static Toolkit graveBuiltFor;

    /** Decoded image: width, height, ARGB pixels (alpha is 0 or 255). */
    private static final class Image {
        final int width;
        final int height;
        final int[] pixels;

        Image(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    private static Image loadPng(String name) {
        try (InputStream in = DeadmanIcons.class.getResourceAsStream("/deadman/" + name + ".png")) {
            if (in == null) {
                return null;
            }
            BufferedImage png = ImageIO.read(in);
            if (png == null) {
                return null;
            }
            int width = png.getWidth();
            int height = png.getHeight();
            int[] pixels = png.getRGB(0, 0, width, height, null, 0, width);
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] = (pixels[i] >>> 24) < 128 ? 0 : pixels[i] | 0xFF000000;
            }
            return new Image(width, height, pixels);
        } catch (Exception e) {
            return null;
        }
    }

    /** Drops fully transparent rows below the artwork; the width (and so the x alignment) is kept. */
    private static Image trimBottom(Image image) {
        int height = image.height;
        rows:
        while (height > 1) {
            for (int x = 0; x < image.width; x++) {
                if (image.pixels[(height - 1) * image.width + x] != 0) {
                    break rows;
                }
            }
            height--;
        }
        if (height == image.height) {
            return image;
        }
        int[] pixels = new int[image.width * height];
        System.arraycopy(image.pixels, 0, pixels, 0, pixels.length);
        return new Image(image.width, height, pixels);
    }

    private static Image recolour(Image image, int[] palette) {
        int[] pixels = image.pixels.clone();
        for (int i = 0; i < pixels.length; i++) {
            int rgb = pixels[i] & 0xFFFFFF;
            if (pixels[i] == 0) {
                continue;
            }
            for (int c = 0; c < BASE_PALETTE.length; c++) {
                if (rgb == BASE_PALETTE[c]) {
                    pixels[i] = 0xFF000000 | palette[c];
                    break;
                }
            }
        }
        return new Image(image.width, image.height, pixels);
    }

    /**
     * {@code top} drawn over {@code bottom} on a canvas the size of the larger one, both centred
     * (the 20x19 crossed swords under the 25x25 "no" circle).
     */
    private static Image overCentred(Image bottom, Image top) {
        int width = Math.max(bottom.width, top.width);
        int height = Math.max(bottom.height, top.height);
        int[] pixels = new int[width * height];
        Image[] layers = { bottom, top };
        for (Image layer : layers) {
            int dx = (width - layer.width) / 2;
            int dy = (height - layer.height) / 2;
            for (int y = 0; y < layer.height; y++) {
                for (int x = 0; x < layer.width; x++) {
                    int px = layer.pixels[x + y * layer.width];
                    if (px != 0) {
                        pixels[(x + dx) + (y + dy) * width] = px;
                    }
                }
            }
        }
        return new Image(width, height, pixels);
    }

    /** {@code top} drawn over {@code bottom}; both are the same 25x25 OSRS canvas. */
    private static Image over(Image bottom, Image top) {
        if (bottom.width != top.width || bottom.height != top.height) {
            return bottom;
        }
        int[] pixels = bottom.pixels.clone();
        for (int i = 0; i < pixels.length; i++) {
            if (top.pixels[i] != 0) {
                pixels[i] = top.pixels[i];
            }
        }
        return new Image(bottom.width, bottom.height, pixels);
    }

    private static Sprite sprite(Toolkit toolkit, Image image) {
        if (image == null) {
            return null;
        }
        try {
            return toolkit.createSprite(image.width, image.width, image.height, image.pixels);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void ensure() {
        Toolkit toolkit = Toolkit.active;
        if (toolkit == null || toolkit == builtFor) {
            return;
        }
        builtFor = toolkit;

        headSkulls = new Sprite[TIER_PALETTES.length][MAX_KEYS + 1];
        plainHeadSkulls = new Sprite[PLAIN_TIER_PALETTES.length][MAX_KEYS + 1];
        for (int keys = 0; keys <= MAX_KEYS; keys++) {
            Image base = loadPng("pk_keys_" + keys);
            if (base == null) {
                continue;
            }
            base = trimBottom(base);
            for (int tier = 0; tier < TIER_PALETTES.length; tier++) {
                headSkulls[tier][keys] = sprite(toolkit, recolour(base, TIER_PALETTES[tier]));
                plainHeadSkulls[tier][keys] = sprite(toolkit, recolour(base, PLAIN_TIER_PALETTES[tier]));
            }
        }

        Image skull = loadPng("hud_skull");
        Image noCircle = loadPng("hud_nocircle");
        skullSprite = sprite(toolkit, skull);
        guardedSprite = skull == null || noCircle == null ? null : sprite(toolkit, over(skull, noCircle));
        exclamationSprite = sprite(toolkit, loadPng("hud_exclamation"));
        Image multi = loadPng("hud_multi");
        multiSprite = sprite(toolkit, multi);
        // Single-way combat (owner 2026-09-18: "the multi and single icon on top of the HUD"): OSRS
        // has no single-way sprite (442 frame 1 is the singles-plus variant), so the single icon
        // is the crossed swords under the same OSRS "no" circle (1047) the Guarded state uses -
        // composed from exact OSRS sprites, no invented pixel art.
        singleSprite = multi == null || noCircle == null ? null : sprite(toolkit, overCentred(multi, noCircle));
    }

    /**
     * The head-icon skull for a server skull id and loot key count, or null when there is none.
     * Ids outside the tier range (the cache's default skull frames) resolve to the bronze tier, so
     * the default skull can never appear. {@code skulled} picks the yellow-eyed variant.
     */
    static Sprite headSkull(int pkIcon, int keys, boolean skulled) {
        if (pkIcon < 0) {
            return null;
        }
        ensure();
        Sprite[][] set = skulled ? headSkulls : plainHeadSkulls;
        if (set == null) {
            return null;
        }
        int tier = pkIcon >= FIRST_TIER_ID && pkIcon <= LAST_TIER_ID ? pkIcon - FIRST_TIER_ID : BRONZE_TIER;
        int count = Math.max(0, Math.min(keys, MAX_KEYS));
        return set[tier][count];
    }

    /** The gravestone timer icon, or null until the sprite archive can deliver it (retried until it can). */
    static Sprite grave() {
        Toolkit toolkit = Toolkit.active;
        if (toolkit == null) {
            return null;
        }
        if (graveSprite == null || graveBuiltFor != toolkit) {
            graveSprite = null;
            try {
                IndexedImage image = IndexedImage.loadFirst(js5.SPRITES, GRAVE_ICON_SPRITE);
                if (image != null) {
                    graveSprite = toolkit.createSprite(image, true);
                    graveBuiltFor = toolkit;
                }
            } catch (RuntimeException e) {
                graveSprite = null;
            }
        }
        return graveSprite;
    }

    /** The Deadman / Wilderness zone icon (OSRS sprite 1046). */
    static Sprite skull() {
        ensure();
        return skullSprite;
    }

    /** The Guarded zone icon: sprite 1046 under the red "no" circle 1047, as OSRS layers them. */
    static Sprite guarded() {
        ensure();
        return guardedSprite;
    }

    /** The red exclamation mark beside the Deadman skull (OSRS sprite 1239). */
    static Sprite exclamation() {
        ensure();
        return exclamationSprite;
    }

    /** The multicombat crossed swords (OSRS sprite 442 frame 0). */
    static Sprite multi() {
        ensure();
        return multiSprite;
    }

    /** The single-way combat icon: crossed swords (442:0) under the OSRS "no" circle (1047). */
    static Sprite single() {
        ensure();
        return singleSprite;
    }

    static void reset() {
        builtFor = null;
        headSkulls = null;
        plainHeadSkulls = null;
        skullSprite = null;
        guardedSprite = null;
        exclamationSprite = null;
        multiSprite = null;
        singleSprite = null;
    }

    private DeadmanIcons() {
        /* empty */
    }
}
