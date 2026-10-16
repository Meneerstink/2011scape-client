package com.jagex;

/**
 * Owner 2026-10-17 (BUGLIJST MAC, first version): what the client does differently on macOS.
 * - first start uses the Software renderer (the 2011 OpenGL library does not reliably attach to a modern macOS window);
 * - the intro scene does not switch to OpenGL;
 * - Ctrl+click is a right click (one-button mice and trackpads without two-finger click).
 * The cache has the macOS x86_64 native libraries; the Mac package ships an Intel (x86_64) Java, which Apple Silicon
 * runs through Rosetta 2.
 */
public final class RspsPlatform {

    public static final boolean MAC = System.getProperty("os.name", "").toLowerCase().startsWith("mac");

    private RspsPlatform() {
    }
}
