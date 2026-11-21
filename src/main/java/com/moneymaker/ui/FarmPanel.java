package com.moneymaker.ui;

import com.moneymaker.MoneyMakerConfig;
import com.moneymaker.MoneyMakerPlugin;
import com.moneymaker.data.FarmRoute;
import com.moneymaker.data.FarmRun;
import com.moneymaker.data.JobResult;
import com.moneymaker.data.PriceBook;
import com.moneymaker.route.Places;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/** Farm runs, bird houses and dailies: the best runs at live prices, with timers. */
class FarmPanel extends JPanel
{
	private static final String ALL = "All runs";
	private static final String[] SORTS = {"Profit per run", "Profit per hour", "XP per run", "Ready soonest"};
	private static final Color WAITING = new Color(255, 200, 70);
	/** Width the route text wraps at, in HTML pixels (the side panel is narrow). */
	private static final int ROUTE_TEXT_WIDTH = 140;

	private final MoneyMakerPlugin plugin;
	private final ItemManager itemManager;
	private final JComboBox<String> group = new JComboBox<>();
	private final JComboBox<String> sort = new JComboBox<>(SORTS);
	private final JLabel status = UiUtil.label(" ", UiUtil.MUTED, false);
	private final JPanel list = UiUtil.verticalList();
	private final Timer tick;
	/** Runs whose route is open under their row (by run id). */
	private final Set<String> openRoutes = new java.util.HashSet<>();
	/** Stops you've ticked off on each run (by run id), until you start its timer. */
	private final Map<String, Set<Integer>> visited = new HashMap<>();
	/** Rows on screen, so the timers can tick without rebuilding the list. */
	private final Map<FarmRun, ItemRow> rows = new HashMap<>();
	/** Whether the route on the map was a farm run's the last time it changed. */
	private boolean routeWasFarm;

