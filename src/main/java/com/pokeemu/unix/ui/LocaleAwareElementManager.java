package com.pokeemu.unix.ui;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import com.pokeemu.unix.config.Config;

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * @author Kyu
 */
public class LocaleAwareElementManager
{
	public static LocaleAwareElementManager instance = new LocaleAwareElementManager();

	private final List<Runnable> updateActions = new ArrayList<>();
	private final Set<Shell> managedShells = new HashSet<>();

	public void addBinding(Runnable updateAction)
	{
		updateActions.add(updateAction);
	}

	public void registerShell(Shell shell)
	{
		managedShells.add(shell);
	}

	public void unregisterShell(Shell shell)
	{
		managedShells.remove(shell);
	}

	public void updateAll()
	{
		Display.getDefault().asyncExec(() -> {
			// Update all text
			updateActions.forEach(Runnable::run);

			// Relayout all managed shells
			for(Shell shell : managedShells)
			{
				if(!shell.isDisposed())
				{
					// Force complete relayout of the shell and all children
					shell.layout(true, true);
					// Optionally resize to preferred size
					shell.pack();
					// Ensure minimum size is maintained
					shell.setSize(Math.max(shell.getSize().x, shell.getMinimumSize().x),
							Math.max(shell.getSize().y, shell.getMinimumSize().y));
				}
			}
		});
	}

	public static Label createLabel(Composite parent, int style, String textKey)
	{
		Label label = new Label(parent, style);
		bindText(label::setText, textKey);
		return label;
	}

	public static Button createButton(Composite parent, int style, String textKey)
	{
		Button button = new Button(parent, style);
		bindText(button::setText, textKey);
		return button;
	}

	public static Group createGroup(Composite parent, int style, String textKey)
	{
		Group group = new Group(parent, style);
		bindText(group::setText, textKey);
		return group;
	}

	public static Text createText(Composite parent, int style)
	{
		return new Text(parent, style);
	}

	// Helper for appending localized strings to Text widgets
	public static void appendLocalizedText(Text text, String key, Object... params)
	{
		text.append(Config.getString(key, params));
	}

	// Helper for binding shell titles
	public static void bindShellTitle(Shell shell, String textKey)
	{
		bindText(shell::setText, textKey);
		instance.registerShell(shell);
		// Clean up when shell is disposed
		shell.addDisposeListener(e -> instance.unregisterShell(shell));
	}

	// Helper for binding tooltip text
	public static void bindTooltip(Button control, String textKey)
	{
		instance.addBinding(() -> control.setToolTipText(Config.getString(textKey)));
		control.setToolTipText(Config.getString(textKey));
	}

	private static void bindText(Consumer<String> setter, String key)
	{
		instance.addBinding(() -> setter.accept(Config.getString(key)));
		setter.accept(Config.getString(key)); // Initial value
	}
}