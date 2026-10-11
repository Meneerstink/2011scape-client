import jaggl.OpenGL;

/**
 * RSPS render A/B switches for the OpenGL toolkit (owner 2026-09-30: parts of npcs, pets, players and the mansion vanish,
 * sometimes by camera angle). Two mechanisms can hide geometry that the model data itself does not hide; each switch
 * turns one off so a single look in the client shows whether it is the cause:
 * <ul>
 *   <li>{@code cull}: GlToolkit enables back-face culling once for everything (GL_CULL_FACE / GL_BACK). A face whose
 *   winding is flipped - mirrored by a frame, folded by a bad bake, authored the other way round - is only visible from
 *   behind. With culling off such faces show up again from the front.</li>
 *   <li>{@code alphadepth}: the 667 hardware models (GL Model_Sub2, native Model_Sub1) draw translucent faces after the opaque
 *   ones in priority order, not back to front, WITH depth writes, so a near translucent face drawn first hides whatever of
 *   the model lies behind it and is drawn later (angle dependent: Little Nightmare, Elidinis' guardian's ward - owner
 *   2026-09-30). OSRS draws translucent faces back to front and never lets one hide another. Since 2026-10-01 the models
 *   draw each run of translucent faces (per-face alpha) without depth writes and every opaque face exactly as before
 *   ({@code alphaDepth = false}, the default). {@code alphadepth=1} restores the 667 behaviour for an A/B look. (The first
 *   version of this switch turned depth writes off for the WHOLE model, opaque faces included, which let a model's far
 *   side show through - the owner's "alpha off maakt het erger".) Material-level translucency (667 water, foliage) keeps
 *   the 667 path.</li>
 * </ul>
 * Driven by the server command {@code rendertest} on the side channel ({@code __RSPS_HUD__:render|cull=0,alphadepth=1});
 * every login resets it. The switches only change drawing state; no model data changes.
 */
final class RspsRenderTest {

    /** Back-face culling as the 667 toolkit sets it. */
    static volatile boolean cull = true;

    /** Depth writes for translucent faces as the 667 toolkit draws them (true), or none for them (false, the default). */
    static volatile boolean alphaDepth = false;

    private static boolean cullApplied = true;

    private RspsRenderTest() {
    }

    /** Whether any of the first [faces] faces is opaque (alpha 0): only then does the translucent split have something to keep. */
    static boolean hasOpaqueFace(byte[] alpha, int faces) {
        int end = Math.min(faces, alpha.length);
        for (int i = 0; i < end; i++) {
            if (alpha[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /** "cull=0,alphadepth=1" - missing keys keep their value; "reset" restores both. */
    static void handle(String payload) {
        if (payload.equals("reset")) {
            cull = true;
            alphaDepth = false;
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
    }

    static void afterModel(GlToolkit toolkit, boolean translucent) {
    }
}
