package com.jagex.graphics.skeletal;

/** 4x4 column-major matrices and quaternions (gl-matrix conventions), a 1:1 port of the server's SkeletalBakeTool math. */
final class SkeletalMath {

    private SkeletalMath() {
    }

    static double[] identity() {
        double[] m = new double[16];
        m[0] = 1.0; m[5] = 1.0; m[10] = 1.0; m[15] = 1.0;
        return m;
    }

    static double[] mul(double[] a, double[] b) {
        double[] o = new double[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                double s = 0.0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
                o[c * 4 + r] = s;
            }
        }
        return o;
    }

    static double[] invert(double[] a) {
        double a00 = a[0], a01 = a[1], a02 = a[2], a03 = a[3];
        double a10 = a[4], a11 = a[5], a12 = a[6], a13 = a[7];
        double a20 = a[8], a21 = a[9], a22 = a[10], a23 = a[11];
        double a30 = a[12], a31 = a[13], a32 = a[14], a33 = a[15];
        double b00 = a00 * a11 - a01 * a10, b01 = a00 * a12 - a02 * a10, b02 = a00 * a13 - a03 * a10;
        double b03 = a01 * a12 - a02 * a11, b04 = a01 * a13 - a03 * a11, b05 = a02 * a13 - a03 * a12;
        double b06 = a20 * a31 - a21 * a30, b07 = a20 * a32 - a22 * a30, b08 = a20 * a33 - a23 * a30;
        double b09 = a21 * a32 - a22 * a31, b10 = a21 * a33 - a23 * a31, b11 = a22 * a33 - a23 * a32;
        double det = b00 * b11 - b01 * b10 + b02 * b09 + b03 * b08 - b04 * b07 + b05 * b06;
        if (det == 0.0) return identity();
        det = 1.0 / det;
        return new double[] {
            (a11 * b11 - a12 * b10 + a13 * b09) * det, (a02 * b10 - a01 * b11 - a03 * b09) * det,
            (a31 * b05 - a32 * b04 + a33 * b03) * det, (a22 * b04 - a21 * b05 - a23 * b03) * det,
            (a12 * b08 - a10 * b11 - a13 * b07) * det, (a00 * b11 - a02 * b08 + a03 * b07) * det,
            (a32 * b02 - a30 * b05 - a33 * b01) * det, (a20 * b05 - a22 * b02 + a23 * b01) * det,
            (a10 * b10 - a11 * b08 + a13 * b06) * det, (a01 * b08 - a00 * b10 - a03 * b06) * det,
            (a30 * b04 - a31 * b02 + a33 * b00) * det, (a21 * b02 - a20 * b04 - a23 * b00) * det,
            (a11 * b07 - a10 * b09 - a12 * b06) * det, (a00 * b09 - a01 * b07 + a02 * b06) * det,
            (a31 * b01 - a30 * b03 - a32 * b00) * det, (a20 * b03 - a21 * b01 + a22 * b00) * det,
        };
    }

    /** Quaternion (x, y, z, w) of a rotation of [rad] around the unit axis. */
    static double[] axisAngle(double ax, double ay, double az, double rad) {
        double s = Math.sin(rad / 2);
        return new double[] { ax * s, ay * s, az * s, Math.cos(rad / 2) };
    }

    static double[] qmul(double[] a, double[] b) {
        double ax = a[0], ay = a[1], az = a[2], aw = a[3];
        double bx = b[0], by = b[1], bz = b[2], bw = b[3];
        return new double[] {
            ax * bw + aw * bx + ay * bz - az * by,
            ay * bw + aw * by + az * bx - ax * bz,
            az * bw + aw * bz + ax * by - ay * bx,
            aw * bw - ax * bx - ay * by - az * bz,
        };
    }

    static double[] fromQuat(double[] q) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        double x2 = x + x, y2 = y + y, z2 = z + z;
        double xx = x * x2, yx = y * x2, yy = y * y2, zx = z * x2, zy = z * y2, zz = z * z2;
        double wx = w * x2, wy = w * y2, wz = w * z2;
        return new double[] { 1 - yy - zz, yx + wz, zx - wy, 0.0, yx - wz, 1 - xx - zz, zy + wx, 0.0, zx + wy, zy - wx, 1 - xx - yy, 0.0, 0.0, 0.0, 0.0, 1.0 };
    }
}
