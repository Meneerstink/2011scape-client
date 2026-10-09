import com.jagex.graphics.Ground;

import java.lang.reflect.Field;

/**
 * Headless checks of the roof flood (Static409.method5656) around RSPS map flag 0x20 (BELOW_BUILDING, the Royal Hall's
 * balconies). Owner 2026-10-09 17:47: inside the hall whole ring wall columns (storey walls + parapet) stayed drawn on every
 * storey. Cause: the diagonal steps only tested roof flag 0x4, so the flood ran out over a balcony row and cut its corner
 * into a walled ring tile from outside; Jagex's edge-wall check then kept that tile's upper walls. Synthetic map, level 0:
 *
 * <pre>
 *   z=11   .  F  F      F = floor (0x4), start at 8,11
 *   z=10   .  B2 B1     B = balcony (0x20 on level 1)
 *   z=9    P  .  R      R = ring tile, west-facing wall (rot 2) on level 1; P = plain outside tile
 *          7  8  9
 * </pre>
 *
 * Before the fix B2's south-east step reached R first with direction 4 and R's wall column stayed; now R is reached by
 * B1's south step and hides, while plain diagonal steps (B2 -> P) still run.
 */
public final class RspsRoofFloodRegressionTest {
    private static int assertions;
    private static final int SIZE = 104;
    private static final byte MARK = 1; // method5656(arg4 = true) marks with 1

    public static void main(String[] args) throws Exception {
        outsideTest();
        hallCorner();
        noBalconyUnchanged();
        System.out.println("RSPS roof flood regression checks passed: " + assertions);
    }

    private static void outsideTest() {
        reset();
        Static280.tileFlags[0][20][20] = 0x4;
        Static280.tileFlags[1][21][20] = RspsGraphicsProfile.BELOW_BUILDING;
        check(!RspsGraphicsProfile.outsideBuilding(20, 20), "roofed tile is not outside");
        check(!RspsGraphicsProfile.outsideBuilding(21, 20), "balcony (0x20 above) is not outside");
        check(RspsGraphicsProfile.outsideBuilding(22, 20), "plain tile beside the balcony is outside");
        Camera.renderingLevel = 3;
        Static280.tileFlags[3][22][20] = 0;
        check(RspsGraphicsProfile.outsideBuilding(22, 20), "top level has no level above: plain tile is outside");
        Camera.renderingLevel = 0;
    }

    private static void hallCorner() throws Exception {
        Tile[][][] tiles = reset();
        Static280.tileFlags[0][8][11] = 0x4;
        Static280.tileFlags[0][9][11] = 0x4;
        Static280.tileFlags[1][8][10] = RspsGraphicsProfile.BELOW_BUILDING;
        Static280.tileFlags[1][9][10] = RspsGraphicsProfile.BELOW_BUILDING;
        tiles[1][9][9] = wallTile(1, 4); // StaticWall.method6553: shape 0 rot 2 -> 4 (faces west, like the hall's ring)
        check(Static409.method5656(11, tiles, 0, 8, true), "flood starts on a roofed tile");
        check(Static328.aByteArrayArrayArray4[1][9][9] == MARK, "ring tile beside the balcony hides its upper wall (was a floating column)");
        check(Static328.aByteArrayArrayArray4[1][8][10] == MARK && Static328.aByteArrayArrayArray4[1][9][10] == MARK, "balcony tiles above stay hidden");
        check(Static328.aByteArrayArrayArray4[1][7][9] == MARK, "plain diagonal step off the balcony still runs (nothing hidden before is lost)");
    }

    private static void noBalconyUnchanged() throws Exception {
        Tile[][][] tiles = reset();
        // Lumbridge layout: floor x 10..12, ring x = 9 with west-facing walls, no 0x20 anywhere
        for (int x = 10; x <= 12; x++) {
            for (int z = 10; z <= 12; z++) {
                Static280.tileFlags[0][x][z] = 0x4;
            }
        }
        for (int z = 10; z <= 12; z++) {
            tiles[1][9][z] = wallTile(1, 4);
        }
        check(Static409.method5656(11, tiles, 0, 11, true), "flood starts inside");
        for (int z = 10; z <= 12; z++) {
            check(Static328.aByteArrayArrayArray4[1][9][z] == MARK, "ring wall 9," + z + " reached by a west step hides");
        }
        check(Static328.aByteArrayArrayArray4[1][8][11] != MARK, "flood does not leave a building without balconies");
    }

    private static Tile[][][] reset() {
        Camera.renderingLevel = 0;
        Static280.tileFlags = new byte[4][SIZE][SIZE];
        Static328.aByteArrayArrayArray4 = new byte[4][SIZE][SIZE];
        Static501.mapLength = SIZE;
        Static720.mapWidth = SIZE;
        Static246.ground = new Ground[4];
        for (int level = 0; level < 4; level++) {
            Static246.ground[level] = flatGround();
        }
        Tile[][][] tiles = new Tile[4][][];
        for (int level = 1; level < 4; level++) {
            tiles[level] = new Tile[SIZE][SIZE];
        }
        return tiles;
    }

    private static Tile wallTile(int level, int direction) throws Exception {
        Wall wall = (Wall) unsafe().allocateInstance(StaticWall.class);
        wall.aShort58 = (short) direction;
        Tile tile = new Tile(level);
        tile.wall = wall;
        return tile;
    }

    private static Ground flatGround() {
        try {
            Ground ground = (Ground) unsafe().allocateInstance(Ground_Sub1.class);
            Field heights = Ground.class.getDeclaredField("tileHeights");
            heights.setAccessible(true);
            heights.set(ground, new int[SIZE + 1][SIZE + 1]);
            return ground;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static sun.misc.Unsafe unsafe() throws ReflectiveOperationException {
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return (sun.misc.Unsafe) f.get(null);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
