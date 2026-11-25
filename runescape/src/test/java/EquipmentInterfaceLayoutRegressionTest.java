public final class EquipmentInterfaceLayoutRegressionTest {

    public static void main(String[] args) {
        Component equipmentAura = new Component();
        EquipmentInterfaceLayout.apply(387, 48, equipmentAura);
        require(equipmentAura.hidden, "387:48 aura layer must be hidden");

        Component bonusesAura = new Component();
        EquipmentInterfaceLayout.apply(667, 14, bonusesAura);
        require(bonusesAura.hidden, "667:14 aura placeholder must be hidden");

        // Owner 2026-11-25 (zz): CS2 2373's cc child 14 of 667:7 (empty aura/quiver silhouette) follows 667:14's hidden flag.
        Component[][] saved = InterfaceList.interfaces;
        try {
            InterfaceList.interfaces = new Component[700][];
            InterfaceList.interfaces[667] = new Component[20];
            InterfaceList.interfaces[667][14] = bonusesAura;
            Component child = new Component();
            child.slot = child.layer = (667 << 16) | 7;
            child.id = 14;
            require(InterfaceManagerHidden.of(child), "667:7 child 14 must hide while 667:14 is hidden");
            bonusesAura.hidden = false;
            require(!InterfaceManagerHidden.of(child), "667:7 child 14 must show while 667:14 is shown (quiver worn)");
            Component other = new Component();
            other.slot = other.layer = (667 << 16) | 7;
            other.id = 13;
            require(!InterfaceManagerHidden.of(other), "other 667:7 children unaffected");
        } finally {
            InterfaceList.interfaces = saved;
        }

        Component otherTitle = new Component();
        EquipmentInterfaceLayout.apply(667, 30, otherTitle);
        require(otherTitle.originalY == 159, "Other Bonuses title y");

        int[] expectedRows = {173, 186, 199, 212};
        for (int i = 0; i < expectedRows.length; i++) {
            Component row = new Component();
            EquipmentInterfaceLayout.apply(667, 45 + i, row);
            require(row.originalY == expectedRows[i], "bonus row " + (45 + i) + " y");
        }

        Component unrelated = new Component();
        unrelated.originalY = 77;
        EquipmentInterfaceLayout.apply(667, 31, unrelated);
        require(!unrelated.hidden && unrelated.originalY == 77, "unrelated component changed");
    }

    private static final class InterfaceManagerHidden {
        static boolean of(Component component) {
            return EquipmentInterfaceLayout.hiddenOverride(component);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
