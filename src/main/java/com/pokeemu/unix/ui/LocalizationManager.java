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
import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.icu.text.UnicodeSet;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.enums.PokeMMOLocale;

import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import imgui.ImGui;
import imgui.ImGuiIO;

/**
 * @author Kyu
 */
public class LocalizationManager
{
	private static final LocalizationManager INSTANCE = new LocalizationManager();
	public static final LocalizationManager instance = INSTANCE;

	private volatile PokeMMOLocale currentLocale;
	private ImFont mainFont;

	// Track font push/pop state for safety
	private final AtomicInteger fontPushCount = new AtomicInteger(0);

	private final Map<String, String> stringCache = new HashMap<>();

	private static final float DEFAULT_FONT_SIZE = 16.0f;
	private static final String MAIN_FONT_RESOURCE = "/fonts/NotoSansCJK-Medium.ttc";

	// Cache the computed ranges so we only calculate once
	private short[] cachedGlyphRanges = null;

	private LocalizationManager()
	{
		updateLocale();
	}

	public static LocalizationManager getInstance()
	{
		return INSTANCE;
	}

	public void initializeFonts()
	{
		ImGuiIO io = ImGui.getIO();
		ImFontAtlas fontAtlas = io.getFonts();

		fontAtlas.clear();

		ImFontConfig fontConfig = new ImFontConfig();
		try
		{
			fontConfig.setSizePixels(DEFAULT_FONT_SIZE);
			fontConfig.setOversampleH(3);
			fontConfig.setOversampleV(1);
			fontConfig.setPixelSnapH(true);

			// Generate optimized glyph ranges from actual resource content
			short[] glyphRanges = getOptimizedGlyphRanges();

			mainFont = loadFontFromResource(fontAtlas, fontConfig, glyphRanges);

			if(mainFont == null)
			{
				System.err.println("Failed to load any fonts from resources, using ImGui default");
				mainFont = fontAtlas.addFontDefault();
			}

			fontAtlas.build();

			if(mainFont != null)
			{
				io.setFontDefault(mainFont);
			}
		}
		finally
		{
			// Ensure font config is destroyed even if exception occurs
			fontConfig.destroy();
		}
	}

	/**
	 * Generate optimized glyph ranges based on actual characters used in all resource bundles
	 */
	private short[] getOptimizedGlyphRanges()
	{
		// Return cached ranges if already computed
		if(cachedGlyphRanges != null)
		{
			return cachedGlyphRanges;
		}

		// Build a UnicodeSet containing all characters from all resource bundles
		UnicodeSet allChars = new UnicodeSet();

		// Always include basic ASCII and common punctuation
		allChars.add(0x0020, 0x007E); // Basic ASCII printable

		// Common symbols and punctuation that might appear in user input
		allChars.add(0x00A0, 0x00FF); // Latin-1 Supplement
		allChars.add(0x2000, 0x206F); // General Punctuation
		allChars.add(0x20A0, 0x20CF); // Currency Symbols

		// Scan all enabled locales for characters
		for(PokeMMOLocale locale : PokeMMOLocale.ENABLED_LANGUAGES)
		{
			try
			{
				ResourceBundle bundle = locale.getStrings();
				if(bundle == null) continue;

				// Iterate through all keys and values
				Enumeration<String> keys = bundle.getKeys();
				while(keys.hasMoreElements())
				{
					String key = keys.nextElement();
					try
					{
						String value = bundle.getString(key);
						// Add all characters from the value
						for(int i = 0; i < value.length(); i++)
						{
							allChars.add(value.charAt(i));
						}
					}
					catch(MissingResourceException e)
					{
						// Skip missing keys
					}
				}

				// Also add characters from the locale's display name
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

		// Convert UnicodeSet to optimized ranges for ImGui
		cachedGlyphRanges = convertUnicodeSetToRanges(allChars);

		System.out.println("Generated font ranges covering " + allChars.size() + " unique characters");

		return cachedGlyphRanges;
	}

	/**
	 * Convert ICU4J UnicodeSet to ImGui glyph ranges format
	 */
	private short[] convertUnicodeSetToRanges(UnicodeSet unicodeSet)
	{
		List<Short> ranges = new ArrayList<>();

		// Get the ranges from UnicodeSet
		int rangeCount = unicodeSet.getRangeCount();
		for(int i = 0; i < rangeCount; i++)
		{
			int start = unicodeSet.getRangeStart(i);
			int end = unicodeSet.getRangeEnd(i);

			// ImGui uses unsigned short, so cap at 0xFFFF
			if(start > 0xFFFF) break;
			if(end > 0xFFFF) end = 0xFFFF;

			ranges.add((short) start);
			ranges.add((short) end);
		}

		// Add terminator
		ranges.add((short) 0);

		// Convert to array
		short[] result = new short[ranges.size()];
		for(int i = 0; i < ranges.size(); i++)
		{
			result[i] = ranges.get(i);
		}

		return result;
	}

	private ImFont loadFontFromResource(ImFontAtlas fontAtlas, ImFontConfig config, short[] glyphRanges)
	{
		try(InputStream is = getClass().getResourceAsStream(LocalizationManager.MAIN_FONT_RESOURCE))
		{
			if(is == null)
			{
				System.err.println("Font resource not found: " + LocalizationManager.MAIN_FONT_RESOURCE);
				return null;
			}

			byte[] fontData = readResourceToByteArray(is);

			if(fontData.length == 0)
			{
				System.err.println("Failed to read font data from: " + LocalizationManager.MAIN_FONT_RESOURCE);
				return null;
			}

			return fontAtlas.addFontFromMemoryTTF(fontData, LocalizationManager.DEFAULT_FONT_SIZE, config, glyphRanges);

		}
		catch(Exception e)
		{
			System.err.println("Error loading font from resource: " + LocalizationManager.MAIN_FONT_RESOURCE);
			e.printStackTrace();
			return null;
		}
	}

	private byte[] readResourceToByteArray(InputStream is) throws IOException
	{
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		byte[] data = new byte[16384];
		int nRead;

		while((nRead = is.read(data, 0, data.length)) != -1)
		{
			buffer.write(data, 0, nRead);
		}

		buffer.flush();
		byte[] result = buffer.toByteArray();
		buffer.close();
		return result;
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

	public void pushLocaleFont()
	{
		if(mainFont != null)
		{
			try
			{
				ImGui.pushFont(mainFont);
				fontPushCount.incrementAndGet();
			}
			catch(Exception e)
			{
				System.err.println("Failed to push font: " + e.getMessage());
			}
		}
	}

	public void popFont()
	{
		if(mainFont != null && fontPushCount.get() > 0)
		{
			try
			{
				ImGui.popFont();
				fontPushCount.decrementAndGet();
			}
			catch(Exception e)
			{
				// Ignore if no font was pushed - this is expected
				// Reset counter if we hit an error
				fontPushCount.set(0);
			}
		}
	}
}