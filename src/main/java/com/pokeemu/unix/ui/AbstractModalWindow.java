package com.pokeemu.unix.ui;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;

public abstract class AbstractModalWindow
{
	protected enum DialogState
	{
		CLOSED,
		OPENING,
		OPEN
	}

	protected volatile DialogState dialogState = DialogState.CLOSED;
	protected String popupId;
	protected final int windowWidth;
	protected final int windowHeight;

	protected static final float BUTTON_HEIGHT = 25.0f;
	protected static final float BUTTON_WIDTH = 100.0f;

	protected boolean centerOnAppear = true;
	protected boolean isResizable = false;
	protected boolean hasTitleBar = false;

	/**
	 * Creates a new modal window with specified dimensions.
	 *
	 * @param popupId Unique identifier for this popup (should start with ##)
	 * @param width Window width in pixels
	 * @param height Window height in pixels
	 */
	protected AbstractModalWindow(String popupId, int width, int height)
	{
		this.popupId = popupId;
		this.windowWidth = width;
		this.windowHeight = height;
	}

	/**
	 * Shows the modal window. Transitions from CLOSED to OPENING state.
	 */
	public void show()
	{
		if(dialogState == DialogState.CLOSED)
		{
			dialogState = DialogState.OPENING;
			onShow();
		}
	}

	/**
	 * Closes the modal window and resets state.
	 */
	public void close()
	{
		dialogState = DialogState.CLOSED;
		ImGui.closeCurrentPopup();
		onClose();
	}

	/**
	 * Force closes the modal without calling ImGui.closeCurrentPopup().
	 * Used when closing from outside the render loop.
	 */
	public void forceClose()
	{
		dialogState = DialogState.CLOSED;
		onClose();
	}

	/**
	 * Returns whether the modal is currently visible.
	 */
	public boolean isVisible()
	{
		return dialogState != DialogState.CLOSED;
	}

	/**
	 * Sets whether the modal is visible.
	 */
	public void setVisible(boolean visible)
	{
		if(visible)
		{
			show();
		}
		else
		{
			dialogState = DialogState.CLOSED;
		}
	}

	/**
	 * Main render method. Handles state transitions and window setup.
	 */
	public void render()
	{
		switch(dialogState)
		{
			case CLOSED:
				return;

			case OPENING:
				ImGui.openPopup(popupId);
				dialogState = DialogState.OPEN;
				break;

			case OPEN:
				break;
		}

		setupWindow();

		if(ImGui.beginPopupModal(popupId, null, buildWindowFlags()))
		{
			if(!hasTitleBar)
			{
				renderTitleBar();
				ImGui.separator();
			}

			renderContent();

			if(hasFooter())
			{
				ImGui.separator();
				renderFooter();
			}

			ImGui.endPopup();
		}
		else
		{
			close();
		}
	}

	/**
	 * Sets up window position and size before rendering.
	 */
	protected void setupWindow()
	{
		if(centerOnAppear)
		{
			ImVec2 center = ImGui.getMainViewport().getCenter();
			ImGui.setNextWindowPos(center.x, center.y,
					imgui.flag.ImGuiCond.Appearing, 0.5f, 0.5f);
		}

		if(!isResizable)
		{
			ImGui.setNextWindowSize(windowWidth, windowHeight);
		}
		else
		{
			ImGui.setNextWindowSize(windowWidth, windowHeight,
					imgui.flag.ImGuiCond.FirstUseEver);
		}
	}

	/**
	 * Builds ImGui window flags based on modal configuration.
	 */
	protected int buildWindowFlags()
	{
		int flags = 0;

		if(!isResizable)
		{
			flags |= ImGuiWindowFlags.NoResize;
		}

		if(!hasTitleBar)
		{
			flags |= ImGuiWindowFlags.NoTitleBar;
		}

		flags |= ImGuiWindowFlags.NoCollapse;
		flags |= ImGuiWindowFlags.NoMove;
		flags |= ImGuiWindowFlags.NoSavedSettings;

		return flags;
	}

	/**
	 * Renders a custom title bar when native title bar is disabled.
	 * Default implementation centers the title text.
	 */
	protected void renderTitleBar()
	{
		String titleText = getTitle();
		float titleWidth = ImGui.calcTextSize(titleText).x;
		float windowWidth = ImGui.getWindowWidth();
		float centerX = (windowWidth - titleWidth) * 0.5f;

		ImGui.setCursorPosX(centerX);
		ImGui.text(titleText);
	}

	/**
	 * Helper method to center a button horizontally.
	 */
	protected void centerButton(String label, float buttonWidth)
	{
		float windowWidth = ImGui.getWindowWidth();
		float centerX = (windowWidth - buttonWidth) * 0.5f;
		ImGui.setCursorPosX(centerX);

		if(ImGui.button(label, buttonWidth, BUTTON_HEIGHT))
		{
			onButtonClick(label);
		}
	}

	/**
	 * Helper method to render multiple buttons in a row, centered.
	 */
	protected void centerButtons(String[] labels, float buttonWidth)
	{
		float spacing = ImGui.getStyle().getItemSpacingX();
		float totalWidth = labels.length * buttonWidth + (labels.length - 1) * spacing;
		float startX = (ImGui.getWindowWidth() - totalWidth) * 0.5f;

		ImGui.setCursorPosX(startX);

		for(int i = 0; i < labels.length; i++)
		{
			if(i > 0)
			{
				ImGui.sameLine();
			}

			if(ImGui.button(labels[i], buttonWidth, BUTTON_HEIGHT))
			{
				onButtonClick(labels[i]);
			}
		}
	}

	/**
	 * Helper method to render a labeled control with consistent spacing.
	 */
	protected void renderLabeledControl(String label, float labelWidth, Runnable controlRenderer)
	{
		ImGui.text(label);
		ImGui.sameLine(labelWidth);
		controlRenderer.run();
	}

	// Abstract methods that subclasses must implement

	/**
	 * Returns the title text for the modal window.
	 */
	protected abstract String getTitle();

	/**
	 * Renders the main content of the modal.
	 */
	protected abstract void renderContent();

	/**
	 * Called when a button is clicked. Default implementation does nothing.
	 */
	protected void onButtonClick(String buttonLabel)
	{
		// Default: do nothing, subclasses can override
	}

	// Optional methods that subclasses can override

	/**
	 * Returns whether this modal has a footer section.
	 */
	protected boolean hasFooter()
	{
		return false;
	}

	/**
	 * Renders the footer section if hasFooter() returns true.
	 */
	protected void renderFooter()
	{
		// Default: do nothing
	}

	/**
	 * Called when the modal is shown. Subclasses can override to initialize state.
	 */
	protected void onShow()
	{
		// Default: do nothing
	}

	/**
	 * Called when the modal is closed. Subclasses can override to clean up state.
	 */
	protected void onClose()
	{
		// Default: do nothing
	}
}