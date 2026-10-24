import com.jagex.core.constants.MiniMenuAction;
import com.jagex.core.datastruct.key.HashTableIterator;
import com.jagex.game.runetek6.client.GameShell;
import com.jagex.game.runetek6.config.iftype.SubInterface;
import rs2.client.event.mouse.MouseMonitor;
import java.util.HashMap;
import java.util.Map;
import rs2.client.event.keyboard.KeyLog;

/**
 * 78Legacy night job 2026-10-24 (Drop Viewer, Collection Log, quest tab - owner designs GOEDGEKEURD 01-06): the three things the
 * revision-667 interfaces cannot do on their own. The server drives all of them with console lines {@code __RSPS_UI__:...}.
 *
 * <ul>
 * <li><b>Text field in a window</b> ({@code field|text|hit|focusGraphic|clear|placeholder|value}): a click on the hit component
 *     focuses it; while focused every key goes to the field and nowhere else (not the chat, not the camera); Enter or Escape or a
 *     click anywhere else ends it, so does closing the window. The text shows in the text component with a caret; every change
 *     reaches the server as the command {@code uisearch <text>} (at most every 200 ms, never in the chat).</li>
 * <li><b>Full npc models</b> on model components ({@code npc|hash,npc,zoom,xangle,spin;...}): the 667 IF_SETNPCMODEL the store
 *     scripts use, with the zoom and angle; spin = the model turns slowly.</li>
 * <li><b>Sizes</b> ({@code size|hash,width,height;...}): a component resized (a sprite drawn at its own size, a wider text).</li>
 * <li><b>Ops</b> ({@code op|hash,text;...}): the first right-click option of a component (the search field's "x").</li>
 * <li><b>Fonts</b> ({@code font|hash,fontSprite;...}): a text component drawn in another game font (a list line that is a bold
 *     group header).</li>
 * <li><b>Tooltips</b> ({@code tip|hash,text;...}): the RuneLite tooltip box under the cursor while the mouse is on the component
 *     (drawn after every interface).</li>
 * </ul>
 */
public final class RspsUi {

    public static final String PREFIX = "__RSPS_UI__:";

    private static final int KEY_ENTER = 84;
    private static final int KEY_BACKSPACE = 85;
    private static final int KEY_ESCAPE = 13;
    private static final int MAX_LENGTH = 30;
    private static final long SEND_GAP_MS = 200L;

    // ---- the text field
    private static int fieldText = -1;
    private static int fieldHit = -1;
    private static int fieldOn = -1;
    private static int fieldClear = -1;
    private static String placeholder = "";
    private static final StringBuilder value = new StringBuilder();
    private static boolean focused;
    private static String sent = "";
    private static long lastSend;
    private static boolean dirty;

    // ---- models and tooltips
    private static final Map<Integer, int[]> spinning = new HashMap<>();
    private static final Map<Integer, String> tips = new HashMap<>();

    private RspsUi() {
    }

