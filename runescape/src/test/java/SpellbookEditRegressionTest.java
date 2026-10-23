import java.util.Arrays;
import java.util.List;

/** Guards N189: the player's spell order completes with new spells in cache order, and a drag moves a spell to the target's place. */
public final class SpellbookEditRegressionTest {

    public static void main(String[] args) {
        List<Integer> cache = Arrays.asList(1, 2, 3, 4, 5);
        require(RspsSpellbookEdit.ordered(cache, null).equals(cache), "no saved order: the cache's");
        require(RspsSpellbookEdit.ordered(cache, Arrays.asList(4, 1)).equals(Arrays.asList(4, 1, 2, 3, 5)), "saved first, the rest in cache order");
        require(RspsSpellbookEdit.ordered(Arrays.asList(1, 2, 3), Arrays.asList(9, 3, 1)).equals(Arrays.asList(3, 1, 2)), "spells not shown now are skipped");
        require(RspsSpellbookEdit.move(cache, 5, 2).equals(Arrays.asList(1, 5, 2, 3, 4)), "drag 5 onto 2: 5 takes 2's place");
        require(RspsSpellbookEdit.move(cache, 1, 4).equals(Arrays.asList(2, 3, 4, 1, 5)), "drag 1 onto 4");
        require(RspsSpellbookEdit.move(cache, 3, 3).equals(cache), "onto itself: nothing");
        require(RspsSpellbookEdit.move(cache, 3, 9).equals(cache), "onto something not in the book: nothing");
        System.out.println("SpellbookEditRegressionTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
