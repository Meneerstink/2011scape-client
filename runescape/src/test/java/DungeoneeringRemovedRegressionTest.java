/** Guards DGN (client half): the stats tab's Dungeoneering cell 320:151 loads hidden and the script total skips skill 24. */
public final class DungeoneeringRemovedRegressionTest {

    public static void main(String[] args) {
        Component cell = new Component();
        DungeoneeringRemoved.apply(320, 151, cell);
        require(cell.hidden, "320:151 hidden");
        Component attack = new Component();
        DungeoneeringRemoved.apply(320, 195, attack);
        require(!attack.hidden, "other stats cells stay");
        Component other = new Component();
        DungeoneeringRemoved.apply(321, 151, other);
        require(!other.hidden, "other interfaces untouched");
        require(DungeoneeringRemoved.scriptBaseLevel(24, 1) == 0, "Dungeoneering counts 0 in the total");
        require(DungeoneeringRemoved.scriptBaseLevel(23, 99) == 99 && DungeoneeringRemoved.scriptBaseLevel(0, 99) == 99, "every other skill unchanged");
        System.out.println("DungeoneeringRemovedRegressionTest OK");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
