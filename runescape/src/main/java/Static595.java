import com.jagex.Client;
import com.jagex.game.runetek6.client.GameShell;
import com.jagex.IndexedImage;
import com.jagex.graphics.Font;
import com.jagex.graphics.FontMetrics;
import com.jagex.graphics.Fonts;
import com.jagex.graphics.MemoryPool;
import com.jagex.graphics.Toolkit;
import com.jagex.graphics.ToolkitType;
import com.jagex.js5.js5;
import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

public final class Static595 {

    @OriginalMember(owner = "client!so", name = "S", descriptor = "[I")
    public static int[] anIntArray702 = new int[1];

    @OriginalMember(owner = "client!so", name = "a", descriptor = "(ILjava/lang/String;ZI)V")
    public static void setToolkit(@OriginalArg(1) String arg0, @OriginalArg(2) boolean inactive, @OriginalArg(3) int toolkit) {
        Static164.method2606();
        int requested = toolkit;
        if (arg0 != null) {
            Toolkit loading = Static255.create(ToolkitType.JAVA, js5.SHADERS,
                    GameShell.canvas, Js5TextureSource.instance, 0);
            try {
                loading.GA(0);
                FontMetrics metrics = FontMetrics.loadGroup(js5.FONTMETRICS, Fonts.p12FullGroup);
                Font font = loading.createFont(metrics,
                        IndexedImage.load(js5.SPRITES, Fonts.p12FullGroup, 0), true);
                Static288.repaintMargins();
                MessageBox.draw(loading, arg0, true, metrics, font);
            } finally {
                loading.free();
            }
        }
        // Stretched Mode on: "Software" runs the Java software renderer, the one that can stretch (StretchedMode.rendererFor).
        int create = com.jagex.StretchedMode.rendererFor(toolkit);
        GraphicsRendererPolicy.Result<Toolkit> created = GraphicsRendererPolicy.create(
                create, Static595::createWithHeap);
        Toolkit.active = created.value;
        // The player's choice stays what they picked (Software = SSE), not the stand-in.
        toolkit = create != toolkit && created.renderer == create ? toolkit : created.renderer;
        // RuneLite Stretched Mode draws through the software renderer, OpenGL's offscreen framebuffer or DirectX's scaled
        // Present; SSE, GLX and the orthographic toolkit draw straight into the canvas and stay unstretched (the layout
        // follows below).
        com.jagex.StretchedMode.toolkitChanged(!ClientOptions.instance.orthographic.isToolkitCompatible()
            && (Toolkit.active instanceof JavaToolkit || Toolkit.active instanceof D3DToolkit
                || Toolkit.active instanceof GlToolkit && ((GlToolkit) Toolkit.active).rspsStretchSupported()));
        // A2 round 8: with OpenGL (framebuffer objects, a plain window) the canvas fills the window in every mode and the game is
        // blitted into it, so a fixed <-> resizable switch never resizes the window or the canvas (RuneLite ClientUI).
        boolean gl = Toolkit.active instanceof GlToolkit && !ClientOptions.instance.orthographic.isToolkitCompatible()
            && ((GlToolkit) Toolkit.active).rspsStretchSupported();
        // A2 round 8 review: the window is plain whenever the driver has framebuffer objects; the player's anti-aliasing is
        // drawn into a multisampled offscreen framebuffer and resolved (GlToolkit.rspsOffscreenSamples).
        boolean plain = gl && ((GlToolkit) Toolkit.active).rspsPlainWindow();
        com.jagex.StretchedMode.glToolkit(gl, plain, plain ? ((GlToolkit) Toolkit.active).rspsOffscreenSamples() : 0);
        // A2 round 9: DirectX too - the canvas fills the window and flip places the frame (D3DToolkit.rspsPlaceFrame).
        com.jagex.StretchedMode.d3dToolkit(Toolkit.active instanceof D3DToolkit && !ClientOptions.instance.orthographic.isToolkitCompatible());
        // A2 round 9 review: the software renderers too (SSE / Java): the canvas fills the window, flip draws the game at its place.
        com.jagex.StretchedMode.swToolkit((Toolkit.active instanceof oa || Toolkit.active instanceof JavaToolkit) && !ClientOptions.instance.orthographic.isToolkitCompatible());
        if (toolkit != requested && requested == ToolkitType.SSE) {
            Static171.graphicsError = true;
        }
        ClientOptions.instance.toolkit.setActive(!inactive);
        ClientOptions.instance.update(toolkit, ClientOptions.instance.toolkit);
        Static112.method2109();
        Toolkit.active.method8003();
        Toolkit.active.X(32);
        Static460.aMatrix_10 = Toolkit.active.createMatrix();
        Static59.aMatrix_5 = Toolkit.active.createMatrix();
        Static209.method3110();
        Toolkit.active.setShrinkTextures(ClientOptions.instance.smallTextures.getValue() == 1);
        if (Toolkit.active.supportsBloom() && ClientOptions.instance.bloom.getValue() == 1) {
            Toolkit.active.hasBloom();
        }
        Static613.method8239(Toolkit.active, Static501.mapLength >> 3, Static720.mapWidth >> 3);
        InterfaceManager.loginOpened();
        Client.changingWindowMode = true;
        Fullscreen.modes = null;
        Static503.sentPreferences = false;
        OrthoMode.enter();
        if (requested != toolkit) {
            // Validation normalizes only the failed preference, preserving other saved choices.
            ClientOptions.save();
        }
    }

