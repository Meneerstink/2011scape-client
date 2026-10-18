package com.jagex;

/**
 * A2 round 9 review (nakijker FOUTEN 1 + 3): how DirectX shows the game in the window-size back buffer, written against a small
 * device seam so the call order can be tested without the native jagdx device (StretchedSwitchSurfaceRegressionTest section 2b).
 *
 * <ul>
 * <li>A 1:1 frame (Stretched OFF: the fixed 765x503 game centred in a bigger window, or the resizable game) is DRAWN IN PLACE:
 * D3DToolkit offsets its viewport and scissor rectangle by {@link #origin} (the jagdx device draws every vertex through the
 * viewport - dxVertexLayout has no pre-transformed POSITIONT element), so nothing has to be copied; flip only clears the
 * margins, one viewport strip per side. No StretchRect at all, so this also holds for a multisampled (Anti-aliasing) back
 * buffer.</li>
 * <li>A scaled frame (Stretched ON) is drawn at the top left and moved: StretchRect back buffer -> a real-size render target
 * (1:1), Clear, StretchRect the copy onto the drawn rectangle. Direct3D 9 cannot StretchRect INTO a multisampled surface, so
 * with Stretched ON the DirectX back buffer is created without samples ({@link StretchedMode#d3dWindowSamples}); a frame that
 * meets a multisampled back buffer anyway (the one frame after Stretched was switched on) is not moved and the device is reset
 * with the right samples ({@link #RESET}).</li>
 * </ul>
 * jagdx rectangles of StretchRect are x, y, width, height (the 667 client's own D3DToolkit.na reads StretchRect(back, x, y,
 * width, height, copy, 0, 0, width, height)); SetScissorRect takes left, top, right, bottom (NativeToolkit.KA keeps x2 / y2).
 */
public final class D3DPresent {

    /** The jagdx calls the present makes (D3DToolkit implements it on the real device; the class test records them). */
    public interface Device {
        /** The window-size back buffer of the main canvas. */
        Object backBuffer();

        /** The back buffer is multisampled (D3DPRESENT_PARAMETERS.MultiSampleType != 0). */
        boolean multisampled();

        /** CreateRenderTarget width x height in the back buffer's format, not multisampled, not lockable. */
        Object createRenderTarget(int width, int height);

        void release(Object surface);

        /** jagdx StretchRect: source x, y, width, height -> destination x, y, width, height. */
        int stretchRect(Object source, int x, int y, int width, int height, Object destination, int dx, int dy, int dwidth, int dheight, int filter);

        void setViewport(int x, int y, int width, int height);

        void scissorTest(boolean on);

        /** Clear the render target (D3DCLEAR_TARGET) inside the current viewport. */
        int clearBlack();

        /** Back to the toolkit's own viewport and scissor test. */
        void restoreState();
    }

    public static final int OK = 0;
    public static final int FAILED = 1;
    /** The scaled frame met a multisampled back buffer: not moved, reset the device (StretchedMode.d3dWindowSamples). */
    public static final int RESET = 2;

    /** D3D_OK is 0; jagdx returns the HRESULT. */
    static boolean ok(int hr) {
        return hr >= 0;
    }

    private Object copy;
    private int copyWidth;
    private int copyHeight;

    /** Where the toolkit draws a frame inside the back buffer: the drawn rectangle's corner for a 1:1 frame, else 0,0. */
    public static int[] origin(int[] plan) {
        if (plan == null || plan[6] != 0) {
            return new int[] {0, 0};
        }
        return new int[] {plan[2], plan[3]};
    }

    /**
     * Before Present. [plan] = StretchedMode.d3dPresentPlan(): {realWidth, realHeight, x, y, width, height, filter} or null
     * (1:1 over the whole canvas: nothing to do).
     */
    public int place(int[] plan, int canvasWidth, int canvasHeight, Device device) {
        if (plan == null) {
            return OK;
        }
        int realWidth = plan[0];
        int realHeight = plan[1];
        int x = plan[2];
        int y = plan[3];
        int width = plan[4];
        int height = plan[5];
        int filter = plan[6];
        if (filter == 0) {
            // Drawn in place (origin): only the margins, one strip per side, each cleared through its own viewport.
            device.scissorTest(false);
            int failed = 0;
            failed += strip(device, 0, 0, canvasWidth, y);                                    // top
            failed += strip(device, 0, y + height, canvasWidth, canvasHeight - y - height);   // bottom
            failed += strip(device, 0, y, x, height);                                         // left
            failed += strip(device, x + width, y, canvasWidth - x - width, height);           // right
            device.restoreState();
            return failed == 0 ? OK : FAILED;
        }
        if (device.multisampled()) {
            return RESET; // Direct3D 9: no StretchRect into a multisampled surface
        }
        Object back = device.backBuffer();
        if (back == null) {
            return OK;
        }
        if (this.copy == null || this.copyWidth != realWidth || this.copyHeight != realHeight) {
            this.release(device);
            try {
                this.copy = device.createRenderTarget(realWidth, realHeight);
            } catch (RuntimeException e) {
                this.copy = null;
            }
            if (this.copy == null) {
                StretchedMode.d3dFailed("CreateRenderTarget " + realWidth + "x" + realHeight);
                return FAILED;
            }
            this.copyWidth = realWidth;
            this.copyHeight = realHeight;
        }
        int copied = device.stretchRect(back, 0, 0, realWidth, realHeight, this.copy, 0, 0, realWidth, realHeight, 0);
        if (!ok(copied)) {
            StretchedMode.d3dFailed("StretchRect copy " + copied);
            return FAILED;
        }
        device.setViewport(0, 0, canvasWidth, canvasHeight);
        device.scissorTest(false);
        device.clearBlack();
        int placed = device.stretchRect(this.copy, 0, 0, realWidth, realHeight, back, x, y, width, height, filter);
        device.restoreState();
        if (!ok(placed)) {
            StretchedMode.d3dFailed("StretchRect place " + placed);
            return FAILED;
        }
        return OK;
    }

    private static int strip(Device device, int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        device.setViewport(x, y, width, height);
        return ok(device.clearBlack()) ? 0 : 1;
    }

    /** A D3DPOOL_DEFAULT render target must be released before every Reset and when the toolkit stops. */
    public void release(Device device) {
        if (this.copy != null) {
            device.release(this.copy);
            this.copy = null;
        }
    }

    /** Tests: the render target is held. */
    public boolean holdsCopy() {
        return this.copy != null;
    }
}
