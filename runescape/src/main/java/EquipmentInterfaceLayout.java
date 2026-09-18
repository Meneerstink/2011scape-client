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
            // Matching slot-14 aura placeholder in the worn-items grid.
            component.hidden = true;
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
