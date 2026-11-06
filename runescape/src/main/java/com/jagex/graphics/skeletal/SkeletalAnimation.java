package com.jagex.graphics.skeletal;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * N462 skeletal route A (client proof, 2026-11-06): a 667 sequence id played with the REAL OSRS skeletal data (rig + animaya clip +
 * skin) instead of its classic frames. The clip is evaluated per client cycle exactly like the OSRS client (SkeletalBakeTool.finalMatrices
 * port); the per-tick bone matrices are immutable and cached, so many actors can share them.
 *
 * Data: cache index 10 (BINARY) - group 'skel_index' (seq.<id>=clip|rig|skin|start|end) and one group 'skel_<name>' per piece, written by
 * the server's SkeletalSidecarExportTool; a test client may instead pass -Prsps.skeletalDir (a folder with <name>.bin files). A cache
 * without 'skel_index' plays every sequence classic, exactly as before. Unregistered sequences (attacks etc.) stay classic (baked).
 */
public final class SkeletalAnimation {

    public final int seqId;
    public final SkeletalRig rig;
    public final SkeletalClip clip;
    public final SkeletalSkin skin;
    public final int start;
    public final int end;
    /** OSRS bone mask (sequence opcode 17): an action animates the bones NOT in it, the movement the bones in it; null = whole body. */
    public final boolean[] mask;
    /** The OSRS sequence this plays (for traces). */
    public final int osrsSeq;
    /** Rig transforms with an alpha curve in this clip (type 5 in the rig, type 4 curve in the clip). */
    private final int[] alphaTransforms;
    private final double[][][] matrices;

    private SkeletalAnimation(int seqId, SkeletalRig rig, SkeletalClip clip, SkeletalSkin skin, int start, int end, boolean[] mask, int osrsSeq) {
        this.seqId = seqId;
        this.rig = rig;
        this.clip = clip;
        this.skin = skin;
        this.start = start;
        this.end = Math.max(end, start + 1);
        this.mask = mask;
        this.osrsSeq = osrsSeq;
        this.matrices = new double[this.end - this.start][][];
        int n = 0;
        for (int i = 0; i < clip.alphaCurves.length; i++) if (clip.alphaCurves[i] != null && i < rig.transformTypes.length && rig.transformTypes[i] == 5) n++;
        this.alphaTransforms = new int[n];
        n = 0;
        for (int i = 0; i < clip.alphaCurves.length; i++) if (clip.alphaCurves[i] != null && i < rig.transformTypes.length && rig.transformTypes[i] == 5) this.alphaTransforms[n++] = i;
    }

    private int clamp(int tick) {
        return tick < this.start ? this.start : tick >= this.end ? this.end - 1 : tick;
    }

    /** Model-space skinning matrices (bone animation x inverse bind) of every bone at clip tick [tick]; cached per tick. */
    public double[][] matrices(int tick) {
        tick = clamp(tick);
        int i = tick - this.start;
        double[][] m = this.matrices[i];
        if (m == null) {
            m = compose(tick, null, 0);
            this.matrices[i] = m;
        }
        return m;
    }

    /**
     * Action [this] at [tick] blended with [movement] at [movementTick] like OSRS SequenceDefinition.applyTransformations: bones in this
     * action's mask take the movement's curves, the others this action's. Same rig required (else the action alone).
     */
    public double[][] blended(int tick, SkeletalAnimation movement, int movementTick) {
        if (movement == null || this.mask == null || movement.rig != this.rig) return matrices(tick);
        return compose(clamp(tick), movement, movement.clamp(movementTick));
    }

    private double[][] compose(int tick, SkeletalAnimation movement, int movementTick) {
        SkeletalRig r = this.rig;
        int pose = Math.min(this.clip.pose, r.poseCount - 1);
        double[][] anim = new double[r.boneCount][];
        for (int b = 0; b < r.boneCount; b++) {
            boolean fromMovement = movement != null && b < this.mask.length && this.mask[b];
            anim[b] = fromMovement ? movement.local(b, movementTick, pose) : local(b, tick, pose);
        }
        double[][] model = new double[r.boneCount][];
        double[][] out = new double[r.boneCount][];
        for (int b = 0; b < r.boneCount; b++) out[b] = SkeletalMath.mul(modelOf(model, anim, b, 0), r.inverseBind(pose, b));
        return out;
    }

