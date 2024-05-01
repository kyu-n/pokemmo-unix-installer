package com.pokeemu.unix.updater;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.net.http.HttpResponse;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.MainFrame;
import com.pokeemu.unix.util.CryptoUtil;
import com.pokeemu.unix.util.Util;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Manager class for loading Main / Update feeds
 *
 * @author Desu
 */
public class FeedManager
{
	public static final String[] DOWNLOAD_MIRRORS = {
			"https://dl.pokemmo.com/",
			"https://files.pokemmo.com/",
			"https://dl.pokemmo.download/"
	};

	/**
	 * Min client revision allowed. If lower, will force update.
	 */
	public static int MIN_REVISION = 0;
	public static boolean SUCCESSFUL = false;
	private static final List<UpdateFile> files = new ArrayList<>();

	public static void load(MainFrame mainFrame)
	{
		String sig_format = "SHA256withRSA";
		PublicKey pub_key = CryptoUtil.getFeedsPublicKey();

		List<Throwable> failures = new ArrayList<>();

		loop:for(String mirror : DOWNLOAD_MIRRORS)
		{
			try
			{
				CompletableFuture<HttpResponse<InputStream>> mainFeedResponse = Util.getUrlAsync(UnixInstaller.httpClient, mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/main_feed.txt");
				CompletableFuture<HttpResponse<InputStream>> signatureResponse = Util.getUrlAsync(UnixInstaller.httpClient, mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/main_feed.sig256");
				CompletableFuture<HttpResponse<InputStream>> updateFeedResponse = Util.getUrlAsync(UnixInstaller.httpClient, mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/update_feed.txt");
				CompletableFuture<HttpResponse<InputStream>> updateSignatureResponse = Util.getUrlAsync(UnixInstaller.httpClient, mirror + "/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/update_feed.sig256");

				// Using CompleteableFuture#allOf#join will eagerly terminate this mirror's processing if one of the URLs throws some kind of exception
				CompletableFuture.allOf(mainFeedResponse, signatureResponse, updateFeedResponse, updateSignatureResponse)
						.exceptionally(error ->
						{
							mainFrame.showInfo("status.networking.feed_load_failed_validation", mirror, "INVALID_001");
							return null;
						}).join();

				byte[] mainFeedRaw, mainFeedSigRaw, updateFeedRaw, updateFeedSigRaw;

				try(InputStream mainFeedIs = mainFeedResponse.get().body() ; InputStream mainFeedSigIs = signatureResponse.get().body())
				{
					mainFeedRaw = mainFeedIs.readAllBytes();
					mainFeedSigRaw = mainFeedSigIs.readAllBytes();

					if(!CryptoUtil.verifySignature(mainFeedRaw, mainFeedSigRaw, pub_key, sig_format))
					{
						System.out.println("Main feed failed verification");
						mainFrame.showInfo(Config.getString("status.networking.feed_load_failed_alt", mirror));
						continue;
					}
				}

				try(InputStream updateFeedIs = updateFeedResponse.get().body() ; InputStream updateFeedSigIs = updateSignatureResponse.get().body())
				{
					updateFeedRaw = updateFeedIs.readAllBytes();
					updateFeedSigRaw = updateFeedSigIs.readAllBytes();

					if(!CryptoUtil.verifySignature(updateFeedRaw, updateFeedSigRaw, pub_key, sig_format))
					{
						System.out.println("Update feed failed verification");
						mainFrame.showInfo(Config.getString("status.networking.feed_load_failed_alt", mirror));
						continue;
					}
				}

				// If sig validity passes, move on to xml parsing / updates / min_revision checks

				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				InputSource is = new InputSource(new StringReader(new String(mainFeedRaw)));
				Document doc = db.parse(is);

				Element main_feed = (Element) doc.getElementsByTagName("main_feed").item(0);

				if(main_feed.getElementsByTagName("min_revision").getLength() > 0)
				{
					MIN_REVISION = Integer.parseInt(main_feed.getElementsByTagName("min_revision").item(0).getTextContent());
				}

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
									// Don't care
								}
							}

							UpdateFile f = new UpdateFile(sanitized, file.getAttribute("sha256"), file.getAttribute("size"), only_if_not_exists);
							files.add(f);
							has_valid_file_entry = true;
						}
						else
						{
							SUCCESSFUL = false;
							continue loop;
						}
					}
				}

				//Make sure we have at least 1 normal file (options/jres don't count towards this check)
				if(has_valid_file_entry)
				{
					SUCCESSFUL = true;
					return;
				}
			}
			catch(Exception e)
			{
				e.printStackTrace();
				mainFrame.showInfo(Config.getString("status.networking.feed_load_failed_alt", mirror));
				failures.add(e);
			}

			if(!SUCCESSFUL)
			{
				mainFrame.showErrorWithStacktrace(Config.getString("status.networking.feed_load_failed"), Config.getString("status.title.fatal_error"), failures.toArray(new Throwable[0]), () -> System.exit(UnixInstaller.EXIT_CODE_NETWORK_FAILURE));
			}
		}
	}

	/**
	 * List files and their checksums.
	 *
	 * @return files
	 */
	public static List<UpdateFile> getFiles()
	{
		return files;
	}
}
