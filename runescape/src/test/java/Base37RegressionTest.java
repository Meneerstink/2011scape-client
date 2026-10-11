import com.jagex.core.stringtools.general.Base37;
import com.jagex.core.stringtools.general.NameTools;

/**
 * Night run 2026-10-12: the refactored Base37 computed its bounds with encode() during class initialisation, which read a
 * bound that was still 0 - encode() kept only the first character and decode() returned null for every name longer than
 * one letter. The Friends Chat tab then showed "Not in chat" after every join (FriendChat.name = Base37.decode(...)).
 * Values are the 2011 client's (base 37, '_' = 0, a-z = 1-26, 0-9 = 27-36).
 */
public final class Base37RegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        check(Base37.encode("nighttest") == 50047652589458L, "encode keeps every character: " + Base37.encode("nighttest"));
        check("nighttest".equals(Base37.decode(50047652589458L)), "decode of a real channel name: " + Base37.decode(50047652589458L));
        for (String name : new String[] {"a", "zezima", "anudd", "anuddd", "sumtest", "mod_ash", "999999999999", "abcdefghijkl"}) {
            check(name.equals(Base37.decode(Base37.encode(name))), "round trip " + name + " -> " + Base37.decode(Base37.encode(name)));
        }
        check(Base37.decode(0L) == null && Base37.decode(-5L) == null, "zero / negative are not names");
        check(Base37.decode(37L) == null, "a trailing space symbol is never produced");
        check(Base37.decode(6582952005840035281L) == null, "37^12 and above are rejected");
        check(Base37.decode(6582952005840035280L) != null, "37^12 - 1 (twelve 9s) is the largest name");
        check(Base37.encode("abcdefghijklmnop") == Base37.encode("abcdefghijkl"), "names stop at twelve symbols");
        // The display form joins words with the 2011 non-breaking space (NameTools.isWhitespace counts it).
        check("Mod\u00A0Ash".equals(Base37.decodeName(Base37.encode("mod ash"))), "display form: " + Base37.decodeName(Base37.encode("mod ash")));
        check("Nighttest".equals(NameTools.normalise("nighttest")), "NameTools.normalise: " + NameTools.normalise("nighttest"));
        System.out.println("Base37 regression checks passed: " + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
