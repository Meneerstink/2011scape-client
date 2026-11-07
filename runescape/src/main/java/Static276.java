import com.jagex.core.util.JagException;
import com.jagex.game.camera.CameraMode;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

public final class Static276 {

    @OriginalMember(owner = "client!ila", name = "a", descriptor = "(I)V")
    public static void method3986() {
        Static317.anInt5046 = 0;
        Static442.aBoolean499 = false;
        Static384.aLocOccluderArray2 = new LocOccluder[500];
        Static150.anInt2634 = 0;
        Static446.anIntArrayArrayArray9 = new int[Static299.tileMaxLevel][Static619.tileMaxX + 1][Static662.tileMaxZ + 1];
        Static663.anInt9874 = Static340.anInt5586;
        Static86.anInt1803 = Static340.anInt5586;
        Static444.anInt6751 = 0;
        Static607.aLocOccluderArray4 = new LocOccluder[2000];
        Static285.aLocOccluderArray1 = new LocOccluder[1000];
        Static469.activeOccluderCount = 0;
        Static560.aLocOccluderArray3 = new LocOccluder[500];
        if (Static665.aToolkit_15 instanceof oa) {
            Static18.occlude = false;
        } else {
            Static18.occlude = true;
        }
    }

    @OriginalMember(owner = "client!ila", name = "b", descriptor = "(I)V")
    public static void method3988() {
        if (ClientOptions.instance.removeRoofsOverride.getValue() != 2) {
            return;
        }
        @Pc(21) byte local21 = (byte) (Static198.anInt3276 - 4 & 0xFF);
        @Pc(25) int local25 = Static198.anInt3276 % Static720.mapWidth;
        @Pc(30) int local30;
        for (@Pc(27) int local27 = 0; local27 < 4; local27++) {
            for (local30 = 0; local30 < Static501.mapLength; local30++) {
                Static328.aByteArrayArrayArray4[local27][local25][local30] = local21;
            }
        }
        if (Camera.renderingLevel == 3) {
            return;
        }
        // RSPS (owner bugs 031449 / 154436 / 032935, "black when zoomed out" in Barrows): underground maps are stacked - the
        // Barrows crypts (plane 3) lie straight above the tunnels, at y -10688. The extended (RuneLite/OSRS) zoom lifts a
        // top-down camera to about y -17500, above that level, whose floor then covers the whole view; the 667 zoom never got
        // that high and those tunnels carry no roof flags (0x4) for the flood below. OSRS with roofs hidden draws nothing above
        // the player's plane; underground that rule is used here: every tile of the levels above is hidden this frame.
        if (WorldMap.areaBaseZ + (PlayerEntity.self.z >> 9) >= RspsGraphicsProfile.UNDERGROUND_Z
            || RspsGraphicsProfile.dynamicSourceUnderground()) {
            @Pc(36) byte hidden = (byte) (Static198.anInt3276 & 0xFF);
            for (@Pc(38) int level = Camera.renderingLevel + 1; level < 4; level++) {
                for (@Pc(40) int x = 0; x < Static720.mapWidth; x++) {
                    java.util.Arrays.fill(Static328.aByteArrayArrayArray4[level][x], hidden);
                }
            }
        }
        for (local30 = 0; local30 < 2; local30++) {
            Static482.anIntArray588[local30] = -1000000;
            Static9.anIntArray18[local30] = 1000000;
            Static457.anIntArray552[local30] = 0;
            Static682.anIntArray817[local30] = 1000000;
            Static153.anIntArray235[local30] = 0;
        }
        @Pc(92) int local92 = PlayerEntity.self.x;
        @Pc(95) int local95 = PlayerEntity.self.z;
        @Pc(149) int local149;
        if (Camera.mode != CameraMode.MODE_DEFAULT && Camera.anInt10376 == -1) {
            local149 = Static102.averageHeight(Camera.renderingLevel, Camera.x, Camera.z);
            if (local149 - Camera.y < 3200 && (Static280.tileFlags[Camera.renderingLevel][Camera.x >> 9][Camera.z >> 9] & 0x4) != 0) {
                Static409.method5656(Camera.z >> 9, Static334.activeTiles, 1, Camera.x >> 9, false);
                return;
            }
            return;
        }
        if (Camera.mode != CameraMode.MODE_DEFAULT) {
            local92 = Camera.anInt10376;
            local95 = Camera.anInt10383;
        }
        if ((Static280.tileFlags[Camera.renderingLevel][local92 >> 9][local95 >> 9] & 0x4) != 0) {
            Static409.method5656(local95 >> 9, Static334.activeTiles, 0, local92 >> 9, false);
        }
        // RSPS owner 2026-09-26: "the roof only need to dissappear when entering the building". The original client also
        // hid a roof whenever one lay on the line from the camera to the player, so turning the camera outside a building
        // took its roof away; that camera test below was switched off. Owner 2026-10-09 (Q1 = A): back to normal RS, the
        // flag is off again and the line test below runs (a roof between the camera and the player hides).
        if (RspsGraphicsProfile.ROOFS_HIDE_ONLY_UNDER_PLAYER) {
            // Owner 2026-10-09 "rare camera issues" in the mansion: with the line test off, a camera swung into a building
            // (player outside, zoomed out or turned along the wall) drew its rooms and the roof's underside from inside.
            // Jagex's own rule for a camera standing in a building (the non-follow camera branch above): the camera tile is
            // roofed (or in the wall ring beside a roofed tile) and the camera is below roof height -> hide that building's
            // levels above, as the line test would. The roof stays visible from every camera outside the building (owner
            // 2026-09-26 / 2026-10-06).
            int start = RspsGraphicsProfile.cameraBuildingTile();
            if (start >= 0) {
                Static409.method5656(start & 0xFFFF, Static334.activeTiles, 1, start >> 16, false);
            }
            return;
        }
        if (Camera.pitch >= 2560) {
            return;
        }
        local149 = Camera.x >> 9;
        @Pc(153) int local153 = Camera.z >> 9;
        @Pc(157) int local157 = local92 >> 9;
        @Pc(161) int local161 = local95 >> 9;
        @Pc(169) int local169;
        if (local157 > local149) {
            local169 = local157 - local149;
        } else {
            local169 = local149 - local157;
        }
        @Pc(186) int local186;
        if (local153 < local161) {
            local186 = local161 - local153;
        } else {
            local186 = local153 - local161;
        }
        if ((local169 != 0 || local186 != 0) && local169 > (-Static720.mapWidth) && local169 < Static720.mapWidth && -Static501.mapLength < local186 && Static501.mapLength > local186) {
            @Pc(278) int local278;
            @Pc(280) int local280;
            if (local169 <= local186) {
                local278 = local169 * 65536 / local186;
                local280 = 32768;
                while (local161 != local153) {
                    if (local161 > local153) {
                        local153++;
                    } else if (local161 < local153) {
                        local153--;
                    }
                    if ((Static280.tileFlags[Camera.renderingLevel][local149][local153] & 0x4) != 0) {
                        Static409.method5656(local153, Static334.activeTiles, 1, local149, false);
                        return;
                    }
                    local280 += local278;
                    if (local280 >= 65536) {
                        local280 -= 65536;
                        if (local149 < local157) {
                            local149++;
                        } else if (local149 > local157) {
                            local149--;
                        }
                        if ((Static280.tileFlags[Camera.renderingLevel][local149][local153] & 0x4) != 0) {
                            Static409.method5656(local153, Static334.activeTiles, 1, local149, false);
                            return;
                        }
                    }
                }
                return;
            }
            local278 = local186 * 65536 / local169;
            local280 = 32768;
            while (local157 != local149) {
                if (local149 < local157) {
                    local149++;
                } else if (local157 < local149) {
                    local149--;
                }
                if ((Static280.tileFlags[Camera.renderingLevel][local149][local153] & 0x4) != 0) {
                    Static409.method5656(local153, Static334.activeTiles, 1, local149, false);
                    return;
                }
                local280 += local278;
                if (local280 >= 65536) {
                    if (local161 > local153) {
                        local153++;
                    } else if (local161 < local153) {
                        local153--;
                    }
                    local280 -= 65536;
                    if ((Static280.tileFlags[Camera.renderingLevel][local149][local153] & 0x4) != 0) {
                        Static409.method5656(local153, Static334.activeTiles, 1, local149, false);
                        return;
                    }
                }
            }
            return;
        }
        JagException.sendTrace(null, "RC: " + local149 + "," + local153 + " " + local157 + "," + local161 + " " + WorldMap.areaBaseX + "," + WorldMap.areaBaseZ);
        return;
    }
}
