/** Camera collision geometry (CameraCollision.clearFraction / ease / minFraction): camera ray vs wall edge -> allowed distance. */
public final class CameraCollisionRegressionTest {
    private static int assertions;

    /** Flat floor at y 0, storey 360 high; optional wall on the x edge between tile wallX and wallX + 1; optional loc. */
    private static final class TestGrid implements CameraCollision.Grid {
        final int wallX;
        final int locX;
        final int locZ;
        final int locTop;

        TestGrid(int wallX, int locX, int locZ, int locTop) {
            this.wallX = wallX;
            this.locX = locX;
            this.locZ = locZ;
            this.locTop = locTop;
        }

        @Override
        public boolean wallBetween(int x, int z, int nx, int nz) {
            return z == nz && Math.min(x, nx) == this.wallX && Math.max(x, nx) == this.wallX + 1;
        }

        @Override
        public int wallTop(int x, int z) {
            return -360;
        }

        @Override
        public int locTop(int x, int z) {
            return x == this.locX && z == this.locZ ? this.locTop : CameraCollision.NO_LOC;
        }
    }

    private static final int NONE = Integer.MIN_VALUE;

    public static void main(String[] args) {
        // Focal point: centre of tile (9, 5), 200 above the floor (the client's player-follow focal height).
        int fx = 9 * 512 + 256;
        int fz = 5 * 512 + 256;
        int fy = -200;
        int wallEdge = 11 * 512; // wall between tiles 10 and 11: 768 east of the focal point

        CameraCollision.Grid open = new TestGrid(NONE, NONE, NONE, 0);
        check(CameraCollision.clearFraction(fx, fy, fz, fx + 3000, fy - 300, fz, open) == CameraCollision.FULL,
            "Nothing in the way: the requested distance");

        CameraCollision.Grid wall = new TestGrid(10, NONE, NONE, 0);
        int low = CameraCollision.clearFraction(fx, fy, fz, fx + 3000, fy - 300, fz, wall);
        check(near(low, fraction(wallEdge - fx - CameraCollision.MARGIN, 3000)), "Wall crossed below its top: pulled in to 32 before the edge, got " + low);
        check(CameraCollision.hitKind == CameraCollision.HIT_WALL && CameraCollision.hitTileX == 11 && CameraCollision.hitTileZ == 5,
            "The trace names the wall and the tile behind it");
        check(CameraCollision.clearFraction(fx, fy, fz, fx + 3000, fy - 5000, fz, wall) == CameraCollision.FULL,
            "Ray over the wall's top (steep camera): not pulled in");
        check(CameraCollision.clearFraction(fx, fy, fz, wallEdge - 100, fy - 30, fz, wall) == CameraCollision.FULL,
            "Camera ending more than the margin before the wall: unchanged");
        int inside = CameraCollision.clearFraction(fx, fy, fz, wallEdge - 10, fy - 30, fz, wall);
        check(near(inside, fraction(wallEdge - fx - CameraCollision.MARGIN, wallEdge - 10 - fx)),
            "Camera ending inside the wall's thickness: pulled in to the margin, got " + inside);

        // The other way round: focal point east of the wall, camera to the west.
        int fxEast = 12 * 512 + 256;
        int west = CameraCollision.clearFraction(fxEast, fy, fz, fxEast - 3000, fy - 300, fz, wall);
        check(near(west, fraction(fxEast - wallEdge - CameraCollision.MARGIN, 3000)), "Westward ray: same rule, got " + west);

        // A diagonal ray (camera to the north-east) crossing the same wall edge.
        int diagonal = CameraCollision.clearFraction(fx, fy, fz, fx + 2000, fy - 300, fz + 2000, wall);
        check(diagonal < CameraCollision.FULL && diagonal > 0, "Diagonal ray: pulled in, got " + diagonal);
        check(CameraCollision.hitKind == CameraCollision.HIT_WALL, "Diagonal ray: blocked by the wall");

        // Solid loc on tile (11, 5): blocks only where its model top is above the ray.
        CameraCollision.Grid tallLoc = new TestGrid(NONE, 11, 5, -400);
        int loc = CameraCollision.clearFraction(fx, fy, fz, fx + 3000, fy - 300, fz, tallLoc);
        check(near(loc, fraction(wallEdge - fx - CameraCollision.MARGIN, 3000)) && CameraCollision.hitKind == CameraCollision.HIT_LOC,
            "Tall loc in the way: pulled in before the loc's tile, got " + loc);
        CameraCollision.Grid lowLoc = new TestGrid(NONE, 11, 5, -100);
        check(CameraCollision.clearFraction(fx, fy, fz, fx + 3000, fy - 300, fz, lowLoc) == CameraCollision.FULL,
            "Low loc (table, desk) under the ray: not pulled in");

        // Player against the wall: never closer than the margin allows, 0 at worst (apply() then keeps MIN_DISTANCE).
        int hugging = CameraCollision.clearFraction(wallEdge - 20, fy, fz, wallEdge + 2000, fy - 100, fz, wall);
        check(hugging == 0, "Focal point inside the margin: fraction 0, got " + hugging);
        check(CameraCollision.minFraction(CameraCollision.MIN_DISTANCE * 4) == CameraCollision.FULL / 4, "Minimum camera distance");
        check(CameraCollision.minFraction(10) == CameraCollision.FULL, "A camera already closer than the minimum stays");

        // Pitch straight down: no horizontal offset, nothing to collide with.
        check(CameraCollision.clearFraction(fx, fy, fz, fx, fy - 2000, fz, wall) == CameraCollision.FULL, "Top-down camera: unchanged");

        // Easing: in at once, out per client cycle (Camera.moveToTick style), never past the target.
        check(CameraCollision.ease(CameraCollision.FULL, 1000, 0) == 1000, "Pull-in is immediate, even within one cycle");
        check(CameraCollision.ease(1000, CameraCollision.FULL, 0) == 1000, "No restore without a client cycle");
        int one = CameraCollision.ease(0, CameraCollision.FULL, 1);
        check(one == CameraCollision.RESTORE_STEP + CameraCollision.FULL * CameraCollision.RESTORE_RATE / 1000, "One cycle of restore, got " + one);
        check(CameraCollision.ease(one, CameraCollision.FULL, 1) > one, "Restore keeps going");
        check(CameraCollision.ease(CameraCollision.FULL - 100, CameraCollision.FULL, 1) == CameraCollision.FULL, "Restore stops at the target");
        check(CameraCollision.ease(0, CameraCollision.FULL, 1000) <= CameraCollision.FULL, "A long pause cannot overshoot");

        System.out.println("CameraCollisionRegressionTest passed " + assertions + " assertions");
    }

    private static int fraction(int part, int whole) {
        return (int) ((double) part / whole * CameraCollision.FULL);
    }

    private static boolean near(int actual, int expected) {
        return Math.abs(actual - expected) <= 2;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
