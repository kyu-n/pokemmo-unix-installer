package com.pokeemu.unix.ui;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.enums.PokeMMOLocale;
import com.pokeemu.unix.enums.UpdateChannel;
import com.pokeemu.unix.util.Util;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * @author Kyu
 */
public class ConfigWindow
{
	private final UnixInstaller parent;
	private final ImGuiThreadBridge threadBridge;

	private enum DialogState
	{
		CLOSED,
		OPENING,
		OPEN
	}

	private volatile DialogState dialogState = DialogState.CLOSED;

	private final imgui.type.ImInt selectedLocaleIndex = new imgui.type.ImInt();
	private final imgui.type.ImInt networkThreads = new imgui.type.ImInt();
	private final imgui.type.ImInt selectedChannelIndex = new imgui.type.ImInt();
	private final imgui.type.ImInt maxMemory = new imgui.type.ImInt();
	private boolean aesWorkaround;

	private final int windowWidth;
	private final int windowHeight;

	private static final float LABEL_WIDTH = 200.0f;
	private static final float INPUT_WIDTH = 200.0f;

	private static final int MEMORY_STEP = 128;
	private static final int MEMORY_MIN = 384;
	private static final int MEMORY_MAX = 1536;
	private static final int MEMORY_DEFAULT = 512;

	private final String[] localeNames;
	private final String[] channelNames;

	private static final String POPUP_ID = "##ConfigModal";

	public ConfigWindow(UnixInstaller parent)
	{
		this.parent = parent;
		this.threadBridge = parent.getThreadBridge();

		this.windowWidth = 363;
		this.windowHeight = 251;

		loadCurrentSettings();

		localeNames = new String[PokeMMOLocale.ENABLED_LANGUAGES.length];
		for(int i = 0; i < PokeMMOLocale.ENABLED_LANGUAGES.length; i++)
		{
			localeNames[i] = PokeMMOLocale.ENABLED_LANGUAGES[i].getDisplayName();
		}

		channelNames = new String[UpdateChannel.ENABLED_UPDATE_CHANNELS.length];
		for(int i = 0; i < UpdateChannel.ENABLED_UPDATE_CHANNELS.length; i++)
		{
			channelNames[i] = UpdateChannel.ENABLED_UPDATE_CHANNELS[i].name();
		}
	}

	private void loadCurrentSettings()
	{
		selectedLocaleIndex.set(getLocaleIndex(Config.ACTIVE_LOCALE));
		networkThreads.set(Config.NETWORK_THREADS);
		selectedChannelIndex.set(getUpdateChannelIndex(Config.UPDATE_CHANNEL));

		int memValue = Config.HARD_MAX_MEMORY_MB;
		if(memValue < MEMORY_MIN)
		{
			memValue = MEMORY_DEFAULT;
		}
		else if(memValue > MEMORY_MAX)
		{
			memValue = MEMORY_MAX;
		}
		memValue = ((memValue + MEMORY_STEP / 2) / MEMORY_STEP) * MEMORY_STEP;
		maxMemory.set(memValue);

		aesWorkaround = Config.AES_INTRINSICS_WORKAROUND_ENABLED;
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

		ImGui.setNextWindowSize(windowWidth, windowHeight, imgui.flag.ImGuiCond.Always);

		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, imgui.flag.ImGuiCond.Always, 0.5f, 0.5f);

		if(!ImGui.beginPopupModal(POPUP_ID, null,
				ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize |
						ImGuiWindowFlags.NoTitleBar))
		{
			dialogState = DialogState.CLOSED;
			return;
		}

		renderCustomTitleBar();
		ImGui.separator();

		renderGeneralSettings();
		ImGui.separator();
		renderAdvancedSettings();
		ImGui.separator();
		renderActions();