	FarmPanel(MoneyMakerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 4));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(new EmptyBorder(0, 0, 2, 0));

		TreeSet<String> groups = new TreeSet<>();
		for (FarmRun r : plugin.getFarmRuns())
		{
			groups.add(r.getGroup());
		}
		group.addItem(ALL);
		for (String g : groups)
		{
			group.addItem(g);
		}
		group.setFocusable(false);
		group.addActionListener(e -> refresh());
		sort.setFocusable(false);
		sort.addActionListener(e -> refresh());
		controls.add(group);
		controls.add(sort);
		controls.add(status);

		add(controls, BorderLayout.NORTH);
		add(UiUtil.scroll(list), BorderLayout.CENTER);

		// tick the countdowns while the panel is open
		tick = new Timer(15_000, e ->
		{
			if (isShowing())
			{
				refreshTimers();
			}
		});
		tick.start();
		refresh();
	}

	void refresh()
	{
		list.removeAll();
		rows.clear();
		PriceBook prices = plugin.getPrices();
		MoneyMakerConfig config = plugin.getConfig();
		if (prices.isEmpty())
		{
			status.setText(plugin.getPriceError() != null ? "Couldn't load prices" : "Loading prices...");
			list.add(UiUtil.emptyState("Fetching prices..."));
			repaintList();
			return;
		}

		String g = (String) group.getSelectedItem();
		List<FarmRun.Priced> runs = new ArrayList<>();
		for (FarmRun r : plugin.getFarmRuns())
		{
			if (g != null && !ALL.equals(g) && !g.equals(r.getGroup()))
			{
				continue;
			}
			if (!config.includeMembers() && r.isMembers())
			{
				continue;
			}
			FarmRun.Priced p = r.price(prices, config.priceMode());
			if (p != null)
			{
				runs.add(p);
			}
		}

		// runs with a timer going come first, then the chosen sort
		Comparator<FarmRun.Priced> bySort = comparator();
		runs.sort(Comparator.comparing((FarmRun.Priced p) -> plugin.getPlanted(p.getRun()) == 0).thenComparing(bySort));

		int timers = 0;
		for (FarmRun.Priced p : runs)
		{
			if (plugin.getPlanted(p.getRun()) != 0)
			{
				timers++;
			}
			ItemRow row = row(p);
			rows.put(p.getRun(), row);
			UiUtil.addRow(list, row);
			if (openRoutes.contains(p.getRun().getId()))
			{
				UiUtil.addRow(list, routePanel(p.getRun()));
			}
		}
		status.setText(runs.size() + " runs · " + timers + " timer" + (timers == 1 ? "" : "s") + " · click a run for its timer and route");
		if (runs.isEmpty())
		{
			list.add(UiUtil.emptyState("No runs match."));
		}
		refreshTimers();
		repaintList();
	}

	void stop()
	{
		tick.stop();
	}

	/** Update the countdown line on each row. */
	void refreshTimers()
	{
		long now = System.currentTimeMillis();
		for (Map.Entry<FarmRun, ItemRow> e : rows.entrySet())
		{
			FarmRun r = e.getKey();
			ItemRow row = e.getValue();
			long ready = plugin.getReadyAt(r);
			long[] skill = r.hasSkill() ? plugin.getSkill(r.getSkill()) : null;
			if (ready == 0)
			{
				if (skill != null && skill[0] < r.getLevel())
				{
					row.setLine3("Needs " + r.getLevel() + " " + pretty(r.getSkill()) + " (you: " + skill[0] + ")");
					row.setLine3Color(UiUtil.LOSS);
				}
				else
				{
					row.setLine3("No timer · click when you've planted");
					row.setLine3Color(UiUtil.MUTED);
				}
			}
			else if (now < ready)
			{
				row.setLine3("Ready in " + left(ready - now));
				row.setLine3Color(WAITING);
			}
			else
			{
				row.setLine3("Ready now!");
				row.setLine3Color(UiUtil.PROFIT);
			}
		}
	}

	private Comparator<FarmRun.Priced> comparator()
	{
		switch (Math.max(0, sort.getSelectedIndex()))
		{
			case 1:
				return Comparator.comparingLong(FarmRun.Priced::getProfitPerHour).reversed();
			case 2:
				return Comparator.comparingDouble((FarmRun.Priced p) -> p.getRun().getXp()).reversed();
			case 3:
				return Comparator.comparingLong((FarmRun.Priced p) ->
				{
					long ready = plugin.getReadyAt(p.getRun());
					return ready == 0 ? Long.MAX_VALUE : ready;
				});
			default:
				return Comparator.comparingLong(FarmRun.Priced::getProfit).reversed();
		}
	}

	private ItemRow row(FarmRun.Priced p)
	{
		FarmRun r = p.getRun();
		int icon = !p.getSell().isEmpty() ? p.getSell().get(0).getItemId() : !p.getBuy().isEmpty() ? p.getBuy().get(0).getItemId() : -1;
		long profit = p.getProfit();
		String line2 = (r.getXp() > 0 ? UiUtil.compact(Math.round(r.getXp())) + " xp · " : "")
			+ "cost " + UiUtil.compact(Math.max(0, p.getCost())) + " · " + grow(r.getGrowMinutes());
		ItemRow row = new ItemRow(itemManager, icon, r.getName(), UiUtil.signedGp(profit), UiUtil.profitColor(profit),
			line2, " ", tooltip(p));
		List<FarmRoute.Stop> stops = plugin.getFarmRoute().stopsFor(r);
		if (!stops.isEmpty())
		{
			boolean open = openRoutes.contains(r.getId());
			row.action(open ? "Hide route" : "Show route (" + stops.size() + " stops)", () ->
			{
				if (!openRoutes.remove(r.getId()))
				{
					openRoutes.add(r.getId());
				}
				refresh();
			});
		}
		row.action("Start timer (planted now)", () ->
		{
			// run done: clear the ticks ready for next time
			visited.remove(r.getId());
			plugin.startFarmTimer(r);
		});
		if (plugin.getPlanted(r) != 0)
		{
			row.action("Clear timer", () -> plugin.clearFarmTimer(r));
		}
		if (!r.getUrl().isEmpty())
		{
			row.action("Open wiki page", () -> LinkBrowser.browse(r.getUrl()));
		}
		return row;
	}

	private String tooltip(FarmRun.Priced p)
	{
		FarmRun r = p.getRun();
		StringBuilder sb = new StringBuilder("<html><b>").append(esc(r.getName())).append("</b>");
		if (r.hasSkill())
		{
			sb.append("<br>Needs ").append(r.getLevel()).append(' ').append(pretty(r.getSkill()));
			if (r.getXp() > 0)
			{
				sb.append(" · ").append(UiUtil.gp(Math.round(r.getXp()))).append(" xp per run");
			}
		}
		sb.append("<br>Grows in ").append(grow(r.getGrowMinutes())).append(" · about ")
			.append(Math.max(1, Math.round(r.getRunMinutes()))).append(" min to do");
		if (r.getPatches() > 0)
		{
			sb.append(" · ").append(r.getPatches()).append(" patches");
		}
		sb.append("<br><br><b>Buy (per run)</b>");
		for (JobResult.Line l : p.getBuy())
		{
			sb.append("<br>").append(qty(l.getQty())).append(" × ").append(esc(l.getItem())).append(" @ ").append(UiUtil.gp(l.getPriceEach()));
		}
		if (r.getCoins() > 0)
		{
			sb.append("<br>+ ").append(UiUtil.gp(Math.round(r.getCoins()))).append(" coins");
		}
		sb.append("<br><b>Get</b>");
		if (r.getCoins() < 0)
		{
			sb.append("<br>").append(UiUtil.gp(Math.round(-r.getCoins()))).append(" coins (or loot valued in coins)");
		}
		for (JobResult.Line l : p.getSell())
		{
			sb.append("<br>").append(qty(l.getQty())).append(" × ").append(esc(l.getItem())).append(" @ ").append(UiUtil.gp(l.getPriceEach()));
		}
		sb.append("<br>Cash needed: ").append(UiUtil.gp(Math.max(0, p.getCost())));
		sb.append("<br>Profit per run after 2% tax: <b>").append(UiUtil.signedGp(p.getProfit())).append("</b>");
		sb.append("<br>= ").append(UiUtil.signedGp(p.getProfitPerHour())).append(" per real hour if you do it every time it's ready");
		List<FarmRoute.Stop> stops = plugin.getFarmRoute().stopsFor(r);
		if (!stops.isEmpty())
		{
			sb.append("<br><br><b>Route</b> (click the run to open it)");
			for (int i = 0; i < stops.size(); i++)
			{
				sb.append("<br>").append(i + 1).append(". ").append(esc(stops.get(i).getName()));
			}
		}
		if (!r.getNote().isEmpty())
		{
			sb.append("<br><br><i>").append(esc(r.getNote())).append("</i>");
		}
		sb.append("<br><br>Click for the timer and the route.</html>");
		return sb.toString();
	}

	/** The stops for a run, in order. Click a stop to tick it off as you go. */
	private JPanel routePanel(FarmRun run)
	{
		List<FarmRoute.Stop> stops = plugin.getFarmRoute().stopsFor(run);
		Set<Integer> done = visited.computeIfAbsent(run.getId(), k -> new java.util.HashSet<>());

		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(new EmptyBorder(4, 8, 6, 6));

		JLabel title = UiUtil.label(done.size() + " of " + stops.size() + " done · click a stop to tick it off", UiUtil.MUTED, false);
		title.setAlignmentX(LEFT_ALIGNMENT);
		title.setBorder(new EmptyBorder(0, 0, 4, 0));
		box.add(title);
		addWayLine(box, run, stops, done);

		for (int i = 0; i < stops.size(); i++)
		{
			final int index = i;
			FarmRoute.Stop s = stops.get(i);
			boolean ticked = done.contains(i);

			// a fixed width makes the text wrap inside the side panel instead of running off the edge
			StringBuilder sb = new StringBuilder("<html><div style='width:")
				.append(ROUTE_TEXT_WIDTH).append("px'><b>")
				.append(ticked ? "&#10003; " : (i + 1) + ". ").append(esc(s.getName())).append("</b>");
			if (!ticked)
			{
				if (!s.getTravel().isEmpty())
				{
					sb.append("<br>").append(esc(s.getTravel()));
				}
				if (!s.getNeeds().isEmpty())
				{
					sb.append("<br><i>Needs: ").append(esc(s.getNeeds())).append("</i>");
				}
			}
			sb.append("</div></html>");

			JLabel stop = UiUtil.label(sb.toString(), ticked ? UiUtil.PROFIT : Color.WHITE, false);
			stop.setAlignmentX(LEFT_ALIGNMENT);
			stop.setBorder(new EmptyBorder(3, 0, 3, 0));
			stop.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			stop.setToolTipText(ticked ? "Click to untick" : "Click when you've done this stop");
			stop.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (e.getButton() != MouseEvent.BUTTON1)
					{
						return;
					}
					if (!done.remove(index))
					{
						done.add(index);
					}
					// if the route on the map is this run's, move it on to the next stop
					if (routeTag(run).equals(plugin.getRoutes().getTag()))
					{
						if (!routeToNext(run, stops, done))
						{
							plugin.clearRoute();
						}
					}
					refresh();
				}
			});
			box.add(stop);
		}
		return box;
	}

	private static String routeTag(FarmRun run)
	{
		return "farm:" + run.getId();
	}

	/** Where a stop is on the map, or null if this copy doesn't know. */
	private Places.Place placeOf(FarmRun run, FarmRoute.Stop stop)
	{
		Places.Place p = plugin.getPlaces().find("farm", run.getName() + ": " + stop.getName());
		return p != null ? p : plugin.getPlaces().find("farm", run.getGroup() + ": " + stop.getName());
	}

	/** Start a route to the first stop that isn't ticked. False when there's nowhere left to go. */
	private boolean routeToNext(FarmRun run, List<FarmRoute.Stop> stops, Set<Integer> done)
	{
		for (int i = 0; i < stops.size(); i++)
		{
			Places.Place p = done.contains(i) ? null : placeOf(run, stops.get(i));
			if (p != null)
			{
				plugin.getRoutes().routeTo(stops.get(i).getName(), routeTag(run), p.getTiles());
				return true;
			}
		}
		return false;
	}

	/** The line under a route's title that starts (or stops) the route on the map. */
	private void addWayLine(JPanel box, FarmRun run, List<FarmRoute.Stop> stops, Set<Integer> done)
	{
		boolean known = false;
		for (FarmRoute.Stop s : stops)
		{
			known |= placeOf(run, s) != null;
		}
		String hint = plugin.routePerkHint();
		if (!known || (!plugin.canRoutePerks() && hint == null))
		{
			return;
		}
		boolean mine = routeTag(run).equals(plugin.getRoutes().getTag());
		String text = !plugin.canRoutePerks() ? hint
			: mine ? "Showing the way · click to stop" : "> Show the way to the next stop";
		JLabel way = UiUtil.label("<html><div style='width:" + ROUTE_TEXT_WIDTH + "px'>" + esc(text) + "</div></html>",
			plugin.canRoutePerks() ? new Color(0, 200, 255) : UiUtil.MUTED, false);
		way.setAlignmentX(LEFT_ALIGNMENT);
		way.setBorder(new EmptyBorder(0, 0, 4, 0));
		if (plugin.canRoutePerks())
		{
			way.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			way.setToolTipText("Draws the route to each stop in turn. Tick a stop off and it moves on to the next");
			way.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mousePressed(MouseEvent e)
				{
					if (e.getButton() != MouseEvent.BUTTON1)
					{
						return;
					}
					if (mine)
					{
						plugin.clearRoute();
					}
					else
					{
						routeToNext(run, stops, done);
					}
					refresh();
				}
			});
		}
		box.add(way);
	}

	/** The route on the map changed: redraw if a run's route is open, as its "showing the way" line may be stale. */
	void routeChanged()
	{
		String tag = plugin.getRoutes().getTag();
		boolean farmNow = tag != null && tag.startsWith("farm:");
		if (!openRoutes.isEmpty() && (farmNow || routeWasFarm) && isShowing())
		{
			refresh();
		}
		routeWasFarm = farmNow;
	}

	private static String left(long ms)
	{
		long mins = (ms + 59_999) / 60_000;
		if (mins >= 24 * 60)
		{
			return (mins / 1440) + "d " + (mins % 1440) / 60 + "h";
		}
		return mins >= 60 ? (mins / 60) + "h " + (mins % 60) + "m" : mins + "m";
	}

	private static String grow(double minutes)
	{
		long m = Math.round(minutes);
		if (m < 60)
		{
			return m + "m";
		}
		if (m < 48 * 60)
		{
			return (m / 60) + "h" + (m % 60 == 0 ? "" : " " + (m % 60) + "m");
		}
		return String.format(Locale.ROOT, "%.1f days", m / 1440.0);
	}

	private static String qty(double v)
	{
		return v == Math.rint(v) ? UiUtil.gp((long) v) : String.format(Locale.ROOT, "%.2f", v);
	}

	private static String pretty(String skill)
	{
		return skill.charAt(0) + skill.substring(1).toLowerCase(Locale.ROOT);
	}

	private static String esc(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void repaintList()
	{
		list.revalidate();
		list.repaint();
	}
}
