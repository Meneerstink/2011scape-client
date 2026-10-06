import com.jagex.Client;
import com.jagex.core.util.RspsTrace;
import com.jagex.core.util.TimeUtils;
import com.jagex.game.Location;
import com.jagex.game.camera.CameraMode;
import com.jagex.game.collision.CollisionFlag;
import com.jagex.game.collision.CollisionMap;

/**
 * RSPS camera collision (owner 2026-10-06, OWNER_QUESTIONS Q11 = A: "client change: pull the camera in / hide the walls in
 * between"). No donor has it: the 667 client, OSRS and RuneLite (reference/runelite CameraPlugin) all let the camera pass
 * through walls and only hide roofs; no client source in Donors/ or import-source/donors implements a camera collision.
 * The rule is therefore our own, in this one place:
 *
 * Every frame of the normal player-follow camera ({@link CameraMode#MODE_DEFAULT}, no cutscene, no orthographic view), while
 * the player stands on a tile under a roof (tile flag 0x4 on the player's level - the flag the client's own roof removal
 * reads, Static276.method3988), the horizontal line from the camera's focal point to the camera's computed position is marched
 * tile edge by tile edge over the player's collision map. Where it crosses a wall edge (the walk flags WALL_EAST/WEST/
 * NORTH/SOUTH of either tile) while the camera ray there is below the wall's top - the height of the level above at that
 * point, or 240 over the floor where the level above lies flat on it - or enters a tile with a solid loc (LOCATION) whose
 * model top is above the ray, the camera is pulled in along its own ray to {@link #MARGIN} short of that point. Pitch and
 * yaw stay as they are. Pulling in is immediate (no frame may show the far side of the wall); going back out is eased per
 * client cycle like Camera.moveToTick ({@code step + remaining * rate / 1000}).
 */
public final class CameraCollision {

    /** A fraction of the requested camera offset, in 1/65536. */
    static final int FULL = 65536;

    /** World units the camera stays on the player's side of a wall edge (horizontally). */
    static final int MARGIN = 32;

    /** The camera never comes closer to its focal point than this (world units, along its ray). */
    static final int MIN_DISTANCE = 48;

    /** Storey height when the level above has no heights of its own (lies flat on this one): the 667 default step. */
    static final int DEFAULT_STOREY = 240;

    /** A level above closer than this to the floor counts as "no heights of its own". */
    static final int MIN_STOREY = 64;

    /** Restore easing per client cycle, as Camera.moveToTick: {@code step + remaining * rate / 1000}. */
    static final int RESTORE_STEP = 256;
    static final int RESTORE_RATE = 100;

    /** Whether solid locs pull the live camera in (off: walls only, see LiveGrid.locTop). */
    static final boolean LOCS_BLOCK = false;

    /** No solid loc on a tile. */
    static final int NO_LOC = Integer.MAX_VALUE;

    /** What blocked the ray, for the trace. */
    static final int HIT_NONE = 0;
    static final int HIT_WALL = 1;
    static final int HIT_LOC = 2;

    /** The scene seen by the march. Tiles are scene-local; heights are world y (smaller = higher, as in the client). */
    interface Grid {
        /** A wall stands on the edge between neighbouring tiles (x, z) and (nx, nz). */
        boolean wallBetween(int x, int z, int nx, int nz);

        /** World y of the top of the walls at fine point (x, z). */
        int wallTop(int x, int z);

        /** World y of the top of a solid loc on tile (x, z), or {@link #NO_LOC}. */
        int locTop(int x, int z);

        /**
         * Whether the edge between tiles (x, z) and (nx, nz), crossed at fine point (px, pz) with the ray at world y [rayY],
         * blocks the camera. Default: a wall on the edge whose top is above the ray.
         */
        default boolean blocks(int x, int z, int nx, int nz, int px, int pz, double rayY) {
            return this.wallBetween(x, z, nx, nz) && rayY > this.wallTop(px, pz);
        }
    }

    private static int current = FULL;
    private static int lastClock = -1;
    private static long lastLogMs;

    /** Set by {@link #clearFraction} for the trace: the kind of blocker and the tile on the far side of it. */
    static int hitKind;
    static int hitTileX;
    static int hitTileZ;

    private CameraCollision() {
        /* utility class */
    }

