package com.pokeemu.unix.ui;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.enums.PokeMMOGC;
import com.pokeemu.unix.enums.PokeMMOLocale;
import com.pokeemu.unix.enums.UpdateChannel;
import com.pokeemu.unix.util.Util;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * 
 * @author Kyu
 *
 */
public class MainFrame extends JFrame implements ActionListener
{
	private final UnixInstaller parent;
	
	protected final LocaleAwareLabel status;
	protected final Label dlSpeed;
	
	protected LocaleAwareButton launchGame;
	protected LocaleAwareButton configLauncher;
	
	private JProgressBar progressBar;
    private LocaleAwareTextArea taskOutput;
    
    private final ExecutorService executorService;
    
	private int downloaded_bytes;
	private long last_download_progress_update = System.currentTimeMillis(), last_download_speed_update, last_download_speed_update_bytes;
	
	private static MainFrame instance;
	
	private JDialog configWindow;
	
	public static MainFrame getInstance()
	{
		return instance;
	}
	
	public MainFrame(UnixInstaller parent)
	{
		instance = this;
		
		this.parent = parent;
		this.executorService = Executors.newFixedThreadPool(1);
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		this.status = new LocaleAwareLabel("main.loading");
		this.dlSpeed = new Label("");
		
		/**
		 * Progress Bar initialization
		 */
		{
			progressBar = new JProgressBar(0, 100);
	        progressBar.setValue(0);
	        progressBar.setStringPainted(false);
	        progressBar.setIndeterminate(true);
		}
		
        /**
         * TaskOutput text area
         */
        {
            taskOutput = new LocaleAwareTextArea(5, 20);
            taskOutput.setMargin(new Insets(5,5,5,5));
            taskOutput.setEditable(false);
            
            DefaultCaret caret = (DefaultCaret)taskOutput.getCaret();
            caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
        }
        
        /**
         * Top Bar
         */
        JPanel top_panel = new JPanel();
        {
            top_panel.add(status, BorderLayout.WEST);
            top_panel.add(progressBar, BorderLayout.CENTER);
            top_panel.add(dlSpeed, BorderLayout.EAST);
        }
        
        /**
         * Configuration popup window
         */
    	configWindow = new JDialog(this, Config.getString("config.title.window"), true);
		{
	        JPanel config_panel = new JPanel(new GridLayout(9,2));
	        config_panel.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
	        {
	        	LocaleAwareLabel localeLabel = new LocaleAwareLabel("config.title.language");
	        	JComboBox<PokeMMOLocale> localeList = new JComboBox<>(PokeMMOLocale.ENABLED_LANGUAGES);
	        	localeList.setSelectedItem(Config.ACTIVE_LOCALE);
	        	localeList.addActionListener((event) -> Config.changeLocale((PokeMMOLocale)localeList.getSelectedItem()));
	        	
	        	if(localeList.getModel().getSize() < 2)
	        		localeList.setEnabled(false);
	        	
	        	config_panel.add(localeLabel);
	        	config_panel.add(localeList);
	        	
	        	LocaleAwareLabel autoStartLabel = new LocaleAwareLabel("config.title.autostart");
	        	LocaleAwareCheckbox autoStartCb = new LocaleAwareCheckbox();
	        	autoStartCb.setSelected(Config.AUTO_START);
	        	autoStartCb.addActionListener((event) -> { Config.AUTO_START = autoStartCb.isSelected(); Config.save(); });
	        	
	        	config_panel.add(autoStartLabel);
	        	config_panel.add(autoStartCb);
	        	
	        	LocaleAwareLabel networkThreadsLabel = new LocaleAwareLabel("config.title.dl_threads");
	        	SpinnerNumberModel networkThreadsModel = new SpinnerNumberModel(Config.NETWORK_THREADS, 1, Config.NETWORK_THREADS_MAX, 1);
	        	JSpinner networkThreadsSpinner = new JSpinner(networkThreadsModel);
	        	networkThreadsSpinner.addChangeListener((event) -> { Config.NETWORK_THREADS = networkThreadsModel.getNumber().intValue(); Config.save(); });
	        	
	        	config_panel.add(networkThreadsLabel);
	        	config_panel.add(networkThreadsSpinner);
	        	
	        	LocaleAwareLabel updateChannelLabel = new LocaleAwareLabel("config.title.update_channel");
	        	JComboBox<UpdateChannel> updateChannelList = new JComboBox<>(UpdateChannel.values());
	        	updateChannelList.setSelectedItem(Config.UPDATE_CHANNEL);
	        	updateChannelList.addActionListener((event) -> { Config.UPDATE_CHANNEL = (UpdateChannel)updateChannelList.getSelectedItem(); Config.save(); });
	        	
	        	// TODO: Care about this
        		updateChannelList.setEnabled(false);
	        	
	        	config_panel.add(updateChannelLabel);
	        	config_panel.add(updateChannelList);
	        	
	        	config_panel.add(new LocaleAwareLabel("config.title.advanced"));
	        	config_panel.add(new Label("")); // Dummy widget to fulfill our column requirements
	        	
	        	LocaleAwareLabel garbageCollectorLabel = new LocaleAwareLabel("config.mem.java_gc");
	        	JComboBox<PokeMMOGC> garbageCollectorList = new JComboBox<>(Stream.of(PokeMMOGC.values()).filter(PokeMMOGC::isEnabled).toArray(PokeMMOGC[]::new));
	        	garbageCollectorList.setSelectedItem(Config.ACTIVE_GC);
        		
	        	LocaleAwareLabel memoryMaxLabel = new LocaleAwareLabel("config.mem.max");
	        	SpinnerNumberModel memoryMaxModel = new SpinnerNumberModel(Config.HARD_MAX_MEMORY_MB, Config.JOPTS_XMX_VAL_MIN, Config.JOPTS_XMX_VAL_MAX, 128);
	        	JSpinner memoryMaxSpinner = new JSpinner(memoryMaxModel);
	        	
	        	garbageCollectorList.addActionListener((event) ->
	        	{
	        		Config.ACTIVE_GC = (PokeMMOGC)garbageCollectorList.getSelectedItem();
	        		Config.save();
	        	});
	        	
	        	memoryMaxSpinner.addChangeListener((event) ->
	        	{
	        		Config.HARD_MAX_MEMORY_MB = memoryMaxModel.getNumber().shortValue();
	        		Config.save();
	        	});
	        	
	        	config_panel.add(garbageCollectorLabel);
	        	config_panel.add(garbageCollectorList);
	        	
	        	config_panel.add(memoryMaxLabel);
	        	config_panel.add(memoryMaxSpinner);
	        	
	        	LocaleAwareLabel aesWorkaroundLabel = new LocaleAwareLabel("config.title.networking_corruption_workaround");
	        	LocaleAwareCheckbox aesWorkaroundCb = new LocaleAwareCheckbox();
	        	aesWorkaroundCb.setSelected(Config.AES_INTRINSICS_WORKAROUND_ENABLED);
	        	aesWorkaroundCb.addActionListener((event) -> { Config.AES_INTRINSICS_WORKAROUND_ENABLED = aesWorkaroundCb.isSelected(); Config.save(); });
	        	aesWorkaroundCb.setEnabled(false);
	        	aesWorkaroundCb.setToolTipKey("config.networking_corruption_workaround.tooltip");
	        	
	        	config_panel.add(aesWorkaroundLabel);
	        	config_panel.add(aesWorkaroundCb);
	        	
	        	LocaleAwareButton openClientFolder = new LocaleAwareButton("config.title.open_client_folder");
	        	openClientFolder.addActionListener((event) -> Util.open(parent.getPokemmoDir()));
	        	
	        	config_panel.add(openClientFolder);
	        	config_panel.add(new Label("")); // Dummy widget to fulfill our column requirements
	        }
	        
	        configWindow.setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
	        configWindow.add(config_panel);
	        configWindow.setTitle(Config.getString("config.title.window"));
	        configWindow.setLocationRelativeTo(MainFrame.this);
	        configWindow.setSize(440, 340);
	        configWindow.setResizable(false);
		}
        
        /**
         * Bottom Bar
         */
        JPanel bottom_panel = new JPanel(new BorderLayout(0,0));
        {
        	configLauncher = new LocaleAwareButton("config.title.window");
        	configLauncher.addActionListener((event) ->	SwingUtilities.invokeLater(() -> configWindow.setVisible(true)));
        	
        	launchGame = new LocaleAwareButton("main.launch");
        	launchGame.setEnabled(false);
        	
            bottom_panel.add(configLauncher, BorderLayout.WEST);
            bottom_panel.add(launchGame, BorderLayout.EAST);
        }
        
        /**
         * Add our widgets
         */
        {
            add(top_panel, BorderLayout.PAGE_START);
            add(new JScrollPane(taskOutput), BorderLayout.CENTER);
            add(bottom_panel, BorderLayout.PAGE_END);
        }
        
		setSize(480, 280);
		setLocationRelativeTo(null);
		setTitle(Config.getString("main.title"));
		setResizable(false);
		setVisible(true);
	}
	