    /** Local animation matrix of bone [b] at [tick]: rotation (Z, X, Y quaternions) times scale, then translation (OSRS class122). */
    private double[] local(int b, int tick, int pose) {
        SkeletalRig r = this.rig;
        SkeletalClip.Curve[] curves = this.clip.boneCurves[b];
        double[] rot = r.rotation[b][pose];
        double[] s = r.scaling[b][pose];
        double[] t = r.translation[b][pose];
        double[] q = { 0.0, 0.0, 0.0, 1.0 };
        q = SkeletalMath.qmul(SkeletalMath.axisAngle(0.0, 0.0, 1.0, ch(curves, 2, tick, rot[2])), q);
        q = SkeletalMath.qmul(SkeletalMath.axisAngle(1.0, 0.0, 0.0, ch(curves, 0, tick, rot[0])), q);
        q = SkeletalMath.qmul(SkeletalMath.axisAngle(0.0, 1.0, 0.0, ch(curves, 1, tick, rot[1])), q);
        double[] m = SkeletalMath.fromQuat(q);
        double[] sm = SkeletalMath.identity();
        sm[0] = ch(curves, 6, tick, s[0]);
        sm[5] = ch(curves, 7, tick, s[1]);
        sm[10] = ch(curves, 8, tick, s[2]);
        m = SkeletalMath.mul(sm, m);
        m[12] = ch(curves, 3, tick, t[0]);
        m[13] = ch(curves, 4, tick, t[1]);
        m[14] = ch(curves, 5, tick, t[2]);
        return m;
    }

    private double[] modelOf(double[][] memo, double[][] anim, int b, int depth) {
        if (memo[b] != null) return memo[b];
        if (depth > this.rig.boneCount) throw new IllegalStateException("bone parent cycle at " + b);
        int p = this.rig.parent[b];
        double[] m = p >= 0 && p < this.rig.boneCount ? SkeletalMath.mul(modelOf(memo, anim, p, depth + 1), anim[b]) : anim[b];
        memo[b] = m;
        return m;
    }

    private static double ch(SkeletalClip.Curve[] curves, int slot, int tick, double def) {
        if (curves == null || curves[slot] == null) return def;
        return curves[slot].value(tick);
    }

    /** True when this clip changes face alpha (the model copy then needs its own alpha array: function mask 0x100). */
    public boolean hasAlpha() {
        return this.alphaTransforms.length > 0;
    }

    /** Rig transforms (type 5, face label groups in {@link SkeletalRig#labels}) that carry an alpha curve. */
    public int[] alphaTransforms() {
        return this.alphaTransforms;
    }

    /** The 667 type-5 transform amount for rig transform [transform] at [tick]: OSRS adds value * 255, the 667 transform adds dx * 8. */
    public int alphaDelta(int transform, int tick) {
        double v = this.clip.alphaCurves[transform].value(clamp(tick));
        return (int) Math.round(v * 255.0 / 8.0);
    }

    /**
     * Skins the model's vertices into the arrays, which hold the bind positions times [readShift] on entry (used once to map model
     * vertices onto skin vertices) and get client units times [shift]. False when the model does not match the skin.
     */
    public boolean skinInto(double[][] matrices, int[] x, int[] y, int[] z, int vertexCount, int readShift, int shift) {
        if (x == null || x.length < vertexCount || vertexCount <= 0) return false;
        int[] map = this.skin.mapFor(x, y, z, vertexCount, readShift);
        if (map == null) return false;
        this.skin.skinInto(matrices, map, vertexCount, x, y, z, shift);
        return true;
    }

    // ---- registry: the cache (index 10 'skel_index' + 'skel_<name>' groups) or, in a test client, -Prsps.skeletalDir ------------

    /** Test-hook switch ('skeletal on|off') to compare with the classic (baked) frames. */
    public static volatile boolean enabled = true;

    private static final Object LOCK = new Object();
    /** seq -> {clip, rig, skin, start, end}; null = index not loaded yet, empty = nothing skeletal. */
    private static Map<Integer, String[]> specs;
    private static final Map<Integer, SkeletalAnimation> loaded = new HashMap<>();
    private static final java.util.Set<Integer> failed = new java.util.HashSet<>();
    private static final Map<String, Object> parts = new HashMap<>();

