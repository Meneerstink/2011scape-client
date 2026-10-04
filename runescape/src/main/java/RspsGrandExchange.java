import static com.jagex.core.constants.ClientScriptOpCode.*;

import com.jagex.game.runetek6.config.enumtype.EnumType;
import com.jagex.game.runetek6.config.enumtype.EnumTypeList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * E6 (owner 2026-10-03): the OSRS Grand Exchange parts the revision-667 cache never had, built from the client's own
 * script engine instead of a cache edit. The server stays in charge: it lays the buttons out and enables their ops.
 *
 * <ul>
 * <li>{@link #SCRIPT_BUTTON}: a 667 stone button (nine pieces + label with up to two ops) as dynamic children of a layer - the GE
 *     screen's History / Collect and the collection box's Inventory / Bank (OSRS scripts 803 / 793 / 788). A click
 *     reaches the server as IF_BUTTON on that layer with the label's child index as slot.</li>
 * <li>{@link #SCRIPT_SET_OPS}: ops + op base on a static component - the History rows' "Buy-offer" / "Examine"
 *     (OSRS script 1645).</li>
 * <li>Item search (interface 389): "Previous search:" when the search is empty, and Enter picks the previous search
 *     (empty search) or the first result (OSRS script 3362; Wiki changes 27 February 2015 and 8 April 2020). The cache's
 *     own scripts 574 (key handler) and 576 (refresh) still run unchanged: the patched versions call them.</li>
 * <li>Item sets (interface 645, owner bug 211448 "sets the layout is not centered correctly"): the cache's script 676 lays the
 *     sets of enum 1087 out ten to a row and starts every category on a new row with fixed pads ("+4 before 11858", ...). The
 *     667 enum has Akrisae's set (21768) as a seventh Barrows set, which the pads never counted, so every category after the
 *     Barrows sets started one column right of the grid. {@link #setsLayout} runs the cache's own 676 with each pad
 *     recomputed from the enum to "up to the next row" ({@link #repadSets}).</li>
 * </ul>
 * The server names the previous search with the console line {@code __RSPS_GE__:last|<itemId>} before it opens the search.
 */
public final class RspsGrandExchange {

    static final String PREFIX = "__RSPS_GE__:";

    public static final int SCRIPT_BUTTON = 40100;
    public static final int SCRIPT_SET_OPS = 40101;
    static final int SCRIPT_BUTTON_HOVER = 40102;
    static final int SCRIPT_PREVIOUS_HOVER = 40103;
    public static final int SCRIPT_SET_OP = 40104;
    static final int SCRIPT_BUTTON_LEAVE = 40105;

    /** A button is {@link #BUTTON_PIECES} stone pieces and its label: {@link #BUTTON_CHILDREN} dynamic children. */
    static final int BUTTON_PIECES = 9;
    static final int BUTTON_LABEL = BUTTON_PIECES;
    public static final int BUTTON_CHILDREN = BUTTON_PIECES + 1;
    static final int BUTTON_BORDER = 9;

    /** Script 680 (normal) and 915 (hover): the background, then the corners TL TR BL BR and the edges left top right bottom. */
    static final int[] BUTTON_NORMAL = {297, 913, 914, 915, 916, 917, 918, 919, 920};
    static final int[] BUTTON_HOVER = {1040, 929, 930, 931, 932, 933, 934, 935, 936};

    /** Per piece: x kind, y kind, width kind, height kind (see {@link #pieceX}). */
    static final int[][] BUTTON_LAYOUT = {
        {0, 0, 0, 0},
        {0, 0, 1, 1}, {1, 0, 1, 1}, {0, 1, 1, 1}, {1, 1, 1, 1},
        {0, 2, 1, 2}, {2, 0, 2, 1}, {1, 2, 1, 2}, {2, 1, 2, 1},
    };

    /** 105:187 "Confirm Offer": b12_full, 0xCC9800. */
    static final int BUTTON_TEXT_COLOUR = 0xCC9800;

    /** The cache's own 574 / 576, reachable under these ids once 574 / 576 are patched. */
    static final int ORIGINAL_BASE = 40000;

    static final int SEARCH_KEYS = 574;
    static final int SEARCH_REFRESH = 576;
    static final int SEARCH_RESULT_CLICK = 580;
    static final int SEARCH_CLOSE = 573;

    static final int SEARCH_RESULTS = (389 << 16) | 4;
    static final int SEARCH_INPUT = (389 << 16) | 9;
    static final int SEARCH_ICON = (389 << 16) | 15;
    static final int VARCSTR_SEARCH = 22;
    static final int VARC_SEARCH_TIMER = 81;
    static final int KEY_ENTER = 84;

    /** Below the instruction text (389:7 spans y 51..82 of the results layer); one 15-pixel row like the results. */
    static final int PREVIOUS_ROW_Y = 86;

    static final int BUTTON_FONT = 496;
    static final int RESULT_FONT = 495;
    static final int RESULT_COLOUR = 10508800;

    static final int HOOK_SOURCE_LAYER = Integer.MIN_VALUE + 3;
    static final int HOOK_SOURCE_CHILD = Integer.MIN_VALUE + 5;

    /** The cache's item sets layout (645:16), its set roster and grid width. */
    static final int SETS_LAYOUT = 676;
    static final int SETS_ENUM = 1087;
    static final int SETS_COLUMNS = 10;

    /** Script 676's grid position counter (int local 5). */
    static final int SETS_POSITION_LOCAL = 5;

    private static volatile int previousSearch = -1;
    private static ClientScript keys;
    private static ClientScript refresh;
    private static int builtFor = Integer.MIN_VALUE;
    private static ClientScript sets;
    private static boolean setsBuilt;

    private RspsGrandExchange() {
    }

    static void handle(String payload) {
        try {
            if (payload.startsWith("last|")) {
                previousSearch = Integer.parseInt(payload.substring(5).trim());
            }
        } catch (RuntimeException malformed) {
            previousSearch = -1;
        }
    }

    /** Called first by {@link ClientScriptList#list}: a script of ours for [id], or null for the cache's own. */
    public static synchronized ClientScript script(int id) {
        switch (id) {
            case SEARCH_KEYS:
            case SEARCH_REFRESH:
                if (builtFor != previousSearch) {
                    keys = null;
                    refresh = null;
                    builtFor = previousSearch;
                }
                if (id == SEARCH_KEYS) {
                    return keys != null ? keys : (keys = keys(previousSearch));
                }
                return refresh != null ? refresh : (refresh = refresh(previousSearch));
            case ORIGINAL_BASE + SEARCH_KEYS:
            case ORIGINAL_BASE + SEARCH_REFRESH:
                return ClientScriptList.original(id - ORIGINAL_BASE);
            case SCRIPT_BUTTON:
                return button();
            case SCRIPT_SET_OPS:
                return setOps();
            case SCRIPT_SET_OP:
                return setOp();
            case SCRIPT_BUTTON_HOVER:
                return pieces(SCRIPT_BUTTON_HOVER, BUTTON_HOVER);
            case SCRIPT_BUTTON_LEAVE:
                return pieces(SCRIPT_BUTTON_LEAVE, BUTTON_NORMAL);
            case SCRIPT_PREVIOUS_HOVER:
                return previousHover();
            case SETS_LAYOUT:
                if (!setsBuilt) {
                    sets = setsLayout();
                    // Built once the set roster can be read (in game); until then the cache's own 676 runs.
                    setsBuilt = sets != null || EnumTypeList.instance != null;
                }
                return sets;
            default:
                return null;
        }
    }

    /* ---------------------------------------------- item sets ---------------------------------------------- */

    /** The cache's 676 with its category pads recomputed from enum 1087, or null (the cache's own 676 runs) when not possible. */
    static ClientScript setsLayout() {
        if (EnumTypeList.instance == null) {
            return null;
        }
        EnumType roster = EnumTypeList.instance.list(SETS_ENUM);
        ClientScript script = ClientScriptList.original(SETS_LAYOUT);
        if (roster == null || script == null) {
            return null;
        }
        // 676 reads enum 1087 from key 1 until a key returns the enum's default (11760 in the 667 cache).
        List<Integer> values = new ArrayList<>();
        for (int key = 1; key <= 4096; key++) {
            int value = roster.getInt(key);
            if (value == roster.defaultInt) {
                break;
            }
            values.add(value);
        }
        return repadSets(script, values) ? script : null;
    }

    /**
     * Finds 676's category pads - {@code if (set == <id>) local5 += <pad>;} compiled as PUSH_CONSTANT_INT id, BRANCH_EQUALS 1,
     * BRANCH 4, PUSH_INT_LOCAL 5, PUSH_CONSTANT_INT pad, ADD, POP_INT_LOCAL 5, once in the item pass and once in the marker
     * pass - and sets each pad to what brings that category to the start of the next row for the sets in [values] (in enum
     * order). Same op count, so every branch of the script stays valid. False when the script has no such pad.
     */
    static boolean repadSets(ClientScript script, List<Integer> values) {
        int[] ops = script.opcodes;
        int[] operands = script.intOperands;
        if (ops == null || operands == null) {
            return false;
        }
        Map<Integer, List<Integer>> pads = new HashMap<>();
        for (int k = 0; k + 6 < ops.length; k++) {
            if (ops[k] == PUSH_CONSTANT_INT
                && ops[k + 1] == BRANCH_EQUALS && operands[k + 1] == 1
                && ops[k + 2] == BRANCH && operands[k + 2] == 4
                && ops[k + 3] == PUSH_INT_LOCAL && operands[k + 3] == SETS_POSITION_LOCAL
                && ops[k + 4] == PUSH_CONSTANT_INT
                && ops[k + 5] == ADD
                && ops[k + 6] == POP_INT_LOCAL && operands[k + 6] == SETS_POSITION_LOCAL) {
                pads.computeIfAbsent(operands[k], id -> new ArrayList<>()).add(k + 4);
            }
        }
        if (pads.isEmpty()) {
            return false;
        }
        int position = 0;
        for (int value : values) {
            List<Integer> at = pads.get(value);
            if (at != null) {
                int pad = (SETS_COLUMNS - position % SETS_COLUMNS) % SETS_COLUMNS;
                for (int index : at) {
                    operands[index] = pad;
                }
                position += pad;
            }
            position++;
        }
        return true;
    }

    /* ------------------------------------------------ scripts ------------------------------------------------ */

    /**
     * (layer, firstChild, x, y, width, height; label, op1, op2): the 667 Grand Exchange's own stone button - the look of
     * "Confirm Offer" (105:186 / 187: cache scripts 680 = background 297 + border pieces 913..920 through script 98, hover 915
     * = background 1040 + pieces 929..936; label font 496, colour 0xCC9800, shadow, centred). The nine pieces are children
     * firstChild .. firstChild + 8 (in 680's order), the label with the ops is firstChild + {@link #BUTTON_LABEL}; hovering
     * the label swaps the pieces to the hover look and back, as 97 / 653 do for "Confirm Offer".
     */
    static ClientScript button() {
        Cs2 c = new Cs2();
        for (int piece = 0; piece < BUTTON_PIECES; piece++) {
            c.iload(0).i(Component.TYPE_GRAPHIC).iload(1).i(piece).op(ADD).op(CC_CREATE);
            pieceX(c, BUTTON_LAYOUT[piece][0]);
            pieceY(c, BUTTON_LAYOUT[piece][1]);
            c.i(0).i(0).op(CC_IF_SETPOSITION);
            pieceWidth(c, BUTTON_LAYOUT[piece][2]);
            pieceHeight(c, BUTTON_LAYOUT[piece][3]);
            c.i(0).i(0).op(CC_IF_SETSIZE);
            c.i(BUTTON_NORMAL[piece]).op(CC_IF_SETGRAPHIC);
            c.i(1).op(CC_IF_SETTILING);
        }

        c.iload(0).i(Component.TYPE_TEXT).iload(1).i(BUTTON_LABEL).op(ADD).op(CC_CREATE);
        c.iload(2).iload(3).i(0).i(0).op(CC_IF_SETPOSITION);
        c.iload(4).iload(5).i(0).i(0).op(CC_IF_SETSIZE);
        c.i(BUTTON_FONT).op(CC_IF_SETTEXTFONT);
        c.i(BUTTON_TEXT_COLOUR).op(CC_IF_SETCOLOUR);
        c.i(1).op(CC_IF_SETTEXTSHADOW);
        c.i(1).i(1).i(0).op(CC_IF_SETTEXTALIGN);
        c.sload(0).op(CC_IF_SETTEXT);
        for (int op = 1; op <= 2; op++) {
            c.sload(op).op(STRING_LENGTH).i(0);
            int skip = c.branch(BRANCH_NOT_GREATER_THAN_PLACEHOLDER);
            c.sload(op).i(op).op(CC_IF_SETOP);
            c.land(skip);
        }
        c.i(SCRIPT_BUTTON_HOVER).i(HOOK_SOURCE_LAYER).i(HOOK_SOURCE_CHILD).s("ii").op(CC_IF_SETONMOUSEOVER);
        c.i(SCRIPT_BUTTON_LEAVE).i(HOOK_SOURCE_LAYER).i(HOOK_SOURCE_CHILD).s("ii").op(CC_IF_SETONMOUSELEAVE);
        c.op(RETURN);
        return c.build(SCRIPT_BUTTON, 6, 3, 6, 3);
    }

    /** (layer, label child): every piece of the label's button to [sprites] (the hover or the normal look). */
    static ClientScript pieces(int id, int[] sprites) {
        Cs2 c = new Cs2();
        for (int piece = 0; piece < BUTTON_PIECES; piece++) {
            c.iload(0).iload(1).i(BUTTON_LABEL - piece).op(SUB).op(CC_FIND);
            int missing = c.branch(BRANCH_IF_FALSE);
            c.i(sprites[piece]).op(CC_IF_SETGRAPHIC);
            c.land(missing);
        }
        c.op(RETURN);
        return c.build(id, 2, 0, 2, 0);
    }

    // Script 680's piece boxes: x (0 left, 1 right - 9, 2 left + 9), y (same with the height), width / height (0 full, 1 nine, 2 full - 18).
    private static void pieceX(Cs2 c, int kind) {
        c.iload(2);
        if (kind == 1) {
            c.iload(4).op(ADD).i(BUTTON_BORDER).op(SUB);
        } else if (kind == 2) {
            c.i(BUTTON_BORDER).op(ADD);
        }
    }

    private static void pieceY(Cs2 c, int kind) {
        c.iload(3);
        if (kind == 1) {
            c.iload(5).op(ADD).i(BUTTON_BORDER).op(SUB);
        } else if (kind == 2) {
            c.i(BUTTON_BORDER).op(ADD);
        }
    }

    private static void pieceWidth(Cs2 c, int kind) {
        if (kind == 1) {
            c.i(BUTTON_BORDER);
        } else {
            c.iload(4);
            if (kind == 2) {
                c.i(2 * BUTTON_BORDER).op(SUB);
            }
        }
    }

    private static void pieceHeight(Cs2 c, int kind) {
        if (kind == 1) {
            c.i(BUTTON_BORDER);
        } else {
            c.iload(5);
            if (kind == 2) {
                c.i(2 * BUTTON_BORDER).op(SUB);
            }
        }
    }

    /** (component; opBase, op1, op10): ops of a static component (IF_SETOP = CC_IF_SETOP + 1000). */
    static ClientScript setOps() {
        Cs2 c = new Cs2();
        c.sload(1).i(1).iload(0).op(CC_IF_SETOP + 1000);
        c.sload(2).i(10).iload(0).op(CC_IF_SETOP + 1000);
        c.sload(0).iload(0).op(CC_IF_SETOPBASE + 1000);
        c.op(RETURN);
        return c.build(SCRIPT_SET_OPS, 1, 3, 1, 3);
    }

    /** (component, op index; text): one op of a static component - the quantity "..." button's "All" (OSRS). */
    static ClientScript setOp() {
        Cs2 c = new Cs2();
        c.sload(0).iload(1).iload(0).op(CC_IF_SETOP + 1000);
        c.op(RETURN);
        return c.build(SCRIPT_SET_OP, 2, 1, 2, 1);
    }

    /** Patched 574: Enter picks the previous search (empty search) or the first result; every other key is the cache's 574. */
    static ClientScript keys(int previous) {
        Cs2 c = new Cs2();
        c.iload(0).i(KEY_ENTER);
        int enter = c.branch(BRANCH_EQUALS);
        c.iload(0).iload(1).op(GOSUB_WITH_PARAMS, ORIGINAL_BASE + SEARCH_KEYS).op(RETURN);
        c.land(enter);
        c.i(0).op(POP_VARC, VARC_SEARCH_TIMER);
        c.i(-1).s("").i(SEARCH_INPUT).op(CC_IF_SETONTIMER + 1000);
        c.op(PUSH_VARCSTR, VARCSTR_SEARCH).op(STRING_LENGTH).i(0);
        int typed = c.branch(BRANCH_GREATER_THAN);
        if (previous != -1) {
            c.i(previous).op(RESUME_OBJDIALOG).op(GOSUB_WITH_PARAMS, SEARCH_CLOSE).op(RETURN);
        }
        c.op(GOSUB_WITH_PARAMS, SEARCH_REFRESH).op(RETURN);
        c.land(typed);
        c.op(PUSH_VARCSTR, VARCSTR_SEARCH).i(1).op(OC_FIND).op(POP_INT_LOCAL, 2);
        c.iload(2).i(0);
        int pick = c.branch(BRANCH_GREATER_THAN);
        c.op(GOSUB_WITH_PARAMS, SEARCH_REFRESH).op(RETURN);
        c.land(pick);
        c.op(OC_FINDNEXT).op(RESUME_OBJDIALOG).op(GOSUB_WITH_PARAMS, SEARCH_CLOSE).op(RETURN);
        return c.build(SEARCH_KEYS, 3, 1, 2, 0);
    }

    /** Patched 576: the cache's refresh, then - on an empty search - the "Previous search:" row (click = select, like a result). */
    static ClientScript refresh(int previous) {
        Cs2 c = new Cs2();
        c.op(GOSUB_WITH_PARAMS, ORIGINAL_BASE + SEARCH_REFRESH);
        if (previous != -1) {
            c.op(PUSH_VARCSTR, VARCSTR_SEARCH).op(STRING_LENGTH).i(0);
            int typed = c.branch(BRANCH_GREATER_THAN);
            // Child 0 is the hover highlight, as in the cache's result list (script 577 / 579).
            c.i(SEARCH_RESULTS).i(Component.TYPE_RECTANGLE).i(0).op(CC_CREATE);
            c.i(SEARCH_RESULTS).i(Component.TYPE_TEXT).i(1).op(CC_CREATE);
            c.i(4).i(PREVIOUS_ROW_Y).i(0).i(0).op(CC_IF_SETPOSITION);
            c.i(422).i(15).i(0).i(0).op(CC_IF_SETSIZE);
            c.i(0).op(CC_IF_SETCOLOUR);
            c.i(RESULT_FONT).op(CC_IF_SETTEXTFONT);
            c.i(0).op(CC_IF_SETTEXTSHADOW);
            c.s("Previous search: <col=" + Integer.toHexString(RESULT_COLOUR) + ">").i(previous).op(OC_NAME).s("</col>").op(JOIN_STRING, 3).op(CC_IF_SETTEXT);
            c.i(SCRIPT_PREVIOUS_HOVER).i(previous).s("o").op(CC_IF_SETONMOUSEOVER);
            c.i(SEARCH_RESULT_CLICK).i(previous).s("o").op(CC_IF_SETONCLICK);
            c.i(previous).i(-1).i(SEARCH_ICON).op(CC_IF_SETOBJECT + 1000);
            c.land(typed);
        }
        c.op(RETURN);
        return c.build(SEARCH_REFRESH, 0, 0, 0, 0);
    }

    /** (item): the previous-search row's highlight and icon, as script 579 does for a result row. */
    static ClientScript previousHover() {
        Cs2 c = new Cs2();
        c.i(SEARCH_RESULTS).i(0).op(CC_FIND);
        int found = c.branch(BRANCH_IF_TRUE);
        c.op(RETURN);
        c.land(found);
        c.i(0).i(PREVIOUS_ROW_Y).i(0).i(0).op(CC_IF_SETPOSITION);
        c.i(430).i(15).i(0).i(0).op(CC_IF_SETSIZE);
        c.i(0).op(CC_IF_SETCOLOUR);
        c.i(220).op(CC_IF_SETTTRANS);
        c.i(1).op(CC_IF_SETFILL);
        c.iload(0).i(-1).i(SEARCH_ICON).op(CC_IF_SETOBJECT + 1000);
        c.op(RETURN);
        return c.build(SCRIPT_PREVIOUS_HOVER, 1, 0, 1, 0);
    }

    /** Marker: "skip when not greater than" is BRANCH_GREATER_THAN over one BRANCH (see {@link Cs2#branch}). */
    static final int BRANCH_NOT_GREATER_THAN_PLACEHOLDER = -1;

    /* ------------------------------------------- tiny assembler ------------------------------------------- */

    static final class Cs2 {
        private final List<Integer> opcodes = new ArrayList<>();
        private final List<Integer> ints = new ArrayList<>();
        private final List<String> strings = new ArrayList<>();

        Cs2 op(int opcode) {
            return op(opcode, 0);
        }

        Cs2 op(int opcode, int operand) {
            opcodes.add(opcode);
            ints.add(operand);
            strings.add(null);
            return this;
        }

        Cs2 i(int value) {
            return op(PUSH_CONSTANT_INT, value);
        }

        Cs2 s(String value) {
            opcodes.add(PUSH_CONSTANT_STRING);
            ints.add(0);
            strings.add(value);
            return this;
        }

        Cs2 iload(int local) {
            return op(PUSH_INT_LOCAL, local);
        }

        Cs2 sload(int local) {
            return op(PUSH_STRING_LOCAL, local);
        }

        /**
         * A forward branch to be {@link #land}ed. {@link #BRANCH_NOT_GREATER_THAN_PLACEHOLDER} emits
         * "if greater: skip the next op; else jump" so the landed code runs only when the comparison is false-y.
         */
        int branch(int opcode) {
            if (opcode == BRANCH_NOT_GREATER_THAN_PLACEHOLDER) {
                op(BRANCH_GREATER_THAN, 1);
                op(BRANCH, 0);
                int jump = opcodes.size() - 1;
                // The "greater" path falls through to the code between here and land(); the jump skips it.
                return jump;
            }
            op(opcode, 0);
            return opcodes.size() - 1;
        }

        /** Points the branch at [at] to the next op emitted. */
        void land(int at) {
            ints.set(at, opcodes.size() - at - 1);
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
            script.name = "rsps_ge_" + id;
            script.intVarCount = intVars;
            script.stringVarCount = stringVars;
            script.intArgCount = intArgs;
            script.stringArgCount = stringArgs;
            return script;
        }
    }
}
