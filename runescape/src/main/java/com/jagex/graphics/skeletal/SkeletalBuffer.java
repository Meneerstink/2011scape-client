package com.jagex.graphics.skeletal;

/** Big-endian reader for the OSRS skeletal formats (rig, animaya clip, skin sidecar). */
final class SkeletalBuffer {

    private final byte[] b;
    int p;

    SkeletalBuffer(byte[] b) {
        this.b = b;
    }

    int u8() {
        return this.b[this.p++] & 0xFF;
    }

    int s16() {
        return (short) ((u8() << 8) | u8());
    }

    int u16() {
        return (u8() << 8) | u8();
    }

    int i32() {
        return (u16() << 16) | u16();
    }

    double f32() {
        return Float.intBitsToFloat(i32());
    }

    /** OSRS readShortSmart: a signed smart (u8 - 64 or u16 - 49152). */
    int smart2() {
        return (this.b[this.p] & 0xFF) < 128 ? u8() - 64 : u16() - 49152;
    }

    int remaining() {
        return this.b.length - this.p;
    }
}
