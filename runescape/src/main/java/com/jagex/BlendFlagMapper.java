package com.jagex;


import java.util.HashMap;
import java.util.Map;

/**
 * Translates a sequence's blend flags ({@code SeqType.blendFlags}) to a second frame base.
 * <p>
 * Blend flags are indexed by the transforms of the action sequence's own frame base. When the action and the movement
 * animation it is blended with share a base (every native 667 sequence) the same array serves both passes of
 * {@code Model.method7477}. The imported OSRS attack sequences use their own bases (268/269), so their flags were applied to
 * the transform indices of the 667 walk/run base 256: arbitrary walk transforms were skipped, the legs stopped animating and a
 * player attacking while moving slid over the ground ("zweven", owner 2026-09-25, worst with 2-tick weapons that animate on
 * almost every step).
 * <p>
 * The link between two bases is the model's skin groups ({@code AnimBase.anIntArrayArray196}): a movement transform belongs to
 * the action when every skin group it moves is moved by a flagged action transform. Transforms that also move other groups
 * (whole-body offsets) stay with the movement animation. Origin transforms (type 0) are always applied by both passes.
 */
public final class BlendFlagMapper {
    private static final Map<Long, boolean[]> CACHE = new HashMap<>();

    private BlendFlagMapper() {
    }

    /** [flags] for [target] when they were written for [source]; the same array when both are the same base. */
    public static boolean[] map(boolean[] flags, AnimBase source, AnimBase target) {
        if (flags == null || source == null || target == null || source == target) {
            return flags;
        }
        long key = ((long) System.identityHashCode(flags) << 32) ^ ((long) source.id << 16) ^ target.id;
        boolean[] cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        boolean[] covered = new boolean[256];
        for (int t = 0; t < source.anInt7690 && t < flags.length; t++) {
            if (!flags[t] || source.anIntArray619[t] == 0) {
                continue;
            }
            for (int group : source.anIntArrayArray196[t]) {
                if (group >= 0 && group < covered.length) {
                    covered[group] = true;
                }
            }
        }
        boolean[] mapped = new boolean[Math.max(256, target.anInt7690)];
        for (int t = 0; t < target.anInt7690; t++) {
            int[] groups = target.anIntArrayArray196[t];
            if (target.anIntArray619[t] == 0 || groups == null || groups.length == 0) {
                continue;
            }
            boolean all = true;
            for (int group : groups) {
                if (group < 0 || group >= covered.length || !covered[group]) {
                    all = false;
                    break;
                }
            }
            mapped[t] = all;
        }
        CACHE.put(key, mapped);
        return mapped;
    }
}
