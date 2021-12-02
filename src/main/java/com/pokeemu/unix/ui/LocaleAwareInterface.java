package com.pokeemu.unix.ui;

/**
 * @author Kyu
 */
public interface LocaleAwareInterface
{
	public abstract void setTextKey(String key, Object... params);
	public abstract void setToolTipKey(String tooltip, Object... params);
	public abstract void updateLocale();
}
