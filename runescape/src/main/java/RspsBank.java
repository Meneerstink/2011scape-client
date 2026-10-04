import static com.jagex.core.constants.ClientScriptOpCode.*;

import java.util.ArrayList;
import java.util.List;

/**
 * E7 (owner 2026-10-03, "onze bank moet precies functioneren als osrs bank"): the OSRS bank controls the revision-667
 * cache never had, built from the client's own script engine instead of a cache edit. The server stays in charge
 * (BankOptions.kt): it names the state on the side channel, lays out the buttons and maps every op.
 *
 * <ul>
 * <li>Patched 1453 (one bank item's ops, run by the cache's 1450 / 1664): OSRS script 669's menu - op1 "Withdraw-" + the
 *     default quantity, op2 "Withdraw-1" (not when the default is 1), op3 "Withdraw-5", op4 "Withdraw-10", op5
 *     "Withdraw-&lt;n&gt;" (once a custom amount is set, varp 1249), op6 "Withdraw-X", op7 "Withdraw-All", op8
 *     "Withdraw-All-but-1", op9 "Placeholder" (while 'Always set placeholders' is off), op10 "Examine". A count of 0 is a
 *     placeholder (OSRS script 278): no number, transparency 120, op8 "Release" and "Examine" only.</li>
 * <li>Patched 1540 (bank side inventory 763, run by the cache's 1448 / 1209): OSRS script 172's menu - op1 the default
 *     quantity, op2-4 "Deposit-1" / "-5" / "-10" (the default's own entry left out), op5 "Deposit-X", op6 "Deposit-All".
 *     Ops 8 / 9 (the 667 item params 1264 / 1265, "Eat" / "Wield") are left empty: the server never enables them on 763.</li>
 * <li>{@link #SCRIPT_BUTTON}: a bank button (background graphic + label with up to two ops) as dynamic children of a
 *     layer, positioned from the bottom edge when asked (the 667 bottom bar's own y mode 2).</li>
 * </ul>
 * The server sends {@code __RSPS_BANK__:state|<quantity mode 0-4>|<always placeholder 0/1>} before it opens the bank and
 * after every change, then reruns 1451 / 1448 so both menus are rebuilt with the new layout.
 */
public final class RspsBank {

    static final String PREFIX = "__RSPS_BANK__:";

    public static final int SCRIPT_BUTTON = 40110;
    static final int SCRIPT_BUTTON_HOVER = 40111;

    /** The cache scripts this class replaces. */
    static final int BANK_ITEM_OPS = 1453;
    static final int BANK_SIDE_INVENTORY = 1540;

    static final int MODE_ONE = 0;
    static final int MODE_FIVE = 1;
    static final int MODE_TEN = 2;
    static final int MODE_X = 3;
    static final int MODE_ALL = 4;

    static final int INV_BANK = 95;
    static final int INV_INVENTORY = 93;
    static final int VARP_CUSTOM_QUANTITY = 1249;
    static final int BANK_ROOT = 762 << 16;

    /** OSRS script 278: a placeholder is drawn with transparency 120. */
    static final int PLACEHOLDER_TRANSPARENCY = 120;

    /** The 667 bank's own button backgrounds (762:15 / 17 / 19 use 1431, a selected toggle 1433 - cache script 1487). */
    static final int SPRITE_BUTTON = 1431;
    static final int SPRITE_BUTTON_SELECTED = 1433;

    /** Hover transparency of the 667 bank buttons (onMouseOver 1357 with 100, onMouseLeave 1501 back to 0). */
    static final int HOVER_TRANSPARENCY = 100;

    static final int ORANGE = 0xFF981F;
    static final int WHITE = 0xFFFFFF;

    static final int HOOK_MOUSE_Y = Integer.MIN_VALUE + 2;
    static final int HOOK_SOURCE_LAYER = Integer.MIN_VALUE + 3;
    static final int HOOK_SOURCE_CHILD = Integer.MIN_VALUE + 5;
    static final int HOOK_TARGET_LAYER = Integer.MIN_VALUE + 6;

