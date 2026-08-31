import com.jagex.EnvironmentContext;
import com.jagex.core.constants.ModeGame;
import com.jagex.core.io.Packet;
import com.jagex.graphics.ToolkitType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class R13RegressionTest {
    private static final EnvironmentContext ENV = new EnvironmentContext(true, 1024, 4, false);
    private static int assertions;

    public static void main(String[] args) throws Exception {
        ClientOptions fresh = options(null, ENV);
        check(fresh.needsDefaultRenderer, "Fresh preferences select a supported default at startup");
        profile(fresh);
        byte[] defaults = encode(fresh);
        check(defaults.length == 35 && defaults[0] == 25, "Server packet format stays v25, 35 bytes");

        for (int length = 0; length < defaults.length; length++) {
            ClientOptions repaired = options(Arrays.copyOf(defaults, length), ENV);
            check(repaired.needsDefaultRenderer, "Truncation is repaired at byte " + length);
            check(Arrays.equals(defaults, encode(repaired)), "No partial settings survive at byte " + length);
        }
        check(Arrays.equals(defaults, encode(options(new byte[]{(byte)255}, ENV))), "Unknown version resets");
        check(Arrays.equals(defaults, encode(options(new byte[]{22, 1, 2}, ENV))), "Truncated legacy resets");

        ClientOptions custom = options(defaults, ENV);
        custom.update(ToolkitType.GL, custom.toolkitDefault);
        custom.update(ToolkitType.GL, custom.toolkit);
        custom.update(2, custom.antialiasingMode);
        custom.update(2, custom.antialiasingQuality);
        custom.update(2, custom.hardShadows);
        custom.update(1, custom.brightness);
        custom.update(2, custom.removeRoofs);
        custom.update(2, custom.removeRoofsOverride);
        custom.update(2, custom.particles);
        custom.update(31, custom.soundVolume);
        custom.update(23, custom.musicVolume);
        custom.update(2, custom.screenSizeDefault);
        custom.update(2, custom.screenSize);
        custom.update(0, custom.customCursors);
        byte[] saved = encode(custom);
        ClientOptions loaded = options(saved, ENV);
        check(!loaded.needsDefaultRenderer, "Saved preferences skip default selection");
        check(Arrays.equals(saved, encode(loaded)), "Saved deliberate overrides round-trip exactly: "
                + Arrays.toString(saved) + " -> " + Arrays.toString(encode(loaded)));
        // Older supported packets lack only sky (v24) or sky + orthographic (v23).
        byte[] v24 = omit(saved, 16);
        v24[0] = 24;
        check(options(v24, ENV).brightness.getValue() == 1, "v24 retains saved brightness");
        check(!options(v24, ENV).needsDefaultRenderer, "v24 is not mistaken for fresh settings");
        byte[] v23 = omit(v24, 12);
        v23[0] = 23;
        check(options(v23, ENV).musicVolume.getValue() == 23, "v23 retains saved audio");

        RspsGraphicsProfile.apply(loaded);
        profile(loaded);
        check(loaded.soundVolume.getValue() == 31 && loaded.musicVolume.getValue() == 23, "Reset preserves audio");
        check(loaded.screenSizeDefault.getValue() == 2 && loaded.screenSize.getValue() == 2, "Reset preserves window mode");
        check(loaded.customCursors.getValue() == 0, "Reset preserves cursor choice");
        check(loaded.toolkitDefault.getValue() == ToolkitType.GL && loaded.toolkit.getValue() == ToolkitType.GL,
                "Reset preserves selected renderer");
        check(Arrays.equals(encode(loaded), encode(options(encode(loaded), ENV))), "Reset persists and reloads");
        loaded.update(2, loaded.brightness);
        // The same aliases used at login must not reapply the RSPS preset.
        loaded.update(loaded.removeRoofs.getValue(), loaded.removeRoofsOverride);
        loaded.update(loaded.animateBackgroundDefault.getValue(), loaded.animateBackground);
        check(loaded.brightness.getValue() == 2, "Login aliases do not reset deliberate graphics changes");

        byte[] invalid = defaults.clone();
        invalid[20] = 4;
        invalid[22] = (byte)255;
        invalid[26] = (byte)255;
        invalid[29] = (byte)255;
        ClientOptions normalized = options(invalid, ENV);
        check(normalized.toolkitDefault.getValue() == ToolkitType.JAVA, "Unknown renderer 4 normalizes to software");
        check(normalized.cpuUsage.value() <= 4, "Invalid CPU value normalizes");
        check(normalized.waterDetail.getValue() <= 2, "Invalid water value normalizes");
        check(normalized.soundVolume.getValue() <= 127, "Invalid saved volume normalizes");
        ClientOptions tiny = options(null, new EnvironmentContext(false, 64, 1, true));
        check(tiny.toolkit.getValue() == ToolkitType.JAVA && tiny.particles.getValue() == 0,
                "Unsigned low-memory fallback remains supported");

        Path directory = Files.createTempDirectory("r13-preferences-test-");
        try {
            Path file = directory.resolve("preferences.dat");
            ClientPreferencesIO.write(file, new byte[100]);
            ClientPreferencesIO.write(file, saved);
            check(Arrays.equals(saved, Files.readAllBytes(file)), "Atomic save replaces old longer data");
            check(Arrays.equals(saved, encode(options(Files.readAllBytes(file), ENV))), "Disk reload preserves overrides");
            Path occupied = Files.createDirectory(directory.resolve("occupied"));
            Path sentinel = Files.write(occupied.resolve("keep.txt"), new byte[]{42});
            boolean rejected = false;
            try {
                ClientPreferencesIO.write(occupied, defaults);
            } catch (java.io.IOException expected) {
                rejected = true;
            }
            check(rejected && Files.readAllBytes(sentinel)[0] == 42, "Failed save preserves existing destination");
            try (var files = Files.list(directory)) {
                check(files.noneMatch(p -> p.getFileName().toString().startsWith(".rsps-preferences-")),
                        "Failed/successful writes clean temporary files");
            }
            Files.delete(sentinel);
            Files.delete(occupied);
            Files.delete(file);
        } finally {
            Files.delete(directory);
        }

        check(GraphicsRendererPolicy.defaultRenderer(true, true, true) == ToolkitType.GL, "Available GL default");
        check(GraphicsRendererPolicy.defaultRenderer(true, false, true) == ToolkitType.D3D, "DX alternative");
        check(GraphicsRendererPolicy.defaultRenderer(true, false, false) == ToolkitType.JAVA, "Missing native fallback");
        check(GraphicsRendererPolicy.defaultRenderer(false, true, true) == ToolkitType.JAVA, "Unsigned fallback");
        List<Integer> attempts = new ArrayList<>();
        var success = GraphicsRendererPolicy.create(ToolkitType.GL, id -> { attempts.add(id); return "hardware"; });
        check(success.renderer == ToolkitType.GL && attempts.equals(List.of(ToolkitType.GL)), "Successful hardware stays selected");
        attempts.clear();
        var failure = GraphicsRendererPolicy.create(ToolkitType.GL, id -> {
            attempts.add(id);
            if (id == ToolkitType.GL) throw new UnsatisfiedLinkError("Test native unavailable");
            return "software";
        });
        check(failure.renderer == ToolkitType.JAVA && attempts.equals(List.of(ToolkitType.GL, ToolkitType.JAVA)),
                "Native failure attempts hardware once, then software");
        attempts.clear();
        GraphicsRendererPolicy.create(ToolkitType.GL, id -> { attempts.add(id); return "software"; });
        check(attempts.equals(List.of(ToolkitType.JAVA)), "Failed native renderer is not retried this session");
        check(options(saved, ENV).toolkitDefault.getValue() == ToolkitType.JAVA, "Failed saved renderer becomes durable fallback");
        attempts.clear();
        GraphicsRendererPolicy.create(ToolkitType.D3D, id -> {
            attempts.add(id);
            if (id == ToolkitType.D3D) throw new IllegalStateException("Test driver initialization failed");
            return "software";
        });
        check(attempts.equals(List.of(ToolkitType.D3D, ToolkitType.JAVA)), "Driver failure uses bounded fallback");
        attempts.clear();
        GraphicsRendererPolicy.create(ToolkitType.SSE, id -> {
            attempts.add(id);
            if (id == ToolkitType.SSE) throw new OutOfMemoryError("Test native heap unavailable");
            return "software";
        });
        check(attempts.equals(List.of(ToolkitType.SSE, ToolkitType.JAVA)), "Native heap failure uses software fallback");
        boolean fatalErrorSurfaced = false;
        try {
            GraphicsRendererPolicy.create(ToolkitType.GLX, id -> { throw new ThreadDeath(); });
        } catch (ThreadDeath expected) {
            fatalErrorSurfaced = true;
        }
        check(fatalErrorSurfaced, "Thread termination is not swallowed as a graphics failure");
        boolean softwareFailureSurfaced = false;
        try {
            GraphicsRendererPolicy.create(ToolkitType.JAVA, id -> { throw new IllegalStateException("Test software failed"); });
        } catch (IllegalStateException expected) {
            softwareFailureSurfaced = true;
        }
        check(softwareFailureSurfaced, "Software failure surfaces without recursive retries");
        System.out.println("R13 regression checks passed: " + assertions);
    }

    private static ClientOptions options(byte[] data, EnvironmentContext environment) {
        return new ClientOptions(data == null ? null : new Packet(data), ModeGame.RUNESCAPE, ToolkitType.JAVA, environment);
    }
    private static byte[] encode(ClientOptions options) {
        Packet packet = options.encode();
        return Arrays.copyOf(packet.data, packet.pos);
    }
    private static void profile(ClientOptions options) {
        check(options.idleAnimations.getValue() == 0, "Few idle animations (verified in-game label)");
        check(options.antialiasingMode.getValue() == 0 && options.antialiasingQuality.getValue() == 0, "AA off");
        check(options.removeRoofs.getValue() == 1 && options.removeRoofsOverride.getValue() == 1, "Roofs always hidden");
        check(options.hardShadows.getValue() == 0 && options.spotShadows.getValue() == 0, "Expensive shadows off");
        check(options.skydetail.getValue() == 0 && options.bloom.getValue() == 0, "Sky and bloom off before hardware startup");
        check(options.groundDecor.getValue() == 1 && options.textures.getValue() == 1, "Home scenery preserved");
        check(options.flickeringEffects.getValue() == 1 && options.particles.getValue() == 1, "Combat effects retained");
    }
    private static byte[] omit(byte[] bytes, int index) {
        byte[] result = new byte[bytes.length - 1];
        System.arraycopy(bytes, 0, result, 0, index);
        System.arraycopy(bytes, index + 1, result, index, bytes.length - index - 1);
        return result;
    }
    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
