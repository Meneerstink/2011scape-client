package com.jagex.graphics.skeletal;

/**
 * N462 skeletal route A: an OSRS "animaya" clip (index 22): per bone up to 9 curves (rotation x/y/z, translation x/y/z, scale x/y/z).
 * Decoder and curve evaluation are a 1:1 port of the server's SkeletalBakeTool.decodeClip / Curve (itself a port of the OSRS client,
 * rev205 class122/class117/class113). Only bone transforms (type 1) are kept; alpha/label curves are not used yet (proof scope).
 */
public final class SkeletalClip {

    /** OSRS curve type -> slot (0-2 rotation, 3-5 translation, 6-8 scale). */
    private static final int[] CURVE_INDICES = { -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 0, 1, 2, 3, 4, 5, 0 };

    public final int rigId;
    public final int pose;
    /** [bone][slot], null when the bone (or slot) has no curve. */
    final Curve[][] boneCurves;
    /**
     * [rig transform] alpha curve (OSRS transform type 4 = class118.field1490, indexed by the rig's classic transform), null = none.
     * The OSRS client adds value * 255 to the original alpha of the faces in that transform's label groups (Model.method4260).
     */
    final Curve[] alphaCurves;
    public final boolean hasAlpha;
    public final int lastTick;

    public SkeletalClip(byte[] bytes, int boneCount) {
        this(bytes, boneCount, 0);
    }

    public SkeletalClip(byte[] bytes, int boneCount, int transformCount) {
        SkeletalBuffer c = new SkeletalBuffer(bytes);
        c.u8(); // version
        this.rigId = c.u16();
        c.u16();
        c.u16();
        this.pose = c.u8();
        int count = c.u16();
        this.boneCurves = new Curve[boneCount][];
        this.alphaCurves = new Curve[transformCount];
        boolean alpha = false;
        int last = 0;
        for (int i = 0; i < count; i++) {
            int transformType = c.u8();
            int bone = c.smart2();
            int curveType = c.u8();
            int n = c.u16();
            c.u8(); // type
            int startInterp = c.u8();
            if (startInterp > 4) startInterp = 0;
            int endInterp = c.u8();
            if (endInterp > 4) endInterp = 0;
            boolean bool = c.u8() != 0;
            Point[] points = new Point[n];
            for (int j = 0; j < n; j++) points[j] = new Point(c.s16(), c.f32(), c.f32(), c.f32(), c.f32(), c.f32());
            if (transformType == 1 && n > 0 && bone >= 0 && bone < boneCount) {
                int slot = curveType >= 0 && curveType < CURVE_INDICES.length ? CURVE_INDICES[curveType] : -1;
                if (slot >= 0) {
                    if (this.boneCurves[bone] == null) this.boneCurves[bone] = new Curve[9];
                    Curve curve = new Curve(startInterp, endInterp, bool, points);
                    this.boneCurves[bone][slot] = curve;
                    last = Math.max(last, curve.endTick);
                }
            } else if (transformType == 4 && n > 0 && bone >= 0 && bone < transformCount) {
                this.alphaCurves[bone] = new Curve(startInterp, endInterp, bool, points);
                alpha = true;
            }
        }
        this.hasAlpha = alpha;
        this.lastTick = last;
    }

    static final class Point {
        final int x;
        final double y, f2, f3, f4, f5;
        Point next;

        Point(int x, double y, double f2, double f3, double f4, double f5) {
            this.x = x; this.y = y; this.f2 = f2; this.f3 = f3; this.f4 = f4; this.f5 = f5;
        }
    }

    static final class Curve {
        final int startInterp, endInterp;
        final boolean bool;
        final Point[] points;
        final int startTick, endTick;
        private int pointIndex = 0;
        private boolean updated = true;
        private boolean interpBool = false;
        private double v0, v1, v2, v3, v4, v5, v6, v7, v8, v9;
        private final double[] values;

        Curve(int startInterp, int endInterp, boolean bool, Point[] points) {
            this.startInterp = startInterp;
            this.endInterp = endInterp;
            this.bool = bool;
            this.points = points;
            this.startTick = points[0].x;
            this.endTick = points[points.length - 1].x;
            for (int i = 0; i < points.length - 1; i++) points[i].next = points[i + 1];
            this.values = new double[this.endTick - this.startTick + 1];
            for (int i = 0; i < this.values.length; i++) this.values[i] = interpolate(this.startTick + i);
        }

