package com.pokeemu.unix.ui;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;

import com.pokeemu.unix.UnixInstaller;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;

/**
 * A dialog UI component for displaying network-related error information to the user.
 * This dialog appears when a network connection failure occurs during installation or update.
 */
public class NetworkErrorDialog
{
	private static final String POPUP_ID = "##NetworkErrorDialog";

	private enum DialogState
	{
		CLOSED,
		OPENING,
		OPEN
	}

	private volatile DialogState dialogState = DialogState.CLOSED;
	private String errorMessage;
	private String stackTrace;
	private final UnixInstaller parent;

	private final Object showLock = new Object();
	private volatile boolean isCurrentlyShowing = false;

	private static final int WINDOW_WIDTH = 363;
	private static final int WINDOW_HEIGHT = 251;
	private static final float BUTTON_HEIGHT = 25.0f;

	private boolean justCopied = false;
	private long copiedTime = 0;

	public NetworkErrorDialog(UnixInstaller parent)
	{
		this.parent = parent;
	}

	public void show(Throwable exception)
	{
		if(exception == null)
		{
			return;
		}

		synchronized(showLock)
		{
			if(isCurrentlyShowing)
			{
				return;
			}
			isCurrentlyShowing = true;
		}

		try
		{
			this.errorMessage = exception.getMessage();

			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			pw.println("Exception Type: " + exception.getClass().getName());
			pw.println("Message: " + exception.getMessage());
			pw.println("\nStack Trace:");
			exception.printStackTrace(pw);
			this.stackTrace = sw.toString();

			pw.close();
			try
			{
				sw.close();
			}
			catch(Exception ignored)
			{
			}

			this.dialogState = DialogState.OPENING;
			this.justCopied = false;
		}
		catch(Exception e)
		{
			System.err.println("Failed to setup network error dialog: " + e.getMessage());
			synchronized(showLock)
			{
				isCurrentlyShowing = false;
			}
		}
	}

	public void render()
	{
		switch(dialogState)
		{
			case CLOSED:
				return;

			case OPENING:
				ImGui.openPopup(POPUP_ID);
				dialogState = DialogState.OPEN;
				break;

			case OPEN:
				break;
		}

		if(justCopied && System.currentTimeMillis() - copiedTime > 2000)
		{
			justCopied = false;
		}

		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
		ImGui.setNextWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);

		if(ImGui.beginPopupModal(POPUP_ID, null,
				ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoTitleBar))
		{

			renderCustomTitleBar();
			ImGui.separator();

			ImGui.spacing();
			ImGui.text("Error:");
			ImGui.indent();
			ImGui.pushTextWrapPos(ImGui.getCursorPosX() + WINDOW_WIDTH - 40);
			ImGui.textWrapped(errorMessage != null ? errorMessage : "Failed to connect to update servers");
			ImGui.popTextWrapPos();
			ImGui.unindent();

			ImGui.spacing();
			ImGui.separator();
			ImGui.spacing();

			ImGui.text("Technical Details:");

			float currentY = ImGui.getCursorPosY();
			float bottomReserve = BUTTON_HEIGHT + ImGui.getStyle().getItemSpacingY() * 4 + 10;
			float availableHeight = WINDOW_HEIGHT - currentY - bottomReserve;

			if(ImGui.beginChild("##StackTraceScroll", 0, availableHeight, true,
					ImGuiWindowFlags.HorizontalScrollbar))
			{
				ImGui.textUnformatted(stackTrace != null ? stackTrace : "No stack trace available");
			}
			ImGui.endChild();

			ImGui.separator();

			renderActionButtons();

			ImGui.endPopup();
		}
		else
		{
			closeDialog();
		}
	}

	private void renderCustomTitleBar()
	{
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.3f, 0.3f, 1.0f);
		String titleText = "Network Error";
		float titleWidth = ImGui.calcTextSize(titleText).x;
		float windowWidth = ImGui.getWindowWidth();
		float centerX = (windowWidth - titleWidth) * 0.5f;
		ImGui.setCursorPosX(centerX);
		ImGui.text(titleText);
		ImGui.popStyleColor();
	}

	private void renderActionButtons()
	{
		float buttonWidth = 100.0f;
		float spacing = ImGui.getStyle().getItemSpacingX();

		float totalWidth = buttonWidth * 3 + spacing * 2;
		float startX = (WINDOW_WIDTH - totalWidth) * 0.5f;

		ImGui.setCursorPosX(startX);

		if(justCopied)
		{
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.0f, 0.5f, 0.0f, 1.0f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.0f, 0.6f, 0.0f, 1.0f);
			ImGui.button("Copied!", buttonWidth, BUTTON_HEIGHT);
			ImGui.popStyleColor(2);
		}
		else
		{
			if(ImGui.button("Copy Details", buttonWidth, BUTTON_HEIGHT))
			{
				copyToClipboard();
				justCopied = true;
				copiedTime = System.currentTimeMillis();
			}
		}

		ImGui.sameLine();

		if(ImGui.button("Retry", buttonWidth, BUTTON_HEIGHT))
		{
			closeDialog();
			parent.retryConnection();
		}

		ImGui.sameLine();

		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.5f, 0.0f, 0.0f, 1.0f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.7f, 0.0f, 0.0f, 1.0f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.4f, 0.0f, 0.0f, 1.0f);
		if(ImGui.button("Exit", buttonWidth, BUTTON_HEIGHT))
		{
			System.exit(UnixInstaller.EXIT_CODE_NETWORK_FAILURE);
		}
		ImGui.popStyleColor(3);
	}

	private void closeDialog()
	{
		dialogState = DialogState.CLOSED;
		ImGui.closeCurrentPopup();

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

	public boolean isVisible()
	{
		return dialogState != DialogState.CLOSED;
	}
}