    public static synchronized void handle(String line) {
        try {
            if (line.startsWith("field|")) {
                String[] f = line.split("\\|", -1);
                fieldText = Integer.parseInt(f[1]);
                fieldHit = Integer.parseInt(f[2]);
                fieldOn = Integer.parseInt(f[3]);
                fieldClear = Integer.parseInt(f[4]);
                placeholder = f[5];
                value.setLength(0);
                value.append(f.length > 6 ? f[6] : "");
                sent = value.toString();
                focused = false;
                redrawField();
            } else if (line.startsWith("npc|")) {
                for (String part : line.substring(4).split(";")) {
                    String[] v = part.split(",");
                    if (v.length < 5) {
                        continue;
                    }
                    int hash = Integer.parseInt(v[0]);
                    Component c = InterfaceList.list(hash);
                    if (c == null) {
                        continue;
                    }
                    // An item shown here before (IF_SETOBJECT) would be drawn first: clear it.
                    c.invObject = -1;
                    c.invCount = 0;
                    c.objType = Component.OBJ_TYPE_NPCMODEL;
                    c.obj = Integer.parseInt(v[1]);
                    c.zoom2d = Integer.parseInt(v[2]);
                    c.xan2d = Integer.parseInt(v[3]);
                    c.yan2d = 0;
                    c.zan2d = 0;
                    // Spin flag second: every registered model is redrawn each loop (its npc data may still be loading).
                    spinning.put(hash, new int[] {c.obj, "1".equals(v[4]) ? 1 : 0});
                    InterfaceManager.redraw(c);
                }
            } else if (line.startsWith("size|")) {
                for (String part : line.substring(5).split(";")) {
                    String[] v = part.split(",");
                    if (v.length < 3) {
                        continue;
                    }
                    Component c = InterfaceList.list(Integer.parseInt(v[0]));
                    if (c != null) {
                        // CC_IF_SETSIZE with absolute sizes (ScriptRunner).
                        c.originalWidth = Integer.parseInt(v[1]);
                        c.originalHeight = Integer.parseInt(v[2]);
                        c.modelAspectRatioX = 0;
                        c.modelAspectRatioY = 0;
                        c.resizeModeX = 0;
                        c.resizeModeY = 0;
                        InterfaceManager.redraw(c);
                        InterfaceManager.resizeAndReposition(c);
                        if (c.type == 0) {
                            InterfaceManager.calculateLayerDimensions(c, false);
                        }
                    }
                }
            } else if (line.startsWith("op|")) {
                for (String part : line.substring(3).split(";")) {
                    int comma = part.indexOf(',');
                    if (comma <= 0) {
                        continue;
                    }
                    Component c = InterfaceList.list(Integer.parseInt(part.substring(0, comma)));
                    if (c != null) {
                        c.setOp(0, part.substring(comma + 1));
                    }
                }
            } else if (line.startsWith("font|")) {
                for (String part : line.substring(5).split(";")) {
                    String[] v = part.split(",");
                    if (v.length < 2) {
                        continue;
                    }
                    Component c = InterfaceList.list(Integer.parseInt(v[0]));
                    if (c != null) {
                        c.fontGraphic = Integer.parseInt(v[1]);
                        InterfaceManager.redraw(c);
                    }
                }
            } else if (line.startsWith("tip|")) {
                for (String part : line.substring(4).split(";")) {
                    int comma = part.indexOf(',');
                    if (comma <= 0) {
                        continue;
                    }
                    tips.put(Integer.parseInt(part.substring(0, comma)), part.substring(comma + 1).replace("\n", "</br>"));
                }
            }
        } catch (RuntimeException malformed) {
            // A malformed line changes nothing.
        }
    }

    private static boolean interfaceOpen(int hash) {
        int id = hash >>> 16;
        HashTableIterator iterator = new HashTableIterator(InterfaceManager.subInterfaces);
        for (SubInterface sub = (SubInterface) iterator.first(); sub != null; sub = (SubInterface) iterator.next()) {
            if (sub.id == id) {
                return true;
            }
        }
        return InterfaceManager.topLevelInterface == id;
    }

    private static void setText(int hash, String text) {
        Component c = InterfaceList.list(hash);
        if (c != null && !text.equals(c.text)) {
            c.text = text;
            InterfaceManager.redraw(c);
        }
    }

    private static void setHidden(int hash, boolean hidden) {
        Component c = InterfaceList.list(hash);
        if (c != null && c.hidden != hidden) {
            c.hidden = hidden;
            InterfaceManager.redraw(c);
        }
    }

    private static void redrawField() {
        if (fieldText < 0) {
            return;
        }
        String shown;
        if (value.length() == 0 && !focused) {
            shown = "<col=6f6556>" + placeholder + "</col>";
        } else {
            boolean caret = focused && (System.currentTimeMillis() / 500L) % 2L == 0L;
            shown = "<col=ffffff>" + escape(value.toString()) + (caret ? "|" : "<col=000000> </col>") + "</col>";
        }
        setText(fieldText, shown);
        setHidden(fieldOn, !focused);
        setHidden(fieldClear, value.length() == 0);
    }

    private static String escape(String s) {
        return s.replace("<", "<lt>").replace(">", "<gt>");
    }

    /** A menu action ran: a click on the field focuses it, on its "x" clears it, anything else ends the typing. */
    public static synchronized void action(int action, int componentHash) {
        if (fieldText < 0) {
            return;
        }
        boolean component = action == MiniMenuAction.IF_BUTTONX1 || action == MiniMenuAction.IF_BUTTONX2;
        if (component && componentHash == fieldHit) {
            focused = true;
            redrawField();
            return;
        }
        if (component && componentHash == fieldClear) {
            value.setLength(0);
            dirty = true;
            focused = true;
            redrawField();
            return;
        }
        if (focused) {
            focused = false;
            redrawField();
        }
    }

