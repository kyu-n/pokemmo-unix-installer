package com.pokeemu.unix.updater;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpResponse;
import java.nio.file.ReadOnlyFileSystemException;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.pokeemu.unix.LauncherUtils;
import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.IProgressReporter;
import com.pokeemu.unix.util.CryptoUtil;
import com.pokeemu.unix.util.Util;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class FeedManager
{
	public static final String[] DOWNLOAD_MIRRORS = {
			"https://dl.pokemmo.com/",
			"https://files.pokemmo.com/",
			"https://dl.pokemmo.download/"
	};

	private static final ReadWriteLock stateLock = new ReentrantReadWriteLock();
	private static final int MAX_RETRY_ATTEMPTS = 2;
	private static final long INITIAL_RETRY_DELAY_MS = 500;
	private static final long MAX_RETRY_DELAY_MS = 2000;
	private static final int HTTP_TIMEOUT_SECONDS = 10;
	private static final int TOTAL_TIMEOUT_SECONDS = 15;

	private static volatile boolean shutdownRequested = false;
	private static CompletableFuture<Boolean> currentLoadOperation = null;

	private enum LoadState
	{
		IDLE,
		LOADING,
		SUCCESS,
		FAILED
	}

	private static volatile LoadState loadState = LoadState.IDLE;
	private static int MIN_REVISION = 0;
	private static final List<UpdateFile> files = new ArrayList<>();
	private static Throwable lastException = null;
	private static String lastFailedMirror = null;
	private static final List<MirrorFailure> allFailures = new ArrayList<>();

	// Store raw feed data for transfer to game client
	private static volatile byte[] mainFeedRaw = null;
	private static volatile byte[] updateFeedRaw = null;
	private static volatile byte[] mainSignatureRaw = null;
	private static volatile byte[] updateSignatureRaw = null;

	private static class MirrorFailure
	{
		final String mirror;
		final Throwable exception;
		final FailureType type;
		final long timestamp;

		enum FailureType
		{
			NETWORK,
			VALIDATION,
			PARSING,
			OTHER
		}

		MirrorFailure(String mirror, Throwable exception)
		{
			this.mirror = mirror;
			this.exception = exception;
			this.timestamp = System.currentTimeMillis();
			this.type = categorizeException(exception);
		}

		private static FailureType categorizeException(Throwable e)
		{
			Throwable cause = unwrapException(e);

			if(cause instanceof ConnectException ||
					cause instanceof SocketTimeoutException ||
					cause instanceof UnknownHostException ||
					cause instanceof TimeoutException)
			{
				return FailureType.NETWORK;
			}
			else if(cause instanceof SecurityException)
			{
				return FailureType.VALIDATION;
			}
			else if(cause.getMessage() != null &&
					(cause.getMessage().contains("parse") ||
							cause.getMessage().contains("XML")))
			{
				return FailureType.PARSING;
			}
			return FailureType.OTHER;
		}

		boolean isRetryable()
		{
			return type == FailureType.NETWORK;
		}
	}

	private static class FeedData
	{
		final byte[] content;
		final byte[] signature;

		FeedData(byte[] content, byte[] signature)
		{
			this.content = content;
			this.signature = signature;
		}
	}

	public static void requestShutdown()
	{
		shutdownRequested = true;
		if(currentLoadOperation != null && !currentLoadOperation.isDone())
		{
			currentLoadOperation.cancel(true);
		}
	}

	public static void resetForRetry()
	{
		executeWithWriteLock(() -> {
			if(loadState == LoadState.LOADING)
			{
				throw new IllegalStateException("Cannot reset while feed loading is in progress");
			}
			clearState();
			shutdownRequested = false;
		});
	}

	public static void load(IProgressReporter progressReporter)
	{
		CompletableFuture<Boolean> loadFuture = loadAsync(progressReporter);

		try
		{
			loadFuture.get();
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
			executeWithWriteLock(() -> {
				loadState = LoadState.FAILED;
				lastException = e;
			});
		}
		catch(ExecutionException e)
		{
			executeWithWriteLock(() -> {
				loadState = LoadState.FAILED;
				lastException = e.getCause();
			});
		}
	}

	public static CompletableFuture<Boolean> loadAsync(IProgressReporter progressReporter)
	{
		return executeWithWriteLock(() -> {
			if(loadState == LoadState.LOADING && currentLoadOperation != null && !currentLoadOperation.isDone())
			{
				System.out.println("FeedManager.loadAsync() called while another load is in progress, returning existing operation");
				return currentLoadOperation;
			}

			if(loadState == LoadState.SUCCESS)
			{
				System.out.println("Feeds already loaded successfully, using cached results");
				return CompletableFuture.completedFuture(true);
			}

			loadState = LoadState.LOADING;
			clearState();
			shutdownRequested = false;

			currentLoadOperation = createLoadOperation(progressReporter);
			return currentLoadOperation;
		});
	}

	private static CompletableFuture<Boolean> createLoadOperation(IProgressReporter progressReporter)
	{
		return CompletableFuture.supplyAsync(() -> {
			String sig_format = "SHA256withRSA";
			PublicKey pub_key = CryptoUtil.getFeedsPublicKey();

			CompletableFuture<Boolean> result = tryAllMirrorsAsync(sig_format, pub_key, progressReporter);

			try
			{
				boolean success = result.get();

				executeWithWriteLock(() -> {
					if(success)
					{
						loadState = LoadState.SUCCESS;
						lastException = null;
					}
					else
					{
						loadState = LoadState.FAILED;
						if(!shutdownRequested)
						{
							handleAllMirrorsFailed(progressReporter);
						}
					}
				});

				return success;
			}
			catch(InterruptedException e)
			{
				Thread.currentThread().interrupt();
				executeWithWriteLock(() -> {
					loadState = LoadState.FAILED;
					lastException = e;
				});
				return false;
			}
			catch(ExecutionException e)
			{
				executeWithWriteLock(() -> {
					loadState = LoadState.FAILED;
					lastException = e.getCause();
				});
				return false;
			}
		});
	}

	private static CompletableFuture<Boolean> tryAllMirrorsAsync(String sig_format, PublicKey pub_key, IProgressReporter progressReporter)
	{
		CompletableFuture<Boolean> result = CompletableFuture.completedFuture(false);

		for(String mirror : DOWNLOAD_MIRRORS)
		{
			result = result.thenCompose(success -> {
				if(success || shutdownRequested || hasNonRetryableError())
				{
					return CompletableFuture.completedFuture(success);
				}
				return tryMirrorWithRetryAsync(mirror, sig_format, pub_key, progressReporter);
			});
		}

		return result;
	}

	private static CompletableFuture<Boolean> tryMirrorWithRetryAsync(String mirror, String sig_format,
																	  PublicKey pub_key, IProgressReporter progressReporter)
	{
		return tryMirrorAttemptAsync(mirror, sig_format, pub_key, progressReporter, 0);
	}

	private static CompletableFuture<Boolean> tryMirrorAttemptAsync(String mirror, String sig_format,
																	PublicKey pub_key, IProgressReporter progressReporter, int attempt)
	{
		if(shutdownRequested)
		{
			return CompletableFuture.completedFuture(false);
		}

		if(attempt > 0)
		{
			progressReporter.showInfo("status.networking.retry_attempt", mirror, attempt);
		}

		return CompletableFuture
				.supplyAsync(() -> tryMirror(mirror, sig_format, pub_key, progressReporter))
				.exceptionally(error -> {
					recordFailure(mirror, error);
					System.err.println("Mirror attempt failed for " + mirror + ": " + error.getMessage());
					error.printStackTrace();
					return false;
				})
				.thenCompose(success -> {
					if(success || shutdownRequested || attempt >= MAX_RETRY_ATTEMPTS)
					{
						return CompletableFuture.completedFuture(success);
					}

					MirrorFailure lastFailure = getLastFailure();
					if(lastFailure != null && !lastFailure.isRetryable())
					{
						return CompletableFuture.completedFuture(false);
					}

					long delay = calculateRetryDelay(attempt);

					CompletableFuture<Boolean> delayedRetry = new CompletableFuture<>();
					CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS).execute(() -> {
						if(shutdownRequested)
						{
							delayedRetry.complete(false);
						}
						else
						{
							tryMirrorAttemptAsync(mirror, sig_format, pub_key, progressReporter, attempt + 1)
									.whenComplete((result, ex) -> {
										if(ex != null)
										{
											delayedRetry.completeExceptionally(ex);
										}
										else
										{
											delayedRetry.complete(result);
										}
									});
						}
					});

					return delayedRetry;
				});
	}

	private static long calculateRetryDelay(int attempt)
	{
		long delay = INITIAL_RETRY_DELAY_MS * (1L << attempt);
		return Math.min(delay, MAX_RETRY_DELAY_MS);
	}

	private static void clearState()
	{
		loadState = LoadState.IDLE;
		files.clear();
		MIN_REVISION = 0;
		lastException = null;
		lastFailedMirror = null;
		allFailures.clear();
		currentLoadOperation = null;

		mainFeedRaw = null;
		updateFeedRaw = null;
		mainSignatureRaw = null;
		updateSignatureRaw = null;
	}

	private static boolean hasNonRetryableError()
	{
		return executeWithReadLock(() -> {
			for(MirrorFailure failure : allFailures)
			{
				if(failure.type == MirrorFailure.FailureType.OTHER &&
						isSystemError(failure.exception))
				{
					return true;
				}
			}
			return false;
		});
	}

	private static boolean isSystemError(Throwable exception)
	{
		Throwable cause = exception;
		while(cause != null)
		{
			if(cause instanceof java.nio.file.ReadOnlyFileSystemException ||
					cause instanceof java.io.IOException)
			{
				return true;
			}
			cause = cause.getCause();
		}
		return false;
	}

	private static void recordFailure(String mirror, Throwable exception)
	{
		MirrorFailure failure = new MirrorFailure(mirror, exception);
		executeWithWriteLock(() -> {
			allFailures.add(failure);
			lastException = exception;
			lastFailedMirror = mirror;
		});
	}

	private static MirrorFailure getLastFailure()
	{
		return executeWithReadLock(() ->
				allFailures.isEmpty() ? null : allFailures.getLast()
		);
	}

	private static boolean tryMirror(String mirror, String sig_format, PublicKey pub_key,
									 IProgressReporter progressReporter)
	{
		try
		{
			if(shutdownRequested)
			{
				return false;
			}

			String baseUrl = mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/";

			FeedData mainFeed = downloadAndVerifyFeed(baseUrl, "main_feed", sig_format, pub_key, progressReporter, mirror);
			if(mainFeed == null) return false;

			if(shutdownRequested)
			{
				return false;
			}

			FeedData updateFeed = downloadAndVerifyFeed(baseUrl, "update_feed", sig_format, pub_key, progressReporter, mirror);
			if(updateFeed == null) return false;

			if(shutdownRequested)
			{
				return false;
			}

			return processFeedData(mainFeed, updateFeed);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			progressReporter.showInfo("status.networking.feed_load_failed_alt", mirror);
			recordFailure(mirror, e);
			return false;
		}
	}

	private static FeedData downloadAndVerifyFeed(String baseUrl, String feedName, String sig_format,
												  PublicKey pub_key, IProgressReporter progressReporter,
												  String mirror) throws Exception
	{
		if(shutdownRequested)
		{
			return null;
		}

		CompletableFuture<HttpResponse<InputStream>> feedResponse =
				Util.getUrlAsync(LauncherUtils.httpClient, baseUrl + feedName + ".txt")
						.orTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		CompletableFuture<HttpResponse<InputStream>> signatureResponse =
				Util.getUrlAsync(LauncherUtils.httpClient, baseUrl + feedName + ".sig256")
						.orTimeout(HTTP_TIMEOUT_SECONDS, TimeUnit.SECONDS);

		CompletableFuture<Void> allFutures = CompletableFuture.allOf(feedResponse, signatureResponse);

		try
		{
			allFutures.get(TOTAL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		}
		catch(TimeoutException | CompletionException | ExecutionException e)
		{
			feedResponse.cancel(true);
			signatureResponse.cancel(true);

			String errorType = getErrorType(e);
			progressReporter.showInfo("status.networking.feed_load_failed_alt", mirror, errorType);
			recordFailure(mirror, unwrapException(e));
			return null;
		}

		if(shutdownRequested)
		{
			return null;
		}

		HttpResponse<InputStream> feedResp = feedResponse.get();
		HttpResponse<InputStream> sigResp = signatureResponse.get();

		if(feedResp == null || sigResp == null)
		{
			progressReporter.showInfo("status.networking.feed_load_failed_alt", mirror, "NULL_RESPONSE");
			recordFailure(mirror, new IOException("Null response from server"));
			return null;
		}

		byte[] feedRaw, sigRaw;
		try(InputStream feedIs = feedResp.body();
			InputStream sigIs = sigResp.body())
		{
			if(feedIs == null || sigIs == null)
			{
				progressReporter.showInfo("status.networking.feed_load_failed_alt", mirror, "NULL_BODY");
				recordFailure(mirror, new IOException("Null response body from server"));
				return null;
			}

			feedRaw = feedIs.readAllBytes();
			sigRaw = sigIs.readAllBytes();
		}

		if(!CryptoUtil.verifySignature(feedRaw, sigRaw, pub_key, sig_format))
		{
			System.out.println(feedName + " failed verification");
			progressReporter.showInfo("status.networking.feed_load_failed_validation", mirror);

			SecurityException ex = new SecurityException(feedName + " signature verification failed for mirror: " + mirror);
			recordFailure(mirror, ex);
			return null;
		}

		return new FeedData(feedRaw, sigRaw);
	}

	private static String getErrorType(Exception e)
	{
		if(e instanceof TimeoutException) return "TIMEOUT";
		if(e instanceof CompletionException) return "NETWORK_ERROR";
		Throwable cause = unwrapException(e);
		return cause.getClass().getSimpleName();
	}

	private static Throwable unwrapException(Throwable e)
	{
		if(e instanceof CompletionException || e instanceof ExecutionException)
		{
			return e.getCause() != null ? e.getCause() : e;
		}
		return e;
	}

	private static boolean processFeedData(FeedData mainFeed, FeedData updateFeed) throws Exception
	{
		Document mainDoc = parseXmlDocument(new String(mainFeed.content));
		Element main_feed = (Element) mainDoc.getElementsByTagName("main_feed").item(0);

		int tempRevision = 0;
		if(main_feed.getElementsByTagName("min_revision").getLength() > 0)
		{
			tempRevision = Integer.parseInt(main_feed.getElementsByTagName("min_revision").item(0).getTextContent());
		}

		Document updateDoc = parseXmlDocument(new String(updateFeed.content));
		Element update_feed = (Element) updateDoc.getElementsByTagName("update_feed").item(0);

		List<UpdateFile> tempFiles = parseUpdateFiles(update_feed);

		if(!tempFiles.isEmpty())
		{
			int finalRevision = tempRevision;
			executeWithWriteLock(() -> {
				MIN_REVISION = finalRevision;
				files.clear();
				files.addAll(tempFiles);

				mainFeedRaw = mainFeed.content;
				updateFeedRaw = updateFeed.content;
				mainSignatureRaw = mainFeed.signature;
				updateSignatureRaw = updateFeed.signature;
			});
			return true;
		}

		return false;
	}

	private static Document parseXmlDocument(String xmlContent) throws Exception
	{
		DocumentBuilder db = getSecureDocumentBuilderFactory().newDocumentBuilder();
		InputSource is = new InputSource(new StringReader(xmlContent));
		return db.parse(is);
	}

	private static List<UpdateFile> parseUpdateFiles(Element update_feed)
	{
		List<UpdateFile> tempFiles = new ArrayList<>();
		File current_directory = new File(".");

		NodeList filesNodeList = update_feed.getElementsByTagName("file");
		for(int x = 0; x < filesNodeList.getLength(); x++)
		{
			Node fileT = filesNodeList.item(x);
			if(fileT.getNodeType() == Node.ELEMENT_NODE)
			{
				Element file = (Element) fileT;
				String sanitized = Util.sanitize(current_directory, file.getAttribute("name"));

				if(sanitized != null && file.hasAttribute("sha256"))
				{
					boolean only_if_not_exists = parseBoolean(file.getAttribute("only_if_not_exists"));

					UpdateFile f = new UpdateFile(sanitized, file.getAttribute("sha256"),
							file.getAttribute("size"), only_if_not_exists);
					tempFiles.add(f);
				}
			}
		}

		return tempFiles;
	}

	private static boolean parseBoolean(String value)
	{
		try
		{
			return Boolean.parseBoolean(value);
		}
		catch(Exception ignored)
		{
			return false;
		}
	}

	private static DocumentBuilderFactory getSecureDocumentBuilderFactory() throws ParserConfigurationException
	{
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
		dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		dbf.setXIncludeAware(false);
		dbf.setExpandEntityReferences(false);
		return dbf;
	}

	private static void handleAllMirrorsFailed(IProgressReporter progressReporter)
	{
		if(!(progressReporter instanceof HeadlessProgressReporter))
		{
			progressReporter.showError(
					Config.getString("status.networking.feed_load_failed"),
					Config.getString("status.title.fatal_error"),
					() -> System.exit(UnixInstaller.EXIT_CODE_NETWORK_FAILURE)
			);
		}
	}

	public static boolean hasFeedData()
	{
		return executeWithReadLock(() -> mainFeedRaw != null && updateFeedRaw != null);
	}

	public static byte[] getMainFeedRaw()
	{
		return executeWithReadLock(() -> mainFeedRaw);
	}

	public static byte[] getUpdateFeedRaw()
	{
		return executeWithReadLock(() -> updateFeedRaw);
	}

	public static byte[] getMainSignatureRaw()
	{
		return executeWithReadLock(() -> mainSignatureRaw);
	}

	public static byte[] getUpdateSignatureRaw()
	{
		return executeWithReadLock(() -> updateSignatureRaw);
	}

	// Create a socket server for feed data transfer
	public static FeedSocketServer createFeedSocketServer()
	{
		return executeWithReadLock(() -> {
			if(!hasFeedData())
			{
				return null;
			}

			return new FeedSocketServer(
					mainFeedRaw,
					updateFeedRaw,
					mainSignatureRaw,
					updateSignatureRaw
			);
		});
	}

	private static <T> T executeWithReadLock(java.util.function.Supplier<T> action)
	{
		stateLock.readLock().lock();
		try
		{
			return action.get();
		}
		finally
		{
			stateLock.readLock().unlock();
		}
	}

	private static void executeWithReadLock(Runnable action)
	{
		stateLock.readLock().lock();
		try
		{
			action.run();
		}
		finally
		{
			stateLock.readLock().unlock();
		}
	}

	private static <T> T executeWithWriteLock(java.util.function.Supplier<T> action)
	{
		stateLock.writeLock().lock();
		try
		{
			return action.get();
		}
		finally
		{
			stateLock.writeLock().unlock();
		}
	}

	private static void executeWithWriteLock(Runnable action)
	{
		stateLock.writeLock().lock();
		try
		{
			action.run();
		}
		finally
		{
			stateLock.writeLock().unlock();
		}
	}

	public static List<UpdateFile> getFiles()
	{
		return executeWithReadLock(() -> new ArrayList<>(files));
	}

	public static Throwable getLastException()
	{
		return executeWithReadLock(() -> lastException);
	}

	public static boolean isSuccessful()
	{
		return executeWithReadLock(() -> loadState == LoadState.SUCCESS);
	}

	public static int getMinRevision()
	{
		return executeWithReadLock(() -> MIN_REVISION);
	}

	public static String getLastExceptionDetails()
	{
		return executeWithReadLock(() -> {
			if(lastException == null && allFailures.isEmpty())
			{
				return "No exception details available";
			}

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);

			if(lastException != null)
			{
				pw.println("Last Failed Mirror: " + (lastFailedMirror != null ? lastFailedMirror : "Unknown"));
				pw.println("Exception Type: " + lastException.getClass().getName());
				pw.println("Message: " + lastException.getMessage());
				pw.println("\nStack Trace:");
				lastException.printStackTrace(pw);
			}

			if(!allFailures.isEmpty())
			{
				pw.println("\n\nAll Mirror Failures:");
				pw.println("====================");
				int i = 1;

				for(MirrorFailure failure : allFailures)
				{
					pw.println("\nMirror " + i + ": " + failure.mirror);
					pw.println("  Time: " + new Date(failure.timestamp));
					pw.println("  Type: " + failure.type);
					pw.println("  Exception: " + failure.exception.getClass().getName());
					pw.println("  Message: " + failure.exception.getMessage());
					pw.println("  Retryable: " + failure.isRetryable());
					i++;
				}
			}

			pw.close();
			try
			{
				sw.close();
			}
			catch(Exception e)
			{
			}

			return sw.toString();
		});
	}

	public static class HeadlessProgressReporter implements IProgressReporter
	{
		@Override
		public void setStatus(String message, int progressValue)
		{
			System.out.println("[" + progressValue + "%] " + message);
		}

		@Override
		public void addDetail(String messageKey, int progressValue, Object... params)
		{
			String formatted = Config.getString(messageKey, params);
			System.out.println("[" + progressValue + "%] " + formatted);
		}

		@Override
		public void showInfo(String messageKey, Object... params)
		{
			String formatted = Config.getString(messageKey, params);
			System.out.println("INFO: " + formatted);
		}

		@Override
		public void showError(String message, String title, Runnable onClose)
		{
			System.err.println("ERROR: " + title + " - " + message);
		}

		@Override
		public void setDownloadSpeed(String speed)
		{
		}
	}
}