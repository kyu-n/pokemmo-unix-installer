package com.pokeemu.unix.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

/**
 * Limited-scope port of GNOME theme detection sourced from https://github.com/Dansoftowner/jSystemThemeDetector
 * Used for detecting the dark theme on a GNOME/GTK system
 *
 * @author Daniel Gyorffy (DansoftOwner), Kyu
 */
public class GnomeThemeDetector
{
	private static final String[] GET_CMD = new String[]{
			"gsettings get org.gnome.desktop.interface gtk-theme",
			"gsettings get org.gnome.desktop.interface color-scheme"
	};

	private static final Pattern darkThemeNamePattern = Pattern.compile(".*dark.*", Pattern.CASE_INSENSITIVE);

	public static boolean isDark()
	{
		try
		{
			Runtime runtime = Runtime.getRuntime();
			for(String cmd : GET_CMD)
			{
				Process process = runtime.exec(cmd);
				try(BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream())))
				{
					String readLine = reader.readLine();
					if(readLine != null && darkThemeNamePattern.matcher(readLine).matches())
					{
						return true;
					}
				}
			}
		}
		catch(IOException e)
		{
			System.out.println("Couldn't detect GNOME theme");
			e.printStackTrace();
		}
		return false;
	}
}