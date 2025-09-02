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
import com.pokeemu.unix.enums.PokeMMOLocale;
import com.pokeemu.unix.enums.UpdateChannel;

public class Config
{
	public static final short JOPTS_XMX_VAL_MIN = 384;
	public static final short JOPTS_XMX_VAL_MAX = 1536;
	public static final int NETWORK_THREADS_MAX = 4;

	public static int NETWORK_THREADS = 4;

	public static UpdateChannel UPDATE_CHANNEL = UpdateChannel.live;

	public static short HARD_MAX_MEMORY_MB = 512;

	public static PokeMMOLocale ACTIVE_LOCALE = PokeMMOLocale.getDefaultLocale();
	private static ResourceBundle STRINGS = ACTIVE_LOCALE.getStrings();

	public static boolean AES_INTRINSICS_WORKAROUND_ENABLED = true;

	private static boolean configHadErrors = false;
	private static StringBuilder configErrors = new StringBuilder();

	private Config()
	{
	}

	public static boolean hasConfigurationErrors()
	{
		return configHadErrors;
	}

	public static String getConfigurationErrors()
	{
		return configErrors.toString();
	}

	public static void load()
	{
		configHadErrors = false;
		configErrors = new StringBuilder();

		Properties props = new Properties();
		try
		{
			props.load(new FileReader(getConfigHome() + "/pokemmo-installer.properties"));
		}
		catch(IOException e)
		{
			return;
		}

		try
		{
			String networkThreadsStr = props.getProperty("network_threads", "4");
			try
			{
				NETWORK_THREADS = Integer.parseInt(networkThreadsStr);
				if(NETWORK_THREADS < 1)
				{
					NETWORK_THREADS = 1;
				}
				else if(NETWORK_THREADS > NETWORK_THREADS_MAX)
				{
					NETWORK_THREADS = NETWORK_THREADS_MAX;
				}
			}
			catch(NumberFormatException e)
			{
				String error = "Invalid network_threads value: " + networkThreadsStr + ", using default: 4";
				System.err.println(error);
				configErrors.append(error).append("\n");
				configHadErrors = true;
				NETWORK_THREADS = 4;
			}

			String maxMemStr = props.getProperty("max_mem_hard", "512");
			try
			{
				HARD_MAX_MEMORY_MB = Short.parseShort(maxMemStr);

				if(HARD_MAX_MEMORY_MB < JOPTS_XMX_VAL_MIN)
				{
					HARD_MAX_MEMORY_MB = JOPTS_XMX_VAL_MIN;
				}
				else if(HARD_MAX_MEMORY_MB > JOPTS_XMX_VAL_MAX)
				{
					HARD_MAX_MEMORY_MB = JOPTS_XMX_VAL_MAX;
				}
			}
			catch(NumberFormatException e)
			{
				String error = "Invalid max_mem_hard value: " + maxMemStr + ", using default: 512";
				System.err.println(error);
				configErrors.append(error).append("\n");
				configHadErrors = true;
				HARD_MAX_MEMORY_MB = 512;
			}

			String localeStr = props.getProperty("launcher_locale");
			if(localeStr != null)
			{
				PokeMMOLocale parsedLocale = PokeMMOLocale.getFromString(localeStr);
				if(parsedLocale != null)
				{
					ACTIVE_LOCALE = parsedLocale;
				}
				else
				{
					String error = "Invalid launcher_locale value: " + localeStr + ", using default locale";
					System.err.println(error);
					configErrors.append(error).append("\n");
					configHadErrors = true;
					ACTIVE_LOCALE = PokeMMOLocale.getDefaultLocale();
				}
			}

			String channelStr = props.getProperty("update_channel");
			if(channelStr != null)
			{
				try
				{
					UpdateChannel parsedChannel = UpdateChannel.valueOf(channelStr);
					if(parsedChannel.isSelectable())
					{
						UPDATE_CHANNEL = parsedChannel;
					}
					else
					{
						String error = "Update channel " + channelStr + " is not selectable, using default: live";
						System.err.println(error);
						configErrors.append(error).append("\n");
						configHadErrors = true;
						UPDATE_CHANNEL = UpdateChannel.live;
					}
				}
				catch(IllegalArgumentException e)
				{
					String error = "Invalid update_channel value: " + channelStr + ", using default: live";
					System.err.println(error);
					configErrors.append(error).append("\n");
					configHadErrors = true;
					UPDATE_CHANNEL = UpdateChannel.live;
				}
			}

			String aesStr = props.getProperty("networking_corruption_workaround", "true");
			try
			{
				AES_INTRINSICS_WORKAROUND_ENABLED = Boolean.parseBoolean(aesStr);
			}
			catch(Exception e)
			{
				String error = "Invalid networking_corruption_workaround value: " + aesStr + ", using default: true";
				System.err.println(error);
				configErrors.append(error).append("\n");
				configHadErrors = true;
				AES_INTRINSICS_WORKAROUND_ENABLED = true;
			}
		}
		catch(Exception e)
		{
			String error = "Unexpected error loading configuration file: " + e.getMessage();
			System.err.println(error);
			configErrors.append(error).append("\n");
			configHadErrors = true;
			e.printStackTrace();
		}

		STRINGS = ACTIVE_LOCALE.getStrings();
	}

	public static void save()
	{
		Properties props = new Properties();
		props.put("network_threads", Integer.toString(NETWORK_THREADS));
		props.put("update_channel", UPDATE_CHANNEL.toString());
		props.put("max_mem_hard", Short.toString(HARD_MAX_MEMORY_MB));
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
}