import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;

import rs2.client.event.keyboard.KeyLog;

/**
 * RSPS developer mode: the server's `devmode` command sends the console marker "__RSPS_DEV__:on|off". While it is on,
 * every loc (also option-less scenery), npc and ground item shows its id on right-click, and extra entries edit the
 * world live through the server's world-edit commands:
 * <ul>
 * <li>loc: Tag, Remove, Rotate, Pick, Teleport from, Walk-through on/off, Remove teleport, and on a wall "Hang 78
 * banner" / "Hang 78 small" (on the side you stand), "78 logo / wide banner / flag here" (place mode on that wall),
 * "Adjust" / "Copy & place" (place mode on a placed decoration);</li>
 * <li>npc: Tag, Remove, Rotate, Pick, Rename, Walk radius;</li>
 * <li>ground: Tag tile, 78 banner, 78 standard, 78 small, 78 logo / wide banner / flag here, and after a Pick
 * "Move ... here" / "Copy ... here".</li>
 * </ul>
 * Place mode (owner 2026-10-06): while the server has a decoration selected for this player (marker
 * "__RSPS_DEV__:deco|on|...") the keys below adjust it live and {@link #drawOverlay} shows the current values; every key
 * becomes a `devdeco` command, the server owns the state and sends the preview only to this client.
 */
public final class RspsDev {

    public static final String PREFIX = "__RSPS_DEV__:";

    public static boolean enabled = false;

    /** Custom mini-menu actions (below the 2000 low-priority offset, unused by the 667 client). */
    public static final int ACTION_TAG_LOC = 1900;

    public static final int ACTION_TAG_TILE = 1901;

    public static final int ACTION_REMOVE_LOC = 1902;

    public static final int ACTION_ROTATE_LOC = 1903;

    public static final int ACTION_BANNER_TILE = 1904;

    public static final int ACTION_STANDARD_TILE = 1905;

    public static final int ACTION_PICK_LOC = 1906;

    public static final int ACTION_NPC_REMOVE = 1907;

    public static final int ACTION_NPC_ROTATE = 1908;

    public static final int ACTION_NPC_PICK = 1909;

    public static final int ACTION_NPC_TAG = 1910;

    public static final int ACTION_MOVE_HERE = 1911;

    public static final int ACTION_COPY_HERE = 1912;

    public static final int ACTION_INFO_LOC = 1913;

    public static final int ACTION_INFO_NPC = 1914;

    public static final int ACTION_TELE_TILE = 1915;

    /** Nudge a loc one tile north/east/south/west (1916-1919) and an npc likewise (1920-1923). */
    public static final int ACTION_NUDGE_LOC = 1916;

    public static final int ACTION_NUDGE_NPC = 1920;

    public static final int ACTION_SMALL_TILE = 1924;

    /** On a wall: hang the 78 banner / small banner on the side of the wall the player stands on (server picks tile and rotation). */
    public static final int ACTION_HANG_BANNER = 1925;

    public static final int ACTION_HANG_SMALL = 1926;

    /** Npc: rename (server asks the name and "this one / every one"), set walk radius (server asks the tiles). */
    public static final int ACTION_NPC_RENAME = 1927;

    public static final int ACTION_NPC_RADIUS = 1928;

    /** Loc: pick as teleport start; then ground "Link teleport here". */
    public static final int ACTION_TELE_FROM = 1929;

    public static final int ACTION_LINK_TELE = 1930;

    /** Loc: toggle walk-through (stays visible); remove its teleport. */
    public static final int ACTION_PASSABLE = 1931;

    public static final int ACTION_UNTELE = 1932;

    /** Place mode on a wall (loc actions 1933-1935: logo, wide banner, flag) and on a bare tile (1936-1938). */
    public static final int ACTION_PLACE_WALL = 1933;

    public static final int ACTION_PLACE_TILE = 1936;

