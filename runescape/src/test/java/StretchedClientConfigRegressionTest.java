import com.jagex.StretchedMode;
import com.jagex.game.runetek6.client.GameShell;

import java.awt.Canvas;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A2 round 4 (Keurmeester A2 round 3 reason 2, owner done-list 8 "alles wordt onthouden na uitloggen, client-herstart en dagen
 * offline"): RuneLite keeps StretchedModeConfig in the client's own config (settings.properties), so its login screen is
 * stretched right after a start and a choice survives a restart before any login. Here the values lived only in memory and
 * came back only with an account's push after login: a restarted client showed an unstretched login screen and lost Keep
 * aspect ratio / scaling set before the restart.
 *
 * Whole class: every Stretched Mode setting (enabled, Keep aspect ratio, Increased performance, Integer scaling, Resizable
 * scaling), set by the player (sidebar) and by a server push (login / Reset), survives a restart; the restarted client lays
 * the login screen out stretched before any login; a new account adopts the remembered values (cfgadopt); a broken file
 * never breaks the client; only Stretched keys are kept; the file is the client's own prefs folder (never the owner's when
 * rsps.preferencesDir is set) and nothing is written before the client read it at start (tests, tools).
 */
public final class StretchedClientConfigRegressionTest {
    private static int assertions;
    private static final List<String> failures = new ArrayList<String>();
    private static final java.io.PrintStream OUT = System.out;

