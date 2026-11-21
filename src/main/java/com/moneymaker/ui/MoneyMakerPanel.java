package com.moneymaker.ui;

import com.moneymaker.MoneyMakerPlugin;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import net.runelite.client.util.LinkBrowser;
import java.awt.event.HierarchyEvent;
import javax.swing.JPanel;
import javax.swing.Timer;
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
	private final GoalPanel goal;
	private final MaterialTabGroup tabs;
	private final MaterialTab goalTab;
	private final RouteBar routeBar;

	private static final int JOBS = 1;
	private static final int TRAIN = 2;
	private static final int FARM = 4;
	private static final int ALL = 8;
	private static final int BOSSES = 16;
	/** How long to wait for XP drops and bank changes to settle before redrawing. */
	private static final int SETTLE_MS = 1000;

	/** What needs redrawing (JOBS, TRAIN, FARM, ALL). Only touched on the Swing thread. */
	private int pending;
	private final Timer settle = new Timer(SETTLE_MS, e -> flush());

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
		goal = new GoalPanel(plugin, itemManager);

		JPanel display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);

		tabs = new MaterialTabGroup(display);
		tabs.setLayout(new GridLayout(1, 5, 0, 0));
		tabs.setBorder(new EmptyBorder(0, 0, 8, 0));

		MaterialTab jobsTab = tab(tabs, "Jobs", "Money-making jobs ranked by gp/hr", jobs);
		MaterialTab trainTab = tab(tabs, "Train", "Fastest XP route to your next milestone level", train);
		tab(tabs, "Boss", "Boss gear from cheapest to best, and whether you can fight it", bosses);
		tab(tabs, "Farm", "Best farm runs, bird houses and dailies, with timers", farm);
		goalTab = tab(tabs, "Goal", "What you want, what it costs and the quickest way to pay for it", goal);
		tabs.select(plugin.isIronman() ? trainTab : jobsTab);

		// the route box sits above the tabs so it shows whichever tab you're on
		routeBar = new RouteBar(plugin);
		JPanel north = new JPanel(new BorderLayout(0, 6));
		north.setBackground(ColorScheme.DARK_GRAY_COLOR);
		north.add(routeBar, BorderLayout.NORTH);
		north.add(tabs, BorderLayout.CENTER);
		add(north, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);
		add(tipLink(), BorderLayout.SOUTH);

		settle.setRepeats(false);
		// catch up on anything that changed while the panel was closed
		addHierarchyListener(e ->
		{
			if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing())
			{
				flush();
			}
		});
	}

	/** Redraw whatever is waiting, if the panel is on screen. If it isn't, it waits until it is. */
	private void flush()
	{
		if (!isShowing() || pending == 0)
		{
			return;
		}
		int todo = pending;
		pending = 0;
		settle.stop();
		if ((todo & ALL) != 0)
		{
			jobs.refresh();
			bosses.refresh();
			farm.refresh();
			train.refresh();
			goal.refresh();
			return;
		}
		if ((todo & JOBS) != 0)
		{
			jobs.refresh();
		}
		if ((todo & TRAIN) != 0)
		{
			train.refresh();
		}
		if ((todo & FARM) != 0)
		{
			farm.refresh();
		}
		if ((todo & BOSSES) != 0)
		{
			bosses.refresh();
		}
		// levels and coins feed both the boss check and the goal plan
		if ((todo & (JOBS | TRAIN)) != 0)
		{
			bosses.refresh();
			goal.refresh();
		}
	}

	/** The route changed, or you moved along it. */
	public void refreshRoute()
	{
		routeBar.refresh();
		farm.routeChanged();
	}

	/** Open the Goal tab with this item as the goal. */
	public void showGoal(String item)
	{
		goal.setItem(item);
		tabs.select(goalTab);
	}

	/** Stop the timers when the plugin is turned off. */
	public void shutDown()
	{
		settle.stop();
		farm.stop();
	}

	private static MaterialTab tab(MaterialTabGroup group, String name, String tooltip, JPanel content)
	{
		MaterialTab tab = new MaterialTab(name, group, content);
		tab.setToolTipText(tooltip);
		tab.setFont(FontManager.getRunescapeSmallFont());
		group.addTab(tab);
		return tab;
	}

	/** Prices or settings changed: redraw everything now, or when the panel is next opened. */
	public void refreshAll()
	{
		pending |= ALL;
		flush();
	}

	/** Your items changed. These come in bursts while banking, so wait for them to settle. */
	public void refreshTrain()
	{
		pending |= TRAIN;
		settle.restart();
	}

	/** Your quest progress changed. */
	public void refreshBosses()
	{
		pending |= BOSSES;
		flush();
	}

	public void refreshFarm()
	{
		pending |= FARM;
		flush();
	}

	public void refreshFarmTimers()
	{
		farm.refreshTimers();
	}

	/** Your levels or XP changed. Every XP drop lands here, so wait for them to settle. */
	public void refreshJobs()
	{
		pending |= JOBS | TRAIN;
		settle.restart();
	}

	/** Where the "Buy me a coffee" link goes. The plugin is free; this is only a tip jar. */
	static final String TIP_URL = "https://ko-fi.com/rabbit10107";

	/** A small link under the tabs to leave a tip on Ko-fi. Opens in your web browser. */
	private static JLabel tipLink()
	{
		JLabel link = new JLabel("Enjoying the Ledger? Leave a tip on Ko-fi");
		link.setFont(FontManager.getRunescapeSmallFont());
		link.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		link.setHorizontalAlignment(SwingConstants.CENTER);
		link.setBorder(new EmptyBorder(6, 0, 0, 0));
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.setToolTipText("Opens " + TIP_URL + " in your web browser. The plugin stays free either way.");
		link.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent e)
			{
				link.setForeground(ColorScheme.BRAND_ORANGE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				link.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				LinkBrowser.browse(TIP_URL);
			}
		});
		return link;
	}
}
