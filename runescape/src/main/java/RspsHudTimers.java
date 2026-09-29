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

    private static boolean vengeanceActive;
    /** End and total duration of each timer: vengeance cooldown, teleblock, frozen. */
    private static final long[] ends = {-1L, -1L, -1L};
    private static final long[] totals = {1L, 1L, 1L};

    private RspsHudTimers() {
    }

    static void update(String rest) {
        String[] parts = rest.split("\\|");
        vengeanceActive = parts[0].equals("1");
        long now = RspsHud.now();
        for (int i = 0; i < 3; i++) {
            long end = RspsHud.endTime(Integer.parseInt(parts[i + 1]));
            // A timer that (re)starts - none before, or later than the running one: its full length is from now.
            if (end > 0L && (ends[i] <= now || end > ends[i] + 1_000L)) {
                totals[i] = Math.max(1L, end - now);
            }
            ends[i] = end;
        }
    }

    static void reset() {
        vengeanceActive = false;
        for (int i = 0; i < 3; i++) {
            ends[i] = -1L;
            totals[i] = 1L;
        }
    }

    private static void timer(int index, int sprite) {
        long left = ends[index] - RspsHud.now();
        if (ends[index] <= 0L || left <= 0L) {
            return;
        }
        int colour = left < totals[index] / 10L ? RED : WHITE;
        RspsInfoBoxes.add(sprite, RspsHud.countdown(ends[index]), colour);
    }

    /** Infoboxes of this frame. */
    static void addInfoboxes() {
        if (vengeanceActive && RspsHudConfig.bool("timers.showVengeanceActive")) {
            RspsInfoBoxes.add(RspsSprites.VENGEANCE_OTHER, "", WHITE);
        }
        if (RspsHudConfig.bool("timers.showVengeance")) {
            timer(0, RspsSprites.VENGEANCE);
        }
        if (RspsHudConfig.bool("timers.showTeleblock")) {
            timer(1, RspsSprites.TELE_BLOCK);
        }
        if (RspsHudConfig.bool("timers.showFreezes")) {
            timer(2, RspsSprites.ICE_BARRAGE);
        }
    }

    /** Tests: 0 vengeance cooldown, 1 teleblock, 2 frozen - whether it is running now. */
    static boolean showing(int index) {
        return ends[index] > RspsHud.now();
    }

    static boolean vengeanceActive() {
        return vengeanceActive;
    }
}
