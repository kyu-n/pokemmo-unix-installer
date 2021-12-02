package com.pokeemu.unix.ui;

/**
 * @author Kyu
 */
public interface LocaleAwareInterface
{
	void setTextKey(String key, Object... params);
	void setToolTipKey(String tooltip, Object... params);
	void updateLocale();
}
