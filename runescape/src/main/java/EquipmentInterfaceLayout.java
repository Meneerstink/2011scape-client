/**
 * Small, cache-compatible presentation corrections for the revision-667 equipment interfaces.
 * Gameplay equipment remains server-owned; this only removes the cache's unused slot-14 aura
 * placeholder and compacts rows whose old Absorb/Summoning labels are no longer displayed.
 */
public final class EquipmentInterfaceLayout {

    static final int EQUIPMENT_TAB = 387;
    static final int EQUIPMENT_BONUSES = 667;

    private EquipmentInterfaceLayout() {
    }

    /** Background sprite of the worn ammo slot (387:37), reused for the Dizana's quiver slot. */
    private static int ammoSlotBackground = -1;

    /** 667:14 while interface 667 is decoded, until the ammo slot 667:17 supplies its background. */
    private static Component bonusesQuiverSlot;

    /** 667:7, the worn-items layer CS2 2373 fills with one cc_create child per inv-94 slot (child id = slot). */
    static final int BONUSES_GRID = (EQUIPMENT_BONUSES << 16) | 7;

    /** Enum 796 (key = equipment slot) = the empty-slot silhouettes CS2 2373 draws; key 13 = ammo (arrows). */
    private static final int SLOT_SILHOUETTE_ENUM = 796;

    private static boolean isBonusesQuiverChild(Component component) {
        return component.slot == BONUSES_GRID && component.id == 14 && component.layer == BONUSES_GRID;
    }

    /**
     * Owner 2026-11-25 (picture zz): Equipment Bonuses showed the old aura silhouette right of the head slot without a
     * Dizana's quiver worn. CS2 2373 draws the empty slot-14 silhouette as a cc_create child of 667:7 at 667:14's position;
     * hiding 667:14 (server, quiver not worn) did not hide that child. The child now follows 667:14's hidden flag.
     */
    public static boolean hiddenOverride(Component component) {
        if (!isBonusesQuiverChild(component)) {
            return component.hidden;
        }
        Component[] bonuses = InterfaceList.interfaces == null ? null : InterfaceList.interfaces[EQUIPMENT_BONUSES];
        Component slot = bonuses == null || bonuses.length <= 14 ? null : bonuses[14];
        return component.hidden || slot == null || slot.hidden;
    }

    /** 387:50, the item component of the worn tab's quiver slot (old aura slot); its CS2 sets the empty-slot silhouette. */
    static final int WORN_QUIVER_ITEM = (EQUIPMENT_TAB << 16) | 50;

    /**
     * The empty quiver slot shows the ammo slot's arrow silhouette (OSRS), not the cache's aura icon - in the Equipment
     * Bonuses grid (cc child 14 of 667:7) and in the Worn Equipment tab (387:50; owner 2026-11-25: quiver worn + empty
     * showed the aura icon).
     */
    public static int graphicOverride(Component component, int graphic) {
        boolean wornQuiver = component.slot == WORN_QUIVER_ITEM && component.id == -1;
        if (graphic <= 0 || (!wornQuiver && !isBonusesQuiverChild(component))) {
            return graphic;
        }
        try {
            int arrows = com.jagex.game.runetek6.config.enumtype.EnumTypeList.instance.list(SLOT_SILHOUETTE_ENUM).getInt(13);
            return arrows > 0 ? arrows : graphic;
        } catch (RuntimeException e) {
            return graphic;
        }
    }

    public static void apply(int interfaceId, int slot, Component component) {
        if (interfaceId == EQUIPMENT_TAB && slot == 37) {
            ammoSlotBackground = component.graphic;
            return;
        }
        if (interfaceId == EQUIPMENT_TAB && slot == 48) {
            // Parent layer for 387:48..50, the cache's slot-14 aura placeholder. The server has no
            // aura slot; it is reused as Dizana's quiver's second ammunition slot (owner 2026-09-18,
            // OSRS: "the extra ammunition slot above the original slot"). Hidden until the server
            // shows it (quiver worn); moved from the top-left to directly above the ammo slot 387:36.
            component.hidden = true;
            component.originalX = 41;
            return;
        }
        if (interfaceId == EQUIPMENT_TAB && slot == 49 && ammoSlotBackground != -1) {
            // Arrow silhouette like the ammo slot instead of the aura icon.
            component.graphic = ammoSlotBackground;
            return;
        }

        if (interfaceId != EQUIPMENT_BONUSES) {
            return;
        }

        if (slot == 14) {
            // Slot-14 aura placeholder in the worn-items grid = Dizana's quiver's second ammunition slot
            // (owner 2026-09-19 picture "quiver bug": the stored ammo was drawn top-left). CS2 2373
            // places the inv-94 slot-14 item at this component's position, so moving it directly
            // above the ammo slot (667:17, x 41) moves the item with it. Hidden until the server
            // shows it (quiver worn); its background becomes the ammo slot's arrow silhouette.
            component.hidden = true;
            component.originalX = 41;
            bonusesQuiverSlot = component;
        } else if (slot == 17) {
            if (bonusesQuiverSlot != null) {
                bonusesQuiverSlot.graphic = component.graphic;
                bonusesQuiverSlot = null;
            }
        } else if (slot == 30) {
            // "Other Bonuses" title: directly below the five defence rows.
            component.originalY = 159;
        } else if (slot >= 45 && slot <= 48) {
            // Melee STR, Ranged STR, Magic DMG and Prayer at the cache's normal 13-pixel row
            // rhythm, immediately below the moved section title. The server writes the matching
            // labels/bonus indices in PlayerExt.refreshBonuses.
            component.originalY = 173 + (slot - 45) * 13;
        }
    }
}
