import rs2.client.event.keyboard.KeyLog;
import rs2.client.event.keyboard.KeyboardMonitor;
import rs2.client.event.mouse.MouseLog;
import rs2.client.event.mouse.MouseMonitor;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Headless checks of the RSPS information overlays (RspsHud*): payload decoding, value format, settings, wildcard
 * matching and the Ground Items label lifecycle over the client's own ground-item state (no ghost labels after a pickup,
 * despawn or zone reset). Drawing itself needs a toolkit and is checked in the client.
 */
public final class RspsHudRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        values();
        settings();
        countdowns();
        groundItems();
        layout();
        settingsPanel();
        crowdedGround();
        System.out.println("RSPS HUD regression checks passed: " + assertions);
    }

    private static void values() {
        check(RspsHud.formatValue(2_400_000L).equals("2.4M"), "2,400,000 -> 2.4M (owner example)");
        check(RspsHud.formatValue(18_400L).equals("18.4K"), "18,400 -> 18.4K (owner example)");
        check(RspsHud.formatValue(4_800L).equals("4.8K"), "4,800 -> 4.8K (owner example)");
        check(RspsHud.formatValue(999L).equals("999"), "below 1,000 the exact number");
        check(RspsHud.formatValue(150_000L).equals("150K"), "100K and up without a decimal");
        check(RspsHud.formatValue(2_000_000_000L).equals("2B"), "billions");
    }

    private static void settings() {
        RspsHud.handle("cfg|1011001|10,20,30,40|ffffff,1,2,3,4,aa00ff");
        check(RspsHudConfig.boosts && !RspsHudConfig.boostsAll && RspsHudConfig.opponent && RspsHudConfig.groundItems, "switches 1-4");
        check(!RspsHudConfig.despawn && !RspsHudConfig.value && RspsHudConfig.timers, "switches 5-7");
        check(RspsHudConfig.valueColour(35L) == 0xFF000003, "35 gp reaches the third tier (30)");
        check(RspsHudConfig.valueColour(5L) == 0xFFFFFFFF, "below the first tier: default colour");
        RspsHud.handle("hl|clear");
        RspsHud.handle("hl|add|dragon*,shark");
        check(RspsHudConfig.isHighlighted("Dragon dagger(p++)"), "dragon* matches Dragon dagger(p++), any case");
        check(RspsHudConfig.isHighlighted("Shark") && !RspsHudConfig.isHighlighted("Sharks"), "a plain name matches the whole name only");
        check(!RspsHudConfig.isHighlighted("Abyssal whip"), "an unlisted item is not highlighted");
        RspsHud.handle("hide|clear");
        RspsHud.handle("hide|add|*bones");
        check(RspsHudConfig.isHidden("Dragon bones") && !RspsHudConfig.isHidden("Bones of x"), "*bones matches names ending in bones");
        RspsHud.handle("hl|clear");
        check(!RspsHudConfig.isHighlighted("Shark"), "clear empties the list");
        RspsHud.handle("cfg|1011111|20000,100000,1000000,10000000|ffffff,66b2ff,99ff99,ff9600,ff66b2,aa00ff");
        RspsHud.handle("garbage|x|y");
        RspsHud.handle("tmr|not-a-number");
        check(true, "malformed payloads are ignored");
    }

    private static void countdowns() {
        check(RspsHud.endTime(0) == -1L && RspsHud.endTime(-1) == -1L, "no countdown for 0 / -1 ticks");
        long end = RspsHud.endTime(100);
        long expected = RspsHud.now() + 100 * RspsHud.TICK_MS;
        check(Math.abs(end - expected) < 50L, "100 ticks = 60 seconds");
        check(RspsHud.countdown(RspsHud.now() + 60_000L).equals("1:00"), "60 s shows 1:00");
        check(RspsHud.countdown(RspsHud.now() + 3_000L).equals("0:03"), "3 s shows 0:03");
        check(RspsHud.countdown(RspsHud.now() - 5_000L).equals("0:00"), "never below 0:00");
    }

    private static final IntFunction<String> NAMES = new IntFunction<String>() {
        @Override
        public String apply(int id) {
            switch (id) {
                case 4151: return "Abyssal whip";
                case 995: return "Coins";
                case 536: return "Dragon bones";
                case 385: return "Shark";
                default: return "Item " + id;
            }
        }
    };

    private static ObjStack stack(int level, int x, int z, int[][] entries) {
        ObjStack stack = new ObjStack();
        for (int[] e : entries) {
            stack.objs.addLast(new ObjStackEntry(e[0], e[1]));
        }
        Static497.objStacks.put(((long) level << 28) | ((long) z << 14) | x, stack);
        return stack;
    }

    private static void groundItems() {
        RspsHud.reset();
        Static497.objStacks.clear();
        ObjStack a = stack(0, 3200, 3200, new int[][]{{4151, 1}, {385, 1}, {385, 1}, {385, 1}, {536, 1}});
        ObjStack b = stack(0, 3201, 3200, new int[][]{{995, 150}});
        RspsHud.handle("gi|0,3200,3200,4151,180,2400000;0,3200,3200,385,50,800;0,3201,3200,995,90,1;0,3200,3200,536,-1,2000");
        check(RspsHudGroundItems.isDirty(), "a server entry marks the labels dirty");
        RspsHudGroundItems.rebuild(NAMES);
        List<String> labels = RspsHudGroundItems.describe();
        check(labels.contains("0,3200,3200 0 Abyssal whip (2.4M) #ffff9600"), "whip: name, value, high tier colour, lowest row: " + labels);
        check(labels.contains("0,3200,3200 1 Shark x3 (2.4K) #ffffffff"), "three separate sharks are one x3 label with the stack value: " + labels);
        check(labels.contains("0,3201,3200 0 Coins x150 (150) #ffffffff"), "coins stack: quantity and value: " + labels);
        check(!joined(labels).contains("Dragon bones"), "hidden (*bones) is not drawn: " + labels);
        check(RspsHudGroundItems.hasServerEntry(0, 3200, 3200, 536), "a hidden item keeps its server entry");

        // Highlight wins over the value colour.
        RspsHud.handle("hl|add|shark");
        RspsHudGroundItems.rebuild(NAMES);
        check(RspsHudGroundItems.describe().contains("0,3200,3200 1 Shark x3 (2.4K) #ffaa00ff"), "highlighted colour");
        RspsHud.handle("hl|clear");

        // Partial pickup of the group: one shark leaves (OBJ_DEL), the label becomes x2.
        for (ObjStackEntry e = (ObjStackEntry) a.objs.first(); e != null; e = (ObjStackEntry) a.objs.next()) {
            if (e.id == 385) {
                e.unlink();
                break;
            }
        }
        RspsHudGroundItems.markDirty();
        RspsHudGroundItems.rebuild(NAMES);
        check(RspsHudGroundItems.describe().contains("0,3200,3200 1 Shark x2 (1.6K) #ffffffff"), "partial pickup: x2 " + RspsHudGroundItems.describe());

        // Stack count change (OBJ_COUNT): coins 150 -> 40.
        ((ObjStackEntry) b.objs.first()).count = 40;
        RspsHudGroundItems.markDirty();
        RspsHudGroundItems.rebuild(NAMES);
        check(RspsHudGroundItems.describe().contains("0,3201,3200 0 Coins x40 (40) #ffffffff"), "quantity change");

        // Full pickup / despawn of the coins tile (OBJ_DEL empties the stack; the client unlinks it) and the server's -2.
        b.unlink();
        RspsHud.handle("gi|0,3201,3200,995,-2,0");
        RspsHudGroundItems.rebuild(NAMES);
        check(!joined(RspsHudGroundItems.describe()).contains("Coins"), "no ghost label after a full pickup");
        check(!RspsHudGroundItems.hasServerEntry(0, 3201, 3200, 995), "no server entry left after a full pickup");

        // Zone reset / map rebuild / teleport: the client drops the stack without any server entry - the label and the
        // server entry go with it.
        a.unlink();
        RspsHudGroundItems.markDirty();
        RspsHudGroundItems.rebuild(NAMES);
        check(RspsHudGroundItems.describe().isEmpty(), "no ghost label after a zone reset");
        check(!RspsHudGroundItems.hasServerEntry(0, 3200, 3200, 4151), "server entries of dropped stacks are pruned");

        // Relog: everything goes.
        stack(0, 3202, 3200, new int[][]{{4151, 1}});
        RspsHud.handle("gi|0,3202,3200,4151,100,2400000");
        Static497.objStacks.clear();
        RspsHud.reset();
        RspsHudGroundItems.rebuild(NAMES);
        check(RspsHudGroundItems.describe().isEmpty() && !RspsHudGroundItems.hasServerEntry(0, 3202, 3200, 4151), "relog clears everything");

        // Values off: no value text.
        RspsHud.handle("cfg|1011101|20000,100000,1000000,10000000|ffffff,66b2ff,99ff99,ff9600,ff66b2,aa00ff");
        stack(0, 3203, 3200, new int[][]{{4151, 1}});
        RspsHud.handle("gi|0,3203,3200,4151,100,2400000");
        RspsHudGroundItems.rebuild(NAMES);
        check(RspsHudGroundItems.describe().contains("0,3203,3200 0 Abyssal whip #ffff9600"), "value text off, tier colour kept: " + RspsHudGroundItems.describe());
        Static497.objStacks.clear();
        RspsHud.reset();
    }

    // ---- movable panels (Alt + drag) and the 78 Plugins panel ---------------------------------------------------------

    private static final class Keys extends KeyboardMonitor {
        boolean alt;
        @Override public KeyLog removeFirstRecorded() { return null; }
        @Override public boolean isPressed(int keyCode) { return alt && keyCode == RspsOverlayLayout.KEY_ALT; }
        @Override public void record() { }
        @Override public void remove() { }
    }

    private static final class Mouse extends MouseMonitor {
        int x;
        int y;
        boolean left;
        @Override public void record() { }
        @Override public boolean isLeftDown() { return left; }
        @Override public boolean isRightDown() { return false; }
        @Override public boolean isMiddleDown() { return false; }
        @Override public void remove() { }
        @Override public MouseLog removeFirstLog() { return null; }
        @Override public int getRecordedX() { return x; }
        @Override public int getRecordedY() { return y; }
    }

    private static MouseLog press(final int type, final int x, final int y) {
        return new MouseLog() {
            @Override public int getType() { return type; }
            @Override public int getX() { return x; }
            @Override public int getExtra() { return 1; }
            @Override public int getY() { return y; }
            @Override public long getTime() { return 0L; }
        };
    }

    private static void layout() {
        try {
            File dir = Files.createTempDirectory("rsps-overlays").toFile();
            System.setProperty("rsps.preferencesDir", dir.getAbsolutePath());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        RspsOverlayLayout.reloadForTest();
        Keys keys = new Keys();
        Mouse mouse = new Mouse();
        KeyboardMonitor.instance = keys;
        MouseMonitor.instance = mouse;

        int[] at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 800, 600);
        check(at[0] == 4 && at[1] == 30, "unmoved panel: default spot");
        at = RspsOverlayLayout.place("far", 790, 590, 129, 100, 0, 0, 800, 600);
        check(at[0] == 671 && at[1] == 500, "a panel never leaves its area");

        // Without Alt a press on a panel goes to the game.
        check(!RspsOverlayLayout.mousePress(press(MouseLog.TYPE_PRESS_LEFT, 10, 40)), "no Alt: the click reaches the game");

        // Alt + left press on the panel starts a drag and is taken from the game.
        keys.alt = true;
        mouse.x = 10;
        mouse.y = 40;
        mouse.left = true;
        check(RspsOverlayLayout.mousePress(press(MouseLog.TYPE_PRESS_LEFT, 10, 40)), "Alt + press on a panel is taken");
        check(!RspsOverlayLayout.mousePress(press(MouseLog.TYPE_PRESS_LEFT, 500, 300)), "Alt + press on empty scene still reaches the game");
        mouse.x = 300;
        mouse.y = 200;
        RspsOverlayLayout.tick();
        at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 800, 600);
        check(at[0] == 294 && at[1] == 190, "while dragging the panel follows the mouse, grab point kept: " + at[0] + "," + at[1]);
        mouse.left = false;
        RspsOverlayLayout.tick();
        check(RspsOverlayLayout.isMoved("boosts"), "released: the panel keeps its new spot");
        File file = RspsOverlayLayout.file();
        check(file.isFile(), "position saved to " + file);

        // A new session reads it back; a smaller window clamps it inside.
        RspsOverlayLayout.reloadForTest();
        at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 800, 600);
        check(at[0] == 294 && at[1] == 190, "position survives a restart");
        at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 300, 200);
        check(at[0] == 171 && at[1] == 100, "a smaller game view clamps the moved panel inside: " + at[0] + "," + at[1]);

        // Alt + right-click puts it back.
        at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 800, 600);
        check(RspsOverlayLayout.mousePress(press(MouseLog.TYPE_PRESS_RIGHT, at[0] + 5, at[1] + 5)), "Alt + right-click on a panel is taken");
        at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 800, 600);
        check(at[0] == 4 && at[1] == 30 && !RspsOverlayLayout.isMoved("boosts"), "Alt + right-click: back on the default spot");

        // Reset all.
        RspsOverlayLayout.dropForTest("boosts", 100, 100);
        check(RspsOverlayLayout.isMoved("boosts"), "moved again");
        RspsOverlayLayout.resetAll();
        check(!RspsOverlayLayout.isMoved("boosts"), "reset all positions");
        keys.alt = false;
    }

    private static void settingsPanel() {
        String all = "1011111111111";
        String tiers = "|20000,100000,1000000,10000000|ffffff,66b2ff,99ff99,ff9600,ff66b2,aa00ff";
        RspsHud.handle("cfg|" + all + tiers + "|0");
        RspsHud.handle("open");
        check(RspsHudSettings.isOpen() && RspsHudSettings.MAIN.equals(RspsHudSettings.view), "open payload (hud command) opens the main view");
        RspsHudSettings.layoutForTest(100, 100);
        int right = 100 + RspsHudSettings.WIDTH;
        int row0 = 100 + RspsHudSettings.TITLE + 2 + 5;
        int step = RspsHudSettings.ROW;

        // Main view: the switch toggles the plugin, ">" opens its settings.
        check(RspsHudSettings.click(130, row0, MouseLog.TYPE_PRESS_LEFT), "a click on a plugin row is taken");
        check(!RspsHudConfig.boosts && "hud set boosts off".equals(RspsHudSettings.lastSent), "the switch turns Boosts off and saves it: " + RspsHudSettings.lastSent);
        RspsHudSettings.click(130, row0, MouseLog.TYPE_PRESS_LEFT);
        check(RspsHudConfig.boosts && "hud set boosts on".equals(RspsHudSettings.lastSent), "and on again");
        RspsHudSettings.click(130, row0 + 3 * step, MouseLog.TYPE_PRESS_LEFT);
        check(!RspsHudConfig.timers && "hud set timers off".equals(RspsHudSettings.lastSent), "Timers & Buffs off");
        RspsHudSettings.click(130, row0 + 3 * step, MouseLog.TYPE_PRESS_LEFT);

        // Boosts settings.
        RspsHudSettings.click(right - 8, row0, MouseLog.TYPE_PRESS_LEFT);
        check(RspsHudSettings.BOOSTS.equals(RspsHudSettings.view), "> opens the Boosts settings");
        RspsHudSettings.click(130, row0, MouseLog.TYPE_PRESS_LEFT);
        check(RspsHudConfig.boostsAll && "hud set boostsall on".equals(RspsHudSettings.lastSent), "all five stats on");
        RspsHudSettings.click(130, row0 + step, MouseLog.TYPE_PRESS_LEFT);
        check(!RspsHudConfig.boostTimers && "hud set boosttimers off".equals(RspsHudSettings.lastSent), "next-change timers off");
        RspsHudSettings.click(106, 110, MouseLog.TYPE_PRESS_LEFT);
        check(RspsHudSettings.MAIN.equals(RspsHudSettings.view), "< goes back to the plugin list");

        // Opponent settings.
        RspsHudSettings.click(right - 8, row0 + step, MouseLog.TYPE_PRESS_LEFT);
        RspsHudSettings.click(130, row0, MouseLog.TYPE_PRESS_LEFT);
        check(!RspsHudConfig.opponentExact && "hud set oppexact off".equals(RspsHudSettings.lastSent), "Opponent: percentage only");
        RspsHudSettings.click(106, 110, MouseLog.TYPE_PRESS_LEFT);

        // Ground Items settings: values open the game's input box through the server, lists add / clear / remove.
        RspsHud.handle("hl|clear");
        RspsHud.handle("hl|add|dragon*,shark");
        RspsHud.handle("hide|clear");
        RspsHudSettings.click(right - 8, row0 + 2 * step, MouseLog.TYPE_PRESS_LEFT);
        check(RspsHudSettings.GROUND.equals(RspsHudSettings.view), "> opens the Ground Items settings");
        RspsHudSettings.click(130, row0, MouseLog.TYPE_PRESS_LEFT);
        check(!RspsHudConfig.value && "hud set value off".equals(RspsHudSettings.lastSent), "values off");
        RspsHudSettings.click(130, row0 + 2 * step, MouseLog.TYPE_PRESS_LEFT);
        check("hud edit hideunder".equals(RspsHudSettings.lastSent), "hide-under value opens the input box: " + RspsHudSettings.lastSent);
        RspsHudSettings.click(130, row0 + 4 * step, MouseLog.TYPE_PRESS_LEFT);
        check("hud edit tier low".equals(RspsHudSettings.lastSent), "the low value opens the input box");
        RspsHudSettings.click(right - 8, row0 + 4 * step, MouseLog.TYPE_PRESS_LEFT);
        check("hud edit colour low".equals(RspsHudSettings.lastSent), "the low colour swatch opens the colour box");
        RspsHudSettings.click(130, row0 + 8 * step, MouseLog.TYPE_PRESS_LEFT);
        check("hud edit colour highlight".equals(RspsHudSettings.lastSent), "highlight colour");
        RspsHudSettings.click(right - 10, row0 + 9 * step, MouseLog.TYPE_PRESS_LEFT);
        check("hud edit highlight".equals(RspsHudSettings.lastSent), "Add on the highlighted list: " + RspsHudSettings.lastSent);
        RspsHudSettings.click(right - RspsHudSettings.ADD_ZONE - 20, row0 + 9 * step, MouseLog.TYPE_PRESS_LEFT);
        check("hud clear highlight".equals(RspsHudSettings.lastSent), "Clear on the highlighted list");
        RspsHudSettings.click(right - 8, row0 + 10 * step, MouseLog.TYPE_PRESS_LEFT);
        check("hud remove highlight dragon*".equals(RspsHudSettings.lastSent), "x removes one entry: " + RspsHudSettings.lastSent);
        RspsHudSettings.click(right - 10, row0 + 12 * step, MouseLog.TYPE_PRESS_LEFT);
        check("hud edit hide".equals(RspsHudSettings.lastSent), "Add on the hidden list (below the 2 highlighted entries): " + RspsHudSettings.lastSent);
        RspsHudSettings.click(106, 110, MouseLog.TYPE_PRESS_LEFT);

        // Timers & Buffs settings: every line on its own.
        RspsHudSettings.click(right - 8, row0 + 3 * step, MouseLog.TYPE_PRESS_LEFT);
        RspsHudSettings.click(130, row0 + 2 * step, MouseLog.TYPE_PRESS_LEFT);
        check(!RspsHudConfig.timerTeleblock && "hud set tteleblock off".equals(RspsHudSettings.lastSent), "Teleblock line off");

        check(RspsHudSettings.click(150, 110, MouseLog.TYPE_PRESS_RIGHT), "a right-click on the panel does not reach the game");
        check(!RspsHudSettings.click(20, 20, MouseLog.TYPE_PRESS_LEFT), "a click beside the panel reaches the game");
        check(RspsHudSettings.click(right - 5, 110, MouseLog.TYPE_PRESS_LEFT) && !RspsHudSettings.isOpen(), "the x closes the panel");
        check(!RspsHudSettings.click(150, row0, MouseLog.TYPE_PRESS_LEFT), "a closed panel takes no clicks");
        RspsHud.handle("open");
        RspsHud.handle("close");
        check(!RspsHudSettings.isOpen(), "close payload");

        // The server push is the final word, including the newer switches and the hide-under value.
        RspsHud.handle("cfg|1011111101101" + tiers + "|5000");
        check(RspsHudConfig.boosts && !RspsHudConfig.boostsAll && RspsHudConfig.boostTimers && !RspsHudConfig.opponentExact, "push: switches 1-9");
        check(RspsHudConfig.timerVengeance && RspsHudConfig.timerVengeanceCooldown && !RspsHudConfig.timerTeleblock && RspsHudConfig.timerFrozen, "push: timer lines");
        check(RspsHudConfig.hideUnder == 5000L, "push: hide under");
        RspsHud.handle("cfg|1011111" + tiers);
        check(RspsHudConfig.timerTeleblock && RspsHudConfig.opponentExact && RspsHudConfig.hideUnder == 0L, "an older 7-switch push leaves the newer ones on");

        // Hide under: small stacks go, highlighted ones stay.
        Static497.objStacks.clear();
        RspsHud.reset();
        RspsHud.handle("cfg|" + all + tiers + "|1000");
        RspsHud.handle("hl|clear");
        RspsHud.handle("hide|clear");
        stack(0, 3300, 3300, new int[][]{{385, 1}, {385, 1}, {995, 150}, {536, 1}});
        RspsHud.handle("gi|0,3300,3300,385,50,800;0,3300,3300,995,90,1;0,3300,3300,536,90,900");
        RspsHud.handle("hl|add|dragon bones");
        RspsHudGroundItems.rebuild(NAMES);
        String labels = joined(RspsHudGroundItems.describe());
        check(labels.contains("Shark x2") && !labels.contains("Coins") && labels.contains("Dragon bones"), "hide under 1000: sharks (1.6K) and highlighted bones (900) stay, coins (150) go: " + labels);
        Static497.objStacks.clear();
        RspsHud.reset();
        RspsHud.handle("cfg|" + all + tiers + "|0");
        RspsHud.handle("hl|clear");
        RspsHud.handle("hide|clear");
    }

    /** 100 players with drops everywhere: 600 stacks of 5 items; the label rebuild (only on a change) must stay cheap. */
    private static void crowdedGround() {
        Static497.objStacks.clear();
        RspsHud.reset();
        StringBuilder gi = new StringBuilder();
        int[] ids = {4151, 995, 385, 536, 385};
        for (int i = 0; i < 600; i++) {
            int x = 3000 + i % 30;
            int z = 3000 + i / 30;
            stack(0, x, z, new int[][]{{4151, 1}, {995, 1000 + i}, {385, 1}, {536, 1}, {385, 1}});
            for (int id : new int[]{4151, 995, 385, 536}) {
                if (gi.length() > 0) {
                    gi.append(';');
                }
                gi.append("0,").append(x).append(',').append(z).append(',').append(id).append(",150,").append(id == 995 ? 1 : 1000);
            }
        }
        long parseStart = System.nanoTime();
        RspsHud.handle("gi|" + gi);
        long parseNanos = System.nanoTime() - parseStart;
        RspsHudGroundItems.rebuild(NAMES);
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 5; run++) {
            RspsHudGroundItems.markDirty();
            long start = System.nanoTime();
            RspsHudGroundItems.rebuild(NAMES);
            best = Math.min(best, System.nanoTime() - start);
        }
        int labels = RspsHudGroundItems.describe().size();
        System.out.println("crowded ground: 600 stacks / 3000 items -> " + labels + " labels, rebuild " + best / 1000 + " us, parse " + parseNanos / 1000 + " us");
        check(labels == 2400, "600 stacks x 4 item kinds (sharks grouped) = 2400 labels: " + labels);
        check(best < 30_000_000L, "label rebuild for 3000 ground items under 30 ms: " + best / 1_000_000.0 + " ms");

        // One pickup in that crowd (OBJ_DEL empties a tile) and one new drop: only those two tiles are rebuilt.
        long pickedKey = (3000L << 14) | 3000L;
        ((ObjStack) Static497.objStacks.get(pickedKey)).unlink();
        RspsHudGroundItems.markTile(pickedKey);
        stack(0, 3100, 3100, new int[][]{{4151, 1}});
        RspsHudGroundItems.markTile(0, 3100, 3100);
        long start = System.nanoTime();
        RspsHudGroundItems.rebuild(NAMES);
        long tileNanos = System.nanoTime() - start;
        List<String> after = RspsHudGroundItems.describe();
        System.out.println("crowded ground: one pickup + one drop rebuilt in " + tileNanos / 1000 + " us");
        check(after.size() == 2400 - 4 + 1, "pickup removed its 4 labels, the drop added 1: " + after.size());
        check(!joined(after).contains("0,3000,3000 "), "no ghost label on the picked-up tile");
        check(!RspsHudGroundItems.hasServerEntry(0, 3000, 3000, 4151), "the picked-up tile's server entries went with it");
        check(tileNanos < 2_000_000L, "a single tile change costs well under 2 ms: " + tileNanos / 1000 + " us");
        RspsHud.handle("gi|0,3100,3100,4151,150,2400000");
        check(RspsHudGroundItems.isDirty(), "a server entry marks only its tile");
        RspsHudGroundItems.rebuild(NAMES);
        check(joined(RspsHudGroundItems.describe()).contains("0,3100,3100 0 Abyssal whip (2.4M)"), "the new drop got its value");
        Static497.objStacks.clear();
        RspsHud.reset();
    }

    private static String joined(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (String s : list) {
            sb.append(s).append('\n');
        }
        return sb.toString();
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
