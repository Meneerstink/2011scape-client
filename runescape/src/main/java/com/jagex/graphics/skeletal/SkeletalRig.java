package com.jagex.graphics.skeletal;

/**
 * N462 skeletal route A: the bone rig of an OSRS skeletal animation (the skeletal tail of an OSRS index-1 framemap).
 * Port of the OSRS client (rev205 class202/class115) via the server's SkeletalBakeTool.skeletalBase / Bone, which this mirrors 1:1.
 */
public final class SkeletalRig {

    public final int boneCount;
    public final int poseCount;
    public final int[] parent;
    /** [bone][pose] local bind matrix (column-major 4x4). */
    final double[][][] local;
    /** [bone][pose] default euler rotation (x, y, z), translation and scale, as the OSRS client derives them from the bind matrix. */
    final double[][][] rotation;
    final double[][][] translation;
    final double[][][] scaling;
    /** [pose][bone] inverse of the model-space bind matrix, computed once. */
    private final double[][][] inverseBind;

    /** The classic part of the framemap: transform types and their label groups (type 5 = face alpha on face labels). */
    public final int[] transformTypes;
    public final int[][] labels;

    public SkeletalRig(byte[] framemap) {
        SkeletalBuffer c = new SkeletalBuffer(framemap);
        int count = c.u8();
        this.transformTypes = new int[count];
        for (int i = 0; i < count; i++) this.transformTypes[i] = c.u8();
        this.labels = new int[count][];
        for (int i = 0; i < count; i++) this.labels[i] = new int[c.u8()];
        for (int i = 0; i < count; i++) for (int j = 0; j < this.labels[i].length; j++) this.labels[i][j] = c.u8();
        if (c.remaining() < 2) throw new IllegalArgumentException("framemap has no skeletal tail");
        this.boneCount = c.u16();
        if (this.boneCount == 0) throw new IllegalArgumentException("framemap has no bones");
        this.poseCount = c.u8();
        this.parent = new int[this.boneCount];
        this.local = new double[this.boneCount][this.poseCount][];
        this.rotation = new double[this.boneCount][this.poseCount][];
        this.translation = new double[this.boneCount][this.poseCount][];
        this.scaling = new double[this.boneCount][this.poseCount][];
        for (int b = 0; b < this.boneCount; b++) {
            this.parent[b] = c.s16();
            for (int p = 0; p < this.poseCount; p++) {
                double[] m = new double[16];
                for (int i = 0; i < 16; i++) m[i] = c.f32();
                c.f32(); c.f32(); c.f32();
                this.local[b][p] = m;
                double[] inv = SkeletalMath.invert(m);
                double[] r = new double[3];
                r[0] = -Math.asin(inv[6]);
                if (Math.abs(Math.cos(r[0])) > 0.005) {
                    r[1] = Math.atan2(inv[2], inv[10]);
                    r[2] = Math.atan2(inv[4], inv[5]);
                } else {
                    r[1] = inv[6] < 0 ? Math.atan2(inv[1], inv[0]) : -Math.atan2(inv[1], inv[0]);
                    r[2] = 0.0;
                }
                this.rotation[b][p] = r;
                this.translation[b][p] = new double[] { m[12], m[13], m[14] };
                this.scaling[b][p] = new double[] { len(m[0], m[1], m[2]), len(m[4], m[5], m[6]), len(m[8], m[9], m[10]) };
            }
        }
        this.inverseBind = new double[this.poseCount][][];
        for (int p = 0; p < this.poseCount; p++) {
            double[][] bind = new double[this.boneCount][];
            double[][] inv = new double[this.boneCount][];
            for (int b = 0; b < this.boneCount; b++) inv[b] = SkeletalMath.invert(bindOf(bind, b, p, 0));
            this.inverseBind[p] = inv;
        }
    }

    private double[] bindOf(double[][] memo, int b, int pose, int depth) {
        if (memo[b] != null) return memo[b];
        if (depth > this.boneCount) throw new IllegalStateException("bone parent cycle at " + b);
        int p = this.parent[b];
        double[] m = p >= 0 && p < this.boneCount ? SkeletalMath.mul(bindOf(memo, p, pose, depth + 1), this.local[b][pose]) : this.local[b][pose];
        memo[b] = m;
        return m;
    }

    double[] inverseBind(int pose, int bone) {
        return this.inverseBind[pose][bone];
    }

    private static double len(double x, double y, double z) {
        return Math.sqrt(x * x + y * y + z * z);
    }
}
