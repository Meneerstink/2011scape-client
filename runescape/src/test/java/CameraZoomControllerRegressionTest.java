public final class CameraZoomControllerRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        // RuneLite Camera plugin off (its default): OSRS's own zoom - script 39 step 25, limits 128..896, 512 default.
        RspsHudConfig.resetToDefaults();
        check(CameraZoomController.adjustedZoom(512, -1) == 537, "Wheel up zooms in one 25 step (script 39)");
        check(CameraZoomController.adjustedZoom(512, 1) == 487, "Wheel down zooms out one 25 step");
        check(CameraZoomController.adjustedZoom(150, 20) == 128, "Outer limit clamps at 128");
        check(CameraZoomController.adjustedZoom(880, -20) == 896, "Inner limit clamps at 896");
        check(CameraZoomController.adjustedZoom(512, Integer.MAX_VALUE) == 128, "Large wheel input cannot overflow");
        // OSRS viewport_setfov: the zoom scales the projection by 2^((zoom - 512) / 256); the camera distance never changes.
        check(CameraZoomController.projectionScale(1000) == 1000, "512 is the client's own projection");

        // Plugin on: RuneLite CameraConfig items.
        RspsHudConfig.put("zoom.enabled", "true");
        RspsHudConfig.put("zoom.inner", "true");
        RspsHudConfig.put("zoom.outerLimit", "400");
        RspsHudConfig.put("zoom.zoomIncrement", "50");
        check(CameraZoomController.maxZoom() == 1400, "Expand inner zoom limit: 1400");
        check(CameraZoomController.minZoom() == -272, "Expand outer zoom limit 400: 128 - 400");
        check(CameraZoomController.adjustedZoom(512, -1) == 562, "Zoom speed 50");
        RspsHudConfig.put("zoom.outerLimit", "-400");
        check(CameraZoomController.minZoom() == 528, "Negative outer limit narrows the range");
        CameraZoomController.restoreZoom(300);
        check(CameraZoomController.getZoom() == 528, "New limits move the zoom inside them");
        // Login push in parts: the plugin switch arrives before the limits - an expanded zoom must survive that.
        RspsHudConfig.put("zoom.outerLimit", "0");
        CameraZoomController.restoreZoom(1200);
        RspsHudConfig.put("zoom.inner", "false");
        check(CameraZoomController.getZoom() == 896, "Defaults in effect while the limits are not known yet");
        RspsHudConfig.put("zoom.inner", "true");
        check(CameraZoomController.getZoom() == 1200, "The saved expanded zoom is back once the account's limits arrive");
        RspsHudConfig.put("zoom.outerLimit", "-400");
        RspsHudConfig.put("zoom.outerLimit", "0");
        RspsHudConfig.put("zoom.controlFunction", "CONTROL_TO_ZOOM");
        CameraZoomController.restoreZoom(512);
        CameraZoomController.tickControl(false, true);
        check(CameraZoomController.applyWheelRotation(-1, true, false, false, false, false, false) && CameraZoomController.getZoom() == 512,
            "Hold to zoom: the wheel without Ctrl does not zoom");
        CameraZoomController.tickControl(true, true);
        CameraZoomController.applyWheelRotation(-1, true, false, false, false, false, false);
        check(CameraZoomController.getZoom() == 562, "Hold to zoom: Ctrl + wheel zooms");
        RspsHudConfig.put("zoom.controlFunction", "CONTROL_TO_RESET");
        RspsHudConfig.put("zoom.ctrlZoomValue", "700");
        CameraZoomController.tickControl(true, true);
        CameraZoomController.tickControl(false, true);
        check(CameraZoomController.getZoom() == 700, "Reset zoom: releasing Ctrl zooms to the reset position");
        CameraZoomController.restoreZoom(600);
        CameraZoomController.tickControl(true, true);
        CameraZoomController.tickControl(false, false);
        CameraZoomController.tickControl(false, true);
        check(CameraZoomController.getZoom() == 600, "Losing focus with Ctrl held is no reset (RuneLite onFocusChanged)");
        check(CameraZoomController.fromLegacyDistance(320) == 512 && CameraZoomController.fromLegacyDistance(660) < 140
            && CameraZoomController.fromLegacyDistance(150) > 890, "Old saved distance maps onto the OSRS scale");
        RspsHudConfig.resetToDefaults();

        // RuneLite Minimap "Zoom" (default on): 4 px per tile, wheel only over the minimap.
        RspsMinimapZoom.zoom = RspsMinimapZoom.DEFAULT;
        RspsMinimapZoom.hovered = false;
        check(!RspsMinimapZoom.wheel(-1), "Minimap zoom ignores the wheel away from the minimap");
        RspsMinimapZoom.hovered = true;
        check(RspsMinimapZoom.wheel(-1) && RspsMinimapZoom.zoom == 4.5, "Wheel up over the minimap zooms it in");
        check(RspsMinimapZoom.scale(16384) == 18432 && RspsMinimapZoom.unscale(18432) == 16384, "Dots and clicks use the same factor");
        for (int i = 0; i < 50; i++) RspsMinimapZoom.wheel(1);
        check(RspsMinimapZoom.zoom == RspsMinimapZoom.MIN, "Minimap zoom clamps");
        RspsMinimapZoom.reset();
        check(RspsMinimapZoom.zoom == 4.0 && !RspsMinimapZoom.isZoomed(), "Right-click Reset zoom");
        RspsHudConfig.put("minimap.zoom", "false");
        check(!RspsMinimapZoom.wheel(-1) && RspsMinimapZoom.factor() == 1.0, "Zoom off: the wheel goes to the camera, scale 1");
        RspsHudConfig.resetToDefaults();
        RspsMinimapZoom.hovered = false;

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

        CameraZoomController.restoreZoom(512);
        check(CameraZoomController.applyWheelRotation(1, true, false, false, false, false, false), "Eligible wheel input is consumed");
        check(CameraZoomController.getZoom() == 487, "Consumed wheel input updates the real camera zoom field");
        check(!CameraZoomController.applyWheelRotation(1, true, true, false, false, false, false) && CameraZoomController.getZoom() == 487,
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