    /** Cache 1453's own drag hooks, kept as they are. */
    static final int SCRIPT_BANK_DRAG = 1454;
    static final int SCRIPT_BANK_DRAG_COMPLETE = 1482;

    /** Cache script 154: (inv, slot, component, child, drag mode, drag target; op1..op9) - one inventory item with its ops. */
    static final int SCRIPT_INVENTORY_ITEM = 154;

    private static volatile int mode = MODE_ONE;
    private static volatile boolean alwaysPlaceholder;

    private static ClientScript itemOps;
    private static ClientScript sideInventory;
    private static int builtFor = Integer.MIN_VALUE;

    private RspsBank() {
    }

    static void handle(String payload) {
        try {
            if (payload.startsWith("state|")) {
                String[] parts = payload.substring("state|".length()).split("\\|");
                int m = Integer.parseInt(parts[0].trim());
                mode = m >= MODE_ONE && m <= MODE_ALL ? m : MODE_ONE;
                alwaysPlaceholder = parts.length > 1 && "1".equals(parts[1].trim());
            }
        } catch (RuntimeException malformed) {
            mode = MODE_ONE;
            alwaysPlaceholder = false;
        }
    }

    /** Called by {@link ClientScriptList#list}: a script of ours for [id], or null for the cache's own. */
    public static synchronized ClientScript script(int id) {
        switch (id) {
            case BANK_ITEM_OPS:
            case BANK_SIDE_INVENTORY: {
                int key = mode * 2 + (alwaysPlaceholder ? 1 : 0);
                if (key != builtFor) {
                    itemOps = null;
                    sideInventory = null;
                    builtFor = key;
                }
                if (id == BANK_ITEM_OPS) {
                    return itemOps != null ? itemOps : (itemOps = itemOps(mode, alwaysPlaceholder));
                }
                return sideInventory != null ? sideInventory : (sideInventory = sideInventory(mode));
            }
            case SCRIPT_BUTTON:
                return button();
            case SCRIPT_BUTTON_HOVER:
                return hover();
            default:
                return null;
        }
    }

    /* ------------------------------------------------ scripts ------------------------------------------------ */

    /** Pushes the default quantity's text (OSRS script 2579): "1", "5", "10", max(1, custom) or "All". */
    static void pushQuantity(Asm c, int mode) {
        switch (mode) {
            case MODE_FIVE:
                c.s("5");
                return;
            case MODE_TEN:
                c.s("10");
                return;
            case MODE_ALL:
                c.s("All");
                return;
            case MODE_X: {
                c.op(PUSH_VAR, VARP_CUSTOM_QUANTITY).i(0);
                int set = c.branch(BRANCH_GREATER_THAN);
                c.s("1");
                int done = c.branch(BRANCH);
                c.land(set);
                c.op(PUSH_VAR, VARP_CUSTOM_QUANTITY).op(TOSTRING);
                c.land(done);
                return;
            }
            default:
                c.s("1");
        }
    }

    /** Patched 1453 (slot): the active component (created / found by the caller) shows bank slot [slot] with the OSRS menu. */
    /** The cache's bank layout script: lays the items of each tab out and stops at the first slot whose count is 0. */
    static final int SCRIPT_BANK_LAYOUT = 1456;

    /**
     * INV_GETNUM as the bank layout (script 1456) sees it: the 667 bank ends its list at the first count of 0 ("1000 ->
     * local0"), so a placeholder (an item with count 0, OSRS script 278) hid itself and every item after it - the owner's
     * "the bank shows placeholders really weird" (2026-10-04 031525; seen in the agent's client: after 'Placeholder' on
     * slot 1 only the coins in slot 0 were left). Inside 1456 a placeholder counts as present; its own slot still draws it
     * with no number (patched 1453).
     */
    static int layoutCount(int inv, int slot, int scriptId, int count) {
        if (count == 0 && inv == INV_BANK && scriptId == SCRIPT_BANK_LAYOUT && ClientInventory.get(inv, slot, false) != -1) {
            return 1;
        }
        return count;
    }

