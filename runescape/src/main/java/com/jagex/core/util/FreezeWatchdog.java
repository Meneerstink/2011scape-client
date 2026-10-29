package com.jagex.core.util;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

/**
 * RSPS debug toolkit (TRACES.md): names the cause of a client freeze. The game loop's "client froze N ms (logic .., drawing ..)"
 * line only says which half was slow (owner session 2026-10-01: 2.5 - 3.7 s drawing stalls while standing idle at the Grand
 * Exchange, nothing else in either log). While a logic or draw pass runs longer than {@link #SAMPLE_MS}, this daemon thread
 * takes the game thread's stack - once at {@link #SAMPLE_MS} and once more at {@link #SECOND_SAMPLE_MS} - and prints it with the
 * garbage-collection time spent since the pass began, so the next freeze names the method (or the GC) it was stuck in.
 * N244 (owner 2026-10-23: "client liep vast toen ik dood ging", no client log): it now runs in every client and appends each
 * sample to &lt;home&gt;/78legacy/logs/freeze.log ({@link #LOG}, last {@link #KEEP_BYTES} kept), which the next bug report's
 * screenshot carries to the server (RspsBugShot.withFreezeLog) - also after a freeze the player had to close the client for.
 * With the traces on the line goes to the console too, as before.
 */
public final class FreezeWatchdog {

    public static final int IDLE = 0;
    public static final int LOGIC = 1;
    public static final int DRAW = 2;

    private static final long SAMPLE_MS = 400L;
    private static final long SECOND_SAMPLE_MS = 2000L;
    private static final int FRAMES = 14;
    private static final long KEEP_BYTES = 64 * 1024L;

    /** The freeze samples of this and earlier sessions (the player's own PC). */
    public static final java.io.File LOG = new java.io.File(System.getProperty("user.home"), "78legacy/logs/freeze.log");

    private static volatile Thread game;
    private static volatile int phase = IDLE;
    private static volatile long phaseStartNanos;
    private static volatile long passId;
    private static volatile long gcAtPassStart;

    private FreezeWatchdog() {
        /* empty */
    }

    /** Starts the watchdog for the calling (game) thread; once. */
    public static synchronized void start() {
        if (game != null) {
            return;
        }
        game = Thread.currentThread();
        Thread watcher = new Thread(FreezeWatchdog::watch, "rsps-freeze-watchdog");
        watcher.setDaemon(true);
        watcher.start();
    }

    /** The game thread enters [newPhase] (LOGIC / DRAW) or leaves the pass (IDLE). */
    public static void phase(int newPhase) {
        if (game == null) {
            return;
        }
        if (newPhase != IDLE && phase == IDLE) {
            passId++;
            gcAtPassStart = gcMillis();
        }
        phaseStartNanos = System.nanoTime();
        phase = newPhase;
    }

    private static void watch() {
        long sampledPass = -1L;
        int samples = 0;
        while (true) {
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                return;
            }
            int now = phase;
            if (now == IDLE) {
                continue;
            }
            long pass = passId;
            long ms = (System.nanoTime() - phaseStartNanos) / 1_000_000L;
            if (pass != sampledPass) {
                sampledPass = pass;
                samples = 0;
            }
            long due = samples == 0 ? SAMPLE_MS : SECOND_SAMPLE_MS;
            if (samples >= 2 || ms < due) {
                continue;
            }
            samples++;
            Thread t = game;
            if (t == null) {
                return;
            }
            StackTraceElement[] stack = t.getStackTrace();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < stack.length && i < FRAMES; i++) {
                sb.append(i == 0 ? "" : " <- ").append(stack[i].getClassName()).append('.').append(stack[i].getMethodName())
                    .append(':').append(stack[i].getLineNumber());
            }
            String line = "PROBLEM client freeze in progress: " + ms + " ms in " + (now == LOGIC ? "logic" : "drawing") + ", gc "
                + (gcMillis() - gcAtPassStart) + " ms since the pass began, game thread " + t.getState() + " at " + sb;
            if (RspsTrace.ENABLED) {
                System.out.println("[avtrace " + System.currentTimeMillis() + "] " + line);
            }
            append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date()) + " " + line);
        }
    }

    /** Appends [line] to [LOG]; past [KEEP_BYTES] only the newer half is kept. Never throws (a full disk must not stop the game). */
    private static void append(String line) {
        try {
            java.io.File dir = LOG.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                return;
            }
            java.nio.file.Files.write(LOG.toPath(), (line + System.lineSeparator()).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            if (LOG.length() > KEEP_BYTES) {
                byte[] all = java.nio.file.Files.readAllBytes(LOG.toPath());
                int from = all.length / 2;
                while (from < all.length && all[from] != '\n') {
                    from++;
                }
                java.nio.file.Files.write(LOG.toPath(), java.util.Arrays.copyOfRange(all, Math.min(all.length, from + 1), all.length));
            }
        } catch (java.io.IOException | RuntimeException ignored) {
            // the log is a help for the next report, never a reason to stop the game
        }
    }

    /** The last [maxLines] lines of [LOG], or "" when there is none. */
    public static String tail(int maxLines) {
        try {
            if (!LOG.isFile()) {
                return "";
            }
            java.util.List<String> lines = java.nio.file.Files.readAllLines(LOG.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            return String.join("\n", lines.subList(Math.max(0, lines.size() - maxLines), lines.size()));
        } catch (java.io.IOException | RuntimeException e) {
            return "";
        }
    }

    private static long gcMillis() {
        long total = 0L;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0L, gc.getCollectionTime());
        }
        return total;
    }
}
