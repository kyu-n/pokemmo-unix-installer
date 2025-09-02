package com.pokeemu.unix;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.ConfigWindow;
import com.pokeemu.unix.ui.ErrorDialog;
import com.pokeemu.unix.ui.ImGuiStyleManager;
import com.pokeemu.unix.ui.ImGuiThreadBridge;
import com.pokeemu.unix.ui.LocalizationManager;
import com.pokeemu.unix.ui.MainWindow;
import com.pokeemu.unix.ui.MessageDialog;
import com.pokeemu.unix.updater.FeedManager;
import com.pokeemu.unix.updater.UpdaterService;

import com.pokeemu.unix.util.GnomeThemeDetector;

import imgui.ImGui;
import imgui.app.Application;
import imgui.app.Configuration;
import imgui.flag.ImGuiConfigFlags;

import org.lwjgl.glfw.GLFW;

public class UnixInstaller extends Application
{
	public static final int INSTALLER_VERSION = 30;

	public static final int EXIT_CODE_SUCCESS = 0;
	public static final int EXIT_CODE_NETWORK_FAILURE = 1;
	public static final int EXIT_CODE_IO_FAILURE = 2;

	public static boolean QUICK_AUTOSTART = true;
	public static boolean FORCE_UI = false;

	private static final int WINDOW_WIDTH = 540;
	private static final int WINDOW_HEIGHT = 300;

	private MainWindow mainWindow;
	private ConfigWindow configWindow;
	private ImGuiThreadBridge threadBridge;
	private ErrorDialog errorDialog;

	private final AtomicBoolean isLaunching = new AtomicBoolean(false);
	private final AtomicBoolean isUpdating = new AtomicBoolean(false);

	private final AtomicBoolean disposed = new AtomicBoolean(false);

	private ExecutorService backgroundExecutor;
	private UpdaterService updaterService;

	private static Throwable headlessException = null;

	@Override
	protected void configure(Configuration config)
	{
		config.setTitle(Config.getString("main.title"));
		config.setWidth(WINDOW_WIDTH);
		config.setHeight(WINDOW_HEIGHT);
	}

	@Override
	protected void initImGui(Configuration config)
	{
		super.initImGui(config);

		ImGui.getIO().addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);
		ImGui.getIO().setIniFilename(null);

		LocalizationManager.instance.initializeFonts();
		ImGuiStyleManager.applySystemTheme();

		threadBridge = new ImGuiThreadBridge();
		mainWindow = new MainWindow(this, threadBridge, WINDOW_WIDTH, WINDOW_HEIGHT);
		configWindow = new ConfigWindow(this);
		errorDialog = new ErrorDialog(this);

