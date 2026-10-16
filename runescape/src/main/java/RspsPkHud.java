import com.jagex.game.runetek6.config.vartype.TimedVarDomain;
import com.jagex.game.runetek6.config.objtype.ObjTypeList;
import com.jagex.graphics.Font;
import com.jagex.graphics.Toolkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bruine Bonen Technologia v5 PK HUD (user 2026-10-12, layout version D: "extra tools to beat a bot"). INFORMATION ONLY -
 * nothing here clicks, picks a target or reacts for the player.
 *
 * <p>One panel in the game (it replaces the v4 status panel and keeps its place, id {@link RspsScriptOverlay#ID}):
 * your target (name, health bar, overhead prayer and the styles it does not protect, weapon + style, freeze), a warning when
 * he holds a special attack weapon, then you (special energy, prayer points, attack timer dots, low HP / prayer warnings)
 * and the last script. A second panel shows the server's fight summary for 20 s. In the prayer tab the protect prayer
 * against the target's weapon gets an outline (you click it yourself).
 *
 * <p>What the target wears and prays comes from his appearance (what every client draws); his freeze and your attack
 * timer from the server (PkHudSync `pkh`), the summary from PkHudSync `pkfight`. The owner's server switch
 * {@code pkhotkeys.allowCombatHints} turns the combat hints off for everyone.
 */
final class RspsPkHud {

    static final String FIGHT_ID = "bbtfight";
    private static final String P = RspsPkHotkeys.ID + ".";
    private static final int TICK_MS = 600;
    private static final long FIGHT_MS = 20000L;
    private static final int SPECIAL_VARP = 300;
    /** Weapon wearpos in the appearance (equipment slot 3). */
    private static final int WEAPON_SLOT = 3;

    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREY = 0xFFA5A5A5;
    private static final int GREEN = 0xFF7FD36B;
    private static final int RED = 0xFFFF5050;
    private static final int ORANGE = 0xFFFF9900;
    private static final int YELLOW = 0xFFFFFF00;
    private static final int[] STYLE_COLOUR = {0xFFFF9900, 0xFF7FD36B, 0xFF66CCFF}; // melee, ranged, magic
    private static final String[] STYLE = {"Melee", "Range", "Mage"};
    static final int MELEE = 0, RANGED = 1, MAGIC = 2;

    // ---- server state (game thread) ------------------------------------------------------------------------------------
    private static int atkLeft, atkTotal, frozenLeft;
    /** Whose freeze [frozenLeft] is: the server opponent's index (Codex v5 review #4), -1 = none. */
    private static int frozenIndex = -1;
    private static long pkhAt;
    private static String[] fight;
    private static long fightAt;

    private RspsPkHud() {
    }

    static void handle(String kind, String rest) {
        String[] f = rest.split("\\|", -1);
        if (kind.equals("pkh")) {
            atkLeft = Integer.parseInt(f[0]);
            int total = Integer.parseInt(f[1]);
            if (total > 0) {
                atkTotal = total; // 0 = ready: the last weapon's dots stay, all green
            }
            frozenLeft = Integer.parseInt(f[2]);
            frozenIndex = f.length > 3 ? Integer.parseInt(f[3]) : -1;
            pkhAt = System.currentTimeMillis();
        } else if (kind.equals("pkfight") && f.length >= 13) {
            RspsPkHotkeys.log("fight summary: " + rest);
            fight = f;
            fightAt = System.currentTimeMillis();
            if (on() && hints() && RspsHudConfig.bool(P + "hudFightSummary")) {
                RspsPkHotkeys.say(summaryLine(f));
            }
        }
    }

    static void reset() {
        atkLeft = 0;
        atkTotal = 0;
        frozenLeft = 0;
        frozenIndex = -1;
        fight = null;
    }

    /** v5: the PK HUD panel is drawn (Opponent Information then leaves player opponents to it). */
    static boolean showing() {
        return on() && RspsHudConfig.bool(P + "showStatusPanel");
    }

    private static boolean on() {
        return RspsHudConfig.enabled(RspsPkHotkeys.ID);
    }

    /** The owner's switch for the combat hints (missing on an older server = off). */
    static boolean hints() {
        return RspsHudConfig.bool(P + "allowCombatHints");
    }

    private static int ticksSince(long at) {
        return (int) ((System.currentTimeMillis() - at) / TICK_MS);
    }

    // ---- what the target shows -------------------------------------------------------------------------------------------

    /** The player the HUD describes: your target, else the server's player opponent. */
    static PlayerEntity target() {
        PlayerEntity p = RspsPkTarget.target();
        if (p != null) {
            return p;
        }
        int index = RspsHudOpponent.playerIndex();
        return index >= 0 && index < PlayerList.highResolutionPlayers.length ? PlayerList.highResolutionPlayers[index] : null;
    }

    /** The target's weapon item id from his appearance, -1 = none. */
    static int weaponOf(PlayerEntity p) {
        if (p == null || p.playerModel == null || p.playerModel.identikit == null || p.playerModel.identikit.length <= WEAPON_SLOT) {
            return -1;
        }
        int kit = p.playerModel.identikit[WEAPON_SLOT];
        return (kit & 0x40000000) != 0 ? kit & 0x3FFFFFFF : -1;
    }

    static String itemName(int id) {
        try {
            String name = ObjTypeList.instance == null ? null : ObjTypeList.instance.list(id).name;
            return name == null || name.equals("null") ? "Item " + id : name;
        } catch (RuntimeException unknown) {
            return "Item " + id;
        }
    }

    /** Style of a weapon from its name (the bots: ancient staff = Mage, rune crossbow = Range, whip = Melee). */
    static int styleOf(int weapon) {
        if (weapon < 0) {
            return MELEE;
        }
        String n = itemName(weapon).toLowerCase(Locale.ROOT);
        if (n.contains("staff") || n.contains("wand") || n.contains("sceptre") || n.contains("trident")) {
            return MAGIC;
        }
        if (n.contains("bow") || n.contains("knife") || n.contains("dart") || n.contains("javelin") || n.contains("thrownaxe")
            || n.contains("throwing axe") || n.contains("chinchompa") || n.contains("ballista") || n.contains("blowpipe")) {
            return RANGED;
        }
        return MELEE;
    }

    private static final String[] SPEC_WEAPONS = {"dragon claws", "armadyl godsword", "granite maul", "dragon dagger",
        "vesta's longsword", "statius's warhammer", "korasi", "dark bow", "dragon warhammer", "morrigan's", "zuriel's staff",
        "volatile nightmare staff", "ballista", "armadyl crossbow", "dragon halberd", "abyssal dagger", "bandos godsword"};

    static boolean specWeapon(int weapon) {
        if (weapon < 0) {
            return false;
        }
        String n = itemName(weapon).toLowerCase(Locale.ROOT);
        for (String s : SPEC_WEAPONS) {
            if (n.contains(s)) {
                return true;
            }
        }
        return false;
    }

    /** Styles the overhead icon protects (server PrayerIcon ids): bit 1 = melee, 2 = ranged, 4 = magic. */
    static int protects(int icon) {
        switch (icon) {
            case 0: case 8: case 12: case 16: return 1;
            case 1: case 9: case 14: case 17: return 2;
            case 2: case 10: case 13: case 18: return 4;
            case 6: return 2 | 4;
            default: return 0;
        }
    }

    static String overheadName(int icon) {
        switch (icon) {
            case -1: return "none";
            case 3: return "Retribution";
            case 4: return "Smite";
            case 5: return "Redemption";
            case 7: case 15: return "Summoning";
            case 19: return "Wrath";
            case 20: return "Soul Split";
            default: {
                int p = protects(icon);
                if (p == (2 | 4)) return "Range+Mage";
                return p == 1 ? "Melee" : p == 2 ? "Range" : p == 4 ? "Mage" : "?";
            }
        }
    }

    // ---- you --------------------------------------------------------------------------------------------------------------

    private static int varp(int id) {
        try {
            return TimedVarDomain.instance == null ? -1 : TimedVarDomain.instance.getVarValueInt(id);
        } catch (RuntimeException unknown) {
            return -1;
        }
    }

    /** Hitpoints left in percent (current / base Hitpoints level), -1 unknown. */
    static int hpPercent() {
        int base = Static498.statBaseLevels[3];
        return base <= 0 ? -1 : Math.max(0, Static581.statLevels[3]) * 100 / base;
    }

    /** Prayer points as the orb shows them (this server's varp holds whole points: 99 = 99), -1 unknown. */
    static int prayerPoints() {
        return varp(RspsPkHotkeys.PRAYER_POINTS_VARP);
    }

    // ---- panel rows -------------------------------------------------------------------------------------------------------

    private static final class Row {
        String left = "";
        int leftColour = WHITE;
        String right = "";
        int rightColour = WHITE;
        int bar = -1;
        int dots = -1;
        int dotsTotal;
        boolean warn;
    }

    private static Row text(String left, int lc, String right, int rc) {
        Row r = new Row();
        r.left = left;
        r.leftColour = lc;
        r.right = right == null ? "" : right;
        r.rightColour = rc;
        return r;
    }

    private static Row warn(String message) {
        Row r = text(message, 0xFFFFD0D0, "", WHITE);
        r.warn = true;
        return r;
    }

    private static List<Row> rows() {
        List<Row> rows = new ArrayList<Row>();
        boolean hints = hints();
        int targetColour = 0xFF000000 | (RspsHudConfig.colour(P + "targetBoxColour") & 0xFFFFFF);
        PlayerEntity t = target();
        if (t == null) {
            rows.add(text("Target: none", targetColour, "", WHITE));
        } else {
            rows.add(text(t.displayName == null ? "?" : t.displayName, targetColour, t.combatLevel > 0 ? "lvl " + t.combatLevel : "", YELLOW));
            int ratio = RspsHudOpponent.playerRatio(t);
            if (ratio >= 0) {
                Row bar = new Row();
                bar.bar = ratio;
                rows.add(bar);
            }
            if (hints) {
                int weapon = weaponOf(t);
                int style = styleOf(weapon);
                if (RspsHudConfig.bool(P + "hudHitWith")) {
                    int prot = protects(t.prayerIcon);
                    StringBuilder hit = new StringBuilder();
                    for (int s = 0; s < 3; s++) {
                        if ((prot & (1 << s)) == 0) {
                            hit.append(hit.length() == 0 ? "" : " / ").append(STYLE[s]);
                        }
                    }
                    rows.add(text("Prays: " + overheadName(t.prayerIcon), WHITE, prot == 0 ? "Hit: any" : "Hit: " + hit, GREEN));
                }
                if (RspsHudConfig.bool(P + "hudWeapon")) {
                    rows.add(text(weapon < 0 ? "Weapon: none" : itemName(weapon), WHITE, STYLE[style], STYLE_COLOUR[style]));
                }
                boolean same = frozenIndex >= 0 && frozenIndex < PlayerList.highResolutionPlayers.length && PlayerList.highResolutionPlayers[frozenIndex] == t;
                int frozen = same ? Math.max(0, frozenLeft - ticksSince(pkhAt)) : 0;
                if (RspsHudConfig.bool(P + "hudTargetFrozen") && frozen > 0) {
                    rows.add(text("Frozen", 0xFF9FD8FF, (frozen * TICK_MS / 1000) + " s", WHITE));
                }
                if (RspsHudConfig.bool(P + "hudSpecWarning") && specWeapon(weapon)) {
                    rows.add(warn("! " + itemName(weapon) + " in hand"));
                }
            }
        }
        // You.
        int energy = varp(SPECIAL_VARP);
        int prayer = prayerPoints();
        Row you = text("Spec " + (energy < 0 ? "?" : energy / 10 + "%") + "  Pray " + (prayer < 0 ? "?" : Integer.toString(prayer)), WHITE, "", WHITE);
        int left = Math.max(0, atkLeft - ticksSince(pkhAt));
        if (hints && RspsHudConfig.bool(P + "hudAttackTimer") && atkTotal > 0) {
            you.dotsTotal = Math.min(8, atkTotal);
            you.dots = Math.max(0, you.dotsTotal - Math.min(you.dotsTotal, left));
        }
        rows.add(you);
        // Codex v5 review #5: the warnings are combat hints too (owner switch).
        int lowHp = hints ? RspsHudConfig.integer(P + "hudLowHp") : 0;
        int hp = hpPercent();
        if (lowHp > 0 && hp >= 0 && hp < lowHp) {
            rows.add(warn("Low HP: " + hp + "% - eat / brew"));
        }
        int base = Static498.statBaseLevels[5];
        if (lowHp > 0 && prayer >= 0 && base > 0 && prayer * 10 < base) {
            rows.add(warn("Low prayer: " + prayer + " - restore"));
        }
        long ago = System.currentTimeMillis() - RspsScriptRunner.lastRunAt;
        if (!RspsScriptRunner.lastRun.isEmpty() && ago <= 60000L) {
            rows.add(text("Last: " + RspsScriptRunner.lastRun, GREY, "", WHITE));
        }
        return rows;
    }

    // ---- drawing (RspsScriptOverlay delegates the status panel here) -----------------------------------------------------

    private static List<Row> frameRows;

    static int height() {
        if (!on() || !RspsHudConfig.bool(P + "showStatusPanel")) {
            frameRows = null;
            return 0;
        }
        frameRows = rows();
        return 2 * RspsHud.PANEL_BORDER + frameRows.size() * (RspsHud.LINE + 2) - 2;
    }

    static int width() {
        if (frameRows == null) {
            return 0;
        }
        int w = 150;
        for (Row r : frameRows) {
            int rw = RspsHud.width(RspsHud.metrics(), r.left) + (r.right.isEmpty() ? 0 : 10 + RspsHud.width(RspsHud.metrics(), r.right))
                + (r.dots >= 0 ? 12 + r.dotsTotal * 8 : 0);
            w = Math.max(w, rw + 2 * RspsHud.PANEL_BORDER);
        }
        return Math.min(w, 230);
    }

    static void draw(int x, int y) {
        if (frameRows == null) {
            return;
        }
        int width = width();
        RspsHud.box(x, y, width, height());
        Font font = RspsHud.font();
        int inner = width - 2 * RspsHud.PANEL_BORDER;
        int lx = x + RspsHud.PANEL_BORDER;
        for (int i = 0; i < frameRows.size(); i++) {
            Row r = frameRows.get(i);
            int top = y + RspsHud.PANEL_BORDER + i * (RspsHud.LINE + 2);
            int baseline = top + RspsHud.LINE - 3;
            if (r.bar >= 0) {
                int fill = inner * Math.min(255, r.bar) / 255;
                Toolkit.active.fillRect(lx, top + 3, inner, RspsHud.LINE - 6, 0xE6660F10);
                Toolkit.active.fillRect(lx, top + 3, fill, RspsHud.LINE - 6, 0xE6009236);
                continue;
            }
            if (r.warn) {
                Toolkit.active.fillRect(lx - 1, top, inner + 2, RspsHud.LINE, 0xC0701010);
            }
            RspsHud.text(font, r.left, lx, baseline, r.leftColour, false);
            int right = x + width - RspsHud.PANEL_BORDER;
            if (r.dots >= 0) {
                for (int d = r.dotsTotal - 1; d >= 0; d--) {
                    int cx = right - 4 - (r.dotsTotal - 1 - d) * 8;
                    Toolkit.active.fillRect(cx - 3, top + 5, 6, 6, d < r.dots ? 0xFF7FD36B : 0xFF555555);
                }
            } else if (!r.right.isEmpty()) {
                RspsHud.text(font, r.right, right - RspsHud.width(RspsHud.metrics(), r.right), baseline, r.rightColour, false);
            }
        }
    }

    // ---- fight summary panel ----------------------------------------------------------------------------------------------

    private static String[] fightLines() {
        String[] f = fight;
        if (f == null || !on() || !hints() || !RspsHudConfig.bool(P + "hudFightSummary") || System.currentTimeMillis() - fightAt > FIGHT_MS) {
            return null;
        }
        int ticks = Integer.parseInt(f[2]);
        int seconds = ticks * TICK_MS / 1000;
        String head = (f[0].equals("WON") ? "Won vs " : f[0].equals("LOST") ? "Lost vs " : "Fight vs ") + f[1] + "  " + seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
        int his = Integer.parseInt(f[7]);
        int right = Integer.parseInt(f[8]);
        List<String> l = new ArrayList<String>();
        l.add(head);
        l.add("Damage dealt / taken: " + f[3] + " / " + f[4]);
        l.add("Your hits into his prayer: " + f[6] + " of " + f[5]);
        l.add("You prayed right: " + (his == 0 ? "-" : right * 100 / his + "% (" + right + " of " + his + ")"));
        l.add("Specs against you: " + f[9]);
        String mistake = mistake(f);
        if (mistake != null) {
            l.add("Biggest mistake: " + mistake);
        }
        return l.toArray(new String[0]);
    }

    private static String mistake(String[] f) {
        int count = Integer.parseInt(f[12]);
        if (count <= 0 || f[11].equals("NONE")) {
            return null;
        }
        String style = f[10].equals("MELEE") ? "melee" : f[10].equals("RANGED") ? "ranged" : "magic";
        String prayer = f[10].equals("MELEE") ? "Melee" : f[10].equals("RANGED") ? "Missiles" : "Magic";
        return f[11].equals("INTO") ? count + "x " + style + " into his Protect " + prayer : count + "x his " + style + " not prayed";
    }

    private static String summaryLine(String[] f) {
        String mistake = mistake(f);
        int his = Integer.parseInt(f[7]);
        return (f[0].equals("WON") ? "Won vs " : f[0].equals("LOST") ? "Lost vs " : "Fight vs ") + f[1] + ": dealt " + f[3] + ", taken " + f[4]
            + ", prayed right " + (his == 0 ? "-" : Integer.parseInt(f[8]) * 100 / his + "%") + (mistake == null ? "." : ". Biggest mistake: " + mistake + ".");
    }

    static int fightHeight() {
        String[] l = fightLines();
        return l == null ? 0 : 2 * RspsHud.PANEL_BORDER + l.length * (RspsHud.LINE + 2) - 2;
    }

    static int fightWidth() {
        String[] l = fightLines();
        if (l == null) {
            return 0;
        }
        int w = RspsHud.PANEL_WIDTH;
        for (String s : l) {
            w = Math.max(w, RspsHud.width(RspsHud.metrics(), s) + 2 * RspsHud.PANEL_BORDER);
        }
        return w;
    }

    static void drawFight(int x, int y) {
        String[] l = fightLines();
        if (l == null) {
            return;
        }
        RspsHud.box(x, y, fightWidth(), fightHeight());
        Font font = RspsHud.font();
        for (int i = 0; i < l.length; i++) {
            int baseline = y + RspsHud.PANEL_BORDER + i * (RspsHud.LINE + 2) + RspsHud.LINE - 3;
            int colour = i == 0 ? (fight[0].equals("WON") ? GREEN : fight[0].equals("LOST") ? RED : ORANGE) : i == l.length - 1 && l[i].startsWith("Biggest") ? 0xFFFFB0B0 : WHITE;
            RspsHud.text(font, l[i], x + RspsHud.PANEL_BORDER, baseline, colour, false);
        }
    }

    // ---- prayer tab outline -----------------------------------------------------------------------------------------------

    private static int cellX, cellY, cellW, cellH;
    private static boolean cellSeen;

    /** InterfaceManager.draw: a prayer grid cell was laid out this frame (slot = grid index). Records the wanted one. */
    static void prayerCell(int slot, int x, int y, int w, int h) {
        if (slot < 0 || slot != wantedSlot) {
            return;
        }
        cellX = x;
        cellY = y;
        cellW = w;
        cellH = h;
        cellSeen = true;
    }

    private static int wantedSlot = -1;

    /** Once per frame before the interfaces: which grid cell to outline (-1 = none). */
    private static void updateWanted() {
        wantedSlot = -1;
        if (!on() || !hints() || !RspsHudConfig.bool(P + "hudPrayHighlight")) {
            return;
        }
        PlayerEntity t = target();
        if (t == null) {
            return;
        }
        int style = styleOf(weaponOf(t));
        boolean curses = RspsPkHotkeys.cursesActive();
        String id = (curses ? "C_DEFLECT_" : "N_PROTECT_FROM_") + (style == MELEE ? "MELEE" : style == RANGED ? "MISSILES" : "MAGIC");
        int i = RspsPkData.prayer(id);
        wantedSlot = i < 0 ? -1 : RspsPkData.PRAYER_SLOT[i];
    }

    /** InterfaceManager.method3833, after the whole interface tree: the outline, then the next frame's wanted cell. */
    static void drawInterfaceOverlay() {
        if (cellSeen && Toolkit.active != null) {
            int colour = 0xFF000000 | (RspsHudConfig.colour(P + "targetBoxColour") & 0xFFFFFF);
            Toolkit.active.outlineRect(cellX - 1, cellY - 1, cellW + 2, cellH + 2, colour);
            Toolkit.active.outlineRect(cellX - 2, cellY - 2, cellW + 4, cellH + 4, colour);
        }
        cellSeen = false;
        updateWanted();
    }
}
