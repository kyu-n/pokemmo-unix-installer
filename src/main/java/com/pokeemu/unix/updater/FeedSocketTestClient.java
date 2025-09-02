package com.pokeemu.unix.updater;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Test client for verifying feed socket server functionality.
 * Run this while the launcher is waiting with the socket open.
 *
 * Usage: java FeedSocketTestClient <pid>
 * Where <pid> is the process ID shown in the socket path
 */
public class FeedSocketTestClient
{
	private static final byte CMD_GET_MAIN_FEED = 0x01;
	private static final byte CMD_GET_UPDATE_FEED = 0x02;
	private static final byte CMD_GET_MAIN_SIGNATURE = 0x03;
	private static final byte CMD_GET_UPDATE_SIGNATURE = 0x04;
	private static final byte CMD_CLOSE = 0x0F;

	// Response codes
	private static final byte RESP_OK = 0x00;
	private static final byte RESP_ERROR = 0x01;
	private static final byte RESP_NOT_FOUND = 0x02;

	private final Path socketPath;
	private SocketChannel channel;

	public FeedSocketTestClient(String pid)
	{
		this.socketPath = Path.of("/tmp/pokemmo_feeds_" + pid + ".sock");
	}

	public void connect() throws IOException
	{
		System.out.println("Connecting to socket: " + socketPath);

		UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
		channel = SocketChannel.open(StandardProtocolFamily.UNIX);
		channel.connect(address);

		System.out.println("Connected successfully!");
	}

	public void testFeed(String name, byte command) throws IOException
	{
		System.out.println("\n=== Testing " + name + " ===");

		// Send command
		ByteBuffer commandBuffer = ByteBuffer.allocate(1);
		commandBuffer.put(command);
		commandBuffer.flip();
		channel.write(commandBuffer);

		// Read response header (1 byte status + 4 bytes length)
		ByteBuffer headerBuffer = ByteBuffer.allocate(5);
		headerBuffer.order(ByteOrder.BIG_ENDIAN);

		while(headerBuffer.hasRemaining())
		{
			int read = channel.read(headerBuffer);
			if(read < 0)
			{
				throw new IOException("Connection closed by server");
			}
		}

		headerBuffer.flip();
		byte status = headerBuffer.get();
		int length = headerBuffer.getInt();

		System.out.println("Status: " + getStatusName(status) + " (0x" + String.format("%02X", status) + ")");
		System.out.println("Length: " + length + " bytes");

		if(status != RESP_OK || length == 0)
		{
			System.out.println("No data received");
			return;
		}

		// Read data
		ByteBuffer dataBuffer = ByteBuffer.allocate(length);
		while(dataBuffer.hasRemaining())
		{
			int read = channel.read(dataBuffer);
			if(read < 0)
			{
				throw new IOException("Connection closed while reading data");
			}
		}

		dataBuffer.flip();
		byte[] data = new byte[length];
		dataBuffer.get(data);

		// Display data based on type
		if(name.contains("Signature"))
		{
			// Signatures are binary, show hex
			System.out.println("Signature (first 64 bytes hex):");
			printHex(data, Math.min(64, data.length));
		}
		else
		{
			// Feeds are XML, show as text
			String content = new String(data, StandardCharsets.UTF_8);
			System.out.println("Content (first 500 chars):");
			if(content.length() > 500)
			{
				System.out.println(content.substring(0, 500) + "...");
			}
			else
			{
				System.out.println(content);
			}

			// Show some stats
			int lineCount = content.split("\n").length;
			System.out.println("\nStats: " + lineCount + " lines, " + length + " bytes");

			// Check for XML structure
			if(content.contains("<?xml"))
			{
				System.out.println("Valid XML header found");
			}
			if(content.contains("<main_feed>") || content.contains("<update_feed>"))
			{
				System.out.println("Feed root element found");
			}
		}
	}

	private String getStatusName(byte status)
	{
		switch(status)
		{
			case RESP_OK: return "OK";
			case RESP_ERROR: return "ERROR";
			case RESP_NOT_FOUND: return "NOT_FOUND";
			default: return "UNKNOWN";
		}
	}

	private void printHex(byte[] data, int maxBytes)
	{
		for(int i = 0; i < maxBytes && i < data.length; i++)
		{
			if(i % 16 == 0 && i > 0)
			{
				System.out.println();
			}
			System.out.printf("%02X ", data[i]);
		}
		System.out.println();
	}

	public void close() throws IOException
	{
		if(channel != null && channel.isOpen())
		{
			// Send close command
			ByteBuffer closeCmd = ByteBuffer.allocate(1);
			closeCmd.put(CMD_CLOSE);
			closeCmd.flip();
			channel.write(closeCmd);

			channel.close();
			System.out.println("\nConnection closed");
		}
	}

	public static void main(String[] args)
	{
		if(args.length != 1)
		{
			System.err.println("Usage: java FeedSocketTestClient <pid>");
			System.err.println("Example: java FeedSocketTestClient 349726");
			System.err.println("\nThe PID is shown in the launcher output:");
			System.err.println("  'Feed socket server started at: /tmp/pokemmo_feeds_349726.sock'");
			System.exit(1);
		}

		FeedSocketTestClient client = new FeedSocketTestClient(args[0]);

		try
		{
			client.connect();

			// Test all feed types
			client.testFeed("Main Feed", CMD_GET_MAIN_FEED);
			client.testFeed("Update Feed", CMD_GET_UPDATE_FEED);
			client.testFeed("Main Signature", CMD_GET_MAIN_SIGNATURE);
			client.testFeed("Update Signature", CMD_GET_UPDATE_SIGNATURE);

			client.close();
		}
		catch(IOException e)
		{
			System.err.println("Error: " + e.getMessage());
			e.printStackTrace();
		}
	}
}