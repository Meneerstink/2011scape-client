import static com.jagex.core.constants.ClientScriptOpCode.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Guards RspsGrandExchange.repadSets (owner bug 211448, 2026-10-03: "grand exchange right button sets the layout is not centered
 * correctly"). Data from the 667 cache (decoded 2026-10-04): enum 1087 lists the 115 sets in this order, and script 676 pads
 * eleven categories with fixed constants. Akrisae's set (21768) sits after the six Barrows sets but the pads never counted it,
 * so every later category started in column 1. The repadded script must start every category in column 0, and for the
 * roster the pads were written for (without 21768) reproduce Jagex's own constants exactly.
 */
public final class GeSetsLayoutRegressionTest {

    /** Enum 1087 values 1..115 (667 cache). */
    private static final int[] ROSTER = {
        11814, 11816, 11818, 11820, 11822, 11824, 11826, 11828, 11830, 11832, 11834, 11836, 11838, 11840, 11842, 11844, 14529, 14531,
        11846, 11848, 11850, 11852, 11854, 11856, 21768, 11858, 11860, 11862, 19580, 11864, 11866, 11868, 11870, 11872, 11960, 11962,
        11874, 11876, 19520, 19530, 19522, 19532, 19524, 19534, 19526, 19536, 19528, 19538, 11878, 11880, 11882, 11884, 19540, 19550,
        19542, 19552, 19544, 19554, 19546, 19556, 19548, 19558, 11886, 11888, 11890, 11892, 19560, 19570, 19562, 19572, 19564, 19574,
        19566, 19576, 19568, 19578, 11894, 11896, 11898, 11900, 11902, 11904, 11906, 11908, 11910, 11912, 11914, 11916, 11918, 11920,
        11922, 11924, 19582, 19586, 19584, 11926, 11932, 11928, 11934, 11930, 11936, 19592, 19594, 19588, 19590, 19596, 19598, 11938,
        11940, 11942, 11944, 11946, 11967, 14525, 14527,
    };

    /** Script 676's pads in the 667 cache: set id -> constant added to the position before that set. */
    private static final int[][] CACHE_PADS = {
        {11846, 2}, {11858, 4}, {11864, 6}, {11872, 6}, {19520, 5}, {11902, 8}, {11908, 7}, {11926, 8}, {11942, 6}, {11967, 7}, {14525, 9},
    };

    private static final int AKRISAE_SET = 21768;

    public static void main(String[] args) {
        // The cache script as it is: categories after the Barrows sets start in column 1 (the reported layout).
        Map<Integer, Integer> cache = new HashMap<>();
        for (int[] pad : CACHE_PADS) {
            cache.put(pad[0], pad[1]);
        }
        require(startColumn(ROSTER, cache, 11858) == 1, "cache 676 starts the next category in column 1 (the bug)");

        // Repadded: every category starts in column 0, both passes get the same pad.
        ClientScript script = script();
        require(RspsGrandExchange.repadSets(script, list(ROSTER)), "676's pads are found");
        Map<Integer, Integer> fixed = pads(script);
        require(fixed.size() == CACHE_PADS.length, "all eleven pads found: " + fixed);
        for (int[] pad : CACHE_PADS) {
            require(startColumn(ROSTER, fixed, pad[0]) == 0, "set " + pad[0] + " starts a row");
        }
        require(fixed.get(11858) == 3, "after the seven Barrows sets (Akrisae's included) the pad is 3, was 4");
        require(positions(ROSTER, fixed)[ROSTER.length - 1] / RspsGrandExchange.SETS_COLUMNS < 19, "no more rows than the cache layout");

        // For the roster the pads were written for (no Akrisae's set) the recomputed pads are exactly Jagex's constants.
        int[] original = Arrays.stream(ROSTER).filter(id -> id != AKRISAE_SET).toArray();
        ClientScript jagex = script();
        require(RspsGrandExchange.repadSets(jagex, list(original)), "pads found");
        Map<Integer, Integer> recomputed = pads(jagex);
        for (int[] pad : CACHE_PADS) {
            require(recomputed.get(pad[0]) == pad[1], "set " + pad[0] + ": Jagex pad " + pad[1] + ", recomputed " + recomputed.get(pad[0]));
        }

        // A script without the pad pattern is left to the cache.
        RspsGrandExchange.Cs2 other = new RspsGrandExchange.Cs2();
        other.i(1).op(RETURN);
        require(!RspsGrandExchange.repadSets(other.build(RspsGrandExchange.SETS_LAYOUT, 0, 0, 0, 0), list(ROSTER)), "no pads -> unchanged");
        System.out.println("GeSetsLayoutRegressionTest OK");
    }

    /** The pad blocks of 676 (item pass and marker pass) with the cache's constants. */
    private static ClientScript script() {
        RspsGrandExchange.Cs2 c = new RspsGrandExchange.Cs2();
        for (int pass = 0; pass < 2; pass++) {
            for (int[] pad : CACHE_PADS) {
                c.i(105).i(79).i(RspsGrandExchange.SETS_ENUM).iload(0).i(1).op(ADD).op(ENUM);
                c.i(pad[0]).op(BRANCH_EQUALS, 1).op(BRANCH, 4);
                c.iload(RspsGrandExchange.SETS_POSITION_LOCAL).i(pad[1]).op(ADD).op(POP_INT_LOCAL, RspsGrandExchange.SETS_POSITION_LOCAL);
            }
        }
        c.op(RETURN);
        return c.build(RspsGrandExchange.SETS_LAYOUT, 6, 0, 0, 0);
    }

    /** set id -> pad as the script now holds it; both passes must agree. */
    private static Map<Integer, Integer> pads(ClientScript script) {
        Map<Integer, Integer> pads = new HashMap<>();
        for (int k = 0; k + 6 < script.opcodes.length; k++) {
            if (script.opcodes[k] == PUSH_CONSTANT_INT && script.opcodes[k + 1] == BRANCH_EQUALS && script.opcodes[k + 4] == PUSH_CONSTANT_INT) {
                Integer before = pads.put(script.intOperands[k], script.intOperands[k + 4]);
                require(before == null || before == script.intOperands[k + 4], "both passes pad set " + script.intOperands[k] + " alike");
            }
        }
        return pads;
    }

    private static int[] positions(int[] roster, Map<Integer, Integer> pads) {
        int[] positions = new int[roster.length];
        int position = 0;
        for (int i = 0; i < roster.length; i++) {
            position += pads.getOrDefault(roster[i], 0);
            positions[i] = position++;
        }
        return positions;
    }

    private static int startColumn(int[] roster, Map<Integer, Integer> pads, int set) {
        int[] positions = positions(roster, pads);
        for (int i = 0; i < roster.length; i++) {
            if (roster[i] == set) {
                return positions[i] % RspsGrandExchange.SETS_COLUMNS;
            }
        }
        throw new AssertionError("set " + set + " not in the roster");
    }

    private static List<Integer> list(int[] values) {
        List<Integer> list = new ArrayList<>();
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
