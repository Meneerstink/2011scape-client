import jaggl.OpenGL;

/**
 * RSPS render A/B switches for the OpenGL toolkit (owner 2026-09-30: parts of npcs, pets, players and the mansion vanish,
 * sometimes by camera angle). Two mechanisms can hide geometry that the model data itself does not hide; each switch
 * turns one off so a single look in the client shows whether it is the cause:
 * <ul>
 *   <li>{@code cull}: GlToolkit enables back-face culling once for everything (GL_CULL_FACE / GL_BACK). A face whose
 *   winding is flipped - mirrored by a frame, folded by a bad bake, authored the other way round - is only visible from
 *   behind. With culling off such faces show up again from the front.</li>
 *   <li>{@code alphadepth}: the GL model draws translucent faces after the opaque ones in priority order, not back to front,
 *   with depth writes on, so a near translucent face drawn first hides the translucent faces behind it (angle dependent).
 *   With this off, models that have translucent faces are drawn without writing depth.</li>
 * </ul>
 * Driven by the server command {@code rendertest} on the side channel ({@code __RSPS_HUD__:render|cull=0,alphadepth=0});
 * default is the normal renderer, and every login resets it. The switches only change drawing state; no model data changes.
 */
final class RspsRenderTest {

    /** Back-face culling as the 667 toolkit sets it. */
    static volatile boolean cull = true;

    /** Depth writes for models that contain translucent faces, as the 667 toolkit draws them. */
    static volatile boolean alphaDepth = true;

    private static boolean cullApplied = true;

    private RspsRenderTest() {
    }

    /** "cull=0,alphadepth=1" - missing keys keep their value; "reset" restores both. */
    static void handle(String payload) {
        if (payload.equals("reset")) {
            cull = true;
            alphaDepth = true;
        } else {
            for (String part : payload.split(",")) {
                int eq = part.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                boolean on = !part.substring(eq + 1).trim().equals("0");
                String key = part.substring(0, eq).trim();
                if (key.equals("cull")) {
                    cull = on;
                } else if (key.equals("alphadepth")) {
                    alphaDepth = on;
                }
            }
        }
        System.out.println("[modeltrace " + System.currentTimeMillis() + "] render test: cull=" + cull + " alphadepth=" + alphaDepth);
    }

    /** Before a GL model draws its faces ([translucent]: it has translucent faces). */
    static void beforeModel(GlToolkit toolkit, boolean translucent) {
        if (cull != cullApplied) {
            if (cull) {
                OpenGL.glEnable(OpenGL.GL_CULL_FACE);
            } else {
                OpenGL.glDisable(OpenGL.GL_CULL_FACE);
            }
            cullApplied = cull;
        }
        if (!alphaDepth && translucent) {
            toolkit.C(false);
        }
    }

    static void afterModel(GlToolkit toolkit, boolean translucent) {
        if (!alphaDepth && translucent) {
            toolkit.C(true);
        }
    }
}
