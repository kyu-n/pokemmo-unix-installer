package com.pokeemu.unix.util;

import java.awt.*;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.MainFrame;

/**
 * @author Desu
 */
public class Util
{
	private static final boolean desktopBrowseSupported, desktopOpenSupported;

	static
	{
		desktopBrowseSupported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
		desktopOpenSupported = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
	}

	public static void open(String file)
	{
		open(new File(file));
	}

	public static void open(File file)
	{
		if(file == null)
		{
			throw new IllegalArgumentException("File may not be null");
		}

		new Thread(() ->
		{
			if(desktopOpenSupported)
			{
				try
				{
					Desktop.getDesktop().open(file);
					return;
				}
				catch(IOException ex)
				{
					System.out.println("Failed to open Desktop#open " + file.getAbsolutePath());
					ex.printStackTrace();
				}
			}

			doXdgOpen(file.getAbsolutePath());

		}).start();
	}

	public static void browse(String url)
	{
		if(url == null || url.isEmpty())
		{
			throw new IllegalArgumentException("Malformed URL " + url);
		}

		new Thread(() ->
		{
			if(desktopBrowseSupported)
			{
				try
				{
					Desktop.getDesktop().browse(new URI(url));
					return;
				}
				catch(IOException | URISyntaxException ex)
				{
					System.out.println("Failed to open Desktop#browse " + url);
					ex.printStackTrace();
				}
			}

			doXdgOpen(url);

		}).start();
	}

	private static void doXdgOpen(String url)
	{
		ProcessBuilder pb = new ProcessBuilder();
		pb.inheritIO();
		pb.command("xdg-open", url);

		try
		{
			pb.start();
		}
		catch(IOException ex)
		{
			System.out.println("Failed to start xdg-open");
			ex.printStackTrace();
			MainFrame.getInstance().showError(Config.getString("error.cant_open_client_folder"), Config.getString("error.io_exception"));
		}
	}

	public static byte[] getBytes(InputStream is) throws IOException
	{
		int len;
		int size = 1024;
		byte[] buf;

		if(is instanceof ByteArrayInputStream)
		{
			size = is.available();
			buf = new byte[size];
		}
		else
		{
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			buf = new byte[size];
			while((len = is.read(buf, 0, size)) != -1)
			{
				bos.write(buf, 0, len);
			}
			buf = bos.toByteArray();
		}
		return buf;
	}

	public static String calculateHash(String digest_type, File file)
	{
		if(digest_type.equalsIgnoreCase("sha256"))
		{
			digest_type = "SHA-256";
		}

		if(!file.exists() || !file.isFile())
		{
			return "FILE_DOESNT_EXIST";
		}

		FileInputStream fis = null;
		try
		{
			fis = new FileInputStream(file);
			return calculateHash(digest_type, fis);
		}
		catch(Exception e)
		{
			return "ERROR CALCULATING";
		}
		finally
		{
			try
			{
				if(fis != null)
				{
					fis.close();
				}
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	public static String calculateHash(String digest_type, InputStream input)
	{
		try
		{
			MessageDigest algorithm = MessageDigest.getInstance(digest_type);
			BufferedInputStream bis = new BufferedInputStream(input);
			DigestInputStream dis = new DigestInputStream(bis, algorithm);

			// read the file and update the hash calculation
			byte[] buffer = new byte[4096];
			while(dis.read(buffer) != -1) ;

			// get the hash value as byte array
			byte[] hash = algorithm.digest();

			return byteArray2Hex(hash);
		}
		catch(NoSuchAlgorithmException e)
		{
			return "Invalid Hash Algo";
		}
		catch(Exception e)
		{
			return "ERROR CALCULATING";
		}
	}

	public static String byteArray2Hex(byte[] hash)
	{
		Formatter formatter = new Formatter();
		for(byte b : hash)
		{
			formatter.format("%02x", b);
		}
		String result = formatter.toString().toLowerCase();
		formatter.close();
		return result;
	}

	/**
	 * Checks a Directory/Filepath combination to make sure it is safe.
	 *
	 * @param dir   current directory we are checking from
	 * @param entry filepath we are checking
	 * @return null if unsafe, otherwise relative path of file
	 */
	public static String sanitize(final File dir, final String entry)
	{
		if(entry.isEmpty())
		{
			return null;
		}

		if(new File(entry).isAbsolute())
		{
			return null;
		}

		try
		{
			final String DirPath = dir.getPath() + File.separator;
			final String EntryPath = new File(dir, entry).getPath();

			if(!EntryPath.startsWith(DirPath))
			{
				return null;
			}

			return EntryPath.substring(DirPath.length());
		}
		catch(Exception e)
		{
			// Ignored
		}

		return null;
	}

	/**
	 * Sends a synchronized HTTP GET request using the requested HttpClient, returning the value as an InputStream
	 */
	public static HttpResponse<InputStream> getUrl(HttpClient httpClient, String raw_url) throws URISyntaxException, IOException, InterruptedException
	{
		HttpRequest httpRequest = HttpRequest.newBuilder(new URI(raw_url))
				.setHeader("User-Agent", UnixInstaller.httpClientUserAgent)
				.GET()
				.build();

		return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
	}

	/**
	 * Sends an asynchronous HTTP GET request using the requested HttpClient, returning the value as an InputStream
	 */
	public static CompletableFuture<HttpResponse<InputStream>> getUrlAsync(HttpClient httpClient, String raw_url) throws URISyntaxException
	{
		HttpRequest httpRequest = HttpRequest.newBuilder(new URI(raw_url))
				.setHeader("User-Agent", UnixInstaller.httpClientUserAgent)
				.GET()
				.build();

		return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
	}

	/**
	 * Sends a synchronized HTTP GET request using the requested HttpClient, returning the value as an InputStream.
	 * This method accepts content compression where available
	 */
	public static HttpResponse<InputStream> downloadFile(HttpClient httpClient, String raw_url) throws URISyntaxException, IOException, InterruptedException
	{
		HttpRequest httpRequest = HttpRequest.newBuilder(new URI(raw_url))
				.setHeader("User-Agent", UnixInstaller.httpClientUserAgent)
				.setHeader("Accept-Encoding", "gzip, deflate")
				.GET()
				.build();

		return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
	}

	/**
	 * Downloads and saves the requested URL to the requested filename
	 * @return true, if the http server successfully responded to the request. false if an exception occurred.
	 * A true response does not necessarily mean the server responded with what you want (e.g. a 404 request will return an http snippet.)
	 */
	public static boolean downloadUrlToFile(HttpClient httpClient, String raw_url, File file)
	{
		try
		{
			raw_url = raw_url.replace("\\", "/");

			HttpResponse<InputStream> downloadResponse = downloadFile(httpClient, raw_url);
			String encoding = downloadResponse.headers().firstValue("Content-Encoding").orElse("");

			InputStream resultingInputStream;
			InputStream rawInputStream = downloadResponse.body();

			switch(encoding.toLowerCase(Locale.ROOT))
			{
				case "gzip" -> resultingInputStream = new GZIPInputStream(rawInputStream);
				case "deflate" -> resultingInputStream = new InflaterInputStream(rawInputStream, new Inflater(true));
				default -> resultingInputStream = rawInputStream;
			}

			//Make parent dirs if not exist
			try(BufferedInputStream in = new BufferedInputStream(resultingInputStream) ;
				BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(file), 1024))
			{
				byte[] data = new byte[1024];
				int x;

				while((x = in.read(data, 0, 1024)) >= 0)
				{
					bout.write(data, 0, x);
				}
			}

			return true;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return false;
		}
	}
}
