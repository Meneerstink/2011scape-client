import com.jagex.game.camera.Shake;
import com.jagex.CameraMouse;
import com.jagex.Client;
import com.jagex.core.constants.MiniMenuAction;

/**
 * RuneLite's Camera plugin at runtime (CameraPlugin.copyConfigs, onClientTick and its mouse listener), owner 2026-10-11:
 * "the whole RuneLite plugin". The zoom items stay in {@link CameraZoomController}; the settings are the plugin's
 * CameraConfig items (RspsHudConfig group "zoom"). Everything here is off while the plugin is off.
 * <p>
 * Units: this client's camera angles are OSRS's x8 - yaw 0..16384, pitch 1024..3072 (OSRS 128..383). The clientscripts
 * (camera keys, middle-mouse drag) work in OSRS units through CAM_GETANGLE / CAM_FORCEANGLE ({@code >> 3}).
 */
public final class RspsCamera {

    /** The 2011 client's upper pitch limit (Static723), OSRS's 383. */
    static final int PITCH_MAX = 3072;
    /** RuneLite's pitch relaxer (CameraMixin NEW_PITCH_MAX 512 OSRS units, x8). */
    static final int RELAXED_PITCH_MAX = 4096;
    /** The middle-mouse drag clientscript: GET_MOUSEBUTTONS, then CAM_FORCEANGLE from the mouse movement (an interface hook). */
    static final int DRAG_SCRIPT = 4731;

    private RspsCamera() {
    }

    private static boolean on() {
        return RspsHudConfig.enabled("zoom");
    }

    private static boolean setting(String key) {
        return on() && RspsHudConfig.bool("zoom." + key);
    }

    /** "Camera speed" (setCameraSpeed): how fast input moves the camera - the camera keys and the mouse drag. */
    static float speed() {
        return on() ? (float) RspsHudConfig.decimal("zoom.cameraSpeed", 1.0) : 1.0F;
    }

    /** "Expand pitch limit" (setCameraPitchRelaxerEnabled): the camera may look further down. */
    static int maxPitch() {
        return setting("relaxCameraPitch") ? RELAXED_PITCH_MAX : PITCH_MAX;
    }

    /** "Disable camera shake" (setCameraShakeDisabled): whether a shake (CAM_SHAKE) in [direction] moves the camera. */
    static boolean shake(int direction) {
        return Shake.enabled[direction] && !setting("disableCameraShake");
    }

    /**
     * setCameraMouseButtonMask: with "Right-click moves camera" on and "Right-click menu blocks camera" off, a right button
     * that opened a menu still drags the camera. What GET_MOUSEBUTTONS reports as the middle button for the drag script.
     */
    static boolean middleForCamera(boolean middle, boolean right) {
        return middle || right && setting("rightClickMovesCamera") && !setting("rightClickMenuBlocksCamera");
    }

    /** OSRS's mouse camera reference point (Client.mouseCamClickedX / Y), in game pixels. */
    private static int clickedX;
    private static int clickedY;
    /** The drag script reached CAM_FORCEANGLE since the camera button went down: its conditions hold (game view, option). */
    private static boolean dragging;

    /**
     * CAM_FORCEANGLE ([pitch], [yaw] in OSRS units). The 2011 middle-mouse drag script set the dragged angle at once (yaw x2,
     * pitch x1 per pixel, no smoothing); OSRS moves the camera in the client itself ({@link #mouseCamera}), so from that
     * script it only switches the mouse camera on. Every other script sets its angle as before.
     */
    static void forceAngle(int pitch, int yaw, int scriptId) {
        if (scriptId == DRAG_SCRIPT) {
            dragging = true;
            return;
        }
        Camera.forceAngle(pitch, yaw, 0);
    }