    /**
     * The pure geometry: the part (0..{@link #FULL}) of the camera offset from focal point (fx, fy, fz) to camera (cx, cy, cz)
     * that stays {@link #MARGIN} on the focal side of the first blocking wall edge or solid loc. {@link #FULL} = nothing in
     * the way. The march runs {@link #MARGIN} past the camera, so a camera ending inside a wall's thickness is pulled in too.
     */
    static int clearFraction(int fx, int fy, int fz, int cx, int cy, int cz, Grid grid) {
        hitKind = HIT_NONE;
        long dx = (long) cx - fx;
        long dz = (long) cz - fz;
        double length = Math.sqrt((double) (dx * dx + dz * dz));
        if (length < 1.0) {
            return FULL;
        }
        double end = 1.0 + MARGIN / length;
        int tx = fx >> 9;
        int tz = fz >> 9;
        int stepX = Long.signum(dx);
        int stepZ = Long.signum(dz);
        double nextX = dx > 0 ? ((double) ((tx + 1) << 9) - fx) / dx : dx < 0 ? ((double) (tx << 9) - fx) / dx : Double.POSITIVE_INFINITY;
        double nextZ = dz > 0 ? ((double) ((tz + 1) << 9) - fz) / dz : dz < 0 ? ((double) (tz << 9) - fz) / dz : Double.POSITIVE_INFINITY;
        double deltaX = dx != 0 ? 512.0 / Math.abs(dx) : Double.POSITIVE_INFINITY;
        double deltaZ = dz != 0 ? 512.0 / Math.abs(dz) : Double.POSITIVE_INFINITY;
        for (int guard = 0; guard < 256; guard++) {
            double t;
            int nx = tx;
            int nz = tz;
            if (nextX <= nextZ) {
                t = nextX;
                nx += stepX;
                nextX += deltaX;
            } else {
                t = nextZ;
                nz += stepZ;
                nextZ += deltaZ;
            }
            if (t > end) {
                break;
            }
            double rayY = fy + (cy - fy) * t;
            int kind = HIT_NONE;
            if (grid.blocks(tx, tz, nx, nz, (int) (fx + dx * t), (int) (fz + dz * t), rayY)) {
                kind = HIT_WALL;
            } else {
                int locTop = grid.locTop(nx, nz);
                if (locTop != NO_LOC && rayY > locTop) {
                    kind = HIT_LOC;
                }
            }
            if (kind != HIT_NONE) {
                hitKind = kind;
                hitTileX = nx;
                hitTileZ = nz;
                double allowed = t - MARGIN / length;
                return allowed <= 0.0 ? 0 : (int) Math.min(FULL, allowed * FULL);
            }
            tx = nx;
            tz = nz;
        }
        return FULL;
    }

    /** Pull in at once; go back out by {@code step + remaining * rate / 1000} per elapsed client cycle. */
    static int ease(int current, int target, int cycles) {
        if (target <= current) {
            return target;
        }
        int value = current;
        for (int i = 0; i < Math.min(cycles, 50); i++) {
            value += RESTORE_STEP + (int) ((long) (target - value) * RESTORE_RATE / 1000);
            if (value >= target) {
                return target;
            }
        }
        return value;
    }

    /** The smallest fraction that keeps {@link #MIN_DISTANCE} for a camera offset of [distance] world units. */
    static int minFraction(int distance) {
        return distance <= MIN_DISTANCE ? FULL : (int) ((long) MIN_DISTANCE * FULL / distance);
    }

