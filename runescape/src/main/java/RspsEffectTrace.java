import com.jagex.core.util.RspsTrace;
import com.jagex.graphics.Model;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * N606 12-11 (owner: "veel mechanics en effecten zijn gewoon niet zichtbaar ... alle grafische dingen loggen tracen zo uitgebreid
 * mogelijk"): follows every projectile and tile graphic from the packet to the screen. One "[effect]" line per event in the client log
 * (only with the debug toolkit's traces on, {@link RspsTrace#ENABLED}); the analyser work/claude-scripts/effect_report.py joins them
 * with the server's [avtrace] lines.
 * <ul>
 *   <li>recv: the packet built the effect (kind, spotanim id, tile, level, the player's level, start/end clock);</li>
 *   <li>replaced: a tile graphic overwrote a still-running one on the same tile (the client keeps ONE per tile);</li>
 *   <li>end: the effect expired - how many frames it was put in the scene, how many times it was rendered with a model, how often its
 *   model was missing, how often it sat below the ground, its lowest height over the ground and its largest model radius; PROBLEM when
 *   it was never rendered (with the most likely reason).</li>
 * </ul>
 */
public final class RspsEffectTrace {

    private static final class Rec {
        String kind;
        int id, tileX, tileZ, level, playerLevel;
        long born;
        int scene, drawn, modelNull, below, otherLevel;
        int minAboveGround = Integer.MAX_VALUE, maxRadius;
        int faces = -1;
    }

    private static final Map<Object, Rec> live = new IdentityHashMap<>();

    private RspsEffectTrace() {
    }

    public static void recv(Object effect, String kind, int id, int tileX, int tileZ, int level) {
        if (!RspsTrace.ENABLED || effect == null) {
            return;
        }
        Rec r = new Rec();
        r.kind = kind;
        r.id = id;
        r.tileX = tileX;
        r.tileZ = tileZ;
        r.level = level;
        r.playerLevel = PlayerEntity.self == null ? -1 : PlayerEntity.self.level;
        r.born = System.currentTimeMillis();
        live.put(effect, r);
        AvTrace.log("[effect] recv " + kind + " id=" + id + " tile=" + abs(tileX, tileZ) + " level=" + level + " playerLevel=" + r.playerLevel
            + " clock=" + com.jagex.core.util.TimeUtils.clock);
    }

    public static void replaced(int oldId, int newId, int tileX, int tileZ, int level) {
        if (RspsTrace.ENABLED) {
            AvTrace.log("[effect] PROBLEM replaced tilegfx id=" + oldId + " by id=" + newId + " tile=" + abs(tileX, tileZ) + " level=" + level
                + " (the client keeps one tile graphic per tile - the first one vanished early)");
        }
    }

    /** Put in the scene this frame (per-frame update loop). */
    public static void scene(Object effect, int level, int y, int x, int z) {
        Rec r = RspsTrace.ENABLED ? live.get(effect) : null;
        if (r == null) {
            return;
        }
        r.scene++;
        if (PlayerEntity.self != null && level != PlayerEntity.self.level) {
            r.otherLevel++;
        }
        int ground = Static102.averageHeight(level, x, z);
        int above = ground - y; // y grows downwards in this engine
        r.minAboveGround = Math.min(r.minAboveGround, above);
        if (above < -8) {
            r.below++;
        }
    }

    /** render(): the model the scene asked for, or null. */
    public static void render(Object effect, Model model) {
        Rec r = RspsTrace.ENABLED ? live.get(effect) : null;
        if (r == null) {
            return;
        }
        if (model == null) {
            r.modelNull++;
        } else {
            r.drawn++;
            r.maxRadius = Math.max(r.maxRadius, model.ma());
        }
    }

    public static void end(Object effect) {
        Rec r = RspsTrace.ENABLED ? live.remove(effect) : null;
        if (r == null) {
            return;
        }
        String reason = null;
        if (r.drawn == 0) {
            if (r.scene == 0) reason = "never put in the scene (start delay never reached, or not animating)";
            else if (r.modelNull > 0) reason = "model never available (" + r.modelNull + "x null: definition/model/animation not loaded)";
            else if (r.otherLevel == r.scene) reason = "always on another level than the player (level " + r.level + ", player " + r.playerLevel + ")";
            else reason = "in the scene " + r.scene + "x but never rendered (out of view / culled)";
        } else if (r.below * 2 > r.scene) {
            reason = "mostly BELOW the ground (" + r.below + "/" + r.scene + " frames, lowest " + r.minAboveGround + ")";
        } else if (r.drawn < 2) {
            reason = "rendered only " + r.drawn + " frame(s)";
        }
        AvTrace.log("[effect] " + (reason != null ? "PROBLEM " : "") + "end " + r.kind + " id=" + r.id + " tile=" + abs(r.tileX, r.tileZ)
            + " level=" + r.level + " life=" + (System.currentTimeMillis() - r.born) + "ms scene=" + r.scene + " drawn=" + r.drawn
            + " modelNull=" + r.modelNull + " below=" + r.below + " otherLevel=" + r.otherLevel + " minAboveGround="
            + (r.minAboveGround == Integer.MAX_VALUE ? "-" : String.valueOf(r.minAboveGround)) + " radius=" + r.maxRadius
            + (reason != null ? " reason=" + reason : ""));
    }

    /** Scene-local tile -> world tile, so client lines match the server's coordinates. */
    private static String abs(int localX, int localZ) {
        return (localX + WorldMap.areaBaseX) + "," + (localZ + WorldMap.areaBaseZ);
    }
}
