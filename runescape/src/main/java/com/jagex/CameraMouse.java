package com.jagex;

/**
 * RuneLite Camera plugin, its mouse listener (CameraPlugin.mousePressed / mouseReleased), owner 2026-10-11 "the whole
 * RuneLite plugin": "Right-click moves camera" turns a right press on an empty menu into a middle press (the camera drag
 * button), "Middle-button opens menu" turns a middle press into a right press; the release of a remapped press is
 * remapped the same way. The mouse monitors (AWT thread) ask {@link #press} / {@link #release}; the game thread keeps
 * the settings and the menu state up to date ({@link #update}, RspsCamera.tick - RuneLite's onClientTick).
 * This class lives in a package so the mouse monitors can reach it.
 */
public final class CameraMouse {

    /** The mouse monitors' click types (MouseMonitor CLICK_TYPE_LEFT / MIDDLE / RIGHT). */
    public static final int LEFT = 0x1;
    public static final int MIDDLE = 0x2;
    public static final int RIGHT = 0x4;

    private static volatile boolean rightClickMovesCamera;
    private static volatile boolean middleClickMenu;
    /** RuneLite menuHasEntries: the menu under the mouse has an entry that should open it (worked out each client tick). */
    private static volatile boolean menuHasEntries;
    /** RuneLite: varp OPTION_MOUSE == 1 - one mouse button, left and right always do the same. */
    private static volatile boolean oneButton;

    /** A remapped press waiting for its release (AWT thread only). */
    private static boolean rightClick;
    private static boolean middleClick;

    private CameraMouse() {
    }

    /** Game thread, every tick: the plugin's settings (false when the plugin is off) and the menu state. */
    public static void update(boolean rightMovesCamera, boolean middleOpensMenu, boolean hasEntries, boolean oneMouseButton) {
        rightClickMovesCamera = rightMovesCamera;
        middleClickMenu = middleOpensMenu;
        menuHasEntries = hasEntries;
        oneButton = oneMouseButton;
    }

    /** CameraPlugin.mousePressed: the click type the game sees for a press of [type]. */
    public static int press(int type) {
        if (type == RIGHT && rightClickMovesCamera) {
            // Only move the camera if there is nothing at the menu, or in one-button mode.
            if (!menuHasEntries || oneButton) {
                rightClick = true;
                return MIDDLE;
            }
        } else if (type == MIDDLE && middleClickMenu) {
            middleClick = true;
            return RIGHT;
        }
        return type;
    }

    /** CameraPlugin.mouseReleased: the release goes to the button the press was turned into. */
    public static int release(int type) {
        if (rightClick) {
            rightClick = false;
            type = (type & ~RIGHT) | MIDDLE;
        }
        if (middleClick) {
            middleClick = false;
            type = (type & ~MIDDLE) | RIGHT;
        }
        return type;
    }
}
