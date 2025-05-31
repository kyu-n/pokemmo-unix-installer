package com.pokeemu.unix.updater;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.swt.widgets.Display;

import com.pokeemu.unix.UnixInstaller;
import com.pokeemu.unix.config.Config;
import com.pokeemu.unix.ui.MainFrame;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Eclipse Job for handling updates and repairs
 *
 * @author Kyu <kyu@pokemmo.com>
 */
public class UpdaterJob extends Job
{
	private final UnixInstaller parent;
	private final MainFrame mainFrame;
	private final boolean repair, clean;
	private boolean success = true;

	public UpdaterJob(UnixInstaller parent, MainFrame mainFrame, boolean repair, boolean clean)
	{
		super("PokeMMO Updater");
		this.parent = parent;
		this.mainFrame = mainFrame;
		this.repair = repair;
		this.clean = clean;
	}

	@Override
	protected IStatus run(IProgressMonitor monitor)
	{
		if(parent.isUpdating())
			return Status.OK_STATUS;

		List<Throwable> failed = new ArrayList<>();

		try
		{
			if(clean)
			{
				monitor.beginTask("Cleaning installation directory", IProgressMonitor.UNKNOWN);

				Files.walk(Path.of(parent.getPokemmoDir().getAbsolutePath()))
						.sorted(Comparator.reverseOrder())
						.takeWhile(p -> success && !monitor.isCanceled())
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

				if(monitor.isCanceled())
				{
					return Status.CANCEL_STATUS;
				}

				Display.getDefault().syncExec(() -> {
					parent.createPokemmoDir();
					parent.createSymlinkedDirectories();
				});
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
			failed.add(e);
			success = false;
		}
		finally
		{
			monitor.done();

			if(success)
			{
				parent.doUpdate(repair);
				Display.getDefault().asyncExec(() -> mainFrame.setCanStart());
			}
			else
			{
				Display.getDefault().asyncExec(() ->
						mainFrame.showErrorWithStacktrace(
								Config.getString("error.dir_not_accessible", parent.getPokemmoDir().getAbsolutePath(), "REPAIR_FAILED"),
								"",
								failed.toArray(new Throwable[0]),
								() -> System.exit(UnixInstaller.EXIT_CODE_IO_FAILURE)
						)
				);
			}
		}

		return success ? Status.OK_STATUS : Status.CANCEL_STATUS;
	}
}