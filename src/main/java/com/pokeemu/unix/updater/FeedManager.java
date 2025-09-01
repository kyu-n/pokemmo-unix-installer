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

	public static void resetForRetry()
	{
		executeWithWriteLock(() -> {
			if(loadState == LoadState.LOADING)
			{
				throw new IllegalStateException("Cannot reset while feed loading is in progress");
			}
			clearState();
		});
	}

	public static void load(IProgressReporter progressReporter)
	{
		executeWithWriteLock(() -> {
			if(loadState == LoadState.LOADING)
			{
				System.out.println("FeedManager.load() called while another load is in progress, skipping");
				return;
			}

			if(loadState == LoadState.SUCCESS)
			{
				System.out.println("Feeds already loaded successfully, using cached results");
				return;
			}

			loadState = LoadState.LOADING;
			clearState();
		});

		boolean success = false;
		try
		{
			String sig_format = "SHA256withRSA";
			PublicKey pub_key = CryptoUtil.getFeedsPublicKey();

			for(String mirror : DOWNLOAD_MIRRORS)
			{
				if(hasNonRetryableError())
				{
					break;
				}

				if(tryMirrorWithRetry(mirror, sig_format, pub_key, progressReporter))
				{
					success = true;
					break;
				}
			}

			boolean finalSuccess = success;
			executeWithWriteLock(() -> {
				if(finalSuccess)
				{
					loadState = LoadState.SUCCESS;
					lastException = null;
				}
				else
				{
					loadState = LoadState.FAILED;
					handleAllMirrorsFailed(progressReporter);
				}
			});
		}
		catch(Exception e)
		{
			executeWithWriteLock(() -> {
				loadState = LoadState.FAILED;
				lastException = e;
			});
		}
	}

	private static void clearState()
	{
		loadState = LoadState.IDLE;
		files.clear();
		MIN_REVISION = 0;
		lastException = null;
		lastFailedMirror = null;
		allFailures.clear();
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

	private static boolean tryMirrorWithRetry(String mirror, String sig_format, PublicKey pub_key,
											  IProgressReporter progressReporter)
	{
		int attempt = 0;
		long retryDelay = INITIAL_RETRY_DELAY_MS;

		while(attempt <= MAX_RETRY_ATTEMPTS)
		{
			try
			{
				if(attempt > 0)
				{
					retryDelay = Math.min(retryDelay * 2, MAX_RETRY_DELAY_MS);
					sleepInterruptibly(retryDelay);
					progressReporter.showInfo("status.networking.retry_attempt", mirror, attempt);
				}

				if(tryMirror(mirror, sig_format, pub_key, progressReporter))
				{
					return true;
				}

				MirrorFailure lastFailure = getLastFailure();
				if(lastFailure != null && !lastFailure.isRetryable())
				{
					break;
				}

				attempt++;
			}
			catch(Exception e)
			{
				e.printStackTrace();
				recordFailure(mirror, e);
				break;
			}
		}

		return false;
	}

	private static MirrorFailure getLastFailure()
	{
		return executeWithReadLock(() ->
				allFailures.isEmpty() ? null : allFailures.getLast()
		);
	}

	private static void sleepInterruptibly(long millis) throws InterruptedException
	{
		Thread.sleep(millis);
	}

	private static boolean tryMirror(String mirror, String sig_format, PublicKey pub_key,
									 IProgressReporter progressReporter)
	{
		try
		{
			String baseUrl = mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/";

			FeedData mainFeed = downloadAndVerifyFeed(baseUrl, "main_feed", sig_format, pub_key, progressReporter, mirror);
			if(mainFeed == null) return false;

			FeedData updateFeed = downloadAndVerifyFeed(baseUrl, "update_feed", sig_format, pub_key, progressReporter, mirror);
			if(updateFeed == null) return false;

			return processFeedData(mainFeed.content, updateFeed.content);
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

		byte[] feedRaw, sigRaw;
		try(InputStream feedIs = feedResponse.get().body();
			InputStream sigIs = signatureResponse.get().body())
		{
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

	private static boolean processFeedData(byte[] mainFeedRaw, byte[] updateFeedRaw) throws Exception
	{
		Document mainDoc = parseXmlDocument(new String(mainFeedRaw));
		Element main_feed = (Element) mainDoc.getElementsByTagName("main_feed").item(0);

		int tempRevision = 0;
		if(main_feed.getElementsByTagName("min_revision").getLength() > 0)
		{
			tempRevision = Integer.parseInt(main_feed.getElementsByTagName("min_revision").item(0).getTextContent());
		}

		Document updateDoc = parseXmlDocument(new String(updateFeedRaw));
		Element update_feed = (Element) updateDoc.getElementsByTagName("update_feed").item(0);

		List<UpdateFile> tempFiles = parseUpdateFiles(update_feed);

		if(!tempFiles.isEmpty())
		{
			int finalRevision = tempRevision;
			executeWithWriteLock(() -> {
				MIN_REVISION = finalRevision;
				files.clear();
				files.addAll(tempFiles);
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