package com.pokeemu.unix.ui;

import java.awt.*;

import com.pokeemu.unix.config.Config;

/**
 * @author Kyu
 */
public class LocaleAwareLabel extends Label implements LocaleAwareInterface
{
	private String key;

	public LocaleAwareLabel(String key)
	{
		this(key, Label.LEFT);
	}

	public LocaleAwareLabel(String key, int alignment)
	{
		this.key = key;
		super.setText(Config.getString(key));
		setAlignment(Label.LEFT);

		LocaleAwareElementManager.instance.addElement(this);
	}

	public void updateLocale()
	{
		super.setText(Config.getString(key));
	}

	@Override
	public void setTextKey(String key, Object... params)
	{
		this.key = key;
		super.setText(Config.getString(key));
	}

	@Override
	public void setToolTipKey(String tooltip, Object... params)
	{
		// Empty
	}

	@Override
	public void setText(String text)
	{
		throw new UnsupportedOperationException("Must use locale-aware text constructor");
	}
}
