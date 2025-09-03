package com.pokeemu.unix.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.pokeemu.unix.config.Config;

public class ImGuiThreadBridge implements IProgressReporter
{
	private final ConcurrentLinkedQueue<Runnable> uiUpdates = new ConcurrentLinkedQueue<>();
	private final ConcurrentLinkedQueue<TaskMessage> taskOutput = new ConcurrentLinkedQueue<>();
	private final AtomicReference<String> statusMessage = new AtomicReference<>("");
	private final AtomicInteger progress = new AtomicInteger(0);
	private final AtomicReference<String> downloadSpeed = new AtomicReference<>("");

	private final ConcurrentLinkedQueue<DialogRequest> dialogRequests = new ConcurrentLinkedQueue<>();

	private static final int MAX_UI_UPDATES_PER_FRAME = 20;
	private static final int MAX_TASK_LINES_PER_FRAME = 50;
	private static final int MAX_DIALOGS_PER_FRAME = 3;

	public record TaskMessage(String text, LogLevel level) {};

	public void asyncExec(Runnable runnable)
	{
		if(runnable != null)
		{
			uiUpdates.offer(runnable);
		}
	}

	public void processUpdates()
	{
		Runnable update;
		int processed = 0;

		while((update = uiUpdates.poll()) != null && processed < MAX_UI_UPDATES_PER_FRAME)
		{
			try
			{
				update.run();
			}
			catch(Exception e)
			{
				System.err.println("Error processing UI update: " + e.getMessage());
				e.printStackTrace();
			}
			processed++;
		}

		DialogRequest dialog;
		int dialogsProcessed = 0;
		while((dialog = dialogRequests.poll()) != null && dialogsProcessed < MAX_DIALOGS_PER_FRAME)
		{
			try
			{
				dialog.show();
			}
			catch(Exception e)
			{
				System.err.println("Error showing dialog: " + e.getMessage());
				e.printStackTrace();
			}
			dialogsProcessed++;
		}
	}

	@Override
	public void setStatus(String message, int progressValue)
	{
		if(message != null)
		{
			statusMessage.set(message);
		}
		if(progressValue >= 0 && progressValue <= 100)
		{
			progress.set(progressValue);
		}
	}

	@Override
	public void addDetail(String messageKey, int progressValue, Object... params)
	{
		try
		{
			String formatted = Config.getString(messageKey, params);
			if(!formatted.isEmpty())
			{
				LogLevel level = determineLogLevelFromKey(messageKey);
				taskOutput.offer(new TaskMessage(formatted, level));
			}

			if(progressValue >= 0 && progressValue <= 100)
			{
				progress.set(progressValue);
			}
		}
		catch(Exception e)
		{
			System.err.println("Error formatting message: " + messageKey);
			e.printStackTrace();
		}
	}

	@Override
	public void setDownloadSpeed(String speed)
	{
		if(speed != null)
		{
			downloadSpeed.set(speed);
		}
	}

	@Override
	public void showInfo(String messageKey, Object... params)
	{
		addDetailWithLevel(messageKey, LogLevel.INFO, -1, params);
	}

	@Override
	public void showError(String message, String title, Runnable onClose)
	{
		if(message != null)
		{
			dialogRequests.offer(new DialogRequest(DialogType.ERROR, message, title, onClose));
			taskOutput.offer(new TaskMessage("ERROR: " + message, LogLevel.ERROR));
		}
	}

	/**
	 * Add a detail message with explicit log level
	 */
	public void addDetailWithLevel(String messageKey, LogLevel level, int progressValue, Object... params)
	{
		try
		{
			String formatted = Config.getString(messageKey, params);
			if(!formatted.isEmpty())
			{
				taskOutput.offer(new TaskMessage(formatted, level));
			}

			if(progressValue >= 0 && progressValue <= 100)
			{
				progress.set(progressValue);
			}
		}
		catch(Exception e)
		{
			System.err.println("Error formatting message: " + messageKey);
			e.printStackTrace();
		}
	}

	/**
	 * Add a raw task line with explicit log level
	 */
	public void addTaskLine(String text, LogLevel level)
	{
		if(text != null && !text.trim().isEmpty())
		{
			taskOutput.offer(new TaskMessage(text, level));
		}
	}

	/**
	 * Determine log level from message key patterns
	 */
	private LogLevel determineLogLevelFromKey(String messageKey)
	{
		if(messageKey == null)
		{
			return LogLevel.INFO;
		}

		// Check for semantic patterns in message keys
		String key = messageKey.toLowerCase();

		if(key.contains("error") ||
				key.contains("failed") ||
				key.contains("fail") ||
				key.contains("fatal") ||
				key.contains("corrupt") ||
				key.contains("invalid") ||
				key.contains("not_accessible") ||
				key.contains("not_found") ||
				key.contains("exception") ||
				key.contains("cant_") ||
				key.contains("cannot_") ||
				key.contains("unable"))
		{
			return LogLevel.ERROR;
		}
		else if(key.contains("warning") ||
				key.contains("warn") ||
				key.contains("repair") ||
				key.contains("retry") ||
				key.contains("timeout") ||
				key.contains("slow"))
		{
			return LogLevel.WARNING;
		}
		else if(key.contains("success") ||
				key.contains("complete") ||
				key.contains("verified") ||
				key.contains("ready") ||
				key.contains("ok") ||
				key.contains("done"))
		{
			return LogLevel.SUCCESS;
		}
		else if(key.contains("debug") ||
				key.contains("trace"))
		{
			return LogLevel.DEBUG;
		}
		else
		{
			return LogLevel.INFO;
		}
	}

	public void showMessage(String message, String title, Runnable onClose)
	{
		if(message != null)
		{
			dialogRequests.offer(new DialogRequest(DialogType.INFO, message, title, onClose));
		}
	}

	public void showYesNoDialog(String message, String title, Runnable onYes, Runnable onNo)
	{
		if(message != null)
		{
			dialogRequests.offer(new DialogRequest(DialogType.YES_NO, message, title, onYes, onNo));
		}
	}

	public String getStatusMessage()
	{
		return statusMessage.get();
	}

	public int getProgress()
	{
		return Math.max(0, Math.min(100, progress.get()));
	}

	public String getDownloadSpeed()
	{
		return downloadSpeed.get();
	}

	public List<TaskMessage> getAndClearTaskOutput()
	{
		List<TaskMessage> messages = new ArrayList<>();
		TaskMessage msg;
		int count = 0;

		while((msg = taskOutput.poll()) != null && count < MAX_TASK_LINES_PER_FRAME)
		{
			messages.add(msg);
			count++;
		}

		return messages;
	}

	public void clearPendingUpdates()
	{
		uiUpdates.clear();
		taskOutput.clear();
		dialogRequests.clear();
	}

	public static class DialogRequest
	{
		private final DialogType type;
		private final String message;
		private final String title;
		private final Runnable onYes;
		private final Runnable onNo;

		public DialogRequest(DialogType type, String message, String title, Runnable onYes)
		{
			this(type, message, title, onYes, null);
		}

		public DialogRequest(DialogType type, String message, String title, Runnable onYes, Runnable onNo)
		{
			this.type = type;
			this.message = message;
			this.title = title;
			this.onYes = onYes;
			this.onNo = onNo;
		}

		public void show()
		{
			MessageDialog.getInstance().showDialog(type, message, title, onYes, onNo);
		}
	}

	public enum DialogType
	{
		INFO,
		ERROR,
		YES_NO,
		WARNING
	}
}