public final class OldStyleHitsplatDisplay {
    /**
     * RCV-010 A6 (owner live 2026-09-13): hitsplats on targets looked too small; the owner asked for them
     * about 10% bigger, on 2026-09-18 another 5% (110 * 1.05 = 115.5, rounded to 116) and on 2026-09-19 another
     * 5% (116 * 1.05 = 121.8, rounded to 122). Applied to every hitsplat sprite part through
     * {@link ScaledHitsplatSprite}; the damage number keeps its cache font.
     */
    public static final int SIZE_PERCENT = 122;

    /** Vertical distance between two stacked hitsplats at 100 %, when the cache gives none (its own default step). */
    private static final int DEFAULT_STACK_STEP = 20;

    /** Extra pixels between two stacked splats so the larger splats never touch. */
    private static final int STACK_GAP = 2;

    /**
     * Height on the target the hitsplats are anchored to, as a percentage of its model height. The 667 client uses 50 % (the neck
     * area once the cache y offset is added); the owner asked on 2026-09-25 for 5 % lower, on the chest.
     */
    public static final int ANCHOR_HEIGHT_PERCENT = 45;

    private OldStyleHitsplatDisplay() {
    }

    /** Height above the ground the hitsplats of an entity [entityHeight] tall are drawn at ({@link #ANCHOR_HEIGHT_PERCENT}). */
    public static int anchorHeight(int entityHeight) {
        return entityHeight * ANCHOR_HEIGHT_PERCENT / 100;
    }

    /** Compatibility hook; the server now publishes real 1:1 hit damage. */
    public static int value(int rawValue) {
        return rawValue;
    }

    /** Scales a hitsplat pixel size or offset by {@link #SIZE_PERCENT}, rounded to the nearest pixel. */
    public static int size(int pixels) {
        return pixels >= 0 ? (pixels * SIZE_PERCENT + 50) / 100 : -((-pixels * SIZE_PERCENT + 50) / 100);
    }

    /**
     * Horizontal offset of hitsplat [index]: every splat is centred on the entity (owner 2026-09-19, picture "burn damage and
     * hp center": the second hit sat to the right of the first). The cache's GraphicsDefaults x offsets are not used.
     */
    public static int offsetX(int[] cacheX, int index) {
        return 0;
    }

    /**
     * Vertical offset of hitsplat [index]: the first sits where the cache puts it, every next one directly above the previous one,
     * one scaled splat height apart (the cache step was sized for 100 % splats, so enlarged splats overlapped).
     */
    public static int offsetY(int[] cacheY, int index) {
        int base = cacheY != null && cacheY.length > 0 ? cacheY[0] : 0;
        if (index <= 0) {
            return base;
        }
        int step = DEFAULT_STACK_STEP;
        if (cacheY != null && cacheY.length > 1 && cacheY[1] != cacheY[0]) {
            step = Math.abs(cacheY[1] - cacheY[0]);
        }
        return base - index * (size(step) + STACK_GAP);
    }
}
