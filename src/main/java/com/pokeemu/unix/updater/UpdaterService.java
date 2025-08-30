package com.pokeemu.unix.updater;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.pokeemu.unix.LauncherUtils;
import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.IProgressReporter;
import com.pokeemu.unix.util.Util;

public class UpdaterService
{

	private final UnixInstaller parent;
	private final IProgressReporter progressReporter;
	private final ExecutorService downloadExecutor;
	private final ScheduledExecutorService speedCalculator;

	// Thread-safe set for disabled mirrors
	private final Set<Integer> disabledMirrors = Collections.synchronizedSet(new HashSet<>());

	private final Map<String, DownloadTask> activeTasks = new ConcurrentHashMap<>();
	private final AtomicLong totalBytesDownloaded = new AtomicLong(0);
	private final AtomicLong lastBytesDownloaded = new AtomicLong(0);
	private final AtomicInteger completedFiles = new AtomicInteger(0);
	private volatile long lastSpeedCalculation = System.currentTimeMillis();

	private volatile boolean isShuttingDown = false;

	public UpdaterService(UnixInstaller parent, IProgressReporter progressReporter)
	{
		this.parent = parent;
		this.progressReporter = progressReporter;
		this.downloadExecutor = Executors.newFixedThreadPool(Config.NETWORK_THREADS);
		this.speedCalculator = Executors.newSingleThreadScheduledExecutor();

		speedCalculator.scheduleAtFixedRate(this::calculateDownloadSpeed, 0, 1, TimeUnit.SECONDS);
	}

