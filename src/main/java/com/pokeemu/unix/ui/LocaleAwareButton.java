package com.pokeemu.unix.ui;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import com.pokeemu.unix.config.Config;

/**
 * SWT Button that automatically updates when locale changes
 *
 * @author Kyu
 */
public class LocaleAwareButton extends Button implements LocaleAwareInterface
{
	private String textKey;

	public LocaleAwareButton(Composite parent, int style)
	{
		super(parent, style);
		LocaleAwareElementManager.instance.addElement(this);
	}

	public void setTextKey(String key)
	{
		this.textKey = key;
		updateLocale();
	}

	@Override
	public void updateLocale()
	{
		if(textKey != null)
		{
			setText(Config.getString(textKey));
		}
	}

	@Override
	protected void checkSubclass()
	{
	}
}