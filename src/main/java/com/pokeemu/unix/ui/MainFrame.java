package com.pokeemu.unix.ui;

import static com.pokeemu.unix.ui.LocaleAwareElementManager.*;

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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.enums.PokeMMOLocale;
import com.pokeemu.unix.enums.UpdateChannel;
import com.pokeemu.unix.updater.UpdaterJob;
import com.pokeemu.unix.util.Util;

public class MainFrame
{
	private final UnixInstaller parent;
	private final Shell shell;
	private final Display display;

	protected final Label status;
	protected final Label dlSpeed;
	protected Button launchGame;
	protected Button configLauncher;

	private final ProgressBar progressBar;
	private final Text taskOutput;

	private static MainFrame instance;

	private volatile Shell configWindow;
	private final Object configLock = new Object();
	private final Font monoFont;

	public static MainFrame getInstance()
	{
		return instance;
	}

	public MainFrame(UnixInstaller parent)
	{
		instance = this;

		this.parent = parent;
		this.display = Display.getDefault();

		shell = new Shell(display);
		shell.setMinimumSize(480, 280);
		shell.setSize(480, 280);
		shell.setLayout(new GridLayout(1, false));

		// Bind shell title to locale - this also registers the shell for relayout
		bindShellTitle(shell, "main.title");

		// Center the shell
		shell.setLocation(
				(display.getBounds().width - shell.getSize().x) / 2,
				(display.getBounds().height - shell.getSize().y) / 2
		);

		// Top panel with status and progress
		Composite topPanel = new Composite(shell, SWT.NONE);
		topPanel.setLayout(new GridLayout(3, false));
		topPanel.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		status = createLabel(topPanel, SWT.NONE, "main.loading");
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

		taskOutput = createText(scrolledComposite, SWT.MULTI | SWT.READ_ONLY | SWT.WRAP);

		// Set monospace font
		FontData[] fontData = taskOutput.getFont().getFontData();
		for(FontData fd : fontData)
		{
			fd.setName("Courier New");
		}
		monoFont = new Font(display, fontData);
		taskOutput.setFont(monoFont);

		scrolledComposite.setContent(taskOutput);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);

		// Bottom panel with buttons
		Composite bottomPanel = new Composite(shell, SWT.NONE);
		bottomPanel.setLayout(new GridLayout(2, false));
		bottomPanel.setLayoutData(new GridData(SWT.FILL, SWT.BOTTOM, true, false));

		configLauncher = createButton(bottomPanel, SWT.PUSH, "config.title.window");
		configLauncher.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		configLauncher.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				getConfigWindow().setVisible(true);
			}
		});

		launchGame = createButton(bottomPanel, SWT.PUSH, "main.launch");
		launchGame.setEnabled(false);
		launchGame.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
		launchGame.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				parent.launchGame();
			}
		});

		shell.addDisposeListener(e -> {
			if(!monoFont.isDisposed())
			{
				monoFont.dispose();
			}

			if(configWindow != null && !configWindow.isDisposed())
			{
				configWindow.dispose();
			}

			display.dispose();
			System.exit(UnixInstaller.EXIT_CODE_SUCCESS);
		});

		shell.setVisible(!UnixInstaller.QUICK_AUTOSTART);
	}

	private Shell getConfigWindow()
	{
		if(configWindow == null || configWindow.isDisposed())
		{
			synchronized(configLock)
			{
				if(configWindow == null || configWindow.isDisposed())
				{
					configWindow = createConfigWindow();
				}
			}
		}
		return configWindow;
	}

	private Shell createConfigWindow()
	{
		Shell configShell = new Shell(shell, SWT.DIALOG_TRIM | SWT.APPLICATION_MODAL);
		configShell.setMinimumSize(500, 340); // Set minimum size
		configShell.setSize(500, 340);
		configShell.setLayout(new GridLayout(1, false));

		// Bind config window title - this also registers for relayout
		bindShellTitle(configShell, "config.title.window");

		// Center config window relative to main window
		configShell.setLocation(
				shell.getLocation().x + (shell.getSize().x - configShell.getSize().x) / 2,
				shell.getLocation().y + (shell.getSize().y - configShell.getSize().y) / 2
		);

		Composite configContent = new Composite(configShell, SWT.NONE);
		configContent.setLayout(new GridLayout(2, false));
		configContent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		// Language setting
		createLabel(configContent, SWT.NONE, "config.title.language");

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
				LocaleAwareElementManager.instance.updateAll();

				// Re-center windows after relayout
				Display.getDefault().asyncExec(() -> {
					// Re-center main window
					if(!shell.isDisposed())
					{
						shell.setLocation(
								(display.getBounds().width - shell.getSize().x) / 2,
								(display.getBounds().height - shell.getSize().y) / 2
						);
					}

					// Re-center config window relative to main
					if(!configShell.isDisposed())
					{
						configShell.setLocation(
								shell.getLocation().x + (shell.getSize().x - configShell.getSize().x) / 2,
								shell.getLocation().y + (shell.getSize().y - configShell.getSize().y) / 2
						);
					}
				});
			}
		});

		if(PokeMMOLocale.ENABLED_LANGUAGES.length < 2)
		{
			localeCombo.setEnabled(false);
		}

		// Network threads setting
		createLabel(configContent, SWT.NONE, "config.title.dl_threads");

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
		createLabel(configContent, SWT.NONE, "config.title.update_channel");

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
		Group advancedGroup = createGroup(configContent, SWT.NONE, "config.title.advanced");
		advancedGroup.setLayout(new GridLayout(2, false));
		GridData advancedData = new GridData(SWT.FILL, SWT.TOP, true, false);
		advancedData.horizontalSpan = 2;
		advancedGroup.setLayoutData(advancedData);

		// Memory setting
		createLabel(advancedGroup, SWT.NONE, "config.mem.max");

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
		createLabel(advancedGroup, SWT.NONE, "config.title.networking_corruption_workaround");

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
		bindTooltip(aesWorkaroundCheck, "config.networking_corruption_workaround.tooltip");

		// Action buttons
		Button openClientFolder = createButton(advancedGroup, SWT.PUSH, "config.title.open_client_folder");
		openClientFolder.addSelectionListener(new SelectionAdapter()
		{
			@Override
			public void widgetSelected(SelectionEvent e)
			{
				Util.open(parent.getPokemmoDir());
			}
		});

		Button repairClientFolder = createButton(advancedGroup, SWT.PUSH, "config.title.repair_client");
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
			status.setText(Config.getString(string, params));
		}
		else
		{
			display.asyncExec(() -> status.setText(Config.getString(string, params)));
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
			appendLocalizedText(taskOutput, string, params);
			taskOutput.append("\n");

			// Auto-scroll to bottom
			taskOutput.setTopIndex(taskOutput.getLineCount() - 1);
		}

		shell.layout(true, true);
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
		Runnable showMsg = () -> {
			MessageBox messageBox = new MessageBox(shell, SWT.OK | style);
			messageBox.setMessage(message);
			messageBox.setText(window_title);
			messageBox.open();
		};

		if(Display.getCurrent() != null)
		{
			showMsg.run();
		}
		else
		{
			display.syncExec(showMsg);
		}

		if(runnable != null)
		{
			display.asyncExec(runnable);
		}
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

			Text textArea = createText(dialogShell, SWT.MULTI | SWT.READ_ONLY | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
			textArea.setText(textAreaContents);
			textArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

			Button okButton = createButton(dialogShell, SWT.PUSH, "button.ok");
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
			display.syncExec(showDialog);
		}

		if(runnable != null)
		{
			display.asyncExec(runnable);
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
	}
}