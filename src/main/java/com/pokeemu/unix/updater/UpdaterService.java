package com.pokeemu.unix.updater;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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
	private final ProgressTracker progress;
	private final TempFileManager tempFiles;

	private final Set<Integer> disabledMirrors = Collections.synchronizedSet(new HashSet<>());
	private volatile boolean isShuttingDown = false;

	static
	{
		TempFileManager.cleanupOrphanedFiles();
	}

	public UpdaterService(UnixInstaller parent, IProgressReporter progressReporter)
	{
		this.parent = parent;
		this.progressReporter = progressReporter;
		this.downloadExecutor = Executors.newFixedThreadPool(Config.NETWORK_THREADS);
		this.speedCalculator = Executors.newSingleThreadScheduledExecutor();
		this.progress = new ProgressTracker(progressReporter);
		this.tempFiles = new TempFileManager();

		speedCalculator.scheduleAtFixedRate(this::updateSpeed, 0, 1, TimeUnit.SECONDS);
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
				if(clean) performCleanInstall();
				if(repair) performRepair();
				else performUpdate();
			}
			catch(Exception e)
			{
				e.printStackTrace();
				progressReporter.showError("Update failed: " + e.getMessage(), "Update Error",
						() -> parent.setUpdating(false));
			}
		});
	}

	private void performCleanInstall() throws IOException
	{
		progressReporter.setStatus(Config.getString("status.cleaning"), 10);
		Path installPath = Path.of(parent.getPokemmoDir().getAbsolutePath());

		if(Files.exists(installPath))
		{
			deleteDirectoryRecursively(installPath);
		}

		LauncherUtils.createPokemmoDir();
		parent.createSymlinkedDirectories();
	}

	private void performRepair()
	{
		clearCaches();
		progressReporter.setStatus(Config.getString("status.game_repair"), 30);

		List<UpdateFile> toRepair = findFilesToRepair();
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

		List<UpdateFile> toDownload = findFilesToUpdate();
		if(!toDownload.isEmpty())
		{
			downloadFiles(toDownload);
		}
		else
		{
			finishUpdate();
		}
	}

	private List<UpdateFile> findFilesToRepair()
	{
		return findFilesNeedingDownload(true);
	}

	private List<UpdateFile> findFilesToUpdate()
	{
		return findFilesNeedingDownload(false);
	}

	private List<UpdateFile> findFilesNeedingDownload(boolean isRepair)
	{
		List<UpdateFile> result = new ArrayList<>();
		int totalFiles = FeedManager.getFiles().size();
		int counter = 0;

		for(UpdateFile file : FeedManager.getFiles())
		{
			if(!file.shouldDownload() || LauncherUtils.isNativeLibraryForOtherPlatform(file.name))
			{
				continue;
			}

			File f = LauncherUtils.getFile(file.name);

			if(file.only_if_not_exists && f.exists())
			{
				continue;
			}

			if(!ensureParentDirectory(f))
			{
				return result; // Error already reported
			}

			String actualHash = Util.calculateHash("SHA-256", f);
			if(!file.sha256.equalsIgnoreCase(actualHash))
			{
				if(isRepair)
				{
					progressReporter.addDetail("status.files.repairing",
							(counter * 100) / totalFiles, file.name);
				}
				result.add(file);
			}
			counter++;
		}

		return result;
	}

	private boolean ensureParentDirectory(File file)
	{
		File parentDir = file.getParentFile();
		if(!parentDir.exists() && !parentDir.mkdirs())
		{
			progressReporter.showError(
					Config.getString("error.dir_not_accessible", parentDir, "DIR_8"), "", null);
			this.parent.setUpdating(false);
			return false;
		}
		return true;
	}

	private void downloadFiles(List<UpdateFile> files)
	{
		progressReporter.setStatus(Config.getString("status.downloading"), 30);

		progress.reset(files);
		disabledMirrors.clear();

		CountDownLatch latch = new CountDownLatch(files.size());

		for(UpdateFile file : files)
		{
			if(isShuttingDown)
			{
				latch.countDown();
				continue;
			}

			downloadExecutor.submit(new DownloadTask(file, latch));
		}

		try
		{
			latch.await();
			progress.markComplete();
			finishUpdate();
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
			progressReporter.showError("Download interrupted", "Error", null);
			parent.setUpdating(false);
		}
	}

	private void finishUpdate()
	{
		progressReporter.setStatus(Config.getString("status.game_verified"), 90);

		try
		{
			Thread.sleep(200); // Brief pause for UI
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}

		progressReporter.setStatus(Config.getString("status.ready"), 100);
		parent.setUpdating(false);
		parent.getThreadBridge().asyncExec(() -> parent.getMainWindow().setCanStart(true));
	}

	private void updateSpeed()
	{
		if(!isShuttingDown)
		{
			String speed = progress.calculateSpeed();
			progressReporter.setDownloadSpeed(speed);
			progress.updateOverallProgress();
		}
	}

	private void clearCaches()
	{
		progressReporter.setStatus(Config.getString("status.delete_caches"), 20);

		clearDirectory(new File(LauncherUtils.getPokemmoDir() + "/cache"), false);
		clearPropertiesFiles(new File(LauncherUtils.getPokemmoDir() + "/config"));
	}

	private void clearDirectory(File dir, boolean deleteDir)
	{
		if(!dir.exists() || !dir.isDirectory()) return;

		try(var pathStream = Files.walk(dir.toPath()))
		{
			pathStream.sorted(Comparator.reverseOrder())
					.filter(path -> deleteDir || !path.equals(dir.toPath()))
					.forEach(this::deleteQuietly);
		}
		catch(IOException e)
		{
			System.err.println("Error clearing directory: " + e.getMessage());
		}
	}

	private void clearPropertiesFiles(File dir)
	{
		if(!dir.exists() || !dir.isDirectory()) return;

		try(var pathStream = Files.list(dir.toPath()))
		{
			pathStream.filter(p -> p.toString().endsWith(".properties"))
					.forEach(this::deleteQuietly);
		}
		catch(IOException e)
		{
			System.err.println("Error clearing properties files: " + e.getMessage());
		}
	}

	private void deleteDirectoryRecursively(Path path) throws IOException
	{
		try(var pathStream = Files.walk(path))
		{
			pathStream.sorted(Comparator.reverseOrder()).forEach(p -> {
				try
				{
					Files.delete(p);
					progressReporter.showInfo("status.deleted_file", p.getFileName());
				}
				catch(IOException e)
				{
					progressReporter.showInfo("status.failed_delete", p.getFileName());
				}
			});
		}
	}

	private void deleteQuietly(Path path)
	{
		try
		{
			Files.delete(path);
		}
		catch(IOException ignored)
		{
		}
	}

	public void shutdown()
	{
		isShuttingDown = true;
		tempFiles.cleanup();
		shutdownExecutor(speedCalculator, "Speed Calculator", 2);
		shutdownExecutor(downloadExecutor, "Download Executor", 5);
	}

	private void shutdownExecutor(ExecutorService executor, String name, int timeoutSeconds)
	{
		if(executor == null || executor.isShutdown()) return;

		executor.shutdown();
		try
		{
			if(!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS))
			{
				System.err.println(name + " didn't terminate gracefully");
				executor.shutdownNow();
				executor.awaitTermination(2, TimeUnit.SECONDS);
			}
		}
		catch(InterruptedException e)
		{
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private class DownloadTask implements Runnable
	{
		private final UpdateFile file;
		private final CountDownLatch latch;

		DownloadTask(UpdateFile file, CountDownLatch latch)
		{
			this.file = file;
			this.latch = latch;
		}

		@Override
		public void run()
		{
			try
			{
				if(isShuttingDown) return;

				progressReporter.addDetail("status.files.downloading", -1, file.name);

				if(downloadFile())
				{
					progress.markFileComplete(file);
				}
				else
				{
					progressReporter.showError(
							Config.getString("error.download_error", file.name),
							"Download Failed", null);
				}
			}
			finally
			{
				latch.countDown();
			}
		}

		private boolean downloadFile()
		{
			Path targetPath = LauncherUtils.getFile(file.name).toPath();

			for(int mirror = 0; mirror < FeedManager.DOWNLOAD_MIRRORS.length; mirror++)
			{
				if(isShuttingDown || disabledMirrors.contains(mirror)) continue;

				String url = buildDownloadUrl(mirror);
				Path tempFile = null;

				try
				{
					tempFile = tempFiles.createTempFile(targetPath);

					if(!Util.downloadUrlToFile(LauncherUtils.httpClient, url, tempFile.toFile()))
					{
						handleMirrorFailure(mirror, "Download failed");
						continue;
					}

					String actualHash = Util.calculateHash("SHA-256", tempFile.toFile());
					if(!file.sha256.equalsIgnoreCase(actualHash))
					{
						handleMirrorFailure(mirror, "Checksum mismatch");
						continue;
					}

					moveFile(tempFile, targetPath);
					progress.addDownloadedBytes(tempFile.toFile().length());
					return true;
				}
				catch(IOException e)
				{
					reportFatalError(tempFile, targetPath);
					return false;
				}
				finally
				{
					tempFiles.deleteFile(tempFile);
				}
			}
			return false;
		}

		private String buildDownloadUrl(int mirrorIndex)
		{
			return FeedManager.DOWNLOAD_MIRRORS[mirrorIndex] + "/" +
					Config.UPDATE_CHANNEL + "/current/client/" +
					file.name + "?v=" + file.getCacheBuster();
		}

		private void moveFile(Path source, Path target) throws IOException
		{
			try
			{
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING,
						StandardCopyOption.ATOMIC_MOVE);
			}
			catch(IOException e)
			{
				Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
		}

		private void handleMirrorFailure(int mirror, String reason)
		{
			progressReporter.showInfo("status.files.failed_download", file.name, mirror);
			disabledMirrors.add(mirror);
		}

		private void reportFatalError(Path tempFile, Path targetPath)
		{
			progressReporter.showError(
					Config.getString("status.title.fatal_error",
							tempFile != null ? tempFile : "null", targetPath),
					Config.getString("status.title.fatal_error"), null);
		}
	}
}

