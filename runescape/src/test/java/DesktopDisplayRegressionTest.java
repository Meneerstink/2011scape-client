import com.jagex.graphics.awt.AwtFullscreenAdapter;
import com.jagex.graphics.awt.DesktopDisplay;
import java.awt.*;

/** Real desktop lifecycle check; does not need an account or switch monitor resolution. */
public final class DesktopDisplayRegressionTest {
    public static void main(String[] args) throws Exception {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        DisplayMode before = device.getDisplayMode();
        for (int i = 0; i < 5; i++) {
            AwtFullscreenAdapter adapter = new AwtFullscreenAdapter();
            Frame frame = new Frame("RSPS fullscreen regression");
            try {
                adapter.enter(frame,800,600,32,0);
                if (!frame.getBounds().equals(DesktopDisplay.bounds()) || !frame.isUndecorated() || !frame.isShowing())
                    throw new AssertionError("Fullscreen must occupy the monitor");
                if (!before.equals(device.getDisplayMode())) throw new AssertionError("Desktop mode changed");
                adapter.exit();
                if (frame.isDisplayable()) throw new AssertionError("Fullscreen frame leaked");
            } finally { frame.dispose(); }
        }
        System.out.println("Desktop fullscreen regression passed: 5 enter/exit cycles, native bounds, unchanged desktop mode, no leaked windows");
        // A2 round 5: a maximise the client did not make is named in the log (the client's only maximise is the remembered window).
        long now = System.currentTimeMillis();
        String external = DesktopDisplay.traceMaximise(Frame.NORMAL, Frame.MAXIMIZED_BOTH, now);
        if (external == null || !external.contains("by Windows")) throw new AssertionError("external maximise not named: " + external);
        String restored = DesktopDisplay.traceMaximise(Frame.MAXIMIZED_BOTH, Frame.NORMAL, now);
        if (restored == null || !restored.contains("restored")) throw new AssertionError("restore not named: " + restored);
        if (DesktopDisplay.traceMaximise(Frame.NORMAL, Frame.NORMAL, now) != null) throw new AssertionError("a non-change was traced");
        if (DesktopDisplay.traceMaximise(Frame.MAXIMIZED_BOTH, Frame.MAXIMIZED_BOTH | Frame.ICONIFIED, now) != null)
            throw new AssertionError("minimising a maximised window was traced as a maximise");
        System.out.println("Window maximise provenance passed: external maximise named, restore named, no false lines");
    }
}
