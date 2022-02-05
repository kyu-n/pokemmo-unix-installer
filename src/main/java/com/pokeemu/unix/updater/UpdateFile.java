package com.pokeemu.unix.updater;

/**
 * @author Desu
 */
public class UpdateFile
{
	/**
	 * Filename (Including Path)
	 */
	public final String name;
	public final String sha256;
	public final boolean only_if_not_exists;
	public final int size;

	public UpdateFile(String name, String sha256, String size, boolean only_if_not_exists)
	{
		this.name = name;
		this.sha256 = sha256;

		int size_t;
		try
		{
			size_t = Integer.parseInt(size);
		}
		catch(Exception e)
		{
			size_t = 0;
		}

		this.size = size_t;

		this.only_if_not_exists = only_if_not_exists;
	}

	public boolean shouldDownload()
	{
		return true;
	}

	public String getCacheBuster()
	{
		if(sha256 != null && !sha256.isEmpty())
		{
			return sha256.substring(0, Math.min(8, sha256.length()));
		}
		return "";
	}
}
