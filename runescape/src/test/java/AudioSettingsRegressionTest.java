import com.jagex.sound.Node_Sub6_Sub3;
import com.jagex.sound.Sound;
import com.jagex.sound.SoundType;

public final class AudioSettingsRegressionTest {
    private static int assertions;

    public static void main(String[] args) {
        SoundManager.activeStreams = new Node_Sub6_Sub3();
        SoundManager.sounds = new Sound[50];
        SoundManager.count = 3;
        SoundManager.sounds[0] = sound(SoundType.SYNTH);
        SoundManager.sounds[1] = sound(SoundType.VORBIS_VOICE_OVER);
        SoundManager.sounds[2] = sound(SoundType.VORBIS);

        SoundManager.stopOneShotSounds(SoundType.SYNTH);
        check(SoundManager.count == 2, "Stopping one type compacts the queue");
        check(SoundManager.sounds[0].type == SoundType.VORBIS_VOICE_OVER, "Speech order is retained");
        check(SoundManager.sounds[1].type == SoundType.VORBIS, "Vorbis effect order is retained");
        check(SoundManager.sounds[2] == null, "Removed queue tail is cleared");

        SoundManager.stopOneShotSounds(SoundType.VORBIS_VOICE_OVER);
        check(SoundManager.count == 1 && SoundManager.sounds[0].type == SoundType.VORBIS,
            "Speech volume changes do not stop ordinary effects");

        Node_Sub6_Sub3 positionedMixer = SoundManager.activeStreams;
        SoundManager.restartPositionedStreams();
        check(SoundManager.activeStreams == positionedMixer,
            "Background volume rebuild preserves unrelated one-shot mixer streams");

        Node_Sub6_Sub3 previous = SoundManager.activeStreams;
        SoundManager.resetOutputStreams();
        check(SoundManager.count == 0, "Output-mode switch stops queued one-shots");
        check(SoundManager.activeStreams != previous, "Output-mode switch replaces the old stream mixer");
        check(SoundManager.sounds.length == 50, "Output-mode switch preserves queue capacity");

        System.out.println("Audio settings regression checks passed: " + assertions);
    }

    private static Sound sound(int type) {
        return new Sound((byte) type, 1, 1, 0, 255, 256, 0, null);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
