package com.pokeemu.unix;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.updater.FeedManager;

public class HeadlessLauncher
{
	private final AtomicBoolean feedsTimedOut = new AtomicBoolean(false);

	private String uiReason = "";

	private Throwable networkException = null;

	private volatile ScheduledExecutorService feedMonitor;
	private volatile CompletableFuture<Boolean> feedLoadFuture;

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
			shutdown();
		}
	}

	private boolean downloadFeedsWithTimeout()
	{
		boolean success = false;
		ScheduledExecutorService localFeedMonitor = null;

		try
		{
			System.out.println("Downloading feeds...");

			localFeedMonitor = Executors.newSingleThreadScheduledExecutor();
			feedMonitor = localFeedMonitor;

			// Start the async feed loading
			feedLoadFuture = FeedManager.loadAsync(new FeedManager.HeadlessProgressReporter());

			// Schedule timeout handler
			localFeedMonitor.schedule(() -> {
				if(feedLoadFuture != null && !feedLoadFuture.isDone())
				{
					feedsTimedOut.set(true);
					setNeedsUI("Network operations taking too long (>3 seconds)");

					// Request shutdown of feed loading
					FeedManager.requestShutdown();

					// Cancel the future
					feedLoadFuture.cancel(true);
				}
			}, 3, TimeUnit.SECONDS);

			try
			{
				// Wait for feed loading with a total timeout of 5 seconds
				success = feedLoadFuture.get(5, TimeUnit.SECONDS);

				if(success && !feedsTimedOut.get())
				{
					System.out.println("Feeds downloaded successfully");
				}
				else if(!FeedManager.isSuccessful())
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
			}
			catch(CancellationException e)
			{
				// This is expected when we cancel due to timeout - already handled via feedsTimedOut flag
				if(!feedsTimedOut.get())
				{
					// Unexpected cancellation
					networkException = e;
					setNeedsUI("Feed download was cancelled");
				}
			}
			catch(TimeoutException e)
			{
				feedsTimedOut.set(true);
				FeedManager.requestShutdown();

				if(feedLoadFuture != null)
				{
					feedLoadFuture.cancel(true);
				}

				networkException = e;
				setNeedsUI("Feed download timed out");
			}
			catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				FeedManager.requestShutdown();

				if(feedLoadFuture != null)
				{
					feedLoadFuture.cancel(true);
				}

				networkException = e;
				setNeedsUI("Feed download interrupted");
			}
			catch(ExecutionException e)
			{
				// Unwrap the actual cause
				Throwable cause = e.getCause();
				if(cause instanceof CancellationException)
				{
					// Handle as cancellation
					if(!feedsTimedOut.get())
					{
						networkException = cause;
						setNeedsUI("Feed download was cancelled");
					}
				}
				else
				{
					// Real execution error
					networkException = cause != null ? cause : e;
					setNeedsUI("Feed download failed: " + networkException.getMessage());
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				networkException = e;
				setNeedsUI("Feed download exception: " + e.getMessage());
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			networkException = e;
			setNeedsUI("Unexpected error during feed download: " + e.getMessage());
		}
		finally
		{
			if(localFeedMonitor != null)
			{
				shutdownExecutor(localFeedMonitor, "Feed Monitor", 1);
			}
			feedMonitor = null;
			feedLoadFuture = null;
		}

		return success && !feedsTimedOut.get();
	}

	private void setNeedsUI(String reason)
	{
		uiReason = reason;
		System.out.println("UI needed: " + reason);
	}

	private void shutdown()
	{
		// Request shutdown of any ongoing feed operations
		FeedManager.requestShutdown();

		// Cancel feed loading if still running
		if(feedLoadFuture != null && !feedLoadFuture.isDone())
		{
			feedLoadFuture.cancel(true);
			feedLoadFuture = null;
		}

		// Shutdown the monitor executor
		ScheduledExecutorService monitor = feedMonitor;
		if(monitor != null)
		{
			shutdownExecutor(monitor, "Feed Monitor", 1);
			feedMonitor = null;
		}
	}

	/**
	 * Properly shutdown an executor with adequate timeout and error handling
	 */
	private void shutdownExecutor(ExecutorService executorToShutdown, String name, int timeoutSeconds)
	{
		if(executorToShutdown == null)
		{
			return;
		}

		executorToShutdown.shutdown();

		try
		{
			if(!executorToShutdown.awaitTermination(timeoutSeconds, TimeUnit.SECONDS))
			{
				System.err.println(name + " didn't terminate gracefully within " + timeoutSeconds + " seconds, forcing shutdown");

				executorToShutdown.shutdownNow();

				if(!executorToShutdown.awaitTermination(timeoutSeconds, TimeUnit.SECONDS))
				{
					System.err.println(name + " failed to terminate even after forced shutdown");
					Thread.getAllStackTraces().forEach((thread, stack) -> {
						if(thread.getName().contains(name))
						{
							System.err.println("Stuck thread: " + thread.getName());
							for(StackTraceElement element : stack)
							{
								System.err.println("  at " + element);
							}
						}
					});
				}
			}
		}
		catch(InterruptedException e)
		{
			System.err.println(name + " shutdown interrupted");
			Thread.currentThread().interrupt();
			executorToShutdown.shutdownNow();
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