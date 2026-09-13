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
    }
}
