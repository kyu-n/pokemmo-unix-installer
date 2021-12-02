package com.pokeemu.unix.enums;

import java.util.stream.Stream;

/**
 * @author Kyu
 */
public enum UpdateChannel
{
	live(true),
	pts(false); // PTS client support is not yet enabled

	public static final UpdateChannel[] ENABLED_UPDATE_CHANNELS;
	static
	{
		ENABLED_UPDATE_CHANNELS = Stream.of(values()).filter(UpdateChannel::isSelectable).toArray(UpdateChannel[]::new);
	}
	
	private final boolean selectable;

	UpdateChannel(boolean selectable)
	{
		this.selectable = selectable;
	}
	
	public boolean isSelectable()
	{
		return selectable;
	}
}
