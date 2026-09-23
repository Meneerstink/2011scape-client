public final class EquipmentInterfaceLayoutRegressionTest {

    public static void main(String[] args) {
        Component equipmentAura = new Component();
        EquipmentInterfaceLayout.apply(387, 48, equipmentAura);
        require(equipmentAura.hidden, "387:48 aura layer must be hidden");

        Component bonusesAura = new Component();
        EquipmentInterfaceLayout.apply(667, 14, bonusesAura);
        require(bonusesAura.hidden, "667:14 aura placeholder must be hidden");

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

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
