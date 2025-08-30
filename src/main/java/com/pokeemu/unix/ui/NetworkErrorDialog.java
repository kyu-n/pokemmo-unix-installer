package com.pokeemu.unix.ui;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicBoolean;

import com.pokeemu.unix.UnixInstaller;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;

/**
 * A dialog UI component for displaying network-related error information to the user.
 * This dialog appears when a network connection failure occurs during installation or update.
 *
 * @author Kyu
 */
public class NetworkErrorDialog
{
	private static final String POPUP_ID = "##NetworkErrorDialog";
	private volatile boolean visible = false;
	private String errorMessage;
	private String stackTrace;
	private final UnixInstaller parent;

	// Prevent multiple show calls from creating duplicate dialogs
	private final AtomicBoolean isShowing = new AtomicBoolean(false);
	private volatile boolean wasVisible = false;

	// Match config window dimensions
	private static final int WINDOW_WIDTH = 363;
	private static final int WINDOW_HEIGHT = 251;
	private static final float BUTTON_HEIGHT = 25.0f;

	// Track if we've copied to clipboard for user feedback
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

		// Prevent multiple simultaneous show calls
		if(!isShowing.compareAndSet(false, true))
		{
			// Already showing or preparing to show
			return;
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

			// Close StringWriter and PrintWriter
			pw.close();
			try
			{
				sw.close();
			}
			catch(Exception e)
			{
				// Ignore
			}

			this.visible = true;
			this.justCopied = false;
		}
		finally
		{
			// Reset the showing flag after setup is complete
			isShowing.set(false);
		}
	}

	public void render()
	{
		if(!visible)
		{
			// If we were visible last frame, ensure popup is closed
			if(wasVisible)
			{
				if(ImGui.isPopupOpen(POPUP_ID))
				{
					ImGui.closeCurrentPopup();
				}
				wasVisible = false;
			}
			return;
		}

		// Check if we should clear the "Copied!" feedback
		if(justCopied && System.currentTimeMillis() - copiedTime > 2000)
		{
			justCopied = false;
		}

		// Open popup only when transitioning from not visible to visible
		if(!wasVisible)
		{
			ImGui.openPopup(POPUP_ID);
			wasVisible = true;
		}

		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
		ImGui.setNextWindowSize(WINDOW_WIDTH, WINDOW_HEIGHT);

		if(ImGui.beginPopupModal(POPUP_ID, null,
				ImGuiWindowFlags.NoResize | ImGuiWindowFlags.NoTitleBar))
		{

			// Custom title bar
			renderCustomTitleBar();
			ImGui.separator();

			// Error message section
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

			// Technical details in scrollable area
			ImGui.text("Technical Details:");

			// Calculate available height for scroll area
			float currentY = ImGui.getCursorPosY();
			float bottomReserve = BUTTON_HEIGHT + ImGui.getStyle().getItemSpacingY() * 4 + 10;
			float availableHeight = WINDOW_HEIGHT - currentY - bottomReserve;

			// Scrollable area for stack trace
			if(ImGui.beginChild("##StackTraceScroll", 0, availableHeight, true,
					ImGuiWindowFlags.HorizontalScrollbar))
			{
				ImGui.textUnformatted(stackTrace != null ? stackTrace : "No stack trace available");
			}
			ImGui.endChild();

			ImGui.separator();

			// Action buttons
			renderActionButtons();

			ImGui.endPopup();
		}
		else
		{
			// Popup was closed externally (shouldn't happen with modal, but handle it)
			visible = false;
			wasVisible = false;
		}
	}

	private void renderCustomTitleBar()
	{
		// Red error icon and title
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

		// Three buttons with equal spacing
		float totalWidth = buttonWidth * 3 + spacing * 2;
		float startX = (WINDOW_WIDTH - totalWidth) * 0.5f;

		ImGui.setCursorPosX(startX);

		// Copy button - show feedback if recently copied
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

		// Retry button
		if(ImGui.button("Retry", buttonWidth, BUTTON_HEIGHT))
		{
			closeDialog();
			parent.retryConnection();
		}

		ImGui.sameLine();

		// Exit button with red styling
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
		visible = false;
		wasVisible = false;
		ImGui.closeCurrentPopup();
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
		return visible;
	}
}