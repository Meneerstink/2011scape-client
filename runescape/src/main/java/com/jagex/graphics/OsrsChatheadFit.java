package com.jagex.graphics;

/**
 * RSPS (owner 2026-10-04 025612: "when talking to an npc and wearing some new osrs gear the chathead animation of my
 * character when wearing the osrs seem not correct"): puts an OSRS item's dialogue-head mesh (item opcodes 90-93) on this
 * client's player head.
 *
 * An OSRS chathead mesh is modelled at the OSRS dialogue size on the OSRS head rig (vertex labels 0-50). This client's
 * player head is the HD kit head on the HD rig (labels 65-199) and plays HD poses, which neither scale nor move labels 0-50:
 * the OSRS helm stayed at a fraction of the head's size, buried inside it (Eclipse moon helm: a bald head). Native 667 item
 * heads carry HD labels, so a head mesh whose every label is 0-50 (or unlabelled) is an OSRS import.
 *
 * Measured fit (scale + translation, iterative closest point, raw units) of the OSRS male head-kit chatheads onto this
 * cache's: kit 0 (63 -> 39799) scale 2.54 offset (-4, 10, 61), kit 1 (49 -> 39905) 2.79 (-1, 13, 79), kit 3 (55 -> 39877)
 * 2.72 (-3, 1, 69), mean distance 14-20 units; the classic NPC poses use 2.5 (320/128) for the same OSRS-to-667 step.
 * The fitted vertices get label [HD_HEAD], the label native helmets use for the cranium, so the helm turns with the head.
 */
public final class OsrsChatheadFit {
    private static final int CLASSIC_MAX_LABEL = 50;
    private static final int NO_GROUP = 255;
    public static final int HD_HEAD = 190;
    public static final double SCALE = 2.6D;
    /** Raw (pre-upscale) units; the mesh is fitted after its x4 upscale, so these are multiplied by 4. */
    public static final int OFFSET_X = 0;
    public static final int OFFSET_Y = 8;
    public static final int OFFSET_Z = 68;

    private OsrsChatheadFit() {
    }

    public static boolean isOsrsHead(Mesh mesh) {
        if (mesh == null || mesh.vertexLabel == null || mesh.vertexCount == 0) {
            return false;
        }
        boolean classic = false;
        for (int i = 0; i < mesh.vertexCount; i++) {
            int label = mesh.vertexLabel[i];
            if (label == NO_GROUP || label == -1) {
                continue;
            }
            if (label > CLASSIC_MAX_LABEL) {
                return false;
            }
            classic = true;
        }
        return classic;
    }

    /** Fits an upscaled OSRS item chathead mesh in place; leaves any other mesh alone. */
    public static void fit(Mesh mesh) {
        if (!isOsrsHead(mesh)) {
            return;
        }
        for (int i = 0; i < mesh.vertexCount; i++) {
            mesh.vertexX[i] = (int) Math.round(mesh.vertexX[i] * SCALE) + (OFFSET_X << 2);
            mesh.vertexY[i] = (int) Math.round(mesh.vertexY[i] * SCALE) + (OFFSET_Y << 2);
            mesh.vertexZ[i] = (int) Math.round(mesh.vertexZ[i] * SCALE) + (OFFSET_Z << 2);
            mesh.vertexLabel[i] = HD_HEAD;
        }
    }
}