    public static void main(String[] args) throws Exception {
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() {
            @Override
            public void write(int b) {
            }
        }));
        GameShell.canvas = new Canvas();
        StretchedMode.toolkitChanged(true);
        File dir = java.nio.file.Files.createTempDirectory("a2r4-prefs").toFile();
        System.setProperty("rsps.preferencesDir", dir.getAbsolutePath());
        File file = new File(dir, "stretchedmode.properties");

        // Nothing is written before the client read its settings at start (unit tests and tools never touch a prefs folder).
        RspsHudConfig.resetToDefaults();
        RspsHudConfig.put("stretchedmode.enabled", "true");
        check(!file.exists(), "no file written before the client's start-up read");

        String[][] runs = {
            // enabled, keepAspectRatio, increasedPerformance, integerScaling, scalingFactor
            {"true", "true", "false", "false", "10"},
            {"true", "false", "true", "true", "0"},
            {"false", "true", "true", "false", "97"},
            {"true", "false", "false", "false", "150"},
            {"false", "false", "false", "false", "50"},
        };
        String[] keys = {"stretchedmode.enabled", "stretchedmode.keepAspectRatio", "stretchedmode.increasedPerformance", "stretchedmode.integerScaling", "stretchedmode.scalingFactor"};
        for (boolean byServer : new boolean[] {false, true}) {
            for (String[] run : runs) {
                String what = (byServer ? "server push " : "sidebar ") + String.join("/", run);
                // client run 1: start, then the player (or the server's login / Reset push) sets the values
                restart();
                for (int i = 0; i < keys.length; i++) {
                    if (byServer) {
                        RspsHudConfig.handle("cfg", keys[i] + "=" + run[i]);
                    } else {
                        RspsHudConfig.put(keys[i], run[i]);
                    }
                }
                // client run 2: a fresh start, no login yet
                restart();
                for (int i = 0; i < keys.length; i++) {
                    check(run[i].equals(RspsHudConfig.get(keys[i])), what + ": after a restart " + keys[i] + " = " + run[i] + " (is '" + RspsHudConfig.get(keys[i]) + "')");
                }
                // the login screen (fixed 765x503 game in the remembered 944x581 window) before any login
                GameShell.frameWid = 944;
                GameShell.frameHei = 581;
                GameShell.canvasWid = 765;
                GameShell.canvasHei = 503;
                StretchedMode.layout(true);
                boolean on = "true".equals(run[0]);
                check(StretchedMode.enabled() == on, what + ": login screen after the restart has Stretched " + (on ? "on" : "off"));
                if (on) {
                    boolean keep = "true".equals(run[1]);
                    boolean integer = "true".equals(run[3]);
                    int[] expected = integer ? new int[] {765, 503} : keep ? new int[] {883, 581} : new int[] {944, 581};
                    check(StretchedMode.stretchedWidth() == expected[0] && StretchedMode.stretchedHeight() == expected[1],
                        what + ": login screen drawn " + expected[0] + "x" + expected[1] + " (is " + StretchedMode.stretchedWidth() + "x" + StretchedMode.stretchedHeight() + ")");
                }
                // a new account (never set Stretched) logs in: the server asks for the client's values (cfgadopt)
                for (int i = 0; i < keys.length; i++) {
                    RspsHud.drainForTest();
                    RspsHudConfig.adopt(keys[i]);
                    String sent = RspsHud.drainForTest();
                    check(("hud set " + keys[i] + " " + run[i]).equals(sent), what + ": a new account adopts the remembered " + keys[i] + " (sent '" + sent + "')");
                }
            }
        }

        // Only Stretched Mode keys go into the client file (the rest stays with the account on the server).
        restart();
        RspsHudConfig.put("chatbox.transparent", "true");
        RspsHudConfig.put("stretchedmode.scalingFactor", "33");
        String text = file.isFile() ? new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8") : "";
        check(text.contains("stretchedmode.scalingFactor=33"), "file holds the Stretched values (" + text.replace('\n', ' ') + ")");
        check(!text.contains("chatbox"), "file holds no other plugin's settings");

        // A broken / hand-edited file never breaks the client: bad values are skipped, the rest is used.
        FileWriter writer = new FileWriter(file);
        writer.write("stretchedmode.enabled=true\nstretchedmode.scalingFactor=abc\nstretchedmode.keepAspectRatio=maybe\nchatbox.enabled=false\ngarbage line\n");
        writer.close();
        restart();
        check("true".equals(RspsHudConfig.get("stretchedmode.enabled")), "broken file: the good line is used");
        check("50".equals(RspsHudConfig.get("stretchedmode.scalingFactor")), "broken file: a bad scaling value keeps the default 50 (is " + RspsHudConfig.get("stretchedmode.scalingFactor") + ")");
        check("false".equals(RspsHudConfig.get("stretchedmode.keepAspectRatio")), "broken file: a bad boolean keeps the default");
        check(!"false".equals(RspsHudConfig.get("chatbox.enabled")), "broken file: other keys are ignored");
        writer = new FileWriter(file);
        writer.write("stretchedmode.scalingFactor=-5\n");
        writer.close();
        restart();
        check("50".equals(RspsHudConfig.get("stretchedmode.scalingFactor")), "a negative scaling keeps the default (is " + RspsHudConfig.get("stretchedmode.scalingFactor") + ")");
        file.delete();
        restart();
        check("false".equals(RspsHudConfig.get("stretchedmode.enabled")), "no file = RuneLite defaults (Stretched off)");

        System.setOut(OUT);
        System.out.println("StretchedClientConfigRegressionTest: " + assertions + " checks");
        if (failures.isEmpty()) {
            System.out.println("all passed");
            return;
        }
        for (String failure : failures.subList(0, Math.min(60, failures.size()))) {
            System.out.println("FAIL " + failure);
        }
        System.out.println(failures.size() + " failed");
        System.exit(1);
    }

    /** A client start: memory as in a new JVM, then the loader's start-up read and the game loop's first apply. */
    private static void restart() throws Exception {
        RspsHudConfig.resetToDefaults();
        StretchedMode.configure(false, false, false, false, 50);
        Method load = null;
        try {
            load = RspsHudConfig.class.getDeclaredMethod("loadClientSettings");
        } catch (NoSuchMethodException missing) {
            // the old client: nothing reads Stretched Mode at start
        }
        assertions++;
        if (load == null) {
            if (!failures.contains("the client never reads its own Stretched Mode settings at start (RspsHudConfig.loadClientSettings)")) {
                failures.add("the client never reads its own Stretched Mode settings at start (RspsHudConfig.loadClientSettings)");
            }
        } else {
            load.setAccessible(true);
            load.invoke(null);
        }
        RspsHudConfig.applyStretchedMode();
    }

    private static void check(boolean ok, String what) {
        assertions++;
        if (!ok) {
            failures.add(what);
        }
    }
}
