package com.pokeemu.unix.ui;

import com.pokeemu.unix.config.Config;

import javax.swing.*;

/**
 * @author Kyu
 */
public class LocaleAwareCheckbox extends JCheckBox implements LocaleAwareInterface
{
	private String tooltip;

	public LocaleAwareCheckbox()
	{
		this.tooltip = "";

		LocaleAwareElementManager.instance.addElement(this);
	}

	@Override
	public void setTextKey(String key, Object... params)
	{
		throw new UnsupportedOperationException();
	}

	@Override
	public void setToolTipKey(String tooltip, Object... params)
	{
		this.tooltip = tooltip;
		super.setToolTipText(Config.getString(tooltip, params));
	}

	@Override
	public void setToolTipText(String text)
	{
		throw new UnsupportedOperationException("Must use locale-aware method to set tooltips");
	}


	@Override
	public void updateLocale()
	{
		super.setToolTipText(Config.getString(tooltip));
	}
}
