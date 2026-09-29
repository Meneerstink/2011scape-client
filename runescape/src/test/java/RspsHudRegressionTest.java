import rs2.client.event.keyboard.KeyLog;
import rs2.client.event.keyboard.KeyboardMonitor;
import rs2.client.event.mouse.MouseLog;
import rs2.client.event.mouse.MouseMonitor;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Headless checks of the RSPS information overlays (RspsHud*, RspsSidebar, RspsOverlayLayout): payload decoding, every
 * RuneLite setting's effect on what is shown, the Ground Items label lifecycle over the client's own ground-item state
 * (no ghost labels), the right-click menu helpers, movable panels, and the 78 Plugins sidebar (switches and settings send
 * the right commands). Drawing itself needs a toolkit and is checked in the client (hudtour screenshots).
 */
public final class RspsHudRegressionTest {
    private static int assertions;

    private static final String CFG_DEFAULTS = "cfg|grounditems.priceDisplayMode=GE;grounditems.groundItemTimers=SECONDS;grounditems.hideUnderValue=0;"
        + "grounditems.showHighlightedOnly=false;grounditems.ownershipFilterMode=ALL;grounditems.itemHighlightMode=BOTH;grounditems.menuHighlightMode=NAME;"
        + "grounditems.highlightValueCalculation=HIGHEST;grounditems.dontHideUntradeables=true";

