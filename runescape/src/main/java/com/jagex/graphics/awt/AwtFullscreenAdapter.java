package com.jagex.graphics.awt;

import java.awt.*;

/** Native-monitor fullscreen without exclusive mode switches or refresh-rate changes. */
public final class AwtFullscreenAdapter {
    private Frame fullscreenFrame;

    public AwtFullscreenAdapter() {
        if (GraphicsEnvironment.isHeadless()) throw new HeadlessException();
    }

    public void enter(Frame frame, int width, int height, int bits, int refreshRate) throws Exception {
        Rectangle bounds = DesktopDisplay.bounds();
        onEventThread(() -> {
            frame.setUndecorated(true);
            frame.setResizable(false);
            frame.setBackground(Color.BLACK);
            frame.enableInputMethods(false);
            frame.setBounds(bounds);
            frame.setVisible(true);
            frame.toFront();
        });
        fullscreenFrame = frame;
    }

    public int[] listmodes() {
        Rectangle bounds = DesktopDisplay.bounds();
        return new int[] { bounds.width, bounds.height, 32, 0 };
    }

    public void exit() throws Exception {
        Frame frame = fullscreenFrame;
        fullscreenFrame = null;
        if (frame != null) onEventThread(() -> { frame.setVisible(false); frame.dispose(); });
    }

    private static void onEventThread(Runnable action) throws Exception {
        if (EventQueue.isDispatchThread()) action.run();
        else EventQueue.invokeAndWait(action);
    }
}