    private static Toolkit createWithHeap(int type) {
        // RuneLite GpuPlugin: OpenGL gets a plain (not multisampled) window whenever it has framebuffer objects; anti-aliasing is
        // drawn offscreen (StretchedMode.glWindowSamples, GlToolkit.rspsWindowNeedsRebuild).
        int playerSamples = ClientOptions.instance.antialiasingQuality.getValue() * 2;
        int antialiasing = type == ToolkitType.GL ? com.jagex.StretchedMode.glWindowSamples(playerSamples) : playerSamples;
        Toolkit created = Static255.create(type, js5.SHADERS, GameShell.canvas,
                Js5TextureSource.instance, antialiasing);
        boolean ready = false;
        try {
            if (type != ToolkitType.JAVA && created.method7949()) {
                boolean largeHeap = SystemInfo.instance == null || SystemInfo.instance.totalMemory > 256;
                MemoryPool heap = created.createHeap(largeHeap ? 0x8C00000 : 0x6400000);
                created.method7938(heap);
            }
            ready = true;
            return created;
        } finally {
            if (!ready) {
                try {
                    created.free();
                } catch (RuntimeException | LinkageError ignored) {
                    // Keep the original initialization failure for the fallback decision.
                }
            }
        }
    }

    @OriginalMember(owner = "client!so", name = "a", descriptor = "(IBIII)V")
    public static void method7810(@OriginalArg(0) int bottom, @OriginalArg(2) int right, @OriginalArg(3) int top, @OriginalArg(4) int left) {
        @Pc(5) int local5 = Static691.anInt10368;
        if (local5 == 0) {
            return;
        }
        if (local5 == 1) {
            Static599.anInt8837 = right;
            Static691.anInt10368 = 2;
            Static435.anInt6597 = left;
            Static320.anInt5085 = bottom;
            Static582.anInt8629 = top;
        } else if (local5 == 2) {
            if (Static320.anInt5085 > bottom) {
                Static320.anInt5085 = bottom;
            }
            if (Static435.anInt6597 > left) {
                Static435.anInt6597 = left;
            }
            if (Static599.anInt8837 < right) {
                Static599.anInt8837 = right;
            }
            if (Static582.anInt8629 < top) {
                Static582.anInt8629 = top;
            }
        }
    }

}
