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
        System.out.println("Audio volume regression checks passed: " + assertions);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
