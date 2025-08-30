package com.pokeemu.unix.ui;

import java.util.LinkedList;
import java.util.Queue;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;

public class MessageDialog
{
	private static final MessageDialog INSTANCE = new MessageDialog();

	private volatile boolean showDialog = false;
	private ImGuiThreadBridge.DialogType currentType;
	private String currentMessage;
	private String currentTitle;
	private Runnable onYes;
	private Runnable onNo;

	private final Queue<QueuedDialog> dialogQueue = new LinkedList<>();

	private static final String POPUP_INFO = "InfoDialog";
	private static final String POPUP_ERROR = "ErrorDialog";
	private static final String POPUP_YES_NO = "YesNoDialog";

	private static final float MIN_DIALOG_WIDTH = 400.0f;
	private static final float MAX_DIALOG_WIDTH = 600.0f;
	private static final float BUTTON_WIDTH = 120.0f;

	private MessageDialog()
	{
	}

	public static MessageDialog getInstance()
	{
		return INSTANCE;
	}

	public void render()
	{
		if(!showDialog && !dialogQueue.isEmpty())
		{
			QueuedDialog next = dialogQueue.poll();
			if(next != null)
			{
				showDialogInternal(next.type, next.message, next.title, next.onYes, next.onNo);
			}
		}

		if(!showDialog)
		{
			return;
		}

		if(currentType != null)
		{
			switch(currentType)
			{
				case INFO -> renderInfoDialog();
				case ERROR -> renderErrorDialog();
				case YES_NO -> renderYesNoDialog();
				case WARNING -> renderWarningDialog();
			}
		}
	}

	private void renderInfoDialog()
	{
		if(!ImGui.isPopupOpen(POPUP_INFO))
		{
			ImGui.openPopup(POPUP_INFO);
		}

		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
		ImGui.setNextWindowSizeConstraints(MIN_DIALOG_WIDTH, 0, MAX_DIALOG_WIDTH, Float.MAX_VALUE);

		if(ImGui.beginPopupModal(POPUP_INFO, null,
				ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings))
		{

			ImGui.text(currentTitle != null ? currentTitle : "Information");
			ImGui.separator();

			ImGui.pushTextWrapPos(ImGui.getCursorPosX() + MIN_DIALOG_WIDTH);
			ImGui.textWrapped(currentMessage != null ? currentMessage : "");
			ImGui.popTextWrapPos();

			ImGui.spacing();
			ImGui.separator();
			ImGui.spacing();

			float buttonPosX = (ImGui.getWindowWidth() - BUTTON_WIDTH) * 0.5f;
			ImGui.setCursorPosX(buttonPosX);

			if(ImGui.button("OK", BUTTON_WIDTH, 0))
			{
				closeDialog();
				ImGui.closeCurrentPopup();
			}

			ImGui.endPopup();
		}
	}

