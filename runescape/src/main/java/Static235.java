import com.jagex.Client;
import com.jagex.core.constants.LocShapes;
import com.jagex.game.runetek6.config.loctype.LocType;
import com.jagex.game.runetek6.config.loctype.LocTypeList;
import com.jagex.graphics.Toolkit;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

public final class Static235 {

    @OriginalMember(owner = "client!hf", name = "a", descriptor = "(IIIIIIIII)V")
    public static void setLocChange(@OriginalArg(3) int x, @OriginalArg(0) int z, @OriginalArg(6) int level, @OriginalArg(8) int id, @OriginalArg(1) int shape, @OriginalArg(2) int rotation, @OriginalArg(4) int animation, @OriginalArg(5) int layer) {
        if (x < 1 || z < 1 || Static720.mapWidth - 2 < x || Static501.mapLength - 2 < z) {
            return;
        }

        @Pc(39) int virtualLevel = level;
        if (level < 3 && Static441.isBridgeAt(z, x)) {
            virtualLevel = level + 1;
        }

        if (ClientOptions.instance.animateBackground.getValue() == 0 && !Static696.isTileVisibleFrom(z, Static164.areaLevel, x, virtualLevel)) {
            return;
        }

        if (Static334.activeTiles == null) {
            return;
        }

        MapRegion.active.removeLoc(level, x, z, layer, Client.collisionMaps[level], Toolkit.active);

        // RSPS (owner 2026-10-02, POH minimap icons): the minimap image and its icon list are captured once per level
        // (Minimap.drawLevel) and never again for a loc spawned or removed later - an instance's furniture and map markers
        // (player-owned house) arrive after that capture. A change the minimap shows (any removal, a wall, a loc with a
        // map scene or a map icon) redraws it on the next pass (Minimap.method2046 runs once per cycle).
        if (affectsMinimap(id, shape)) {
            Minimap.level = -1;
        }

        if (id >= 0) {
            @Pc(93) int valueBefore = ClientOptions.instance.groundDecor.getValue();
            ClientOptions.instance.update(1, ClientOptions.instance.groundDecor);
            MapRegion.active.loadLocation(x, z, level, virtualLevel, id, shape, rotation, animation, Client.collisionMaps[level], Toolkit.active);
            ClientOptions.instance.update(valueBefore, ClientOptions.instance.groundDecor);
        }
    }

    /** Whether a loc change at this spot changes the minimap: a removal, a wall, or a loc with a map scene or map icon. */
    static boolean affectsMinimap(int id, int shape) {
        if (id < 0 || LocShapes.isWall(shape)) {
            return true;
        }
        LocType type = LocTypeList.instance.list(id);
        if (type.mapelement >= 0 || type.msi != -1) {
            return true;
        }
        if (type.multiloc != null) {
            for (int child : type.multiloc) {
                if (child >= 0 && LocTypeList.instance.list(child).mapelement >= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @OriginalMember(owner = "client!hf", name = "b", descriptor = "(III)Z")
    public static boolean loadedModels(@OriginalArg(0) int id, @OriginalArg(2) int shape) {
        if (shape == LocShapes.CENTREPIECE_DIAGONAL) {
            shape = LocShapes.CENTREPIECE_STRAIGHT;
        }
        @Pc(19) LocType locType = LocTypeList.instance.list(id);
        if (shape >= LocShapes.WALLDECOR_STRAIGHT_OFFSET && shape <= LocShapes.WALLDECOR_DIAGONAL_BOTH) {
            shape = LocShapes.WALLDECOR_STRAIGHT_NOOFFSET;
        }
        return locType.loadedModels(shape);
    }

}
