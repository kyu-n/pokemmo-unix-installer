package com.pokeemu.unix.ui;

import java.util.LinkedList;
import java.util.Queue;

import com.pokeemu.unix.UnixInstaller;

import com.pokeemu.unix.config.Config;

import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;

public class MessageDialog extends AbstractModalWindow
{
	private static MessageDialog INSTANCE;

	private UnixInstaller parent;

	private ImGuiThreadBridge.DialogType currentType;
	private String currentMessage;
	private String currentTitle;
	private Runnable onYes;
	private Runnable onNo;

	private final Queue<QueuedDialog> dialogQueue = new LinkedList<>();

	private static final String POPUP_INFO = "##InfoDialog";
	private static final String POPUP_ERROR = "##ErrorDialog";
	private static final String POPUP_YES_NO = "##YesNoDialog";
	private static final String POPUP_WARNING = "##WarningDialog";

	private static final float MIN_WIDTH = 400.0f;
	private static final float MAX_WIDTH = 600.0f;

	private MessageDialog()
	{
		super(POPUP_INFO, 500, 200);

		this.isResizable = true;
		this.hasTitleBar = false;
		this.centerOnAppear = true;
	}

	public static MessageDialog getInstance()
	{
		if(INSTANCE == null)
		{
			INSTANCE = new MessageDialog();
		}

		return INSTANCE;
	}

	public void setParent(UnixInstaller parent)
	{
		this.parent = parent;
	}

	@Override
	public void render()
	{
		if(parent != null)
		{
			boolean otherModalOpen = (parent.getConfigWindow() != null && parent.getConfigWindow().isVisible()) ||
					(parent.getNetworkErrorDialog() != null && parent.getNetworkErrorDialog().isVisible());

			if(otherModalOpen)
			{
				return;
			}
		}

		if(dialogState == DialogState.CLOSED && !dialogQueue.isEmpty())
		{
			QueuedDialog next = dialogQueue.poll();
			if(next != null)
			{
				showDialog(next.type, next.message, next.title, next.onYes, next.onNo);
			}
		}

		super.render();
	}

	@Override
	protected void setupWindow()
	{
		if(centerOnAppear)
		{
			imgui.ImVec2 center = ImGui.getMainViewport().getCenter();
			ImGui.setNextWindowPos(center.x, center.y,
					imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
		}

		ImGui.setNextWindowSizeConstraints(MIN_WIDTH, 0, MAX_WIDTH, Float.MAX_VALUE);
	}

	@Override
	protected int buildWindowFlags()
	{
		return ImGuiWindowFlags.AlwaysAutoResize |
				ImGuiWindowFlags.NoSavedSettings |
				ImGuiWindowFlags.NoTitleBar |
				ImGuiWindowFlags.NoMove;
	}

	@Override
	protected String getTitle()
	{
		return currentTitle != null ? currentTitle : "Message";
	}

	@Override
	protected void renderTitleBar()
	{
		switch(currentType)
		{
			case ERROR -> {
				ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.2f, 0.2f, 1.0f);
				ImGui.text("[!] ");
				ImGui.popStyleColor();
				ImGui.sameLine();
			}
			case WARNING -> {
				ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, 1.0f, 0.8f, 0.0f, 1.0f);
				ImGui.text("[!] ");
				ImGui.popStyleColor();
				ImGui.sameLine();
			}
			default -> {
				// INFO and YES_NO use normal text
			}
		}

		ImGui.text(getTitle());
	}

	@Override
	protected void renderContent()
	{
		ImGui.pushTextWrapPos(ImGui.getCursorPosX() + MIN_WIDTH);
		ImGui.textWrapped(currentMessage != null ? currentMessage : "");
		ImGui.popTextWrapPos();

		ImGui.spacing();
	}

	@Override
	protected boolean hasFooter()
	{
		return true;
	}

	@Override
	protected void renderFooter()
	{
		if(currentType == ImGuiThreadBridge.DialogType.YES_NO)
		{
			renderYesNoButtons();
		}
		else
		{
			renderOkButton();
		}
	}

	private void renderOkButton()
	{
		float buttonPosX = (ImGui.getWindowWidth() - BUTTON_WIDTH) * 0.5f;
		ImGui.setCursorPosX(buttonPosX);

		if(ImGui.button(Config.getString("button.ok"), BUTTON_WIDTH, BUTTON_HEIGHT))
		{
			handleOk();
		}
	}

	private void renderYesNoButtons()
	{
		float totalButtonWidth = BUTTON_WIDTH * 2 + ImGui.getStyle().getItemSpacingX();
		float buttonPosX = (ImGui.getWindowWidth() - totalButtonWidth) * 0.5f;
		ImGui.setCursorPosX(buttonPosX);

		if(ImGui.button(Config.getString("button.yes"), BUTTON_WIDTH, BUTTON_HEIGHT))
		{
			handleYes();
		}

		ImGui.sameLine();

		if(ImGui.button(Config.getString("button.no"), BUTTON_WIDTH, BUTTON_HEIGHT))
		{
			handleNo();
		}
	}

	private void handleOk()
	{
		if(onYes != null && currentType != ImGuiThreadBridge.DialogType.YES_NO)
		{
			onYes.run();
		}

		close();
	}

	private void handleYes()
	{
		if(onYes != null)
		{
			onYes.run();
		}

		close();
	}

	private void handleNo()
	{
		if(onNo != null)
		{
			onNo.run();
		}

		close();
	}

	@Override
	protected void onClose()
	{
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

		switch(type)
		{
			case INFO -> this.popupId = POPUP_INFO;
			case ERROR -> this.popupId = POPUP_ERROR;
			case YES_NO -> this.popupId = POPUP_YES_NO;
			case WARNING -> this.popupId = POPUP_WARNING;
		}

		if(parent != null)
		{
			if(parent.getConfigWindow() != null && parent.getConfigWindow().isVisible())
			{
				parent.getConfigWindow().forceClose();
			}
		}

		if(isVisible())
		{
			dialogQueue.offer(new QueuedDialog(type, message, title, onYes, onNo));
		}
		else
		{
			this.currentType = type;
			this.currentMessage = message;
			this.currentTitle = title;
			this.onYes = onYes;
			this.onNo = onNo;

			super.show();
		}
	}

	public void showInfo(String message, String title)
	{
		showDialog(ImGuiThreadBridge.DialogType.INFO, message, title, null, null);
	}

	public void showError(String message, String title, Runnable onClose)
	{
		showDialog(ImGuiThreadBridge.DialogType.ERROR, message, title, onClose, null);
	}

	public void showWarning(String message, String title)
	{
		showDialog(ImGuiThreadBridge.DialogType.WARNING, message, title, null, null);
	}

	public void showYesNo(String message, String title, Runnable onYes, Runnable onNo)
	{
		showDialog(ImGuiThreadBridge.DialogType.YES_NO, message, title, onYes, onNo);
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