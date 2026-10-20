package com.moneymaker.ui;

import com.moneymaker.MoneyMakerPlugin;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

public class MoneyMakerPanel extends PluginPanel
{
	private final JobsPanel jobs;
	private final BossesPanel bosses;
	private final FarmPanel farm;
	private final TrainPanel train;

	public MoneyMakerPanel(MoneyMakerPlugin plugin, ItemManager itemManager)
	{
		super(false);
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(new EmptyBorder(6, 4, 6, 4));

		jobs = new JobsPanel(plugin, itemManager);
		bosses = new BossesPanel(plugin, itemManager);
		farm = new FarmPanel(plugin, itemManager);
		train = new TrainPanel(plugin, itemManager);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		MaterialTabGroup tabs = new MaterialTabGroup(display);
		tabs.setLayout(new GridLayout(1, 4, 0, 0));
		tabs.setBorder(new EmptyBorder(0, 0, 8, 0));

		MaterialTab jobsTab = tab(tabs, "Jobs", "Money-making jobs ranked by gp/hr", jobs);
		MaterialTab trainTab = tab(tabs, "Train", "Fastest XP route to your next milestone level", train);
		tab(tabs, "Bosses", "Boss gear from cheapest to best", bosses);
		tab(tabs, "Farm", "Best farm runs, bird houses and dailies, with timers", farm);
		tabs.select(plugin.isIronman() ? trainTab : jobsTab);

		add(tabs, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
	}

	private static MaterialTab tab(MaterialTabGroup group, String name, String tooltip, JPanel content)
	{
		MaterialTab tab = new MaterialTab(name, group, content);
		tab.setToolTipText(tooltip);
		tab.setFont(FontManager.getRunescapeSmallFont());
		group.addTab(tab);
		return tab;
	}

	public void refreshAll()
	{
		jobs.refresh();
		bosses.refresh();
		farm.refresh();
		train.refresh();
	}

	public void refreshTrain()
	{
		train.refresh();
	}

	public void refreshFarm()
	{
		farm.refresh();
	}

	public void refreshFarmTimers()
	{
		farm.refreshTimers();
	}

	public void refreshJobs()
	{
		jobs.refresh();
		train.refresh();
	}
}