    /**
     * Camera.method4606, after it placed the camera for focal point (fx, fy, fz): pulls Camera.x / y / z in when the rule
     * above applies. Every other camera mode is left exactly as computed.
     */
    static void apply(int fx, int fy, int fz) {
        int clock = TimeUtils.clock;
        int cycles = lastClock < 0 ? 0 : Math.max(0, clock - lastClock);
        lastClock = clock;
        PlayerEntity self = PlayerEntity.self;
        if (Camera.mode != CameraMode.MODE_DEFAULT || CutsceneManager.state != 0 || OrthoMode.enabled || self == null) {
            current = FULL;
            return;
        }
        int level = self.level;
        int target = FULL;
        long ox = (long) Camera.x - fx;
        long oy = (long) Camera.y - fy;
        long oz = (long) Camera.z - fz;
        int distance = (int) Math.sqrt((double) (ox * ox + oy * oy + oz * oz));
        if (level >= 0 && level < 4 && underRoof(level, self.x >> 9, self.z >> 9) && Client.collisionMaps[level] != null) {
            LIVE.level = level;
            LIVE.map = Client.collisionMaps[level];
            target = clearFraction(fx, fy, fz, Camera.x, Camera.y, Camera.z, LIVE);
            LIVE.map = null;
            if (target < FULL) {
                target = Math.max(target, minFraction(distance));
            }
        }
        current = ease(current, target, cycles);
        if (current >= FULL) {
            return;
        }
        Camera.x = fx + (int) (ox * current / FULL);
        Camera.y = fy + (int) (oy * current / FULL);
        Camera.z = fz + (int) (oz * current / FULL);
        if (RspsTrace.ENABLED && target < FULL) {
            long now = System.currentTimeMillis();
            if (now - lastLogMs >= 2000) {
                lastLogMs = now;
                System.out.println("[cameratrace " + now + "] PROBLEM camera pulled in by " + (hitKind == HIT_LOC ? "loc" : "wall")
                    + " at " + (hitTileX + WorldMap.areaBaseX) + "," + (hitTileZ + WorldMap.areaBaseZ) + "," + level
                    + " player=" + ((self.x >> 9) + WorldMap.areaBaseX) + "," + ((self.z >> 9) + WorldMap.areaBaseZ) + "," + level
                    + " distance=" + distance + "->" + (int) ((long) distance * current / FULL)
                    + " pitch=" + Camera.pitch + " yaw=" + Camera.yaw + " zoom=" + CameraZoomController.getZoom());
            }
        }
    }

    /** The roof flag the client's roof removal reads (Static276.method3988) on the player's own tile. */
    private static boolean underRoof(int level, int x, int z) {
        byte[][][] flags = Static280.tileFlags;
        if (flags == null || x < 0 || z < 0 || x >= flags[level].length || z >= flags[level][x].length) {
            return false;
        }
        return (flags[level][x][z] & 0x4) != 0;
    }

    private static final LiveGrid LIVE = new LiveGrid();

    /** The client's own data: collision flags, ground heights and the scene's locs of the player's level. */
    private static final class LiveGrid implements Grid {
        int level;
        CollisionMap map;

        /**
         * Own client 2026-10-06 (Royal Hall, player 3164,3492,0, camera south of the hall): nothing was pulled in - the ray
         * passed over the ground storey's 360, and then through the south doorway, where no collision wall stands but the
         * hall's facade is drawn above the opening (GeHomeHallCurtains: storey 2 as a level-0 wall, storey 1 as a level-0
         * shape-10 loc hanging over the doorway). So the edge blocks, on every level from the player's up, where the ray runs
         * through what is actually drawn there:
         *  - a wall model on that edge (Tile.wall / adjacentWall, shape 0 one edge, shape 2 two edges), between its base y and
         *    its top (y + getMinY(2));
         *  - a wall in that level's collision map, between the floor and the storey top ({@link #wallTop});
         *  - a loc hanging over the tile being entered (its model's bottom at least {@link #MIN_STOREY} above the floor - a
         *    lintel or facade, never furniture standing on the floor), between its bottom and top.
         */
        @Override
        public boolean blocks(int x, int z, int nx, int nz, int px, int pz, double rayY) {
            int player = this.level;
            CollisionMap own = this.map;
            int edgeA = nx > x ? 2 : nx < x ? 0 : nz > z ? 1 : 3;
            int edgeB = (edgeA + 2) & 3;
            try {
                for (int l = player; l < 4; l++) {
                    if (drawnWall(l, x, z, edgeA, rayY) || drawnWall(l, nx, nz, edgeB, rayY) || overhang(l, nx, nz, px, pz, rayY)) {
                        return true;
                    }
                    CollisionMap m = Client.collisionMaps[l];
                    if (m == null) {
                        continue;
                    }
                    this.level = l;
                    this.map = m;
                    if (this.wallBetween(x, z, nx, nz) && rayY > this.wallTop(px, pz) && rayY <= Static102.averageHeight(l, px, pz)) {
                        return true;
                    }
                }
                return false;
            } finally {
                this.level = player;
                this.map = own;
            }
        }

        private static Tile sceneTile(int l, int x, int z) {
            Tile[][][] tiles = Static334.activeTiles;
            if (tiles == null || l >= tiles.length || x < 0 || z < 0 || x >= tiles[l].length || z >= tiles[l][x].length) {
                return null;
            }
            return tiles[l][x][z];
        }

