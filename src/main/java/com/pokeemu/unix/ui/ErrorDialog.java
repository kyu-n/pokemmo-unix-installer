package com.pokeemu.unix.ui;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

public class ErrorDialog extends AbstractModalWindow
{
	public enum ErrorType
	{
		NETWORK(UnixInstaller.EXIT_CODE_NETWORK_FAILURE, "status.title.network_failure", "Network Error"),
		IO(UnixInstaller.EXIT_CODE_IO_FAILURE, "status.title.io_failure", "I/O Error"),
		GENERAL(UnixInstaller.EXIT_CODE_IO_FAILURE, "status.title.fatal_error", "Fatal Error");

		public final int exitCode;
		public final String titleKey;
		public final String defaultTitle;

		ErrorType(int exitCode, String titleKey, String defaultTitle)
		{
			this.exitCode = exitCode;
			this.titleKey = titleKey;
			this.defaultTitle = defaultTitle;
		}

		public String getLocalizedTitle()
		{
			try {
				return Config.getString(titleKey);
			} catch(Exception e) {
				return defaultTitle;
			}
		}

		/**
		 * Attempts to determine error type from an exception
		 */
		public static ErrorType fromException(Throwable exception)
		{
			if(exception == null)
			{
				return GENERAL;
			}

			Throwable current = exception;
			while(current != null)
			{
				// Network-related exceptions
				if(current instanceof java.net.SocketException ||
						current instanceof java.net.UnknownHostException ||
						current instanceof java.net.SocketTimeoutException ||
						current instanceof java.net.ProtocolException ||
						current instanceof javax.net.ssl.SSLException ||
						current instanceof java.util.concurrent.TimeoutException ||
						current instanceof java.nio.channels.UnresolvedAddressException ||
						current instanceof java.nio.channels.ClosedChannelException)
				{
					return NETWORK;
				}

				// File system and I/O exceptions
				if(current instanceof java.io.IOException /*Could be rare network errors. But, no way to know*/ ||
						current instanceof java.nio.channels.NonReadableChannelException ||
						current instanceof java.nio.channels.NonWritableChannelException ||
						current instanceof java.nio.ReadOnlyBufferException ||
						current instanceof SecurityException) // Permission issues
				{
					return IO;
				}

				// Check cause chain
				current = current.getCause();
			}

			// Default to general error
			return GENERAL;
		}
	}

	private final UnixInstaller parent;

	// Error details
	private String errorMessage;
	private String stackTrace;
	private ErrorType errorType = ErrorType.GENERAL;

	// Thread safety
	private final Object showLock = new Object();
	private volatile boolean isCurrentlyShowing = false;

	// Copy feedback
	private boolean justCopied = false;
	private long copiedTime = 0;

	public ErrorDialog(UnixInstaller parent)
	{
		super("##ErrorDialog", 363, 251);

		this.parent = parent;

		// Configure modal behavior
		this.isResizable = false;
		this.hasTitleBar = false;
		this.centerOnAppear = true;
	}

	/**
	 * Show error with automatic type detection
	 */
	public void show(Throwable exception)
	{
		show(exception, ErrorType.fromException(exception));
	}

	/**
	 * Show error with explicit type specification
	 */
	public void show(Throwable exception, ErrorType type)
	{
		if(exception == null)
		{
			return;
		}

		// Prepare error data outside of ImGui thread
		String preparedErrorMessage = exception.getMessage();
		String preparedStackTrace;

		try {
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println("Exception Type: " + exception.getClass().getName());
			pw.println("Message: " + exception.getMessage());
			pw.println("\nStack Trace:");
			exception.printStackTrace(pw);
			preparedStackTrace = sw.toString();

			pw.close();
			sw.close();
		} catch(Exception e) {
			preparedStackTrace = "Failed to capture stack trace: " + e.getMessage();
		}

		// Queue the actual dialog setup for the main ImGui thread
		final String finalErrorMessage = preparedErrorMessage;
		final String finalStackTrace = preparedStackTrace;
		final ErrorType finalType = (type != null) ? type : ErrorType.GENERAL;

		parent.getThreadBridge().asyncExec(() -> {
			synchronized(showLock)
			{
				if(isCurrentlyShowing)
				{
					return;
				}
				isCurrentlyShowing = true;
			}

			if(parent.getConfigWindow().isVisible())
			{
				parent.getConfigWindow().forceClose();
			}

			this.errorMessage = finalErrorMessage;
			this.stackTrace = finalStackTrace;
			this.errorType = finalType;
			this.justCopied = false;

			super.show();
		});
	}

	@Override
	protected String getTitle()
	{
		return errorType.defaultTitle;
	}

