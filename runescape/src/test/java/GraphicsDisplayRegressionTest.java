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
        int[] mid = {3,1,0,0,0,0,0,1,0,4,0,0,0,0,0,1,1,0,1,1,0,0};
        int[] values = {1,2,1,1,1,2,2,2,1,2,1,2,2,1,1,0,0,1,2,0,1,1};
        for (int i = 0; i < names.length; i++) {
            check(option(fresh, names[i]).value == mid[i], "MID " + names[i]);
            ClientOptions options = read(fresh.encodeLocal());
            options.toolkit.value = ToolkitType.GL;
            Map<String, Integer> before = snapshot(options);
            options.update(values[i], option(options, names[i]));
            check(option(options, names[i]).value == values[i], "Changed " + names[i]);
            check(options.graphicsQuality.getValue() == 0, "CUSTOM after " + names[i]);
            for (Map.Entry<String, Integer> field : before.entrySet()) {
                String name = field.getKey();
                if (name.equals(names[i]) || name.equals("graphicsQuality")) continue;
                if (name.startsWith("antialiasing") && names[i].startsWith("antialiasing")) continue;
                check(option(options, name).value == field.getValue(), names[i] + " preserves " + name);
            }
            byte[] bytes = options.encodeLocal();
            check(Arrays.equals(bytes, read(bytes).encodeLocal()), "Restart preserves " + names[i]);
        }
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
        CameraZoomController.restoreZoom(320); Camera.zoom = 320; Static228.aShort45 = 256;
        Camera.method4606(0, 3000, 334, 0, 0, 0, 0);
        int distance = -Camera.z;
        CameraZoomController.applyWheelRotation(1, true, false, false, false, false, false);
        Camera.method4606(0, 3000, 334, 0, 0, 0, 0);
        check(-Camera.z > distance, "Wheel changes actual fixed-mode camera position");
        Camera.zoom = 320; Static228.aShort45 = 256;
        check(CameraZoomController.getZoom() == 335, "Script projection reset retains wheel zoom");
        Camera.method4606(0, 3000, 700, 0, 0, 0, 0); distance = -Camera.z;
        CameraZoomController.applyWheelRotation(-1, true, false, false, false, false, false);
        Camera.method4606(0, 3000, 700, 0, 0, 0, 0);
        check(-Camera.z < distance, "Wheel changes actual resized camera position");
        System.out.println("Graphics/display regression checks passed: " + assertions);
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
    private static void check(boolean ok,String label) { assertions++; if (!ok) throw new AssertionError(label); }
}