	@Override
	public void actionPerformed(ActionEvent e)
	{
		if("exit".equals(e.getActionCommand()))
		{
			System.exit(UnixInstaller.EXIT_CODE_SUCCESS);
		}
	}

	public void setStatus(final String string, int progress, Object... params)
	{
		EventQueue.invokeLater(() ->
		{
			status.setTextKey(string);
			addDetail(string, progress, params);
		});
	}
	
	public void addDetail(final String string, final int progress, Object... params)
	{
		EventQueue.invokeLater(() -> addDetailPrivate(string, progress, params));
	}

	protected void addDetailPrivate(String string, int progress, Object... params)
	{
		if(progress > 0)
		{
			progressBar.setIndeterminate(false);
			progressBar.setValue(progress);
		}
		else
		{
			progressBar.setIndeterminate(true);
			progressBar.setValue(progress);
		}
		
		if(string != null)
		{
			taskOutput.appendLocaleStr(string, params);
			taskOutput.appendLocaleStr("\n");
		}
		
		validate();
	}
	
	public void updateDLSpeed(final long bytes_per_second)
	{
		EventQueue.invokeLater(() -> dlSpeed.setText(humanReadableByteCount(bytes_per_second, false)+"/s"));
	}
	
	public static String humanReadableByteCount(long bytes, boolean si) {
	    int unit = si ? 1000 : 1024;
	    if (bytes < unit) return bytes + " B";
	    int exp = (int) (Math.log(bytes) / Math.log(unit));
	    String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
	    return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
	}
	
