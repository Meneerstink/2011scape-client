import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;

import java.util.ArrayList;
import java.util.List;

/**
 * OSRS XP drops (N207, owner 2026-10-22: "OSRS XP drops (XP van de hit vliegt omhoog), instelling voor totaal-XP aan/uit"): every
 * experience gain shows "+N" with the skill's icon at the right of the game view and floats up to just under the XP counter. What
 * one hit gives in several skills in the same moment (Attack + Hitpoints) is one drop with every icon and the total, as OSRS
 * does. The drops do not depend on the counter: "Toggle XP Total" on the XP orb still hides or shows the total, the drop of each
 * hit is always there: with the total off these OSRS drops show the XP of each hit; with the total on the game's own 2011 counter
 * shows the total and its own drop under the orb, so these stay away (never two drops of one hit).
 *
 * The gain is the difference between two UPDATE_STAT values of a skill; the first value of a skill after a login (the full
 * stat sync) is only remembered, so a login never shows a drop of the whole total.
 */
final class RspsXpDrops {

    /** Gains this close together (one hit's packets arrive in the same cycle) are one drop. */
    static final long GROUP_MS = 60L;
    /** A drop rises {@link #RISE} px in {@link #LIFE_MS}, ending {@link #END_BELOW_TOP} px below the top of the game view. */
    static final long LIFE_MS = 1500L;
    static final int RISE = 100;
    static final int END_BELOW_TOP = 80;
    static final int RIGHT_MARGIN = 10;
    private static final int ICON = 14;
    private static final int LINE = 16;
    private static final int COLOUR = 0xFFFFFF;

    static final class Drop {
        final List<Integer> skills = new ArrayList<Integer>();
        long amount;
        final long start;

        Drop(long start) {
            this.start = start;
        }
    }

    private static final int SKILLS = 25;
    private static final boolean[] known = new boolean[SKILLS];
    private static final int[] last = new int[SKILLS];
    static final List<Drop> drops = new ArrayList<Drop>();

    /** The gameframe's XP total box ("XP:", 2011 counter): resizable 746:190, fixed 548:27. */
    static final int COUNTER_RESIZABLE = (746 << 16) | 190;
    static final int COUNTER_FIXED = (548 << 16) | 27;
    /** When the total box was last drawn (InterfaceManager.draw): it is on while drawn in the last {@link #COUNTER_MS}. */
    static volatile long counterDrawnAt;
    static final long COUNTER_MS = 250L;

    private RspsXpDrops() {
    }

    /** InterfaceManager.draw: a component that is not hidden is being drawn. */
    static void drawn(Component component) {
        if (component.slot == COUNTER_RESIZABLE || component.slot == COUNTER_FIXED) {
            counterDrawnAt = System.currentTimeMillis();
        }
    }

    static boolean counterShown(long now) {
        return now - counterDrawnAt < COUNTER_MS;
    }

    /** Login, logout, reconnect: the next value of every skill is the sync again. */
    static synchronized void reset() {
        java.util.Arrays.fill(known, false);
        drops.clear();
    }

    /** UPDATE_STAT (network thread): [xp] is the skill's new total experience. */
    static void stat(int skill, int xp) {
        stat(skill, xp, System.currentTimeMillis());
    }

    static synchronized void stat(int skill, int xp, long now) {
        if (skill < 0 || skill >= SKILLS) {
            return;
        }
        boolean seen = known[skill];
        int before = last[skill];
        known[skill] = true;
        last[skill] = xp;
        if (!seen || xp <= before) {
            return;
        }
        Drop drop = drops.isEmpty() ? null : drops.get(drops.size() - 1);
        if (drop == null || now - drop.start > GROUP_MS) {
            drop = new Drop(now);
            drops.add(drop);
        }
        if (!drop.skills.contains(skill)) {
            drop.skills.add(skill);
        }
        drop.amount += xp - before;
    }

    /** Height above its end point of a drop [age] ms old (0 at the end, null-free: callers drop it after LIFE_MS). */
    static int rise(long age) {
        return (int) (RISE - RISE * Math.min(age, LIFE_MS) / LIFE_MS);
    }

    static String text(Drop drop) {
        return "+" + String.format(java.util.Locale.US, "%,d", drop.amount);
    }

    /** The scene pass (RspsHud.render, clipped to the game view). */
    static synchronized void draw(int viewX, int viewY, int viewWidth, int viewHeight) {
        if (drops.isEmpty() || Toolkit.active == null) {
            return;
        }
        long now = System.currentTimeMillis();
        drops.removeIf(d -> now - d.start >= LIFE_MS);
        if (counterShown(now)) {
            return;
        }
        Font font = RspsHud.font();
        FontMetrics metrics = RspsHud.metrics();
        int right = viewX + viewWidth - RIGHT_MARGIN;
        int endY = viewY + END_BELOW_TOP;
        // Newest drop lowest; an older one stays at least a line above it (fast hits never write over each other).
        int limit = Integer.MAX_VALUE;
        for (int i = drops.size() - 1; i >= 0; i--) {
            Drop drop = drops.get(i);
            int baseline = Math.min(endY + rise(now - drop.start), limit);
            limit = baseline - LINE;
            if (baseline > viewY + viewHeight || baseline < viewY + ICON) {
                continue;
            }
            String value = text(drop);
            int x = right - RspsHud.width(metrics, value);
            RspsHud.text(font, value, x, baseline, COLOUR, false);
            for (int s = drop.skills.size() - 1; s >= 0; s--) {
                x -= ICON + 2;
                Sprite icon = RspsSprites.sprite(RspsSprites.skillIcon(drop.skills.get(s)));
                if (icon != null) {
                    icon.render(x, baseline - 12, ICON, ICON);
                }
            }
        }
    }
}