	@Override
	protected void renderTitleBar()
	{
		float r = 1.0f, g = 0.3f, b = 0.3f; // Default red for errors

		if(errorType == ErrorType.IO)
		{
			// Orange for I/O errors
			g = 0.5f;
		}

		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, r, g, b, 1.0f);
		String titleText = getTitle();
		float titleWidth = ImGui.calcTextSize(titleText).x;
		float windowWidth = ImGui.getWindowWidth();
		float centerX = (windowWidth - titleWidth) * 0.5f;
		ImGui.setCursorPosX(centerX);
		ImGui.text(titleText);
		ImGui.popStyleColor();
	}

	@Override
	protected void renderContent()
	{
		if(justCopied && System.currentTimeMillis() - copiedTime > 2000)
		{
			justCopied = false;
		}

		ImGui.spacing();
		ImGui.text(errorType.getLocalizedTitle());
		ImGui.indent();
		ImGui.pushTextWrapPos(ImGui.getCursorPosX() + windowWidth - 40);

		String defaultMessage = switch(errorType) {
			case NETWORK -> Config.getString("error.default.network");
			case IO -> Config.getString("error.default.io");
			case GENERAL -> Config.getString("error.default.general");
		};

		ImGui.textWrapped(errorMessage != null ? errorMessage : defaultMessage);
		ImGui.popTextWrapPos();
		ImGui.unindent();

		ImGui.spacing();
		ImGui.separator();
		ImGui.spacing();

		ImGui.text(Config.getString("error.technical_details"));

		float currentY = ImGui.getCursorPosY();
		float bottomReserve = BUTTON_HEIGHT + ImGui.getStyle().getItemSpacingY() * 4 + 10;
		float availableHeight = windowHeight - currentY - bottomReserve;

		if(ImGui.beginChild("##StackTraceScroll", 0, availableHeight, true,
				ImGuiWindowFlags.HorizontalScrollbar))
		{
			ImGui.textUnformatted(stackTrace != null ? stackTrace : "No stack trace available");
		}
		ImGui.endChild();
	}

	@Override
	protected boolean hasFooter()
	{
		return true;
	}

	@Override
	protected void renderFooter()
	{
		renderActionButtons();
	}

	private void renderActionButtons()
	{
		float buttonWidth = 100.0f;
		float spacing = ImGui.getStyle().getItemSpacingX();

		boolean showRetry = (errorType == ErrorType.NETWORK);
		int buttonCount = showRetry ? 3 : 2;

		float totalWidth = buttonWidth * buttonCount + spacing * (buttonCount - 1);
		float startX = (windowWidth - totalWidth) * 0.5f;

		ImGui.setCursorPosX(startX);

		if(justCopied)
		{
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.0f, 0.5f, 0.0f, 1.0f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.0f, 0.6f, 0.0f, 1.0f);
			ImGui.button(Config.getString("button.copied"), buttonWidth, BUTTON_HEIGHT);
			ImGui.popStyleColor(2);
		}
		else
		{
			if(ImGui.button(Config.getString("button.copy"), buttonWidth, BUTTON_HEIGHT))
			{
				copyToClipboard();
				justCopied = true;
				copiedTime = System.currentTimeMillis();
			}
		}

		ImGui.sameLine();

		if(showRetry)
		{
			if(ImGui.button(Config.getString("button.retry"), buttonWidth, BUTTON_HEIGHT))
			{
				close();
				parent.retryConnection();
			}

			ImGui.sameLine();
		}

		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.5f, 0.0f, 0.0f, 1.0f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.7f, 0.0f, 0.0f, 1.0f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.4f, 0.0f, 0.0f, 1.0f);
		if(ImGui.button(Config.getString("button.exit"), buttonWidth, BUTTON_HEIGHT))
		{
			System.exit(errorType.exitCode);
		}
		ImGui.popStyleColor(3);
	}

	@Override
	protected void onClose()
	{
		synchronized(showLock)
		{
			isCurrentlyShowing = false;
		}
	}

	private void copyToClipboard()
	{
		String fullDetails = String.format(
				"PokeMMO Error Report\n" +
						"============================\n" +
						"Error Type: %s\n" +
						"Error Message: %s\n\n" +
						"Technical Details:\n" +
						"-------------------\n" +
						"%s\n\n" +
						"System Info:\n" +
						"-------------------\n" +
						"OS: %s %s\n" +
						"Java: %s\n" +
						"Installer Version: %d",
				errorType.name(),
				errorMessage != null ? errorMessage : "Unknown",
				stackTrace != null ? stackTrace : "No stack trace available",
				System.getProperty("os.name"),
				System.getProperty("os.version"),
				System.getProperty("java.version"),
				UnixInstaller.INSTALLER_VERSION
		);

		try
		{
			StringSelection selection = new StringSelection(fullDetails);
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
		}
		catch(Exception e)
		{
			System.err.println("Failed to copy to clipboard: " + e.getMessage());
		}
	}
}