import com.jagex.core.constants.WindowMode;

/**
 * Transparent or opaque chatbox in resizable and fullscreen (owner 2026-10-01: "Herstel een werkende transparent / opaque
 * chatbox keuze voor Resizable").
 *
 * The 667 cache already carries both looks, chosen by the window mode inside its own chat scripts:
 * <ul>
 * <li>transparent (resizable/fullscreen): 752:1 = sprite 1247 (black, per-pixel alpha), 752:2 frame hidden, 137:51 hidden,
 * 137:52/53 shown, bright chat colours with a text shadow;</li>
 * <li>opaque (fixed): 752:1 = sprite 1017 (the parchment), 752:2 frame shown, 137:51 shown, 137:52/53 hidden, the dark
 * fixed-mode chat colours without shadow.</li>
 * </ul>
 * Decoded 2026-10-01 from CS2 1362 (chat init), 1364 (chat tab/pane refresh), 84 (chat lines: colour tags + shadow),
 * 1558 (input line colours) and 1301 (chat scrollbar sprites). The chat-look scripts {@link #LOOK_SCRIPTS} branch only on
 * GETWINDOWMODE for that look, so with "opaque" chosen they are told "fixed" and draw the cache's own fixed look. Script
 * 1364 picks the background by quick chat (137:0 shown = parchment) and also runs resizable-only layout (the hidden chat of
 * varc 41 = -1), so it is not told "fixed"; instead the background it leaves on 752:1 is switched to the fixed look
 * afterwards ({@link #enforce()}) - exactly the values CS2 1362 sets in fixed mode.
 *
 * The choice is the "Chatbox" entry of the 78 Plugins sidebar (item "Transparent chatbox"), saved with the account by the
 * server (ClientHudConfig), so it survives relogs, client restarts and display-mode switches.
 */
final class RspsChatbox {

    private static final int CHAT_PANE = 752;
    private static final int CHATBOX = 137;
    /** 752:1 - the chat background graphic. */
    private static final int BACKGROUND = (CHAT_PANE << 16) | 1;
    /** 752:2 - the 9-slice frame around it (CS2 1087/1088). */
    private static final int FRAME = (CHAT_PANE << 16) | 2;
    private static final int DIVIDER_FIXED = (CHATBOX << 16) | 51;
    private static final int DIVIDER_TRANSPARENT_A = (CHATBOX << 16) | 52;
    private static final int DIVIDER_TRANSPARENT_B = (CHATBOX << 16) | 53;
    private static final int INPUT = (CHATBOX << 16) | 56;

    static final int SPRITE_TRANSPARENT = 1247;
    static final int SPRITE_OPAQUE = 1017;

    /** CS2 that choose the chat colours / shadows / scrollbar by window mode and nothing else. */
    private static final int[] LOOK_SCRIPTS = {84, 1301, 1362, 1558};
    /** CS2 that write the chat background 752:1 (1050-1052, 1069 quick chat; 1186/1188 dialogues; 1362/1364 chat). */
    private static final int[] BACKGROUND_SCRIPTS = {1050, 1051, 1052, 1069, 1186, 1188, 1362, 1364};
    /** CS2 1364: the chat pane refresh, run again when the choice or the window mode changes. */
    private static final int REFRESH_SCRIPT = 1364;
    /** varc 41: the selected chat tab, -1 while the chat is hidden (clicking the selected tab in resizable). */
    private static final int VARC_CHAT_TAB = 41;

    private static int appliedKey = Integer.MIN_VALUE;

    private RspsChatbox() {
    }

    static boolean opaque() {
        return RspsHudConfig.enabled("chatbox") && !RspsHudConfig.bool("chatbox.transparent");
    }

    private static boolean contains(int[] ids, int id) {
        for (int i : ids) {
            if (i == id) {
                return true;
            }
        }
        return false;
    }

    /** GETWINDOWMODE as the running script sees it. */
    static int windowModeFor(int scriptId, int mode) {
        if (mode >= WindowMode.RESIZABLE && contains(LOOK_SCRIPTS, scriptId) && opaque()) {
            return WindowMode.FIXED;
        }
        return mode;
    }

    static boolean touchesBackground(int scriptId) {
        return contains(BACKGROUND_SCRIPTS, scriptId);
    }

    private static Component loaded(int hash) {
        Component[][] all = InterfaceList.interfaces;
        int id = hash >> 16;
        if (all == null || id >= all.length || all[id] == null) {
            return null;
        }
        int slot = hash & 0xFFFF;
        return slot < all[id].length ? all[id][slot] : null;
    }

    private static void hide(int hash, boolean hidden) {
        Component c = loaded(hash);
        if (c != null && c.hidden != hidden) {
            c.hidden = hidden;
            InterfaceManager.redraw(c);
        }
    }

    /**
     * After a background script: in resizable/fullscreen with "opaque" chosen, the transparent chat background becomes the
     * fixed-mode one. Dialogue backgrounds (1204/1205) and a hidden chat (varc 41 = -1) are left as the cache draws them.
     */
    static void enforce() {
        if (!opaque() || InterfaceManager.getWindowMode() < WindowMode.RESIZABLE) {
            return;
        }
        Component background = loaded(BACKGROUND);
        if (background == null || background.graphic != SPRITE_TRANSPARENT || background.hidden) {
            return;
        }
        if (Static511.varcs != null && Static511.varcs[VARC_CHAT_TAB] == -1) {
            return;
        }
        background.graphic = SPRITE_OPAQUE;
        background.alpha = false;
        background.tiling = false;
        InterfaceManager.redraw(background);
        hide(FRAME, false);
        hide(DIVIDER_FIXED, false);
        hide(DIVIDER_TRANSPARENT_A, true);
        hide(DIVIDER_TRANSPARENT_B, true);
        Component input = loaded(INPUT);
        if (input != null && input.textShadow) {
            input.textShadow = false;
            InterfaceManager.redraw(input);
        }
    }

    /**
     * Every game tick: when the choice (sidebar, login push) or the window mode changed since the chat was last drawn, the
     * chat pane is refreshed through the cache's own script so every line, colour and background follows at once.
     */
    static void tick() {
        int mode = InterfaceManager.getWindowMode();
        int key = mode * 2 + (opaque() ? 1 : 0);
        if (key == appliedKey) {
            return;
        }
        if (loaded(BACKGROUND) == null || ClientScriptList.list(REFRESH_SCRIPT) == null) {
            return;
        }
        appliedKey = key;
        HookRequest hook = new HookRequest();
        hook.arguments = new Object[] {REFRESH_SCRIPT};
        ScriptRunner.executeHookInner(hook);
        enforce();
    }

    /** New login / world hop: the chat is rebuilt from scratch, so the next tick applies the choice again. */
    static void reset() {
        appliedKey = Integer.MIN_VALUE;
    }
}
