import com.jagex.TestHook;
import com.jagex.core.constants.MainLogicStep;
import com.jagex.graphics.Toolkit;

/**
 * Agent team 2026-10-14 (owner: "trage laptop, low settings"): applies a {@code detail min|mid} request of the test hook
 * on the game thread. Min = the Graphics Options "Min" button (ScriptRunner AUTOSETUP_SETMIN) with the lowest CPU usage;
 * mid = the 78 default profile every player starts on (RspsGraphicsProfile), for items about what players see.
 */
final class RspsTestDetail {

    private RspsTestDetail() {
    }

    /** Game-thread safe point (client.gameTick, beside RspsGraphicsProfile.processPendingRestore). */
    static void process() {
        final String level = TestHook.pendingDetail();
        if (level == null) {
            return;
        }
        final int step = MainLogicManager.step;
        if (ClientOptions.instance == null || Toolkit.active == null || !(MainLogicStep.isAtGameScreen(step)
            || step == MainLogicStep.STEP_LOGIN_SCREEN || step == MainLogicStep.STEP_LOBBY_SCREEN)) {
            return;
        }
        try {
            // A12 keuring 2026-10-14: the shared client-test prefs had sound volume 0 (clicked off once), so [avtrace] said
            // "dropped - volume 0" and sound items could not be proven. The speakers are silent anyway (TestHook.SILENT):
            // every test client starts with the default volumes.
            final ClientOptions o = ClientOptions.instance;
            for (VolumeOption v : new VolumeOption[] {o.soundVolume, o.backgroundSoundVolume, o.speechVolume, o.musicVolume, o.loginVolume}) {
                o.update(v.getDefaultValue(), v);
            }
            if ("gl".equals(level) || "java".equals(level) || "dx".equals(level) || "sse".equals(level)) {
                // N462 skeletal proof: switch the renderer like the dev console tk0/tk1/tk2/tk3 (OpenGL = the owner's renderer).
                final int tk = "gl".equals(level) ? com.jagex.graphics.ToolkitType.GL : "dx".equals(level) ? com.jagex.graphics.ToolkitType.D3D
                    : "sse".equals(level) ? com.jagex.graphics.ToolkitType.SSE : com.jagex.graphics.ToolkitType.JAVA;
                Static32.setToolkit(tk, false);
                if (ClientOptions.instance.toolkit.getValue() != tk) {
                    TestHook.detailDone("err detail " + level + ": toolkit stayed " + ClientOptions.instance.toolkit.getValue());
                    return;
                }
                // Session only: the saved renderer (toolkitDefault) stays as it was - saving Java there made every later start
                // show "Available graphics options have changed" (2026-11-06).
            } else if ("min".equals(level)) {
                Static468.method7643();
                ClientOptions.instance.update(0, ClientOptions.instance.cpuUsage);
                MainLogicManager.mapBuild();
                ClientOptions.save();
                Static503.sentPreferences = false;
            } else {
                RspsGraphicsProfile.requestRestore();
                RspsGraphicsProfile.processPendingRestore();
            }
            TestHook.detailDone("ok detail " + level);
        } catch (Throwable t) {
            TestHook.detailDone("err detail " + t);
        }
    }
}
