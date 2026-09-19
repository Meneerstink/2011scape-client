package com.jagex.game.runetek6.config.hitmarktype;

import com.jagex.IndexedImage;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * RSPS OSRS hitsplats the revision-667 cache has no art for (owner 2026-09-18/19: venom and burn exactly like OSRS).
 * The type is decoded from a cache base type (font, text colour, text offset, duration) and only its art is replaced by the
 * real OSRS hitsplat sprite, exported unchanged from the pinned OSRS cache 2686 into {@code /hitsplats/<name>.png}:
 * venom = OSRS hitsplat 5 (sprite 1632), burn = OSRS hitsplat 74 (sprite 4767). OSRS draws both as one background sprite
 * with the number centred on it, so the sprite is served as the single "inner" part (no icon / left / right).
 * Without the resource the venom type falls back to the poison art re-coloured in the OSRS venom teal ({@link VenomPalette}).
 */
public final class VenomHitmarkType extends HitmarkType {
    private final String art;
    private final boolean tintFallback;

    private Toolkit builtFor;
    private Sprite osrsSprite;
    private Sprite iconSprite;
    private Sprite innerSprite;
    private Sprite leftSprite;
    private Sprite rightSprite;

    /** The venom splat (server hit type {@link HitmarkTypeList#VENOM_ID}). */
    public VenomHitmarkType() {
        this("venom", true);
    }

    public VenomHitmarkType(String art, boolean tintFallback) {
        this.art = art;
        this.tintFallback = tintFallback;
    }

    private void ensure(Toolkit toolkit) {
        if (toolkit == null || toolkit == this.builtFor) {
            return;
        }
        this.builtFor = toolkit;
        this.osrsSprite = osrs(toolkit, this.art);
        if (this.osrsSprite == null && this.tintFallback) {
            this.iconSprite = tinted(toolkit, this.icon);
            this.innerSprite = tinted(toolkit, this.inner);
            this.leftSprite = tinted(toolkit, this.left);
            this.rightSprite = tinted(toolkit, this.right);
        }
    }

    private static Sprite osrs(Toolkit toolkit, String name) {
        try (InputStream in = VenomHitmarkType.class.getResourceAsStream("/hitsplats/" + name + ".png")) {
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
            return toolkit.createSprite(width, width, height, pixels);
        } catch (Exception e) {
            return null;
        }
    }

    private Sprite tinted(Toolkit toolkit, int spriteId) {
        if (spriteId < 0 || this.myList == null || !this.myList.sprites.fileready(spriteId)) {
            return null;
        }
        try {
            IndexedImage image = IndexedImage.loadFirst(this.myList.sprites, spriteId);
            if (image == null || image.palette == null) {
                return null;
            }
            VenomPalette.tint(image.palette, VenomPalette.HITSPLAT);
            return toolkit.createSprite(image, true);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public Sprite getIcon(Toolkit toolkit) {
        ensure(toolkit);
        if (this.osrsSprite != null) {
            return null;
        }
        return this.iconSprite != null ? this.iconSprite : super.getIcon(toolkit);
    }

    @Override
    public Sprite getInner(Toolkit toolkit) {
        ensure(toolkit);
        if (this.osrsSprite != null) {
            return this.osrsSprite;
        }
        return this.innerSprite != null ? this.innerSprite : super.getInner(toolkit);
    }

    @Override
    public Sprite getLeft(Toolkit toolkit) {
        ensure(toolkit);
        if (this.osrsSprite != null) {
            return null;
        }
        return this.leftSprite != null ? this.leftSprite : super.getLeft(toolkit);
    }

    @Override
    public Sprite getRight(Toolkit toolkit) {
        ensure(toolkit);
        if (this.osrsSprite != null) {
            return null;
        }
        return this.rightSprite != null ? this.rightSprite : super.getRight(toolkit);
    }
}
