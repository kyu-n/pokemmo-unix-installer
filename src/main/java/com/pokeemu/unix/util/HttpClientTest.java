package com.pokeemu.unix.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;

/**
 * @author Kyu
 */
public class HttpClientTest
{
	public static void main(String[] args) throws Exception
	{
		new HttpClientTest().run2();
	}

	private void run() throws Exception
	{
		long start = System.currentTimeMillis();

		CompletableFuture<HttpResponse<InputStream>> mainFeedResponse = Util.getUrlAsync(UnixInstaller.httpClient, "https://dl.pokemmo.com/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/main_feed.txt");
		CompletableFuture<HttpResponse<InputStream>> signatureResponse = Util.getUrlAsync(UnixInstaller.httpClient, "https://dl.pokemmo.com/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/main_feed.sig256");
		CompletableFuture<HttpResponse<InputStream>> updateFeedResponse = Util.getUrlAsync(UnixInstaller.httpClient, "https://dl.pokemmo.com/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/update_feed.txt");
		CompletableFuture<HttpResponse<InputStream>> updateSignatureResponse = Util.getUrlAsync(UnixInstaller.httpClient, "https://dl.pokemmo.com/" + Config.UPDATE_CHANNEL.name() + "/current/feeds/update_feed.sig256");

		System.out.println("Waiting on CompleteableFuture#allOf.." + (System.currentTimeMillis() - start)+"ms");
		// Using CompleteableFuture#allOf#join will eagerly terminate this mirror's processing if one of the URLs throws some kind of exception
		CompletableFuture.allOf(mainFeedResponse, signatureResponse, updateFeedResponse, updateSignatureResponse)
				.exceptionally(error ->
				{
					error.printStackTrace();
					return null;
				})
				.join();


		System.out.println("Waiting on CryptoUtil#verifySignature.." + (System.currentTimeMillis() - start)+"ms");
		if(!CryptoUtil.verifySignature(mainFeedResponse.get().body().readAllBytes(), signatureResponse.get().body().readAllBytes(), CryptoUtil.getFeedsPublicKey(), "SHA256withRSA"))
		{
			System.out.println("Main feed failed verification");
		}
		System.out.println("Main feed passed verification in " + (System.currentTimeMillis() - start)+"ms");

		if(!CryptoUtil.verifySignature(updateFeedResponse.get().body().readAllBytes(), updateSignatureResponse.get().body().readAllBytes(), CryptoUtil.getFeedsPublicKey(), "SHA256withRSA"))
		{
			System.out.println("Update feed failed verification");
		}
		System.out.println("Update feed passed verification in " + (System.currentTimeMillis() - start)+"ms");

		System.out.println("Job's done " + (System.currentTimeMillis() - start)+"ms");
	}

	private void run2()
	{
		try
		{
			HttpResponse<InputStream> downloadResponse = Util.downloadFile(UnixInstaller.httpClient, "https://dl.pokemmo.com/live/current/feeds/main_feed.txt");

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
			File file = new File(System.getProperty("user.dir")+"/main_feed.txt");

			try(BufferedInputStream in = new BufferedInputStream(resultingInputStream) ; FileOutputStream fos = new FileOutputStream(file)
				; BufferedOutputStream bout = new BufferedOutputStream(fos, 1024))
			{
				byte[] data = new byte[1024];
				int x;
				while((x = in.read(data, 0, 1024)) >= 0)
				{
					bout.write(data, 0, x);
				}
			}

			System.out.println("Job's done");
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}
