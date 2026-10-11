import org.openrs2.deob.annotation.OriginalArg;
import org.openrs2.deob.annotation.OriginalClass;
import org.openrs2.deob.annotation.OriginalMember;
import org.openrs2.deob.annotation.Pc;

import java.awt.Canvas;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Hashtable;

@OriginalClass("client!on")
public final class JavaDefaultSurface extends JavaSurface {

    @OriginalMember(owner = "client!on", name = "r", descriptor = "Ljava/awt/Image;")
    public Image image;

    @OriginalMember(owner = "client!on", name = "t", descriptor = "Ljava/awt/Rectangle;")
    public Rectangle clipBox;

    @OriginalMember(owner = "client!on", name = "q", descriptor = "Ljava/awt/Shape;")
    public Shape clip;

    @OriginalMember(owner = "client!on", name = "s", descriptor = "Ljava/awt/Canvas;")
    public Canvas canvas;

    @OriginalMember(owner = "client!on", name = "<init>", descriptor = "()V")
    public JavaDefaultSurface() {
    }

    @OriginalMember(owner = "client!on", name = "a", descriptor = "(IIIZLjava/awt/Graphics;III)V")
    @Override
    public void clip(@OriginalArg(2) int centerX, @OriginalArg(6) int centerY, @OriginalArg(1) int x, @OriginalArg(7) int y, @OriginalArg(0) int width, @OriginalArg(5) int height, @OriginalArg(4) Graphics graphics) {
        this.clip = graphics.getClip();
        this.clipBox.height = height;
        this.clipBox.x = centerX;
        this.clipBox.y = centerY;
        this.clipBox.width = width;
        if (com.jagex.StretchedMode.active() && this.canvas == com.jagex.game.runetek6.client.GameShell.canvas) {
            // RuneLite Stretched Mode (Hooks.draw): the whole real-size frame drawn over the stretched canvas.
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) graphics;
            g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, com.jagex.StretchedMode.fast()
                ? java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(this.image, 0, 0, com.jagex.StretchedMode.stretchedWidth(), com.jagex.StretchedMode.stretchedHeight(), this.canvas);
            return;
        }
        graphics.setClip(this.clipBox);
        graphics.drawImage(this.image, centerX - x, centerY - y, this.canvas);
        graphics.setClip(this.clip);
    }

    @OriginalMember(owner = "client!on", name = "a", descriptor = "(ZIILjava/awt/Canvas;)V")
    @Override
    public void initialize(@OriginalArg(3) Canvas canvas, @OriginalArg(1) int width, @OriginalArg(2) int height) {
        this.canvas = canvas;
        this.clipBox = new Rectangle();
        this.height = height;
        this.width = width;
        this.raster = new int[this.height * this.width];
        @Pc(39) DataBufferInt local39 = new DataBufferInt(this.raster, this.raster.length);
        @Pc(47) DirectColorModel local47 = new DirectColorModel(32, 16711680, 65280, 255);
        @Pc(57) WritableRaster local57 = Raster.createWritableRaster(local47.createCompatibleSampleModel(this.width, this.height), local39, null);
        this.image = new BufferedImage(local47, local57, false, new Hashtable());
    }
}
