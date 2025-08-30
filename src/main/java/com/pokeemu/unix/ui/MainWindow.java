package com.pokeemu.unix.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;

import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;

public class MainWindow
{
	private final UnixInstaller parent;
	private final ImGuiThreadBridge threadBridge;

	private final ConcurrentLinkedDeque<TaskLine> taskLines = new ConcurrentLinkedDeque<>();
	private final AtomicBoolean canStart = new AtomicBoolean(false);

	// Use atomic counter for task line count
	private final AtomicInteger taskLineCount = new AtomicInteger(0);

	private final int windowWidth;
	private final int windowHeight;

	private static final int MAX_TASK_LINES = 1000;
	private static final float BUTTON_HEIGHT = 25.0f;
	private static final float STATUS_PANEL_HEIGHT = 60.0f;
	private static final float BOTTOM_PANEL_HEIGHT = 40.0f;

	private static final float[] COLOR_ERROR = {1.0f, 0.3f, 0.3f, 1.0f};
	private static final float[] COLOR_WARNING = {1.0f, 0.8f, 0.3f, 1.0f};
	private static final float[] COLOR_SUCCESS = {0.3f, 1.0f, 0.3f, 1.0f};
	private static final float[] COLOR_INFO = {0.7f, 0.7f, 1.0f, 1.0f};

	private boolean needsScrollToBottom = false;

	private static class TaskLine
	{
		final String text;
		final LogLevel level;
		final long timestamp;

		TaskLine(String text, LogLevel level)
		{
			this.text = text;
			this.level = level;
			this.timestamp = System.currentTimeMillis();
		}
	}

	private enum LogLevel
	{
		INFO, SUCCESS, WARNING, ERROR
	}

	public MainWindow(UnixInstaller parent, ImGuiThreadBridge threadBridge, int width, int height)
	{
		this.parent = parent;
		this.threadBridge = threadBridge;
		this.windowWidth = width;
		this.windowHeight = height;

		threadBridge.setStatus(Config.getString("main.loading"), 0);
	}

	public void render()
	{
		ImGui.setNextWindowSize(windowWidth, windowHeight, imgui.flag.ImGuiCond.FirstUseEver);

		ImVec2 center = ImGui.getMainViewport().getCenter();
		ImGui.setNextWindowPos(center.x, center.y, imgui.flag.ImGuiCond.FirstUseEver, 0.5f, 0.5f);

		float originalRounding = ImGui.getStyle().getWindowRounding();
		ImGui.getStyle().setWindowRounding(0.0f);

		int windowFlags = ImGuiWindowFlags.NoCollapse |
				ImGuiWindowFlags.NoTitleBar |
				ImGuiWindowFlags.NoMove |
				ImGuiWindowFlags.NoResize |
				ImGuiWindowFlags.NoSavedSettings;

		if(ImGui.begin("##MainWindow", windowFlags))
		{
			renderTopPanel();
			ImGui.separator();
			renderTaskOutput();
			ImGui.separator();
			renderBottomPanel();
		}
		ImGui.end();

		ImGui.getStyle().setWindowRounding(originalRounding);
	}

	private void renderTopPanel()
	{
		ImGui.beginChild("##TopPanel", 0, STATUS_PANEL_HEIGHT, false, ImGuiWindowFlags.NoScrollbar);

		String status = threadBridge.getStatusMessage();
		if(status != null && !status.isEmpty())
		{
			ImGui.text("Status: " + status);
		}
		else
		{
			ImGui.text("Status: Ready");
		}

		float progress = threadBridge.getProgress() / 100.0f;
		ImGui.text("Progress:");
		ImGui.sameLine();

		float availableWidth = ImGui.getContentRegionAvailX();
		float progressBarWidth = Math.min(availableWidth * 0.7f, 400.0f);

		String progressText = progress > 0 ? String.format("%.0f%%", progress * 100) : "";
		ImGui.progressBar(progress, progressBarWidth, 0, progressText);

		String dlSpeed = threadBridge.getDownloadSpeed();
		if(dlSpeed != null && !dlSpeed.isEmpty() && !dlSpeed.equals("0 B/s"))
		{
			ImGui.sameLine();
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, COLOR_INFO[0], COLOR_INFO[1], COLOR_INFO[2], COLOR_INFO[3]);
			ImGui.text("[" + dlSpeed + "]");
			ImGui.popStyleColor();
		}

