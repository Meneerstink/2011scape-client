package com.jagex.graphics.awt;

import com.jagex.game.runetek6.client.GameShell;
import java.awt.*;

/** Uses the monitor containing the client, including secondary monitors and AWT DPI coordinates. */
public final class DesktopDisplay {
    private DesktopDisplay() { }

    public static Rectangle bounds() {
        Component source = GameShell.loaderApplet != null ? GameShell.loaderApplet : GameShell.frame;
        GraphicsConfiguration configuration = source == null ? null : source.getGraphicsConfiguration();
        if (configuration == null) configuration = GraphicsEnvironment.getLocalGraphicsEnvironment()
            .getDefaultScreenDevice().getDefaultConfiguration();
        return new Rectangle(configuration.getBounds());
    }

    /**
     * The window the game sits in: the player client's own Frame around the loader applet (RS2Loader), else GameShell's.
     * GameShell.frame is null in the player client, so asking it alone missed the real window.
     */
    private static Frame window() {
        Component source = GameShell.loaderApplet != null ? GameShell.loaderApplet : GameShell.frame;
        while (source != null && !(source instanceof Frame)) source = source.getParent();
        return source instanceof Frame ? (Frame) source : null;
    }

    /** The player maximised the window: the client never sizes it then (night run 2026-10-11). */
    public static boolean maximised() {
        Frame frame = window();
        return frame != null && (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    }

    public static void restoreWindowSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        EventQueue.invokeLater(() -> {
            Frame frame = window();
            // A maximised window stays maximised: fitting it to the fixed game un-maximised and shrank it at once (owner A2).
            if (frame == null || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) return;
            Rectangle bounds = bounds();
            // The game area is the loader applet; the frame around it also holds the insets and the 78 Plugins strip /
            // sidebar (RspsSidebar), so the frame grows or shrinks by what the game area lacks.
            Component game = GameShell.loaderApplet;
            int w;
            int h;
            if (game != null && game.getWidth() > 0 && game.getHeight() > 0) {
                w = frame.getWidth() + width - game.getWidth();
                h = frame.getHeight() + height - game.getHeight();
            } else {
                Insets insets = frame.getInsets();
                w = width + insets.left + insets.right;
                h = height + insets.top + insets.bottom;
            }
            w = Math.min(bounds.width, w);
            h = Math.min(bounds.height, h);
            if (frame.getWidth() != w || frame.getHeight() != h) {
                System.out.println("[78] window sized for a " + width + "x" + height + " game (frame " + frame.getWidth() + "x" + frame.getHeight() + " -> " + w + "x" + h + ")");
                frame.setSize(w, h);
            }
        });
    }
}
