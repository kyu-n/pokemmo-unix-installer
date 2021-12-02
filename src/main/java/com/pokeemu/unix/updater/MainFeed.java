package com.pokeemu.unix.updater;

import java.io.StringReader;
import java.net.URL;
import java.security.PublicKey;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.MainFrame;
import com.pokeemu.unix.util.Base64;
import com.pokeemu.unix.util.CryptoUtil;
import com.pokeemu.unix.util.Util;

/**
 * Responsible for loading IP/Port and MinRevision from the feeds
 * @author Desu
 *
 */
public class MainFeed
{
	public static final String[] DOWNLOAD_MIRRORS = {
			"https://dl.pokemmo.eu/",
			"https://files.pokemmo.eu/",
			"https://dl.pokemmo.com/",
			"https://dl.pokemmo.download/"
	};

	/**
	 * Min client revision allowed. If lower, will force update.
	 */
	public static int MIN_REVISION = 0;

	public static void load(MainFrame mainFrame)
	{
		String sig_format = "SHA256withRSA";
		PublicKey pub_key = CryptoUtil.getFeedsPublicKey();
		
		for(int feed_id = 0; feed_id < DOWNLOAD_MIRRORS.length; feed_id++)
		{
			try
			{
				byte[] raw = Util.getBytes(new URL(DOWNLOAD_MIRRORS[feed_id]+"/feeds/main_feed.txt").openStream());
				byte[] signature = Util.getBytes(new URL(DOWNLOAD_MIRRORS[feed_id]+"/feeds/main_feed.sig256").openStream());
				
				if(!CryptoUtil.verifySignature(raw, signature, pub_key, sig_format))
				{
					mainFrame.showInfo("status.networking.feed_load_failed_validation", feed_id, "INVALID_001");
					throw new RuntimeException("Error verifying feed signature.");
				}
				
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				
				DocumentBuilder db = dbf.newDocumentBuilder();
				InputSource is = new InputSource(new StringReader(new String(raw)));
				Document doc = db.parse(is);
				
				Element main_feed = (Element)doc.getElementsByTagName("main_feed").item(0);
				
				if(main_feed.getElementsByTagName("min_revision").getLength() > 0)
					MIN_REVISION = Integer.parseInt(main_feed.getElementsByTagName("min_revision").item(0).getTextContent());
			}
			catch (Exception e) // Something really bad happened. (XML formatting issue / fatal networking error / network signature feed failure)
			{
				e.printStackTrace();

				mainFrame.showInfo(Config.getString("status.networking.feed_load_failed_alt", feed_id));
				mainFrame.showError(Config.getString("status.networking.feed_load_failed"), Config.getString("status.title.fatal_error"),  () -> System.exit(UnixInstaller.EXIT_CODE_NETWORK_FAILURE));

				return;
			}
		}
	}
}
