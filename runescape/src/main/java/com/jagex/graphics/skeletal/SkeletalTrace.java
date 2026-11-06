package com.jagex.graphics.skeletal;

import java.util.HashSet;
import java.util.Set;

/** N462: one console line per distinct skeletal problem (never per frame). */
public final class SkeletalTrace {

    private static final Set<String> seen = new HashSet<>();

    private SkeletalTrace() {
    }

    public static synchronized void mismatch(SkeletalAnimation animation, int vertexCount) {
        String key = "mismatch " + animation.seqId + " " + vertexCount;
        if (seen.add(key)) {
            System.out.println("[skeletal] seq " + animation.seqId + ": model has " + vertexCount + " vertices, skin " + animation.skin.vertexCount + " - classic frame kept");
        }
    }

    public static synchronized void first(SkeletalAnimation animation, String renderer) {
        String key = "first " + animation.seqId + " " + renderer;
        if (seen.add(key)) {
            System.out.println("[skeletal] seq " + animation.seqId + " playing skeletal on " + renderer);
        }
    }
}
