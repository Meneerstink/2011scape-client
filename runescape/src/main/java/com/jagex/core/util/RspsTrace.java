package com.jagex.core.util;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * RSPS debug toolkit switch for client code outside the default package (manual: C:/RSPS/TRACES.md). On while
 * C:/RSPS/trace.on or C:/RSPS/avtrace.on exists at client start. {@link #problemOnce} prints a PROBLEM line once per key to
 * stdout (the launcher's client-*.out.log), so a fault that repeats every frame is reported once.
 */
public final class RspsTrace {

    public static final boolean ENABLED = new File("C:/RSPS/trace.on").exists() || new File("C:/RSPS/avtrace.on").exists();

    private static final Set<String> reported = new HashSet<>();

    private RspsTrace() {
        /* empty */
    }

    public static void problemOnce(String key, String message) {
        if (!ENABLED) {
            return;
        }
        synchronized (reported) {
            if (reported.size() > 20000 || !reported.add(key)) {
                return;
            }
        }
        System.out.println("[avtrace " + System.currentTimeMillis() + "] PROBLEM " + message);
    }

    private static final java.util.Map<String, Integer> counts = new java.util.HashMap<>();

    /** Counts [key]; the [threshold]-th time it is seen, prints [message] as a PROBLEM once (a wait that never ends). */
    public static void problemAfter(String key, int threshold, String message) {
        if (!ENABLED) {
            return;
        }
        int n;
        synchronized (counts) {
            if (counts.size() > 20000) {
                return;
            }
            n = counts.merge(key, 1, Integer::sum);
        }
        if (n == threshold) {
            problemOnce(key, message);
        }
    }

    /** A plain trace line, printed once per [key]. */
    public static void logOnce(String key, String message) {
        if (!ENABLED) {
            return;
        }
        synchronized (reported) {
            if (reported.size() > 20000 || !reported.add("log:" + key)) {
                return;
            }
        }
        System.out.println("[avtrace " + System.currentTimeMillis() + "] " + message);
    }
}
