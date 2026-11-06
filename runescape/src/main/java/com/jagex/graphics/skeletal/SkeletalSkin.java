package com.jagex.graphics.skeletal;

/**
 * N462 skeletal route A: the per-vertex bone influences of an npc's OSRS body model(s) (the 'fffd' skin block the 667 model format
 * drops), plus the OSRS bind positions and [scale] = client units per OSRS unit (the client upscales version<13 meshes by 4).
 * Sidecar format 'SKN1' written by the server's SkeletalSidecarExportTool.
 */
public final class SkeletalSkin {

    public final int vertexCount;
    final float scale;
    final int[] x, y, z;
    final int[][] bones;
    final int[][] weights;

    public SkeletalSkin(byte[] bytes) {
        SkeletalBuffer c = new SkeletalBuffer(bytes);
        if (c.i32() != 0x534B4E31) throw new IllegalArgumentException("not a SKN1 skin");
        this.scale = (float) c.f32();
        this.vertexCount = c.u16();
        this.x = new int[this.vertexCount];
        this.y = new int[this.vertexCount];
        this.z = new int[this.vertexCount];
        this.bones = new int[this.vertexCount][];
        this.weights = new int[this.vertexCount][];
        for (int v = 0; v < this.vertexCount; v++) {
            this.x[v] = c.i32();
            this.y[v] = c.i32();
            this.z[v] = c.i32();
            int n = c.u8();
            this.bones[v] = new int[n];
            this.weights[v] = new int[n];
            for (int j = 0; j < n; j++) {
                this.bones[v][j] = c.u16();
                this.weights[v][j] = c.u8();
            }
        }
    }

    /** Model vertex -> skin vertex, per model vertex count (built once from the bind positions; null entry = no match). */
    private final java.util.Map<Integer, int[]> maps = new java.util.HashMap<>();
    private static final int[] NO_MAP = new int[0];

    /**
     * The client merges multi-model npcs (Mesh(Mesh[])) in face order and welds vertices at the same position, so a model vertex index
     * is not the OSRS vertex index. Map every model vertex to the skin vertex at the same bind position (client units). Null when any
     * model vertex has no skin vertex there (then the classic frame is kept).
     */
    synchronized int[] mapFor(int[] mx, int[] my, int[] mz, int n, int readShift) {
        int[] map = this.maps.get(n);
        if (map == null) {
            java.util.Map<Long, Integer> at = new java.util.HashMap<>();
            for (int v = this.vertexCount - 1; v >= 0; v--) {
                at.put(key(Math.round(this.x[v] * this.scale), Math.round(this.y[v] * this.scale), Math.round(this.z[v] * this.scale)), v);
            }
            map = new int[n];
            int missing = 0;
            for (int i = 0; i < n; i++) {
                Integer v = at.get(key(mx[i] / readShift, my[i] / readShift, mz[i] / readShift));
                if (v == null) {
                    missing++;
                    map[i] = -1;
                } else {
                    map[i] = v;
                }
            }
            System.out.println("[skeletal] skin " + this.vertexCount + " vertices mapped onto a model of " + n + " vertices by bind position: " + (n - missing) + " matched, " + missing + " missing");
            if (missing > 0) map = NO_MAP;
            this.maps.put(n, map);
        }
        return map == NO_MAP ? null : map;
    }

    private static long key(long x, long y, long z) {
        return ((x & 0x1FFFFF) << 42) | ((y & 0x1FFFFF) << 21) | (z & 0x1FFFFF);
    }

    /**
     * Linear-blend skins every skin vertex with the model-space bone matrices [m] and writes the result for each model vertex (through
     * [map]), in client units times [shift] (16 between Model.NA and Model.wa), into [ox]/[oy]/[oz]. Same math as SkeletalBakeTool.skin:
     * Y and Z flipped around the matrices, weight / 255 like the OSRS client. Vertices without influences keep their bind position.
     */
    void skinInto(double[][] m, int[] map, int n, int[] ox, int[] oy, int[] oz, int shift) {
        double k = this.scale * shift;
        for (int i = 0; i < n; i++) {
            int v = map[i];
            int[] b = this.bones[v];
            double px = this.x[v];
            double py = -this.y[v];
            double pz = -this.z[v];
            if (b.length == 0) {
                ox[i] = (int) Math.round(px * k);
                oy[i] = (int) Math.round(-py * k);
                oz[i] = (int) Math.round(-pz * k);
                continue;
            }
            int[] w = this.weights[v];
            double sx = 0.0, sy = 0.0, sz = 0.0;
            for (int j = 0; j < b.length; j++) {
                if (b[j] >= m.length) continue;
                double[] a = m[b[j]];
                double wt = w[j] / 255.0;
                sx += wt * (a[0] * px + a[4] * py + a[8] * pz + a[12]);
                sy += wt * (a[1] * px + a[5] * py + a[9] * pz + a[13]);
                sz += wt * (a[2] * px + a[6] * py + a[10] * pz + a[14]);
            }
            ox[i] = (int) Math.round(sx * k);
            oy[i] = (int) Math.round(-sy * k);
            oz[i] = (int) Math.round(-sz * k);
        }
    }
}
