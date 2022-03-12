package com.pokeemu.unix.config;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.Properties;
import java.util.ResourceBundle;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.enums.PokeMMOGC;
import com.pokeemu.unix.enums.PokeMMOLocale;
import com.pokeemu.unix.enums.UpdateChannel;
import com.pokeemu.unix.ui.LocaleAwareElementManager;

/**
 * @author Kyu
 */
public class Config
{
	public static final short JOPTS_XMX_VAL_MIN = 384;
	public static final short JOPTS_XMX_VAL_MAX = 1024;
	public static final int NETWORK_THREADS_MAX = 4;

	public static int NETWORK_THREADS = 4;
	public static boolean AUTO_START = false;

	public static UpdateChannel UPDATE_CHANNEL = UpdateChannel.live;

	public static short HARD_MAX_MEMORY_MB = JOPTS_XMX_VAL_MIN;

	public static PokeMMOGC ACTIVE_GC = PokeMMOGC.getDefault();
	public static PokeMMOLocale ACTIVE_LOCALE = PokeMMOLocale.getDefaultLocale();
	private static ResourceBundle STRINGS = ACTIVE_LOCALE.getStrings();

	public static boolean AES_INTRINSICS_WORKAROUND_ENABLED = true;

	private Config()
	{
	}

	public static void load()
	{
		Properties props = new Properties();
		try
		{
			props.load(new FileReader(getConfigHome() + "/pokemmo-installer.properties"));
		}
		catch(IOException e)
		{
			return; // Use default properties
		}

		try
		{
			NETWORK_THREADS = Integer.parseInt(props.getProperty("network_threads", "4"));
			if(NETWORK_THREADS < 1)
			{
				NETWORK_THREADS = 1;
			}
			else if(NETWORK_THREADS > NETWORK_THREADS_MAX)
			{
				NETWORK_THREADS = NETWORK_THREADS_MAX;
			}

			AUTO_START = Boolean.parseBoolean(props.getProperty("auto_start", "false"));
			// UPDATE_CHANNEL = UpdateChannel.valueOf(props.getProperty("update_channel"));

			HARD_MAX_MEMORY_MB = Short.parseShort(props.getProperty("max_mem_hard", "512"));

			if(HARD_MAX_MEMORY_MB < JOPTS_XMX_VAL_MIN)
			{
				HARD_MAX_MEMORY_MB = JOPTS_XMX_VAL_MIN;
			}
			else if(HARD_MAX_MEMORY_MB > JOPTS_XMX_VAL_MAX)
			{
				HARD_MAX_MEMORY_MB = JOPTS_XMX_VAL_MAX;
			}

			ACTIVE_GC = PokeMMOGC.valueOf(props.getProperty("active_gc", "Shenandoah"));
			ACTIVE_LOCALE = PokeMMOLocale.getFromString(props.getProperty("launcher_locale"));

			UPDATE_CHANNEL = UpdateChannel.valueOf(props.getProperty("update_channel"));

			AES_INTRINSICS_WORKAROUND_ENABLED = Boolean.parseBoolean(props.getProperty("networking_corruption_workaround", "true"));
		}
		catch(Exception e)
		{
			System.out.println("Failed to load configuration file");
		}

		if(!ACTIVE_GC.is_enabled)
		{
			ACTIVE_GC = PokeMMOGC.getDefault();
		}

		STRINGS = ACTIVE_LOCALE.getStrings();
	}

	public static void save()
	{
		Properties props = new Properties();
		props.put("network_threads", Integer.toString(NETWORK_THREADS));
		props.put("auto_start", Boolean.toString(AUTO_START));
		props.put("update_channel", UPDATE_CHANNEL.toString());
		props.put("max_mem_hard", Short.toString(HARD_MAX_MEMORY_MB));
		props.put("active_gc", ACTIVE_GC.toString());
		props.put("launcher_locale", ACTIVE_LOCALE.getLangTag());
		props.put("networking_corruption_workaround", Boolean.toString(AES_INTRINSICS_WORKAROUND_ENABLED));

		File config_dir = new File(getConfigHome());
		if(config_dir.exists() || config_dir.mkdir())
		{
			File config_file = new File(getConfigHome() + "/pokemmo-installer.properties");
			try
			{
				props.store(new FileWriter(config_file, StandardCharsets.UTF_8), "PokeMMO Unix Installer v" + UnixInstaller.INSTALLER_VERSION + " Properties");
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
		else
		{
			System.out.println("Failed to save configuration for config_dir " + config_dir);
		}
	}

	public static void changeLocale(PokeMMOLocale target)
	{
		ACTIVE_LOCALE = target;
		STRINGS = target.getStrings();
		save();

		LocaleAwareElementManager.instance.updateElements();
	}

	private static String getConfigHome()
	{
		String config_home = System.getenv("SNAP_USER_COMMON");
		if(config_home == null)
		{
			config_home = Objects.requireNonNullElse(System.getenv("XDG_CONFIG_HOME"), System.getProperty("user.home") + "/.config");
		}

		return config_home;
	}

	public static String getString(String key)
	{
		try
		{
			return STRINGS.getString(key);
		}
		catch(MissingResourceException | NullPointerException e)
		{
			return "[" + key + "]";
		}
	}

	public static String getString(String key, Object... params)
	{
		try
		{
			return MessageFormat.format(STRINGS.getString(key), params);
		}
		catch(MissingResourceException | NullPointerException e)
		{
			return "[" + key + "]";
		}
	}

	public static boolean hasString(String key)
	{
		return STRINGS.containsKey(key);
	}
}