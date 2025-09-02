package com.pokeemu.unix.updater;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unix domain socket server for transferring feed data to the game client.
 * This keeps feed data in memory and avoids disk I/O.
 * @author Kyu
 */
public class FeedSocketServer
{
	private static final String SOCKET_PREFIX = "/tmp/pokemmo_feeds_";
	private static final int TIMEOUT_SECONDS = 10;

	// Protocol commands
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
	private final byte[] mainFeed;
	private final byte[] updateFeed;
	private final byte[] mainSignature;
	private final byte[] updateSignature;

	private ServerSocketChannel server;
	private Thread serverThread;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private final AtomicBoolean clientConnected = new AtomicBoolean(false);
	private final CountDownLatch shutdownLatch = new CountDownLatch(1);

	public FeedSocketServer(byte[] mainFeed, byte[] updateFeed,
							byte[] mainSignature, byte[] updateSignature)
	{
		// Generate unique socket name with PID
		long pid = ProcessHandle.current().pid();
		this.socketPath = Path.of(SOCKET_PREFIX + pid + ".sock");

		this.mainFeed = mainFeed;
		this.updateFeed = updateFeed;
		this.mainSignature = mainSignature;
		this.updateSignature = updateSignature;
	}

	/**
	 * Start the socket server and return the socket path for the client
	 */
	public String start() throws IOException
	{
		Files.deleteIfExists(socketPath);

		UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
		server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
		server.bind(address);

		try
		{
			Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
			Files.setPosixFilePermissions(socketPath, perms);
		}
		catch(UnsupportedOperationException e)
		{
			System.out.println("Unable to set socket permissions (non-POSIX system)");
		}

		running.set(true);

		serverThread = new Thread(this::serverLoop, "FeedSocketServer");
		serverThread.setDaemon(true);
		serverThread.start();

		return socketPath.toString();
	}

	private void serverLoop()
	{
		System.out.println("Feed socket server started at: " + socketPath);

		try
		{
			server.configureBlocking(false);

			long startTime = System.currentTimeMillis();
			SocketChannel client = null;

			while(running.get() && client == null)
			{
				client = server.accept();

				if(client == null)
				{
					if(System.currentTimeMillis() - startTime > TIMEOUT_SECONDS * 1000)
					{
						System.out.println("Feed socket server timed out waiting for connection");
						running.set(false);
						return;
					}

					try
					{
						Thread.sleep(100);
					}
					catch(InterruptedException e)
					{
						Thread.currentThread().interrupt();
						running.set(false);
						return;
					}
				}
			}

			if(client != null)
			{
				clientConnected.set(true);
				System.out.println("Game client connected to feed socket");

				client.configureBlocking(true);

				try
				{
					handleClient(client);
				}
				finally
				{
					client.close();
				}
			}
		}
		catch(IOException e)
		{
			if(running.get())
			{
				System.err.println("Feed socket server error: " + e.getMessage());
			}
		}
		finally
		{
			running.set(false);
			cleanup();
			shutdownLatch.countDown();
		}
	}

	private void handleClient(SocketChannel client) throws IOException
	{
		ByteBuffer commandBuffer = ByteBuffer.allocate(1);

		while(running.get() && client.isConnected())
		{
			commandBuffer.clear();
			int bytesRead = client.read(commandBuffer);

			if(bytesRead < 0)
			{
				break;
			}

			if(bytesRead == 0)
			{
				continue;
			}

			commandBuffer.flip();
			byte command = commandBuffer.get();

			switch(command)
			{
				case CMD_GET_MAIN_FEED -> sendData(client, mainFeed);
				case CMD_GET_UPDATE_FEED -> sendData(client, updateFeed);
				case CMD_GET_MAIN_SIGNATURE -> sendData(client, mainSignature);
				case CMD_GET_UPDATE_SIGNATURE -> sendData(client, updateSignature);
				case CMD_CLOSE -> {
					System.out.println("Game client requested socket close");
					return;
				}
				default -> sendError(client, RESP_ERROR);
			}
		}

		System.out.println("Game client disconnected from feed socket");
	}

	private void sendData(SocketChannel client, byte[] data) throws IOException
	{
		if(data == null || data.length == 0)
		{
			sendError(client, RESP_NOT_FOUND);
			return;
		}

		// Protocol: [1 byte status][4 bytes length][N bytes data]
		ByteBuffer response = ByteBuffer.allocate(1 + 4 + data.length);
		response.order(ByteOrder.BIG_ENDIAN);

		response.put(RESP_OK);
		response.putInt(data.length);
		response.put(data);
		response.flip();

		while(response.hasRemaining())
		{
			client.write(response);
		}
	}

	private void sendError(SocketChannel client, byte errorCode) throws IOException
	{
		ByteBuffer response = ByteBuffer.allocate(5);
		response.put(errorCode);
		response.putInt(0); // Zero length for error
		response.flip();

		client.write(response);
	}

	/**
	 * Stop the server and clean up resources
	 */
	public void stop()
	{
		if(!running.compareAndSet(true, false))
		{
			return;
		}

		System.out.println("Stopping feed socket server");

		try
		{
			if(server != null && server.isOpen())
			{
				server.close();
			}
		}
		catch(IOException e)
		{
			System.err.println("Error closing socket server: " + e.getMessage());
		}

		try
		{
			if(!shutdownLatch.await(2, TimeUnit.SECONDS))
			{
				System.err.println("Feed socket server thread did not terminate gracefully");
				if(serverThread != null)
				{
					serverThread.interrupt();
				}
			}
		}
		catch(InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}

		cleanup();
	}

	private void cleanup()
	{
		try
		{
			Files.deleteIfExists(socketPath);
		}
		catch(IOException e)
		{
			System.err.println("Failed to delete socket file: " + e.getMessage());
		}
	}

	public boolean isRunning()
	{
		return running.get();
	}
}