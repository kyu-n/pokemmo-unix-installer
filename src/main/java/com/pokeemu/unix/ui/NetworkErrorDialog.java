package com.pokeemu.unix.ui;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

/**
 * Network error dialog refactored to extend AbstractModalWindow.
 * Thread-safe implementation ensures all ImGui operations happen on main thread.
 */
public class NetworkErrorDialog extends AbstractModalWindow
{
	private final UnixInstaller parent;

	// Error details
	private String errorMessage;
	private String stackTrace;

	// Thread safety
	private final Object showLock = new Object();
	private volatile boolean isCurrentlyShowing = false;

	// Copy feedback
	private boolean justCopied = false;
	private long copiedTime = 0;

	public NetworkErrorDialog(UnixInstaller parent)
	{
		super("##NetworkErrorDialog", 363, 251);

		this.parent = parent;

		// Configure modal behavior
		this.isResizable = false;
		this.hasTitleBar = false;
		this.centerOnAppear = true;
	}

	/**
	 * Thread-safe show method that prepares error data and queues UI update.
	 */
	public void show(Throwable exception)
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

		parent.getThreadBridge().asyncExec(() -> {
			synchronized(showLock)
			{
				if(isCurrentlyShowing)
				{
					return;
				}
				isCurrentlyShowing = true;
			}

			this.errorMessage = finalErrorMessage;
			this.stackTrace = finalStackTrace;
			this.justCopied = false;

			super.show();
		});
	}

	@Override
	protected String getTitle()
	{
		return "Network Error";
	}

	@Override
	protected void renderTitleBar()
	{
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.3f, 0.3f, 1.0f);
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
		ImGui.text(Config.getString("status.title.network_failure"));
		ImGui.indent();
		ImGui.pushTextWrapPos(ImGui.getCursorPosX() + windowWidth - 40);
		ImGui.textWrapped(errorMessage != null ? errorMessage : "Failed to connect to update servers");
		ImGui.popTextWrapPos();
		ImGui.unindent();

		ImGui.spacing();
		ImGui.separator();
		ImGui.spacing();

		ImGui.text("Technical Details:");

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

		float totalWidth = buttonWidth * 3 + spacing * 2;
		float startX = (windowWidth - totalWidth) * 0.5f;

		ImGui.setCursorPosX(startX);

		if(justCopied)
		{
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.0f, 0.5f, 0.0f, 1.0f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.0f, 0.6f, 0.0f, 1.0f);
			ImGui.button("button.copied", buttonWidth, BUTTON_HEIGHT);
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

		if(ImGui.button("Retry", buttonWidth, BUTTON_HEIGHT))
		{
			close();
			parent.retryConnection();
		}

		ImGui.sameLine();

		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.5f, 0.0f, 0.0f, 1.0f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.7f, 0.0f, 0.0f, 1.0f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.4f, 0.0f, 0.0f, 1.0f);
		if(ImGui.button(Config.getString("button.exit"), buttonWidth, BUTTON_HEIGHT))
		{
			System.exit(UnixInstaller.EXIT_CODE_NETWORK_FAILURE);
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
		String fullDetails = "PokeMMO Network Error Report\n" +
				"============================\n" +
				"Error Message: " + (errorMessage != null ? errorMessage : "Unknown") + "\n\n" +
				"Technical Details:\n" +
				"-------------------\n" +
				(stackTrace != null ? stackTrace : "No stack trace available") + "\n\n" +
				"System Info:\n" +
				"-------------------\n" +
				"OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + "\n" +
				"Java: " + System.getProperty("java.version") + "\n" +
				"Installer Version: " + UnixInstaller.INSTALLER_VERSION;

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