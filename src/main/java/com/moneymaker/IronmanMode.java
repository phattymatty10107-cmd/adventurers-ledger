package com.moneymaker;

/** Whether the plugin treats you as an ironman. */
public enum IronmanMode
{
	AUTO("Detect from account"),
	ON("On"),
	OFF("Off");

	private final String label;

	IronmanMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
