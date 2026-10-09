import com.jagex.Entity;
import com.jagex.game.Location;
import com.jagex.graphics.EnvironmentLight;
import com.jagex.game.runetek6.config.loctype.LocTypeCustomisation;

/**
 * Owner 2026-10-09: "ik heb vlaggen geplaatst aan gebouwen - als een player een gebouw ingaat moeten de vlaggen ook
 * verdwijnen". A 78 logo / wide banner / flag hung on the outside of a building disappears together with that
 * building's roof: the same moment, the same building and only for the player inside, because it uses the roof's own
 * selective-hide flood (Static276 / Static409, "Remove roofs" selective): every frame the flood marks the roofed tiles of
 * the building the player stands in, plus the ring of tiles around them, with this frame's marker on the player's level;
 * the levels above are hidden on those tiles. A decoration on such a marked tile on the player's level is hidden too
 * when it is on the outside: on a tile without the roof flag (0x4), or, angled on a diagonal wall, facing a tile without
 * it. Decorations inside the building and ones lying flat on a floor stay. Every placement of these locs follows the rule,
 * whatever it was placed on (no per-placement list).
 */
public final class RspsRoofDecor {

    private RspsRoofDecor() {
    }

    /** True when [entity] is a 78 decoration outside the building whose roof is hidden this frame (do not draw it). */
    public static boolean hidden(Entity entity, byte[][][] marks, byte marker) {
        if (marks == null || !(entity instanceof Location) || ClientOptions.instance.removeRoofsOverride.getValue() != 2) {
            return false;
        }
        Location loc = (Location) entity;
        int id = loc.getId();
        if (id < RspsDev.LOGO_78 || id > RspsDev.FLAG_78) {
            return false;
        }
        int level = entity.level;
        if (level != Camera.renderingLevel || level < 0 || level >= marks.length) {
            return false;
        }
        int tx = entity.x >> EnvironmentLight.anInt1066;
        int tz = entity.z >> EnvironmentLight.anInt1066;
        if (!inside(marks[level], tx, tz) || marks[level][tx][tz] != marker) {
            return false;
        }
        LocTypeCustomisation fit = customisation(entity);
        int pitch = fit == null ? 0 : fit.fitPitch & 0x3FFF;
        if (pitch == 0x3000) {
            // Lying flat on a floor (server LocFit.PITCH_FLAT): it does not float when the roof goes.
            return false;
        }
        if (!roofed(level, tx, tz)) {
            return true;
        }
        int yaw = fit == null ? 0 : fit.fitYaw & 0x3FFF;
        if (yaw % 4096 == 0) {
            // A straight decoration on a roofed tile hangs inside the building.
            return false;
        }
        // Angled (a diagonal wall): the tile its face looks at decides. Rotation r with yaw 45 degrees faces north-east,
        // south-east, south-west, north-west for r 0..3 (seen in the client 2026-10-09, screens/placemode-2026-10-09/22).
        double angle = yaw * 2 * Math.PI / 16384;
        double fx = Math.sin(angle);
        double fz = Math.cos(angle);
        for (int r = loc.getRotation() & 0x3; r > 0; r--) {
            double t = fx;
            fx = fz;
            fz = -t;
        }
        int nx = tx + (fx > 0.38 ? 1 : fx < -0.38 ? -1 : 0);
        int nz = tz + (fz > 0.38 ? 1 : fz < -0.38 ? -1 : 0);
        return inside(marks[level], nx, nz) && !roofed(level, nx, nz);
    }

    private static boolean inside(byte[][] plane, int x, int z) {
        return x >= 0 && z >= 0 && x < plane.length && z < plane[x].length;
    }

    private static boolean roofed(int level, int x, int z) {
        return (Static280.tileFlags[level][x][z] & 0x4) != 0;
    }

    private static LocTypeCustomisation customisation(Entity entity) {
        if (entity instanceof DynamicWallDecor) {
            return ((DynamicWallDecor) entity).entity.customisation;
        }
        if (entity instanceof DynamicLocation) {
            return ((DynamicLocation) entity).entity.customisation;
        }
        return null;
    }
}
