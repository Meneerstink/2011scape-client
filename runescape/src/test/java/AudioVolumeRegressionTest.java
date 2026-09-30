public final class AudioVolumeRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        check(SoundManager.AMBIENT_GAIN_PERCENT == 76.5D,
            "Ambient gain keeps the requested 10% reduction after the prior 85% mix");
        check(SoundManager.ambientVolumeFor(127) == 97,
            "Maximum background volume is reduced by the shared ambient mixer");
        check(SoundManager.ambientVolumeFor(100) == 77,
            "Normal background volume uses the 76.5% gain, rounded only at the output");
        check(SoundManager.ambientVolumeFor(0) == 0,
            "Muted background audio remains muted");
        // Audio Options sliders (cs2 1215 / 1159): music and login music run 0..255, effects and area sounds 0..127.
        ClientOptions fresh = new ClientOptions(null, com.jagex.core.constants.ModeGame.RUNESCAPE, com.jagex.graphics.ToolkitType.JAVA,
            new com.jagex.EnvironmentContext(true, 1024, 4, false));
        check(fresh.musicVolume.getValue() == 255 && fresh.soundVolume.getValue() == 127 && fresh.backgroundSoundVolume.getValue() == 127,
            "Every slider starts at its own maximum");
        fresh.update(255, fresh.musicVolume);
        fresh.update(64, fresh.backgroundSoundVolume);
        ClientOptions reloaded = new ClientOptions(new com.jagex.core.io.Packet(fresh.encodeLocal()), com.jagex.core.constants.ModeGame.RUNESCAPE,
            com.jagex.graphics.ToolkitType.JAVA, new com.jagex.EnvironmentContext(true, 1024, 4, false));
        check(reloaded.musicVolume.getValue() == 255, "Music at the slider maximum (255) survives a restart (was reset to 127 = half)");
        check(reloaded.backgroundSoundVolume.getValue() == 64, "Area sound volume survives a restart");
        VolumeOption effects = new VolumeOption(200, fresh, VolumeOption.EFFECT_MAX);
        check(effects.getValue() == 127, "Effects / area values above 127 are still invalid");
        System.out.println("Audio volume regression checks passed: " + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