        /** OSRS Curve.getValue after load: outside the range the (zero) min/max values - the OSRS client quirk. */
        double value(int t) {
            return t < this.startTick || t > this.endTick ? 0.0 : this.values[t - this.startTick];
        }

        private int indexFor(int t) {
            int p = this.pointIndex;
            if (p < 0 || this.points[p].x > t || (this.points[p].next != null && this.points[p].next.x <= t)) {
                if (t < this.startTick || t > this.endTick) return -1;
                int lo = 0;
                int hi = this.points.length - 1;
                int found = this.pointIndex;
                while (lo <= hi) {
                    int mid = (lo + hi) >> 1;
                    if (t < this.points[mid].x) {
                        if (t > this.points[mid - 1].x) { found = mid - 1; break; }
                        hi = mid - 1;
                    } else {
                        if (t <= this.points[mid].x) { found = mid; break; }
                        if (t < this.points[mid + 1].x) { found = mid; break; }
                        lo = mid + 1;
                    }
                }
                if (found != this.pointIndex) {
                    this.pointIndex = found;
                    this.updated = true;
                }
            }
            return this.pointIndex;
        }

        double interpolate(int t) {
            if (t < this.startTick) return this.startInterp == 0 ? this.points[0].y : extrapolate(t, true);
            if (t > this.endTick) return this.endInterp == 0 ? this.points[this.points.length - 1].y : extrapolate(t, false);
            int idx = indexFor(t);
            if (idx < 0 || idx >= this.points.length) return 0.0;
            Point point = this.points[idx];
            double max = Float.MAX_VALUE;
            boolean flat = false;
            boolean step = false;
            if (point.f4 == 0.0 && point.f5 == 0.0) {
                flat = true;
            } else if (point.f4 == max && point.f5 == max) {
                step = true;
            } else if (point.next == null) {
                flat = true;
            } else if (this.updated) {
                Point next = point.next;
                double x0 = point.x, y0 = point.y;
                double x1 = point.f4 * 0.33333334 + x0, y1 = point.f5 * 0.33333334 + y0;
                double x3 = next.x, y3 = next.y;
                double x2 = x3 - next.f2 * 0.33333334, y2 = y3 - next.f3 * 0.33333334;
                if (this.bool) {
                    double ya = y1;
                    double yb = y2;
                    double span = x3 - x0;
                    if (span != 0.0) {
                        double[] v = { (x1 - x0) / span, (x2 - x0) / span };
                        this.interpBool = v[0] == 0.33333334 && v[1] == 0.6666667;
                        double o0 = v[0];
                        double o1 = v[1];
                        if (v[0] < 0.0) v[0] = 0.0;
                        if (v[1] > 1.0) v[1] = 1.0;
                        if (v[0] > 1.0 || v[1] < -1.0) clampBezier(v);
                        if (v[0] != o0 && o0 != 0.0) ya = (y1 - y0) * v[0] / o0 + y0;
                        if (o1 != v[1] && o1 != 1.0) yb = y3 - (1.0 - v[1]) * (y3 - y2) / (1.0 - o1);
                        this.v0 = x0;
                        this.v1 = x3;
                        double a = v[0];
                        double b = v[1] - v[0];
                        double cc = 1.0 - v[1];
                        double d = b - a;
                        this.v5 = cc - b - d; this.v4 = d + d + d; this.v3 = a + a + a; this.v2 = 0.0;
                        a = ya - y0; b = yb - ya; cc = y3 - yb; d = b - a;
                        this.v9 = cc - b - d; this.v8 = d + d + d; this.v7 = a + a + a; this.v6 = y0;
                    }
                } else {
                    this.v0 = x0;
                    double dx = x3 - x0;
                    double dy = y3 - y0;
                    double s0 = 0.0;
                    double s1 = 0.0;
                    if (x1 - x0 != 0.0) s0 = (y1 - y0) / (x1 - x0);
                    if (x3 - x2 != 0.0) s1 = (y3 - y2) / (x3 - x2);
                    double inv = 1.0 / (dx * dx);
                    double a = s0 * dx;
                    double b = s1 * dx;
                    this.v2 = inv * (a + b - dy - dy) / dx;
                    this.v3 = inv * (dy + dy + dy - a - a - b);
                    this.v4 = s0;
                    this.v5 = y0;
                }
                this.updated = false;
            }
            if (flat) return point.y;
            if (step) return point.x != t && point.next != null ? point.next.y : point.y;
            if (this.bool) return bezier(t);
            double d = t - this.v0;
            return this.v5 + d * ((d * this.v2 + this.v3) * d + this.v4);
        }

