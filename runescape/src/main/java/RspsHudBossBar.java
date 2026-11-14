import com.jagex.graphics.Font;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.Sprite;
import com.jagex.graphics.Toolkit;

/**
 * N606 (13-11): the OSRS boss HP HUD - OSRS interface 303 hpbar_hud, drawn by the client itself from the server's `hpbar|...` payload
 * (server BossHpHud / ClientHudSync). OSRS shows it during every Tombs of Amascut boss fight (Kaoz Entry guide 05-09-2022,
 * screens/N606/hpbar-osrs/strips-*.png).
 *
 * Exact OSRS sources (cache 2686 interface 303, decoded with the N462 probe -> work/claude-scripts/toa-patch/osrs-if-303.json, and
 * Joshua-F/osrs-dumps@5cdf0f47 scripts hp_hud_init 2099 / hp_hud_pos 2101 / hp_hud_update 2103 / hp_hud_update_bar 1414 /
 * hp_hud_open 2377 / hp_hud_fade_in 2888 / hp_hud_fade_out 2890):
 *  - width = minmax(200, 600, game view width / 4) (boss, not compact), centred; height 42 with a name, 27 without;
 *  - 303:6 outer border: unfilled 0x191411 at (1,1) size (w-2, h-2); 303:7 tradebacking_dark (sprite 897 - byte-identical in our
 *    667 cache) tiled at (2,2) size (w-4, h-4);
 *  - 303:8/9 name: p11_full, 0xff981f, shadow, centred in (8, 2, w-16, 19);
 *  - 303:10 bar (3, h-24, w-6, 21): 303:11 border 0x191411 at (1,1) (w-8, 19); 303:12 inner (2,2) (w-10, 17) with 303:13 back
 *    0xCC0000, 303:14 sliding 0x009500 and 303:15 remaining 0x00CC00 from the left;
 *  - 303:20 text "hp / max": p11_full 0xEAEAEA, shadow, centred in the bar, 1 px down;
 *  - remaining = scale(hp, max, barWidth), at least 1 while hp > 0, at most barWidth - 1 while hp < max; a loss leaves the sliding
 *    part where it was for 20 client cycles, then it shrinks 1 px per client cycle (20 ms); a gain grows the bar 1 px per cycle;
 *  - fade: transparency 255 -> 0 in steps of 3 per client cycle on open, 0 -> 255 on close.
 * The cyan (Kephri's shield, Het's Seal) and yellow (a P2 Warden's damage bar) colours are not in the cache (the OSRS server sets
 * them); they are the colours measured in the Kaoz video, corrected by the video's loss on the known green/red (0x00CC00 -> #00c602).
 * The top offset (26 px) is the box's position in that video. RuneLite's Opponent Information hides its own panel for the npc this
 * HUD shows (OpponentInfoOverlay.hasHpHud) - see {@link #shows}.
 */
final class RspsHudBossBar {
    private static final int BORDER = 0x191411;
    private static final int NAME = 0xFF981F;
    private static final int TEXT = 0xEAEAEA;
    private static final int TOP = 26;
    private static final int BACKING_SPRITE = 897;
    /** Per style: back, sliding, remaining (OSRS 303:13/14/15 for STANDARD; video-measured for SHIELD and DAMAGE). */
    private static final int[][] COLOURS = {
        {0xCC0000, 0x009500, 0x00CC00},
        {0x002020, 0x00D4A9, 0x00D4A9},
        {0x282600, 0xD9D600, 0xD9D600},
    };
    private static final long CYCLE_MS = 20L;

    private static int npcIndex = -1;
    private static int style;
    private static int hp;
    private static int max = 1;
    private static String name = "";
    private static boolean open;

    private static int remaining = -1;
    private static int sliding = -1;
    private static int barWidth = -1;
    private static long slideAfter;
    private static int transparency = 255;
    private static long lastStep;

    private RspsHudBossBar() {
    }

    static void update(String rest) {
        if (rest.equals("none")) {
            open = false;
            return;
        }
        String[] p = rest.split("\\|", 5);
        int index = Integer.parseInt(p[0]);
        boolean fresh = !open || index != npcIndex;
        npcIndex = index;
        style = Math.max(0, Math.min(COLOURS.length - 1, Integer.parseInt(p[1])));
        int oldHp = hp;
        hp = Math.max(0, Integer.parseInt(p[2]));
        max = Math.max(1, Integer.parseInt(p[3]));
        name = p.length > 4 ? p[4] : "";
        if (fresh) {
            // hp_hud_open: a new bar starts at its value (hp_hud_update ..., true) and fades in.
            remaining = -1;
            sliding = -1;
            if (!open) {
                transparency = 255;
            }
        } else if (hp < oldHp) {
            slideAfter = System.currentTimeMillis() + 20L * CYCLE_MS;
        }
        open = true;
    }

