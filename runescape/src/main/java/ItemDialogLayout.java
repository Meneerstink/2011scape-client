/**
 * CONFIRM-ICOON-KLEIN (owner 2026-11-25: "in de chatdialoog de maxcape heel klein ... vermoedelijk zijn er meer osrs items die dit
 * hebben"): the 667 destroy / confirm dialog (interface 94, used for Destroy and confirmItemAction, e.g. combining a max cape) draws
 * its item in 94:9, a 32 x 32 GRAPHIC. Item sprites are 36 x 32, so RspsObjSmooth squeezed every item to 89% width and the
 * one-pixel outline averaged away - a tall cape looked about 75% as wide as in the inventory. OSRS shows the item at inventory size.
 * The slot is now 36 x 32 around the same centre, so the item sprite is drawn as it is (no shrink) for every item.
 */
final class ItemDialogLayout {

    static final int CONFIRM_DIALOG = 94;
    static final int ITEM_SLOT = 9;

    private ItemDialogLayout() {
    }

    static void apply(int interfaceId, int slot, Component component) {
        if (interfaceId != CONFIRM_DIALOG || slot != ITEM_SLOT || component.type != Component.TYPE_GRAPHIC) {
            return;
        }
        if (component.originalWidth == 32 && component.originalHeight == 32) {
            component.originalWidth = 36;
            component.originalX -= 2;
        }
    }
}
