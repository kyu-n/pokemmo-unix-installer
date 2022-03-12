package com.pokeemu.unix.ui;

import com.pokeemu.unix.config.Config;

import javax.swing.*;

/**
 * @author Kyu
 */
public class LocaleAwareLabel extends JLabel implements LocaleAwareInterface
{
	private String key;

	public LocaleAwareLabel(String key)
	{
		this.key = key;
		super.setText(Config.getString(key));

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
		super.setText(Config.getString(key));
	}
}
