package com.pokeemu.unix;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.updater.FeedManager;

public class HeadlessLauncher
{
	private final AtomicBoolean feedsTimedOut = new AtomicBoolean(false);

	private String uiReason = "";

	private Throwable networkException = null;

	private final ExecutorService executor = Executors.newCachedThreadPool();
	private ScheduledExecutorService feedMonitor;

	public boolean tryLaunchWithoutUI()
	{
		try
		{
			System.out.println("Attempting headless launch...");

			Config.load();
			LauncherUtils.setupDirectories();

			if(!LauncherUtils.checkJavaVersion())
			{
				setNeedsUI("Java version incompatible");
				return false;
			}

			if(LauncherUtils.isGameRunning())
			{
				setNeedsUI("Game already running");
				return false;
			}

			if(!downloadFeedsWithTimeout())
			{
				return false;
			}

			File pokemmoDirectory = new File(LauncherUtils.getPokemmoDir());
			if(!pokemmoDirectory.exists())
			{
				setNeedsUI("PokeMMO not installed");
				return false;
			}

			if(!pokemmoDirectory.isDirectory())
			{
				setNeedsUI("Installation directory is invalid");
				return false;
			}

			if(!LauncherUtils.isPokemmoValid())
			{
				setNeedsUI("Game files need updating");
				return false;
			}

			System.out.println("Game is up to date, launching directly...");
			try
			{
				LauncherUtils.launchGame();
				return true;
			}
			catch(IOException e)
			{
				e.printStackTrace();
				setNeedsUI("Failed to launch game: " + e.getMessage());
				return false;
			}

		}
		catch(Exception e)
		{
			e.printStackTrace();
			networkException = e;
			setNeedsUI("Unexpected error: " + e.getMessage());
			return false;
		}
		finally
		{
			// Always cleanup resources
			shutdown();
		}
	}

	private boolean downloadFeedsWithTimeout()
	{
		boolean success = false;

		try
		{
			// Start
			System.out.println("Downloading feeds...");

			feedMonitor = Executors.newSingleThreadScheduledExecutor();
			feedMonitor.schedule(() -> {
				if(!FeedManager.SUCCESSFUL)
				{
					feedsTimedOut.set(true);
					setNeedsUI("Network operations taking too long (>3 seconds)");
				}
			}, 3, TimeUnit.SECONDS);

			FeedManager.load(new FeedManager.HeadlessProgressReporter());

			// Check result
			if(!FeedManager.SUCCESSFUL)
			{
				networkException = FeedManager.getLastException();

				if(networkException == null)
				{
					networkException = new Exception("Failed to download update feeds from all mirrors");
				}

				if(!feedsTimedOut.get())
				{
					String errorDetails = "Network Error: " + networkException.getMessage();
					setNeedsUI(errorDetails);
				}
			}
			else if(feedsTimedOut.get())
			{
				// If we timed out but eventually succeeded, still show UI
			}
			else
			{
				System.out.println("Feeds downloaded successfully");
				success = true;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			networkException = e;
			setNeedsUI("Feed download exception: " + e.getMessage());
		}
		finally
		{
			// Always cleanup the feed monitor
			if(feedMonitor != null)
			{
				feedMonitor.shutdownNow();
				try
				{
					feedMonitor.awaitTermination(100, TimeUnit.MILLISECONDS);
				}
				catch(InterruptedException e)
				{
					Thread.currentThread().interrupt();
				}
				feedMonitor = null;
			}
		}

		return success;
	}

	private void setNeedsUI(String reason)
	{
		uiReason = reason;
		System.out.println("UI needed: " + reason);
	}

	private void shutdown()
	{
		// Shutdown feed monitor if still active
		if(feedMonitor != null)
		{
			feedMonitor.shutdownNow();
			try
			{
				feedMonitor.awaitTermination(500, TimeUnit.MILLISECONDS);
			}
			catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
			}
			feedMonitor = null;
		}

		// Shutdown main executor
		executor.shutdown();
		try
		{
			if(!executor.awaitTermination(2, TimeUnit.SECONDS))
			{
				executor.shutdownNow();

				// Final attempt
				if(!executor.awaitTermination(1, TimeUnit.SECONDS))
				{
					System.err.println("Executor failed to terminate cleanly");
				}
			}
		}
		catch(InterruptedException e)
		{
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	public String getUIReason()
	{
		return uiReason;
	}

	public Throwable getNetworkException()
	{
		return networkException;
	}
}