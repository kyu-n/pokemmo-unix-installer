package com.pokeemu.unix.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import com.pokeemu.unix.LauncherUtils;

/**
 * Manages display server detection and configuration for the game client.
 * Ensures the game launches with the correct compositor settings.
 */
public class DisplayServerManager
{
	public enum DisplayServer
	{
		WAYLAND("Wayland"),
		X11("X11"),
		UNKNOWN("Unknown");

		private final String value;

		DisplayServer(String value)
		{
			this.value = value;
		}

		public String getValue()
		{
			return value;
		}
	}

	/**
	 * Detect the current display server based on environment variables
	 */
	public static DisplayServer detectDisplayServer()
	{
		// Primary detection methods
		String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
		String xdgSessionType = System.getenv("XDG_SESSION_TYPE");
		String display = System.getenv("DISPLAY");

		// Flatpak-specific checks
		boolean inFlatpak = System.getenv("FLATPAK_ID") != null;

		// Check for Wayland
		if("wayland".equalsIgnoreCase(xdgSessionType))
		{
			return DisplayServer.WAYLAND;
		}

		if(waylandDisplay != null && !waylandDisplay.isEmpty())
		{
			return DisplayServer.WAYLAND;
		}

		// Check for X11
		if("x11".equalsIgnoreCase(xdgSessionType))
		{
			return DisplayServer.X11;
		}

		if(display != null && !display.isEmpty())
		{
			// In Flatpak, DISPLAY might be set even in Wayland sessions
			// Double-check if we're really in X11
			if(inFlatpak)
			{
				// If we have both DISPLAY and WAYLAND_DISPLAY, prefer Wayland
				if(waylandDisplay != null)
				{
					return DisplayServer.WAYLAND;
				}

				// Check if XDG_SESSION_TYPE is explicitly set
				if(xdgSessionType != null)
				{
					return "wayland".equalsIgnoreCase(xdgSessionType) ?
							DisplayServer.WAYLAND : DisplayServer.X11;
				}
			}

			return DisplayServer.X11;
		}

		// Unable to determine
		return DisplayServer.UNKNOWN;
	}

	/**
	 * Fix compositor settings in main.properties before game launch
	 */
	public static void fixCompositorSettings() throws IOException
	{
		File mainPropsFile = LauncherUtils.getFile("config/main.properties");

		// Create config directory if it doesn't exist
		File configDir = mainPropsFile.getParentFile();
		if(!configDir.exists())
		{
			configDir.mkdirs();
		}

		// Load existing properties or create new
		Properties props = new Properties();
		if(mainPropsFile.exists())
		{
			try(FileInputStream fis = new FileInputStream(mainPropsFile))
			{
				props.load(fis);
			}
		}

		DisplayServer detected = detectDisplayServer();


		// Determine what to set
		boolean needsUpdate = false;
		String newCompositor = null;

		// SPECIAL FLATPAK OVERRIDE: Always prefer Wayland when available
		if(System.getenv("FLATPAK_ID") != null)
		{
			String currentCompositor = props.getProperty("client.graphics.linux.preferred_compositor");

			System.out.println("Running in Flatpak container - checking for Wayland availability");

			// Check if Wayland is actually available
			String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
			if(waylandDisplay != null && !waylandDisplay.isEmpty())
			{
				// Force Wayland regardless of user settings
				if(!"Wayland".equals(currentCompositor))
				{
					newCompositor = "Wayland";
					needsUpdate = true;
					System.out.println("FLATPAK: Forcing Wayland compositor (was: " + currentCompositor + ")");
				}
				else
				{
					System.out.println("FLATPAK: Wayland already set");
				}
			}
			else
			{
				newCompositor = "X11";
				needsUpdate = true;
				System.out.println("FLATPAK: No Wayland available, forcing X11");
			}
		}

		// Update properties if needed
		if(needsUpdate)
		{
			props.setProperty("client.graphics.linux.preferred_compositor", newCompositor);
			props.setProperty("client.graphics.linux.preferred_compositor.has_been_selected", "true");

			// Save the file
			try(FileOutputStream fos = new FileOutputStream(mainPropsFile))
			{
				String comment = "Updated by PokeMMO Unix Launcher - Flatpak Wayland Override";
				props.store(fos, comment);
			}

			System.out.println("Updated main.properties with compositor: " + newCompositor);
		}
	}

	/**
	 * Log detailed display server information for debugging
	 */
	public static void logDisplayServerInfo()
	{
		System.out.println("=================================================");
		System.out.println("[DEBUG] Display Server Detection:");

		// Environment variables
		System.out.println("WAYLAND_DISPLAY: " + System.getenv("WAYLAND_DISPLAY"));
		System.out.println("XDG_SESSION_TYPE: " + System.getenv("XDG_SESSION_TYPE"));
		System.out.println("DISPLAY: " + System.getenv("DISPLAY"));
		System.out.println("XDG_CURRENT_DESKTOP: " + System.getenv("XDG_CURRENT_DESKTOP"));

		// Container detection
		String flatpakId = System.getenv("FLATPAK_ID");
		if(flatpakId != null)
		{
			System.out.println("Running in Flatpak: " + flatpakId);
		}

		String snapName = System.getenv("SNAP_NAME");
		if(snapName != null)
		{
			System.out.println("Running in Snap: " + snapName);
		}

		// Final detection result
		DisplayServer detected = detectDisplayServer();
		System.out.println("Detected Display Server: " + detected.getValue());
		System.out.println("=================================================");
	}
}