        private double extrapolate(int t, boolean start) {
            double x0 = this.points[0].x;
            double x1 = this.points[this.points.length - 1].x;
            double span = x1 - x0;
            if (span == 0.0) return this.points[0].y;
            double f = t > x1 ? (t - x1) / span : (t - x0) / span;
            double whole = (int) f;
            double frac = Math.abs(f - whole);
            double local = frac * span;
            whole = Math.abs(1.0 + whole);
            double half = whole / 2.0;
            frac = half - (int) half;
            int mode = start ? this.startInterp : this.endInterp;
            if (start) {
                if (mode == 4) {
                    local = frac != 0.0 ? local + x0 : x1 - local;
                } else if (mode == 2 || mode == 3) {
                    local = x1 - local;
                } else if (mode == 1) {
                    Point p = this.points[0];
                    return p.f2 != 0.0 ? p.y - (x0 - t) * p.f3 / p.f2 : p.y;
                }
            } else {
                if (mode == 4) {
                    local = frac != 0.0 ? x1 - local : local + x0;
                } else if (mode == 2 || mode == 3) {
                    local += x0;
                } else if (mode == 1) {
                    Point p = this.points[this.points.length - 1];
                    return p.f4 != 0.0 ? p.y + p.f5 * (t - x1) / p.f4 : p.y;
                }
            }
            double out = interpolate((int) local);
            double rise = this.points[this.points.length - 1].y - this.points[0].y;
            if (start && mode == 3) out -= rise * whole;
            if (!start && mode == 3) out += rise * whole;
            return out;
        }

        private static void clampBezier(double[] v) {
            double ulp = 1.1920929e-7;
            v[1] = 1.0 - v[1];
            if (v[0] < 0.0) v[0] = 0.0;
            if (v[1] < 0.0) v[1] = 0.0;
            if (v[0] > 1.0 || v[1] > 1.0) {
                double q = 1.0 + v[0] * (v[0] - 2.0 + v[1]) + (v[1] - 2.0) * v[1];
                if (q + ulp > 0.0) {
                    if (ulp + v[0] < 1.3333334) {
                        double a = v[0] - 2.0;
                        double b = v[0] - 1.0;
                        double r = Math.sqrt(a * a - 4.0 * b * b);
                        double hi = 0.5 * (r - a);
                        if (v[1] + ulp > hi) {
                            v[1] = hi - ulp;
                        } else {
                            double lo = (-a - r) * 0.5;
                            if (v[1] < lo + ulp) v[1] = lo + ulp;
                        }
                    } else {
                        v[0] = 1.3333334 - ulp;
                        v[1] = 0.33333334 - ulp;
                    }
                }
            }
            v[1] = 1.0 - v[1];
        }

        /** Solve the bezier x(s) = t for s (bisection), then y(s). */
        private double bezier(double t) {
            double u = this.v0 == t ? 0.0 : t == this.v1 ? 1.0 : (t - this.v0) / (this.v1 - this.v0);
            double s;
            if (this.interpBool) {
                s = u;
            } else {
                double lo = 0.0;
                double hi = 1.0;
                for (int i = 0; i < 60; i++) {
                    double mid = (lo + hi) / 2;
                    double x = this.v2 + mid * (this.v3 + mid * (this.v4 + mid * this.v5));
                    if (x < u) lo = mid; else hi = mid;
                }
                s = (lo + hi) / 2;
            }
            return s * (this.v7 + s * (s * this.v9 + this.v8)) + this.v6;
        }
    }
}
