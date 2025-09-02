package com.pokeemu.unix;

import java.io.File;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.updater.FeedManager;
import com.pokeemu.unix.updater.UpdateFile;
import com.pokeemu.unix.util.Util;

public class LauncherUtils
{
	public static final HttpClient httpClient = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.connectTimeout(Duration.ofSeconds(20))
			.build();

	public static final String snapcraft = System.getenv("POKEMMO_IS_SNAPPED");
	public static final String flatpak = System.getenv("POKEMMO_IS_FLATPAKED");
	public static final String httpClientUserAgent;

	private static String pokemmoDir;
	private static String jrePath;

	static
	{
		if(snapcraft != null)
		{
			httpClientUserAgent = "Mozilla/5.0 (PokeMMO; UnixInstaller v" + UnixInstaller.INSTALLER_VERSION + ") (Snapcraft)";
		}
		else if(flatpak != null)
		{
			httpClientUserAgent = "Mozilla/5.0 (PokeMMO; UnixInstaller v" + UnixInstaller.INSTALLER_VERSION + ") (Flatpak)";
		}
		else
		{
			httpClientUserAgent = "Mozilla/5.0 (PokeMMO; UnixInstaller v" + UnixInstaller.INSTALLER_VERSION + ")";
		}
	}

	/**
	 * Setup directories for PokeMMO installation
	 * Must be called before using getPokemmoDir() or getJrePath()
	 */
	public static void setupDirectories()
	{
		String fileSeparator = File.separator;
		String userHome = System.getProperty("user.home");
		String pokemmoDataHome = System.getenv("SNAP_USER_COMMON");

		if(pokemmoDataHome == null)
		{
			pokemmoDataHome = Objects.requireNonNullElse(
					System.getenv("XDG_DATA_HOME"),
					userHome + fileSeparator + ".local" + fileSeparator + "share"
			);
		}

		pokemmoDir = pokemmoDataHome + fileSeparator + "pokemmo-client-" + Config.UPDATE_CHANNEL.name() + fileSeparator;
		jrePath = System.getProperty("java.home") + fileSeparator + "bin" + fileSeparator + "java";
	}

	public static String getPokemmoDir()
	{
		return pokemmoDir;
	}

	public static File getFile(String relativePath)
	{
		if(pokemmoDir == null)
		{
			throw new IllegalStateException("setupDirectories() must be called first");
		}
		return new File(pokemmoDir + relativePath);
	}

	/**
	 * Check if Java version meets requirements (Java 21+)
	 */
	public static boolean checkJavaVersion()
	{
		String version = System.getProperty("java.specification.version");
		int majorver;

		try
		{
			majorver = Integer.parseInt(version);
		}
		catch(NumberFormatException e)
		{
			try
			{
				majorver = Integer.parseInt(version.split("\\.")[1]);
			}
			catch(Exception e2)
			{
				majorver = -1;
			}
		}

		return majorver >= 21;
	}

	/**
	 * Check if the game appears to be running (simple lock file check)
	 */
	public static boolean isGameRunning()
	{
		if(pokemmoDir == null)
		{
			return false;
		}
		File lockFile = new File(pokemmoDir + "/.lock");
		return lockFile.exists();
	}

	public static boolean isPokemmoValid()
	{
		if(System.getenv("POKEMMO_NOVERIFY") != null)
		{
			return true;
		}

		for(UpdateFile file : FeedManager.getFiles())
		{
			boolean isNativeLibraryForOtherPlatform = isNativeLibraryForOtherPlatform(file.name);

			if(file.only_if_not_exists)
			{
				continue;
			}

			File f = getFile(file.name);
			if(!f.exists() && !isNativeLibraryForOtherPlatform)
			{
				return false;
			}

			if(!file.shouldDownload())
			{
				continue;
			}

			if(isNativeLibraryForOtherPlatform)
			{
				continue;
			}

			String checksumSha256 = file.sha256;
			String actualSha256 = Util.calculateHash("SHA-256", f);

			if(!checksumSha256.equalsIgnoreCase(actualSha256))
			{
				return false;
			}
		}

		return true;
	}

	public static boolean isNativeLibraryForOtherPlatform(String filename)
	{
		if(!filename.startsWith("lib/native/"))
		{
			return false;
		}

		if(filename.contains("/angle/") ||
				filename.contains("/lwjgl/") ||
				filename.contains("/native/"))
		{
			return !filename.contains("linux64");
		}

		return false;
	}

	public static List<String> buildJvmArgs()
	{
		List<String> args = new ArrayList<>();

		args.add(jrePath);

		args.add("-XX:+IgnoreUnrecognizedVMOptions");
		args.add("-XX:+UseZGC");
		args.add("-XX:+ZGenerational");
		args.add("-Xms192M");
		args.add("-Xmx" + Config.HARD_MAX_MEMORY_MB + "M");

		if(Config.AES_INTRINSICS_WORKAROUND_ENABLED)
		{
			args.add("-XX:+UnlockDiagnosticVMOptions");
			args.add("-XX:-UseAESCTRIntrinsics");
			args.add("-XX:-UseAESIntrinsics");
		}

		args.add("-Dfile.encoding=UTF-8");

		args.addAll(Arrays.asList("-cp", "PokeMMO.exe", "com.pokeemu.client.Client"));

		return args;
	}

	public static Map<String, String> buildEnvironment()
	{
		Map<String, String> env = new HashMap<>();

		env.put("GTK_USE_PORTALS", "1");
		env.put("POKEMMO_UNIX_LAUNCHER_VER", Integer.toString(UnixInstaller.INSTALLER_VERSION));

		if(snapcraft != null)
		{
			env.put("POKEMMO_IS_SNAPPED", snapcraft);
		}
		if(flatpak != null)
		{
			env.put("POKEMMO_IS_FLATPAKED", flatpak);
		}

		return env;
	}

	public static Process launchGame() throws IOException
	{
		if(pokemmoDir == null || jrePath == null)
		{
			throw new IllegalStateException("setupDirectories() must be called first");
		}

		List<String> args = buildJvmArgs();

		ProcessBuilder pb = new ProcessBuilder(args);
		pb.directory(new File(pokemmoDir));
		pb.inheritIO();

		pb.environment().putAll(buildEnvironment());

		System.out.println("Launching game: " + String.join(" ", args));

		return pb.start();
	}

	public static boolean createPokemmoDir()
	{
		File f = new File(pokemmoDir);
		return f.exists() || f.mkdirs();
	}
}