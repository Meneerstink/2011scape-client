import java.util.HashMap;
import java.util.Map;

/** Guards N358: Rigour / Augury cells of the prayer book stay grey until their unlock varbit is set. */
public final class PrayerScrollUnlocksRegressionTest {

    public static void main(String[] args) {
        Map<Integer, Integer> vars = new HashMap<>();
        PrayerScrollUnlocks.VarbitReader reader = id -> vars.getOrDefault(id, 0);
        int[] rigour = new int[] {74, 0, 0, (271 << 16) | 8, 28};
        int[] augury = new int[] {77, 0, 0, (271 << 16) | 8, 29};
        int[] piety = new int[] {70, 0, 0, (271 << 16) | 8, 27};
        require(PrayerScrollUnlocks.baseLevel(52, 5, 99, rigour, reader) == 0, "Rigour grey without the scroll");
        require(PrayerScrollUnlocks.baseLevel(52, 5, 99, augury, reader) == 0, "Augury grey without the scroll");
        require(PrayerScrollUnlocks.baseLevel(52, 5, 99, piety, reader) == 99, "other prayers unchanged");
        vars.put(5451, 1);
        require(PrayerScrollUnlocks.baseLevel(52, 5, 99, rigour, reader) == 99, "Rigour lit once learned");
        require(PrayerScrollUnlocks.baseLevel(52, 5, 99, augury, reader) == 0, "Augury still grey");
        vars.put(6840, 1);
        require(PrayerScrollUnlocks.baseLevel(52, 5, 99, augury, reader) == 99, "curses book untouched");
        vars.put(6840, 0);
        require(PrayerScrollUnlocks.baseLevel(53, 5, 99, augury, reader) == 99, "other scripts untouched");
        require(PrayerScrollUnlocks.baseLevel(52, 6, 99, augury, reader) == 99, "other skills untouched");
        require(PrayerScrollUnlocks.baseLevel(52, 5, 99, new int[] {77, 0, 0, (320 << 16) | 8, 29}, reader) == 99, "other interfaces untouched");
        System.out.println("PrayerScrollUnlocksRegressionTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
