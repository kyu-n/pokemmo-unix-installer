package com.pokeemu.unix;

import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.enums.PokeMMOGC;
import com.pokeemu.unix.ui.MainFrame;
import com.pokeemu.unix.updater.MainFeed;
import com.pokeemu.unix.updater.UpdateFeed;
import com.pokeemu.unix.updater.UpdateFile;
import com.pokeemu.unix.util.Util;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

/**
 * PokeMMO Unix Installer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * This program is created as a pairing to the PokeMMO Game Client. PokeMMO's
 * Game Client software is provided with the PokeMMO License. To view this license, visit
 * https://pokemmo.eu/tos/
 *
 * This program manages:
 * - Downloads of the PokeMMO game client
 * - Signature verification for the downloaded files
 * - Cache management / storage of the program
 * - Execution of the program
 *
 * @author Kyu <kyu@pokemmo.eu>
 * @author Desu <desu@pokemmo.eu>
 *
 */
public class UnixInstaller
{
	public static final int INSTALLER_VERSION = 20;
	
	public static final int EXIT_CODE_SUCCESS = 0;
	public static final int EXIT_CODE_NETWORK_FAILURE = 1;
	public static final int EXIT_CODE_IO_FAILURE = 2;
	public static final int EXIT_CODE_UNK_FAILURE = -127;
	
	private UpdateFeed updateFeed;
	private MainFrame mainFrame;

	/**
	 * The default location of PokeMMO.exe and other files
	 */
	private String pokemmoDir;
	private String jrePath;

	/**
	 * The list of mirrors which have returned invalid results and must be skipped
	 */
	private final Set<Integer> disabledMirrors = new HashSet<>();
	/**
	 * If our PokeMMO client folder was missing
	 */
	private boolean firstRun = false;
	
	private boolean isLaunching = false;
	
	private void run()
	{
		String user_home = System.getProperty("user.home");
		String pokemmo_data_home = System.getenv("SNAP_USER_COMMON");
		if(pokemmo_data_home == null)
			pokemmo_data_home = Objects.requireNonNullElse(System.getenv("XDG_DATA_HOME"), user_home+"/.local/share");

		pokemmoDir = pokemmo_data_home+"/pokemmo-client-"+Config.UPDATE_CHANNEL.toString()+"/";

		jrePath = System.getProperty("java.home")+"/bin/java";
		mainFrame = new MainFrame(this);

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

		if(majorver > 16 || majorver < 11)
		{
			mainFrame.showError(Config.getString("error.incompatible_jvm", Config.getString("status.title.failed_startup")), "", () -> System.exit(EXIT_CODE_IO_FAILURE));
			return;
		}

		checkForRunning();
		downloadFeeds();
		
		File pokemmo_directory = new File(pokemmoDir);
		if(!pokemmo_directory.exists())
		{
			if(!pokemmo_directory.mkdirs() && !pokemmo_directory.exists())
				mainFrame.showError(Config.getString("error.dir_not_accessible", pokemmoDir, "DIR_1"), "", () -> System.exit(EXIT_CODE_IO_FAILURE));
				
			firstRun = true;
		}
		
		if(!pokemmo_directory.isDirectory())
			mainFrame.showError(Config.getString("error.dir_not_dir", pokemmoDir, "DIR_5"), "", () -> System.exit(EXIT_CODE_IO_FAILURE));

		if(!pokemmo_directory.setReadable(true) || !pokemmo_directory.setWritable(true) || !pokemmo_directory.setExecutable(true))
			mainFrame.showError(Config.getString("error.dir_not_accessible", pokemmoDir, "DIR_2"), "", () -> System.exit(EXIT_CODE_IO_FAILURE));
		
		if(firstRun)
		{
			// Screenshots symlink is only created if XDG_PICTURES_DIR is set. There is no way to predict what the pictures directory is otherwise set to, due to each DE implementing its own (and potentially different languages)
			String xdg_pictures_home = System.getenv("XDG_PICTURES_DIR");
			if(xdg_pictures_home != null)
			{
				File screenshots = new File(xdg_pictures_home+"/PokeMMO Screenshots/");

				if(!screenshots.exists() && screenshots.mkdir())
				{
					try
					{
						Files.createSymbolicLink(new File(pokemmoDir+"/screenshots").toPath(), screenshots.toPath());
					}
					catch (IOException e)
					{
						// Something has already set these up
						e.printStackTrace();
					}
				}
			}

			doUpdate(false);
		}
		else if(!isPokemmoValid())
		{
			File revision_file = new File(pokemmoDir+"/revision.txt");
			int revision = -1;
			if(revision_file.exists() && revision_file.isFile())
			{
				try
				{
					revision = Integer.parseInt(new String(Files.readAllBytes(revision_file.toPath())));
				}
				catch (IOException | NumberFormatException e)
				{
					// Don't care
				}
			}
			
			// If our declared revision is invalid, or we have already received this update, we must repair
			doUpdate(revision <= 0 || (MainFeed.MIN_REVISION > 0 && revision >= MainFeed.MIN_REVISION));
		}
		
		mainFrame.showInfo("status.check_success");
		mainFrame.setStatus("status.ready", 100);
		
		mainFrame.setCanStart(true);
	}
	
