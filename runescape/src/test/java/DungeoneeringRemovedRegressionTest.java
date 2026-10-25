/** Guards DGN (client half): the stats tab's Dungeoneering skill parts stay hidden, its frame holds the total, the total skips skill 24. */
public final class DungeoneeringRemovedRegressionTest {

    public static void main(String[] args) {
        Component cell = new Component();
        cell.originalX = 3;
        cell.originalY = 229;
        DungeoneeringRemoved.apply(320, 151, cell);
        require(!cell.hidden && cell.originalX == 65 && cell.originalY == 229, "N356: the old cell's frame moves to the middle column");
        for (int slot = 153; slot <= 158; slot++) {
            Component part = new Component();
            DungeoneeringRemoved.apply(320, slot, part);
            require(part.hidden, "320:" + slot + " (Dungeoneering icon/levels/lines/ops) hidden");
        }
        Component frame = new Component();
        DungeoneeringRemoved.apply(320, 152, frame);
        require(!frame.hidden, "320:152 the cell frame stays");
        Component attack = new Component();
        DungeoneeringRemoved.apply(320, 195, attack);
        require(!attack.hidden, "other stats cells stay");
        Component total = new Component();
        total.originalX = 65;
        total.originalY = 231;
        total.originalWidth = 122;
        DungeoneeringRemoved.apply(320, 201, total);
        require(total.originalX == 65 && total.originalY == 229 && total.originalWidth == 60 && total.originalHeight == 27, "N356: Total level fills the cell");
        require(total.textAlignX == 1 && total.textAlignY == 1, "centred both ways");
        require("Total level:<br>2376".equals(DungeoneeringRemoved.text((320 << 16) | 201, "Total level: 2376")), "two lines");
        require("Total level: 2376".equals(DungeoneeringRemoved.text((320 << 16) | 200, "Total level: 2376")), "other components untouched");
        Component other = new Component();
        DungeoneeringRemoved.apply(321, 153, other);
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
