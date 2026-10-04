/**
 * RSPS rune pouch in the 667 spellbook (owner bug 2026-10-04 024552: "the runes in a rune pouch are not recognised by the
 * spellbook"). The cache's rune counter, script 19 (rune id -> staff 99999999 or the inventory total of the rune and its
 * combination runes, used by every spellbook redraw), only reads inventory 93. The server sends the carried pouch's runes as
 * client inventory {@link #INV} right before inventory 93 (Player.inventoryFlushHook), and an INV_TOTAL on 93 executed inside
 * script 19 adds the same rune from that inventory - as OSRS counts pouch runes towards a spell. Nothing else reads {@link #INV}.
 */
public final class RspsRunePouch {

    /** Client-only inventory id for the pouch contents (not an InvType of the cache; ClientInventory accepts any id). */
    public static final int INV = 30001;

    /** The cache's rune counter script. */
    static final int RUNE_COUNT_SCRIPT = 19;

    static final int INVENTORY = 93;

    private RspsRunePouch() {
    }

    /** Runes of [obj] in the pouch that the rune counter adds to an INV_TOTAL on [inv]. */
    static int extra(int inv, int obj, int scriptId) {
        if (inv != INVENTORY || scriptId != RUNE_COUNT_SCRIPT) {
            return 0;
        }
        return ClientInventory.total(INV, obj, false);
    }
}
