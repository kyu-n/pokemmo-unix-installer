package com.pokeemu.unix.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.enums.PokeMMOLocale;
import com.pokeemu.unix.enums.UpdateChannel;
import com.pokeemu.unix.updater.UpdaterJob;
import com.pokeemu.unix.util.Util;

/**
 * @author Kyu
 */
public class MainFrame
{
	private final UnixInstaller parent;
	private final Shell shell;
	private final Display display;

	protected final LocaleAwareLabel status;
	protected final Label dlSpeed;

	protected LocaleAwareButton launchGame;
	protected LocaleAwareButton configLauncher;

	private final ProgressBar progressBar;
	private final LocaleAwareText taskOutput;

	private final ExecutorService executorService;

	private int downloaded_bytes;
	private long last_download_progress_update = System.currentTimeMillis(), last_download_speed_update, last_download_speed_update_bytes;

	private static MainFrame instance;

	private final Shell configWindow;

	public static MainFrame getInstance()
	{
		return instance;
	}

	public MainFrame(UnixInstaller parent)
	{
		instance = this;

		this.parent = parent;
		this.executorService = Executors.newFixedThreadPool(1);
		this.display = Display.getDefault();

		shell = new Shell(display);
		shell.setSize(480, 280);
		shell.setLayout(new GridLayout(1, false));

		// Set title using locale-aware approach
		updateShellTitle();

		// Center the shell
		shell.setLocation(
				(display.getBounds().width - shell.getSize().x) / 2,
				(display.getBounds().height - shell.getSize().y) / 2
		);

		// Top panel with status and progress
		Composite topPanel = new Composite(shell, SWT.NONE);
		topPanel.setLayout(new GridLayout(3, false));
		topPanel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		status = new LocaleAwareLabel(topPanel, SWT.NONE);
		status.setTextKey("main.loading");
		status.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

		progressBar = new ProgressBar(topPanel, SWT.SMOOTH);
		progressBar.setMinimum(0);
		progressBar.setMaximum(100);
		progressBar.setSelection(0);
		progressBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		dlSpeed = new Label(topPanel, SWT.NONE);
		dlSpeed.setText("");
		dlSpeed.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));

		// Task output area
		ScrolledComposite scrolledComposite = new ScrolledComposite(shell, SWT.V_SCROLL | SWT.H_SCROLL | SWT.BORDER);
		scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		taskOutput = new LocaleAwareText(scrolledComposite, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP);

		// Set monospace font
		FontData[] fontData = taskOutput.getFont().getFontData();
		for(FontData fd : fontData)
		{
			fd.setName("Courier New");
		}
		Font monoFont = new Font(display, fontData);
		taskOutput.setFont(monoFont);

		scrolledComposite.setContent(taskOutput);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);

		// Bottom panel with buttons
		Composite bottomPanel = new Composite(shell, SWT.NONE);
		bottomPanel.setLayout(new GridLayout(2, false));
		bottomPanel.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

		configLauncher = new LocaleAwareButton(bottomPanel, SWT.PUSH);
		configLauncher.setTextKey("config.title.window");
		configLauncher.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		configLauncher.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				configWindow.setVisible(true);
			}
		});

		launchGame = new LocaleAwareButton(bottomPanel, SWT.PUSH);
		launchGame.setTextKey("main.launch");
		launchGame.setEnabled(false);
		launchGame.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));

		// Configuration window
		configWindow = createConfigWindow();

		shell.addDisposeListener(e -> {
			monoFont.dispose();
			display.dispose();
			System.exit(UnixInstaller.EXIT_CODE_SUCCESS);
		});

		shell.setVisible(!UnixInstaller.QUICK_AUTOSTART);
	}

	private void updateShellTitle()
	{
		shell.setText(Config.getString("main.title"));
	}

	private Shell createConfigWindow()
	{
		Shell configShell = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		configShell.setSize(500, 340);
		configShell.setLayout(new GridLayout(1, false));

		// Update config window title
		updateConfigWindowTitle(configShell);

		// Center config window relative to main window
		configShell.setLocation(
				shell.getLocation().x + (shell.getSize().x - configShell.getSize().x) / 2,
				shell.getLocation().y + (shell.getSize().y - configShell.getSize().y) / 2
		);

		Composite configContent = new Composite(configShell, SWT.NONE);
		configContent.setLayout(new GridLayout(2, false));
		configContent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		// Language setting
		LocaleAwareLabel localeLabel = new LocaleAwareLabel(configContent, SWT.NONE);
		localeLabel.setTextKey("config.title.language");

		Combo localeCombo = new Combo(configContent, SWT.READ_ONLY);
		for(PokeMMOLocale locale : PokeMMOLocale.ENABLED_LANGUAGES)
		{
			localeCombo.add(locale.toString());
		}
		localeCombo.select(getLocaleIndex(Config.ACTIVE_LOCALE));
		localeCombo.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				Config.changeLocale(PokeMMOLocale.ENABLED_LANGUAGES[localeCombo.getSelectionIndex()]);
				// Update all locale-aware elements
				LocaleAwareElementManager.instance.updateElements();
				// Update shell titles manually since they're not in the manager
				updateShellTitle();
				updateConfigWindowTitle(configShell);
			}
		});
		if(PokeMMOLocale.ENABLED_LANGUAGES.length < 2)
		{
			localeCombo.setEnabled(false);
		}

		// Network threads setting
		LocaleAwareLabel networkThreadsLabel = new LocaleAwareLabel(configContent, SWT.NONE);
		networkThreadsLabel.setTextKey("config.title.dl_threads");

		Spinner networkThreadsSpinner = new Spinner(configContent, SWT.BORDER);
		networkThreadsSpinner.setMinimum(1);
		networkThreadsSpinner.setMaximum(Config.NETWORK_THREADS_MAX);
		networkThreadsSpinner.setSelection(Config.NETWORK_THREADS);
		networkThreadsSpinner.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				Config.NETWORK_THREADS = networkThreadsSpinner.getSelection();
				Config.save();
			}
		});

		// Update channel setting
		LocaleAwareLabel updateChannelLabel = new LocaleAwareLabel(configContent, SWT.NONE);
		updateChannelLabel.setTextKey("config.title.update_channel");

		Combo updateChannelCombo = new Combo(configContent, SWT.READ_ONLY);
		for(UpdateChannel channel : UpdateChannel.values())
		{
			updateChannelCombo.add(channel.toString());
		}
		updateChannelCombo.select(getUpdateChannelIndex(Config.UPDATE_CHANNEL));
		updateChannelCombo.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				Config.UPDATE_CHANNEL = UpdateChannel.values()[updateChannelCombo.getSelectionIndex()];
				parent.doUpdate(false);
				Config.save();
			}
		});
		updateChannelCombo.setEnabled(UpdateChannel.ENABLED_UPDATE_CHANNELS.length > 1);

		// Advanced section
		LocaleAwareGroup advancedGroup = new LocaleAwareGroup(configContent, SWT.NONE);
		advancedGroup.setTextKey("config.title.advanced");
		advancedGroup.setLayout(new GridLayout(2, false));
		GridData advancedData = new GridData(SWT.FILL, SWT.TOP, true, false);
		advancedData.horizontalSpan = 2;
		advancedGroup.setLayoutData(advancedData);

		// Memory setting
		LocaleAwareLabel memoryMaxLabel = new LocaleAwareLabel(advancedGroup, SWT.NONE);
		memoryMaxLabel.setTextKey("config.mem.max");

		Spinner memoryMaxSpinner = new Spinner(advancedGroup, SWT.BORDER);
		memoryMaxSpinner.setMinimum(Config.JOPTS_XMX_VAL_MIN);
		memoryMaxSpinner.setMaximum(Config.JOPTS_XMX_VAL_MAX);
		memoryMaxSpinner.setIncrement(128);
		memoryMaxSpinner.setSelection(Config.HARD_MAX_MEMORY_MB);
		memoryMaxSpinner.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				Config.HARD_MAX_MEMORY_MB = (short) memoryMaxSpinner.getSelection();
				Config.save();
			}
		});

		// AES workaround setting
		LocaleAwareLabel aesWorkaroundLabel = new LocaleAwareLabel(advancedGroup, SWT.NONE);
		aesWorkaroundLabel.setTextKey("config.title.networking_corruption_workaround");

		Button aesWorkaroundCheck = new Button(advancedGroup, SWT.CHECK);
		aesWorkaroundCheck.setSelection(Config.AES_INTRINSICS_WORKAROUND_ENABLED);
		aesWorkaroundCheck.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				Config.AES_INTRINSICS_WORKAROUND_ENABLED = aesWorkaroundCheck.getSelection();
				Config.save();
			}
		});
		aesWorkaroundCheck.setEnabled(false);
		aesWorkaroundCheck.setToolTipText(Config.getString("config.networking_corruption_workaround.tooltip"));

		// Action buttons
		LocaleAwareButton openClientFolder = new LocaleAwareButton(advancedGroup, SWT.PUSH);
		openClientFolder.setTextKey("config.title.open_client_folder");
		openClientFolder.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				Util.open(parent.getPokemmoDir());
			}
		});

		LocaleAwareButton repairClientFolder = new LocaleAwareButton(advancedGroup, SWT.PUSH);
		repairClientFolder.setTextKey("config.title.repair_client");
		repairClientFolder.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				if(showYesNoDialogue(Config.getString("status.game_repair_prompt"), Config.getString("config.title.repair_client")))
				{
					configShell.setVisible(false);
					new UpdaterJob(parent, MainFrame.this, true, true).schedule();
				}
			}
		});

		return configShell;
	}

	private void updateConfigWindowTitle(Shell configShell)
	{
		configShell.setText(Config.getString("config.title.window"));
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
		for(int i = 0; i < UpdateChannel.values().length; i++)
		{
			if(UpdateChannel.values()[i] == channel)
			{
				return i;
			}
		}
		return 0;
	}

	public Shell getShell()
	{
		return shell;
	}

	public void setStatus(final String string, int progress, Object... params)
	{
		if(Display.getCurrent() != null)
		{
			status.setTextKey(string);
		}
		else
		{
			display.asyncExec(() -> status.setTextKey(string));
		}

		addDetail(string, progress, params);
	}

	public void addDetail(final String string, final int progress, Object... params)
	{
		if(Display.getCurrent() != null)
		{
			addDetailPrivate(string, progress, params);
		}
		else
		{
			display.asyncExec(() -> addDetailPrivate(string, progress, params));
		}
	}

	protected void addDetailPrivate(String string, int progress, Object... params)
	{
		if(progress > 0)
		{
			progressBar.setSelection(progress);
		}

		if(string != null)
		{
			taskOutput.appendLocaleStr(string);
			taskOutput.appendLocaleStr("\n");

			// Auto-scroll to bottom
			taskOutput.setTopIndex(taskOutput.getLineCount() - 1);
		}

		shell.layout(true, true);
	}

	public void updateDLSpeed(final long bytes_per_second)
	{
		if(Display.getCurrent() != null)
		{
			dlSpeed.setText(humanReadableByteCount(bytes_per_second, false) + "/s");
		}
		else
		{
			display.asyncExec(() -> dlSpeed.setText(humanReadableByteCount(bytes_per_second, false) + "/s"));
		}
	}

	public static String humanReadableByteCount(long bytes, boolean si)
	{
		int unit = si ? 1000 : 1024;
		if(bytes < unit) return bytes + " B";
		int exp = (int) (Math.log(bytes) / Math.log(unit));
		String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
		return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}

	public void showMessage(String message, String window_title)
	{
		showMessage(message, window_title, null);
	}

	public void showMessage(String message, String window_title, Runnable runnable)
	{
		showMessage(message, window_title, SWT.ICON_INFORMATION, runnable);
	}

	public void showError(String message, String window_title)
	{
		showError(message, window_title, null);
	}

	public void showError(String message, String window_title, Runnable runnable)
	{
		showMessage(message, window_title, SWT.ICON_ERROR, runnable);
	}

	public void showErrorWithStacktrace(String message, String window_title, String stacktrace, Runnable runnable)
	{
		showMessageWithTextArea(message, window_title, stacktrace, SWT.ICON_ERROR, runnable);
	}

	public void showErrorWithStacktrace(String message, String window_title, Throwable throwable, Runnable runnable)
	{
		showMessageWithTextArea(message, window_title, parent.getStacktraceString(throwable), SWT.ICON_ERROR, runnable);
	}

	public void showErrorWithStacktrace(String message, String window_title, Throwable[] throwables, Runnable runnable)
	{
		showMessageWithTextArea(message, window_title, parent.getStacktraceString(throwables), SWT.ICON_ERROR, runnable);
	}

	public void showInfo(String message, Object... params)
	{
		addDetail(message, 90, params);
	}

	public boolean showYesNoDialogue(String message, String window_title)
	{
		MessageBox messageBox = new MessageBox(shell, SWT.YES | SWT.NO | SWT.ICON_WARNING);
		messageBox.setMessage(message);
		messageBox.setText(window_title);
		return messageBox.open() == SWT.YES;
	}

	public void showMessage(String message, String window_title, int style, Runnable runnable)
	{
		if(Display.getCurrent() != null)
		{
			MessageBox messageBox = new MessageBox(shell, SWT.OK | style);
			messageBox.setMessage(message);
			messageBox.setText(window_title);
			messageBox.open();
		}
		else
		{
			display.asyncExec(() -> {
				MessageBox messageBox = new MessageBox(shell, SWT.OK | style);
				messageBox.setMessage(message);
				messageBox.setText(window_title);
				messageBox.open();
			});
		}

		if(runnable != null)
			executorService.execute(runnable);
	}

	public void showMessageWithTextArea(String message, String window_title, String textAreaContents, int style, Runnable runnable)
	{
		Runnable showDialog = () -> {
			Shell dialogShell = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
			dialogShell.setText(window_title);
			dialogShell.setSize(500, 300);
			dialogShell.setLayout(new GridLayout(1, false));

			Label messageLabel = new Label(dialogShell, SWT.WRAP);
			messageLabel.setText(message);
			messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

			LocaleAwareText textArea = new LocaleAwareText(dialogShell, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			textArea.setText(textAreaContents);
			textArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			LocaleAwareButton okButton = new LocaleAwareButton(dialogShell, SWT.PUSH);
			okButton.setTextKey("button.ok");
			okButton.setLayoutData(new GridData(SWT.CENTER, SWT.BOTTOM, false, false));
			okButton.addSelectionListener(new SelectionAdapter()
			{
				@Override
				public void widgetSelected(SelectionEvent e)
				{
					dialogShell.dispose();
				}
			});

			dialogShell.setDefaultButton(okButton);
			dialogShell.setLocation(
					shell.getLocation().x + (shell.getSize().x - dialogShell.getSize().x) / 2,
					shell.getLocation().y + (shell.getSize().y - dialogShell.getSize().y) / 2
			);

			dialogShell.open();
		};

		if(Display.getCurrent() != null)
		{
			showDialog.run();
		}
		else
		{
			display.asyncExec(showDialog);
		}

		if(runnable != null)
			executorService.execute(runnable);
	}

	public void showDownloadProgress(int bytes)
	{
		downloaded_bytes += bytes;

		if(System.currentTimeMillis() - last_download_progress_update > 100)
		{
			last_download_progress_update = System.currentTimeMillis();
		}

		if(last_download_speed_update == 0)
		{
			last_download_speed_update = System.currentTimeMillis();
		}

		if(System.currentTimeMillis() - last_download_speed_update > 1000)
		{
			last_download_speed_update = System.currentTimeMillis();

			if(last_download_speed_update_bytes > 0)
			{
				updateDLSpeed(downloaded_bytes - last_download_speed_update_bytes);
			}

			last_download_speed_update_bytes = downloaded_bytes;
		}
	}

	public void setCanStart()
	{
		if(Display.getCurrent() != null)
		{
			launchGame.setEnabled(true);
		}
		else
		{
			display.asyncExec(() -> launchGame.setEnabled(true));
		}

		if(UnixInstaller.QUICK_AUTOSTART)
		{
			parent.launchGame();
		}
		else
		{
			launchGame.addSelectionListener(new SelectionAdapter()
			{
				@Override
				public void widgetSelected(SelectionEvent e)
				{
					parent.launchGame();
				}
			});
		}
	}
}