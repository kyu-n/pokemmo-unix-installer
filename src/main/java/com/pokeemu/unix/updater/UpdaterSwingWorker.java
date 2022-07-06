package com.pokeemu.unix.updater;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.MainFrame;

import javax.swing.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * @author Kyu <kyu@pokemmo.com>
 */
public class UpdaterSwingWorker extends SwingWorker<Void, Void>
{
	private final UnixInstaller parent;
	private final MainFrame mainFrame;
	private final boolean repair, clean;
	private boolean success = true;

	public UpdaterSwingWorker(UnixInstaller parent, MainFrame mainFrame, boolean repair, boolean clean)
	{
		this.parent = parent;
		this.mainFrame = mainFrame;
		this.repair = repair;
		this.clean = clean;
	}

	@Override
	protected Void doInBackground()
	{
		if(parent.isUpdating())
			return null;

		List<Throwable> failed = new ArrayList<>();

		try
		{
			if(clean)
			{
				Files.walk(Path.of(parent.getPokemmoDir().getAbsolutePath()))
						.sorted(Comparator.reverseOrder())
						.takeWhile(p -> success)
						.forEach(p -> {
							try
							{
								Files.delete(p);
							}
							catch(IOException e)
							{
								e.printStackTrace();
								failed.add(e);
								success = false;
							}
						});

				SwingUtilities.invokeAndWait(() -> {
					parent.createPokemmoDir();
					parent.createSymlinkedDirectories();
				});
			}
		}
		catch(IOException | InterruptedException | InvocationTargetException e)
		{
			e.printStackTrace();
			failed.add(e);
		}
		finally
		{
			if(success)
			{
				parent.doUpdate(repair);
				SwingUtilities.invokeLater(() -> mainFrame.setCanStart(false));
			}
			else
				SwingUtilities.invokeLater(() -> mainFrame.showErrorWithStacktrace(Config.getString("error.dir_not_accessible", parent.getPokemmoDir().getAbsolutePath(), "REPAIR_FAILED"), "", failed.toArray(new Throwable[0]), () -> System.exit(UnixInstaller.EXIT_CODE_IO_FAILURE)));
		}

		return null;
	}
}
