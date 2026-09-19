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
        System.out.println("Deadman worldmap overlay regression checks passed: " + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
