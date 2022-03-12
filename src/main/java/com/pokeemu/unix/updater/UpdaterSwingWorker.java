package com.pokeemu.unix.updater;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.MainFrame;

import javax.swing.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * @author Kyu <kyu@pokemmo.eu>
 */
public class UpdaterSwingWorker extends SwingWorker<Void, Void>
{
	private final UnixInstaller parent;
	private final MainFrame mainFrame;
	private final boolean repair;
	private boolean success = true;

	public UpdaterSwingWorker(UnixInstaller parent, MainFrame mainFrame, boolean repair)
	{
		this.parent = parent;
		this.mainFrame = mainFrame;
		this.repair = repair;
	}

	@Override
	protected Void doInBackground()
	{
		if(parent.isUpdating())
			return null;

		try
		{
			if(repair)
			{
				Files.walk(Path.of(parent.getPokemmoDir().getAbsolutePath()))
						.sorted(Comparator.reverseOrder())
						.forEach(p -> {
							try
							{
								if(!Files.isSymbolicLink(p))
									Files.delete(p);
							}
							catch(IOException e)
							{
								e.printStackTrace();
								success = false;
							}
						});
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
		finally
		{
			if(success)
				parent.doUpdate(repair);
			else
				SwingUtilities.invokeLater(() -> mainFrame.showError(Config.getString("error.dir_not_accessible", parent.getPokemmoDir().getAbsolutePath(), "REPAIR_FAILED"), "", () -> System.exit(UnixInstaller.EXIT_CODE_IO_FAILURE)));
		}

		return null;
	}
}