	public void showMessage(String message, String window_title)
	{		
		showMessage(message, window_title, null);
	}
	
	public void showMessage(String message, String window_title, Runnable runnable)
	{		
		showMessage(message, window_title, JOptionPane.INFORMATION_MESSAGE, runnable);
	}
	
	public void showError(String message, String window_title)
	{
		showError(message, window_title, null);
	}
	
	public void showError(String message, String window_title, Runnable runnable)
	{
		showMessage(message, window_title, JOptionPane.ERROR_MESSAGE, runnable);
	}
	
	public void showInfo(String message, Object... params)
	{
		addDetail(message, 90, params);
	}
	
	public int showYesNo(String message, String window_title)
	{
		return JOptionPane.showConfirmDialog(this, message, window_title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
	}
	
	public void showMessage(String message, String window_title, int information_code, Runnable runnable)
	{
		System.out.println(window_title+" - "+message);
		JOptionPane.showMessageDialog(this, message, window_title, information_code);
		
		if(runnable != null)
			executorService.execute(runnable);
	}
	
	public void showDownloadProgress(int bytes)
	{
		downloaded_bytes += bytes;
	
		if(System.currentTimeMillis() - last_download_progress_update > 100)
			last_download_progress_update = System.currentTimeMillis();
		
		if(last_download_speed_update == 0)
			last_download_speed_update = System.currentTimeMillis();
		
		if(System.currentTimeMillis() - last_download_speed_update > 1000)
		{
			last_download_speed_update = System.currentTimeMillis();
			
			if(last_download_speed_update_bytes > 0)
				updateDLSpeed(downloaded_bytes-last_download_speed_update_bytes);
			
			last_download_speed_update_bytes = downloaded_bytes;
		}
	}
	
	public void setCanStart()
	{
		launchGame.setEnabled(true);
		
		if(Config.AUTO_START)
		{
			// Start countdown task
		    Timer timer = new Timer();
		    TimerTask task = new TimerTask()
		    {
				@Override
				public void run()
				{
					timer.cancel();

					if(configWindow.isVisible())
					{
						launchGame.setTextKey("main.launch");
						launchGame.addActionListener((event2) -> parent.launchGame());
					}
					else
					{
						parent.launchGame();
					}
				}
			};
			
		    timer.scheduleAtFixedRate(task, 1000, 1000);
		    
		    launchGame.addActionListener((event) ->
		    {
		    	timer.cancel();
		    	launchGame.setTextKey(Config.getString("main.launch"));
		    	launchGame.addActionListener((event2) -> parent.launchGame());
		    });
		}
		else
		{
	    	launchGame.addActionListener((event) -> parent.launchGame());
		}
	}
}