    /**
     * The skeletal animation of local sequence [seqId], or null (play the classic frames). Everything loads lazily: while the index or
     * one of the pieces is still downloading this returns null and the next frame tries again.
     */
    public static SkeletalAnimation forSeq(int seqId) {
        if (!enabled) return null;
        synchronized (LOCK) {
            if (specs == null) {
                try {
                    specs = loadIndex();
                } catch (RuntimeException e) {
                    // Never let the skeletal index break rendering: an unexpected cache shape = play everything classic.
                    System.out.println("[skeletal] index unavailable, all sequences classic: " + e);
                    specs = new HashMap<>();
                }
                if (specs == null) return null;
            }
            if (specs.isEmpty()) return null;
            String[] f = specs.get(seqId);
            if (f == null) return null;
            SkeletalAnimation a = loaded.get(seqId);
            if (a != null || failed.contains(seqId)) return a;
            try {
                byte[] clipB = piece(f[0]);
                byte[] rigB = parts.containsKey(f[1]) ? null : piece(f[1]);
                byte[] skinB = parts.containsKey(f[2]) ? null : piece(f[2]);
                if (clipB == null || (rigB == null && !parts.containsKey(f[1])) || (skinB == null && !parts.containsKey(f[2]))) return null;
                SkeletalRig rig = (SkeletalRig) parts.computeIfAbsent(f[1], n -> new SkeletalRig(rigB));
                SkeletalSkin skin = (SkeletalSkin) parts.computeIfAbsent(f[2], n -> new SkeletalSkin(skinB));
                SkeletalClip clip = new SkeletalClip(clipB, rig.boneCount, rig.transformTypes.length);
                a = new SkeletalAnimation(seqId, rig, clip, skin, Integer.parseInt(f[3].trim()), Integer.parseInt(f[4].trim()), parseMask(f), f.length > 6 && !f[6].trim().isEmpty() ? Integer.parseInt(f[6].trim()) : -1);
                loaded.put(seqId, a);
                System.out.println("[skeletal] seq " + seqId + " <- " + f[0] + " rig " + f[1] + " (" + rig.boneCount + " bones) skin " + f[2] + " (" + skin.vertexCount + " vertices) ticks " + f[3] + ".." + f[4] + (a.mask != null ? " mask" : "") + (a.hasAlpha() ? " alpha x" + a.alphaTransforms().length : "") + " (OSRS " + a.osrsSeq + ") from " + source());
                return a;
            } catch (RuntimeException e) {
                failed.add(seqId);
                System.out.println("[skeletal] seq " + seqId + " skipped: " + e);
                return null;
            }
        }
    }

    private static String source() {
        String dir = System.getProperty("rsps.skeletalDir");
        return dir == null || dir.isEmpty() ? "cache" : dir;
    }

    /** The seq table, null while it is not available yet. */
    private static Map<Integer, String[]> loadIndex() {
        String text;
        String dir = System.getProperty("rsps.skeletalDir");
        if (dir != null && !dir.isEmpty()) {
            File props = new File(dir, "skeletal.properties");
            try {
                text = props.isFile() ? new String(Files.readAllBytes(props.toPath()), java.nio.charset.StandardCharsets.UTF_8) : "";
            } catch (IOException e) {
                text = "";
            }
        } else {
            com.jagex.js5.js5 bin = com.jagex.js5.js5.BINARY;
            if (bin == null || !bin.indexReady()) return null;
            if (!bin.groupExists(INDEX_GROUP)) return new HashMap<>();
            byte[] b = bin.getfile(INDEX_GROUP, "");
            if (b == null) return null;
            text = new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }
        Properties p = new Properties();
        try {
            p.load(new java.io.StringReader(text));
        } catch (IOException e) {
            return new HashMap<>();
        }
        Map<Integer, String[]> out = new HashMap<>();
        for (String key : p.stringPropertyNames()) {
            if (!key.startsWith("seq.")) continue;
            try {
                String[] f = p.getProperty(key).split("\\|");
                if (f.length < 5) continue;
                for (int i = 0; i < 3; i++) if (f[i].endsWith(".bin")) f[i] = f[i].substring(0, f[i].length() - 4);
                out.put(Integer.parseInt(key.substring(4).trim()), f);
            } catch (RuntimeException ignored) {
            }
        }
        System.out.println("[skeletal] index: " + out.size() + " skeletal sequences from " + source());
        return out;
    }

    /** Field 6 of an index line: the OSRS bone mask as a comma list (empty = none). */
    private static boolean[] parseMask(String[] f) {
        if (f.length < 6 || f[5].trim().isEmpty()) return null;
        boolean[] mask = new boolean[256];
        for (String bone : f[5].split(",")) {
            int b = Integer.parseInt(bone.trim());
            if (b >= 0 && b < mask.length) mask[b] = true;
        }
        return mask;
    }

    private static final String INDEX_GROUP = "skel_index";
    private static final String GROUP_PREFIX = "skel_";

    /** One named piece (rig/clip/skin), null while it downloads. */
    private static byte[] piece(String name) {
        String dir = System.getProperty("rsps.skeletalDir");
        if (dir != null && !dir.isEmpty()) {
            try {
                return Files.readAllBytes(new File(dir, name + ".bin").toPath());
            } catch (IOException e) {
                throw new IllegalArgumentException("cannot read " + name + ": " + e);
            }
        }
        com.jagex.js5.js5 bin = com.jagex.js5.js5.BINARY;
        if (bin == null) return null;
        if (!bin.groupExists(GROUP_PREFIX + name)) throw new IllegalArgumentException("cache has no group " + GROUP_PREFIX + name);
        return bin.getfile(GROUP_PREFIX + name, "");
    }
}
