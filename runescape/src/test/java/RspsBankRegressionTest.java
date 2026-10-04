import static com.jagex.core.constants.ClientScriptOpCode.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * E7: runs RspsBank's scripts on a small model of the client's script engine (same pop order as ScriptRunner: hook setters
 * pop the signature, then its args, then the script id; CC_IF_SETOP the op number, then the text). Checks that every
 * script leaves the stacks balanced, never runs off its end, and draws the OSRS menus BankOptions.kt maps on the server
 * (OSRS scripts 669 / 278 / 172).
 */
public final class RspsBankRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        itemMenus();
        placeholder();
        sideInventory();
        buttons();
        System.out.println("RspsBankRegressionTest: " + assertions + " assertions passed");
    }

    /** What one run did to its active component. */
    static final class Run {
        final String[] ops = new String[10];
        String opBase;
        int obj = -2;
        int count = -2;
        boolean noNum;
        int transparency = -1;
        int graphic = -2;
        int hidden = -1;
        String text;
        int colour = -1;
        int created;
        final List<String[]> gosubStrings = new ArrayList<>();
        final List<int[]> gosubInts = new ArrayList<>();
        final List<Integer> hooks = new ArrayList<>();
    }

    static Run run(ClientScript script, int[] intArgs, String[] stringArgs, int bankCount, int custom) {
        check(script.intArgCount == intArgs.length, "int args of " + script.id);
        check(script.stringArgCount == stringArgs.length, "string args of " + script.id);
        int[] ints = new int[script.intVarCount];
        String[] strings = new String[script.stringVarCount];
        System.arraycopy(intArgs, 0, ints, 0, intArgs.length);
        System.arraycopy(stringArgs, 0, strings, 0, stringArgs.length);
        Deque<Integer> is = new ArrayDeque<>();
        Deque<String> ss = new ArrayDeque<>();
        Run r = new Run();
        int pc = 0;
        int steps = 0;
        while (true) {
            check(pc >= 0 && pc < script.opcodes.length, "pc " + pc + " inside script " + script.id);
            check(++steps < 100_000, "script " + script.id + " ends");
            int op = script.opcodes[pc];
            int operand = script.intOperands[pc];
            String sOperand = script.stringOperands[pc];
            pc++;
            switch (op) {
                case PUSH_CONSTANT_INT: is.push(operand); break;
                case PUSH_CONSTANT_STRING: ss.push(sOperand); break;
                case PUSH_INT_LOCAL: is.push(ints[operand]); break;
                case POP_INT_LOCAL: ints[operand] = is.pop(); break;
                case PUSH_STRING_LOCAL: ss.push(strings[operand]); break;
                case POP_STRING_LOCAL: strings[operand] = ss.pop(); break;
                case PUSH_VAR: is.push(operand == RspsBank.VARP_CUSTOM_QUANTITY ? custom : 0); break;
                case INV_GETOBJ: is.pop(); is.pop(); is.push(4151); break;
                case INV_GETNUM: is.pop(); is.pop(); is.push(bankCount); break;
                case INV_SIZE: is.pop(); is.push(28); break;
                case OC_NAME: is.pop(); ss.push("Abyssal whip"); break;
                case TOSTRING: ss.push(Integer.toString(is.pop())); break;
                case STRING_LENGTH: is.push(ss.pop().length()); break;
                case JOIN_STRING: {
                    String[] parts = new String[operand];
                    for (int i = operand - 1; i >= 0; i--) parts[i] = ss.pop();
                    ss.push(String.join("", parts));
                    break;
                }
                case ADD: case SUB: case MULTIPLY: case DIVIDE: case MODULO: {
                    int b = is.pop();
                    int a = is.pop();
                    is.push(op == ADD ? a + b : op == SUB ? a - b : op == MULTIPLY ? a * b : op == DIVIDE ? a / b : a % b);
                    break;
                }
                case IF_GETWIDTH: is.pop(); is.push(162); break;
                case IF_GETHEIGHT: is.pop(); is.push(250); break;
                case BRANCH: pc += operand; break;
                case BRANCH_IF_TRUE: if (is.pop() == 1) pc += operand; break;
                case BRANCH_NOT: case BRANCH_EQUALS: case BRANCH_LESS_THAN: case BRANCH_GREATER_THAN: {
                    int b = is.pop();
                    int a = is.pop();
                    boolean jump = op == BRANCH_NOT ? a != b : op == BRANCH_EQUALS ? a == b : op == BRANCH_LESS_THAN ? a < b : a > b;
                    if (jump) pc += operand;
                    break;
                }
                case RETURN:
                    check(is.isEmpty() && ss.isEmpty(), "script " + script.id + " leaves the stacks empty");
                    return r;
                case GOSUB_WITH_PARAMS: {
                    check(operand == RspsBank.SCRIPT_INVENTORY_ITEM, "only 154 is called");
                    String[] s = new String[9];
                    for (int i = 8; i >= 0; i--) s[i] = ss.pop();
                    int[] n = new int[6];
                    for (int i = 5; i >= 0; i--) n[i] = is.pop();
                    r.gosubStrings.add(s);
                    r.gosubInts.add(n);
                    break;
                }
                case CC_CREATE: is.pop(); is.pop(); is.pop(); r.created++; break;
                case CC_FIND: is.pop(); is.pop(); is.push(1); break;
                case CC_DELETEALL: is.pop(); break;
                case CC_IF_SETSIZE: case CC_IF_SETPOSITION: is.pop(); is.pop(); is.pop(); is.pop(); break;
                case CC_IF_SETTEXTALIGN: is.pop(); is.pop(); is.pop(); break;
                case CC_IF_SETDRAGGABLE: is.pop(); is.pop(); break;
                case CC_IF_SETGRAPHIC: r.graphic = is.pop(); break;
                case CC_IF_SETHIDE: r.hidden = is.pop(); break;
                case CC_IF_SETCOLOUR: r.colour = is.pop(); break;
                case CC_IF_SETTTRANS: r.transparency = is.pop(); break;
                case CC_IF_SETTEXTFONT: case CC_IF_SETTEXTSHADOW: case CC_IF_SETDRAGDEADZONE: case CC_IF_SETDRAGDEADTIME: is.pop(); break;
                case CC_IF_SETTEXT: r.text = ss.pop(); break;
                case CC_IF_SETOPBASE: r.opBase = ss.pop(); break;
                case CC_IF_CLEAROPS: Arrays.fill(r.ops, null); break;
                case CC_IF_SETOP: {
                    int number = is.pop();
                    String text = ss.pop();
                    check(number >= 1 && number <= 10, "op number " + number);
                    r.ops[number - 1] = text;
                    break;
                }
                case CC_IF_SETOBJECT: case CC_IF_SETOBJECT_NONUM:
                    r.count = is.pop();
                    r.obj = is.pop();
                    r.noNum = op == CC_IF_SETOBJECT_NONUM;
                    break;
                case CC_IF_SETONDRAG: case CC_IF_SETONDRAGCOMPLETE: case CC_IF_SETONMOUSEOVER: case CC_IF_SETONMOUSELEAVE: {
                    String types = ss.pop();
                    for (int i = types.length() - 1; i >= 0; i--) {
                        if (types.charAt(i) == 's') ss.pop(); else is.pop();
                    }
                    r.hooks.add(is.pop());
                    break;
                }
                default:
                    throw new AssertionError("opcode " + op + " is not modelled (script " + script.id + ")");
            }
        }
    }

    static List<String> shown(String[] ops) {
        List<String> out = new ArrayList<>();
        for (String op : ops) out.add(op == null ? "" : op);
        return out;
    }

    /** OSRS script 669 for every default quantity, with and without a custom amount and 'Always set placeholders'. */
    static void itemMenus() {
        Run one = run(RspsBank.itemOps(RspsBank.MODE_ONE, false), new int[] {3}, new String[0], 40, 0);
        equal(Arrays.asList("Withdraw-1", "", "Withdraw-5", "Withdraw-10", "", "Withdraw-X", "Withdraw-All", "Withdraw-All-but-1", "Placeholder", "Examine<col=ff9040>"), shown(one.ops), "default 1");
        equal(40, one.count, "count shown");
        check(!one.noNum && one.transparency == 0, "an item is drawn normally");
        equal("<col=ff981f>Abyssal whip", one.opBase, "opbase");

        Run all = run(RspsBank.itemOps(RspsBank.MODE_ALL, true), new int[] {3}, new String[0], 40, 25);
        equal(Arrays.asList("Withdraw-All", "Withdraw-1", "Withdraw-5", "Withdraw-10", "Withdraw-25", "Withdraw-X", "Withdraw-All", "Withdraw-All-but-1", "", "Examine<col=ff9040>"), shown(all.ops), "default All, n 25, always placeholder");

        equal("Withdraw-25", run(RspsBank.itemOps(RspsBank.MODE_X, false), new int[] {0}, new String[0], 5, 25).ops[0], "default X uses n");
        equal("Withdraw-1", run(RspsBank.itemOps(RspsBank.MODE_X, false), new int[] {0}, new String[0], 5, 0).ops[0], "default X without n is max(1, n)");
        equal("Withdraw-5", run(RspsBank.itemOps(RspsBank.MODE_FIVE, false), new int[] {0}, new String[0], 5, 0).ops[0], "default 5");
        equal("Withdraw-10", run(RspsBank.itemOps(RspsBank.MODE_TEN, false), new int[] {0}, new String[0], 5, 0).ops[0], "default 10");
        equal(2, one.hooks.size(), "drag hooks kept");
        equal(RspsBank.SCRIPT_BANK_DRAG, one.hooks.get(0), "1454");
        equal(RspsBank.SCRIPT_BANK_DRAG_COMPLETE, one.hooks.get(1), "1482");
    }

    /** OSRS script 278: count 0 is a placeholder - no number, transparency 120, "Release" (op8) and "Examine". */
    static void placeholder() {
        Run r = run(RspsBank.itemOps(RspsBank.MODE_ONE, false), new int[] {7}, new String[0], 0, 9);
        equal(Arrays.asList("", "", "", "", "", "", "", "Release", "", "Examine<col=ff9040>"), shown(r.ops), "placeholder ops");
        check(r.noNum && r.count == 0 && r.obj == 4151, "placeholder object without a number");
        equal(RspsBank.PLACEHOLDER_TRANSPARENCY, r.transparency, "placeholder transparency");
    }

    /** OSRS script 172 through the cache's 154: one call per inventory slot with the default's own entry left out. */
    static void sideInventory() {
        Run r = run(RspsBank.sideInventory(RspsBank.MODE_FIVE), new int[] {763 << 16}, new String[0], 1, 0);
        equal(28, r.created, "28 slots");
        equal(28, r.gosubStrings.size(), "154 per slot");
        equal(Arrays.asList("Deposit-5", "Deposit-1", "", "Deposit-10", "Deposit-X", "Deposit-All", "", "", ""), Arrays.asList(r.gosubStrings.get(0)), "default 5");
        equal(Arrays.toString(new int[] {93, 27, 763 << 16, 27, 1, -1}), Arrays.toString(r.gosubInts.get(27)), "154 ints of the last slot");
        Run x = run(RspsBank.sideInventory(RspsBank.MODE_X), new int[] {763 << 16}, new String[0], 1, 12);
        equal("Deposit-12", x.gosubStrings.get(0)[0], "default X");
        Run all = run(RspsBank.sideInventory(RspsBank.MODE_ALL), new int[] {763 << 16}, new String[0], 1, 0);
        equal(Arrays.asList("Deposit-All", "Deposit-1", "Deposit-5", "Deposit-10", "Deposit-X", "", "", "", ""), Arrays.asList(all.gosubStrings.get(0)), "default All");
    }

    /** The bottom-bar / title-row buttons: background sprite by state, label with its ops, hover hooks. */
    static void buttons() {
        int root = 762 << 16;
        Run selected = run(RspsBank.button(), new int[] {root, 0, 104, 18, 25, 22, 2, 1, 1, 495}, new String[] {"1", "Default quantity: 1", "", ""}, 0, 0);
        equal(2, selected.created, "background + label");
        equal(RspsBank.SPRITE_BUTTON_SELECTED, selected.graphic, "selected sprite");
        equal(RspsBank.WHITE, selected.colour, "selected colour");
        equal("1", selected.text, "label");
        equal(Arrays.asList("Default quantity: 1", "", "", "", "", "", "", "", "", ""), shown(selected.ops), "one op");
        equal(2, selected.hooks.size(), "hover in / out");

        Run x = run(RspsBank.button(), new int[] {root, 6, 182, 18, 25, 22, 2, 0, 1, 495}, new String[] {"X", "Default quantity: X", "Set custom quantity", ""}, 0, 0);
        equal(RspsBank.SPRITE_BUTTON, x.graphic, "normal sprite");
        equal(RspsBank.ORANGE, x.colour, "normal colour");
        equal("Set custom quantity", x.ops[1], "second op");

        Run text = run(RspsBank.button(), new int[] {root, 10, 22, 21, 110, 13, 0, 0, 0, 494}, new String[] {"Placeholders: Off", "Enable", "Release all placeholders", "<col=ff9040>Always set placeholders</col>"}, 0, 0);
        equal(1, text.hidden, "no background");
        equal("<col=ff9040>Always set placeholders</col>", text.opBase, "opbase");

        Run hover = run(RspsBank.hover(), new int[] {root, 1, 100}, new String[0], 0, 0);
        equal(100, hover.transparency, "hover transparency");
    }

    static void check(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
        assertions++;
    }

    static void equal(Object expected, Object actual, String what) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but was " + actual);
        }
        assertions++;
    }
}
