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
	private final ConcurrentLinkedQueue<String> taskOutput = new ConcurrentLinkedQueue<>();
	private final AtomicReference<String> statusMessage = new AtomicReference<>("");
	private final AtomicInteger progress = new AtomicInteger(0);
	private final AtomicReference<String> downloadSpeed = new AtomicReference<>("");

	private final ConcurrentLinkedQueue<DialogRequest> dialogRequests = new ConcurrentLinkedQueue<>();

	private static final int MAX_UI_UPDATES_PER_FRAME = 20;
	private static final int MAX_TASK_LINES_PER_FRAME = 50;
	private static final int MAX_DIALOGS_PER_FRAME = 3;

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
				taskOutput.offer(formatted);
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
		addDetail(messageKey, -1, params);
	}

	@Override
	public void showError(String message, String title, Runnable onClose)
	{
		if(message != null)
		{
			dialogRequests.offer(new DialogRequest(DialogType.ERROR, message, title, onClose));
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

	public List<String> getAndClearTaskOutput()
	{
		List<String> lines = new ArrayList<>();
		String line;
		int count = 0;

		while((line = taskOutput.poll()) != null && count < MAX_TASK_LINES_PER_FRAME)
		{
			lines.add(line);
			count++;
		}

		return lines;
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