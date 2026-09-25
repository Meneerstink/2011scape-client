/**
 * RSPS: per-texture resolution for the hardware toolkits (OpenGL / native). The 667 client builds every texture at one
 * global size (128 unless `texsize` is used), which blurs artwork such as the 78 house banner. The OpenGL and native
 * toolkits upload each texture as its own GPU texture with normalised UVs, so a few named textures can safely be built
 * larger without touching any other texture. The software toolkit shares one texture size in its rasteriser and is
 * left at the global size.
 */
public final class RspsTextures {

    /** 78 house banner cloth (HouseBannerTool / materials id). */
    public static final int BANNER_78 = 1409;

    /** Resolution for the high-resolution textures; OpenGL accepts 128..1024 (GlToolkit.setTextureSize). */
    public static final int HIGH_RES = 512;

    /** Deadman emblem plate textures, tiers 1..6 (DeadmanEmblemTool / materials ids), painted at 256. */
    public static final int EMBLEM_FIRST = 1410;
    public static final int EMBLEM_LAST = 1415;
    public static final int EMBLEM_RES = 256;

    private RspsTextures() {
    }

    public static int hardwareSize(int textureId, int defaultSize) {
        if (textureId == BANNER_78) {
            return Math.max(defaultSize, HIGH_RES);
        }
        if (textureId >= EMBLEM_FIRST && textureId <= EMBLEM_LAST) {
            return Math.max(defaultSize, EMBLEM_RES);
        }
        return defaultSize;
    }
}
