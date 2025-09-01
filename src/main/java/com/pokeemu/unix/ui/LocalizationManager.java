package com.pokeemu.unix.ui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.ibm.icu.text.UnicodeSet;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.enums.PokeMMOLocale;

import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;

public class LocalizationManager
{
	private static final LocalizationManager INSTANCE = new LocalizationManager();
	public static final LocalizationManager instance = INSTANCE;

	private volatile PokeMMOLocale currentLocale;
	private final Map<String, String> stringCache = new HashMap<>();

	private static final float DEFAULT_FONT_SIZE = 16.0f;
	private static final String MAIN_FONT_RESOURCE = "/fonts/NotoSansCJK-Medium.ttc";

	private short[] cachedGlyphRanges = null;

	private LocalizationManager()
	{
		updateLocale();
	}

	public void initializeFonts()
	{
		ImGuiIO io = ImGui.getIO();
		ImFontAtlas fontAtlas = io.getFonts();

		fontAtlas.clear();

		ImFontConfig fontConfig = new ImFontConfig();

		fontConfig.setSizePixels(DEFAULT_FONT_SIZE);
		fontConfig.setOversampleH(3);
		fontConfig.setOversampleV(1);
		fontConfig.setPixelSnapH(true);

		short[] glyphRanges = getOptimizedGlyphRanges();

		ImFont mainFont = loadFontFromResource(fontAtlas, fontConfig, glyphRanges);

		if(mainFont == null)
		{
			System.err.println("Failed to load any fonts from resources, using ImGui default");
			mainFont = fontAtlas.addFontDefault();
		}

		fontAtlas.build();
		io.setFontDefault(mainFont);
	}

	private short[] getOptimizedGlyphRanges()
	{
		if(cachedGlyphRanges != null)
		{
			return cachedGlyphRanges;
		}

		UnicodeSet allChars = new UnicodeSet();

		allChars.add(0x0020, 0x007E);
		allChars.add(0x00A0, 0x00FF);
		allChars.add(0x2000, 0x206F);
		allChars.add(0x20A0, 0x20CF);

		for(PokeMMOLocale locale : PokeMMOLocale.ENABLED_LANGUAGES)
		{
			try
			{
				ResourceBundle bundle = locale.getStrings();
				if(bundle == null) continue;

				Enumeration<String> keys = bundle.getKeys();
				while(keys.hasMoreElements())
				{
					String key = keys.nextElement();
					try
					{
						String value = bundle.getString(key);
						for(int i = 0; i < value.length(); i++)
						{
							allChars.add(value.charAt(i));
						}
					}
					catch(MissingResourceException ignored)
					{
					}
				}

				String displayName = locale.getDisplayName();
				if(displayName != null)
				{
					for(int i = 0; i < displayName.length(); i++)
					{
						allChars.add(displayName.charAt(i));
					}
				}

			}
			catch(Exception e)
			{
				System.err.println("Error scanning locale " + locale + ": " + e.getMessage());
			}
		}

		cachedGlyphRanges = convertUnicodeSetToRanges(allChars);
		System.out.println("Generated font ranges covering " + allChars.size() + " unique characters");

		return cachedGlyphRanges;
	}

	private short[] convertUnicodeSetToRanges(UnicodeSet unicodeSet)
	{
		List<Short> ranges = new ArrayList<>();

		int rangeCount = unicodeSet.getRangeCount();
		for(int i = 0; i < rangeCount; i++)
		{
			int start = unicodeSet.getRangeStart(i);
			int end = unicodeSet.getRangeEnd(i);

			if(start > 0xFFFF) break;
			if(end > 0xFFFF) end = 0xFFFF;

			ranges.add((short) start);
			ranges.add((short) end);
		}

		ranges.add((short) 0);

		short[] result = new short[ranges.size()];
		for(int i = 0; i < ranges.size(); i++)
		{
			result[i] = ranges.get(i);
		}

		return result;
	}

	private ImFont loadFontFromResource(ImFontAtlas fontAtlas, ImFontConfig config, short[] glyphRanges)
	{
		InputStream is = null;
		ByteArrayOutputStream buffer = null;

		try
		{
			is = getClass().getResourceAsStream(MAIN_FONT_RESOURCE);
			if(is == null)
			{
				System.err.println("Font resource not found: " + MAIN_FONT_RESOURCE);
				return null;
			}

			buffer = new ByteArrayOutputStream();
			byte[] data = new byte[16384];
			int nRead;

			while((nRead = is.read(data, 0, data.length)) != -1)
			{
				buffer.write(data, 0, nRead);
			}

			buffer.flush();
			byte[] fontData = buffer.toByteArray();

			if(fontData.length == 0)
			{
				System.err.println("Failed to read font data from: " + MAIN_FONT_RESOURCE);
				return null;
			}

			return fontAtlas.addFontFromMemoryTTF(fontData, DEFAULT_FONT_SIZE, config, glyphRanges);

		}
		catch(Exception e)
		{
			System.err.println("Error loading font from resource: " + MAIN_FONT_RESOURCE);
			e.printStackTrace();
			return null;
		}
		finally
		{
			if(buffer != null)
			{
				try
				{
					buffer.close();
				}
				catch(IOException ignored)
				{
				}
			}
			if(is != null)
			{
				try
				{
					is.close();
				}
				catch(IOException ignored)
				{
				}
			}
		}
	}

	public void updateLocale()
	{
		currentLocale = Config.ACTIVE_LOCALE;
		stringCache.clear();
	}

	public String getString(String key)
	{
		if(key == null)
		{
			return "";
		}

		String cached = stringCache.get(key);
		if(cached != null)
		{
			return cached;
		}

		try
		{
			ResourceBundle bundle = currentLocale.getStrings();
			String value = bundle.getString(key);
			stringCache.put(key, value);
			return value;
		}
		catch(MissingResourceException | NullPointerException e)
		{
			return "[" + key + "]";
		}
	}

	public String getString(String key, Object... params)
	{
		if(key == null)
		{
			return "";
		}

		String template = getString(key);

		if(params == null || params.length == 0)
		{
			return template;
		}

		try
		{
			Locale locale = Locale.forLanguageTag(currentLocale.getLangTag());
			return String.format(locale, template, params);
		}
		catch(IllegalFormatException | NullPointerException e)
		{
			return template;
		}
	}
}