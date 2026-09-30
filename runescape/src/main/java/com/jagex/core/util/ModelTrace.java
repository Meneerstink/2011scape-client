package com.jagex.core.util;

import com.jagex.game.runetek6.config.billboardtype.BillboardTypeList;
import com.jagex.graphics.Mesh;
import com.jagex.graphics.MeshBillboard;
import com.jagex.graphics.TextureMetrics;
import com.jagex.graphics.TextureSource;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeMap;

/**
 * RSPS render trace (owner 2026-09-30: "delen van npcs / pets / mijn player verdwijnen, soms per camerahoek"). On with the
 * rest of the debug toolkit (C:/RSPS/trace.on or avtrace.on at client start, {@link RspsTrace#ENABLED}); every line goes to
 * the client log with the "[modeltrace]" tag, faults as PROBLEM lines (manual: C:/RSPS/TRACES.md).
 * <p>
 * It measures every place where this client can lose part of an entity model:
 * <ul>
 *   <li>{@link #built}: when an npc / player body model is assembled - faces the toolkits never draw (shading type 2,
 *   a hide-face billboard, a material whose metrics say "not a surface"), faces that are fully transparent, translucent
 *   faces (the GL model draws those in priority order with depth writes, so their visibility depends on the camera),
 *   faces with no area, and every texture a face uses that is not loaded yet.</li>
 *   <li>{@link #transform}: every animation frame transform, with the npc/player, sequence and frame that sent it -
 *   a mirror scale (negative determinant: the triangles turn inside out and back-face culling hides them from the
 *   front), a scale that collapses parts to (almost) nothing, a translation that throws parts away, and an alpha step
 *   that makes the labelled faces invisible.</li>
 *   <li>{@link #textureNotLoaded}: a GL texture bind for an image that never becomes available.</li>
 * </ul>
 * One line per distinct finding (npc id + sequence + frame + kind), so a fault that repeats every frame is reported once.
 */
public final class ModelTrace {

    public static final boolean ENABLED = RspsTrace.ENABLED;

    /** Who is being built / animated right now ("npc 6970 'Pikkupstix'", "player self"), null for scenery etc. */
    private static String who;

    /** Sequence and frame being applied ("seq 808 f3"), set per animator. */
    private static String frame;

    private static final Set<String> seen = new HashSet<>();

    /** Scale values are x/128: below this magnitude (1/16) a part is collapsed to almost nothing. */
    private static final int COLLAPSE_SCALE = 8;

    /** A single translation step further than this (4 tiles in model units) throws the labelled vertices away. */
    private static final int THROW_DISTANCE = 2048;

    private ModelTrace() {
        /* empty */
    }

    public static void setWho(String value) {
        if (ENABLED) {
            who = value;
            frame = null;
        }
    }

    public static void setFrame(int seqId, int frameIndex) {
        if (ENABLED) {
            frame = "seq " + seqId + " f" + frameIndex;
        }
    }

    /** Who is being built / animated right now, or [fallback]. */
    public static String who(String fallback) {
        return who == null ? fallback : who;
    }

    public static void clear() {
        if (ENABLED) {
            who = null;
            frame = null;
        }
    }

    private static boolean first(String key) {
        synchronized (seen) {
            return seen.size() < 50000 && seen.add(key);
        }
    }

    private static void log(String message) {
        System.out.println("[modeltrace " + System.currentTimeMillis() + "] " + message);
    }

