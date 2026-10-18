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

    /** The listeners are on this frame (a new frame after fullscreen gets them again). */
    private static Frame watched;
    private static javax.swing.Timer boundsTimer;

    /**
     * RuneLite ClientUI windowBoundsChanged (owner 2026-10-11 "net als de officiele versie"): every resize and maximise of the
     * window runs [remember] 250 ms later (RuneLite defers it so a maximise is not saved as a size).
     */
    public static void watchWindow(Runnable remember) {
        Frame frame = window();
        if (frame == null || frame == watched) return;
        watched = frame;
        if (boundsTimer == null) {
            boundsTimer = new javax.swing.Timer(250, e -> remember.run());
            boundsTimer.setRepeats(false);
        }
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                boundsTimer.restart();
            }
        });
        frame.addWindowStateListener(e -> {
            traceMaximise(e.getOldState(), e.getNewState(), System.currentTimeMillis());
            boundsTimer.restart();
        });
    }

    /** When the client itself last maximised the window (restoreIfGameSized, the remembered window); 0 = never. */
    private static volatile long clientMaximisedAt;

    /**
     * A2 round 5 (Keurmeester round 4 reason 2: "the window MAXIMISED BY ITSELF between two intro clicks"): the client has
     * exactly one call that maximises the window - [restoreIfGameSized] for a player who left it maximised (RuneLite
     * rememberScreenBounds), and it logs "remembered window: maximised" first. Every other maximise comes from Windows (title-bar
     * double-click, Win+Up, dragging to the top edge, another program's ShowWindow). Say which one it was, so a maximise in a log
     * is never unexplained again.
     */
    public static String traceMaximise(int oldState, int newState, long now) {
        boolean was = (oldState & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        boolean is = (newState & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
        String line = null;
        if (is && !was) {
            boolean byClient = clientMaximisedAt != 0 && now - clientMaximisedAt < 3000;
            line = byClient ? "[78] window maximised by the client (the player's remembered maximised window)"
                : "[78] window maximised by Windows (title bar / Win+Up / snap / another program) - the client did not maximise it";
        } else if (was && !is) {
            line = "[78] window restored from maximised";
        }
        if (line != null) System.out.println(line);
        return line;
    }

    /** The game area's size (the loader applet in the player client), or null without a window. */
    public static Dimension gameArea() {
        Frame frame = window();
        if (frame == null) return null;
        Component game = GameShell.loaderApplet != null ? GameShell.loaderApplet : frame;
        return game.getSize();
    }

    /**
     * RuneLite rememberScreenBounds: a window whose game area is the fitted fixed game ([gameWidth] x [gameHeight]) gets back
     * the player's remembered window - maximised, or [width] x [height] of game area. A window of any other size was sized by
     * the player and stays as it is; the game is never maximised or resized for a setting.
     */
    public static void restoreIfGameSized(boolean always, int gameWidth, int gameHeight, boolean maximised, int width, int height) {
        EventQueue.invokeLater(() -> {
            Frame frame = window();
            if (frame == null || GameShell.fsframe != null || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) return;
            Component game = GameShell.loaderApplet != null ? GameShell.loaderApplet : frame;
            if (!always && (game.getWidth() > gameWidth + 2 || game.getHeight() > gameHeight + 2)) return;
            boolean sized = width >= 765 && height >= 503 && (width != game.getWidth() || height != game.getHeight());
            System.out.println("[78] remembered window: " + (maximised ? "maximised" : sized ? width + "x" + height : "none") + " (game area was " + game.getWidth() + "x" + game.getHeight() + ")");
            if (sized) {
                // The normal size first, so a maximised window restores to it (RuneLite keeps the bounds under the maximised state).
                Rectangle screen = bounds();
                int w = Math.min(screen.width, frame.getWidth() + width - game.getWidth());
                int h = Math.min(screen.height, frame.getHeight() + height - game.getHeight());
                frame.setBounds(screen.x + Math.max(0, (screen.width - w) / 2), screen.y + Math.max(0, (screen.height - h) / 2), w, h);
            }
            if (maximised) {
                clientMaximisedAt = System.currentTimeMillis();
                frame.setExtendedState(frame.getExtendedState() | Frame.MAXIMIZED_BOTH);
            }
        });
    }

    // ---- A2 round 7 part 2: the game area a window fit is about to give (nakijker A2 round 7 FOUTEN 1) -----------------------
    //
    // A window-mode switch draws the new mode's first frame offscreen at the size the window is about to have, before the window
    // changes (InterfaceManager.prepareOffscreenCanvas). These say which size the fits below will give; the frame keeps its own
    // borders and 78 strip around the game area, so the new game area = the new frame minus what the frame holds now. A wrong
    // guess only scales that one offscreen frame to the real size; the next frame is laid out for the real window.

    /** What the frame holds around the game area now (insets + the 78 Plugins strip / sidebar), or null without a window. */
    private static Dimension around(Frame frame) {
        Component game = GameShell.loaderApplet;
        if (game != null && game.getWidth() > 0 && game.getHeight() > 0) {
            return new Dimension(frame.getWidth() - game.getWidth(), frame.getHeight() - game.getHeight());
        }
        Insets insets = frame.getInsets();
        return new Dimension(insets.left + insets.right, insets.top + insets.bottom);
    }

    /** The game area {@link #restoreWindowSize}([width], [height]) leaves (the current one when it does nothing). */
    public static Dimension fittedGameArea(int width, int height) {
        Frame frame = window();
        Dimension game = gameArea();
        if (frame == null || game == null || width <= 0 || height <= 0 || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH) {
            return game;
        }
        Rectangle bounds = bounds();
        Dimension around = around(frame);
        int w = Math.min(bounds.width, width + around.width);
        int h = Math.min(bounds.height, height + around.height);
        return new Dimension(w - around.width, h - around.height);
    }

    /** The game area {@link #restoreIfGameSized}(false, ...) leaves: the remembered window, maximised = the screen's work area. */
    public static Dimension restoredGameArea(int gameWidth, int gameHeight, boolean maximised, int width, int height) {
        Frame frame = window();
        Dimension game = gameArea();
        if (frame == null || game == null || GameShell.fsframe != null || (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH
            || game.width > gameWidth + 2 || game.height > gameHeight + 2) {
            return game;
        }
        Dimension around = around(frame);
        if (maximised) {
            // Windows lays a maximised frame over the work area with its resize border (insets.left thick) outside the left, right,
            // top and bottom edge; the caption stays on screen.
            GraphicsConfiguration configuration = frame.getGraphicsConfiguration();
            Rectangle work = configuration == null ? bounds() : configuration.getBounds();
            if (configuration != null) {
                Insets screen = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(configuration);
                work = new Rectangle(work.x + screen.left, work.y + screen.top, work.width - screen.left - screen.right, work.height - screen.top - screen.bottom);
            }
            Insets insets = frame.getInsets();
            int w = work.width + insets.left + insets.right;
            int h = work.height + insets.left + insets.bottom;
            return new Dimension(w - around.width, h - around.height);
        }
        boolean sized = width >= 765 && height >= 503 && (width != game.width || height != game.height);
        if (!sized) {
            return game;
        }
        Rectangle screen = bounds();
        int w = Math.min(screen.width, frame.getWidth() + width - game.width);
        int h = Math.min(screen.height, frame.getHeight() + height - game.height);
        return new Dimension(w - around.width, h - around.height);
    }

    /**
     * Waits (at most [maxMs]) until the window fits queued on the event thread have run and the game area has left [before]
     * (a maximise reaches the frame a moment after setExtendedState). Never on the event thread itself.
     */
    public static void awaitWindow(Dimension before, Dimension expected, long maxMs) {
        if (EventQueue.isDispatchThread()) return;
        long end = System.currentTimeMillis() + maxMs;
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        EventQueue.invokeLater(done::countDown);
        try {
            done.await(maxMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            // The frame's layout (the game area) follows its resize on the event thread.
            while (before != null && expected != null && !expected.equals(before) && System.currentTimeMillis() < end) {
                Dimension now = gameArea();
                if (now == null || !now.equals(before)) {
                    java.util.concurrent.CountDownLatch laid = new java.util.concurrent.CountDownLatch(1);
                    EventQueue.invokeLater(laid::countDown);
                    laid.await(Math.max(1, end - System.currentTimeMillis()), java.util.concurrent.TimeUnit.MILLISECONDS);
                    return;
                }
                Thread.sleep(2);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
