import com.jagex.Client;
import com.jagex.core.constants.PathDestination;
import com.jagex.core.util.RspsTrace;
import com.jagex.core.util.TimeUtils;
import com.jagex.game.PathFinder;
import com.jagex.game.runetek6.config.seqtype.SeqType;

/**
 * RSPS obstacle / noclip trace (owner priority #1, 2026-10-03): what the real client does with the local player's
 * movement. On with C:/RSPS/trace.on at client start, like the other client traces; every line goes to the launcher's
 * client-*.out.log as {@code [walktrace <ms>] ...}. It records the three things a server log can never show:
 * <ul>
 * <li>{@code click}: a walk click - the logical tile ({@code pathX[0]}), the drawn tile (the rendered x/z), the target,
 * whether the client's own route finder has a route from the logical tile, and the action animation that is playing;</li>
 * <li>{@code move} / {@code teleport} / {@code exactmove}: every movement the server sent for the local player and the
 * logical tile it produced;</li>
 * <li>{@code PROBLEM body held}: the body is not drawn along its path because an action animation with walking
 * precedence 0 (or a looping one) is still playing - the "stuck after a rock" the owner sees while the server has long
 * moved on (bounded: once per 30 client cycles).</li>
 * </ul>
 */
public final class WalkTrace {

    public static final boolean ENABLED = RspsTrace.ENABLED;

    private static int lastHeldClock = -1000;

    private static int lastHeldAnim = -2;

    private static final int[] routeX = new int[128];

    private static final int[] routeZ = new int[128];

    private WalkTrace() {
    }

    private static void log(String message) {
        System.out.println("[walktrace " + System.currentTimeMillis() + "] " + message);
    }

    private static String self(PlayerEntity p) {
        int lx = p.pathX[0] + WorldMap.areaBaseX;
        int lz = p.pathZ[0] + WorldMap.areaBaseZ;
        int dx = (p.x >> 9) + WorldMap.areaBaseX;
        int dz = (p.z >> 9) + WorldMap.areaBaseZ;
        return "logical=" + lx + "," + lz + "," + p.level + " drawn=" + dx + "," + dz + " pathPointer=" + p.pathPointer
            + " animPathPointer=" + p.animationPathPointer + " delayedWalk=" + p.delayedWalkingTicks
            + " exact=" + (p.exactMoveT1 > TimeUtils.clock ? "phase1" : p.exactMoveT2 >= TimeUtils.clock ? "phase2" : "none")
            + " clock=" + TimeUtils.clock + anim(p);
    }

    private static String anim(PathingEntity p) {
        SeqType seq = p.actionAnimator.isAnimating() ? p.actionAnimator.getAnimation() : null;
        if (seq == null) {
            return " anim=none";
        }
        return " anim=" + seq.id + "(walkPrec=" + seq.walkingPrecedence + " animPrec=" + seq.animatingPrecedence
            + " loopOffset=" + seq.loopOffset + " loop=" + p.actionAnimator.loopCount + "/" + seq.maxLoops
            + " frame=" + p.actionAnimator.currentFrame + "/" + (seq.frames == null ? 0 : seq.frames.length) + ")";
    }

    /** A walk click (MiniMenu WALK): [x],[z] are map-local; the packet is sent regardless of the local route. */
    public static void click(int x, int z, int clickType) {
        if (!ENABLED) {
            return;
        }
        PlayerEntity p = PlayerEntity.self;
        if (p == null) {
            return;
        }
        int route;
        try {
            route = PathFinder.findPath(Client.collisionMaps[p.level], routeZ, routeX, p.pathX[0], p.pathZ[0], p.getSize(), x, z, 0, 0, PathDestination.ON, 0, 0, true);
        } catch (RuntimeException e) {
            route = -2;
        }
        String verdict = route == -1 ? " PROBLEM no local route from the logical tile (client collision encloses it)" : "";
        log("click target=" + (x + WorldMap.areaBaseX) + "," + (z + WorldMap.areaBaseZ) + " type=" + clickType + " localRoute=" + route
            + " " + self(p) + verdict);
    }

    /** A movement the server sent for the local player ([kind] move / teleport), before the client applies it. */
    public static void moved(PlayerEntity p, String kind, int x, int z, int speed) {
        if (!ENABLED || p != PlayerEntity.self) {
            return;
        }
        if (p.pathPointer == 0 && p.pathX[0] == x && p.pathZ[0] == z) {
            return; // the login screen re-places the player every cycle; nothing moved
        }
        log(kind + " to=" + (x + WorldMap.areaBaseX) + "," + (z + WorldMap.areaBaseZ) + " speed=" + speed + " before: " + self(p));
    }

    /** An EXACT_MOVE block for the local player, after decoding (endpoints are already absolute map-local tiles). */
    public static void exactMove(PlayerEntity p) {
        if (!ENABLED || p != PlayerEntity.self) {
            return;
        }
        log("exactmove from=" + (p.exactMoveX1 + WorldMap.areaBaseX) + "," + (p.exactMoveZ1 + WorldMap.areaBaseZ)
            + " to=" + (p.exactMoveX2 + WorldMap.areaBaseX) + "," + (p.exactMoveZ2 + WorldMap.areaBaseZ)
            + " t1=" + (p.exactMoveT1 - TimeUtils.clock) + " t2=" + (p.exactMoveT2 - TimeUtils.clock) + " dir=" + p.exactMoveDirection
            + " " + self(p));
    }

    /** The body stayed where it is this cycle because of an action animation while a path is pending (Static256.movementTick). */
    public static void held(PathingEntity entity, String why) {
        if (!ENABLED || entity != PlayerEntity.self) {
            return;
        }
        PlayerEntity p = (PlayerEntity) entity;
        int animId = p.actionAnimator.isAnimating() ? p.actionAnimator.getAnimationId() : -1;
        if (animId == lastHeldAnim && TimeUtils.clock - lastHeldClock < 30) {
            return;
        }
        lastHeldClock = TimeUtils.clock;
        lastHeldAnim = animId;
        log("PROBLEM body held by action animation (" + why + "): the server moves the player but the client draws him standing still - " + self(p));
    }

    /** An exact move finished: where the body is drawn against the logical tile. */
    public static void exactMoveEnd(PathingEntity entity) {
        if (!ENABLED || entity != PlayerEntity.self) {
            return;
        }
        log("exactmove end " + self((PlayerEntity) entity));
    }
}
