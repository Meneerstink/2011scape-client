import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Insets;
import java.awt.Panel;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * A2 round 3 (Keurmeester round 2 reason 1, 2026-10-17): opening the 78 Plugins sidebar must never change the player's game
 * area. RuneLite ClientUI (KEEP_GAME_SIZE, not maximised): the window grows by the sidebar's width, the game keeps its size,
 * and the window's minimum is the layout's minimum (ContainableFrame.revalidateMinimumSize: game minimum + sidebar). Closing
 * gives the width back. The 10-11 code counted the panel twice (open: minimum 765+30+242+242, so a 944 game jumped to 1007 and
 * the window could not be made narrower than 1279; close: minimum 765+30-242, so the game could be dragged to 570 px).
 *
 * The frame is built exactly like RS2Loader.start (Frame + the 765x503-minimum game panel + RspsSidebar.install, pack,
 * minimum = packed size) but never shown, so the test never touches the screen. Every game size of the class is checked:
 * widths 765..1600, heights 503 / 581 / 989, at the left of the screen and against its right edge (RuneLite grows to the left
 * there), several open/close cycles each.
 */
public final class StretchedSidebarRegressionTest {
    private static int assertions;
    private static final List<String> failures = new ArrayList<String>();

    public static void main(String[] args) throws Exception {
        Frame frame = new Frame("78 test");
        Panel game = new Panel();
        Dimension gameMin = new Dimension(765, 503);
        game.setMinimumSize(gameMin);
        game.setPreferredSize(gameMin);
        game.setSize(gameMin);
        frame.add(game);
        RspsSidebar.install(frame);
        RspsSidebar sidebar = RspsSidebar.instance;
        edt(() -> {
            frame.pack();
            frame.setMinimumSize(frame.getSize());
        });
        Insets insets = frame.getInsets();
        int borders = insets.left + insets.right;
        int panel = RspsSidebar.PANEL_WIDTH + RspsSidebar.SCROLLBAR_WIDTH;
        int closedMin = 765 + RspsSidebar.NAV_WIDTH + borders;
        int openMin = closedMin + panel;
        Rectangle screen = frame.getGraphicsConfiguration().getBounds();

        int cases = 0;
        for (int height : new int[] {503, 581, 989}) {
            for (int width = 765; width <= 1600; width += 7) {
                for (boolean rightEdge : new boolean[] {false, true}) {
                    int gameWidth = width;
                    int frameWidth = gameWidth + RspsSidebar.NAV_WIDTH + borders;
                    int frameHeight = height + insets.top + insets.bottom;
                    int x = rightEdge ? screen.x + screen.width - frameWidth : screen.x;
                    String at = gameWidth + "x" + height + (rightEdge ? " at the right edge" : "") + ": ";
                    edt(() -> {
                        frame.setBounds(x, screen.y, frameWidth, frameHeight);
                        frame.validate();
                    });
                    check(game.getWidth() == gameWidth, at + "closed, the game is " + game.getWidth() + " wide");
                    for (int cycle = 1; cycle <= 3; cycle++) {
                        edt(() -> {
                            sidebar.setExpanded(true);
                            frame.validate();
                        });
                        check(game.getWidth() == gameWidth, at + "cycle " + cycle + ": opening the sidebar keeps the game " + gameWidth + " wide (now " + game.getWidth() + ")");
                        check(frame.getWidth() == frameWidth + panel, at + "cycle " + cycle + ": the window grows by the panel " + panel + " (" + frameWidth + " -> " + frame.getWidth() + ")");
                        check(frame.getMinimumSize().width == openMin, at + "cycle " + cycle + ": minimum with the panel open = game 765 + strip + panel = " + openMin + " (is " + frame.getMinimumSize().width + ")");
                        if (rightEdge && frameWidth + panel <= screen.width) {
                            check(frame.getX() + frame.getWidth() <= screen.x + screen.width, at + "cycle " + cycle + ": grows to the left, stays on screen (x " + frame.getX() + ")");
                        }
                        edt(() -> {
                            sidebar.setExpanded(false);
                            frame.validate();
                        });
                        check(game.getWidth() == gameWidth, at + "cycle " + cycle + ": closing keeps the game " + gameWidth + " wide (now " + game.getWidth() + ")");
                        check(frame.getWidth() == frameWidth, at + "cycle " + cycle + ": closing gives the panel width back (" + frame.getWidth() + ", expected " + frameWidth + ")");
                        check(frame.getX() == x, at + "cycle " + cycle + ": closing puts the window back where it was (x " + frame.getX() + ", expected " + x + ")");
                        check(frame.getMinimumSize().width == closedMin, at + "cycle " + cycle + ": minimum with the panel closed = game 765 + strip = " + closedMin + " (is " + frame.getMinimumSize().width + ")");
                    }
                    cases++;
                }
            }
        }
        // The player drags the window narrower with the panel open: the game may go down to its own 765, not further, and a
        // window just above the minimum is allowed (the Keurmeester's 1216 / 1100 requests on the owner's 944 window).
        edt(() -> {
            frame.setBounds(screen.x, screen.y, 944 + RspsSidebar.NAV_WIDTH + borders, 581 + insets.top + insets.bottom);
            frame.validate();
            sidebar.setExpanded(true);
            frame.validate();
        });
        for (int request : new int[] {944, 900, 800, 770, 765}) {
            int frameWidth = request + RspsSidebar.NAV_WIDTH + panel + borders;
            check(frameWidth >= frame.getMinimumSize().width, "panel open: a " + request + " px game (window " + frameWidth + ") is allowed (minimum " + frame.getMinimumSize().width + ")");
        }
        check(764 + RspsSidebar.NAV_WIDTH + panel + borders < frame.getMinimumSize().width, "panel open: a 764 px game is below the minimum");
        edt(() -> sidebar.setExpanded(false));
        frame.dispose();

        for (int i = 0; i < Math.min(12, failures.size()); i++) {
            System.out.println("FAIL " + failures.get(i));
        }
        System.out.println("StretchedSidebarRegressionTest: " + cases + " window cases, " + assertions + " checks, "
            + (failures.isEmpty() ? "all passed" : failures.size() + " failed"));
        // The AWT threads would keep the JVM alive after a thrown error: the exit code carries the verdict.
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    private static void edt(Runnable work) {
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            failures.add(message);
        }
    }
}
