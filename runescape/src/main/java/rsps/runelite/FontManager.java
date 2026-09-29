/*
 * Copyright (c) 2017, Tyler <https://github.com/tylerthardy>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package rsps.runelite;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;

/**
 * RuneLite's fonts (net.runelite.client.ui.FontManager), BSD-2-Clause: the RuneScape, RuneScape Small and RuneScape Bold
 * TrueType fonts shipped with RuneLite (resources /runelite/*.ttf), all at size 16 like RuneLite derives them.
 */
public final class FontManager
{
	private static final Font runescapeFont;
	private static final Font runescapeSmallFont;
	private static final Font runescapeBoldFont;

	static
	{
		runescapeFont = load("/runelite/runescape.ttf", Font.PLAIN);
		runescapeSmallFont = load("/runelite/runescape_small.ttf", Font.PLAIN);
		runescapeBoldFont = load("/runelite/runescape_bold.ttf", Font.BOLD);
	}

	private static Font load(String resource, int style)
	{
		try (InputStream in = FontManager.class.getResourceAsStream(resource))
		{
			Font font = Font.createFont(Font.TRUETYPE_FONT, in);
			try
			{
				GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
			}
			catch (RuntimeException headless)
			{
				// registering is only for other components asking for the family by name
			}
			return font.deriveFont(style, 16f);
		}
		catch (Exception missing)
		{
			return new Font(Font.DIALOG, style, 12);
		}
	}

	public static Font getRunescapeFont()
	{
		return runescapeFont;
	}

	public static Font getRunescapeSmallFont()
	{
		return runescapeSmallFont;
	}

	public static Font getRunescapeBoldFont()
	{
		return runescapeBoldFont;
	}

	private FontManager()
	{
	}
}
