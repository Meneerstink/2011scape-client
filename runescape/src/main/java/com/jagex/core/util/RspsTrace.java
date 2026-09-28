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
}