	public void startUpdate(boolean repair, boolean clean)
	{
		if(parent.isUpdating())
		{
			return;
		}

		parent.setUpdating(true);

		CompletableFuture.runAsync(() -> {
			try
			{
				if(clean)
				{
					performCleanInstall();
				}

				if(repair)
				{
					performRepair();
				}
				else
				{
					performUpdate();
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				progressReporter.showError("Update failed: " + e.getMessage(), "Update Error", () -> parent.setUpdating(false));
			}
		});
	}

	private void performCleanInstall() throws IOException
	{
		progressReporter.setStatus(Config.getString("status.cleaning"), 10);

		Path installPath = Path.of(parent.getPokemmoDir().getAbsolutePath());

		if(Files.exists(installPath))
		{
			Files.walk(installPath).sorted(Comparator.reverseOrder()).forEach(path -> {
				try
				{
					Files.delete(path);
					progressReporter.showInfo("status.deleted_file", path.getFileName());
				}
				catch(IOException e)
				{
					progressReporter.showInfo("status.failed_delete", path.getFileName());
				}
			});
		}

		LauncherUtils.createPokemmoDir();
		parent.createSymlinkedDirectories();
	}

	private void performRepair()
	{
		progressReporter.setStatus(Config.getString("status.game_repair"), 30);

		List<UpdateFile> toRepair = new ArrayList<>();
		int totalFiles = FeedManager.getFiles().size();
		int counter = 0;

		for(UpdateFile file : FeedManager.getFiles())
		{
			if(!file.shouldDownload())
			{
				continue;
			}

			if(LauncherUtils.isNativeLibraryForOtherPlatform(file.name))
			{
				continue;
			}

			File f = LauncherUtils.getFile(file.name);
			String checksum_sha256 = file.sha256;

			if(file.only_if_not_exists && f.exists())
			{
				continue;
			}

			if(!f.getParentFile().exists() && !f.getParentFile().mkdirs())
			{
				progressReporter.showError(Config.getString("error.dir_not_accessible", f.getParentFile(), "DIR_8"), "", null);
				parent.setUpdating(false);
				return;
			}

			String hash_sha256 = Util.calculateHash("SHA-256", f);

			if(!checksum_sha256.equalsIgnoreCase(hash_sha256))
			{
				progressReporter.addDetail("status.files.repairing", (counter * 100) / totalFiles, file.name);
				toRepair.add(file);
			}

			counter++;
		}

		if(!toRepair.isEmpty())
		{
			downloadFiles(toRepair);
		}
		else
		{
			finishUpdate();
		}
	}

	private void performUpdate()
	{
		progressReporter.addDetail("status.title.update_available", 30);
		progressReporter.setStatus(Config.getString("status.game_download"), 30);

		List<UpdateFile> toDownload = new ArrayList<>();

		for(UpdateFile file : FeedManager.getFiles())
		{
			if(!file.shouldDownload())
			{
				continue;
			}

			if(LauncherUtils.isNativeLibraryForOtherPlatform(file.name))
			{
				continue;
			}

			File f = LauncherUtils.getFile(file.name);
			String checksum_sha256 = file.sha256;

			if(file.only_if_not_exists && f.exists())
			{
				continue;
			}

			if(!f.getParentFile().exists() && !f.getParentFile().mkdirs())
			{
				progressReporter.showError(Config.getString("error.dir_not_accessible", f.getParentFile(), "DIR_8"), "", null);
				parent.setUpdating(false);
				return;
			}

			String hash_sha256 = Util.calculateHash("SHA-256", f);

			if(!checksum_sha256.equalsIgnoreCase(hash_sha256))
			{
				toDownload.add(file);
			}
		}

		if(!toDownload.isEmpty())
		{
			downloadFiles(toDownload);
		}
		else
		{
			finishUpdate();
		}
	}

	private void downloadFiles(List<UpdateFile> files)
	{
		progressReporter.setStatus(Config.getString("status.downloading"), 40);

		completedFiles.set(0);
		totalBytesDownloaded.set(0);
		disabledMirrors.clear();

		CountDownLatch downloadLatch = new CountDownLatch(files.size());

		for(UpdateFile file : files)
		{
			if(isShuttingDown)
			{
				downloadLatch.countDown();
				continue;
			}

			DownloadTask task = new DownloadTask(file, downloadLatch);
			activeTasks.put(file.name, task);
			downloadExecutor.submit(task);
		}

		try
		{
			downloadLatch.await();
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
			progressReporter.showError("Download interrupted", "Error", null);
			parent.setUpdating(false);
			return;
		}

		finishUpdate();
	}

	private void finishUpdate()
	{
		progressReporter.setStatus(Config.getString("status.game_verified"), 90);
		progressReporter.setStatus(Config.getString("status.ready"), 100);
		parent.setUpdating(false);

		parent.getThreadBridge().asyncExec(() -> parent.getMainWindow().setCanStart(true));
	}

	private void calculateDownloadSpeed()
	{
		if(isShuttingDown)
		{
			return;
		}

		long currentBytes = totalBytesDownloaded.get();
		long bytesSinceLastCheck = currentBytes - lastBytesDownloaded.get();
		long currentTime = System.currentTimeMillis();
		long timeDiff = currentTime - lastSpeedCalculation;

		if(timeDiff > 0)
		{
			double bytesPerSecond = (bytesSinceLastCheck * 1000.0) / timeDiff;
			String speedText = formatSpeed(bytesPerSecond);
			progressReporter.setDownloadSpeed(speedText);
		}

		lastBytesDownloaded.set(currentBytes);
		lastSpeedCalculation = currentTime;
	}

	private String formatSpeed(double bytesPerSecond)
	{
		if(bytesPerSecond < 1024)
		{
			return String.format("%.0f B/s", bytesPerSecond);
		}
		else if(bytesPerSecond < 1024 * 1024)
		{
			return String.format("%.1f KB/s", bytesPerSecond / 1024);
		}
		else
		{
			return String.format("%.1f MB/s", bytesPerSecond / (1024 * 1024));
		}
	}

	private class DownloadTask implements Runnable
	{
		private final UpdateFile file;
		private final CountDownLatch latch;

		public DownloadTask(UpdateFile file, CountDownLatch latch)
		{
			this.file = file;
			this.latch = latch;
		}

		@Override
		public void run()
		{
			try
			{
				if(isShuttingDown)
				{
					return;
				}

				int progress = (completedFiles.get() * 100) / activeTasks.size();
				progressReporter.addDetail("status.files.downloading", progress, file.name);

				if(downloadFile())
				{
					completedFiles.incrementAndGet();
					progress = (completedFiles.get() * 100) / activeTasks.size();
					progressReporter.setStatus(Config.getString("status.downloading"), progress);
				}
				else
				{
					progressReporter.showError(Config.getString("error.download_error", file.name), "Download Failed", null);
				}
			}
			finally
			{
				activeTasks.remove(file.name);
				latch.countDown();
			}
		}

		private boolean downloadFile()
		{
			String checksum_sha256 = file.sha256;

			for(int mirror_index = 0; mirror_index < FeedManager.DOWNLOAD_MIRRORS.length; mirror_index++)
			{
				if(isShuttingDown)
				{
					return false;
				}

				// Thread-safe check for disabled mirrors
				if(disabledMirrors.contains(mirror_index))
				{
					continue;
				}

				String url = FeedManager.DOWNLOAD_MIRRORS[mirror_index] + "/" + Config.UPDATE_CHANNEL + "/current/client/" + file.name + "?v=" + file.getCacheBuster();

				File tempFile = LauncherUtils.getFile(file.name + ".TEMPORARY");

				if(!downloadWithProgress(url, tempFile))
				{
					progressReporter.showInfo("status.files.failed_download", file.name, mirror_index);
					disabledMirrors.add(mirror_index);
					continue;
				}

				String new_sha256_hash = Util.calculateHash("SHA-256", tempFile);
				if(!checksum_sha256.equalsIgnoreCase(new_sha256_hash))
				{
					progressReporter.showInfo("status.files.failed_checksum", file.name, checksum_sha256, new_sha256_hash, mirror_index);

					if(tempFile.exists())
					{
						tempFile.delete();
					}

					disabledMirrors.add(mirror_index);
					continue;
				}

				File oldFile = LauncherUtils.getFile(file.name);
				if(oldFile.exists() && !oldFile.delete())
				{
					progressReporter.showError(Config.getString("status.title.fatal_error", oldFile.getPath()), Config.getString("status.title.fatal_error"), null);
					return false;
				}

				if(!tempFile.renameTo(oldFile))
				{
					progressReporter.showError(Config.getString("status.title.fatal_error", tempFile.getPath(), oldFile.getPath()), Config.getString("status.title.fatal_error"), null);
					return false;
				}

				return true;
			}

			return false;
		}

		private boolean downloadWithProgress(String url, File destination)
		{
			boolean success = Util.downloadUrlToFile(LauncherUtils.httpClient, url, destination);

			if(success && file.size > 0)
			{
				totalBytesDownloaded.addAndGet(file.size);
			}

			return success;
		}
	}

	public void shutdown()
	{
		isShuttingDown = true;

		// Cancel all active tasks
		activeTasks.clear();

		// Shutdown executors with proper cleanup
		shutdownExecutor(speedCalculator, "Speed Calculator", 2);
		shutdownExecutor(downloadExecutor, "Download Executor", 5);
	}

	private void shutdownExecutor(ExecutorService executor, String name, int timeoutSeconds)
	{
		if(executor == null)
		{
			return;
		}

		executor.shutdown();
		try
		{
			if(!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS))
			{
				System.err.println(name + " didn't terminate gracefully, forcing shutdown");
				executor.shutdownNow();

				if(!executor.awaitTermination(2, TimeUnit.SECONDS))
				{
					System.err.println(name + " didn't terminate after forced shutdown");
				}
			}
		}
		catch(InterruptedException e)
		{
			System.err.println(name + " shutdown interrupted");
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}