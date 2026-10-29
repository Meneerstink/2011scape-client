import com.jagex.StretchedMode;
import com.jagex.game.runetek6.client.GameShell;

import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import java.awt.Canvas;
import java.util.ArrayList;
import java.util.List;

/**
 * Resizable scaling is RuneLite's StretchedModeMixin: every step up to the window's last changing step makes the game visibly
 * smaller (so the UI bigger), every step above it draws the same picture. N188 (owner 2026-10-22, "exact RuneLite - zelfde
 * bereik, standaard 50%", replacing A2 round 2 / VRAGEN 23's window maximum): the sidebar spinner is RuneLite's - 0 ..
 * Integer.MAX_VALUE, the stored value shown as it is. In fixed mode (N187/N188, owner 2026-10-25: "Resizable scaling moet OOK in
 * fixed werken") the stretched game is 765x503 times the factor, never larger than the window.
 * A window change never rewrites the player's stored value.
 *
 * The oracle is the layout itself: for every window the test walks p = 0..400 through StretchedMode.layout and takes the first
 * p whose game size equals the size of every larger p. It then checks the real 78 Plugins sidebar (headless Swing) against it.
 * Every failure is collected, so a red run lists the whole class, not only the first window.
 */
public final class StretchedScalingRegressionTest {
    private static int assertions;
    private static final List<String> failures = new ArrayList<String>();
    private static final String KEY = "stretchedmode.scalingFactor";

