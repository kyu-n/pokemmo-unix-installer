package com.pokeemu.unix.enums;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Stream;

public enum PokeMMOLocale
{
	en("English", "en", true),
	fr("Français", "fr", true),
	es("Español", "es", true),
	de("Deutsche", "de", true),
	it("Italiano", "it", true),
	pt_br("Português (Brasileiro)", "pt-BR", true),
	ko("한국어", "ko", true),
	ja("日本語", "ja", true),
	zh("中国人", "zh", true),
	zh_tw("中國人", "zh-TW", true),
	fil("Filipino", "fil", true),
	ru("Русские", "ru", false),
	pl("Polski", "pl", false);

	private final String display_name, lang_tag;
	private final boolean language_is_selectable;

	public static final PokeMMOLocale[] ENABLED_LANGUAGES;
	public static final Map<PokeMMOLocale, ResourceBundle> RESOURCES = new HashMap<>();

	static
	{
		ENABLED_LANGUAGES = Stream.of(values()).filter(PokeMMOLocale::isEnabled).toArray(PokeMMOLocale[]::new);
		for(var v : ENABLED_LANGUAGES)
		{
			RESOURCES.put(v, ResourceBundle.getBundle("MessagesBundle", Locale.forLanguageTag(v.getLangTag())));
		}
	}

	PokeMMOLocale(String display_name, String lang_tag, boolean language_is_selectable)
	{
		this.display_name = display_name;
		this.lang_tag = lang_tag;
		this.language_is_selectable = language_is_selectable;
	}

	public String getDisplayName()
	{
		return display_name;
	}

	public String getLangTag()
	{
		return lang_tag;
	}

	public boolean isEnabled()
	{
		return language_is_selectable;
	}

	public ResourceBundle getStrings()
	{
		return RESOURCES.getOrDefault(this, RESOURCES.get(PokeMMOLocale.en));
	}

	public static PokeMMOLocale getFromString(String value)
	{
		for(var lang : ENABLED_LANGUAGES)
		{
			String target_tag = lang.getLangTag();
			if(value.equalsIgnoreCase(target_tag))
			{
				return lang;
			}
		}

		return PokeMMOLocale.en;
	}

	public static PokeMMOLocale getFromLocale(Locale locale)
	{
		for(var lang : ENABLED_LANGUAGES)
		{
			if(locale.toLanguageTag().equals(lang.getLangTag()))
			{
				return lang;
			}
		}

		for(var lang : ENABLED_LANGUAGES)
		{
			if(locale.getLanguage().equals(lang.getLangTag()))
			{
				return lang;
			}
		}

		return PokeMMOLocale.en;
	}

	public static PokeMMOLocale getDefaultLocale()
	{
		return getFromLocale(Locale.getDefault());
	}

	@Override
	public String toString()
	{
		return getDisplayName();
	}
}