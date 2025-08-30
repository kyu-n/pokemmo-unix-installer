package com.pokeemu.unix.util;

import java.awt.*;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import com.pokeemu.unix.LauncherUtils;

public class Util {
	private static final boolean desktopBrowseSupported, desktopOpenSupported;

	static {
		desktopBrowseSupported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
		desktopOpenSupported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
	}

	public static void open(File file) {
		if (file == null) {
			throw new IllegalArgumentException("File may not be null");
		}

		new Thread(() -> {
			if (desktopOpenSupported) {
				try {
					Desktop.getDesktop().open(file);
					return;
				} catch (IOException ex) {
					System.out.println("Failed to open Desktop#open " + file.getAbsolutePath());
					ex.printStackTrace();
				}
			}

			doXdgOpen(file.getAbsolutePath());
		}).start();
	}

	public static void browse(String url) {
		if (url == null || url.isEmpty()) {
			throw new IllegalArgumentException("Malformed URL " + url);
		}

		new Thread(() -> {
			if (desktopBrowseSupported) {
				try {
					Desktop.getDesktop().browse(new URI(url));
					return;
				} catch (IOException | URISyntaxException ex) {
					System.out.println("Failed to open Desktop#browse " + url);
					ex.printStackTrace();
				}
			}

			doXdgOpen(url);
		}).start();
	}

	private static void doXdgOpen(String url) {
		ProcessBuilder pb = new ProcessBuilder();
		pb.inheritIO();
		pb.command("xdg-open", url);

		try {
			pb.start();
		} catch (IOException ex) {
			System.out.println("Failed to start xdg-open");
			ex.printStackTrace();
		}
	}

	public static String calculateHash(String digestType, File file) {
		if (digestType.equalsIgnoreCase("sha256")) {
			digestType = "SHA-256";
		}

		if (!file.exists() || !file.isFile()) {
			return "FILE_DOESNT_EXIST";
		}

		try (FileInputStream fis = new FileInputStream(file)) {
			return calculateHash(digestType, fis);
		} catch (Exception e) {
			return "ERROR CALCULATING";
		}
	}

	public static String calculateHash(String digestType, InputStream input) {
		try {
			MessageDigest algorithm = MessageDigest.getInstance(digestType);
			BufferedInputStream bis = new BufferedInputStream(input);
			DigestInputStream dis = new DigestInputStream(bis, algorithm);

			byte[] buffer = new byte[4096];
			while (dis.read(buffer) != -1) ;

			byte[] hash = algorithm.digest();

			return byteArray2Hex(hash);
		} catch (NoSuchAlgorithmException e) {
			return "Invalid Hash Algo";
		} catch (Exception e) {
			return "ERROR CALCULATING";
		}
	}

	public static String byteArray2Hex(byte[] hash) {
		Formatter formatter = new Formatter();
		for (byte b : hash) {
			formatter.format("%02x", b);
		}
		String result = formatter.toString().toLowerCase();
		formatter.close();
		return result;
	}

	public static String sanitize(final File dir, final String entry) {
		if (entry.isEmpty()) {
			return null;
		}

		if (new File(entry).isAbsolute()) {
			return null;
		}

		try {
			final String DirPath = dir.getPath() + File.separator;
			final String EntryPath = new File(dir, entry).getPath();

			if (!EntryPath.startsWith(DirPath)) {
				return null;
			}

			return EntryPath.substring(DirPath.length());
		} catch (Exception e) {
			// Ignored
		}

		return null;
	}

	public static HttpResponse<InputStream> getUrl(HttpClient httpClient, String rawUrl) throws URISyntaxException, IOException, InterruptedException {
		HttpRequest httpRequest = HttpRequest.newBuilder(new URI(rawUrl))
				.setHeader("User-Agent", LauncherUtils.httpClientUserAgent)
				.GET()
				.build();

		return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
	}

	public static CompletableFuture<HttpResponse<InputStream>> getUrlAsync(HttpClient httpClient, String rawUrl) throws URISyntaxException {
		HttpRequest httpRequest = HttpRequest.newBuilder(new URI(rawUrl))
				.setHeader("User-Agent", LauncherUtils.httpClientUserAgent)
				.GET()
				.build();

		return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
	}

	public static HttpResponse<InputStream> downloadFile(HttpClient httpClient, String rawUrl) throws URISyntaxException, IOException, InterruptedException {
		HttpRequest httpRequest = HttpRequest.newBuilder(new URI(rawUrl))
				.setHeader("User-Agent", LauncherUtils.httpClientUserAgent)
				.setHeader("Accept-Encoding", "gzip, deflate")
				.GET()
				.build();

		return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
	}

	public static boolean downloadUrlToFile(HttpClient httpClient, String rawUrl, File file) {
		try {
			rawUrl = rawUrl.replace("\\", "/");

			HttpResponse<InputStream> downloadResponse = downloadFile(httpClient, rawUrl);
			String encoding = downloadResponse.headers().firstValue("Content-Encoding").orElse("");

			InputStream resultingInputStream;
			InputStream rawInputStream = downloadResponse.body();

			switch (encoding.toLowerCase(Locale.ROOT)) {
				case "gzip" -> resultingInputStream = new GZIPInputStream(rawInputStream);
				case "deflate" -> resultingInputStream = new InflaterInputStream(rawInputStream, new Inflater(true));
				default -> resultingInputStream = rawInputStream;
			}

			try (BufferedInputStream in = new BufferedInputStream(resultingInputStream);
				 BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(file), 1024)) {
				byte[] data = new byte[1024];
				int x;

				while ((x = in.read(data, 0, 1024)) >= 0) {
					bout.write(data, 0, x);
				}
			}

			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}
}