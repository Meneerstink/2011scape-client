/**
 * Guards N207 (owner 2026-10-22: OSRS XP drops - the XP of the hit floats up; the total counter stays its own toggle).
 */
public final class XpDropsRegressionTest {

    public static void main(String[] args) {
        RspsXpDrops.reset();
        // The login sync: every skill's first value is only remembered.
        for (int skill = 0; skill < 25; skill++) {
            RspsXpDrops.stat(skill, 13_034_431, 1_000L);
        }
        require(RspsXpDrops.drops.isEmpty(), "a login shows no drop of the whole total");

        // One melee hit: Attack +40 and Hitpoints +13 in the same moment -> one drop, both icons, +53.
        RspsXpDrops.stat(0, 13_034_471, 2_000L);
        RspsXpDrops.stat(3, 13_034_444, 2_010L);
        require(RspsXpDrops.drops.size() == 1, "one hit is one drop (" + RspsXpDrops.drops.size() + ")");
        RspsXpDrops.Drop hit = RspsXpDrops.drops.get(0);
        require(hit.amount == 53 && hit.skills.size() == 2 && hit.skills.get(0) == 0 && hit.skills.get(1) == 3, "Attack + Hitpoints, +53");
        require("+53".equals(RspsXpDrops.text(hit)), "shown as +53 (" + RspsXpDrops.text(hit) + ")");

        // The next hit, a tick later, is a drop of its own; a big one keeps its thousands separator.
        RspsXpDrops.stat(0, 13_036_471, 2_600L);
        require(RspsXpDrops.drops.size() == 2 && "+2,000".equals(RspsXpDrops.text(RspsXpDrops.drops.get(1))), "a later hit is a new drop, +2,000");

        // No gain (the same value again) or a lower value (::setxp, a reset) shows nothing.
        RspsXpDrops.stat(5, 13_034_431, 3_000L);
        RspsXpDrops.stat(6, 10, 3_000L);
        require(RspsXpDrops.drops.size() == 2, "no drop without a gain");

        // It rises from RISE px below its end to the end over LIFE_MS.
        require(RspsXpDrops.rise(0) == RspsXpDrops.RISE && RspsXpDrops.rise(RspsXpDrops.LIFE_MS) == 0 && RspsXpDrops.rise(RspsXpDrops.LIFE_MS / 2) == RspsXpDrops.RISE / 2, "rises evenly to its end");

        // The 2011 total box on (drawn this frame): it shows the total and its own drop, these stay away; off: these show.
        Component box = new Component();
        box.slot = (746 << 16) | 190;
        RspsXpDrops.counterDrawnAt = 0;
        require(!RspsXpDrops.counterShown(System.currentTimeMillis()), "total box not drawn: OSRS drops on");
        RspsXpDrops.drawn(box);
        require(RspsXpDrops.counterShown(System.currentTimeMillis()), "resizable total box drawn: OSRS drops off");
        box.slot = (548 << 16) | 27;
        RspsXpDrops.counterDrawnAt = 0;
        RspsXpDrops.drawn(box);
        require(RspsXpDrops.counterShown(System.currentTimeMillis()), "fixed total box drawn: OSRS drops off");
        require(!RspsXpDrops.counterShown(System.currentTimeMillis() + RspsXpDrops.COUNTER_MS), "total box toggled off: OSRS drops back");
        Component other = new Component();
        other.slot = (746 << 16) | 202;
        RspsXpDrops.counterDrawnAt = 0;
        RspsXpDrops.drawn(other);
        require(!RspsXpDrops.counterShown(System.currentTimeMillis()), "another component is not the total box");

        // A logout forgets everything: the next login is a sync again.
        RspsXpDrops.reset();
        RspsXpDrops.stat(0, 99, 5_000L);
        require(RspsXpDrops.drops.isEmpty(), "after a reset the first value is a sync again");
        System.out.println("XpDropsRegressionTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
