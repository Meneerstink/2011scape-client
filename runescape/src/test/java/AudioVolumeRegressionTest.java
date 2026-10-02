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
        // Overnight 2026-10-02: a map area change (teleport) keeps the sounds queued in that tick instead of wiping them.
        SoundManager.reset();
        SoundManager.sounds[SoundManager.count++] = new com.jagex.sound.Sound((byte) com.jagex.sound.SoundType.SYNTH, 188, 1, 0, 116, 256, 0, null);
        SoundManager.sounds[SoundManager.count++] = new com.jagex.sound.Sound((byte) com.jagex.sound.SoundType.SYNTH, 2, 1, 0, 116, 256, (1 << 24) | (60 << 16) | (50 << 8) | 10, null);
        SoundManager.sounds[SoundManager.count++] = new com.jagex.sound.Sound((byte) com.jagex.sound.SoundType.SYNTH, 3, 1, 0, 116, 256, (5 << 16) | (5 << 8) | 3, null);
        SoundManager.rebase(8, -16, 104, 104);
        check(SoundManager.count == 2, "Map area change keeps the own sound and the positional one still inside the area, drops the one outside");
        check(SoundManager.sounds[0].id == 188 && SoundManager.sounds[0].coord == 0, "A non-positional sound survives a map area change unchanged");
        check(SoundManager.sounds[1].id == 2 && SoundManager.sounds[1].coord == ((1 << 24) | (52 << 16) | (66 << 8) | 10),
            "A positional sound moves with the area delta like the player's path (local -= delta), level and radius kept");
        SoundManager.reset();
        System.out.println("Audio volume regression checks passed: " + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