	public void launchGame()
	{
		if(isLaunching)
			return;
		
		isLaunching = true;
		try { start(); }
		catch (InterruptedException e) { System.exit(UnixInstaller.EXIT_CODE_UNK_FAILURE); }
	}
		
	private void start() throws InterruptedException
	{
		/*
		 * We unfortunately must initialize another JVM in order to launch the game due to -XstartOnFirstThread being required on
		 * macOS implementations of lwjgl3. This parameter is fundamentally incompatible with AWT used for our UI
		 * 
		 * This also fixes any working directory issues which may arise
		 */
		List<String> final_args = new ArrayList<>();
		
		final_args.add(jrePath);
		final_args.add("-XX:+IgnoreUnrecognizedVMOptions");

		PokeMMOGC active_gc = Config.ACTIVE_GC;
		
		final_args.add(active_gc.launch_arg);
		final_args.add("-Xms128M");
		
		final_args.add("-Xmx"+Config.HARD_MAX_MEMORY_MB+"M");
		
		if(Config.AES_INTRINSICS_WORKAROUND_ENABLED)
		{
			final_args.add("-XX:+UnlockDiagnosticVMOptions");
			final_args.add("-XX:-UseAESCTRIntrinsics");
			final_args.add("-XX:-UseAESIntrinsics");
		}

		final_args.add("-Dfile.encoding=UTF-8");

		/*
		 * The default parameters used for launching the PokeMMO Client
		 */
		final_args.addAll(Arrays.asList("-cp", "PokeMMO.exe", "com.pokeemu.client.Client"));

		ProcessBuilder pb = new ProcessBuilder(final_args);
		pb.directory(new File(pokemmoDir));
		pb.inheritIO();

		// Used by KDE to xdg-portal file dialogues, but we do not have a native file dialogue available at this time
//		pb.environment().put("GTK_USE_PORTALS", "1");

		String snap_env = System.getenv("POKEMMO_IS_SNAPPED");
		if(snap_env != null)
			pb.environment().put("POKEMMO_IS_SNAPPED", snap_env);

		System.out.println("Starting with params " + Arrays.toString(final_args.toArray(new String[0])));
		
		try
		{
			pb.start();
		}
		catch (IOException e)
		{
			e.printStackTrace();
			mainFrame.showError(Config.getString("status.failed_startup"), Config.getString("status.title.failed_startup"), () -> System.exit(EXIT_CODE_IO_FAILURE));
			return;
		}
		
		System.exit(0);
	}
	
