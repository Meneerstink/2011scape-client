package com.jagex.core.util;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * RSPS debug toolkit, client side (manual: C:/RSPS/TRACES.md): every client error the client reports ({@link JagException#sendTrace})
 * and every cache record that fails to decode ({@link DecodeFailures}) goes to C:/RSPS/bugs/client-crashes-&lt;date&gt;.log, always
 * (traces on or off), and as a "PROBLEM" line to stdout (the launcher's client-*.out.log) for tools/Show-Problems.ps1.
 */
public final class RspsBugLog {

    private static final String DIR = "C:/RSPS/bugs";

    private RspsBugLog() {
        /* empty */
    }

    public static synchronized void write(String what, Throwable cause) {
        try {
            String time = new SimpleDateFormat("HH:mm:ss.SSS").format(new Date());
            String head = time + " " + what + (cause != null ? ": " + cause : "");
            System.out.println("[rsps-bug] PROBLEM client " + head);
            File dir = new File(DIR);
            dir.mkdirs();
            File file = new File(dir, "client-crashes-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log");
            try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
                out.println(head);
                if (cause != null) {
                    StringWriter stack = new StringWriter();
                    cause.printStackTrace(new PrintWriter(stack));
                    String[] lines = stack.toString().split("\n");
                    for (int i = 1; i < Math.min(lines.length, 26); i++) {
                        out.println(lines[i].replace("\r", ""));
                    }
                }
                out.println();
            }
        } catch (Throwable ignored) {
            // the bug log must never break the client
        }
    }
}