    /**
     * OSRS's mouse camera (Client.java, once per 20 ms cycle, before the angle speeds are halved): while the camera button is
     * held the speed is twice the distance from a reference point that moves half way to the mouse each cycle - a smooth
     * catch-up - and after the release the speeds halve every cycle (MainLogicManager), so the camera glides out. This
     * client's angleAxisX / angleAxisY are OSRS's camAngleDY / camAngleDX: both advance the angle by speed / 2 OSRS units per
     * cycle (Static604). RuneLite: "Camera speed" scales it, "Invert yaw" / "Invert pitch" turn it round.
     */
    static void mouseCamera(boolean cameraButton, int mouseX, int mouseY) {
        if (cameraButton && dragging) {
            int dy = mouseY - clickedY;
            int dx = clickedX - mouseX;
            float speed = speed();
            Camera.angleAxisY = dy * 2 * speed * (setting("invertPitch") ? -1 : 1);
            Camera.angleAxisX = dx * 2 * speed * (setting("invertYaw") ? -1 : 1);
            // Set, not eased: this cycle's halving is skipped (OSRS's else branch).
            Camera.angleChangingY = true;
            Camera.angleChangingX = true;
            Camera.angleUpdated = true;
            clickedY = dy != -1 && dy != 1 ? (clickedY + mouseY) / 2 : mouseY;
            clickedX = dx != -1 && dx != 1 ? (clickedX + mouseX) / 2 : mouseX;
            return;
        }
        if (!cameraButton) {
            dragging = false;
        }
        clickedX = mouseX;
        clickedY = mouseY;
    }

    /** Every client tick (RuneLite onClientTick): the mouse listener's settings and whether the menu has entries. */
    static void tick() {
        boolean on = on();
        CameraMouse.update(on && RspsHudConfig.bool("zoom.rightClickMovesCamera"), on && RspsHudConfig.bool("zoom.middleClickMenu"),
            on && menuHasEntries(RspsHudConfig.bool("zoom.rightClickObjects"), RspsHudConfig.bool("zoom.rightClickExamine")),
            Client.mouseButtons == 1);
    }

    /** CameraPlugin.hasMenuEntries over this client's menu actions (a deprioritised entry is its action + 2000). */
    static boolean menuHasEntries(boolean objects, boolean examine) {
        for (MiniMenuEntryInner entry = (MiniMenuEntryInner) MiniMenu.innerEntryQueue.first(); entry != null; entry = (MiniMenuEntryInner) MiniMenu.innerEntryQueue.next()) {
            if (opensMenu(entry.action >= 2000 ? entry.action - 2000 : entry.action, objects, examine)) {
                return true;
            }
        }
        return false;
    }

    static boolean opensMenu(int action, boolean objects, boolean examine) {
        switch (action) {
            case MiniMenuAction.CANCEL:
            case MiniMenuAction.WALK:
            case MiniMenuAction.FACE_SQUARE: // RuneLite SET_HEADING
                return false;
            case MiniMenuAction.OPLOC6: // Examine: EXAMINE_OBJECT / EXAMINE_NPC / EXAMINE_ITEM_GROUND
            case MiniMenuAction.OPNPC6:
            case MiniMenuAction.OPOBJ6:
                return examine;
            case MiniMenuAction.OPLOC1:
            case MiniMenuAction.OPLOC2:
            case MiniMenuAction.OPLOC3:
            case MiniMenuAction.OPLOC4:
            case MiniMenuAction.OPLOC5:
            case MiniMenuAction.OPNPC1:
            case MiniMenuAction.OPNPC2:
            case MiniMenuAction.OPNPC3:
            case MiniMenuAction.OPNPC4:
            case MiniMenuAction.OPNPC5:
            case MiniMenuAction.OPOBJ1:
            case MiniMenuAction.OPOBJ2:
            case MiniMenuAction.OPOBJ3:
            case MiniMenuAction.OPOBJ4:
            case MiniMenuAction.OPOBJ5:
            case MiniMenuAction.OPPLAYER1:
            case MiniMenuAction.OPPLAYER2:
            case MiniMenuAction.OPPLAYER3:
            case MiniMenuAction.OPPLAYER4:
            case MiniMenuAction.OPPLAYER5:
            case MiniMenuAction.OPPLAYER6:
            case MiniMenuAction.OPPLAYER7:
            case MiniMenuAction.OPPLAYER8:
            case MiniMenuAction.OPPLAYER9:
            case MiniMenuAction.OPPLAYER10:
                return objects;
            default:
                return true;
        }
    }
}
