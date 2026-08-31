public final class OldStyleStatDisplay {
    public static final int HITPOINTS = 3;
    public static final int PRAYER = 5;

    private OldStyleStatDisplay() {
    }

    /** Returns old-style visible HP/Prayer while leaving every stored stat unchanged. */
    public static int value(int skill, int rawValue) {
        if (skill != HITPOINTS && skill != PRAYER) {
            return rawValue;
        }
        return OldStyleHitsplatDisplay.value(rawValue);
    }
}