		ImGui.endChild();
	}

	private void renderTaskOutput()
	{
		float availableHeight = ImGui.getContentRegionAvailY() - BOTTOM_PANEL_HEIGHT - ImGui.getStyle().getItemSpacingY() * 2;

		if(ImGui.beginChild("##TaskOutput", 0, availableHeight, true,
				ImGuiWindowFlags.HorizontalScrollbar | ImGuiWindowFlags.AlwaysVerticalScrollbar))
		{

			processNewTaskLines();
			trimOldLines();
			renderTaskLines();

			if(needsScrollToBottom)
			{
				ImGui.setScrollHereY(1.0f);
				needsScrollToBottom = false;
			}
		}
		ImGui.endChild();
	}

	private void processNewTaskLines()
	{
		List<String> newLines = threadBridge.getAndClearTaskOutput();
		if(!newLines.isEmpty())
		{
			for(String line : newLines)
			{
				if(line != null && !line.trim().isEmpty())
				{
					LogLevel level = determineLogLevel(line);
					TaskLine taskLine = new TaskLine(line, level);

					// Atomically add line and increment counter
					taskLines.addLast(taskLine);
					taskLineCount.incrementAndGet();
				}
			}
			needsScrollToBottom = true;
		}
	}

	private LogLevel determineLogLevel(String line)
	{
		String upperLine = line.toUpperCase();
		if(upperLine.contains("ERROR") || upperLine.contains("FAILED") || upperLine.contains("FATAL"))
		{
			return LogLevel.ERROR;
		}
		else if(upperLine.contains("WARNING") || upperLine.contains("WARN"))
		{
			return LogLevel.WARNING;
		}
		else if(upperLine.contains("SUCCESS") || upperLine.contains("COMPLETE") || upperLine.contains("OK"))
		{
			return LogLevel.SUCCESS;
		}
		else
		{
			return LogLevel.INFO;
		}
	}

	private void trimOldLines()
	{
		// Use atomic counter for thread-safe size check
		int currentCount = taskLineCount.get();

		while(currentCount > MAX_TASK_LINES)
		{
			TaskLine removed = taskLines.pollFirst();
			if(removed != null)
			{
				currentCount = taskLineCount.decrementAndGet();
			}
			else
			{
				// Queue is empty, reset counter
				taskLineCount.set(0);
				break;
			}
		}
	}

	private void renderTaskLines()
	{
		ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.ItemSpacing, 0, 2);

		// Create a snapshot for rendering to avoid concurrent modification issues
		List<TaskLine> snapshot = new ArrayList<>(taskLines);

		for(TaskLine taskLine : snapshot)
		{
			switch(taskLine.level)
			{
				case ERROR:
					ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, COLOR_ERROR[0], COLOR_ERROR[1], COLOR_ERROR[2], COLOR_ERROR[3]);
					break;
				case WARNING:
					ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, COLOR_WARNING[0], COLOR_WARNING[1], COLOR_WARNING[2], COLOR_WARNING[3]);
					break;
				case SUCCESS:
					ImGui.pushStyleColor(imgui.flag.ImGuiCol.Text, COLOR_SUCCESS[0], COLOR_SUCCESS[1], COLOR_SUCCESS[2], COLOR_SUCCESS[3]);
					break;
				default:
					break;
			}

			ImGui.textUnformatted(taskLine.text);

			if(taskLine.level != LogLevel.INFO)
			{
				ImGui.popStyleColor();
			}
		}

		ImGui.popStyleVar();
	}

	private void renderBottomPanel()
	{
		ImGui.beginChild("##BottomPanel", 0, BUTTON_HEIGHT, false, ImGuiWindowFlags.NoScrollbar);

		if(ImGui.button(Config.getString("config.title.window"), 120, BUTTON_HEIGHT))
		{
			parent.getConfigWindow().setVisible(true);
		}

		float buttonWidth = 120;
		float rightAlignX = ImGui.getWindowWidth() - buttonWidth - ImGui.getStyle().getWindowPaddingX();

		ImGui.sameLine();
		ImGui.setCursorPosX(rightAlignX);

		boolean enabled = canStart.get() && !parent.isUpdating();

		if(!enabled)
		{
			ImGui.pushStyleVar(imgui.flag.ImGuiStyleVar.Alpha, 0.6f);
		}

		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.1f, 0.4f, 0.1f, 1.0f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.2f, 0.5f, 0.2f, 1.0f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.45f, 0.15f, 1.0f);

		boolean shouldLaunch = ImGui.button(Config.getString("main.launch"), buttonWidth, BUTTON_HEIGHT);

		ImGui.popStyleColor(3);

		if(!enabled)
		{
			ImGui.popStyleVar();
		}

		if(shouldLaunch && enabled)
		{
			parent.launchGame();
		}

		if(ImGui.isItemHovered() && !enabled)
		{
			ImGui.beginTooltip();
			if(parent.isUpdating())
			{
				ImGui.text(Config.getString("status.downloading"));
			}
			else
			{
				ImGui.text(Config.getString("main.loading"));
			}
			ImGui.endTooltip();
		}

		ImGui.endChild();
	}

	public void setCanStart(boolean canStart)
	{
		this.canStart.set(canStart);

		if(UnixInstaller.QUICK_AUTOSTART && canStart)
		{
			parent.launchGame();
		}
	}

	public boolean isCanStart()
	{
		return canStart.get();
	}

	public void clearTaskOutput()
	{
		taskLines.clear();
		taskLineCount.set(0);
		needsScrollToBottom = false;
	}

	public void addTaskLine(String line)
	{
		if(line != null && !line.trim().isEmpty())
		{
			LogLevel level = determineLogLevel(line);
			TaskLine taskLine = new TaskLine(line, level);

			// Atomically add and track count
			taskLines.addLast(taskLine);
			int newCount = taskLineCount.incrementAndGet();

			// Trim if needed
			if(newCount > MAX_TASK_LINES)
			{
				trimOldLines();
			}

			needsScrollToBottom = true;
		}
	}
}