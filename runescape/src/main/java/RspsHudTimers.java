import com.jagex.graphics.Font;

/**
 * Timers & Buffs, PK subset: Vengeance primed, Vengeance cooldown, Teleblock and the local player's own freeze/bind. All
 * four are the server's live state (VENGEANCE_ACTIVE_ATTR and the VENGEANCE_COOLDOWN, TELEBLOCK_TIMER and FROZEN_TIMER
 * timers) resent whenever they change, so a shortened Teleblock, a freeze broken by distance, a death or a relog show
 * exactly what the server has. Vengeance "active" is a state, not a countdown: it lasts until the next hit taken.
 */
final class RspsHudTimers {

    private static final int ACTIVE = 0xFF00FF00;
    private static final int NORMAL = 0xFFFFFFFF;
    private static final int LINE = 15;

    private static boolean vengeanceActive;
    private static long vengeanceCooldownEnd = -1L;
    private static long teleblockEnd = -1L;
    private static long frozenEnd = -1L;

    private RspsHudTimers() {
    }

    static void update(String rest) {
        String[] parts = rest.split("\\|");
        vengeanceActive = parts[0].equals("1");
        vengeanceCooldownEnd = RspsHud.endTime(Integer.parseInt(parts[1]));
        teleblockEnd = RspsHud.endTime(Integer.parseInt(parts[2]));
        frozenEnd = RspsHud.endTime(Integer.parseInt(parts[3]));
    }

    static void reset() {
        vengeanceActive = false;
        vengeanceCooldownEnd = -1L;
        teleblockEnd = -1L;
        frozenEnd = -1L;
    }

    private static boolean running(long end) {
        return end > 0L && end > RspsHud.now();
    }

    static final String ID = "timers";

    /** Panel height for this frame; 0 = nothing to show. */
    static int height() {
        int lines = (vengeanceActive ? 1 : 0) + (running(vengeanceCooldownEnd) ? 1 : 0) + (running(teleblockEnd) ? 1 : 0) + (running(frozenEnd) ? 1 : 0);
        return lines == 0 ? 0 : lines * LINE + 6;
    }

    static void draw(int x, int y, int height) {
        RspsHud.box(x, y, RspsHud.PANEL_WIDTH, height);
        Font font = RspsHud.font();
        int baseline = y + LINE;
        int right = x + RspsHud.PANEL_WIDTH - 4;
        if (vengeanceActive) {
            font.render("Vengeance", x + 4, baseline, RspsHud.SHADOW, NORMAL);
            font.renderRight("ACTIVE", right, baseline, ACTIVE, RspsHud.SHADOW);
            baseline += LINE;
        }
        baseline = line(font, "Vengeance CD", vengeanceCooldownEnd, x, right, baseline);
        baseline = line(font, "Teleblock", teleblockEnd, x, right, baseline);
        line(font, "Frozen", frozenEnd, x, right, baseline);
    }

    private static int line(Font font, String label, long end, int x, int right, int baseline) {
        if (!running(end)) {
            return baseline;
        }
        font.render(label, x + 4, baseline, RspsHud.SHADOW, NORMAL);
        font.renderRight(RspsHud.countdown(end), right, baseline, NORMAL, RspsHud.SHADOW);
        return baseline + LINE;
    }
}