		backgroundExecutor = Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			t.setName("UnixInstaller-Worker-" + t.hashCode());
			return t;
		});

		updaterService = new UpdaterService(this, threadBridge);

		MessageDialog.getInstance().setParent(this);

		if(headlessException != null)
		{
			QUICK_AUTOSTART = false;

			threadBridge.asyncExec(() -> {
				errorDialog.show(headlessException);
				mainWindow.addTaskLine("ERROR: " + headlessException.getMessage());
			});
		}
		else
		{
			initializeInstaller();
		}
	}

	@Override
	public void process()
	{
		threadBridge.processUpdates();

		mainWindow.render();

		if(configWindow.isVisible())
		{
			configWindow.render();
		}

		errorDialog.render();

		MessageDialog.getInstance().render();

		if(QUICK_AUTOSTART && mainWindow.isCanStart() && !isLaunching.get() &&
				!isUpdating.get() && !errorDialog.isVisible())
		{
			launchGame();
		}
	}

	@Override
	protected void dispose()
	{
		if(!disposed.compareAndSet(false, true))
		{
			return;
		}

		// Request shutdown of async feed operations
		FeedManager.requestShutdown();

		try
		{
			Config.save();
		}
		catch(Exception e)
		{
			System.err.println("Failed to save configuration: " + e.getMessage());
		}

		if(updaterService != null)
		{
			try
			{
				updaterService.shutdown();
			}
			catch(Exception e)
			{
				System.err.println("Failed to shutdown updater service: " + e.getMessage());
			}
			updaterService = null;
		}

		if(backgroundExecutor != null)
		{
			backgroundExecutor.shutdown();
			try
			{
				if(!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS))
				{
					backgroundExecutor.shutdownNow();
					if(!backgroundExecutor.awaitTermination(2, TimeUnit.SECONDS))
					{
						System.err.println("Background executor did not terminate");
					}
				}
			}
			catch(InterruptedException e)
			{
				backgroundExecutor.shutdownNow();
				Thread.currentThread().interrupt();
			}
			backgroundExecutor = null;
		}

		if(threadBridge != null)
		{
			threadBridge.clearPendingUpdates();
			threadBridge = null;
		}

		try
		{
			super.dispose();
		}
		catch(Exception e)
		{
			System.err.println("Error in super.dispose(): " + e.getMessage());
		}
	}

	private void initializeInstaller()
	{
		backgroundExecutor.submit(() -> {
			try
			{
				LauncherUtils.setupDirectories();

				if(Config.hasConfigurationErrors())
				{
					String errors = Config.getConfigurationErrors();
					threadBridge.asyncExec(() -> {
						mainWindow.addTaskLine("WARNING: Configuration file had errors, using defaults:");
						for(String line : errors.split("\n"))
						{
							if(!line.trim().isEmpty())
							{
								mainWindow.addTaskLine("  - " + line);
							}
						}
						mainWindow.addTaskLine("You can fix these in Settings or by editing pokemmo-installer.properties");
					});
				}

				if(!LauncherUtils.checkJavaVersion())
				{
					threadBridge.showError(
							Config.getString("error.incompatible_jvm"),
							Config.getString("status.title.failed_startup"),
							() -> System.exit(EXIT_CODE_IO_FAILURE)
					);
					return;
				}

				if(LauncherUtils.isGameRunning())
				{
					System.out.println("Lock file exists, game may be running");
				}

				downloadFeeds();

				if(!FeedManager.isSuccessful())
				{
					return;
				}

				File pokemmoDirectory = new File(LauncherUtils.getPokemmoDir());
				if(!pokemmoDirectory.exists())
				{
					createPokemmoDir();
					createSymlinkedDirectories();
					updaterService.startUpdate(false, false);
				}
				else if(!pokemmoDirectory.isDirectory())
				{
					threadBridge.showError(
							Config.getString("error.dir_not_dir", LauncherUtils.getPokemmoDir(), "DIR_5"),
							Config.getString("status.title.io_failure"),
							() -> System.exit(EXIT_CODE_IO_FAILURE)
					);
				}
				else if(!LauncherUtils.isPokemmoValid())
				{
					File revisionFile = new File(LauncherUtils.getPokemmoDir() + "/revision.txt");
					int revision = -1;
					if(revisionFile.exists() && revisionFile.isFile())
					{
						try
						{
							revision = Integer.parseInt(new String(Files.readAllBytes(revisionFile.toPath())).trim());
						}
						catch(IOException | NumberFormatException e)
						{
						}
					}

					boolean repair = (revision <= 0 || (FeedManager.getMinRevision() > 0 && revision >= FeedManager.getMinRevision()));
					updaterService.startUpdate(repair, false);
				}
				else
				{
					threadBridge.addDetail(Config.getString("status.check_success"), 90);
					threadBridge.setStatus(Config.getString("status.ready"), 100);
					mainWindow.setCanStart(true);
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				threadBridge.asyncExec(() -> errorDialog.show(e));
			}
		});
	}

	private void downloadFeeds()
	{
		threadBridge.setStatus(Config.getString("status.networking.load"), 0);
		FeedManager.load(threadBridge);

		if(!FeedManager.isSuccessful())
		{
			Throwable feedException = FeedManager.getLastException();
			if(feedException == null)
			{
				feedException = new Exception(Config.getString("status.networking.feed_load_failed") +
						"\n\nDetails:\n" + FeedManager.getLastExceptionDetails());
			}

			final Throwable exceptionToShow = feedException;

			threadBridge.asyncExec(() -> errorDialog.show(exceptionToShow, ErrorDialog.ErrorType.NETWORK));
		}
	}

	public void retryConnection()
	{
		mainWindow.clearTaskOutput();
		mainWindow.setCanStart(false);

		FeedManager.resetForRetry();

		mainWindow.addTaskLine(Config.getString("status.retrying_connection"));

		initializeInstaller();
	}

	public void launchGame()
	{
		if(isLaunching.getAndSet(true) || isUpdating.get())
		{
			return;
		}

		backgroundExecutor.submit(() -> {
			try
			{
				Process gameProcess = LauncherUtils.launchGame();

				System.out.println("Game process launched successfully, PID: " + gameProcess.pid());

				threadBridge.asyncExec(() -> {
					System.out.println("Closing launcher UI for socket transfer...");
					disposeWindow();
				});

				LauncherUtils.waitForSocketCompletion();
				System.exit(EXIT_CODE_SUCCESS);
			}
			catch(IOException e)
			{
				e.printStackTrace();
				isLaunching.set(false);
				threadBridge.showError(
						Config.getString("status.failed_startup"),
						Config.getString("status.title.failed_startup"),
						null
				);
			}
		});
	}

	public void createPokemmoDir()
	{
		if(!LauncherUtils.createPokemmoDir())
		{
			threadBridge.showError(
					Config.getString("error.dir_not_accessible", LauncherUtils.getPokemmoDir(), "DIR_1"),
					"Directory Error",
					() -> System.exit(EXIT_CODE_IO_FAILURE)
			);
		}
	}

	public void createSymlinkedDirectories()
	{
		String xdgPicturesHome = System.getenv("XDG_PICTURES_DIR");
		if(xdgPicturesHome != null)
		{
			File screenshots = new File(xdgPicturesHome + "/PokeMMO Screenshots/");
			if(!screenshots.exists() && screenshots.mkdir())
			{
				try
				{
					Path link = new File(LauncherUtils.getPokemmoDir() + "/screenshots").toPath();
					if(!Files.exists(link))
					{
						Files.createSymbolicLink(link, screenshots.toPath());
					}
				}
				catch(IOException e)
				{
					System.err.println("Failed to create screenshots symlink: " + e.getMessage());
				}
			}
		}
	}

	public File getPokemmoDir()
	{
		return new File(LauncherUtils.getPokemmoDir());
	}

	public boolean isUpdating()
	{
		return isUpdating.get();
	}

	public void setUpdating(boolean updating)
	{
		isUpdating.set(updating);
	}

	public ConfigWindow getConfigWindow()
	{
		return configWindow;
	}

	public MainWindow getMainWindow()
	{
		return mainWindow;
	}

	public ErrorDialog getErrorDialog()
	{
		return errorDialog;
	}

	public UpdaterService getUpdaterService()
	{
		return updaterService;
	}

	public ImGuiThreadBridge getThreadBridge()
	{
		return threadBridge;
	}

	public static void main(String[] args)
	{
		for(String arg : args)
		{
			if(arg.equals("--force-ui"))
			{
				FORCE_UI = true;
				QUICK_AUTOSTART = false;
				break;
			}
		}

		Config.load();

		if(!FORCE_UI)
		{
			HeadlessLauncher headless = new HeadlessLauncher();
			try
			{
				if(headless.tryLaunchWithoutUI())
				{
					System.exit(EXIT_CODE_SUCCESS);
				}

				headlessException = headless.getNetworkException();
			}
			catch(Exception e)
			{
				headlessException = e;
			}

			System.out.println("=================================================");
			System.out.println("[INFO] Launching installer UI");
			System.out.println("Reason: " + headless.getUIReason());
			if(headlessException != null)
			{
				System.out.println("Exception: " + headlessException);
			}
			System.out.println("=================================================");
		}
		else
		{
			System.out.println("=================================================");
			System.out.println("[INFO] UI mode forced via --force-ui flag");
			System.out.println("=================================================");
		}

		detectAndLogDisplayServer();
		launch(new UnixInstaller());
	}

	private static void detectAndLogDisplayServer()
	{
		System.out.println("=================================================");
		System.out.println("[DEBUG] Display Server Detection:");

		// Primary detection methods
		String waylandDisplay = System.getenv("WAYLAND_DISPLAY");
		String xdgSessionType = System.getenv("XDG_SESSION_TYPE");
		String display = System.getenv("DISPLAY");

		// Desktop environment detection
		String xdgCurrentDesktop = System.getenv("XDG_CURRENT_DESKTOP");
		String desktopSession = System.getenv("DESKTOP_SESSION");
		String kdeFullSession = System.getenv("KDE_FULL_SESSION");

		// Container detection
		String flatpakId = System.getenv("FLATPAK_ID");
		String snapName = System.getenv("SNAP_NAME");
		String snapRevision = System.getenv("SNAP_REVISION");

		// Determine display server
		String displayServer = "Unknown";
		if("wayland".equalsIgnoreCase(xdgSessionType) || waylandDisplay != null)
		{
			displayServer = "Wayland";
			if(waylandDisplay != null)
			{
				displayServer += " (WAYLAND_DISPLAY=" + waylandDisplay + ")";

				GLFW.glfwWindowHint(GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
				GLFW.glfwWindowHint(GLFW.GLFW_WAYLAND_LIBDECOR, GLFW.GLFW_WAYLAND_PREFER_LIBDECOR);
			}
		}
		else if("x11".equalsIgnoreCase(xdgSessionType) || display != null)
		{
			displayServer = "X11";
			if(display != null)
			{
				displayServer += " (DISPLAY=" + display + ")";
			}
		}

		System.out.println("Display Server: " + displayServer);
		System.out.println("XDG_SESSION_TYPE: " + (xdgSessionType != null ? xdgSessionType : "not set"));

		// Desktop environment
		System.out.println("Desktop Environment: " +
				(xdgCurrentDesktop != null ? xdgCurrentDesktop : "unknown"));
		if(desktopSession != null)
		{
			System.out.println("Desktop Session: " + desktopSession);
		}

		// Specific DE detection
		if(kdeFullSession != null)
		{
			System.out.println("KDE Detected: KDE_FULL_SESSION=" + kdeFullSession);
		}

		// Container environment
		if(flatpakId != null)
		{
			System.out.println("Running in Flatpak: " + flatpakId);
		}
		if(snapName != null)
		{
			System.out.println("Running in Snap: " + snapName + " (rev: " + snapRevision + ")");
		}

		// Theme detection
		boolean isDarkTheme = GnomeThemeDetector.isDark();
		System.out.println("Theme Detection: " + (isDarkTheme ? "Dark" : "Light"));

		// Additional environment info
		String gtkTheme = System.getenv("GTK_THEME");
		if(gtkTheme != null)
		{
			System.out.println("GTK_THEME: " + gtkTheme);
		}

		String qtStyle = System.getenv("QT_STYLE_OVERRIDE");
		if(qtStyle != null)
		{
			System.out.println("QT_STYLE_OVERRIDE: " + qtStyle);
		}

		// Graphics info
		String glVendor = System.getProperty("jogl.vendor");
		String glRenderer = System.getProperty("jogl.renderer");
		if(glVendor != null || glRenderer != null)
		{
			System.out.println("Graphics: " +
					(glVendor != null ? glVendor : "unknown") + " / " +
					(glRenderer != null ? glRenderer : "unknown"));
		}

		System.out.println("=================================================");
	}
}