    /** The client's key loop: true when the key belonged to the focused field (it then reaches nothing else). */
    public static synchronized boolean key(KeyLog log) {
        if (!focused) {
            return false;
        }
        if (fieldText < 0 || !interfaceOpen(fieldText)) {
            focused = false;
            return false;
        }
        int type = log.getType();
        int code = log.getKeyCode();
        // Special keys arrive as HELD (repeating while held), characters as TYPED (key code -1).
        if (type == KeyLog.TYPE_HELD) {
            if (code == KEY_ENTER || code == KEY_ESCAPE) {
                focused = false;
                dirty = true;
                redrawField();
            } else if (code == KEY_BACKSPACE && value.length() > 0) {
                value.setLength(value.length() - 1);
                dirty = true;
                redrawField();
            }
        } else if (type == KeyLog.TYPE_TYPED) {
            char ch = log.getKeyChar();
            if (ch >= 32 && ch < 127 && ch != '|' && ch != ';' && value.length() < MAX_LENGTH) {
                value.append(ch);
                dirty = true;
                redrawField();
            }
        }
        return true;
    }

    /** Once per client loop: the caret blink, the debounced send, the spinning models; forgets a closed window's field. */
    public static synchronized void tick() {
        if (fieldText >= 0) {
            if (!interfaceOpen(fieldText)) {
                fieldText = -1;
                focused = false;
                value.setLength(0);
                sent = "";
                dirty = false;
            } else {
                if (focused) {
                    redrawField();
                }
                long now = System.currentTimeMillis();
                if (dirty && now - lastSend >= SEND_GAP_MS) {
                    dirty = false;
                    String text = value.toString().trim();
                    if (!text.equals(sent)) {
                        sent = text;
                        lastSend = now;
                        send("uisearch " + text);
                    }
                }
            }
        }
        if (!spinning.isEmpty()) {
            // A closed window's models are forgotten (its components reload from the cache when it opens again).
            spinning.keySet().removeIf(hash -> !interfaceOpen(hash));
            for (Map.Entry<Integer, int[]> e : spinning.entrySet()) {
                Component c = InterfaceList.list(e.getKey());
                if (c == null || c.objType != Component.OBJ_TYPE_NPCMODEL || c.obj != e.getValue()[0]) {
                    continue;
                }
                if (e.getValue()[1] == 1) {
                    c.yan2d = (c.yan2d + 6) & 2047;
                }
                InterfaceManager.redraw(c);
            }
        }
    }

    private static void send(String command) {
        if (ServerConnection.GAME == null || ServerConnection.GAME.isaac == null) {
            return;
        }
        ClientMessage message = ClientMessage.create(com.jagex.ClientProt.CLIENT_CHEAT, ServerConnection.GAME.isaac);
        message.bitPacket.p1(command.length() + 3);
        message.bitPacket.p1(0);
        message.bitPacket.p1(0);
        message.bitPacket.pjstr(command);
        ServerConnection.GAME.send(message);
    }

    /** The night job's screens draw their own amounts (Qty column, the log's yellow count): their item pictures never show one. */
    private static final int[] OWN_COUNT_INTERFACES = {1172, 1176, 1182, 1183, 1184};

    public static int objNumMode(Component child) {
        int id = child.slot >>> 16;
        for (int own : OWN_COUNT_INTERFACES) {
            if (own == id) {
                return com.jagex.game.runetek6.config.objtype.ObjNumMode.SHOWCOUNT_NEVER;
            }
        }
        return child.objNumMode;
    }

    /** After every interface, before the menu: the hovered component's tooltip. */
    public static synchronized void drawTooltip() {
        if (tips.isEmpty() || MiniMenu.open || MouseMonitor.instance == null) {
            return;
        }
        MiniMenuEntryInner top = MiniMenu.topEntry;
        if (top == null) {
            return;
        }
        int action = top.action >= 2000 ? top.action - 2000 : top.action;
        if (action != MiniMenuAction.IF_BUTTONX1 && action != MiniMenuAction.IF_BUTTONX2) {
            return;
        }
        String tip = tips.get(top.v3);
        if (tip == null || !interfaceOpen(top.v3)) {
            return;
        }
        int mx = MouseMonitor.instance.getRecordedX();
        int my = MouseMonitor.instance.getRecordedY();
        RspsHud.tooltip(tip, mx, my, 0, 0, GameShell.canvasWid, GameShell.canvasHei);
    }
}
