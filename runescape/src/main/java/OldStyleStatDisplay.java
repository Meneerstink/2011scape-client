/**
 * Compatibility hook for old-style hitpoint/prayer-point rendering.
 *
 * <p><b>Not</b> a conversion for skill levels, and deliberately not wired into the client script
 * {@code STAT} opcode. {@code Static581.statLevels} carries ordinary 1-99 skill levels; the
 * lifepoint and prayer-point totals are published separately in varbits 7198 and 9816.
 *
 * <p>The orb text hook remains at the text boundary for compatibility - see
 * {@link OldStyleOrbDisplay}.
 */
public final class OldStyleStatDisplay {
    public static final int HITPOINTS = 3;
    public static final int PRAYER = 5;

    private OldStyleStatDisplay() {
    }

    /** Returns the visible value without conversion; server totals are already 1:1. */
    public static int value(int skill, int rawValue) {
        if (skill != HITPOINTS && skill != PRAYER) {
            return rawValue;
        }
        return rawValue;
    }
}
