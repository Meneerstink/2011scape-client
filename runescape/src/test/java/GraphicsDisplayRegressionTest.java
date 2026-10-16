import com.jagex.EnvironmentContext;
import com.jagex.core.constants.ModeGame;
import com.jagex.core.io.Packet;
import com.jagex.graphics.ToolkitType;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;

public final class GraphicsDisplayRegressionTest {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            byte[] bytes = Files.readAllBytes(Path.of(args[0]));
            check(Arrays.equals(bytes, read(bytes).encodeLocal()), "Fresh JVM restores complete CUSTOM configuration");
            finish("Child JVM checks passed: ");
            return;
        }
        ClientOptions fresh = read(null);
        check(fresh.graphicsQuality.getValue() == 3, "Fresh MID label");
        check(fresh.toolkitDefault.getValue() == 1, "Fresh OpenGL");
        check(fresh.startupWindowMode() == 1, "Fresh fixed mode");
        check(fresh.brightness.getValue() == 3, "Rev-667 brightness 3");
        check(fresh.maxScreenSize.getValue() == 0, "Unrestricted rendering area");
        String[] names = {"brightness", "removeRoofs", "idleAnimations", "spotShadows", "lightDetail",
            "antialiasingMode", "antialiasingQuality", "particles", "groundBlending", "cpuUsage",
            "groundDecor", "hardShadows", "waterDetail", "fog", "customCursors", "textures",
            "flickeringEffects", "maxScreenSize", "screenSizeDefault", "toolkitDefault", "bloom", "buildArea"};
        // customCursors (index 14) defaults to Off (0) per owner requirement, not the deobfuscated
        // client's own default of On (1).
        int[] mid = {3,2,0,0,0,0,0,1,0,4,0,0,0,0,0,1,1,0,1,1,0,0};
        int[] values = {1,1,1,1,1,2,2,2,1,2,1,2,2,1,1,0,0,1,2,0,1,1};
        for (int i = 0; i < names.length; i++) {
            check(option(fresh, names[i]).value == mid[i], "MID " + names[i]);
            ClientOptions options = read(fresh.encodeLocal());
            options.toolkit.value = ToolkitType.GL;
            Map<String, Integer> before = snapshot(options);
            options.update(values[i], option(options, names[i]));
            check(option(options, names[i]).value == values[i], "Changed " + names[i]);
            // Jagex rule (client!kv.a, script 2699 AUTOSETUP_SETCUSTOM): ClientOptions.update() only sets the value; the level
            // becomes Custom only when the Graphics Options detail script says so (owner D3 round 2, 2026-10-16).
            check(options.graphicsQuality.getValue() == 3, "MID kept after " + names[i]);
            for (Map.Entry<String, Integer> field : before.entrySet()) {
                String name = field.getKey();
                if (name.equals(names[i]) || name.equals("graphicsQuality")) continue;
                if (name.startsWith("antialiasing") && names[i].startsWith("antialiasing")) continue;
                check(option(options, name).value == field.getValue(), names[i] + " preserves " + name);
            }
            byte[] bytes = options.encodeLocal();
            check(Arrays.equals(bytes, read(bytes).encodeLocal()), "Restart preserves " + names[i]);
        }
        levelKept(fresh, names, values);
        ClientOptions custom = read(fresh.encodeLocal());
        custom.toolkit.value = ToolkitType.GL;
        custom.update(1, custom.fog); custom.update(2, custom.hardShadows);
        custom.update(2, custom.antialiasingQuality); custom.update(1, custom.bloom);
        custom.update(0, custom.textures); custom.update(0, custom.groundBlending);
        check(custom.fog.getValue() == 1 && custom.hardShadows.getValue() == 2, "Dependencies retain values");
        check(custom.groundBlending.canSet(0) == 1 && custom.textures.canSet(1) == 1
            && custom.fog.canSet(1) == 1 && custom.waterDetail.canSet(2) == 1, "UI requests no collateral changes");
        custom.update(0, custom.toolkitDefault); custom.update(0, custom.toolkit);
        check(Arrays.equals(custom.encodeLocal(), read(custom.encodeLocal()).encodeLocal()), "Software retains hardware preferences");
        check(custom.antialiasingMode.getValue() == 2 && custom.bloom.getValue() == 1, "Renderer retains AA/bloom");
        custom.preferredWindowMode = 3; custom.windowWidth = 1537; custom.windowHeight = 913; custom.cameraZoom = 485;
        byte[] saved = custom.encodeLocal();
        ClientOptions restarted = read(saved);
        check(restarted.startupWindowMode() == 3 && restarted.windowWidth == 1537 && restarted.windowHeight == 913,
            "Fullscreen and window dimensions restored");
        check(restarted.cameraZoom == 485, "Zoom restored");
        check(custom.encode().pos == 35 && custom.encode().data[0] == 25, "Server remains v25 / 35 bytes");
        for (int length = 35; length < saved.length; length++)
            check(Arrays.equals(packet(custom), packet(read(Arrays.copyOf(saved, length)))), "Partial local trailer at " + length);
        // Owner D3 2026-10-11: a first start counts as "Leave Alone" (script 3411) - no "first time playing / Auto Setup" prompt
        // that would benchmark the PC up to HIGH, custom cursors off; saved client variables are never touched.
        int[] savedVarcs = Static511.varcs;
        Static511.varcs = new int[2000];
        Arrays.fill(Static511.varcs, -1);
        RspsGraphicsProfile.firstStartVarcs();
        check(Static511.varcs[1240] == 3 && Static511.varcs[1277] == 0 && Static511.varcs[987] == 0, "First start: Leave Alone, cursors off");
        Static511.varcs[987] = 1;
        Static511.varcs[1240] = 2;
        RspsGraphicsProfile.firstStartVarcs();
        check(Static511.varcs[1240] == 2 && Static511.varcs[987] == 1, "Saved client variables are kept");
        Static511.varcs = savedVarcs;
        int sky = custom.skydetail.getValue();
        RspsGraphicsProfile.apply(custom);
        check(custom.skydetail.getValue() == sky && custom.cameraZoom == 485, "MID preserves sky and camera");
        Path path = Files.createTempFile("graphics-restart-", ".dat");
        try {
            ClientPreferencesIO.write(path, saved);
            Process child = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
                GraphicsDisplayRegressionTest.class.getName(), path.toString()).inheritIO().start();
            check(child.waitFor() == 0, "Separate JVM reloads disk preferences");
        } finally { Files.deleteIfExists(path); }
        size(1919,1007,0,false,1919,1007); size(3440,1440,0,false,3440,1440);
        size(3840,2160,1,false,1024,768); size(3840,2160,2,false,800,600);
        size(3840,2160,2,true,3840,2160); size(700,450,1,false,700,450); size(0,-1,0,false,1,1);
        InterfaceManager.resizableScreen = true;
        Static552.aShort123 = 32767; Static598.aShort120 = 256;
        Static640.aShort122 = 256; Static640.aShort121 = 205;
        Static25.aShort1 = 1; Static306.aShort59 = 32767;
        Static498.method6643(0, false, 0, 1440, 3440);
        check(Static242.anInt3971 == 3440 && Static200.anInt3305 == 1440, "Wide viewport has no projection bars");
        CameraZoomController.restoreZoom(CameraZoomController.DEFAULT_ZOOM); Camera.zoom = 320; Static228.aShort45 = 256;
        Camera.method4606(0, 3000, 334, 0, 0, 0, 0);
        // OSRS zoom (b7df0498): the wheel scales the projection (viewport_setfov); the camera keeps its distance.
        int distance = -Camera.z;
        int scale = CameraZoomController.projectionScale(1000);
        CameraZoomController.applyWheelRotation(1, true, false, false, false, false, false);
        Camera.method4606(0, 3000, 334, 0, 0, 0, 0);
        check(-Camera.z == distance && CameraZoomController.projectionScale(1000) < scale, "Wheel out: smaller projection, same camera distance (fixed)");
        Camera.zoom = 320; Static228.aShort45 = 256;
        check(CameraZoomController.getZoom() == 487, "Script projection reset retains wheel zoom");
        Camera.method4606(0, 3000, 700, 0, 0, 0, 0); distance = -Camera.z;
        scale = CameraZoomController.projectionScale(1000);
        CameraZoomController.applyWheelRotation(-1, true, false, false, false, false, false);
        Camera.method4606(0, 3000, 700, 0, 0, 0, 0);
        check(-Camera.z == distance && CameraZoomController.projectionScale(1000) > scale, "Wheel in: larger projection, same camera distance (resized)");
        finish("Graphics/display regression checks passed: ");
    }
    /**
     * D3 class (owner 2026-10-11 "keep the player's own choice"; Keurmeester D3-1): no client path except the player's own
     * Graphics Options detail click may turn a chosen level into Custom. Every level x every option set by a non-UI caller x
     * every window-mode switch keeps the level; the Custom route itself (AUTOSETUP_SETCUSTOM) still works and survives a restart.
     */
    private static void levelKept(ClientOptions fresh, String[] names, int[] values) throws Exception {
        String[] levelNames = {"CUSTOM", "MIN", "LOW", "MID", "HIGH"};
        for (int level = 1; level <= 4; level++) {
            for (int i = 0; i < names.length; i++) {
                ClientOptions options = read(fresh.encodeLocal());
                options.toolkit.value = ToolkitType.GL;
                options.graphicsQuality.setValue(level);
                options.update(values[i], option(options, names[i]));
                check(options.graphicsQuality.getValue() == level, levelNames[level] + " kept after " + names[i]);
                check(read(options.encodeLocal()).graphicsQuality.getValue() == level, levelNames[level] + " kept after restart, " + names[i]);
            }
            ClientOptions options = read(fresh.encodeLocal());
            ClientOptions saved = ClientOptions.instance;
            ClientOptions.instance = options;
            try {
                options.graphicsQuality.setValue(level);
                // Graphics Options -> FIXED / RESIZEABLE / FULLSCREEN (script 2698 -> rememberWindowMode), three times over.
                int[] modes = {2, 1, 2, 1, 2, 3, 1};
                for (int mode : modes) {
                    options.rememberWindowMode(mode);
                    check(options.graphicsQuality.getValue() == level, levelNames[level] + " kept after window mode " + mode);
                    check(options.startupWindowMode() == mode, "Window mode " + mode + " remembered");
                }
                check(read(options.encodeLocal()).graphicsQuality.getValue() == level, levelNames[level] + " kept after window modes + restart");
            } finally {
                ClientOptions.instance = saved;
            }
        }
        ClientOptions custom = read(fresh.encodeLocal());
        custom.update(0, custom.graphicsQuality);
        check(custom.graphicsQuality.getValue() == 0 && read(custom.encodeLocal()).graphicsQuality.getValue() == 0,
            "AUTOSETUP_SETCUSTOM still makes Custom and Custom survives a restart");
        customOnlyFromScripts();
        sceneSaveKeepsPlayerSettings(fresh);
    }

    /**
     * The intro scene (RspsCinematic cine|1) runs on the 78 MID profile and keeps the player's own settings aside. A save during
     * the scene (wheel zoom, window mode) must write the player's settings, so a scene that never reaches cine|0 (client closed,
     * crash) does not leave a HIGH player on MID (D3 dossier OORZAAK 3, reproduced on the old build: before-2.txt).
     */
    private static void sceneSaveKeepsPlayerSettings(ClientOptions fresh) throws Exception {
        ClientOptions high = read(fresh.encodeLocal());
        high.toolkit.value = ToolkitType.GL;
        high.update(2, high.hardShadows);
        high.update(2, high.waterDetail);
        high.graphicsQuality.setValue(4);
        Packet own = high.encode();
        Field kept = RspsCinematic.class.getDeclaredField("kept");
        Field keptToolkit = RspsCinematic.class.getDeclaredField("keptToolkit");
        kept.setAccessible(true);
        keptToolkit.setAccessible(true);
        ClientOptions live = read(fresh.encodeLocal()); // the scene's MID profile
        live.cameraZoom = 485;
        live.preferredWindowMode = 2;
        ClientOptions saved = ClientOptions.instance;
        ClientOptions.instance = live;
        try {
            kept.set(null, Arrays.copyOf(own.data, own.pos));
            keptToolkit.setInt(null, ToolkitType.GL);
            ClientOptions restarted = read(live.encodeLocal());
            check(restarted.graphicsQuality.getValue() == 4 && restarted.hardShadows.getValue() == 2
                && restarted.waterDetail.getValue() == 2, "Save during the intro scene keeps the player's HIGH");
            check(restarted.cameraZoom == 485 && restarted.startupWindowMode() == 2, "Save during the scene keeps the live zoom and window mode");
            kept.set(null, null);
            check(read(live.encodeLocal()).graphicsQuality.getValue() == 3, "Without a scene the live options are saved");
        } finally {
            kept.set(null, null);
            keptToolkit.setInt(null, -1);
            ClientOptions.instance = saved;
        }
    }

    /**
     * The only places that may set the level to Custom are the Jagex ones: AUTOSETUP_SETCUSTOM (script 2699 / 3388) and
     * DETAIL_TOOLKIT_DEFAULT with its "keep level" flag 0 (script 2697 / 2705). A home-made markCustom() or update(0, graphicsQuality)
     * anywhere else is the D3 bug class again.
     */
    private static void customOnlyFromScripts() throws Exception {
        Path src = Path.of("src", "main", "java");
        java.util.List<String> found = new java.util.ArrayList<>();
        try (java.util.stream.Stream<Path> files = Files.walk(src)) {
            for (Path file : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".java"))::iterator) {
                java.util.List<String> lines = Files.readAllLines(file);
                for (int n = 0; n < lines.size(); n++) {
                    String line = lines.get(n).trim();
                    if (line.startsWith("//") || line.startsWith("*")) continue;
                    if (line.contains("markCustom()") || line.contains("graphicsQuality.setValue(0)")
                        || line.matches(".*update\\(0, *[A-Za-z.]*graphicsQuality\\).*")) {
                        found.add(src.relativize(file) + ":" + (n + 1) + " " + line);
                    }
                }
            }
        }
        check(found.size() == 2 && found.stream().allMatch(f -> f.startsWith("ScriptRunner.java:")),
            "Custom is set only by AUTOSETUP_SETCUSTOM and DETAIL_TOOLKIT_DEFAULT, found " + found);
    }

    private static ClientOptions read(byte[] bytes) {
        return new ClientOptions(bytes == null ? null : new Packet(bytes), ModeGame.RUNESCAPE, ToolkitType.JAVA,
            new EnvironmentContext(true,1024,4,false));
    }
    private static byte[] packet(ClientOptions options) { Packet p = options.encode(); return Arrays.copyOf(p.data,p.pos); }
    private static Option option(ClientOptions o, String name) throws Exception { return (Option) ClientOptions.class.getField(name).get(o); }
    private static Map<String,Integer> snapshot(ClientOptions o) throws Exception {
        Map<String,Integer> fields = new LinkedHashMap<>();
        for (Field f : ClientOptions.class.getFields()) if (Option.class.isAssignableFrom(f.getType()))
            fields.put(f.getName(), ((Option)f.get(o)).value);
        return fields;
    }
    private static void size(int w,int h,int max,boolean fs,int ew,int eh) {
        check(Arrays.equals(DisplaySizing.canvas(w,h,max,fs),new int[]{ew,eh}), "Canvas " + w + "x" + h);
    }
    private static final List<String> failures = new ArrayList<>();
    /** Every failed check is listed (D3 round 2), so one run shows the whole class, not only the first failure. */
    private static void check(boolean ok,String label) { assertions++; if (!ok) { failures.add(label); System.out.println("FAILED: " + label); } }
    private static void finish(String passed) {
        if (!failures.isEmpty()) throw new AssertionError(failures.size() + " of " + assertions + " checks failed, first: " + failures.get(0));
        System.out.println(passed + assertions);
    }
}
