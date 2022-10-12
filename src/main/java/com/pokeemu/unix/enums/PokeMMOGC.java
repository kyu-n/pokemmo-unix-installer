package com.pokeemu.unix.enums;

/**
 * @author Kyu
 */
public enum PokeMMOGC
{
	G1("-XX:+UseG1GC", true),
	ZGC("-XX:+UseZGC", true),
	Shenandoah("-XX:+UseShenandoahGC", false);

	public final String launch_arg;
	public final boolean is_enabled;

	PokeMMOGC(String launch_arg, boolean is_enabled)
	{
		this.launch_arg = launch_arg;
		this.is_enabled = is_enabled;
	}

	public boolean isEnabled()
	{
		return is_enabled;
	}

	public static PokeMMOGC getDefault()
	{
		return ZGC;
	}
}	
