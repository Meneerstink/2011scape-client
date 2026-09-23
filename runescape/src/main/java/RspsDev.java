/**
 * RSPS developer mode: the server's `devmode` command sends the console marker "__RSPS_DEV__:on|off". While it is on,
 * every loc (also option-less scenery), npc and ground item shows its id on right-click, and extra entries edit the
 * world live through the server's world-edit commands:
 * <ul>
 * <li>loc: Tag, Remove, Rotate, Pick;</li>
 * <li>npc: Tag, Remove, Rotate, Pick;</li>
 * <li>ground: Tag tile, 78 banner, 78 standard, and after a Pick "Move ... here" / "Copy ... here".</li>
 * </ul>
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

    public static final String[] NUDGE_NAMES = {"north", "east", "south", "west"};

    private static final int[] NUDGE_DX = {0, 1, 0, -1};

    private static final int[] NUDGE_DZ = {1, 0, -1, 0};

    /** 78 house banner locs (server world_edits.plugin.kts). */
    public static final int BANNER_78 = 62751;

    public static final int STANDARD_78 = 62750;

    /** The picked thing for Move/Copy: 0 nothing, 1 loc, 2 npc. */
    public static int pickKind = 0;

    public static int pickId;

    public static int pickX;

    public static int pickZ;

    public static int pickLevel;

    public static String pickName = "";

    private RspsDev() {
    }

    public static void handle(String value) {
        enabled = value.equals("on");
        if (!enabled) {
            pickKind = 0;
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
        return action >= ACTION_TAG_LOC && action <= ACTION_NUDGE_NPC + 3;
    }

    public static boolean isNpcAction(int action) {
        return (action >= ACTION_NPC_REMOVE && action <= ACTION_NPC_TAG) || action == ACTION_INFO_NPC || (action >= ACTION_NUDGE_NPC && action <= ACTION_NUDGE_NPC + 3);
    }

    public static boolean isLocAction(int action) {
        return action == ACTION_TAG_LOC || action == ACTION_REMOVE_LOC || action == ACTION_ROTATE_LOC || action == ACTION_PICK_LOC || action == ACTION_INFO_LOC || (action >= ACTION_NUDGE_LOC && action <= ACTION_NUDGE_LOC + 3);
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
            default:
                if (action >= ACTION_NUDGE_NPC && action <= ACTION_NUDGE_NPC + 3) {
                    int d = action - ACTION_NUDGE_NPC;
                    send("devnpc move " + target + " " + (x + NUDGE_DX[d]) + " " + (z + NUDGE_DZ[d]) + " " + level);
                }
                break;
        }
    }

    private static void pick(int kind, int id, int x, int z, int level, String name) {
        pickKind = kind;
        pickId = id;
        pickX = x;
        pickZ = z;
        pickLevel = level;
        pickName = name == null ? String.valueOf(id) : name;
        debugconsole.addline("Picked " + pickName + " (" + id + ") - right-click the ground: Move/Copy here");
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