    /** Place mode on an existing decoration: adjust it, or save a copy and adjust that. */
    public static final int ACTION_ADJUST_LOC = 1939;

    public static final int ACTION_COPY_DECO = 1940;

    /** pickKind of a loc picked as teleport start. */
    public static final int PICK_TELE = 3;

    public static final String[] NUDGE_NAMES = {"north", "east", "south", "west"};

    private static final int[] NUDGE_DX = {0, 1, 0, -1};

    private static final int[] NUDGE_DZ = {1, 0, -1, 0};

    /** 78 house banner locs (server world_edits.plugin.kts). */
    public static final int BANNER_78 = 62751;

    public static final int STANDARD_78 = 62750;

    /** Small bank-wall banner (HouseBannerTool smallLoc). */
    public static final int SMALL_78 = 62753;

    /** Legacy Deadman artwork locs (Branding78Tool, tools/78-branding-approved/derived/branding78_ids.properties). */
    public static final int LOGO_78 = 62908;

    public static final int WIDE_BANNER_78 = 62909;

    public static final int FLAG_78 = 62910;

    public static final int[] PLACE_LOCS = {LOGO_78, WIDE_BANNER_78, FLAG_78};

    public static final String[] PLACE_NAMES = {"78 logo", "78 wide banner", "78 flag"};

    /**
     * Level of each loc offered in the current menu, keyed by its entry v1. A loc can stand on another level than the
     * player (a tree canopy, an upper floor, a roof), and the edit must name the loc's own level.
     */
    public static final java.util.HashMap<Long, Integer> LOC_LEVELS = new java.util.HashMap<>();

    public static int locLevel(long v1, int fallback) {
        Integer level = LOC_LEVELS.get(v1);
        return level == null ? fallback : level;
    }

    /** The picked thing for Move/Copy: 0 nothing, 1 loc, 2 npc. */
    public static int pickKind = 0;

    public static int pickId;

    public static int pickX;

    public static int pickZ;

    public static int pickLevel;

    public static String pickName = "";

    /** Place mode: the server has a decoration selected for this player; {@link #status} are its overlay lines. */
    public static boolean placing = false;

    public static String[] status = new String[0];

    private static final String[] KEY_HELP = {
        "Left/Right along the wall   Up/Down height   PgUp/PgDn distance to the wall",
        "+/- size   Q/E angle   R quarter turn   F other side of the wall   T fine/coarse step",
        "Enter save   Esc cancel   C copy   Del remove   U undo last",
    };

    /** Jagex key codes (SimpleKeyboardMonitor.KEY_MAP). */
    private static final int KEY_ESC = 13;
    private static final int KEY_TAB = 80;
    private static final int KEY_ENTER = 84;
    private static final int KEY_LEFT = 96;
    private static final int KEY_RIGHT = 97;
    private static final int KEY_UP = 98;
    private static final int KEY_DOWN = 99;
    private static final int KEY_DELETE = 101;
    private static final int KEY_PAGE_UP = 104;
    private static final int KEY_PAGE_DOWN = 105;

    private RspsDev() {
    }

    public static void handle(String value) {
        if (value.startsWith("deco|")) {
            String[] parts = value.split("\\|", -1);
            if (parts.length >= 2 && parts[1].equals("on")) {
                placing = true;
                String[] lines = new String[parts.length - 2];
                System.arraycopy(parts, 2, lines, 0, lines.length);
                status = lines;
            } else {
                placing = false;
                status = new String[0];
            }
            return;
        }
        enabled = value.equals("on");
        if (!enabled) {
            pickKind = 0;
            placing = false;
            status = new String[0];
        }
        debugconsole.addline("Developer mode " + (enabled ? "on" : "off"));
    }

    public static String tag(int id) {
        return enabled ? " <col=ffffff>(" + id + ")" : "";
    }

    public static String tag(int id, int x, int z, int level) {
        return enabled ? " <col=ffffff>(" + id + " @ " + x + "," + z + "," + level + ")" : "";
    }

