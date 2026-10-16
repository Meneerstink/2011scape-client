import com.jagex.core.constants.ComponentClientCode;
import com.jagex.core.constants.MainLogicStep;
import com.jagex.core.io.Packet;
import com.jagex.core.util.TimeUtils;
import com.jagex.graphics.Toolkit;
import com.jagex.graphics.ToolkitType;
import com.jagex.game.runetek6.config.iftype.SubInterface;

import java.util.HashMap;
import java.util.Map;

/**
 * Cinematic mode for scripted scenes (owner 2026-10-08, the new-player intro's guard scene: "hide it all" - only the 3D
 * scene, the Deadman zone box and the Quest Tutor's lines on screen, like a Jagex cutscene).
 * <p>
 * The server switches it with the HUD side channel {@code cine|1} / {@code cine|0} (IntroGuardScene). While it is on, the
 * game frame (the top-level interface: side tabs, orbs, minimap, chat tabs, chat messages, tooltips) is not drawn; what
 * stays is the 3D view (clientcode SCENE), the fade windows the server opens over the main screen (115 / 170) and the
 * chatbox's dialogue slot 752:13 - where the Tutor's lines and the Deadman teleport countdown appear. The Deadman HUD box is
 * drawn after the whole interface tree (DeadmanSkullHud.drawOverlay) and so is never hidden (owner: it may never
 * disappear). The mouse-over text and the RSPS overlays (RspsHud) are skipped too.
 * <p>
 * Owner 2026-10-08: the scene in HD. On {@code cine|1} the player's own settings are kept ({@link ClientOptions#encode}),
 * the hardware toolkit is used with the 78 medium preset ({@link RspsGraphicsProfile#apply}, the Graphics menu's "medium") -
 * owner 2026-10-10: "HD, maar niet max", max settings made the intro lag on his laptop; {@code cine|0} puts the kept settings
 * and toolkit back. Both are applied at the client's safe
 * point ({@link #process}, next to RspsGraphicsProfile.processPendingRestore) and the scene values are never saved.
 */
public final class RspsCinematic {

    static volatile boolean active;

    /** The chatbox container and its dialogue slot. */
    private static final int CHATBOX = 752;
    private static final int CHATBOX_DIALOGUE = 13;
    private static final int CHAT_HISTORY = 137;
    /** The world map top level. */
    private static final int WORLD_MAP = 755;
    /** Sub-interfaces the game frame may still show: the chatbox, fade to black, fade from black. */
    private static final int[] FRAME_SUBS = {CHATBOX, 115, 170};

    private static final Map<Integer, Boolean> keep = new HashMap<>();
    private static int keepClock = -1;

    private RspsCinematic() {
    }

    /** The player's own settings while the scene runs, and the pending switch for {@link #process}. */
    private static byte[] kept;
    private static int keptToolkit = -1;
    private static volatile int pending; // 1 = to max, 2 = back

    /**
     * A black curtain over the game view (cine|black): a new account's intro starts the moment he logs in, before the fade
     * to black has run - without it the hall showed for a few frames (owner 2026-10-08). cine|1 lifts it, cine|0 too.
     */
    static volatile boolean curtain;

    static void handle(String rest) {
        curtain = rest.startsWith("black");
        boolean on = rest.startsWith("1") || curtain;
        if (on != active) {
            pending = on ? 1 : 2;
        }
        active = on;
        keep.clear();
    }

    static void reset() {
        curtain = false;
        if (active || kept != null) {
            pending = 2;
        }
        active = false;
        keep.clear();
    }

    /** Game-thread safe point (client.java, beside RspsGraphicsProfile.processPendingRestore). */
    static void process() {
        int step = MainLogicManager.step;
        if (pending == 0 || ClientOptions.instance == null || Toolkit.active == null || !MainLogicStep.isAtGameScreen(step)) {
            return;
        }
        ClientOptions options = ClientOptions.instance;
        int what = pending;
        pending = 0;
        if (what == 1) {
            if (kept == null) {
                Packet own = options.encode();
                kept = java.util.Arrays.copyOf(own.data, own.pos);
                keptToolkit = options.toolkit.getValue();
            }
            int hardware = ToolkitType.isHardware(keptToolkit) ? keptToolkit : ToolkitType.GL;
            options.update(hardware, options.toolkit);
            RspsGraphicsProfile.apply(options);
            Static32.setToolkit(hardware, false);
            Static77.method1561();
        } else if (kept != null) {
            Packet own = new Packet(kept);
            options.decode(own);
            int toolkit = keptToolkit;
            kept = null;
            keptToolkit = -1;
            Static32.setToolkit(toolkit, false);
            Static77.method1561();
            ClientOptions.save();
        }
    }