	private void checkForRunning()
	{
		/*
		 * It's safe to assume that only one process may use this processes's JRE, and it should be sufficient to query if any other processes are running from the current directory
		 * This is not usable on Windows/Linux due to the potential for shared JREs/JDKs, but the approach works on macOS due to the app format
		 */
	    ProcessHandle processHandle = ProcessHandle.current();
	    ProcessHandle.Info processInfo = processHandle.info();
	    
	    System.out.println("Started launcher at " + System.getProperty("user.dir"));

	    if(processInfo.command().isEmpty())
		{
			// Something really bad happened. Our j11 process API doesn't work. Bail out to prevent other issues.
			mainFrame.showError(Config.getString("status.failed_startup"), Config.getString("status.title.failed_startup"), () -> System.exit(EXIT_CODE_IO_FAILURE));
			return;
		}

		String launcherPath = processInfo.command().get();

		List<ProcessHandle> destroyables = new ArrayList<>();
	    ProcessHandle.allProcesses().filter(ProcessHandle::isAlive).forEach(f ->
	    {
	    	try
	    	{
	    		if(f.info().command().isPresent() && f.pid() != processHandle.pid() && f.info().user().isPresent() && f.info().user().equals(processInfo.user()))
	    		{
	    	    	String path = f.info().command().get();
	    	    	if(path.equals(launcherPath) || path.equals(jrePath))
	    	    		destroyables.add(f);
	    		}
	    	}
	    	catch(Exception e)
	    	{
	    		// Skip!
	    	}
	    });
	    
	    if(destroyables.size() > 0)
	    {
	    	if(mainFrame.showYesNo(Config.getString("status.game_already_running"), "") == JOptionPane.YES_OPTION)
	    	{
	    		for(ProcessHandle p : destroyables)
    				p.destroyForcibly();
	    	}
	    	else
	    	{
	    		System.exit(EXIT_CODE_SUCCESS);
	    	}
	    }
	}
		
	private boolean isPokemmoValid()
	{
		if(System.getenv("POKEMMO_NOVERIFY") != null)
			return true;

		/*
		 * The list of files which MUST be updated before continuing to launch
		 */
		Set<UpdateFile> invalidFiles = new HashSet<>();
		
		mainFrame.setStatus("status.game_verification", 20);
		
		for(UpdateFile file : updateFeed.getFiles())
		{
			if(file.only_if_not_exists)
				continue;
			
			File f = getFile(file.name);
			
			if(!f.exists())
			{
				invalidFiles.add(file);
				continue;
			}
			
			if(!file.shouldDownload())
				continue;
			
			String checksum_sha256 = file.sha256;
			String actual_sha256 = Util.calculateHash("SHA-256", f);
			
			if(!checksum_sha256.equalsIgnoreCase(actual_sha256))
				invalidFiles.add(file);
		}
		
		return invalidFiles.size() < 1;
	}
	
	private void doUpdate(boolean repair)
	{
		if(repair)
		{
			mainFrame.setStatus("status.game_repair", 30);
		}
		else
		{
			mainFrame.addDetail("status.title.update_available", 30);
			mainFrame.setStatus("status.game_download", 30);
		}
		
		ExecutorService networkExecutorService = Executors.newFixedThreadPool(Config.NETWORK_THREADS);

		int total_files = updateFeed.getFiles().size();
		if(total_files < 1)
			total_files = 1;
		
		int counter = 0;
		
		List<UpdateFile> to_download = new ArrayList<>();
		
		for(UpdateFile file : updateFeed.getFiles())
		{
			if(!file.shouldDownload())
				continue;
			
			String checksum_sha256 = file.sha256;
			
			File f = getFile(file.name);
			
			if(file.only_if_not_exists && f.exists())
				continue;
			
			if(!f.getParentFile().mkdirs() && !f.getParentFile().exists())
				mainFrame.showError(Config.getString("error.dir_not_accessible", f.getParentFile(), "DIR_8"), "", () -> System.exit(EXIT_CODE_IO_FAILURE));

			String hash_sha256 = Util.calculateHash("SHA-256", f);
			
			if(!checksum_sha256.equalsIgnoreCase(hash_sha256))
			{
				if(repair)
				{
					mainFrame.addDetail("status.files.repairing", ((counter*100) / total_files), file.name);
					System.out.println("Checksum mismatch for " + file.name);
					System.out.println("Wanted SHA256: "+ checksum_sha256 + " | Actual: "+ hash_sha256);
				}
			
				to_download.add(file);
			}
			
			counter++;
		}
		
		if(to_download.size() < 1)
		{
			mainFrame.setStatus("status.game_verified", 90);
			return;
		}
		
		mainFrame.setStatus("status.downloading", 0);
		
		Phaser phaser = new Phaser(to_download.size()+1);
		
		for(UpdateFile file : to_download)
		{
			networkExecutorService.submit(() ->
			{
				mainFrame.addDetail("status.files.downloading", ((phaser.getArrivedParties()*100)/to_download.size()), file.name);
				
				if(downloadFile(updateFeed, file))
				{
					phaser.arrive();
				}
				else
				{
					mainFrame.showError(Config.getString("error.download_error"), "", () -> System.exit(EXIT_CODE_NETWORK_FAILURE));
				}
			});
		}
		
		phaser.arriveAndAwaitAdvance();
		
		if(repair)
			clearCache();
		
		networkExecutorService.shutdown();
	}
	