    public static void main(String[] args) {
        values();
        config();
        countdowns();
        groundItems();
        groundSettings();
        menu();
        boosts();
        timers();
        opponent();
        layout();
        sidebar();
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

    private static void config() {
        RspsHudConfig.resetToDefaults();
        int ground = 0;
        for (RspsHudConfig.Setting s : RspsHudConfig.SETTINGS) {
            if (s.plugin.equals("grounditems")) {
                ground++;
            }
        }
        check(ground == 35, "Ground Items offers all 35 RuneLite config items (the other 2 of its 37 annotations are list setters): " + ground);
        check(RspsHudConfig.is("grounditems.priceDisplayMode", "GE") && RspsHudConfig.integer("grounditems.lowValuePrice") == 20000, "defaults before the push");
        RspsHud.handle("cfg|grounditems.lowValuePrice=30;grounditems.mediumValuePrice=40;grounditems.highValuePrice=50;grounditems.insaneValuePrice=60;grounditems.highValueColor=000003");
        check(RspsHudConfig.tier(55L) == RspsHudConfig.TIER_HIGH && RspsHudConfig.tierColour(RspsHudConfig.TIER_HIGH) == 0xFF000003, "tiers and colours from the push");
        check(RspsHudConfig.tier(5L) == RspsHudConfig.TIER_NONE && RspsHudConfig.tierColour(0) == 0xFFFFFFFF, "below the first tier: default colour");
        check(RspsHudConfig.tierSetting("grounditems.notifyTier") == Integer.MAX_VALUE, "notify tier OFF reaches nothing");
        RspsHud.handle("hl|clear");
        RspsHud.handle("hl|add|dragon*,shark");
        check(RspsHudConfig.isHighlighted("Dragon dagger(p++)") && RspsHudConfig.isHighlighted("Shark") && !RspsHudConfig.isHighlighted("Sharks"), "wildcards, whole-name match");
        check(RspsHudConfig.names(false).size() == 2, "list names kept for the sidebar");
        RspsHud.handle("hl|clear");
        check(RspsHudConfig.hotkeyCode() == 86, "hotkey Alt = client key 86");
        RspsHud.handle("cfg|grounditems.hotkey=SHIFT");
        check(RspsHudConfig.hotkeyCode() == 81, "hotkey Shift = 81");
        RspsHud.handle("garbage|x|y");
        RspsHud.handle("tmr|not-a-number");
        RspsHudConfig.resetToDefaults();
        check(true, "malformed payloads are ignored");
    }

    private static void countdowns() {
        check(RspsHud.endTime(0) == -1L && RspsHud.endTime(-1) == -1L, "no countdown for 0 / -1 ticks");
        long end = RspsHud.endTime(100);
        check(Math.abs(end - (RspsHud.now() + 100 * RspsHud.TICK_MS)) < 50L, "100 ticks = 60 seconds");
        check(RspsHud.countdown(RspsHud.now() + 60_000L).equals("1:00"), "60 s shows 1:00 (RuneLite Timer %d:%02d)");
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
                case 6570: return "Fire cape";
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

    private static List<String> labels() {
        RspsHudGroundItems.rebuild(NAMES);
        return RspsHudGroundItems.describe();
    }

    private static void freshGround() {
        Static497.objStacks.clear();
        RspsHud.reset();
        RspsHudConfig.resetToDefaults();
    }

    private static void groundItems() {
        freshGround();
        RspsHud.handle("hide|add|*bones");
        ObjStack a = stack(0, 3200, 3200, new int[][]{{4151, 1}, {385, 1}, {385, 1}, {385, 1}, {536, 1}});
        ObjStack b = stack(0, 3201, 3200, new int[][]{{995, 150}});
        RspsHud.handle("gi|0,3200,3200,4151,180,2400000,72000,0;0,3200,3200,385,50,800,120,0;0,3201,3200,995,90,1,0,0;0,3200,3200,536,-1,2000,1,0");
        check(RspsHudGroundItems.isDirty(), "a server entry marks its tile");
        List<String> l = labels();
        check(l.contains("0,3200,3200 0 Abyssal whip (2.4M gp) #ffff9600 beam"), "whip: name, GE price, high tier colour, lowest row, a lootbeam (RuneLite default: High tier): " + l);
        check(l.contains("0,3200,3200 1 Shark x3 (2.4K gp) #ffffffff"), "three separate sharks are one x3 label with the stack value: " + l);
        check(l.contains("0,3201,3200 0 Coins x150 #ffffffff"), "coins: quantity, no price (RuneLite shows none for coins): " + l);
        check(joined(l).contains("Dragon bones") && joined(l).contains("#ff808080 hidden"), "hidden (*bones) is kept as a hidden label in the hidden colour (drawn only with the hotkey): " + l);

        RspsHud.handle("hl|add|shark");
        check(labels().contains("0,3200,3200 1 Shark x3 (2.4K gp) #ffaa00ff"), "highlighted colour");
        RspsHud.handle("hl|clear");

        // Partial pickup of the group: one shark leaves (OBJ_DEL), the label becomes x2.
        for (ObjStackEntry e = (ObjStackEntry) a.objs.first(); e != null; e = (ObjStackEntry) a.objs.next()) {
            if (e.id == 385) {
                e.unlink();
                break;
            }
        }
        RspsHudGroundItems.markTile(0, 3200, 3200);
        check(labels().contains("0,3200,3200 2 Shark x2 (1.6K gp) #ffffffff"), "partial pickup: x2, now below the 2K bones: " + labels());
        ((ObjStackEntry) b.objs.first()).count = 40;
        RspsHudGroundItems.markTile(0, 3201, 3200);
        check(labels().contains("0,3201,3200 0 Coins x40 #ffffffff"), "quantity change");
        b.unlink();
        RspsHud.handle("gi|0,3201,3200,995,-2,0,0,0");
        check(!joined(labels()).contains("Coins") && !RspsHudGroundItems.hasServerEntry(0, 3201, 3200, 995), "no ghost label or entry after a full pickup");
        a.unlink();
        RspsHudGroundItems.markTile(0, 3200, 3200);
        check(labels().isEmpty() && !RspsHudGroundItems.hasServerEntry(0, 3200, 3200, 4151), "no ghost label after a zone reset");
        stack(0, 3202, 3200, new int[][]{{4151, 1}});
        RspsHud.handle("gi|0,3202,3200,4151,100,2400000,72000,0");
        Static497.objStacks.clear();
        RspsHud.reset();
        check(labels().isEmpty() && !RspsHudGroundItems.hasServerEntry(0, 3202, 3200, 4151), "relog clears everything");
    }

    private static void groundSettings() {
        freshGround();
        stack(0, 3300, 3300, new int[][]{{4151, 1}});
        stack(0, 3301, 3300, new int[][]{{6570, 1}});
        stack(0, 3302, 3300, new int[][]{{385, 1}});
        // whip: GE 2.4M, HA 72K; fire cape: untradeable, GE 0, HA 0 (flag 1); shark (mine, private, fresh): GE 800 HA 120
        RspsHud.handle("gi|0,3300,3300,4151,180,2400000,72000,4;0,3301,3300,6570,180,0,0,1;0,3302,3300,385,180,800,120,26");
        RspsHud.handle("cfg|grounditems.priceDisplayMode=BOTH");
        check(labels().contains("0,3300,3300 0 Abyssal whip (GE: 2.4M gp) (HA: 72K gp) #ffff9600 beam"), "price display Both: " + labels());
        RspsHud.handle("cfg|grounditems.priceDisplayMode=HA");
        check(labels().contains("0,3300,3300 0 Abyssal whip (HA: 72K gp) #ffff9600 beam"), "price display HA (the tier still uses the highest value): " + labels());
        RspsHud.handle("cfg|grounditems.highlightValueCalculation=HA");
        check(labels().contains("0,3300,3300 0 Abyssal whip (HA: 72K gp) #ff66b2ff"), "value calculation HA: 72K is the low tier: " + labels());
        RspsHud.handle("cfg|grounditems.priceDisplayMode=OFF;grounditems.highlightValueCalculation=HIGHEST");
        check(labels().contains("0,3300,3300 0 Abyssal whip #ffff9600 beam"), "price display Off");
        RspsHud.handle("cfg|grounditems.hideUnderValue=1000");
        String l = joined(labels());
        check(l.contains("Shark #ff808080 hidden") && l.contains("Fire cape #ffffffff") && !l.contains("Abyssal whip #ff808080"),
            "hide under 1000: the shark (800) goes, the untradeable fire cape stays (Do not hide untradeables): " + l);
        RspsHud.handle("cfg|grounditems.dontHideUntradeables=false");
        check(joined(labels()).contains("Fire cape #ff808080 hidden"), "untradeables hidden too when that is off");
        RspsHud.handle("cfg|grounditems.hideUnderValue=0;grounditems.dontHideUntradeables=true;grounditems.showHighlightedOnly=true");
        RspsHud.handle("hl|add|abyssal whip");
        l = joined(labels());
        check(l.contains("Abyssal whip #ffaa00ff") && l.contains("Shark #ff808080 hidden") && l.contains("Fire cape #ff808080 hidden"), "show highlighted items only: " + l);
        RspsHud.handle("hl|clear");
        RspsHud.handle("cfg|grounditems.showHighlightedOnly=false;grounditems.ownershipFilterMode=DROPS");
        l = joined(labels());
        check(l.contains("Shark") && !l.contains("Abyssal whip") && !l.contains("Fire cape"), "ownership Drops: only your own drops: " + l);
        RspsHud.handle("cfg|grounditems.ownershipFilterMode=ALL;grounditems.showLootbeamTier=HIGH");
        check(joined(labels()).contains("Abyssal whip #ffff9600 beam"), "lootbeam from the High tier");
        RspsHud.handle("cfg|grounditems.showLootbeamTier=OFF;grounditems.showLootbeamForHighlighted=true");
        RspsHud.handle("hl|add|shark");
        l = joined(labels());
        check(l.contains("Shark #ffaa00ff beam") && !l.contains("whip #ffff9600 beam"), "lootbeam for highlighted items only: " + l);

        // Notifications for your own fresh drops.
        freshGround();
        RspsHud.handle("cfg|grounditems.notifyHighlightedDrops=true");
        RspsHud.handle("hl|add|shark");
        RspsHud.lastNotification = null;
        stack(0, 3303, 3300, new int[][]{{385, 1}});
        RspsHud.handle("gi|0,3303,3300,385,200,800,120,18");
        labels();
        check("You received a highlighted drop: Shark (800 gp)".equals(RspsHud.lastNotification), "highlighted own drop notified: " + RspsHud.lastNotification);
        RspsHud.lastNotification = null;
        stack(0, 3304, 3300, new int[][]{{4151, 1}});
        RspsHud.handle("cfg|grounditems.notifyTier=HIGH");
        RspsHud.handle("gi|0,3304,3300,4151,200,2400000,72000,22");
        labels();
        check("You received a high value drop: Abyssal whip (2.4M gp)".equals(RspsHud.lastNotification), "tier drop notified: " + RspsHud.lastNotification);
        RspsHud.lastNotification = null;
        stack(0, 3305, 3300, new int[][]{{4151, 1}});
        RspsHud.handle("gi|0,3305,3300,4151,200,2400000,72000,20");
        labels();
        check(RspsHud.lastNotification == null, "someone else's drop is not notified");

        // Double tap of the hotkey toggles hide-all.
        RspsHudGroundItems.keyPressed(86, 1000L);
        RspsHudGroundItems.keyPressed(86, 1100L);
        check(RspsHudGroundItems.hideAll(), "double tap within 250 ms hides everything");
        RspsHudGroundItems.keyPressed(86, 5000L);
        RspsHudGroundItems.keyPressed(86, 5600L);
        check(RspsHudGroundItems.hideAll(), "two taps 600 ms apart are no double tap");
        RspsHudGroundItems.keyPressed(86, 5700L);
        check(!RspsHudGroundItems.hideAll(), "the next double tap shows them again");
        freshGround();
    }

    private static void menu() {
        freshGround();
        stack(0, 3400, 3400, new int[][]{{4151, 1}, {995, 25000}, {536, 1}});
        RspsHud.handle("gi|0,3400,3400,4151,180,2400000,72000,0;0,3400,3400,995,180,1,0,0;0,3400,3400,536,180,2000,1,0");
        RspsHud.handle("hide|add|dragon bones");
        labels();
        check(RspsHudGroundItems.menuTarget(0, 3400, 3400, 4151, "Abyssal whip", 1, 1).equals("<col=ff9600>Abyssal whip"), "menu name in the tier colour");
        check(RspsHudGroundItems.menuTarget(0, 3400, 3400, 995, "Coins", 25000, 1).equals("<col=66b2ff>Coins (25K)"), "menu quantity + low tier colour: "
            + RspsHudGroundItems.menuTarget(0, 3400, 3400, 995, "Coins", 25000, 1));
        check(RspsHudGroundItems.menuOption(0, 3400, 3400, 4151, "Take").equals("Take"), "menu highlight mode Name: the option keeps its colour");
        RspsHud.handle("cfg|grounditems.menuHighlightMode=BOTH");
        check(RspsHudGroundItems.menuOption(0, 3400, 3400, 4151, "Take").equals("<col=ff9600>Take"), "menu highlight mode Both colours the option too");
        check(RspsHudGroundItems.menuTarget(0, 3400, 3400, 536, "Dragon bones", 1, 1).equals("<col=ff9040>Dragon bones"), "a hidden item keeps the game colour ...");
        RspsHud.handle("cfg|grounditems.recolorMenuHiddenItems=true;grounditems.deprioritizeHiddenItems=true");
        check(RspsHudGroundItems.menuTarget(0, 3400, 3400, 536, "Dragon bones", 1, 1).equals("<col=808080>Dragon bones"), "... or the hidden colour with Recolor menu hidden items");
        check(RspsHudGroundItems.deprioritize(0, 3400, 3400, 536) && !RspsHudGroundItems.deprioritize(0, 3400, 3400, 4151), "only hidden items are deprioritised");
        RspsHud.handle("cfg|grounditems.showMenuItemQuantities=false;grounditems.itemHighlightMode=OVERLAY");
        check(RspsHudGroundItems.menuTarget(0, 3400, 3400, 995, "Coins", 25000, 3).equals("<col=ff9040>Coins x 3"), "no quantity, no menu colours in Overlay mode, collapsed count: "
            + RspsHudGroundItems.menuTarget(0, 3400, 3400, 995, "Coins", 25000, 3));
        RspsHud.handle("cfg|grounditems.collapseEntries=true");
        check(RspsHudGroundItems.collapse(), "collapse entries switch");
        freshGround();
    }

    private static void boosts() {
        RspsHudConfig.resetToDefaults();
        RspsHud.reset();
        int[] cur = Static581.statLevels;
        int[] base = Static498.statBaseLevels;
        for (int i = 0; i < 25; i++) {
            cur[i] = 99;
            base[i] = 99;
        }
        cur[0] = 118;
        cur[1] = 77;
        cur[14] = 104;
        RspsHud.handle("boost|50|20|70");
        check(RspsHudBoosts.displayed().length == 5, "Display boosts: Combat = the five combat stats");
        check(RspsHudBoosts.text(0, true).equals("118/99") && RspsHudBoosts.colour(19) == RspsHudBoosts.BOOSTED && RspsHudBoosts.colour(-22) == RspsHudBoosts.DRAINED,
            "absolute text and RuneLite colours");
        RspsHud.handle("cfg|boosts.relativeBoost=true;boosts.boostThreshold=20");
        check(RspsHudBoosts.text(0, true).equals("+19") && RspsHudBoosts.text(1, false).equals("-22"), "relative boosts");
        check(RspsHudBoosts.colour(19) == RspsHudBoosts.NEAR_THRESHOLD, "a boost at or under the threshold is yellow");
        check(RspsHudBoosts.nextBuffSeconds() == 30 && RspsHudBoosts.nextDebuffSeconds() == 12, "next change seconds from the server cycles (BOOSTED mode)");
        RspsHud.handle("cfg|boosts.displayNextBuffChange=NEVER;boosts.displayNextDebuffChange=NEVER");
        check(RspsHudBoosts.nextBuffSeconds() == -1 && RspsHudBoosts.nextDebuffSeconds() == -1, "NEVER hides both");
        RspsHud.handle("cfg|boosts.displayBoosts=NON_COMBAT;boosts.displayNextBuffChange=BOOSTED");
        check(RspsHudBoosts.nextBuffSeconds() == 42, "non-combat boosts use the non-combat cycle (Mining +5)");
        cur[14] = 99;
        check(RspsHudBoosts.nextBuffSeconds() == -1, "BOOSTED: nothing boosted, nothing shown");
        RspsHud.handle("cfg|boosts.displayNextBuffChange=ALWAYS");
        check(RspsHudBoosts.nextBuffSeconds() == 42, "ALWAYS: shown with nothing boosted");
        RspsHud.handle("cfg|boosts.displayBoosts=COMBAT;boosts.displayInfoboxes=true;boosts.displayNextDebuffChange=BOOSTED");
        RspsInfoBoxes.clear();
        RspsHudBoosts.addInfoboxes();
        check(RspsInfoBoxes.count() == 4, "infoboxes: attack, defence, the restore arrow and the drop arrow: " + RspsInfoBoxes.count());
        RspsHud.handle("cfg|boosts.displayPanel=false");
        check(RspsHudBoosts.height() == 0, "Display panel off: no panel");
        RspsHud.handle("cfg|boosts.displayPanel=true;boosts.displayNextBuffChange=NEVER;boosts.displayNextDebuffChange=NEVER");
        check(RspsHudBoosts.height() == 4 + 2 * 16 + 4, "panel: two changed stats, RuneLite's 4 px border and 16 px lines");
        // Threshold notification: a boost draining to the threshold.
        RspsHud.handle("cfg|boosts.notifyOnBoost=true;boosts.boostThreshold=18");
        RspsHud.lastNotification = null;
        RspsHudBoosts.checkThreshold();
        cur[0] = 117;
        RspsHudBoosts.checkThreshold();
        check("Attack level is getting low!".equals(RspsHud.lastNotification), "threshold notification: " + RspsHud.lastNotification);
        for (int i = 0; i < 25; i++) {
            cur[i] = 1;
            base[i] = 1;
        }
        RspsHudConfig.resetToDefaults();
    }

    private static void timers() {
        RspsHud.reset();
        RspsHudConfig.resetToDefaults();
        RspsHud.handle("tmr|1|50|500|0");
        RspsInfoBoxes.clear();
        RspsHudTimers.addInfoboxes();
        check(RspsInfoBoxes.count() == 3, "vengeance active, cooldown and teleblock infoboxes");
        RspsHud.handle("cfg|timers.showTeleblock=false;timers.showVengeanceActive=false");
        RspsInfoBoxes.clear();
        RspsHudTimers.addInfoboxes();
        check(RspsInfoBoxes.count() == 1, "each timer switchable");
        RspsHud.handle("tmr|0|0|0|20");
        RspsInfoBoxes.clear();
        RspsHudTimers.addInfoboxes();
        check(RspsInfoBoxes.count() == 1 && RspsHudTimers.showing(2), "a freeze");
        RspsHudConfig.resetToDefaults();
        RspsHud.reset();
    }

    private static void opponent() {
        RspsHudConfig.resetToDefaults();
        RspsHud.handle("opp|n|5|170|20|30");
        check(RspsHudOpponent.healthText().equals("20/30"), "Hitpoints style: " + RspsHudOpponent.healthText());
        RspsHud.handle("cfg|opponent.hitpointsDisplayStyle=PERCENTAGE");
        check(RspsHudOpponent.healthText().equals("67%"), "Percentage style");
        RspsHud.handle("cfg|opponent.hitpointsDisplayStyle=BOTH");
        check(RspsHudOpponent.healthText().equals("20/30 (67%)"), "Both style");
        RspsHud.handle("opp|p|3|128");
        check(RspsHudOpponent.healthText().equals("50%"), "a player: the percentage only (hit-mask ratio)");
        RspsHud.handle("opp|none");
        RspsHudConfig.resetToDefaults();
    }

    // ---- movable panels (Alt + drag) ----------------------------------------------------------------------------------

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
        check(!RspsOverlayLayout.mousePress(press(MouseLog.TYPE_PRESS_LEFT, 10, 40)), "no Alt: the click reaches the game");
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
        check(at[0] == 294 && at[1] == 190, "while dragging the panel follows the mouse: " + at[0] + "," + at[1]);
        mouse.left = false;
        RspsOverlayLayout.tick();
        check(RspsOverlayLayout.isMoved("boosts") && RspsOverlayLayout.file().isFile(), "released: kept and saved");
        RspsOverlayLayout.reloadForTest();
        at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 800, 600);
        check(at[0] == 294 && at[1] == 190, "position survives a restart");
        at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 300, 200);
        check(at[0] == 171 && at[1] == 100, "a smaller game view clamps the moved panel inside");
        at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 800, 600);
        check(RspsOverlayLayout.mousePress(press(MouseLog.TYPE_PRESS_RIGHT, at[0] + 5, at[1] + 5)), "Alt + right-click on a panel is taken");
        at = RspsOverlayLayout.place("boosts", 4, 30, 129, 100, 0, 0, 800, 600);
        check(at[0] == 4 && at[1] == 30 && !RspsOverlayLayout.isMoved("boosts"), "Alt + right-click: back on the default spot");
        RspsOverlayLayout.dropForTest("boosts", 100, 100);
        RspsOverlayLayout.resetAll();
        check(!RspsOverlayLayout.isMoved("boosts"), "reset all positions");
        keys.alt = false;
    }

    // ---- the 78 Plugins sidebar (headless Swing) ----------------------------------------------------------------------

    private static void sidebar() {
        RspsHudConfig.resetToDefaults();
        RspsHud.reset();
        RspsSidebar sidebar = new RspsSidebar(null);
        RspsSidebar.Toggle toggle = (RspsSidebar.Toggle) sidebar.find("toggle.boosts");
        check(toggle != null && toggle.isOn(), "plugin list: Boosts with its switch on");
        toggle.click();
        check(!RspsHudConfig.enabled("boosts") && "hud set boosts.enabled false".equals(RspsHud.drainForTest()), "the switch turns the plugin off here and saves it");
        ((javax.swing.JButton) sidebar.find("config.grounditems")).doClick();
        check("grounditems".equals(sidebar.openPlugin()), "the gear opens Ground Items' configuration");
        @SuppressWarnings("unchecked")
        JComboBox<String> price = (JComboBox<String>) sidebar.find("grounditems.priceDisplayMode");
        price.setSelectedIndex(2);
        check(RspsHudConfig.is("grounditems.priceDisplayMode", "BOTH") && "hud set grounditems.priceDisplayMode BOTH".equals(RspsHud.drainForTest()), "a choice");
        JSpinner low = (JSpinner) sidebar.find("grounditems.lowValuePrice");
        low.setValue(5000);
        check("hud set grounditems.lowValuePrice 5000".equals(RspsHud.drainForTest()), "a number");
        JCheckBox tiles = (JCheckBox) sidebar.find("grounditems.highlightTiles");
        tiles.doClick();
        check(RspsHudConfig.bool("grounditems.highlightTiles") && "hud set grounditems.highlightTiles true".equals(RspsHud.drainForTest()), "a check box");
        check(sidebar.find("grounditems.highValueColor") != null && sidebar.find("grounditems.highlightedItems") != null, "colour buttons and item lists are there");
        RspsHud.handle("hl|clear");
        RspsHud.handle("hl|add|dragon*,shark");
        RspsSidebar.saveList(false, "shark, abyssal whip");
        String first = RspsHud.drainForTest();
        check("hud list highlight add abyssal whip".equals(first), "a list: the differences are sent (last: add): " + first);
        ((javax.swing.JButton) sidebar.find("reset")).doClick();
        check("hud reset grounditems".equals(RspsHud.drainForTest()), "Reset resets this plugin");
        // A server push refreshes the open view.
        RspsHud.handle("cfg|grounditems.highlightTiles=false");
        sidebar.refresh();
        check(!tiles.isSelected(), "a push refreshes the controls");
        ((javax.swing.JButton) sidebar.find("back")).doClick();
        check(sidebar.openPlugin() == null && sidebar.find("toggle.timers") != null, "back to the plugin list");
        RspsHudConfig.resetToDefaults();
    }

    /** 100 players with drops everywhere: 600 stacks of 5 items; the label rebuild (only on a change) must stay cheap. */
    private static void crowdedGround() {
        freshGround();
        StringBuilder gi = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            int x = 3000 + i % 30;
            int z = 3000 + i / 30;
            stack(0, x, z, new int[][]{{4151, 1}, {995, 1000 + i}, {385, 1}, {536, 1}, {385, 1}});
            for (int id : new int[]{4151, 995, 385, 536}) {
                if (gi.length() > 0) {
                    gi.append(';');
                }
                gi.append("0,").append(x).append(',').append(z).append(',').append(id).append(",150,").append(id == 995 ? 1 : 1000).append(",0,0");
            }
        }
        RspsHud.handle("gi|" + gi);
        RspsHudGroundItems.rebuild(NAMES);
        long best = Long.MAX_VALUE;
        for (int run = 0; run < 5; run++) {
            RspsHudGroundItems.markDirty();
            long start = System.nanoTime();
            RspsHudGroundItems.rebuild(NAMES);
            best = Math.min(best, System.nanoTime() - start);
        }
        int count = RspsHudGroundItems.describe().size();
        check(count == 2400, "600 stacks x 4 item kinds (sharks grouped) = 2400 labels: " + count);
        check(best < 30_000_000L, "label rebuild for 3000 ground items under 30 ms: " + best / 1_000_000.0 + " ms");
        long pickedKey = (3000L << 14) | 3000L;
        ((ObjStack) Static497.objStacks.get(pickedKey)).unlink();
        RspsHudGroundItems.markTile(pickedKey);
        stack(0, 3100, 3100, new int[][]{{4151, 1}});
        RspsHudGroundItems.markTile(0, 3100, 3100);
        long start = System.nanoTime();
        RspsHudGroundItems.rebuild(NAMES);
        long tileNanos = System.nanoTime() - start;
        System.out.println("crowded ground: full rebuild " + best / 1000 + " us, one pickup + one drop " + tileNanos / 1000 + " us");
        check(RspsHudGroundItems.describe().size() == 2400 - 4 + 1, "pickup removed its 4 labels, the drop added 1");
        check(tileNanos < 2_000_000L, "a single tile change costs well under 2 ms: " + tileNanos / 1000 + " us");
        freshGround();
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
