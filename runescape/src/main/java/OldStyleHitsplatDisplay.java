public final class OldStyleHitsplatDisplay {
    /**
     * RCV-010 A6 (owner live 2026-09-13): hitsplats on targets looked too small; the owner asked for them
     * about 10% bigger, and on 2026-09-18 another 5% (110 * 1.05 = 115.5, rounded to 116). Applied to every hitsplat
     * sprite part through {@link ScaledHitsplatSprite}; the damage number keeps its cache font.
     */
    public static final int SIZE_PERCENT = 116;

    private OldStyleHitsplatDisplay() {
    }

    /** Compatibility hook; the server now publishes real 1:1 hit damage. */
    public static int value(int rawValue) {
        return rawValue;
    }

    /** Scales a hitsplat pixel size or offset by {@link #SIZE_PERCENT}, rounded to the nearest pixel. */
    public static int size(int pixels) {
        return pixels >= 0 ? (pixels * SIZE_PERCENT + 50) / 100 : -((-pixels * SIZE_PERCENT + 50) / 100);
    }
}