		ImGui.endPopup();
	}

	/**
	 * Render a custom title bar that updates with language changes
	 */
	private void renderCustomTitleBar()
	{
		String titleText = Config.getString("config.title.window");
		float titleWidth = ImGui.calcTextSize(titleText).x;
		float windowWidth = ImGui.getWindowWidth();
		float centerX = (windowWidth - titleWidth) * 0.5f;

		ImGui.setCursorPosX(centerX);
		ImGui.text(titleText);
	}

	private void closeWindow()
	{
		dialogState = DialogState.CLOSED;
		ImGui.closeCurrentPopup();
	}

	private void renderGeneralSettings()
	{
		ImGui.pushItemWidth(INPUT_WIDTH);

		renderLabeledWidget("config.title.language", () -> {
			boolean enabled = PokeMMOLocale.ENABLED_LANGUAGES.length > 1;
			if(!enabled)
			{
				ImGui.beginDisabled();
			}

			if(ImGui.combo("##Language", selectedLocaleIndex, localeNames))
			{
				PokeMMOLocale newLocale = PokeMMOLocale.ENABLED_LANGUAGES[selectedLocaleIndex.get()];
				if(Config.ACTIVE_LOCALE != newLocale)
				{
					Config.changeLocale(newLocale);
					LocalizationManager.instance.updateLocale();
				}
			}

			if(!enabled)
			{
				ImGui.endDisabled();
			}
		});

		renderLabeledWidget("config.title.dl_threads", () -> {
			int[] threadArray = {networkThreads.get()};
			if(ImGui.sliderInt("##NetworkThreads", threadArray, 1, Config.NETWORK_THREADS_MAX))
			{
				networkThreads.set(threadArray[0]);
				Config.NETWORK_THREADS = networkThreads.get();
				Config.save();
			}
		});

		renderLabeledWidget("config.title.update_channel", () -> {
			boolean enabled = UpdateChannel.ENABLED_UPDATE_CHANNELS.length > 1;
			if(!enabled)
			{
				ImGui.beginDisabled();
			}

			if(ImGui.combo("##UpdateChannel", selectedChannelIndex, channelNames))
			{
				UpdateChannel newChannel = UpdateChannel.ENABLED_UPDATE_CHANNELS[selectedChannelIndex.get()];
				if(Config.UPDATE_CHANNEL != newChannel)
				{
					Config.UPDATE_CHANNEL = newChannel;
					Config.save();
				}
			}

			if(!enabled)
			{
				ImGui.endDisabled();
			}
		});

		ImGui.popItemWidth();
	}

	private void renderAdvancedSettings()
	{
		if(ImGui.collapsingHeader(Config.getString("config.title.advanced")))
		{
			ImGui.indent();

			ImGui.pushItemWidth(INPUT_WIDTH);

			renderLabeledWidget("config.mem.max", () -> {
				if(ImGui.inputInt("##MaxMemory", maxMemory, MEMORY_STEP, MEMORY_STEP * 2))
				{
					int value = maxMemory.get();
					value = Math.max(MEMORY_MIN, Math.min(MEMORY_MAX, value));
					value = ((value + MEMORY_STEP / 2) / MEMORY_STEP) * MEMORY_STEP;
					maxMemory.set(value);
					Config.HARD_MAX_MEMORY_MB = (short) value;
					Config.save();
				}

				ImGui.sameLine();
				ImGui.text("MB");
			});

			renderLabeledWidget("config.title.networking_corruption_workaround", () -> {
				ImGui.beginDisabled();

				ImBoolean aesValue = new ImBoolean(aesWorkaround);
				if(ImGui.checkbox("##AESWorkaround", aesValue))
				{
					aesWorkaround = aesValue.get();
					Config.AES_INTRINSICS_WORKAROUND_ENABLED = aesWorkaround;
					Config.save();
				}

				ImGui.endDisabled();

				ImGui.sameLine();
				renderHelpMarker("config.networking_corruption_workaround.tooltip");
			});

			ImGui.popItemWidth();
			ImGui.unindent();
		}
	}

	private void renderActions()
	{
		boolean isUpdating = parent.isUpdating();
		if(isUpdating)
		{
			ImGui.beginDisabled();
		}

		if(ImGui.button(Config.getString("config.title.open_client_folder")))
		{
			try
			{
				Util.open(parent.getPokemmoDir());
			}
			catch(Exception e)
			{
				threadBridge.showError(
						Config.getString("error.cant_open_client_folder"),
						Config.getString("error.io_exception"),
						null
				);
			}
		}

		ImGui.sameLine();
		float buttonWidth = 100.0f;
		ImGui.setCursorPosX(ImGui.getWindowWidth() - buttonWidth - ImGui.getStyle().getWindowPaddingX());

		if(ImGui.button("Close", buttonWidth, 0))
		{
			closeWindow();
		}

		if(ImGui.button(Config.getString("config.title.repair_client")))
		{
			closeWindow();

			threadBridge.asyncExec(() -> threadBridge.showYesNoDialog(
					Config.getString("status.game_repair_prompt"),
					"Confirm Repair",
					() -> {
						parent.getMainWindow().clearTaskOutput();

						parent.getMainWindow().addTaskLine("Starting client repair...");

						parent.getUpdaterService().startUpdate(true, false);
					},
					null
			));
		}

		if(isUpdating)
		{
			ImGui.endDisabled();

			ImGui.sameLine();
			ImGui.textColored(1.0f, 0.8f, 0.0f, 1.0f, "Updating...");
		}
	}

	/**
	 * Helper method to render a labeled widget with consistent spacing
	 */
	private void renderLabeledWidget(String labelKey, Runnable widgetRenderer)
	{
		ImGui.text(Config.getString(labelKey));
		ImGui.sameLine(LABEL_WIDTH);
		widgetRenderer.run();
	}

	private void renderHelpMarker(String tooltipKey)
	{
		ImGui.textDisabled("(?)");
		if(ImGui.isItemHovered())
		{
			ImGui.beginTooltip();
			ImGui.pushTextWrapPos(ImGui.getFontSize() * 35.0f);
			ImGui.textUnformatted(Config.getString(tooltipKey));
			ImGui.popTextWrapPos();
			ImGui.endTooltip();
		}
	}

	private int getLocaleIndex(PokeMMOLocale locale)
	{
		for(int i = 0; i < PokeMMOLocale.ENABLED_LANGUAGES.length; i++)
		{
			if(PokeMMOLocale.ENABLED_LANGUAGES[i] == locale)
			{
				return i;
			}
		}
		return 0;
	}

	private int getUpdateChannelIndex(UpdateChannel channel)
	{
		for(int i = 0; i < UpdateChannel.ENABLED_UPDATE_CHANNELS.length; i++)
		{
			if(UpdateChannel.ENABLED_UPDATE_CHANNELS[i] == channel)
			{
				return i;
			}
		}
		return 0;
	}

	public void setVisible(boolean visible)
	{
		if(visible)
		{
			loadCurrentSettings();
			dialogState = DialogState.OPENING;
		}
		else
		{
			dialogState = DialogState.CLOSED;
		}
	}

	public boolean isVisible()
	{
		return dialogState != DialogState.CLOSED;
	}
}