    /**
     * The assembled mesh of [whoBuilt], after merging, before the toolkit model is made. [retexSrc]/[retexDst] are the
     * definition's retextures (applied by the toolkit afterwards), so the texture check names the texture really drawn.
     */
    public static void built(String whoBuilt, Mesh mesh, TextureSource textures, short[] retexSrc, short[] retexDst) {
        if (!ENABLED || mesh == null || !first("built:" + whoBuilt + ":" + mesh.faceCount + ":" + mesh.vertexCount)) {
            return;
        }
        int hiddenShading = 0;
        int hiddenBillboard = 0;
        int materialSkipped = 0;
        int invisibleAlpha = 0;
        int translucent = 0;
        int flat = 0;
        int textured = 0;
        TreeMap<Integer, Integer> notLoaded = new TreeMap<>();
        Set<Integer> billboardFaces = new HashSet<>();
        if (mesh.billboards != null) {
            for (MeshBillboard billboard : mesh.billboards) {
                if (billboard != null && BillboardTypeList.list(billboard.id).hideFace) {
                    billboardFaces.add(billboard.face);
                }
            }
        }
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int v = 0; v < mesh.vertexCount; v++) {
            minY = Math.min(minY, mesh.vertexY[v]);
            maxY = Math.max(maxY, mesh.vertexY[v]);
        }
        for (int f = 0; f < mesh.faceCount; f++) {
            if (mesh.shadingType != null && mesh.shadingType[f] == 2) {
                hiddenShading++;
                continue;
            }
            if (billboardFaces.contains(f)) {
                hiddenBillboard++;
                continue;
            }
            int alpha = mesh.faceAlpha == null ? 0 : mesh.faceAlpha[f] & 0xFF;
            if (alpha >= 254) {
                invisibleAlpha++;
            } else if (alpha != 0) {
                translucent++;
            }
            int a = mesh.faceA[f];
            int b = mesh.faceB[f];
            int c = mesh.faceC[f];
            long cx = (long) (mesh.vertexY[b] - mesh.vertexY[a]) * (mesh.vertexZ[c] - mesh.vertexZ[a]) - (long) (mesh.vertexZ[b] - mesh.vertexZ[a]) * (mesh.vertexY[c] - mesh.vertexY[a]);
            long cy = (long) (mesh.vertexZ[b] - mesh.vertexZ[a]) * (mesh.vertexX[c] - mesh.vertexX[a]) - (long) (mesh.vertexX[b] - mesh.vertexX[a]) * (mesh.vertexZ[c] - mesh.vertexZ[a]);
            long cz = (long) (mesh.vertexX[b] - mesh.vertexX[a]) * (mesh.vertexY[c] - mesh.vertexY[a]) - (long) (mesh.vertexY[b] - mesh.vertexY[a]) * (mesh.vertexX[c] - mesh.vertexX[a]);
            if (cx == 0 && cy == 0 && cz == 0) {
                flat++;
            }
            int texture = mesh.faceTexture == null ? -1 : mesh.faceTexture[f];
            if (texture != -1 && retexSrc != null && retexDst != null) {
                for (int i = 0; i < retexSrc.length && i < retexDst.length; i++) {
                    if (retexSrc[i] == texture) {
                        texture = retexDst[i];
                        break;
                    }
                }
            }
            if (texture != -1) {
                textured++;
                int id = texture & 0xFFFF;
                TextureMetrics metrics = id < textures.textureCount() ? textures.getMetrics(id) : null;
                if (metrics == null) {
                    notLoaded.merge(id, 1, Integer::sum);
                } else {
                    if (metrics.aBoolean234) {
                        materialSkipped++;
                    }
                    if (!textures.textureAvailable(id)) {
                        notLoaded.merge(id, 1, Integer::sum);
                    }
                }
            }
        }
        log("built " + whoBuilt + " faces=" + mesh.faceCount + " vertices=" + mesh.vertexCount + " height=" + (maxY - minY)
            + " version=" + mesh.version + " hiddenShading=" + hiddenShading + " hiddenBillboard=" + hiddenBillboard
            + " materialSkipped=" + materialSkipped + " invisibleAlpha=" + invisibleAlpha + " translucent=" + translucent
            + " zeroArea=" + flat + " textured=" + textured + " texturesNotLoaded=" + notLoaded);
        if (invisibleAlpha > 0) {
            log("model " + whoBuilt + ": " + invisibleAlpha + " of " + mesh.faceCount + " faces are fully transparent (alpha >= 254) - never drawn");
        }
        if (materialSkipped > 0) {
            log("model " + whoBuilt + ": " + materialSkipped + " textured faces use a material the GL model skips (TextureMetrics.aBoolean234)");
        }
        if (translucent * 10 >= mesh.faceCount && translucent > 0) {
            log("model " + whoBuilt + ": " + translucent + " of " + mesh.faceCount + " faces are translucent - the GL model draws them in priority order with depth writes, so parts can vanish by camera angle");
        }
        if (!notLoaded.isEmpty()) {
            log("model " + whoBuilt + " built before textures " + notLoaded.keySet() + " were loaded (faces per texture " + notLoaded + ")");
        }
    }

    /**
     * One animation frame transform ([type] 0 origin, 1 translate, 2 rotate, 3 scale, 5 alpha) with the values the toolkit
     * applies ([x], [y], [z]: scale in 1/128, alpha step in 1/8) to [labels] vertex/face groups.
     * <p>
     * A mirror scale (negative determinant: the triangles turn inside out and back-face culling hides them from the
     * front), a scale that collapses parts, a translation of more than 4 tiles and an alpha step that makes faces
     * invisible are all used on purpose by native 667 and OSRS animations (offline scan 2026-09-30: 2410 native and 283
     * imported sequences contain at least one), so they are logged - once per model, sequence and kind - rather than
     * reported as PROBLEM. Read them next to the owner's report: "npc X seq Y hides label groups" names what hid a part.
     */
    public static void transform(int type, int x, int y, int z, int labels) {
        if (!ENABLED || labels == 0) {
            return;
        }
        String kind = null;
        if (type == 3) {
            if ((long) x * y * z < 0) {
                kind = "mirror scale (faces turn inside out, culled from the front)";
            } else if (Math.abs(x) < COLLAPSE_SCALE || Math.abs(y) < COLLAPSE_SCALE || Math.abs(z) < COLLAPSE_SCALE) {
                kind = "scale to (almost) nothing (part hidden)";
            }
        } else if (type == 1) {
            if (Math.abs(x) > THROW_DISTANCE || Math.abs(y) > THROW_DISTANCE || Math.abs(z) > THROW_DISTANCE) {
                kind = "translation over 4 tiles (part moved away)";
            }
        } else if (type == 5 && x * 8 >= 247) {
            kind = "alpha step to invisible (part hidden)";
        }
        if (kind == null) {
            return;
        }
        String where = (who == null ? "unowned model" : who) + (frame == null ? "" : " " + frame.substring(0, frame.lastIndexOf(' ')));
        if (first("transform:" + where + ":" + kind)) {
            log("animation " + where + (frame == null ? "" : " (first at " + frame.substring(frame.lastIndexOf(' ') + 1) + ")") + ": " + kind
                + " on " + labels + " label group(s), values " + x + "," + y + "," + z);
        }
    }

    /**
     * An npc model animated with a movement sequence that is not in the npc's own BAS: the frames were made for another
     * skeleton (typically left over from the npc type before a transform / metamorphosis), so they move the wrong vertex
     * groups - parts are stretched, collapsed or hidden until the movement sequence is replaced.
     */
    public static void movementSequence(String npc, int basId, int seqId, int[] basSequences) {
        if (!ENABLED || seqId == -1) {
            return;
        }
        for (int id : basSequences) {
            if (id == seqId) {
                return;
            }
        }
        RspsTrace.problemAfter("stale-seq:" + npc + ":" + seqId, 3, "[modeltrace] " + npc + " (BAS " + basId + ") is animated with movement seq " + seqId
            + " which is not in its BAS - frames made for another skeleton move the wrong vertex groups (parts stretch or vanish)");
    }

    /** A GL texture bind whose image is not available; reported once it has happened [threshold] times. */
    public static void textureNotLoaded(int textureId) {
        if (ENABLED) {
            RspsTrace.problemAfter("gl-texture-missing:" + textureId, 600, "[modeltrace] GL drew faces with texture " + textureId + " 600 times without its image (not loaded / sprite missing)");
        }
    }
}
