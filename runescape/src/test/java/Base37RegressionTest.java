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
        // A34 round 2 (2026-10-14): the exact g8 channel names our server sends in UPDATE_FRIENDCHAT_CHANNEL_FULL
        // (gg.rsmod.util.Base37.encode, server test FriendsChatWireTests) - spaces, digits, twelve characters - must come out of the
        // client as the same name: raw form for FriendChat.name, display form for the tab.
        String[][] serverSent = {
            {"34402101396", "mod_ash", "Mod Ash"},
            {"187939216216112118", "abcdefghijkl", "Abcdefghijkl"},
            {"735858658289993200", "deadman_2026", "Deadman 2026"},
            {"2726988998106585767", "old_school_1", "Old School 1"},
            {"35630", "z_9", "Z 9"},
            {"41871544023", "pk3r_99", "Pk3r 99"},
            {"8447777363642384", "a1_b2_c3_d4", "A1 B2 C3 D4"},
        };
        for (String[] sent : serverSent) {
            long value = Long.parseLong(sent[0]);
            check(sent[1].equals(Base37.decode(value)), "server channel name " + sent[0] + " -> " + Base37.decode(value));
            String shown = Base37.decodeName(value);
            check(shown != null && sent[2].equals(shown.replace(' ', ' ')), "server channel name shown " + sent[0] + " -> " + shown);
            check(value == Base37.encode(sent[1]), "client encodes " + sent[1] + " like the server: " + Base37.encode(sent[1]));
        }
        System.out.println("Base37 regression checks passed: " + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