    /**
     * What a {@link ClientOptions#save()} must write: during the scene the live options carry the 78 MID scene profile, so every
     * save (a wheel zoom, a window-mode switch, ...) wrote that profile as the player's own, and a scene that never reached cine|0
     * (client closed, crash, logout) left the player on MID (owner D3 round 2, 2026-10-16: "keep the player's own choice"). The
     * player's kept settings are written instead, with the live window / zoom trailer (those stay the player's while the scene runs).
     */
    static ClientOptions playerSettings(ClientOptions live) {
        byte[] own = kept;
        if (own == null || live != ClientOptions.instance) {
            return live;
        }
        ClientOptions player = new ClientOptions(new Packet(own), live.getModeGame(), keptToolkit, live.getEnvironment());
        player.preferredWindowMode = live.preferredWindowMode;
        player.windowWidth = live.windowWidth;
        player.windowHeight = live.windowHeight;
        player.cameraZoom = live.cameraZoom;
        player.windowMaximised = live.windowMaximised;
        return player;
    }

    /** InterfaceManager.draw: true when [child] is not drawn this frame. */
    static boolean hide(Component child) {
        if (!active || child == null || InterfaceManager.topLevelInterface == WORLD_MAP) {
            // The guard scene's world map is shown in scene mode (owner 2026-10-10): leaving it (cine|0) switched the client
            // back to the player's own graphics and the map stood on "Loading - please wait" for 8 of its 10 seconds.
            return false;
        }
        int iface = child.slot >>> 16;
        if (iface != InterfaceManager.topLevelInterface && iface != CHATBOX) {
            // Everything inside a kept host (a dialogue, a fade) is drawn as normal.
            return false;
        }
        if (keepClock != TimeUtils.clock) {
            keepClock = TimeUtils.clock;
            keep.clear();
        }
        return !keeps(child, iface, 0);
    }

    /** A frame component stays when it is the 3D view, hosts an allowed sub-interface, or contains one of those. */
    private static boolean keeps(Component c, int iface, int depth) {
        Boolean cached = keep.get(c.slot);
        if (cached != null) {
            return cached;
        }
        boolean result = false;
        if (c.clientcode == ComponentClientCode.SCENE) {
            result = true;
        } else if (iface == CHATBOX) {
            // Owner 2026-10-08: the chatbox is gone during the scene and only comes up while something is said in its dialogue
            // slot (the Quest Tutor, the teleport countdown) - then with its background; the chat history (137) and the input
            // line stay hidden.
            boolean talking = InterfaceManager.subInterfaces.get(((long) CHATBOX << 16) | CHATBOX_DIALOGUE) != null;
            SubInterface hosted = (SubInterface) InterfaceManager.subInterfaces.get(c.slot);
            result = talking && ((c.slot & 0xFFFF) == CHATBOX_DIALOGUE || (c.type != Component.TYPE_TEXT && (hosted == null || hosted.id != CHAT_HISTORY)));
        } else {
            SubInterface sub = (SubInterface) InterfaceManager.subInterfaces.get(c.slot);
            if (sub != null) {
                for (int id : FRAME_SUBS) {
                    result |= sub.id == id;
                }
            }
        }
        if (!result && depth < 12 && c.type == Component.TYPE_LAYER) {
            Component[] all = InterfaceList.interfaces != null && iface < InterfaceList.interfaces.length ? InterfaceList.interfaces[iface] : null;
            if (all != null) {
                for (Component other : all) {
                    if (other != null && other != c && other.layer == c.slot && keeps(other, iface, depth + 1)) {
                        result = true;
                        break;
                    }
                }
            }
            if (!result && c.dynamicComponents != null) {
                for (Component other : c.dynamicComponents) {
                    if (other != null && keeps(other, iface, depth + 1)) {
                        result = true;
                        break;
                    }
                }
            }
        }
        keep.put(c.slot, result);
        return result;
    }
}
