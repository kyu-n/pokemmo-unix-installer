package com.pokeemu.unix.updater;

import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpResponse;
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

	private static final int MAX_RETRY_ATTEMPTS = 2;
	private static final long INITIAL_RETRY_DELAY_MS = 500;
	private static final long MAX_RETRY_DELAY_MS = 2000;

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
			Throwable cause = e;
			if(e instanceof CompletionException && e.getCause() != null)
			{
				cause = e.getCause();
			}

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

	public static void resetForRetry()
	{
		stateLock.writeLock().lock();
		try
		{
			if(loadState == LoadState.LOADING)
			{
				throw new IllegalStateException("Cannot reset while feed loading is in progress");
			}

			loadState = LoadState.IDLE;
			files.clear();
			MIN_REVISION = 0;
			lastException = null;
			lastFailedMirror = null;
			allFailures.clear();
		}
		finally
		{
			stateLock.writeLock().unlock();
		}
	}

	public static void load(IProgressReporter progressReporter)
	{
		stateLock.writeLock().lock();

		try
		{
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
			files.clear();
			MIN_REVISION = 0;
			lastException = null;
			lastFailedMirror = null;
			allFailures.clear();
		}
		finally
		{
			stateLock.writeLock().unlock();
		}

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

				boolean mirrorSuccess = tryMirrorWithRetry(mirror, sig_format, pub_key, progressReporter);

				if(mirrorSuccess)
				{
					success = true;
					break;
				}
			}

			stateLock.writeLock().lock();

			try
			{
				if(success)
				{
					loadState = LoadState.SUCCESS;
					lastException = null;
				}
				else
				{
					loadState = LoadState.FAILED;
					handleAllMirrorsFailed(progressReporter);
				}
			}
			finally
			{
				stateLock.writeLock().unlock();
			}
		}
		catch(Exception e)
		{
			stateLock.writeLock().lock();
			try
			{
				loadState = LoadState.FAILED;
				lastException = e;
			}
			finally
			{
				stateLock.writeLock().unlock();
			}
		}
	}

	private static boolean hasNonRetryableError()
	{
		stateLock.readLock().lock();

		try
		{
			for(MirrorFailure failure : allFailures)
			{
				if(failure.type == MirrorFailure.FailureType.OTHER)
				{
					String message = failure.exception.getMessage();
					if(message != null)
					{
						String lowerMessage = message.toLowerCase();
						if(lowerMessage.contains("disk") ||
								lowerMessage.contains("space") ||
								lowerMessage.contains("permission") ||
								lowerMessage.contains("access denied"))
						{
							return true;
						}
					}
				}
			}
		}
		finally
		{
			stateLock.readLock().unlock();
		}

		return false;
	}

	private static void recordFailure(String mirror, Throwable exception)
	{
		MirrorFailure failure = new MirrorFailure(mirror, exception);
		stateLock.writeLock().lock();
		try
		{
			allFailures.add(failure);
			lastException = exception;
			lastFailedMirror = mirror;
		}
		finally
		{
			stateLock.writeLock().unlock();
		}
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
					try
					{
						retryDelay *= 2;
						Thread.sleep(Math.min(retryDelay, MAX_RETRY_DELAY_MS));
					}
					catch(InterruptedException e)
					{
						Thread.currentThread().interrupt();
						return false;
					}

					progressReporter.showInfo("status.networking.retry_attempt", mirror, attempt);
				}

				boolean success = tryMirror(mirror, sig_format, pub_key, progressReporter);
				if(success)
				{
					return true;
				}

				MirrorFailure lastFailure = null;
				stateLock.readLock().lock();
				try
				{
					if(!allFailures.isEmpty())
					{
						lastFailure = allFailures.getLast();
					}
				}
				finally
				{
					stateLock.readLock().unlock();
				}

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

	private static boolean tryMirror(String mirror, String sig_format, PublicKey pub_key,
									 IProgressReporter progressReporter)
	{
		try
		{
			CompletableFuture<HttpResponse<InputStream>> mainFeedResponse =
					Util.getUrlAsync(LauncherUtils.httpClient, mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/main_feed.txt")
							.orTimeout(10, TimeUnit.SECONDS);
			CompletableFuture<HttpResponse<InputStream>> signatureResponse =
					Util.getUrlAsync(LauncherUtils.httpClient, mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/main_feed.sig256")
							.orTimeout(10, TimeUnit.SECONDS);
			CompletableFuture<HttpResponse<InputStream>> updateFeedResponse =
					Util.getUrlAsync(LauncherUtils.httpClient, mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/update_feed.txt")
							.orTimeout(10, TimeUnit.SECONDS);
			CompletableFuture<HttpResponse<InputStream>> updateSignatureResponse =
					Util.getUrlAsync(LauncherUtils.httpClient, mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/update_feed.sig256")
							.orTimeout(10, TimeUnit.SECONDS);

			CompletableFuture<Void> allFutures = CompletableFuture.allOf(
					mainFeedResponse, signatureResponse, updateFeedResponse, updateSignatureResponse);

			try
			{
				allFutures.get(15, TimeUnit.SECONDS);
			}
			catch(TimeoutException te)
			{
				mainFeedResponse.cancel(true);
				signatureResponse.cancel(true);
				updateFeedResponse.cancel(true);
				updateSignatureResponse.cancel(true);

				progressReporter.showInfo("status.networking.feed_load_failed_validation", mirror, "TIMEOUT");
				recordFailure(mirror, te);
				return false;
			}
			catch(CompletionException ce)
			{
				Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
				progressReporter.showInfo("status.networking.feed_load_failed_validation", mirror, "NETWORK_ERROR");
				recordFailure(mirror, cause);
				return false;
			}
			catch(ExecutionException ee)
			{
				Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
				progressReporter.showInfo("status.networking.feed_load_failed_validation", mirror, cause.getClass().getSimpleName());
				recordFailure(mirror, cause);
				return false;
			}
			catch(Exception e)
			{
				progressReporter.showInfo("status.networking.feed_load_failed_validation", mirror, e.getClass().getSimpleName());
				recordFailure(mirror, e);
				return false;
			}

			byte[] mainFeedRaw, mainFeedSigRaw, updateFeedRaw, updateFeedSigRaw;

			try(InputStream mainFeedIs = mainFeedResponse.get().body();
				InputStream mainFeedSigIs = signatureResponse.get().body())
			{

				mainFeedRaw = mainFeedIs.readAllBytes();
				mainFeedSigRaw = mainFeedSigIs.readAllBytes();

				if(!CryptoUtil.verifySignature(mainFeedRaw, mainFeedSigRaw, pub_key, sig_format))
				{
					System.out.println("Main feed failed verification");
					progressReporter.showInfo("status.networking.feed_load_failed_alt", mirror);

					SecurityException ex = new SecurityException("Main feed signature verification failed for mirror: " + mirror);
					MirrorFailure failure = new MirrorFailure(mirror, ex);

					stateLock.writeLock().lock();

					try
					{
						allFailures.add(failure);
						lastException = ex;
						lastFailedMirror = mirror;
					}
					finally
					{
						stateLock.writeLock().unlock();
					}
					return false;
				}
			}

			try(InputStream updateFeedIs = updateFeedResponse.get().body();
				InputStream updateFeedSigIs = updateSignatureResponse.get().body())
			{

				updateFeedRaw = updateFeedIs.readAllBytes();
				updateFeedSigRaw = updateFeedSigIs.readAllBytes();

				if(!CryptoUtil.verifySignature(updateFeedRaw, updateFeedSigRaw, pub_key, sig_format))
				{
					System.out.println("Update feed failed verification");
					progressReporter.showInfo("status.networking.feed_load_failed_alt", mirror);

					SecurityException ex = new SecurityException("Update feed signature verification failed for mirror: " + mirror);
					MirrorFailure failure = new MirrorFailure(mirror, ex);

					stateLock.writeLock().lock();

					try
					{
						allFailures.add(failure);
						lastException = ex;
						lastFailedMirror = mirror;
					}
					finally
					{
						stateLock.writeLock().unlock();
					}
					return false;
				}
			}

			DocumentBuilderFactory dbf = getSecureDocumentBuilderFactory();
			DocumentBuilder db = dbf.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(new String(mainFeedRaw)));
			Document doc = db.parse(is);

			Element main_feed = (Element) doc.getElementsByTagName("main_feed").item(0);

			int tempRevision = 0;
			if(main_feed.getElementsByTagName("min_revision").getLength() > 0)
			{
				tempRevision = Integer.parseInt(main_feed.getElementsByTagName("min_revision").item(0).getTextContent());
			}

			File current_directory = new File(".");
			dbf = getSecureDocumentBuilderFactory();
			db = dbf.newDocumentBuilder();
			is = new InputSource(new StringReader(new String(updateFeedRaw)));
			doc = db.parse(is);

			Element update_feed = (Element) doc.getElementsByTagName("update_feed").item(0);
			boolean has_valid_file_entry = false;

			List<UpdateFile> tempFiles = new ArrayList<>();

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
						boolean only_if_not_exists = false;
						if(file.hasAttribute("only_if_not_exists"))
						{
							try
							{
								only_if_not_exists = Boolean.parseBoolean(file.getAttribute("only_if_not_exists"));
							}
							catch(Exception ignored)
							{
							}
						}

						UpdateFile f = new UpdateFile(sanitized, file.getAttribute("sha256"),
								file.getAttribute("size"), only_if_not_exists);
						tempFiles.add(f);
						has_valid_file_entry = true;
					}
					else
					{
						return false;
					}
				}
			}

			if(has_valid_file_entry)
			{
				stateLock.writeLock().lock();
				try
				{
					MIN_REVISION = tempRevision;
					files.clear();
					files.addAll(tempFiles);
				}
				finally
				{
					stateLock.writeLock().unlock();
				}
			}

			return has_valid_file_entry;

		}
		catch(Exception e)
		{
			e.printStackTrace();
			progressReporter.showInfo("status.networking.feed_load_failed_alt", mirror);

			MirrorFailure failure = new MirrorFailure(mirror, e);

			stateLock.writeLock().lock();
			try
			{
				allFailures.add(failure);
				lastException = e;
				lastFailedMirror = mirror;
			}
			finally
			{
				stateLock.writeLock().unlock();
			}
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

	public static List<UpdateFile> getFiles()
	{
		stateLock.readLock().lock();
		try
		{
			return new ArrayList<>(files);
		}
		finally
		{
			stateLock.readLock().unlock();
		}
	}

	public static Throwable getLastException()
	{
		stateLock.readLock().lock();
		try
		{
			return lastException;
		}
		finally
		{
			stateLock.readLock().unlock();
		}
	}

	public static boolean isSuccessful()
	{
		stateLock.readLock().lock();
		try
		{
			return loadState == LoadState.SUCCESS;
		}
		finally
		{
			stateLock.readLock().unlock();
		}
	}

	public static int getMinRevision()
	{
		stateLock.readLock().lock();
		try
		{
			return MIN_REVISION;
		}
		finally
		{
			stateLock.readLock().unlock();
		}
	}

	public static String getLastExceptionDetails()
	{
		stateLock.readLock().lock();
		try
		{
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
		}
		finally
		{
			stateLock.readLock().unlock();
		}
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