        /** Edges (0 west, 1 north, 2 east, 3 south) a wall of [shape] / [rotation] stands on: shape 0 one, shape 2 two. */
        static boolean onEdge(int shape, int rotation, int edge) {
            if (shape == 0) {
                return (rotation & 3) == edge;
            }
            if (shape == 2) {
                return (rotation & 3) == edge || ((rotation + 1) & 3) == edge;
            }
            return false;
        }

        private static boolean drawnWall(int l, int x, int z, int edge, double rayY) {
            Tile tile = sceneTile(l, x, z);
            if (tile == null) {
                return false;
            }
            return wallHit(tile.wall, edge, rayY) || wallHit(tile.adjacentWall, edge, rayY);
        }

        private static boolean wallHit(Wall wall, int edge, double rayY) {
            if (!(wall instanceof Location)) {
                return false;
            }
            Location loc = (Location) wall;
            return onEdge(loc.getShape(), loc.getRotation(), edge) && rayY > wall.y + wall.getMinY(2) && rayY <= wall.y;
        }

        private static boolean overhang(int l, int x, int z, int px, int pz, double rayY) {
            Tile tile = sceneTile(l, x, z);
            if (tile == null) {
                return false;
            }
            int floor = Static102.averageHeight(l, px, pz);
            for (PositionEntityNode node = tile.head; node != null; node = node.node) {
                if (!(node.entity instanceof StaticLocation)) {
                    continue;
                }
                StaticLocation loc = (StaticLocation) node.entity;
                if (loc.model == null) {
                    continue;
                }
                int top = loc.y + loc.model.fa();
                int bottom = loc.y + loc.model.EA();
                if (floor - bottom >= MIN_STOREY && rayY > top && rayY <= bottom) {
                    return true;
                }
            }
            return false;
        }

        private int flags(int x, int z) {
            int ix = x - this.map.x;
            int iz = z - this.map.z;
            if (ix < 0 || iz < 0 || ix >= this.map.width || iz >= this.map.length) {
                return 0;
            }
            int value = this.map.flags[ix][iz];
            return value == -1 ? 0 : value; // the map's outer border is all ones, not walls
        }

        @Override
        public boolean wallBetween(int x, int z, int nx, int nz) {
            if (nx > x) {
                return (flags(x, z) & CollisionFlag.WALL_EAST) != 0 || (flags(nx, nz) & CollisionFlag.WALL_WEST) != 0;
            }
            if (nx < x) {
                return (flags(x, z) & CollisionFlag.WALL_WEST) != 0 || (flags(nx, nz) & CollisionFlag.WALL_EAST) != 0;
            }
            if (nz > z) {
                return (flags(x, z) & CollisionFlag.WALL_NORTH) != 0 || (flags(nx, nz) & CollisionFlag.WALL_SOUTH) != 0;
            }
            if (nz < z) {
                return (flags(x, z) & CollisionFlag.WALL_SOUTH) != 0 || (flags(nx, nz) & CollisionFlag.WALL_NORTH) != 0;
            }
            return false;
        }

        @Override
        public int wallTop(int x, int z) {
            int floor = Static102.averageHeight(this.level, x, z);
            if (this.level < 3) {
                int above = Static102.averageHeight(this.level + 1, x, z);
                if (floor - above >= MIN_STOREY) {
                    return above;
                }
            }
            return floor - DEFAULT_STOREY;
        }

        @Override
        public int locTop(int x, int z) {
            // Walls only in the live client (2026-10-06, own client at 3164,3492,0): the loc next to the player in the hall
            // centre pulled a fully zoomed-out camera (10145) in to 300, inside the player's head - with a low camera every
            // loc beside the player is "above the ray". The owner's reports are about walls; the loc rule stays for the
            // geometry regression only.
            if (!LOCS_BLOCK || (flags(x, z) & CollisionFlag.LOCATION) == 0) {
                return NO_LOC;
            }
            Tile[][][] tiles = Static334.activeTiles;
            if (tiles == null || this.level >= tiles.length || x < 0 || z < 0 || x >= tiles[this.level].length || z >= tiles[this.level][x].length) {
                return NO_LOC;
            }
            Tile tile = tiles[this.level][x][z];
            int top = NO_LOC;
            for (PositionEntityNode node = tile == null ? null : tile.head; node != null; node = node.node) {
                PositionEntity entity = node.entity;
                if (entity instanceof StaticLocation || entity instanceof DynamicLocation) {
                    top = Math.min(top, entity.y + entity.getMinY(2));
                }
            }
            return top;
        }
    }
}
