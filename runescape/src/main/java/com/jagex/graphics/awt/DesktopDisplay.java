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

    public static void restoreWindowSize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        EventQueue.invokeLater(() -> {
            Component source = GameShell.loaderApplet != null ? GameShell.loaderApplet : GameShell.frame;
            while (source != null && !(source instanceof Frame)) source = source.getParent();
            if (!(source instanceof Frame)) return;
            Frame frame = (Frame) source;
            Rectangle bounds = bounds();
            Insets insets = frame.getInsets();
            frame.setSize(Math.min(bounds.width, width + insets.left + insets.right),
                Math.min(bounds.height, height + insets.top + insets.bottom));
        });
    }
}
