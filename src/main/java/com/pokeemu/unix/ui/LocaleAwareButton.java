package com.pokeemu.unix.ui;

import javax.swing.Icon;
import javax.swing.JButton;

import com.pokeemu.unix.config.Config;

/**
 * @author Kyu
 */
public class LocaleAwareButton extends JButton implements LocaleAwareInterface
{
	private String key;
	private String tooltip;
	
	public LocaleAwareButton(String key)
	{
		this.key = key;
		this.tooltip = "";
		init(key, null);
        
        LocaleAwareElementManager.instance.addElement(this);
	}
	
	@Override
    protected void init(String text, Icon icon)
	{
		if(text != null)
			super.setText(Config.getString(text));
		
        // Set the UI
        updateUI();
        
        setAlignmentX(LEFT_ALIGNMENT);
        setAlignmentY(CENTER_ALIGNMENT);
	}
	
	@Override
	public void setTextKey(String key, Object... params)
	{
		this.key = key;
		super.setText(Config.getString(key, params));
	}
	
	@Override
	public void setText(String key)
	{
		throw new UnsupportedOperationException("Must use locale-aware constructor");
	}

	@Override
	public void setToolTipKey(String key, Object... params)
	{
		this.tooltip = key;
		super.setToolTipText(Config.getString(key, params));
	}
	
	@Override
	public void setToolTipText(String key)
	{
		throw new UnsupportedOperationException("Must use locale-aware tooltip constructor");
	}

	@Override
	public void updateLocale()
	{
		super.setText(Config.getString(key));
		super.setToolTipText(Config.getString(tooltip));
	}
}
