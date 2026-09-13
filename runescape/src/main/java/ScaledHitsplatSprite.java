import com.jagex.graphics.ClippingMask;
import com.jagex.graphics.Sprite;

/**
 * RCV-010 A6: draws one hitsplat sprite part (icon, left, inner tile, right) {@link OldStyleHitsplatDisplay#SIZE_PERCENT}
 * of its cache size. Width, height and projected offsets are reported scaled so the existing hitmark layout in
 * {@link OverlayManager} (tile count, centring, spacing) is computed for the bigger splat unchanged; drawing goes
 * through the toolkit's own scaled blit ({@code Sprite.render(x, y, width, height, op, colour, mode)}).
 */
public final class ScaledHitsplatSprite extends Sprite {
    private final Sprite inner;

    private ScaledHitsplatSprite(Sprite inner) {
        this.inner = inner;
    }

    public static Sprite wrap(Sprite sprite) {
        return sprite == null ? null : new ScaledHitsplatSprite(sprite);
    }

    @Override
    public int getHeight() {
        return OldStyleHitsplatDisplay.size(this.inner.getHeight());
    }

    @Override
    public int getWidth() {
        return OldStyleHitsplatDisplay.size(this.inner.getWidth());
    }

    @Override
    public int scaleHeight() {
        return OldStyleHitsplatDisplay.size(this.inner.scaleHeight());
    }

    @Override
    public int scaleWidth() {
        return OldStyleHitsplatDisplay.size(this.inner.scaleWidth());
    }

    @Override
    public void setOffsets(int x1, int y1, int x2, int y2) {
        this.inner.setOffsets(x1, y1, x2, y2);
    }

    @Override
    public void projectOffsets(int[] destination) {
        this.inner.projectOffsets(destination);
        for (int i = 0; i < destination.length; i++) {
            destination[i] = OldStyleHitsplatDisplay.size(destination[i]);
        }
    }

    @Override
    public void render(int x, int y, int op, int color, int mode) {
        this.inner.render(x, y, this.getWidth(), this.getHeight(), op, color, mode);
    }

    @Override
    public void render(int x, int y, ClippingMask mask, int maskX, int maskY) {
        this.inner.render(x, y, mask, maskX, maskY);
    }

    @Override
    public void renderTiled(int x, int y, int width, int height, int op, int colour, int mode) {
        this.inner.renderTiled(x, y, width, height, op, colour, mode);
    }

    @Override
    protected void renderImpl(int x, int y, int width, int height, int op, int colour, int mode) {
        this.inner.render(x, y, width, height, op, colour, mode);
    }

    @Override
    protected void renderImpl(float x, float y, float width, float height, float op, float colour, int mode, int filter) {
        this.inner.renderParallelogram(x, y, width, height, op, colour, mode, filter);
    }

    @Override
    protected void renderParallelogramImpl(float centerX, float centerY, float x1, float y1, float x2, float y2, ClippingMask mask, int maskX, int maskY) {
        this.inner.renderParallelogram(centerX, centerY, x1, y1, x2, y2, mask, maskX, maskY);
    }

    @Override
    public void copyAlpha(int x, int y, int channel) {
        this.inner.copyAlpha(x, y, channel);
    }

    @Override
    public void copyRect(int x, int y, int width, int height, int op, int colour) {
        this.inner.copyRect(x, y, width, height, op, colour);
    }
}