    public static boolean isDevAction(int action) {
        return action >= ACTION_TAG_LOC && action <= ACTION_COPY_DECO;
    }

    public static boolean isNpcAction(int action) {
        return (action >= ACTION_NPC_REMOVE && action <= ACTION_NPC_TAG) || action == ACTION_INFO_NPC || action == ACTION_NPC_RENAME || action == ACTION_NPC_RADIUS || (action >= ACTION_NUDGE_NPC && action <= ACTION_NUDGE_NPC + 3);
    }

    public static boolean isLocAction(int action) {
        return action == ACTION_TAG_LOC || action == ACTION_REMOVE_LOC || action == ACTION_ROTATE_LOC || action == ACTION_PICK_LOC || action == ACTION_INFO_LOC || action == ACTION_HANG_BANNER || action == ACTION_HANG_SMALL || action == ACTION_TELE_FROM || action == ACTION_PASSABLE || action == ACTION_UNTELE || (action >= ACTION_NUDGE_LOC && action <= ACTION_NUDGE_LOC + 3)
            || (action >= ACTION_PLACE_WALL && action <= ACTION_PLACE_WALL + 2) || action == ACTION_ADJUST_LOC || action == ACTION_COPY_DECO;
    }

    /** A loc or tile action at world tile x,z,level (locId -1 for a bare tile). */
    public static void perform(int action, int x, int z, int level, int locId, String locName) {
        String at = x + " " + z + " " + level;
        switch (action) {
            case ACTION_TAG_LOC:
            case ACTION_TAG_TILE:
                send("devtag " + at + " " + locId);
                break;
            case ACTION_REMOVE_LOC:
                send("devremove " + at + " " + locId);
                break;
            case ACTION_ROTATE_LOC:
                send("devrotate " + at + " " + locId);
                break;
            case ACTION_PICK_LOC:
                pick(1, locId, x, z, level, locName);
                break;
            case ACTION_INFO_LOC:
                send("devinfo " + at + " " + locId);
                break;
            case ACTION_TELE_TILE:
                send("tele " + at);
                break;
            case ACTION_BANNER_TILE:
                send("devplace " + at + " " + BANNER_78);
                break;
            case ACTION_STANDARD_TILE:
                send("devplace " + at + " " + STANDARD_78);
                break;
            case ACTION_SMALL_TILE:
                send("devplace " + at + " " + SMALL_78);
                break;
            case ACTION_HANG_BANNER:
                send("devhang " + at + " " + locId + " " + BANNER_78);
                break;
            case ACTION_HANG_SMALL:
                send("devhang " + at + " " + locId + " " + SMALL_78);
                break;
            case ACTION_TELE_FROM:
                pick(PICK_TELE, locId, x, z, level, locName);
                break;
            case ACTION_LINK_TELE:
                if (pickKind == PICK_TELE) {
                    send("devtele " + pickX + " " + pickZ + " " + pickLevel + " " + pickId + " " + at);
                    pickKind = 0;
                }
                break;
            case ACTION_PASSABLE:
                send("devpassable " + at + " " + locId);
                break;
            case ACTION_UNTELE:
                send("devuntele " + at + " " + locId);
                break;
            case ACTION_ADJUST_LOC:
                send("devdeco adjust " + at + " " + locId);
                break;
            case ACTION_COPY_DECO:
                send("devdeco copy " + at + " " + locId);
                break;
            case ACTION_MOVE_HERE:
            case ACTION_COPY_HERE:
                if (pickKind == 0) {
                    break;
                }
                boolean move = action == ACTION_MOVE_HERE;
                String from = pickX + " " + pickZ + " " + pickLevel + " " + pickId;
                if (pickKind == 1) {
                    send((move ? "devmoveloc " : "devcopyloc ") + from + " " + at);
                } else {
                    send("devnpc " + (move ? "move " : "copy ") + from + " " + at);
                }
                if (move) {
                    // The picked thing now stands on the new tile; a further Move takes it from there.
                    pickX = x;
                    pickZ = z;
                    pickLevel = level;
                }
                break;
            default:
                if (action >= ACTION_NUDGE_LOC && action <= ACTION_NUDGE_LOC + 3) {
                    int d = action - ACTION_NUDGE_LOC;
                    send("devmoveloc " + at + " " + locId + " " + (x + NUDGE_DX[d]) + " " + (z + NUDGE_DZ[d]) + " " + level);
                } else if (action >= ACTION_PLACE_WALL && action <= ACTION_PLACE_WALL + 2) {
                    // On the wall the player right-clicked (the server picks the side he stands on; F flips it).
                    send("devdeco start " + PLACE_LOCS[action - ACTION_PLACE_WALL] + " " + at + " " + locId);
                } else if (action >= ACTION_PLACE_TILE && action <= ACTION_PLACE_TILE + 2) {
                    send("devdeco start " + PLACE_LOCS[action - ACTION_PLACE_TILE] + " " + at);
                }
                break;
        }
    }

