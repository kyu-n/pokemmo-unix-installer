package com.pokeemu.unix.ui;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Kyu
 */
public class LocaleAwareElementManager
{
	public static LocaleAwareElementManager instance;
	static
	{
		instance = new LocaleAwareElementManager();
	}
	
	private final Set<LocaleAwareInterface> active_elements = new HashSet<>();
	
	private LocaleAwareElementManager() { }
	
	public void addElement(LocaleAwareInterface element)
	{
		active_elements.add(element);
	}
	
	public void updateElements()
	{
		active_elements.forEach(LocaleAwareInterface::updateLocale);
	}
}
