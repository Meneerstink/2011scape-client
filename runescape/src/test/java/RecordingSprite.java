import com.jagex.graphics.ClippingMask;
import com.jagex.graphics.Sprite;

/** Test double for {@link OldStyleHitsplatDisplayRegressionTest}: records the last scaled blit. */
final class RecordingSprite extends Sprite {
    private final int width;
    private final int height;
    int lastX;
    int lastY;
    int lastWidth;
    int lastHeight;
    int lastColour;
    int lastMode;

    RecordingSprite(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override public int getHeight() { return this.height; }
    @Override public int getWidth() { return this.width; }
    @Override public int scaleHeight() { return this.height; }
    @Override public int scaleWidth() { return this.width; }
    @Override public void setOffsets(int x1, int y1, int x2, int y2) { }
    @Override public void projectOffsets(int[] destination) { destination[0] = 2; destination[1] = -3; }
    @Override public void render(int x, int y, int op, int color, int mode) { this.lastWidth = -1; }
    @Override public void render(int x, int y, ClippingMask mask, int maskX, int maskY) { }
    @Override public void renderTiled(int x, int y, int width, int height, int op, int colour, int mode) { }

    @Override
    protected void renderImpl(int x, int y, int width, int height, int op, int colour, int mode) {
        this.lastX = x;
        this.lastY = y;
        this.lastWidth = width;
        this.lastHeight = height;
        this.lastColour = colour;
        this.lastMode = mode;
    }

    @Override protected void renderImpl(float x, float y, float width, float height, float op, float colour, int mode, int filter) { }
    @Override protected void renderParallelogramImpl(float centerX, float centerY, float x1, float y1, float x2, float y2, ClippingMask mask, int maskX, int maskY) { }
    @Override public void copyAlpha(int x, int y, int channel) { }
    @Override public void copyRect(int x, int y, int width, int height, int op, int colour) { }
}