    /** An npc action on the npc with base type [id] standing at world tile x,z,level. */
    public static void performNpc(int action, int x, int z, int level, int id, String name) {
        String target = x + " " + z + " " + level + " " + id;
        switch (action) {
            case ACTION_NPC_TAG:
                send("devtag " + x + " " + z + " " + level + " -1 npc " + id + " " + name);
                break;
            case ACTION_NPC_REMOVE:
                send("devnpc remove " + target);
                break;
            case ACTION_NPC_ROTATE:
                send("devnpc rotate " + target);
                break;
            case ACTION_NPC_PICK:
                pick(2, id, x, z, level, name);
                break;
            case ACTION_INFO_NPC:
                send("devnpcinfo " + target);
                break;
            case ACTION_NPC_RENAME:
                send("devnpcname " + target);
                break;
            case ACTION_NPC_RADIUS:
                send("devnpcradius " + target);
                break;
            default:
                if (action >= ACTION_NUDGE_NPC && action <= ACTION_NUDGE_NPC + 3) {
                    int d = action - ACTION_NUDGE_NPC;
                    send("devnpc move " + target + " " + (x + NUDGE_DX[d]) + " " + (z + NUDGE_DZ[d]) + " " + level);
                }
                break;
        }
    }

    /**
     * Place mode keys. Called for every keyboard event before the game sees it; returns true when the event was a
     * place-mode key (it is then swallowed: no chat input, no camera turn). Pressed events carry the special keys
     * (arrows, PgUp/PgDn, Enter, Esc, Tab, Del), typed events the letters and +/-; the typed twins of the special
     * keys are swallowed too, so Enter never sends the chat line.
     */
    public static boolean key(KeyLog log) {
        if (!placing || !enabled || debugconsole.isOpen() || !com.jagex.core.constants.MainLogicStep.isAtGameScreen(MainLogicManager.step)) {
            return false;
        }
        int type = log.getType();
        if (type == KeyLog.TYPE_PRESSED || type == KeyLog.TYPE_HELD) {
            String command = special(log.getKeyCode());
            if (command != null) {
                // One physical press arrives as a PRESSED record plus the same log as HELD (SimpleKeyboardMonitor.record),
                // and auto-repeat as more HELD logs: act once, on the press, swallow the rest (2026-10-06: every arrow
                // step counted twice before this).
                if (type == KeyLog.TYPE_PRESSED) {
                    send("devdeco " + command);
                }
                return true;
            }
            if (type == KeyLog.TYPE_HELD) {
                return letter(log.getKeyChar()) != null;
            }
            return false;
        }
        if (type == KeyLog.TYPE_TYPED) {
            if (special(log.getKeyCode()) != null) {
                return true;
            }
            char c = log.getKeyChar();
            if (c == '\n' || c == '\r' || c == 27 || c == '\t' || c == 127) {
                return true;
            }
            String command = letter(c);
            if (command != null) {
                send("devdeco " + command);
                return true;
            }
        }
        return false;
    }

