package com.pokeemu.unix.ui;

public class LocaleAwareStringBundle
{
	private final String key;
	private final Object[] params;
	
	public LocaleAwareStringBundle(String key, Object... params)
	{
		this.key = key;
		this.params = params;
	}
	
	public String getKey()
	{
		return key;
	}
	
	public Object[] getParams()
	{
		return params;
	}
}
