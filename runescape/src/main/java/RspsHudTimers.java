/**
 * Timers & Buffs, PK subset (RuneLite TimersAndBuffsPlugin: showVengeance, showVengeanceActive, showTeleblock,
 * showFreezes), shown as RuneLite shows timers: infoboxes. Vengeance active is an indicator (the Vengeance Other icon, as
 * RuneLite's VENGEANCE_ACTIVE), the others count down in m:ss, white, bright red for the last 10 % (RuneLite Timer).
 *
 * All four are the server's live state (VENGEANCE_ACTIVE_ATTR and the VENGEANCE_COOLDOWN, TELEBLOCK_TIMER and FROZEN_TIMER
 * timers) resent whenever they change, so a shortened Teleblock, a freeze broken by distance, a death or a relog show
 * exactly what the server has.
 */
final class RspsHudTimers {

    private static final int WHITE = 0xFFFFFFFF;
    /** java.awt.Color.RED.brighter(): RuneLite's colour for the last 10 % of a timer. */
    private static final int RED = 0xFFFF0000;

    /** RuneLite: Antipoison(4) and Anti-venom(4) are the antipoison / antivenom timer images (GameTimer ANTIPOISON, ANTIVENOM). */
    private static final int ANTIPOISON_4 = 2446;
    private static final int ANTI_VENOM_4 = 23247;

    private static boolean vengeanceActive;
    /**
     * End and total duration of each timer: vengeance cooldown, teleblock, frozen, then the server's damage-over-time countdowns:
     * next poison / venom hit, antipoison immunity, antivenom immunity, next burn hit, regular poison wearing off.
     */
    private static final int TIMERS = 8;
    private static final long[] ends = {-1L, -1L, -1L, -1L, -1L, -1L, -1L, -1L};
    private static final long[] totals = {1L, 1L, 1L, 1L, 1L, 1L, 1L, 1L};
    /** 0 none, 1 poison, 2 venom (server state) and the damage of its next hit. */
    private static int dot;
    private static int dotDamage;
    /** Active burn stacks = the damage of the next combined burn hit. */
    private static int burns;

    private RspsHudTimers() {
    }

    static void update(String rest) {
        String[] parts = rest.split("\\|");
        vengeanceActive = parts[0].equals("1");
        long now = RspsHud.now();
        // tmr fields 1-3: cooldown / teleblock / frozen; 6, 7, 8, 10, 11: next poison hit, antipoison, antivenom, next burn hit,
        // poison cured.
        int[] fields = {1, 2, 3, 6, 7, 8, 10, 11};
        for (int i = 0; i < TIMERS; i++) {
            int field = fields[i];
            long end = field < parts.length ? RspsHud.endTime(Integer.parseInt(parts[field])) : -1L;
            // A timer that (re)starts - none before, or later than the running one: its full length is from now.
            if (end > 0L && (ends[i] <= now || end > ends[i] + 1_000L)) {
                totals[i] = Math.max(1L, end - now);
            }
            ends[i] = end;
        }
        dot = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        dotDamage = parts.length > 5 ? Integer.parseInt(parts[5]) : 0;
        burns = parts.length > 9 ? Integer.parseInt(parts[9]) : 0;
    }

    static void reset() {
        vengeanceActive = false;
        dot = 0;
        dotDamage = 0;
        burns = 0;
        for (int i = 0; i < TIMERS; i++) {
            ends[i] = -1L;
            totals[i] = 1L;
        }
    }

    /** RuneLite TimerTimer: m:ss, white, bright red in the last 10 %; the tooltip is the GameTimer description. */
    private static void timer(int index, int sprite, String tooltip) {
        long left = ends[index] - RspsHud.now();
        if (ends[index] <= 0L || left <= 0L) {
            return;
        }
        int colour = left < totals[index] / 10L ? RED : WHITE;
        RspsInfoBoxes.add(sprite, RspsHud.countdown(ends[index]), colour, tooltip);
    }

    /** Infoboxes of this frame. */
    static void addInfoboxes() {
        if (vengeanceActive && RspsHudConfig.bool("timers.showVengeanceActive")) {
            RspsInfoBoxes.add(RspsSprites.VENGEANCE_OTHER, "", WHITE, "Vengeance active");
        }
        if (RspsHudConfig.bool("timers.showVengeance")) {
            timer(0, RspsSprites.VENGEANCE, "Vengeance");
        }
        if (RspsHudConfig.bool("timers.showTeleblock")) {
            timer(1, RspsSprites.TELE_BLOCK, "Teleblock");
        }
        if (RspsHudConfig.bool("timers.showFreezes")) {
            timer(2, RspsSprites.ICE_BARRAGE, "Ice barrage");
        }
        // RuneLite Poison plugin infobox (owner 2026-10-09 180630 "20 0:12"): the splat with the next hit's damage drawn inside it,
        // the time until that hit as the caption, always bright red, and the PoisonPlugin tooltip.
        if (dot != 0 && RspsHudConfig.bool("timers.showPoison")) {
            boolean venom = dot == 2;
            String next = ends[3] > 0L ? RspsHud.countdown(ends[3]) : "0:00";
            String tooltip = "Next " + (venom ? "venom" : "poison") + " damage: <col=ff0000>" + dotDamage + "</col></br>Time until damage: " + next
                + (!venom && ends[7] > 0L ? "</br>Time until cure: " + RspsHud.countdown(ends[7]) : "");
            RspsInfoBoxes.add(venom ? RspsSprites.OSRS_VENOM_SPLAT : RspsSprites.OSRS_POISON_SPLAT, next, RED, tooltip, Integer.toString(dotDamage));
        }
        // RuneLite Timers & Buffs "Burn damage next hit" (the next hit's size), laid out like the poison box (owner 2026-10-09: "check
        // burn venom poison" - the number in the splat, the time to that hit neatly below it).
        if (burns > 0 && RspsHudConfig.bool("timers.showBurn")) {
            String next = ends[6] > 0L ? RspsHud.countdown(ends[6]) : "0:00";
            RspsInfoBoxes.add(RspsSprites.OSRS_BURN_SPLAT, next, WHITE, "Burn damage next hit: <col=ff0000>" + burns + "</col></br>Time until damage: " + next,
                Integer.toString(burns));
        }
        if (RspsHudConfig.bool("timers.showAntiPoison")) {
            // Immunity after an antipoison / antivenom (server POISON_IMMUNITY / VENOM_IMMUNITY), on the potion it came from.
            timer(4, RspsSprites.item(ANTIPOISON_4), "Antipoison");
            timer(5, RspsSprites.item(ANTI_VENOM_4), "Anti-venom");
        }
    }

    /** Tests: the server's poison state (0 none, 1 poison, 2 venom), its next damage, and the burn stacks. */
    static int dot() {
        return dot;
    }

    static int dotDamage() {
        return dotDamage;
    }

    static int burns() {
        return burns;
    }

    /** Tests: 0 vengeance cooldown, 1 teleblock, 2 frozen - whether it is running now. */
    static boolean showing(int index) {
        return ends[index] > RspsHud.now();
    }

    static boolean vengeanceActive() {
        return vengeanceActive;
    }
}
