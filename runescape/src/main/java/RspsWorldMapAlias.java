/**
 * N462 (owner 07-11 22:5x 'de wereldkaart moet necropolis laten zien'): the OSRS world map has no area for the Tombs of Amascut
 * (cache2686 index 19 group 0: 107 areas, none for the raid or its lobby), so opening the map in the ToA lobby or a raid instance
 * fell back to the surface map's origin (Lumbridge) without the player. In the ToA lobby (map square 52_142) and in a dynamic area
 * whose source zone lies in the imported raid block (squares 64..70 x 104..108) the map shows the player at the Jaltevas pyramid
 * entrance in the Necropolis (3357,2711 - the tile the raid is entered from).
 */
public final class RspsWorldMapAlias {
    static final int PYRAMID_X = 3357;
    static final int PYRAMID_Z = 2711;

    private RspsWorldMapAlias() {
    }

    /**
     * A packed coord (level << 28 | x << 14 | z) from a world-map clientscript op: the player's own real position (clientscript COORD)
     * becomes the pyramid while in ToA, so the map picks the surface area; every other coord is returned unchanged.
     */
    public static int packed(int coord) {
        if (PlayerEntity.self == null) {
            return coord;
        }
        int x = (PlayerEntity.self.x >> 9) + WorldMap.areaBaseX;
        int z = (PlayerEntity.self.z >> 9) + WorldMap.areaBaseZ;
        if (((coord >> 14) & 0x3FFF) != x || (coord & 0x3FFF) != z) {
            return coord;
        }
        int[] alias = playerCoord();
        return alias == null ? coord : (alias[0] << 28) + (alias[1] << 14) + alias[2];
    }

    /** {level, x, z} of the player for the world map, or null when the real position is used. */
    public static int[] playerCoord() {
        if (PlayerEntity.self == null) {
            return null;
        }
        int x = (PlayerEntity.self.x >> 9) + WorldMap.areaBaseX;
        int z = (PlayerEntity.self.z >> 9) + WorldMap.areaBaseZ;
        if (x >= 3328 && x <= 3391 && z >= 9088 && z <= 9151) {
            return new int[] {0, PYRAMID_X, PYRAMID_Z};
        }
        if (Static117.areaMode == com.jagex.core.constants.AreaMode.STATIC_AREA || Static623.zonePointers == null) {
            return null;
        }
        int zx = (PlayerEntity.self.x >> 9) >> 3;
        int zz = (PlayerEntity.self.z >> 9) >> 3;
        int[][][] zones = Static623.zonePointers;
        int level = PlayerEntity.self.level & 0x3;
        if (zx < 0 || zz < 0 || zx >= zones[level].length || zz >= zones[level][zx].length) {
            return null;
        }
        int pointer = zones[level][zx][zz];
        if (pointer == -1) {
            pointer = zones[0][zx][zz];
        }
        if (pointer == -1) {
            return null;
        }
        // REBUILD_REGION zone pointer: x in bits 14..23, z in bits 3..13, both in zones of 8 tiles.
        int sourceX = ((pointer >> 14) & 0x3FF) * 8;
        int sourceZ = ((pointer >> 3) & 0x7FF) * 8;
        if (sourceX >= 64 * 64 && sourceX < 71 * 64 && sourceZ >= 104 * 64 && sourceZ < 109 * 64) {
            return new int[] {0, PYRAMID_X, PYRAMID_Z};
        }
        return null;
    }
}