class ProgressTracker
{
	private final IProgressReporter reporter;
	private final AtomicInteger completedFiles = new AtomicInteger(0);
	private final AtomicLong totalBytes = new AtomicLong(0);
	private final AtomicLong downloadedBytes = new AtomicLong(0);
	private final AtomicLong lastBytes = new AtomicLong(0);

	private volatile int totalFiles = 0;
	private volatile long lastSpeedCalc = System.currentTimeMillis();
	private volatile boolean isComplete = false;

	ProgressTracker(IProgressReporter reporter)
	{
		this.reporter = reporter;
	}

	void reset(List<UpdateFile> files)
	{
		completedFiles.set(0);
		totalBytes.set(0);
		downloadedBytes.set(0);
		lastBytes.set(0);
		totalFiles = files.size();
		isComplete = false;

		for(UpdateFile file : files)
		{
			long size = file.hasSizeForProgress() ? file.size : 1024 * 1024;
			totalBytes.addAndGet(size);
		}
	}

	void markFileComplete(UpdateFile file)
	{
		completedFiles.incrementAndGet();
	}

	void addDownloadedBytes(long bytes)
	{
		downloadedBytes.addAndGet(bytes);
	}

	void markComplete()
	{
		isComplete = true;
	}

	void updateOverallProgress()
	{
		if(isComplete || totalFiles == 0 || totalBytes.get() == 0) return;

		double byteProgress = (double) downloadedBytes.get() / totalBytes.get();
		double fileProgress = (double) completedFiles.get() / totalFiles;
		double combined = (byteProgress * 0.7) + (fileProgress * 0.3);

		int progress = 30 + (int)(combined * 60); // 30-90% range
		progress = Math.min(progress, 89);

		reporter.setStatus(Config.getString("status.downloading"), progress);
	}

