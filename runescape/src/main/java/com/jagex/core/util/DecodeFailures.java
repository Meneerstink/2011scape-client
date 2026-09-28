package com.jagex.core.util;

import java.util.HashSet;
import java.util.Set;

/**
 * K-04: one log line per (kind, id) when a cache record (model, seq) fails to decode and the client falls back to an
 * empty/default record instead of letting the exception reach the render loop.
 */
public final class DecodeFailures {

    private static final Set<String> reported = new HashSet<>();

    private DecodeFailures() {
        /* empty */
    }

    public static void report(String kind, int id, Throwable failure) {
        String key = kind + ':' + id;
        synchronized (reported) {
            if (!reported.add(key)) {
                return;
            }
        }
        System.out.println("[decode] " + kind + " " + id + " is corrupt, using fallback: " + failure);
        RspsBugLog.write("cache record failed to decode: " + kind + " " + id + " (shown with a fallback)", failure);
    }
}
