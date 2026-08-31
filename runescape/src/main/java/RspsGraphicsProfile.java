import com.jagex.core.constants.MainLogicStep;
import com.jagex.graphics.Toolkit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local preset version; deliberately independent of the server's preference packet version. */
public final class RspsGraphicsProfile {
    public static final String NAME = "RSPS Performance";
    public static final int VERSION = 2;
    private static final AtomicBoolean restoreRequested = new AtomicBoolean();

    private RspsGraphicsProfile() { }

    public static void apply(ClientOptions options) {
        options.antialiasingMode.setSafeValue(0);
        options.antialiasingQuality.setSafeValue(0);
        options.bloom.setSafeValue(0);
        options.brightness.setSafeValue(3);
        options.buildArea.setSafeValue(0);
        options.removeRoofs.setSafeValue(1); // Always hidden, rather than selectively hidden (2).
        options.removeRoofsOverride.setSafeValue(1);
        options.orthographic.setSafeValue(0);
        options.spotShadows.setSafeValue(0);
        options.hardShadows.setSafeValue(0);
        options.lightDetail.setSafeValue(0);
        options.waterDetail.setSafeValue(0);
        options.fog.setSafeValue(0);
        // The software renderer disables this UI setter, but the stored hardware setting
        // still needs to be off before first-start hardware selection happens.
        options.skydetail.setValue(0);
        options.idleAnimations.setSafeValue(0); // Few, verified against the in-game dropdown.
        // Preserve home scenery and combat effects: this is not the engine's MIN preset.
        options.groundDecor.setSafeValue(1);
        options.groundBlending.setSafeValue(1);
        options.textures.setSafeValue(1);
        options.flickeringEffects.setSafeValue(1);
        options.particles.setSafeValue(options.environment.getHeapSize() < 96 ? 0 : 1);
        options.cpuUsage.setSafeValue(options.environment.cpuCount() > 1 ? 4 : 2);
        options.graphicsQuality.setSafeValue(0); // CUSTOM, not a falsely labelled built-in preset.
        options.validate();
    }

    /** Called by the AWT menu; actual changes belong on the game thread. */
    public static void requestRestore() {
        restoreRequested.set(true);
    }

    static void processPendingRestore() {
        int step = MainLogicManager.step;
        if (ClientOptions.instance == null || Toolkit.active == null
                || !(MainLogicStep.isAtGameScreen(step)
                || step == MainLogicStep.STEP_LOGIN_SCREEN
                || step == MainLogicStep.STEP_LOBBY_SCREEN)) {
            return;
        }
        if (!restoreRequested.getAndSet(false)) {
            return;
        }
        ClientOptions options = ClientOptions.instance;
        apply(options);
        // Recreate AA/bloom resources and rebuild the map using the existing renderer path.
        Static32.setToolkit(options.toolkit.getValue(), false);
        Static77.method1561();
        ClientOptions.save();
        Static503.sentPreferences = false;
    }
}
