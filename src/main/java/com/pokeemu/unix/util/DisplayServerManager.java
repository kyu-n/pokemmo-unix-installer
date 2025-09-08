package com.pokeemu.unix.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

import com.pokeemu.unix.LauncherUtils;

import org.lwjgl.glfw.GLFW;

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

	private static boolean isCrostini()
	{
		return System.getenv().keySet().stream()
				.anyMatch(key -> key.startsWith("SOMMELIER_"));
	}

	public static DisplayServer detectDisplayServer()
	{
		if(isCrostini())
		{
			return DisplayServer.X11;
		}

		if(isWaylandAvailable())
		{
			return DisplayServer.WAYLAND;
		}

		if(isX11Available())
		{
			return DisplayServer.X11;
		}

		return DisplayServer.UNKNOWN;
	}

	public static boolean isWaylandAvailable()
	{
		String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
		return GLFW.glfwPlatformSupported(GLFW.GLFW_PLATFORM_WAYLAND) && waylandDisplay != null && !waylandDisplay.isEmpty();
	}

	public static boolean isX11Available()
	{
		String x11Display = System.getenv("DISPLAY");
		return GLFW.glfwPlatformSupported(GLFW.GLFW_PLATFORM_X11) && x11Display != null && !x11Display.isEmpty();
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