    static ClientScript itemOps(int mode, boolean alwaysPlaceholder) {
        Asm c = new Asm();
        // int locals: 0 slot (arg), 1 obj, 2 count
        c.i(INV_BANK).iload(0).op(INV_GETOBJ).op(POP_INT_LOCAL, 1);
        c.i(INV_BANK).iload(0).op(INV_GETNUM).op(POP_INT_LOCAL, 2);
        c.op(CC_IF_CLEAROPS);
        c.s("<col=ff981f>").iload(1).op(OC_NAME).op(JOIN_STRING, 2).op(CC_IF_SETOPBASE);
        c.iload(2).i(0);
        int item = c.branch(BRANCH_NOT);

        // A placeholder (count 0): OSRS script 278.
        c.iload(1).i(0).op(CC_IF_SETOBJECT_NONUM);
        c.i(PLACEHOLDER_TRANSPARENCY).op(CC_IF_SETTTRANS);
        c.i(8).s("Release").op(CC_IF_SETOP);
        int common = c.branch(BRANCH);

        // An item: OSRS script 669.
        c.land(item);
        c.iload(1).iload(2).op(CC_IF_SETOBJECT);
        c.i(0).op(CC_IF_SETTTRANS);
        c.i(1).s("Withdraw-");
        pushQuantity(c, mode);
        c.op(JOIN_STRING, 2).op(CC_IF_SETOP);
        if (mode != MODE_ONE) {
            c.i(2).s("Withdraw-1").op(CC_IF_SETOP);
        }
        c.i(3).s("Withdraw-5").op(CC_IF_SETOP);
        c.i(4).s("Withdraw-10").op(CC_IF_SETOP);
        c.op(PUSH_VAR, VARP_CUSTOM_QUANTITY).i(0);
        int custom = c.branch(BRANCH_GREATER_THAN);
        int noCustom = c.branch(BRANCH);
        c.land(custom);
        c.i(5).s("Withdraw-").op(PUSH_VAR, VARP_CUSTOM_QUANTITY).op(TOSTRING).op(JOIN_STRING, 2).op(CC_IF_SETOP);
        c.land(noCustom);
        c.i(6).s("Withdraw-X").op(CC_IF_SETOP);
        c.i(7).s("Withdraw-All").op(CC_IF_SETOP);
        c.i(8).s("Withdraw-All-but-1").op(CC_IF_SETOP);
        if (!alwaysPlaceholder) {
            c.i(9).s("Placeholder").op(CC_IF_SETOP);
        }

        // Both: Examine and the cache 1453's drag set-up, unchanged.
        c.land(common);
        c.i(10).s("Examine").s("<col=ff9040>").op(JOIN_STRING, 2).op(CC_IF_SETOP);
        c.i(BANK_ROOT).i(-1).op(CC_IF_SETDRAGGABLE);
        c.i(5).op(CC_IF_SETDRAGDEADZONE);
        c.i(5).op(CC_IF_SETDRAGDEADTIME);
        c.i(SCRIPT_BANK_DRAG).i(HOOK_MOUSE_Y).s("i").op(CC_IF_SETONDRAG);
        c.i(SCRIPT_BANK_DRAG_COMPLETE).i(HOOK_TARGET_LAYER).s("I").op(CC_IF_SETONDRAGCOMPLETE);
        c.op(RETURN);
        return c.build(BANK_ITEM_OPS, 3, 0, 1, 0);
    }

