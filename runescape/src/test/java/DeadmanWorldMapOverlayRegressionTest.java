public final class DeadmanWorldMapOverlayRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        check(DeadmanWorldMapOverlay.projectX(3000, 3000, 0, 100, 50, 250) == 50,
            "the map origin projects to the left viewport edge");
        check(DeadmanWorldMapOverlay.projectX(3100, 3000, 0, 100, 50, 250) == 250,
            "an absolute world tile is converted through areaX before projection");
        check(DeadmanWorldMapOverlay.projectZ(3000, 3000, 0, 100, 50, 250) == 250,
            "the map origin projects to the lower viewport edge");
        check(DeadmanWorldMapOverlay.projectZ(3100, 3000, 0, 100, 50, 250) == 50,
            "an absolute world tile is converted through areaZ before projection");
        // Screen square 10..20 x 10..20 (y down), listed clockwise on screen.
        double[] xs = { 10, 20, 20, 10 };
        double[] ys = { 10, 10, 20, 20 };
        double[] out = new double[4];
        check(DeadmanWorldMapOverlay.crossingsAt(xs, ys, 15.5, out) == 2 && out[0] == 10 && out[1] == 20,
            "a row through the zone is filled from its left edge to its right edge");
        check(DeadmanWorldMapOverlay.crossingsAt(xs, ys, 25.5, out) == 0, "a row outside the zone is not filled");
        check(DeadmanWorldMapOverlay.signedArea2(xs, ys) > 0,
            "positive area: the (dy, -dx) normal of edge 10,10 -> 20,10 is (0, -1), i.e. outside (the red side)");
        double[] reversedX = { 10, 10, 20, 20 };
        double[] reversedY = { 10, 20, 20, 10 };
        check(DeadmanWorldMapOverlay.signedArea2(reversedX, reversedY) < 0, "the opposite winding flips the outward sign");
        System.out.println("Deadman worldmap overlay regression checks passed: " + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