	private boolean downloadFile(UpdateFeed update_feed, UpdateFile file)
	{
		String checksum_sha256 = file.sha256;
		for(int mirror_index = 0; mirror_index < MainFeed.DOWNLOAD_MIRRORS.length; mirror_index++)
		{
			if(disabledMirrors.contains(mirror_index))
				continue;
			
			if(!Util.downloadUrlToFile(MainFeed.DOWNLOAD_MIRRORS[mirror_index]+"/download/client/"+file.name+"?v="+file.getCacheBuster(), getFile(file.name+".TEMPORARY")))
			{
				System.out.println("FAILURE");
				mainFrame.showInfo("status.files.failed_download", file.name, mirror_index);
				disabledMirrors.add(mirror_index);
				continue;
			}
			
			System.out.println("Downloaded new " + file.name + ".TEMPORARY");
			
			File temporary_file = getFile(file.name + ".TEMPORARY");
			
			System.out.println("Requested file download to " + temporary_file.getAbsolutePath());
			
			String new_sha256_hash = Util.calculateHash("SHA-256", temporary_file);
			if(!checksum_sha256.equalsIgnoreCase(new_sha256_hash))
			{
				mainFrame.showInfo(Config.getString("status.files.failed_checksum", file.name, checksum_sha256, new_sha256_hash, mirror_index));
				
				if(temporary_file.isFile() && temporary_file.exists())
					temporary_file.delete();
				
				disabledMirrors.add(mirror_index);
				continue;					
			}

			File old_file = getFile(file.name);
			if(old_file.isFile() && old_file.exists() && !old_file.delete())
			{
				//This is a fail case, changing mirror will not help.
				mainFrame.showError(Config.getString("status.title.fatal_error", old_file.getPath()), Config.getString("status.title.fatal_error"));
				return false;
			}
			
			if(!temporary_file.renameTo(old_file))
			{
				//This is a fail case, changing mirror will not help.
				mainFrame.showError(Config.getString("status.title.fatal_error", temporary_file.getPath(), old_file.getPath()), Config.getString("status.title.fatal_error"));
				return false;
			}
			return true;
		}
		
		return false;
	}
	
	private void clearCache()
	{
		mainFrame.showInfo("status.delete_caches");
		if(!getFile("PokeMMO.exe").exists())
			return;
		
		File cache_folder = getFile("cache");
		if(!cache_folder.exists() || (!Files.isSymbolicLink(cache_folder.toPath()) && !cache_folder.isDirectory()))
			return;
		
		File[] cache_files = cache_folder.listFiles(f ->
		{
			if(f.isDirectory() || !f.isFile())
				return false;

			return f.getName().toLowerCase(Locale.ENGLISH).endsWith(".bin");
		});

		if(cache_files == null)
			return;
		
		for(File cache_file : cache_files)
		{
			if(cache_file.delete())
				mainFrame.showInfo("status.delete_cache_file", cache_file.getPath());
		}
	}
	
	private void downloadFeeds()
	{
		try
		{
			System.setProperty("http.agent", "Mozilla/5.0 (PokeMMO; UnixInstaller v"+INSTALLER_VERSION+")");
		}
		catch(Exception e)
		{
			// Don't care
		}
		
		mainFrame.setStatus("status.networking.load", 0);
		
		MainFeed.load(mainFrame);
		
		updateFeed = new UpdateFeed(mainFrame);
		if(!updateFeed.SUCCESSFUL)
			mainFrame.showError(Config.getString("status.networking.feed_load_failed"), Config.getString("status.title.network_failure"), () -> System.exit(EXIT_CODE_NETWORK_FAILURE));
	}
	
	private File getFile(String path)
	{
		return new File(pokemmoDir+path);
	}
	
	public File getPokemmoDir()
	{
		return new File(pokemmoDir);
	}
	
	public static void main(String[] args)
	{
		init();
		
		Config.load();
		
		try
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		Runtime.getRuntime().addShutdownHook(new Thread(Config::save));
		
		new UnixInstaller().run();
	}
	
	private static void init()
	{
		System.setProperty("awt.useSystemAAFontSettings","on");
		System.setProperty("swing.aatext", "true");
	}
}
