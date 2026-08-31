public final class OldStyleHitsplatDisplay {
    private OldStyleHitsplatDisplay() {
    }

    /** Converts revision-667 damage to the pre-Constitution display scale. */
    public static int value(int rawValue) {
        if (rawValue <= 0) {
            return rawValue;
        }
        return 1 + (rawValue - 1) / 10;
    }
}
