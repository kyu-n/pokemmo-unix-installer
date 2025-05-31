package com.pokeemu.unix.ui;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;

import com.pokeemu.unix.config.Config;

/**
 * SWT Text that automatically updates when locale changes
 *
 * @author Kyu
 */
public class LocaleAwareText extends Text
{
	public LocaleAwareText(Composite parent, int style)
	{
		super(parent, style);
	}

	public void appendLocaleStr(String key)
	{
		append(Config.getString(key));
	}

	@Override
	protected void checkSubclass()
	{
	}
}