    private static String special(int keyCode) {
        switch (keyCode) {
            case KEY_LEFT:
                return "along -";
            case KEY_RIGHT:
                return "along +";
            case KEY_UP:
                return "up +";
            case KEY_DOWN:
                return "up -";
            case KEY_PAGE_UP:
                return "out +";
            case KEY_PAGE_DOWN:
                return "out -";
            case KEY_ENTER:
                return "save";
            case KEY_ESC:
                return "cancel";
            case KEY_TAB:
                return "step";
            case KEY_DELETE:
                return "delete";
            default:
                return null;
        }
    }

    private static String letter(char c) {
        switch (Character.toLowerCase(c)) {
            case '+':
            case '=':
                return "scale +";
            case '-':
            case '_':
                return "scale -";
            case 'q':
                return "yaw -";
            case 'e':
                return "yaw +";
            case 'r':
                return "turn";
            case 'f':
                return "flip";
            case 'c':
                return "copy";
            case 'u':
                return "undo";
            case 't':
                // Tab is an AWT focus-traversal key and does not always reach the canvas, so T toggles the step too.
                return "step";
            default:
                return null;
        }
    }

    /** The place-mode panel: the server's status lines (what, where, values, step) and the keys, top left of the view. */
    public static void drawOverlay(int viewX, int viewY, int viewWidth, int viewHeight) {
        if (!placing || status.length == 0) {
            return;
        }
        Font font = RspsHud.font();
        FontMetrics metrics = RspsHud.metrics();
        int lines = status.length + KEY_HELP.length;
        int width = 0;
        for (String line : status) {
            width = Math.max(width, RspsHud.width(metrics, line));
        }
        for (String line : KEY_HELP) {
            width = Math.max(width, RspsHud.width(metrics, line));
        }
        int border = RspsHud.PANEL_BORDER + 2;
        int panelWidth = width + 2 * border;
        int panelHeight = lines * RspsHud.LINE + 2 * border + 2;
        // Top left of the view under the "Walk here" hover line: a wall decoration is usually up in the middle or the
        // right of the view, the minimap sits top right and the chatbox runs under the bottom of the resizable view.
        int x = viewX + RspsHud.PANEL_GAP;
        int y = viewY + 22;
        RspsHud.box(x, y, panelWidth, panelHeight);
        int baseline = y + border + RspsHud.LINE - 3;
        for (int i = 0; i < status.length; i++) {
            RspsHud.text(font, status[i], x + border, baseline, i == 0 ? 0xFFFFFF00 : 0xFFFFFFFF, false);
            baseline += RspsHud.LINE;
        }
        baseline += 2;
        for (String line : KEY_HELP) {
            RspsHud.text(font, line, x + border, baseline, 0xFF9F9F9F, false);
            baseline += RspsHud.LINE;
        }
    }

    private static void pick(int kind, int id, int x, int z, int level, String name) {
        pickKind = kind;
        pickId = id;
        pickX = x;
        pickZ = z;
        pickLevel = level;
        pickName = name == null ? String.valueOf(id) : name;
        debugconsole.addline("Picked " + pickName + " (" + id + ") - right-click the ground: " + (kind == PICK_TELE ? "Link teleport here" : "Move/Copy here"));
    }

    /** Sends [command] to the server as a client cheat. */
    public static void send(String command) {
        ClientMessage message = ClientMessage.create(com.jagex.ClientProt.CLIENT_CHEAT, ServerConnection.GAME.isaac);
        message.bitPacket.p1(command.length() + 3);
        message.bitPacket.p1(0);
        message.bitPacket.p1(0);
        message.bitPacket.pjstr(command);
        ServerConnection.GAME.send(message);
    }
}
