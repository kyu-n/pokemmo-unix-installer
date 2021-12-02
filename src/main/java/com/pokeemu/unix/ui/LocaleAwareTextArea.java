package com.pokeemu.unix.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JTextArea;

import com.pokeemu.unix.config.Config;

/**
 * @author Kyu
 */
public class LocaleAwareTextArea extends JTextArea implements LocaleAwareInterface
{
	private final List<LocaleAwareStringBundle> appended_lines = new ArrayList<>();
	
	public LocaleAwareTextArea()
	{
		super();
		
		LocaleAwareElementManager.instance.addElement(this);
	}
	
    public LocaleAwareTextArea(int rows, int columns)
    {
        super(null, null, rows, columns);
		LocaleAwareElementManager.instance.addElement(this);
    }
	
	@Override
	public void setTextKey(String key, Object... params)
	{
		// Default
	}

	@Override
	public void setToolTipKey(String tooltip, Object... params)
	{
		// Default
	}
	
	public void appendLocaleStr(String str, Object... params)
	{
		var bundle = new LocaleAwareStringBundle(str, params);
		
		if(str.matches("\\n"))
		{
			super.append(str);
			appended_lines.add(bundle);
			return;
		}
		
		String resolved = Config.getString(str, params);
		appended_lines.add(bundle);
		super.append(resolved);
	}
	
	@Override
    public void append(String str)
	{
		throw new UnsupportedOperationException("Use locale-aware appendLocaleStr");
    }

	@Override
	public void updateLocale()
	{
		setDocument(createDefaultModel());
		
		appended_lines.forEach(s ->
		{
			if(Config.hasString(s.getKey()))
				super.append(Config.getString(s.getKey(), s.getParams()));
			else
				super.append(s.getKey());
		});
	}
}
