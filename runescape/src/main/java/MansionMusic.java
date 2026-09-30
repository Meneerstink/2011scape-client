import java.io.BufferedInputStream;
import java.io.InputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/**
 * The mansion's own soundtrack (owner 2026-09-26): inside the Grand Exchange Royal Hall ("the mansion", both floors) the
 * client plays /music/mansion.mp3 in a loop instead of the area's MIDI track. It fades in on entering and fades out slowly
 * when the player walks out, and only then does the area's MIDI track come back. New players who spawn inside hear it at
 * once. The volume follows the Music volume setting.
 *
 * The MIDI side ({@link #tick()}) runs on the client thread from MainLogicManager; decoding and playback run on one daemon
 * thread that only reads the two volatile fields.
 */
public final class MansionMusic {

    /** Ground floor: GeHomeHall X 3158, Z 3486, 14 x 12. */
    private static final int GROUND_MIN_X = 3158, GROUND_MAX_X = 3171, GROUND_MIN_Z = 3486, GROUND_MAX_Z = 3497;

    /** Upper floor with its balconies. */
    private static final int UPPER_MIN_X = 3154, UPPER_MAX_X = 3175, UPPER_MIN_Z = 3482, UPPER_MAX_Z = 3501;

    /**
     * The Skilling Basement under the mansion (AfkBasementMapTool room, level 0): the same soundtrack instead of the Grand
     * Exchange track that kept playing down there (owner 2026-09-27).
     */
    private static final int BASEMENT_MIN_X = 2964, BASEMENT_MAX_X = 2987, BASEMENT_MIN_Z = 6102, BASEMENT_MAX_Z = 6119;

    /** Fade speeds in gain per second: about 2.5 s in, 5 s out. */
    private static final double FADE_IN = 0.4, FADE_OUT = 0.2;

    private static volatile boolean inside;
    private static volatile double fade;
    private static volatile boolean running;

    /** No audio device or no track: stay silent for this session instead of retrying every cycle. */
    private static volatile boolean failed;

    /** The MIDI track that was playing when the player walked in; restored once the mansion music has faded out. */
    private static int savedMidi = -1;

    private MansionMusic() {
    }

    public static boolean contains(int x, int z, int level) {
        if (level == 0) {
            return x >= GROUND_MIN_X && x <= GROUND_MAX_X && z >= GROUND_MIN_Z && z <= GROUND_MAX_Z
                || x >= BASEMENT_MIN_X && x <= BASEMENT_MAX_X && z >= BASEMENT_MIN_Z && z <= BASEMENT_MAX_Z;
        }
        if (level == 1) {
            return x >= UPPER_MIN_X && x <= UPPER_MAX_X && z >= UPPER_MIN_Z && z <= UPPER_MAX_Z;
        }
        return false;
    }

    /** Client thread, once per logic cycle. */
    public static void tick() {
        try {
            PlayerEntity self = PlayerEntity.self;
            boolean now = false;
            if (self != null && MainLogicManager.step == 11) {
                int x = (self.x >> 9) + WorldMap.areaBaseX;
                int z = (self.z >> 9) + WorldMap.areaBaseZ;
                now = contains(x, z, self.level);
            }
            inside = now;
            if (now) {
                if (!running) {
                    start();
                }
                if (SongManager.playing != -1) {
                    savedMidi = SongManager.playing;
                    SoundManager.playMidiSong(-1, 255, 0);
                }
            } else if (SongManager.playing != -1) {
                // The server sent a newer track after the player walked out (e.g. Death's Office): keep it.
                savedMidi = -1;
            } else if (savedMidi != -1 && fade <= 0.02) {
                int song = savedMidi;
                savedMidi = -1;
                SoundManager.playMidiSong(song, 255, 0);
            }
        } catch (Throwable ignored) {
            // Never let the soundtrack disturb the game loop.
        }
    }

    private static synchronized void start() {
        if (running || failed) {
            return;
        }
        running = true;
        Thread thread = new Thread(MansionMusic::run, "mansion-music");
        thread.setDaemon(true);
        thread.start();
    }

    private static double volumeSetting() {
        try {
            // The music slider runs 0..255 (VolumeOption.MUSIC_MAX): /127 reached full volume at the middle of the slider.
            return Math.max(0, Math.min(VolumeOption.MUSIC_MAX, ClientOptions.instance.musicVolume.getValue())) / (double) VolumeOption.MUSIC_MAX;
        } catch (Throwable t) {
            return 1.0;
        }
    }

    private static void run() {
        SourceDataLine line = null;
        try {
            while (true) {
                InputStream resource = MansionMusic.class.getResourceAsStream("/music/mansion.mp3");
                if (resource == null) {
                    failed = true;
                    return;
                }
                Bitstream bitstream = new Bitstream(new BufferedInputStream(resource, 64 * 1024));
                Decoder decoder = new Decoder();
                long last = System.nanoTime();
                boolean faded = false;
                try {
                    Header header;
                    while ((header = bitstream.readFrame()) != null) {
                        SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                        bitstream.closeFrame();
                        if (line == null) {
                            AudioFormat format = new AudioFormat(decoder.getOutputFrequency(), 16, decoder.getOutputChannels(), true, false);
                            line = AudioSystem.getSourceDataLine(format);
                            line.open(format, decoder.getOutputFrequency() * decoder.getOutputChannels());
                            line.start();
                        }
                        long nowNs = System.nanoTime();
                        double seconds = (nowNs - last) / 1e9;
                        last = nowNs;
                        double f = fade;
                        f = inside ? Math.min(1.0, f + FADE_IN * seconds) : Math.max(0.0, f - FADE_OUT * seconds);
                        fade = f;
                        if (!inside && f <= 0.0) {
                            faded = true;
                            break;
                        }
                        double gain = f * f * volumeSetting();
                        short[] samples = out.getBuffer();
                        int length = out.getBufferLength();
                        byte[] bytes = new byte[length * 2];
                        for (int i = 0; i < length; i++) {
                            int s = (int) (samples[i] * gain);
                            bytes[i * 2] = (byte) s;
                            bytes[i * 2 + 1] = (byte) (s >> 8);
                        }
                        line.write(bytes, 0, bytes.length);
                    }
                } finally {
                    try {
                        bitstream.close();
                    } catch (Throwable ignored) {
                    }
                }
                if (faded) {
                    line.drain();
                    line.close();
                    line = null;
                    // Wait here until the player walks back in; the track then starts from the beginning.
                    while (!inside) {
                        Thread.sleep(200);
                    }
                }
            }
        } catch (Throwable ignored) {
            // No audio device or a broken file: stay silent.
            failed = true;
        } finally {
            if (line != null) {
                line.close();
            }
            running = false;
        }
    }
}