	private void renderErrorDialog()
	{
		if(!ImGui.isPopupOpen(POPUP_ERROR))
		{
			ImGui.openPopup(POPUP_ERROR);
		}

		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
		ImGui.setNextWindowSizeConstraints(MIN_DIALOG_WIDTH, 0, MAX_DIALOG_WIDTH, Float.MAX_VALUE);

		if(ImGui.beginPopupModal(POPUP_ERROR, null,
				ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings))
		{
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.2f, 0.2f, 1.0f);
			ImGui.text("[!] ");
			ImGui.popStyleColor();

			ImGui.sameLine();
			ImGui.text(currentTitle != null ? currentTitle : "Error");
			ImGui.separator();

			ImGui.pushTextWrapPos(ImGui.getCursorPosX() + MIN_DIALOG_WIDTH);
			ImGui.textWrapped(currentMessage != null ? currentMessage : "");
			ImGui.popTextWrapPos();

			ImGui.spacing();
			ImGui.separator();
			ImGui.spacing();

			float buttonPosX = (ImGui.getWindowWidth() - BUTTON_WIDTH) * 0.5f;
			ImGui.setCursorPosX(buttonPosX);

			if(ImGui.button("OK", BUTTON_WIDTH, 0))
			{
				closeDialog();
				ImGui.closeCurrentPopup();
			}

			ImGui.endPopup();
		}
	}

	private void renderYesNoDialog()
	{
		if(!ImGui.isPopupOpen(POPUP_YES_NO))
		{
			ImGui.openPopup(POPUP_YES_NO);
		}

		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
		ImGui.setNextWindowSizeConstraints(MIN_DIALOG_WIDTH, 0, MAX_DIALOG_WIDTH, Float.MAX_VALUE);

		if(ImGui.beginPopupModal(POPUP_YES_NO, null,
				ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings))
		{

			ImGui.text(currentTitle != null ? currentTitle : "Confirm");
			ImGui.separator();

			ImGui.pushTextWrapPos(ImGui.getCursorPosX() + MIN_DIALOG_WIDTH);
			ImGui.textWrapped(currentMessage != null ? currentMessage : "");
			ImGui.popTextWrapPos();

			ImGui.spacing();
			ImGui.separator();
			ImGui.spacing();

			float totalButtonWidth = BUTTON_WIDTH * 2 + ImGui.getStyle().getItemSpacingX();
			float buttonPosX = (ImGui.getWindowWidth() - totalButtonWidth) * 0.5f;
			ImGui.setCursorPosX(buttonPosX);

			if(ImGui.button("Yes", BUTTON_WIDTH, 0))
			{
				if(onYes != null)
				{
					onYes.run();
				}
				closeDialog();
				ImGui.closeCurrentPopup();
			}

			ImGui.sameLine();

			if(ImGui.button("No", BUTTON_WIDTH, 0))
			{
				if(onNo != null)
				{
					onNo.run();
				}
				closeDialog();
				ImGui.closeCurrentPopup();
			}

			ImGui.endPopup();
		}
	}

	private void renderWarningDialog()
	{
		if(!ImGui.isPopupOpen(POPUP_INFO))
		{
			ImGui.openPopup(POPUP_INFO);
		}

		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
		ImGui.setNextWindowSizeConstraints(MIN_DIALOG_WIDTH, 0, MAX_DIALOG_WIDTH, Float.MAX_VALUE);

		if(ImGui.beginPopupModal(POPUP_INFO, null,
				ImGuiWindowFlags.AlwaysAutoResize | ImGuiWindowFlags.NoSavedSettings))
		{

			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.8f, 0.0f, 1.0f);
			ImGui.text("[!] ");
			ImGui.popStyleColor();

			ImGui.sameLine();
			ImGui.text(currentTitle != null ? currentTitle : "Warning");
			ImGui.separator();

			ImGui.pushTextWrapPos(ImGui.getCursorPosX() + MIN_DIALOG_WIDTH);
			ImGui.textWrapped(currentMessage != null ? currentMessage : "");
			ImGui.popTextWrapPos();

			ImGui.spacing();
			ImGui.separator();
			ImGui.spacing();

			float buttonPosX = (ImGui.getWindowWidth() - BUTTON_WIDTH) * 0.5f;
			ImGui.setCursorPosX(buttonPosX);

			if(ImGui.button("OK", BUTTON_WIDTH, 0))
			{
				closeDialog();
				ImGui.closeCurrentPopup();
			}

			ImGui.endPopup();
		}
	}

	private void closeDialog()
	{
		showDialog = false;

		if(onYes != null && currentType != ImGuiThreadBridge.DialogType.YES_NO)
		{
			onYes.run();
		}

		currentType = null;
		currentMessage = null;
		currentTitle = null;
		onYes = null;
		onNo = null;
	}

	public void showDialog(ImGuiThreadBridge.DialogType type, String message,
						   String title, Runnable onYes, Runnable onNo)
	{
		if(type == null || message == null)
		{
			return;
		}

		if(showDialog)
		{
			dialogQueue.offer(new QueuedDialog(type, message, title, onYes, onNo));
		}
		else
		{
			showDialogInternal(type, message, title, onYes, onNo);
		}
	}

	private void showDialogInternal(ImGuiThreadBridge.DialogType type, String message,
									String title, Runnable onYes, Runnable onNo)
	{
		this.currentType = type;
		this.currentMessage = message;
		this.currentTitle = title;
		this.onYes = onYes;
		this.onNo = onNo;
		this.showDialog = true;
	}

	private static class QueuedDialog
	{
		final ImGuiThreadBridge.DialogType type;
		final String message;
		final String title;
		final Runnable onYes;
		final Runnable onNo;

		QueuedDialog(ImGuiThreadBridge.DialogType type, String message,
					 String title, Runnable onYes, Runnable onNo)
		{
			this.type = type;
			this.message = message;
			this.title = title;
			this.onYes = onYes;
			this.onNo = onNo;
		}
	}
}