    /**
     * Patched 1540 (component): the cache's grid (4 columns spread over the width, 7 rows over the height) with the OSRS
     * deposit menu; script 154 draws each slot (and clears an empty one) exactly as before.
     */
    static ClientScript sideInventory(int mode) {
        Asm c = new Asm();
        // int locals: 0 component (arg), 1 x gap, 2 y gap, 3 slot, 4 size
        c.iload(0).op(CC_DELETEALL);
        c.iload(0).op(IF_GETWIDTH).i(36).i(4).op(MULTIPLY).op(SUB).i(3).op(DIVIDE).op(POP_INT_LOCAL, 1);
        c.iload(0).op(IF_GETHEIGHT).i(32).i(7).op(MULTIPLY).op(SUB).i(6).op(DIVIDE).op(POP_INT_LOCAL, 2);
        c.i(0).op(POP_INT_LOCAL, 3);
        c.i(INV_INVENTORY).op(INV_SIZE).op(POP_INT_LOCAL, 4);

        int top = c.mark();
        c.iload(3).iload(4);
        int body = c.branch(BRANCH_LESS_THAN);
        int end = c.branch(BRANCH);
        c.land(body);
        c.iload(0).i(Component.TYPE_GRAPHIC).iload(3).op(CC_CREATE);
        c.i(36).i(32).i(0).i(0).op(CC_IF_SETSIZE);
        c.i(36).iload(1).op(ADD).iload(3).i(4).op(MODULO).op(MULTIPLY);
        c.iload(3).i(4).op(DIVIDE).i(32).iload(2).op(ADD).op(MULTIPLY);
        c.i(0).i(0).op(CC_IF_SETPOSITION);

        c.i(INV_INVENTORY).iload(3).iload(0).iload(3).i(1).i(-1);
        c.s("Deposit-");
        pushQuantity(c, mode);
        c.op(JOIN_STRING, 2);
        c.s(mode == MODE_ONE ? "" : "Deposit-1");
        c.s(mode == MODE_FIVE ? "" : "Deposit-5");
        c.s(mode == MODE_TEN ? "" : "Deposit-10");
        c.s("Deposit-X");
        c.s(mode == MODE_ALL ? "" : "Deposit-All");
        c.s("").s("").s("");
        c.op(GOSUB_WITH_PARAMS, SCRIPT_INVENTORY_ITEM);

        c.iload(3).i(1).op(ADD).op(POP_INT_LOCAL, 3);
        c.jumpTo(top);
        c.land(end);
        c.op(RETURN);
        return c.build(BANK_SIDE_INVENTORY, 5, 0, 1, 0);
    }

    /**
     * (layer, firstChild, x, y, width, height, yMode, selected, background, font; label, op1, op2, opBase): the background
     * graphic at firstChild (the bank's button sprite, or none), the label with the ops at firstChild + 1.
     */
    static ClientScript button() {
        Asm c = new Asm();
        c.iload(0).i(Component.TYPE_GRAPHIC).iload(1).op(CC_CREATE);
        c.iload(2).iload(3).i(0).iload(6).op(CC_IF_SETPOSITION);
        c.iload(4).iload(5).i(0).i(0).op(CC_IF_SETSIZE);
        c.iload(8).i(1);
        int withBackground = c.branch(BRANCH_EQUALS);
        c.i(1).op(CC_IF_SETHIDE);
        int backgroundDone = c.branch(BRANCH);
        c.land(withBackground);
        c.i(0).op(CC_IF_SETHIDE);
        c.iload(7).i(1);
        int selectedSprite = c.branch(BRANCH_EQUALS);
        c.i(SPRITE_BUTTON).op(CC_IF_SETGRAPHIC);
        int spriteDone = c.branch(BRANCH);
        c.land(selectedSprite);
        c.i(SPRITE_BUTTON_SELECTED).op(CC_IF_SETGRAPHIC);
        c.land(spriteDone);
        c.land(backgroundDone);

        c.iload(0).i(Component.TYPE_TEXT).iload(1).i(1).op(ADD).op(CC_CREATE);
        c.iload(2).iload(3).i(0).iload(6).op(CC_IF_SETPOSITION);
        c.iload(4).iload(5).i(0).i(0).op(CC_IF_SETSIZE);
        c.iload(9).op(CC_IF_SETTEXTFONT);
        c.iload(7).i(1);
        int selectedColour = c.branch(BRANCH_EQUALS);
        c.i(ORANGE).op(CC_IF_SETCOLOUR);
        int colourDone = c.branch(BRANCH);
        c.land(selectedColour);
        c.i(WHITE).op(CC_IF_SETCOLOUR);
        c.land(colourDone);
        c.i(1).op(CC_IF_SETTEXTSHADOW);
        c.i(1).i(1).i(0).op(CC_IF_SETTEXTALIGN);
        c.sload(0).op(CC_IF_SETTEXT);
        for (int op = 1; op <= 2; op++) {
            c.sload(op).op(STRING_LENGTH).i(0);
            int set = c.branch(BRANCH_GREATER_THAN);
            int skip = c.branch(BRANCH);
            c.land(set);
            c.i(op).sload(op).op(CC_IF_SETOP);
            c.land(skip);
        }
        c.sload(3).op(CC_IF_SETOPBASE);
        c.i(SCRIPT_BUTTON_HOVER).i(HOOK_SOURCE_LAYER).i(HOOK_SOURCE_CHILD).i(HOVER_TRANSPARENCY).s("iii").op(CC_IF_SETONMOUSEOVER);
        c.i(SCRIPT_BUTTON_HOVER).i(HOOK_SOURCE_LAYER).i(HOOK_SOURCE_CHILD).i(0).s("iii").op(CC_IF_SETONMOUSELEAVE);
        c.op(RETURN);
        return c.build(SCRIPT_BUTTON, 10, 4, 10, 4);
    }

