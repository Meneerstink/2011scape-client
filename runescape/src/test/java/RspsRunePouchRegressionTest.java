/**
 * RspsRunePouch.inRuneCount (owner bug 2026-10-06 163302): pouch runes count inside the rune counter script 19 AND inside every
 * script it calls - script 19 counts the combination runes (smoke, mud, ...) through GOSUB script 1.
 */
public final class RspsRunePouchRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        ScriptRunner.framePointer = 0;
        check(RspsRunePouch.inRuneCount(19), "script 19 itself counts the pouch");
        check(!RspsRunePouch.inRuneCount(1), "script 1 outside script 19 (e.g. a bank count) does not");

        // Script 19 called script 1 (GOSUB_WITH_PARAMS 1): the frame of 19 is on the stack while 1 runs.
        ClientScript runeCounter = new ClientScript();
        runeCounter.id = 19;
        StackFrame frame = new StackFrame();
        frame.script = runeCounter;
        ScriptRunner.frames[0] = frame;
        ScriptRunner.framePointer = 1;
        check(RspsRunePouch.inRuneCount(1), "script 1 called from script 19 counts the pouch (combination runes)");

        // A different caller does not.
        ClientScript other = new ClientScript();
        other.id = 2000;
        frame.script = other;
        check(!RspsRunePouch.inRuneCount(1), "script 1 called from another script does not");

        ScriptRunner.frames[0] = null;
        ScriptRunner.framePointer = 0;
        System.out.println("RspsRunePouchRegressionTest OK (" + assertions + " assertions)");
    }

    private static void check(boolean condition, String what) {
        assertions++;
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
