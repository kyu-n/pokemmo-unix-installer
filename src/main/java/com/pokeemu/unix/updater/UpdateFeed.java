package com.pokeemu.unix.updater;

import java.io.File;
import java.io.StringReader;
import java.net.URL;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

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
 * Responsible for loading an UpdateFeed
 *
 * @author Desu
 */
public class UpdateFeed
{
	/**
	 * List of files and their checksums.
	 */
	private final List<UpdateFile> files = new ArrayList<>();
	public boolean SUCCESSFUL = false;

	public UpdateFeed(MainFrame mainFrame)
	{
		String sig_format = "SHA256withRSA";
		PublicKey pub_key = CryptoUtil.getFeedsPublicKey();

		for(int feed_id = 0; feed_id < MainFeed.DOWNLOAD_MIRRORS.length; feed_id++)
		{
			try
			{
				files.clear();
				boolean has_valid_file_entry = false;

				byte[] raw = Util.getBytes(new URL(MainFeed.DOWNLOAD_MIRRORS[feed_id] + "/feeds/update_feed.txt").openStream());
				byte[] signature = Util.getBytes(new URL(MainFeed.DOWNLOAD_MIRRORS[feed_id] + "/feeds/update_feed.sig256").openStream());

				if(!CryptoUtil.verifySignature(raw, signature, pub_key, sig_format))
				{
					throw new RuntimeException("Error verifying feed signature.");
				}

				File current_directory = new File(".");
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				InputSource is = new InputSource(new StringReader(new String(raw)));
				Document doc = db.parse(is);

				Element update_feed = (Element) doc.getElementsByTagName("update_feed").item(0);

				NodeList filesNodeList = update_feed.getElementsByTagName("file");
				for(int x = 0; x < filesNodeList.getLength(); x++)
				{
					Node fileT = filesNodeList.item(x);
					if(fileT.getNodeType() == Node.ELEMENT_NODE)
					{
						Element file = (Element) fileT;

						String sanitized = Util.sanitize(current_directory, file.getAttribute("name"));
						if(sanitized == null)
						{
							mainFrame.showInfo(Config.getString("status.networking.feed_load_failed"), feed_id, "INVALID_001");
						}
						else if(!file.hasAttribute("sha256"))
						{
							mainFrame.showInfo(Config.getString("status.networking.feed_load_failed"), feed_id, "INVALID_002");
						}
						else
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
				mainFrame.showInfo(Config.getString("status.networking.feed_load_failed_alt", feed_id));
			}
		}

		if(!SUCCESSFUL)
		{
			mainFrame.showError(Config.getString("status.networking.feed_load_failed"), Config.getString("status.title.fatal_error"), () -> System.exit(UnixInstaller.EXIT_CODE_NETWORK_FAILURE));
		}
	}

	/**
	 * List files and their checksums.
	 *
	 * @return files
	 */
	public List<UpdateFile> getFiles()
	{
		return files;
	}
}
