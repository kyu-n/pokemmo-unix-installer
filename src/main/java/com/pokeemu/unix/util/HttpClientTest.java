package com.pokeemu.unix.util;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.MainFrame;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * @author Kyu
 */
public class HttpClientTest
{
	public static void main(String[] args) throws Exception
	{
		new HttpClientTest().run();
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
}