    /** N610 ToA Extended: Varbits.BOSS_HEALTH_CURRENT / BOSS_HEALTH_MAXIMUM while the HUD is open. */
    static boolean isOpen() {
        return open;
    }

    static int currentHp() {
        return hp;
    }

    static int maxHp() {
        return max;
    }

    static void reset() {
        open = false;
        npcIndex = -1;
        transparency = 255;
        remaining = -1;
        sliding = -1;
    }

    /** RuneLite OpponentInfoOverlay.hasHpHud: our Opponent Information hides for the npc this HUD shows. */
    static boolean shows(int index) {
        return open && index == npcIndex;
    }

    private static int target(int width) {
        int filled = (int) ((long) Math.min(hp, max) * width / max);
        if (filled == 0 && hp > 0) {
            filled = 1;
        }
        if (filled == width && hp < max) {
            filled--;
        }
        return filled;
    }

    /** Called every frame from {@link RspsHud#render} with the game view. */
    static void render(int viewX, int viewY, int viewWidth, int viewHeight) {
        Toolkit toolkit = Toolkit.active;
        if (toolkit == null || npcIndex < 0) {
            return;
        }
        long now = System.currentTimeMillis();
        int steps = lastStep == 0L ? 1 : (int) Math.min(255L, (now - lastStep) / CYCLE_MS);
        if (steps > 0) {
            lastStep = now;
        }
        transparency = open ? Math.max(0, transparency - 3 * steps) : Math.min(255, transparency + 3 * steps);
        if (!open && transparency >= 255) {
            npcIndex = -1;
            return;
        }

        int width = Math.max(200, Math.min(600, viewWidth / 4));
        boolean named = name.length() > 0;
        int height = named ? 42 : 27;
        int x = viewX + (viewWidth - width) / 2;
        int y = viewY + TOP;
        int inner = width - 10;

        int goal = target(inner);
        if (barWidth != inner || remaining < 0) {
            barWidth = inner;
            remaining = goal;
            sliding = goal;
        } else if (goal < remaining) {
            remaining = goal;
            if (sliding < remaining) {
                sliding = remaining;
            }
        } else if (goal > remaining && steps > 0) {
            remaining = Math.min(goal, remaining + steps);
            sliding = Math.max(sliding, remaining);
        }
        if (sliding > remaining && now >= slideAfter && steps > 0) {
            sliding = Math.max(remaining, sliding - steps);
        }

        int alpha = 255 - transparency;
        int a = alpha << 24;
        int mode = transparency == 0 ? 0 : 1;
        int[] colours = COLOURS[style];

        toolkit.outlineRect(x + 1, y + 1, width - 2, height - 2, a | BORDER, mode);
        Sprite backing = RspsSprites.sprite(BACKING_SPRITE);
        if (backing != null) {
            int[] clip = new int[4];
            toolkit.K(clip);
            toolkit.T(x + 2, y + 2, x + width - 2, y + height - 2);
            if (transparency == 0) {
                backing.renderTiled(x + 2, y + 2, width - 4, height - 4);
            } else {
                backing.renderTiled(x + 2, y + 2, width - 4, height - 4, 0, a | 0xFFFFFF, 1);
            }
            toolkit.KA(clip[0], clip[1], clip[2], clip[3]);
        }
        Font font = Fonts.p11 != null ? Fonts.p11 : RspsHud.font();
        if (named && font != null) {
            font.renderLines(name, x + 8, y + 2, 0, 0, width - 16, 19, 1, 1, 0, 0, a | NAME, a, null, null, null);
        }
        int barX = x + 3;
        int barY = y + height - 24;
        toolkit.outlineRect(barX + 1, barY + 1, width - 8, 19, a | BORDER, mode);
        int innerX = barX + 2;
        int innerY = barY + 2;
        toolkit.aa(innerX, innerY, inner, 17, a | colours[0], mode);
        if (sliding > 0) {
            toolkit.aa(innerX, innerY, sliding, 17, a | colours[1], mode);
        }
        if (remaining > 0) {
            toolkit.aa(innerX, innerY, remaining, 17, a | colours[2], mode);
        }
        if (font != null) {
            String text = hp + " / " + max;
            font.renderLines(text, barX, barY + 1, 0, 0, width - 6, 21, 1, 1, 0, 0, a | TEXT, a, null, null, null);
        }
    }
}
