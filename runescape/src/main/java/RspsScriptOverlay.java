import com.jagex.game.runetek6.config.vartype.TimedVarDomain;
import com.jagex.graphics.Font;
import com.jagex.graphics.Toolkit;

/**
 * 78Legacy v4 "Bruine Bonen Technologia" in the game view (information only, never a click):
 * - Target Overlay: a box on the tile of your target (the player you attacked / cast on / picked with "Set target"), its
 *   colour, an optional see-through fill, and the name above it;
 * - status panel (Main Config): target, the last script that ran, special attack energy. One more panel in RspsHud's stack
 *   (Alt + drag moves it, like the others).
 */
final class RspsScriptOverlay {

    static final String ID = "bbtstatus";
    private static final int LINES = 3;
    /** Server AttackTab.SPECIAL_ATTACK_ENERGY_VARP (energy x 10). */
    private static final int SPECIAL_VARP = 300;

    private RspsScriptOverlay() {
    }

    private static boolean on() {
        return RspsHudConfig.enabled(RspsPkHotkeys.ID);
    }

    // ---- target box -------------------------------------------------------------------------------------------------------

    /** RspsHud.render, scene pass (camera still set): the box and name around the target. */
    static void drawTarget(int screenX, int screenY) {
        if (!on() || Toolkit.active == null) {
            return;
        }
        boolean box = RspsHudConfig.bool(RspsPkHotkeys.ID + ".showTargetBox");
        boolean label = RspsHudConfig.bool(RspsPkHotkeys.ID + ".showTargetName");
        if (!box && !label) {
            return;
        }
        PlayerEntity p = RspsPkTarget.target();
        if (p == null || PlayerEntity.self == null || p.level != PlayerEntity.self.level) {
            return;
        }
        int colour = 0xFF000000 | (RspsHudConfig.colour(RspsPkHotkeys.ID + ".targetBoxColour") & 0xFFFFFF);
        int half = 256; // a player is one tile: 512 fine units
        int[][] c = {
            project(p.level, p.x - half, p.z - half, screenX, screenY),
            project(p.level, p.x + half, p.z - half, screenX, screenY),
            project(p.level, p.x + half, p.z + half, screenX, screenY),
            project(p.level, p.x - half, p.z + half, screenX, screenY),
        };
        for (int[] corner : c) {
            if (corner == null) {
                return;
            }
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int[] corner : c) {
            minX = Math.min(minX, corner[0]);
            maxX = Math.max(maxX, corner[0]);
            minY = Math.min(minY, corner[1]);
            maxY = Math.max(maxY, corner[1]);
        }
        if (box) {
            if (RspsHudConfig.bool(RspsPkHotkeys.ID + ".fillTargetBox")) {
                Toolkit.active.fillRect(minX, minY, maxX - minX, maxY - minY, (colour & 0xFFFFFF) | 0x40000000);
            }
            for (int k = 0; k < 4; k++) {
                int[] a = c[k];
                int[] b = c[(k + 1) % 4];
                Toolkit.active.line(a[0], a[1], b[0], b[1], colour, 1);
                Toolkit.active.line(a[0], a[1] + 1, b[0], b[1] + 1, colour, 1);
            }
        }
        if (label) {
            String name = RspsPkTarget.name;
            Font font = RspsHud.font();
            int w = RspsHud.width(RspsHud.metrics(), name);
            RspsHud.text(font, name, (minX + maxX) / 2 - w / 2, minY - 4, colour, true);
        }
    }

    /** Same projection as RspsQuestHelperHints / RspsHudGroundItems: a fine local point at ground height, or null. */
    private static int[] project(int level, int fineX, int fineZ, int screenX, int screenY) {
        Static418.method7860(level, fineZ, 0, fineX, 0);
        if (OverlayManager.hitmarkpos[0] < 0) {
            return null;
        }
        return new int[]{screenX + OverlayManager.hitmarkpos[0], screenY + OverlayManager.hitmarkpos[1]};
    }

    // ---- status panel -------------------------------------------------------------------------------------------------------

    private static String[] lines() {
        String target = RspsPkTarget.slot < 0 || RspsPkTarget.name.isEmpty() ? "none" : RspsPkTarget.name;
        long ago = System.currentTimeMillis() - RspsScriptRunner.lastRunAt;
        String last = RspsScriptRunner.lastRun.isEmpty() || ago > 60000L ? "-" : RspsScriptRunner.lastRun;
        int energy;
        try {
            energy = TimedVarDomain.instance == null ? -1 : TimedVarDomain.instance.getVarValueInt(SPECIAL_VARP) / 10;
        } catch (RuntimeException unknown) {
            energy = -1;
        }
        return new String[]{"Target: " + target, "Last: " + last, "Spec: " + (energy < 0 ? "?" : energy + "%")};
    }

    // v5: the status panel became the PK HUD (RspsPkHud), same place and id; the v4 three-line panel stays below unused.
    static int height() {
        return RspsPkHud.height();
    }

    static int width() {
        return RspsPkHud.width();
    }

    static void draw(int x, int y) {
        RspsPkHud.draw(x, y);
    }

    static int heightV4() {
        if (!on() || !RspsHudConfig.bool(RspsPkHotkeys.ID + ".showStatusPanel")) {
            return 0;
        }
        return 2 * RspsHud.PANEL_BORDER + LINES * RspsHud.LINE + (LINES - 1) * 2;
    }

    static int widthV4() {
        int w = RspsHud.PANEL_WIDTH;
        for (String line : lines()) {
            w = Math.max(w, RspsHud.width(RspsHud.metrics(), line) + 2 * RspsHud.PANEL_BORDER);
        }
        return w;
    }

    static void drawV4(int x, int y) {
        String[] lines = lines();
        int width = widthV4();
        RspsHud.box(x, y, width, heightV4());
        Font font = RspsHud.font();
        int colour = 0xFF000000 | (RspsHudConfig.colour(RspsPkHotkeys.ID + ".targetBoxColour") & 0xFFFFFF);
        for (int i = 0; i < lines.length; i++) {
            int baseline = y + RspsHud.PANEL_BORDER + (i + 1) * RspsHud.LINE + i * 2;
            RspsHud.text(font, lines[i], x + RspsHud.PANEL_BORDER, baseline, i == 0 ? colour : 0xFFFFFFFF, false);
        }
    }
}
