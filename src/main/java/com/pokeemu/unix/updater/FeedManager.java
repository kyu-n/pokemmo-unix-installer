package com.pokeemu.unix.updater;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class FeedManager
{
	public static final String[] DOWNLOAD_MIRRORS = {
			"https://dl.pokemmo.com/",
			"https://files.pokemmo.com/",
			"https://dl.pokemmo.download/"
	};

	public static int MIN_REVISION = 0;
	public static boolean SUCCESSFUL = false;
	private static final List<UpdateFile> files = new ArrayList<>();

	// Fields for exception tracking
	private static Throwable lastException = null;
	private static String lastFailedMirror = null;
	private static final List<MirrorFailure> allFailures = new ArrayList<>();

	// Error recovery configuration
	private static final int MAX_RETRY_ATTEMPTS = 2;
	private static final long INITIAL_RETRY_DELAY_MS = 500;
	private static final long MAX_RETRY_DELAY_MS = 2000;

	// Helper class to track mirror failures
	private static class MirrorFailure
	{
		final String mirror;
		final Throwable exception;
		final FailureType type;
		final long timestamp;

		enum FailureType
		{
			NETWORK,      // Network connectivity issues
			VALIDATION,   // Signature validation failure
			PARSING,      // XML parsing errors
			OTHER         // Unknown errors
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
			// Unwrap CompletionException if needed
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
			// Network errors are retryable, others generally aren't
			return type == FailureType.NETWORK;
		}
	}

	public static void load(IProgressReporter progressReporter)
	{
		// Reset ALL state for retry attempts - critical for proper retry behavior
		resetState();

		String sig_format = "SHA256withRSA";
		PublicKey pub_key = CryptoUtil.getFeedsPublicKey();

		for(String mirror : DOWNLOAD_MIRRORS)
		{
			// Skip if we've had a non-retryable error (like disk full)
			if(hasNonRetryableError())
			{
				break;
			}

			boolean mirrorSuccess = tryMirrorWithRetry(mirror, sig_format, pub_key, progressReporter);

			if(mirrorSuccess)
			{
				SUCCESSFUL = true;
				lastException = null;
				return;
			}
		}

		// If we get here, all mirrors failed
		handleAllMirrorsFailed(progressReporter);
	}

	private static void resetState()
	{
		SUCCESSFUL = false;
		files.clear();
		MIN_REVISION = 0;
		lastException = null;
		lastFailedMirror = null;
		allFailures.clear();
	}

	private static boolean hasNonRetryableError()
	{
		// Check if we've encountered a non-retryable error that affects all mirrors
		for(MirrorFailure failure : allFailures)
		{
			if(failure.type == MirrorFailure.FailureType.OTHER)
			{
				// Check for specific non-retryable conditions
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
		return false;
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
					// Wait before retry with exponential backoff
					try
					{
						Thread.sleep(Math.min(retryDelay, MAX_RETRY_DELAY_MS));
						retryDelay *= 2;
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

				// Check if the last failure is retryable
				if(!allFailures.isEmpty())
				{
					MirrorFailure lastFailure = allFailures.getLast();
					if(!lastFailure.isRetryable())
					{
						// Don't retry non-network errors
						break;
					}
				}

				attempt++;

			}
			catch(Exception e)
			{
				// Unexpected error during retry logic
				e.printStackTrace();
				MirrorFailure failure = new MirrorFailure(mirror, e);
				allFailures.add(failure);
				lastException = e;
				lastFailedMirror = mirror;
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
			// Set a reasonable timeout for feed downloads
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
				allFutures.join();
			}
			catch(CompletionException ce)
			{
				Throwable cause = ce.getCause() != null ? ce.getCause() : ce;
				progressReporter.showInfo("status.networking.feed_load_failed_validation", mirror, "NETWORK_ERROR");

				MirrorFailure failure = new MirrorFailure(mirror, cause);
				allFailures.add(failure);
				lastException = cause;
				lastFailedMirror = mirror;
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
					allFailures.add(failure);
					lastException = ex;
					lastFailedMirror = mirror;
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
					allFailures.add(failure);
					lastException = ex;
					lastFailedMirror = mirror;
					return false;
				}
			}

			// Parse main feed
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			InputSource is = new InputSource(new StringReader(new String(mainFeedRaw)));
			Document doc = db.parse(is);

			Element main_feed = (Element) doc.getElementsByTagName("main_feed").item(0);

			if(main_feed.getElementsByTagName("min_revision").getLength() > 0)
			{
				MIN_REVISION = Integer.parseInt(main_feed.getElementsByTagName("min_revision").item(0).getTextContent());
			}

			// Parse update feed
			File current_directory = new File(".");
			dbf = DocumentBuilderFactory.newInstance();
			db = dbf.newDocumentBuilder();
			is = new InputSource(new StringReader(new String(updateFeedRaw)));
			doc = db.parse(is);

			Element update_feed = (Element) doc.getElementsByTagName("update_feed").item(0);
			boolean has_valid_file_entry = false;

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
							catch(Exception e)
							{
								// Use default
							}
						}

						UpdateFile f = new UpdateFile(sanitized, file.getAttribute("sha256"),
								file.getAttribute("size"), only_if_not_exists);
						files.add(f);
						has_valid_file_entry = true;
					}
					else
					{
						// Invalid file entry, fail this mirror
						return false;
					}
				}
			}

			return has_valid_file_entry;

		}
		catch(Exception e)
		{
			e.printStackTrace();
			progressReporter.showInfo("status.networking.feed_load_failed_alt", mirror);

			MirrorFailure failure = new MirrorFailure(mirror, e);
			allFailures.add(failure);
			lastException = e;
			lastFailedMirror = mirror;
			return false;
		}
	}

	private static void handleAllMirrorsFailed(IProgressReporter progressReporter)
	{
		// Only show error dialog if we're in UI mode (not headless)
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
		return files;
	}

	public static Throwable getLastException()
	{
		return lastException;
	}

	public static String getLastExceptionDetails()
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
				pw.println("  Time: " + new java.util.Date(failure.timestamp));
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
			// Ignore
		}

		return sw.toString();
	}

	// Inner class marker to identify headless progress reporter
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
			// Don't exit immediately in headless mode - let the caller decide
			System.err.println("ERROR: " + title + " - " + message);
		}

		@Override
		public void setDownloadSpeed(String speed)
		{
			// Not relevant for headless operation
		}
	}
}