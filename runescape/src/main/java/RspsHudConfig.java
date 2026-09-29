import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Overlay settings, pushed by the server from the account's saved `hud` settings (server ClientHudConfig). The defaults
 * here only apply until the login push arrives and match the server's defaults.
 */
final class RspsHudConfig {

    static boolean boosts = true;
    static boolean boostsAll = false;
    static boolean opponent = true;
    static boolean groundItems = true;
    static boolean despawn = true;
    static boolean value = true;
    static boolean timers = true;

    /** Value tiers low / medium / high / insane, in gp of the whole stack. */
    static long[] tiers = {20_000L, 100_000L, 1_000_000L, 10_000_000L};

    /** default, low, medium, high, insane, highlight (ARGB). */
    static int[] colours = {0xFFFFFFFF, 0xFF66B2FF, 0xFF99FF99, 0xFFFF9600, 0xFFFF66B2, 0xFFAA00FF};

    private static final List<Pattern> highlighted = new ArrayList<Pattern>();
    private static final List<Pattern> hidden = new ArrayList<Pattern>();

    private RspsHudConfig() {
    }

    static void handle(String kind, String rest) {
        if (kind.equals("cfg")) {
            String[] parts = rest.split("\\|");
            String flags = parts[0];
            boosts = flags.charAt(0) == '1';
            boostsAll = flags.charAt(1) == '1';
            opponent = flags.charAt(2) == '1';
            groundItems = flags.charAt(3) == '1';
            despawn = flags.charAt(4) == '1';
            value = flags.charAt(5) == '1';
            timers = flags.charAt(6) == '1';
            String[] tierText = parts[1].split(",");
            for (int i = 0; i < tiers.length && i < tierText.length; i++) {
                tiers[i] = Long.parseLong(tierText[i]);
            }
            String[] colourText = parts[2].split(",");
            for (int i = 0; i < colours.length && i < colourText.length; i++) {
                colours[i] = 0xFF000000 | Integer.parseInt(colourText[i], 16);
            }
            return;
        }
        List<Pattern> list = kind.equals("hl") ? highlighted : hidden;
        if (rest.equals("clear")) {
            list.clear();
        } else if (rest.startsWith("add|")) {
            for (String name : rest.substring(4).split(",")) {
                if (name.length() > 0) {
                    list.add(wildcard(name));
                }
            }
        }
    }

    /** RuneLite WildcardMatcher: '*' matches anything, the rest literally, case-insensitive, whole name. */
    private static Pattern wildcard(String name) {
        StringBuilder regex = new StringBuilder();
        String[] parts = name.split("\\*", -1);
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(parts[i]));
        }
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    static boolean isHighlighted(String name) {
        return matches(highlighted, name);
    }

    static boolean isHidden(String name) {
        return matches(hidden, name);
    }

    private static boolean matches(List<Pattern> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Colour of a stack worth {@code total} gp: the highest tier it reaches, else the default colour. */
    static int valueColour(long total) {
        for (int i = tiers.length - 1; i >= 0; i--) {
            if (total >= tiers[i]) {
                return colours[i + 1];
            }
        }
        return colours[0];
    }

    static int highlightColour() {
        return colours[5];
    }
}
