package com.moneymaker.data;

public enum PriceMode
{
	INSTANT("Instant (buy high, sell low)"),
	AVERAGE("Average (last hour)"),
	PATIENT("Patient offers (buy low, sell high)");

	private final String label;

	PriceMode(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
