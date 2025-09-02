package com.pokeemu.unix.updater;

public class UpdateFile
{
	public final String name;
	public final String sha256;
	public final boolean only_if_not_exists;
	public final int size;

	public final boolean sizeValid;

	public UpdateFile(String name, String sha256, String size, boolean only_if_not_exists)
	{
		this.name = name;
		this.sha256 = sha256;
		this.only_if_not_exists = only_if_not_exists;

		int size_t = -1;
		boolean valid = false;

		if(size != null && !size.isEmpty())
		{
			try
			{
				size_t = Integer.parseInt(size);
				if(size_t > 0 && size_t <= 500 * 1024 * 1024)
				{
					valid = true;
				}
				else
				{
					System.err.println("Invalid file size for " + name + ": " + size_t);
					size_t = -1;
				}
			}
			catch(NumberFormatException e)
			{
				System.err.println("Failed to parse file size for " + name + ": " + size);
				size_t = -1;
			}
		}

		this.size = size_t;
		this.sizeValid = valid;
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

	public boolean hasSizeForProgress()
	{
		return sizeValid && size > 0;
	}
}