	String calculateSpeed()
	{
		long current = downloadedBytes.get();
		long delta = current - lastBytes.get();
		long now = System.currentTimeMillis();
		long timeDiff = now - lastSpeedCalc;

		if(timeDiff > 0)
		{
			double bytesPerSec = (delta * 1000.0) / timeDiff;
			lastBytes.set(current);
			lastSpeedCalc = now;
			return formatSpeed(bytesPerSec);
		}
		return "0 B/s";
	}

	private String formatSpeed(double bytesPerSec)
	{
		if(bytesPerSec < 1024) return String.format("%.0f B/s", bytesPerSec);
		if(bytesPerSec < 1024 * 1024) return String.format("%.1f KB/s", bytesPerSec / 1024);
		return String.format("%.1f MB/s", bytesPerSec / (1024 * 1024));
	}
}

class TempFileManager
{
	private static final String TEMP_PREFIX = "pokemmo_download_";
	private static final String TEMP_SUFFIX = ".tmp";
	private final Set<Path> activeFiles = Collections.synchronizedSet(new HashSet<>());

	static void cleanupOrphanedFiles()
	{
		try
		{
			LauncherUtils.setupDirectories();
			File dir = new File(LauncherUtils.getPokemmoDir());
			if(dir.exists())
			{
				cleanupTempFiles(dir);
			}
		}
		catch(Exception e)
		{
			System.err.println("Failed to cleanup temp files: " + e.getMessage());
		}
	}

	private static void cleanupTempFiles(File dir)
	{
		File[] temps = dir.listFiles((d, name) ->
				name.startsWith(TEMP_PREFIX) && name.endsWith(TEMP_SUFFIX));

		if(temps != null)
		{
			for(File temp : temps)
			{
				if(temp.delete())
				{
					System.out.println("Cleaned up temp file: " + temp.getName());
				}
			}
		}

		File[] subdirs = dir.listFiles(File::isDirectory);
		if(subdirs != null)
		{
			for(File subdir : subdirs)
			{
				cleanupTempFiles(subdir);
			}
		}
	}

	Path createTempFile(Path target) throws IOException
	{
		Files.createDirectories(target.getParent());

		String name = TEMP_PREFIX +
				target.getFileName().toString().replace("/", "_") + "_" +
				Integer.toHexString(target.hashCode() & 0x7FFFFFFF) + "_" +
				System.currentTimeMillis() + TEMP_SUFFIX;

		Path temp = target.getParent().resolve(name);
		activeFiles.add(temp);
		return temp;
	}

	void deleteFile(Path file)
	{
		if(file != null)
		{
			try
			{
				Files.deleteIfExists(file);
				activeFiles.remove(file);
			}
			catch(IOException ignored)
			{
			}
		}
	}

	void cleanup()
	{
		for(Path file : activeFiles)
		{
			deleteFile(file);
		}
		activeFiles.clear();
	}
}