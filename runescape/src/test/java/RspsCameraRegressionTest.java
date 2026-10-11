import com.jagex.CameraMouse;
import com.jagex.core.constants.MiniMenuAction;

/**
 * RuneLite Camera plugin + OSRS mouse camera (owner 2026-10-11: "the whole RuneLite plugin"; rotation speed, smoothness,
 * up/down limit, mouse buttons). No display, server or toolkit needed.
 */
public final class RspsCameraRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        RspsHudConfig.resetToDefaults();

        // Plugin off: OSRS's own camera - standard pitch limit, speed 1, nothing remapped.
        check(RspsCamera.maxPitch() == 3072, "Off: OSRS pitch limit 383 (x8)");
        check(RspsCamera.speed() == 1.0F, "Off: camera speed 1");
        RspsCamera.tick();
        check(CameraMouse.press(CameraMouse.RIGHT) == CameraMouse.RIGHT, "Off: right stays right");
        check(CameraMouse.release(CameraMouse.RIGHT) == CameraMouse.RIGHT, "Off: right release stays right");

        // OSRS mouse camera (Client.java): speed = 2 x distance to a reference point that moves half way each cycle.
        Camera.angleAxisX = 0;
        Camera.angleAxisY = 0;
        RspsCamera.mouseCamera(false, 100, 100);
        RspsCamera.forceAngle(0, 0, RspsCamera.DRAG_SCRIPT); // the drag script only switches the mouse camera on
        RspsCamera.mouseCamera(true, 90, 110);
        check(Camera.angleAxisX == 20 && Camera.angleAxisY == 20, "Drag: yaw speed (100 - 90) x 2, pitch speed (110 - 100) x 2");
        check(Camera.angleChangingX && Camera.angleChangingY, "Drag: this cycle's halving is skipped");
        RspsCamera.mouseCamera(true, 90, 110);
        check(Camera.angleAxisX == 10 && Camera.angleAxisY == 10, "Drag: the reference point moved half way - smooth catch-up");
        RspsCamera.mouseCamera(false, 90, 110);
        check(Camera.angleAxisX == 10, "Release: the speed is left to glide out (halved per cycle by MainLogicManager)");

        // Plugin on: RuneLite CameraConfig.
        RspsHudConfig.put("zoom.enabled", "true");
        RspsHudConfig.put("zoom.relaxCameraPitch", "true");
        check(RspsCamera.maxPitch() == 4096, "Expand pitch limit: 512 (x8)");
        RspsHudConfig.put("zoom.cameraSpeed", "2.0");
        check(RspsCamera.speed() == 2.0F, "Camera speed 2");
        RspsHudConfig.put("zoom.invertYaw", "true");
        RspsCamera.mouseCamera(false, 100, 100);
        RspsCamera.forceAngle(0, 0, RspsCamera.DRAG_SCRIPT);
        RspsCamera.mouseCamera(true, 90, 110);
        check(Camera.angleAxisX == -40 && Camera.angleAxisY == 40, "Speed 2 doubles the drag, Invert yaw turns it round");
        RspsHudConfig.put("zoom.invertYaw", "false");
        RspsHudConfig.put("zoom.cameraSpeed", "1.0");
        RspsCamera.mouseCamera(false, 90, 110);

        // Right-click moves camera: an empty menu (walk only) drags the camera, an object menu opens.
        RspsHudConfig.put("zoom.rightClickMovesCamera", "true");
        check(!RspsCamera.opensMenu(MiniMenuAction.WALK, true, false), "Walk here never opens the menu");
        check(!RspsCamera.opensMenu(MiniMenuAction.CANCEL, true, false), "Cancel never opens the menu");
        check(RspsCamera.opensMenu(MiniMenuAction.OPNPC1, true, false), "Right-click objects: an npc option opens the menu");
        check(!RspsCamera.opensMenu(MiniMenuAction.OPNPC1, false, false), "Right-click objects off: it does not");
        check(!RspsCamera.opensMenu(MiniMenuAction.OPLOC6, true, false), "Examine only with Right-click examine");
        check(RspsCamera.opensMenu(MiniMenuAction.OPLOC6, true, true), "Right-click examine: examine opens the menu");
        check(RspsCamera.opensMenu(MiniMenuAction.IF_BUTTONX1, false, false), "Interface options always open the menu");
        CameraMouse.update(true, false, false, false);
        check(CameraMouse.press(CameraMouse.RIGHT) == CameraMouse.MIDDLE, "Right press on an empty menu drags the camera");
        check(CameraMouse.release(CameraMouse.RIGHT) == CameraMouse.MIDDLE, "...and is released as the middle button");
        CameraMouse.update(true, false, true, false);
        check(CameraMouse.press(CameraMouse.RIGHT) == CameraMouse.RIGHT, "Right press with menu entries opens the menu");
        check(CameraMouse.release(CameraMouse.RIGHT) == CameraMouse.RIGHT, "...and is released as right");
        CameraMouse.update(true, false, true, true);
        check(CameraMouse.press(CameraMouse.RIGHT) == CameraMouse.MIDDLE, "One mouse button: right always drags the camera");
        CameraMouse.release(CameraMouse.RIGHT);
        check(RspsCamera.middleForCamera(false, true) == false, "Right-click menu blocks camera (default): no drag mask");
        RspsHudConfig.put("zoom.rightClickMenuBlocksCamera", "false");
        check(RspsCamera.middleForCamera(false, true), "Menu does not block: the right button drags too");

        // Middle-button opens menu.
        CameraMouse.update(false, true, false, false);
        check(CameraMouse.press(CameraMouse.MIDDLE) == CameraMouse.RIGHT, "Middle press opens the menu");
        check(CameraMouse.release(CameraMouse.MIDDLE) == CameraMouse.RIGHT, "...and is released as right");

        // Zoom (OSRS viewport_setfov): the projection scales, the camera distance never changes.
        RspsHudConfig.put("zoom.enabled", "false");
        CameraZoomController.restoreZoom(768);
        check(CameraZoomController.projectionScale(1000) == 2000, "Zoom 768: twice the projection");
        CameraZoomController.restoreZoom(256);
        check(CameraZoomController.projectionScale(1000) == 500, "Zoom 256: half the projection");
        CameraZoomController.restoreZoom(512);

        System.out.println("RspsCameraRegressionTest: " + assertions + " assertions passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        assertions++;
    }
}
