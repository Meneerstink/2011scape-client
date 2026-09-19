package com.jagex.game.runetek6.config.hitmarktype;

/**
 * RSPS venom colours (owner 2026-09-19, pictures "Venomed" and "venom hit" in C:\RSPS\foto, both OSRS screenshots).
 * The revision-667 cache has no venom art, so the poison art is re-coloured: every palette colour keeps its brightness
 * relative to the brightest colour of the sprite and takes the OSRS venom hue, the brightest shade becoming exactly the
 * brightest colour measured in the OSRS picture.
 */
public final class VenomPalette {

    /** Brightest colour of the OSRS venom hitsplat (picture "Venomed": body 0x142F28, rim 0x204B3F). */
    public static final int HITSPLAT = 0x204B3F;

    /** Brightest colour of the OSRS venom hitpoints-orb fill (picture "venom hit": 0x24573D down to 0x0B1B13). */
    public static final int ORB = 0x24573D;

    private VenomPalette() {
    }

    private static int luminance(int rgb) {
        return (((rgb >> 16) & 0xFF) * 299 + ((rgb >> 8) & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
    }

    /** Re-colours [palette] in place; index 0 (transparent) is left alone. */
    public static void tint(int[] palette, int target) {
        if (palette == null) {
            return;
        }
        int max = 1;
        for (int i = 1; i < palette.length; i++) {
            max = Math.max(max, luminance(palette[i]));
        }
        int tr = (target >> 16) & 0xFF;
        int tg = (target >> 8) & 0xFF;
        int tb = target & 0xFF;
        for (int i = 1; i < palette.length; i++) {
            int l = luminance(palette[i]);
            int r = tr * l / max;
            int g = tg * l / max;
            int b = tb * l / max;
            int rgb = (r << 16) | (g << 8) | b;
            palette[i] = rgb == 0 ? 0x010101 : rgb; // 0 would be read as transparent
        }
    }
}