    /** (layer, label child, transparency): the hovered button's background (one child below its label), like 1357 / 1501. */
    static ClientScript hover() {
        Asm c = new Asm();
        c.iload(0).iload(1).i(1).op(SUB).op(CC_FIND);
        int found = c.branch(BRANCH_IF_TRUE);
        c.op(RETURN);
        c.land(found);
        c.iload(2).op(CC_IF_SETTTRANS);
        c.op(RETURN);
        return c.build(SCRIPT_BUTTON_HOVER, 3, 0, 3, 0);
    }

    /* ------------------------------------------- tiny assembler ------------------------------------------- */

    /** As RspsGrandExchange's, plus a backward jump for loops. */
    static final class Asm {
        private final List<Integer> opcodes = new ArrayList<>();
        private final List<Integer> ints = new ArrayList<>();
        private final List<String> strings = new ArrayList<>();

        Asm op(int opcode) {
            return op(opcode, 0);
        }

        Asm op(int opcode, int operand) {
            opcodes.add(opcode);
            ints.add(operand);
            strings.add(null);
            return this;
        }

        Asm i(int value) {
            return op(PUSH_CONSTANT_INT, value);
        }

        Asm s(String value) {
            opcodes.add(PUSH_CONSTANT_STRING);
            ints.add(0);
            strings.add(value);
            return this;
        }

        Asm iload(int local) {
            return op(PUSH_INT_LOCAL, local);
        }

        Asm sload(int local) {
            return op(PUSH_STRING_LOCAL, local);
        }

        /** The index the next op will get (a loop head for {@link #jumpTo}). */
        int mark() {
            return opcodes.size();
        }

        /** A forward branch to be {@link #land}ed. */
        int branch(int opcode) {
            op(opcode, 0);
            return opcodes.size() - 1;
        }

        /** Points the branch at [at] to the next op emitted. */
        void land(int at) {
            ints.set(at, opcodes.size() - at - 1);
        }

        /** An unconditional jump back to [target] (from {@link #mark}). */
        void jumpTo(int target) {
            int at = opcodes.size();
            op(BRANCH, target - at - 1);
        }

        ClientScript build(int id, int intVars, int stringVars, int intArgs, int stringArgs) {
            ClientScript script = new ClientScript();
            int n = opcodes.size();
            script.opcodes = new int[n];
            script.intOperands = new int[n];
            script.stringOperands = new String[n];
            for (int k = 0; k < n; k++) {
                script.opcodes[k] = opcodes.get(k);
                script.intOperands[k] = ints.get(k);
                script.stringOperands[k] = strings.get(k);
            }
            script.id = id;
            script.name = "rsps_bank_" + id;
            script.intVarCount = intVars;
            script.stringVarCount = stringVars;
            script.intArgCount = intArgs;
            script.stringArgCount = stringArgs;
            return script;
        }
    }
}