    /** The real stdout: the client's layout log line per change is silenced while the sweep runs (thousands of layouts). */
    private static final java.io.PrintStream OUT = System.out;
    private static final java.util.Map<String, Integer> MAX = new java.util.HashMap<String, Integer>();

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
            }
        }));
        GameShell.canvas = new Canvas();
        StretchedMode.toolkitChanged(true);

        // 1. The formula (RuneLite getRealDimensions, 765x503 floor since A2 round 3) over every window from the smallest frame to the
        //    laptop screen: below the maximum every step changes the game, above it nothing does.
        List<int[]> windows = new ArrayList<int[]>();
        for (int w = 765; w <= 1920; w += 23) {
            for (int h = 503; h <= 1080; h += 17) {
                windows.add(new int[] {w, h});
            }
        }
        int[][] named = {{944, 581}, {1007, 581}, {854, 581}, {948, 709}, {1648, 989}, {1890, 989}, {1690, 1080}, {1920, 1080}, {1428, 900}, {765, 503}, {765, 520}, {900, 510}};
        for (int[] w : named) {
            windows.add(w);
        }
        int checkedWindows = 0;
        for (int[] w : windows) {
            int max = oracleMax(w[0], w[1]);
            int[] previous = real(w[0], w[1], 0);
            for (int p = 1; p <= max; p++) {
                int[] now = real(w[0], w[1], p);
                check(now[0] < previous[0] || now[1] < previous[1], w, "step " + p + "% makes the game smaller (UI bigger): " + size(previous) + " -> " + size(now));
                previous = now;
            }
            int[] atMax = real(w[0], w[1], max);
            for (int p = max + 1; p <= 400; p += 7) {
                check(java.util.Arrays.equals(real(w[0], w[1], p), atMax), w, p + "% above the maximum " + max + "% changes nothing");
            }
            checkedWindows++;
        }

        // 1b. A2 round 3 (Keurmeester round 2 reason 2; owner 10-17 VRAGEN 23 C "precies RuneLite", "nooit kleiner dan 765x503"):
        //     every resizable size equals OpenOSRS StretchedModeMixin.getRealDimensions (ported verbatim below as an
        //     independent oracle) - the 10-11 520 floor is gone. The one named difference: a truncated mixin size that falls a
        //     pixel under 765x503 by float rounding is held on 765x503 (the 667 game never runs below its own minimum).
        for (int[] w : windows) {
            for (int p = 0; p <= 400; p = p < 120 ? p + 1 : p + 7) {
                int[] mixin = runeLiteRealDimensions(w[0], w[1], p);
                int[] expected = {Math.max(765, mixin[0]), Math.max(503, mixin[1])};
                int[] ours = real(w[0], w[1], p);
                check(java.util.Arrays.equals(ours, expected), w, "RuneLite getRealDimensions at " + p + "%: " + size(expected) + " (mixin " + size(mixin) + "), ours " + size(ours));
            }
        }
        // The owner's window 944x581 by name (RuneLite numbers worked out by hand from the mixin): 15 % = 820x505, from 16 %
        // the game is 817x503 - so the spinner runs 0..16.
        check(java.util.Arrays.equals(real(944, 581, 15), new int[] {820, 505}), new int[] {944, 581}, "15% = 820x505 (" + size(real(944, 581, 15)) + ")");
        check(java.util.Arrays.equals(real(944, 581, 16), new int[] {817, 503}), new int[] {944, 581}, "16% = 817x503 (" + size(real(944, 581, 16)) + ")");
        check(oracleMax(944, 581) == 16, new int[] {944, 581}, "the owner's window: maximum 16% (" + oracleMax(944, 581) + ")");

        // 2. The sidebar spinner (the control the player uses).
        RspsHudConfig.resetToDefaults();
        RspsHud.reset();
        RspsSidebar sidebar = new RspsSidebar(null);
        RspsSidebar.instance = sidebar;
        RspsHudConfig.handle("cfg", "stretchedmode.enabled=true;" + KEY + "=50");
        window(944, 581, false);
        ((javax.swing.JButton) sidebar.find("config.stretchedmode")).doClick();
        JSpinner spinner = (JSpinner) sidebar.find(KEY);
        if (spinner == null) {
            failures.add("the Resizable scaling spinner is not on the Stretched Mode page");
            report(checkedWindows);
            return;
        }
        RspsHud.drainForTest();

        // 2a. N188 (owner 2026-10-22: "exact RuneLite - zelfde bereik, standaard 50%", replacing VRAGEN 23's window maximum):
        //     RuneLite's spinner in every window - 0 .. Integer.MAX_VALUE, the stored value shown as it is, nothing sent.
        int[] stored = {0, 5, 10, 11, 12, 50, 90, 150};
        for (int[] w : windows) {
            for (int s : stored) {
                RspsHudConfig.put(KEY, Integer.toString(s));
                window(w[0], w[1], false);
                edt(sidebar::refresh);
                SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
                check(((Number) model.getMaximum()).intValue() == Integer.MAX_VALUE, w, "stored " + s + "%: spinner maximum " + model.getMaximum() + " = RuneLite's Integer.MAX_VALUE");
                check(((Number) model.getMinimum()).intValue() == 0, w, "stored " + s + "%: spinner minimum " + model.getMinimum() + " = 0");
                check(((Number) spinner.getValue()).intValue() == s, w, "stored " + s + "%: spinner shows " + spinner.getValue());
                check(RspsHudConfig.integer(KEY) == s, w, "stored " + s + "%: a window change keeps the player's value (now " + RspsHudConfig.integer(KEY) + ")");
                String sent = RspsHud.drainForTest();
                check(sent == null || sent.isEmpty(), w, "stored " + s + "%: refreshing the spinner sends nothing to the server (sent '" + sent + "')");
            }
        }

        // 2b. The owner's window 944x581: the up arrow keeps going past the last changing step (RuneLite), every step is saved,
        //     and a typed 150 stays 150.
        int ownerMax = oracleMax(944, 581);
        RspsHudConfig.put(KEY, "0");
        window(944, 581, false);
        edt(sidebar::refresh);
        RspsHud.drainForTest();
        for (int p = 1; p <= ownerMax + 5; p++) {
            Object next = spinner.getNextValue();
            check(next != null && ((Number) next).intValue() == p, new int[] {944, 581}, "up arrow step " + p + " gives " + next);
            if (next != null) {
                edt(() -> spinner.setValue(next));
                check(("hud set " + KEY + " " + p).equals(RspsHud.drainForTest()), new int[] {944, 581}, "up arrow step " + p + " is saved");
            }
        }
        javax.swing.JFormattedTextField field = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
        edt(() -> {
            field.setText("150%");
            try {
                field.commitEdit();
            } catch (java.text.ParseException e) {
                throw new AssertionError(e);
            }
        });
        check(((Number) spinner.getValue()).intValue() == 150, new int[] {944, 581}, "typed 150% stays 150% (shows " + spinner.getValue() + ")");
        check(("hud set " + KEY + " 150").equals(RspsHud.drainForTest()), new int[] {944, 581}, "typed 150% is saved");

        // 2c. The window grows and shrinks: the spinner keeps the stored 50 % and RuneLite's range.
        RspsHudConfig.put(KEY, "50");
        edt(sidebar::refresh);
        int[][] path = {{944, 581}, {1648, 989}, {944, 581}, {1890, 989}, {1690, 1080}, {765, 503}, {944, 581}};
        for (int[] w : path) {
            window(w[0], w[1], false);
            SwingUtilities.invokeAndWait(() -> { });
            SwingUtilities.invokeAndWait(() -> { });
            SpinnerNumberModel model = (SpinnerNumberModel) spinner.getModel();
            check(((Number) model.getMaximum()).intValue() == Integer.MAX_VALUE, w, "resize: RuneLite's range (" + model.getMaximum() + ")");
            check(((Number) spinner.getValue()).intValue() == 50, w, "resize: the spinner shows " + spinner.getValue() + ", expected the stored 50");
            check(RspsHudConfig.integer(KEY) == 50, w, "resize: the stored 50% is kept");
        }

        // 2d. Fixed mode, Stretched on (N187/N188): the 765x503 game is stretched to 765x503 times the factor, never beyond the window,
        //     so every step changes the picture until it fills the window; the spinner stays usable with the stored value.
        for (int[] w : new int[][] {{944, 581}, {1648, 989}, {1920, 1080}}) {
            int max = oracleMax(w[0], w[1]);
            int lastWidth = -1;
            for (int s : new int[] {0, 5, 10, 20, max / 2, max, 50, 150}) {
                RspsHudConfig.put(KEY, Integer.toString(s));
                window(w[0], w[1], true);
                int wantW = Math.min(w[0], (int) Math.round(765 * (1 + s / 100D)));
                int wantH = Math.min(w[1], (int) Math.round(503 * (1 + s / 100D)));
                check(GameShell.canvasWid == 765 && GameShell.canvasHei == 503 && StretchedMode.canvasWidth() == wantW && StretchedMode.canvasHeight() == wantH,
                    w, "fixed " + s + "%: 765x503 stretched to " + wantW + "x" + wantH + " (" + GameShell.canvasWid + "x" + GameShell.canvasHei + " -> " + StretchedMode.canvasWidth() + "x" + StretchedMode.canvasHeight() + ")");
                boolean rising = s == 0 || s == 5 || s == 10 || s == 20;
                if (rising && s > 0 && wantW < w[0]) {
                    check(StretchedMode.canvasWidth() > lastWidth, w, "fixed " + s + "%: a step up makes the picture bigger");
                }
                if (rising) {
                    lastWidth = StretchedMode.canvasWidth();
                }
                edt(sidebar::refresh);
                check(spinner.isShowing() || spinner.isVisible(), w, "fixed: the spinner is still there");
                check(((Number) spinner.getValue()).intValue() == s, w, "fixed " + s + "%: shows the stored value (" + spinner.getValue() + ")");
            }
        }
        RspsHudConfig.put(KEY, "50");
        window(1648, 989, false);
        RspsHudConfig.handle("cfg", "stretchedmode.enabled=false");
        window(765, 503, true);
        edt(sidebar::refresh);
        check(((Number) spinner.getValue()).intValue() == 50, new int[] {765, 503}, "fixed without stretching: shows the stored 50% (" + spinner.getValue() + ")");

        // 3. N93 / A2 round 3: a new account's login never switches Stretched Mode off (RuneLite: the client's config). The
        //    player switches it on at the login screen with his own values; the login push of an account that never set them
        //    leaves them out (server ClientHudConfig.push) and asks for them ("cfgadopt"): the client keeps them and sends each
        //    as a `hud set`, so the account remembers them.
        int[] login = {1648, 989};
        RspsHudConfig.resetToDefaults();
        RspsHud.drainForTest();
        RspsHudConfig.put("stretchedmode.enabled", "true");
        RspsHudConfig.put("stretchedmode.scalingFactor", "30");
        RspsHudConfig.put("stretchedmode.keepAspectRatio", "true");
        RspsHud.handle("cfg|boosts.enabled=true;opponent.enabled=true");
        check(StretchedMode.enabled(), login, "a login push without Stretched Mode keys keeps the player's Stretched Mode on");
        String[][] own = {{"stretchedmode.enabled", "true"}, {"stretchedmode.scalingFactor", "30"}, {"stretchedmode.keepAspectRatio", "true"},
            {"stretchedmode.integerScaling", "false"}, {"stretchedmode.increasedPerformance", "false"}};
        for (String[] kv : own) {
            RspsHud.drainForTest();
            RspsHud.handle("cfgadopt|" + kv[0]);
            String answer = RspsHud.drainForTest();
            check(("hud set " + kv[0] + " " + kv[1]).equals(answer), login, "cfgadopt " + kv[0] + ": the client hands its own value " + kv[1] + " to the account (sent '" + answer + "')");
        }
        RspsHud.handle("cfgadopt|boosts.enabled");
        check(RspsHud.drainForTest() == null, login, "cfgadopt takes only Stretched Mode keys");
        check(StretchedMode.enabled(), login, "after the adopt the player's Stretched Mode is still on");

        report(checkedWindows);
    }

    /** The resizable game size for a window at [percent], through the client's own layout. */
    private static int[] real(int width, int height, int percent) {
        StretchedMode.configure(true, false, false, false, percent);
        GameShell.frameWid = width;
        GameShell.frameHei = height;
        GameShell.canvasWid = width;
        GameShell.canvasHei = height;
        StretchedMode.layout(false);
        return new int[] {GameShell.canvasWid, GameShell.canvasHei};
    }

    /**
     * OpenOSRS StretchedModeMixin.getRealDimensions, resizable branch, verbatim (github.com/open-osrs/runelite
     * runelite-mixins/.../StretchedModeMixin.java l.92-130): scalingFactor = 1 + factor / 100D (setScalingFactor l.78-81).
     */
    private static int[] runeLiteRealDimensions(int parentWidth, int parentHeight, int factor) {
        double scalingFactor = 1 + (factor / 100D);
        int newWidth = (int) (parentWidth / scalingFactor);
        int newHeight = (int) (parentHeight / scalingFactor);
        if (newWidth < 765 || newHeight < 503) {
            double scalingFactorW = (double) parentWidth / 765;
            double scalingFactorH = (double) parentHeight / 503;
            double clamp = Math.min(scalingFactorW, scalingFactorH);
            newWidth = (int) (parentWidth / clamp);
            newHeight = (int) (parentHeight / clamp);
        }
        return new int[] {newWidth, newHeight};
    }

    /** The first percentage whose game size equals that of every larger percentage (the spinner's maximum). */
    private static int oracleMax(int width, int height) {
        Integer known = MAX.get(width + "x" + height);
        if (known != null) {
            return known;
        }
        int max = sweepMax(width, height);
        MAX.put(width + "x" + height, max);
        return max;
    }

    private static int sweepMax(int width, int height) {
        int[] floor = real(width, height, 100000);
        for (int p = 0; p <= 100000; p++) {
            if (java.util.Arrays.equals(real(width, height, p), floor)) {
                return p;
            }
        }
        return 100000;
    }

    /** Lays the client out for a window with the sidebar's current settings (as the resize path does). */
    private static void window(int width, int height, boolean fixed) {
        RspsHudConfig.applyStretchedMode();
        GameShell.frameWid = width;
        GameShell.frameHei = height;
        GameShell.canvasWid = fixed ? 765 : width;
        GameShell.canvasHei = fixed ? 503 : height;
        GameShell.leftMargin = 0;
        GameShell.topMargin = 0;
        StretchedMode.layout(fixed);
    }

    /** Swing work runs on the event thread, as in the client (the layout's own refresh is queued there too). */
    private static void edt(Runnable work) {
        try {
            SwingUtilities.invokeAndWait(work);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static String size(int[] s) {
        return s[0] + "x" + s[1];
    }

    private static void check(boolean condition, int[] window, String message) {
        assertions++;
        if (!condition) {
            failures.add(window[0] + "x" + window[1] + ": " + message);
        }
    }

    private static void report(int windows) {
        // Grouped by kind (numbers taken out), three examples each, so a red run shows every kind of failure in the class.
        java.util.Map<String, List<String>> kinds = new java.util.LinkedHashMap<String, List<String>>();
        for (String failure : failures) {
            String kind = failure.substring(failure.indexOf(": ") + 2).replaceAll("[0-9]+", "N");
            List<String> list = kinds.get(kind);
            if (list == null) {
                kinds.put(kind, list = new ArrayList<String>());
            }
            list.add(failure);
        }
        for (java.util.Map.Entry<String, List<String>> kind : kinds.entrySet()) {
            OUT.println("FAIL x" + kind.getValue().size() + "  " + kind.getKey());
            for (int i = 0; i < Math.min(3, kind.getValue().size()); i++) {
                OUT.println("     e.g. " + kind.getValue().get(i));
            }
        }
        OUT.println("StretchedScalingRegressionTest: " + windows + " windows, " + assertions + " checks, "
            + (failures.isEmpty() ? "all passed" : failures.size() + " failed"));
        if (!failures.isEmpty()) {
            throw new AssertionError(failures.size() + " Resizable scaling checks failed (first: " + failures.get(0) + ")");
        }
    }
}
