package com.pokeemu.unix.ui;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;

import com.pokeemu.unix.config.Config;

/**
 * SWT Group that automatically updates when locale changes
 *
 * @author Kyu
 */
public class LocaleAwareGroup extends Group implements LocaleAwareInterface
{
	private String textKey;

	public LocaleAwareGroup(Composite parent, int style)
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