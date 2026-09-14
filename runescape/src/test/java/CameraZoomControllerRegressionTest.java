public final class CameraZoomControllerRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        check(CameraZoomController.adjustedZoom(320, -1) == 305, "Wheel up zooms one bounded step");
        check(CameraZoomController.adjustedZoom(320, 1) == 335, "Wheel down zooms one bounded step");
        check(CameraZoomController.adjustedZoom(155, -20) == CameraZoomController.MIN_ZOOM, "Lower bound clamps");
        check(CameraZoomController.adjustedZoom(655, 20) == CameraZoomController.MAX_ZOOM, "Upper bound clamps");
        check(CameraZoomController.adjustedZoom(320, Integer.MAX_VALUE) == CameraZoomController.MAX_ZOOM, "Large wheel input cannot overflow");

        check(consume(true, false, false, false, false, false), "Open scene accepts wheel zoom");
        check(!consume(false, false, false, false, false, false), "Outside scene preserves interface scrolling");
        check(!consume(true, true, false, false, false, false), "Hovered interface owns its wheel input");
        check(!consume(true, false, true, false, false, false), "Modal bank-style interface blocks zoom");
        check(!consume(true, false, false, true, false, false), "Open context menu blocks zoom");
        check(!consume(true, false, false, false, true, false), "Debug console blocks zoom");
        check(!consume(true, false, false, false, false, true), "Staff plane shortcut keeps wheel input");
        check(!CameraZoomController.shouldConsume(0, true, false, false, false, false, false), "Zero rotation is ignored");

        // RCV-012.B15: npc dialogues are MODAL sub-interfaces in the chatbox pane 752 and must not block world zoom.
        check(!CameraZoomController.blocksWorldZoom(true, 752), "Chatbox dialogue (752) leaves world zoom available");
        check(CameraZoomController.blocksWorldZoom(true, 548), "Main-screen modal (fixed gameframe 548) still blocks zoom");
        check(CameraZoomController.blocksWorldZoom(true, 746), "Main-screen modal (resizable gameframe 746) still blocks zoom");
        check(!CameraZoomController.blocksWorldZoom(false, 548), "Non-modal overlays never block zoom");

        CameraZoomController.restoreZoom(320);
        check(CameraZoomController.applyWheelRotation(1, true, false, false, false, false, false), "Eligible wheel input is consumed");
        check(CameraZoomController.getZoom() == 335, "Consumed wheel input updates the real camera zoom field");
        check(!CameraZoomController.applyWheelRotation(1, true, true, false, false, false, false) && CameraZoomController.getZoom() == 335,
            "Blocked interface input leaves the real camera unchanged");

        System.out.println("Camera zoom regression checks passed: " + assertions);
    }

    private static boolean consume(
        boolean overScene,
        boolean claimed,
        boolean modal,
        boolean menu,
        boolean console,
        boolean staffShortcut
    ) {
        return CameraZoomController.shouldConsume(1, overScene, claimed, modal, menu, console, staffShortcut);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
