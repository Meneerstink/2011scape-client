package rsps.runelite;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * The image operations RuneLite applies to its sidebar icons (net.runelite.client.util.ImageUtil: grayscaleImage,
 * luminanceScale, luminanceOffset, flipImage, rotateImage), so the derived icons (the OFF plugin switch, the dimmed star,
 * the section arrows) come out as in RuneLite. Written for this client; the icon files are RuneLite's (resources
 * /runelite, BSD-2-Clause, runelite/LICENSE.txt).
 */
public final class ImageUtil
{
	private ImageUtil()
	{
	}

	public static BufferedImage load(String name)
	{
		try (InputStream in = ImageUtil.class.getResourceAsStream("/runelite/" + name))
		{
			BufferedImage image = ImageIO.read(in);
			BufferedImage argb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = argb.createGraphics();
			g.drawImage(image, 0, 0, null);
			g.dispose();
			return argb;
		}
		catch (Exception missing)
		{
			return new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		}
	}

	private interface PixelOp
	{
		int apply(int argb);
	}

	private static BufferedImage map(BufferedImage image, PixelOp op)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				out.setRGB(x, y, op.apply(image.getRGB(x, y)));
			}
		}
		return out;
	}

	private static int clamp(int v)
	{
		return Math.max(0, Math.min(255, v));
	}

	/** Grey by luminance (0.299 R + 0.587 G + 0.114 B), alpha kept. */
	public static BufferedImage grayscaleImage(BufferedImage image)
	{
		return map(image, argb ->
		{
			int r = (argb >> 16) & 0xFF;
			int g = (argb >> 8) & 0xFF;
			int b = argb & 0xFF;
			int l = clamp((int) Math.round(0.299 * r + 0.587 * g + 0.114 * b));
			return (argb & 0xFF000000) | (l << 16) | (l << 8) | l;
		});
	}

	/** Every colour channel times [scale], alpha kept. */
	public static BufferedImage luminanceScale(BufferedImage image, float scale)
	{
		return map(image, argb -> (argb & 0xFF000000)
			| (clamp((int) (((argb >> 16) & 0xFF) * scale)) << 16)
			| (clamp((int) (((argb >> 8) & 0xFF) * scale)) << 8)
			| clamp((int) ((argb & 0xFF) * scale)));
	}

	/** RuneLite ImageUtil.alphaOffset(Image, int): the alpha channel plus [offset] (RescaleOp offset), clamped; colours kept. */
	public static BufferedImage alphaOffset(BufferedImage image, int offset)
	{
		return map(image, argb -> (clamp(((argb >>> 24) & 0xFF) + offset) << 24) | (argb & 0xFFFFFF));
	}

	/** RuneLite ImageUtil.alphaOffset(Image, float): the alpha channel times [percentage], clamped; colours kept. */
	public static BufferedImage alphaOffset(BufferedImage image, float percentage)
	{
		return map(image, argb -> (clamp((int) (((argb >>> 24) & 0xFF) * percentage)) << 24) | (argb & 0xFFFFFF));
	}

	/** Every colour channel plus [offset], alpha kept. */
	public static BufferedImage luminanceOffset(BufferedImage image, int offset)
	{
		return map(image, argb -> (argb & 0xFF000000)
			| (clamp(((argb >> 16) & 0xFF) + offset) << 16)
			| (clamp(((argb >> 8) & 0xFF) + offset) << 8)
			| clamp((argb & 0xFF) + offset));
	}

	public static BufferedImage flipImage(BufferedImage image, boolean horizontal, boolean vertical)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < image.getHeight(); y++)
		{
			for (int x = 0; x < image.getWidth(); x++)
			{
				int sx = horizontal ? image.getWidth() - 1 - x : x;
				int sy = vertical ? image.getHeight() - 1 - y : y;
				out.setRGB(x, y, image.getRGB(sx, sy));
			}
		}
		return out;
	}

	public static BufferedImage rotateImage(BufferedImage image, double theta)
	{
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		AffineTransform transform = AffineTransform.getRotateInstance(theta, image.getWidth() / 2.0, image.getHeight() / 2.0);
		g.drawImage(image, transform, null);
		g.dispose();
		return out;
	}
}
