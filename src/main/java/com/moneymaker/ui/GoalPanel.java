package com.moneymaker.ui;

import com.moneymaker.MoneyMakerConfig;
import com.moneymaker.MoneyMakerPlugin;
import com.moneymaker.data.BossCheck;
import com.moneymaker.data.Gear;
import com.moneymaker.data.GearPricer;
import com.moneymaker.data.GoalPlan;
import com.moneymaker.data.ItemPrice;
import com.moneymaker.data.JobResult;
import com.moneymaker.data.PriceBook;
import com.moneymaker.data.Recipe;
import com.moneymaker.data.Training;
import com.moneymaker.route.Places;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;

/**
 * Pick something you want (an item to buy, or a level to reach) and see what it costs, how much
 * you still need, and the quickest jobs you can do to pay for it.
 */
class GoalPanel extends JPanel
{
	private static final String ITEM = "Buy an item";
	private static final String LEVEL = "Reach a level";
	private static final String PLACE = "Go somewhere";
	private static final int MAX_MATCHES = 8;
	private static final int MAX_EARNERS = 5;
	/** Width the summary text wraps at, in HTML pixels (the side panel is narrow). */
	private static final int TEXT_WIDTH = 145;

	private final MoneyMakerPlugin plugin;
	private final ItemManager itemManager;
	private final JComboBox<String> mode = new JComboBox<>(new String[]{ITEM, LEVEL, PLACE});
	private final IconTextField search = new IconTextField();
	private final IconTextField placeSearch = new IconTextField();
	private final JComboBox<String> skill = new JComboBox<>();
	private final JSpinner level = new JSpinner(new SpinnerNumberModel(70, 2, 99, 1));
	private final JPanel inputs = new JPanel(new CardLayout());
	private final JPanel list = UiUtil.verticalList();
	private final Timer searchDelay = new Timer(250, e -> refresh());

	/** The item you're saving for, or null. Remembered between sessions. */
	private String goalItem;
	/** True while the controls are being filled in by code, so that doesn't count as you changing them. */
	private boolean updating;
	/** Told about the level goal whenever you pick one, so the Train tab shows its plan. */
	private java.util.function.BiConsumer<String, Integer> onLevelGoal = (sk, lvl) -> { };
	/** Opens the Train tab. */
	private Runnable showTrain = () -> { };

	GoalPanel(MoneyMakerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel controls = new JPanel();
		controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(new EmptyBorder(0, 0, 2, 0));

		mode.setFocusable(false);
		controls.add(mode);
		controls.add(javax.swing.Box.createRigidArea(new Dimension(0, 4)));

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(0, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				searchDelay.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				searchDelay.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				searchDelay.restart();
			}
		});
		searchDelay.setRepeats(false);
		placeSearch.setIcon(IconTextField.Icon.SEARCH);
		placeSearch.setPreferredSize(new Dimension(0, 30));
		placeSearch.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		placeSearch.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		placeSearch.setToolTipText("Search for a quest or a place");
		placeSearch.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				searchDelay.restart();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				searchDelay.restart();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				searchDelay.restart();
			}
		});

		JPanel levelRow = new JPanel(new BorderLayout(4, 0));
		levelRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (String s : plugin.getTraining().getSkills())
		{
			skill.addItem(s);
		}
		skill.setRenderer(new javax.swing.DefaultListCellRenderer()
		{
			@Override
			public java.awt.Component getListCellRendererComponent(javax.swing.JList<?> l, Object value, int index, boolean sel, boolean focus)
			{
				super.getListCellRendererComponent(l, value, index, sel, focus);
				if (value != null)
				{
					setText(pretty(value.toString()));
				}
				return this;
			}
		});
		skill.setFocusable(false);
		levelRow.add(skill, BorderLayout.CENTER);
		levelRow.add(level, BorderLayout.EAST);

		inputs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		inputs.add(search, ITEM);
		inputs.add(levelRow, LEVEL);
		inputs.add(placeSearch, PLACE);
		controls.add(inputs);

		add(controls, BorderLayout.NORTH);
		add(UiUtil.scroll(list), BorderLayout.CENTER);

		// pick up where you left off
		updating = true;
		goalItem = emptyToNull(plugin.getSaved("goalItem"));
		String savedMode = plugin.getSaved("goalMode");
		if (LEVEL.equals(savedMode) || PLACE.equals(savedMode))
		{
			mode.setSelectedItem(savedMode);
		}
		String savedSkill = plugin.getSaved("goalSkill");
		if (savedSkill != null)
		{
			skill.setSelectedItem(savedSkill);
		}
		try
		{
			String savedLevel = plugin.getSaved("goalLevel");
			if (savedLevel != null)
			{
				level.setValue(Math.max(2, Math.min(99, Integer.parseInt(savedLevel))));
			}
		}
		catch (NumberFormatException ignored)
		{
			// keep the default
		}
		updating = false;
		showInputs();

		mode.addActionListener(e -> changed());
		skill.addActionListener(e -> changed());
		level.addChangeListener(e -> changed());
		refresh();
	}

	/** Picking a level goal sends it here (the Train tab). Sends the current goal straight away. */
	void setOnLevelGoal(java.util.function.BiConsumer<String, Integer> listener, Runnable openTrain)
	{
		onLevelGoal = listener;
		showTrain = openTrain;
		sendLevelGoal();
	}

	private void sendLevelGoal()
	{
		String sk = (String) skill.getSelectedItem();
		if (LEVEL.equals(mode.getSelectedItem()) && sk != null)
		{
			onLevelGoal.accept(sk, (Integer) level.getValue());
		}
	}

	/** Set the item to save for (used by "Plan how to afford this" on the Boss tab). */
	void setItem(String name)
	{
		goalItem = emptyToNull(name);
		plugin.save("goalItem", goalItem);
		updating = true;
		mode.setSelectedItem(ITEM);
		search.setText("");
		updating = false;
		plugin.save("goalMode", ITEM);
		showInputs();
		searchDelay.stop();
		refresh();
	}

	private void changed()
	{
		if (updating)
		{
			return;
		}
		plugin.save("goalMode", (String) mode.getSelectedItem());
		plugin.save("goalSkill", (String) skill.getSelectedItem());
		plugin.save("goalLevel", String.valueOf(level.getValue()));
		showInputs();
		sendLevelGoal();
		refresh();
	}

	private void showInputs()
	{
		((CardLayout) inputs.getLayout()).show(inputs, (String) mode.getSelectedItem());
	}

	void refresh()
	{
		list.removeAll();
		if (PLACE.equals(mode.getSelectedItem()))
		{
			// routes don't need prices
			placeGoal();
			repaintList();
			return;
		}
		PriceBook prices = plugin.getPrices();
		if (prices.isEmpty())
		{
			list.add(UiUtil.emptyState(plugin.getPriceError() != null
				? "Couldn't reach the OSRS Wiki price API." : "Fetching prices..."));
			repaintList();
			return;
		}
		if (LEVEL.equals(mode.getSelectedItem()))
		{
			levelGoal(prices);
		}
		else
		{
			itemGoal(prices);
		}
		repaintList();
	}

	// ---------------------------------------------------------------- buying an item

	private void itemGoal(PriceBook prices)
	{
		String q = search.getText() == null ? "" : search.getText().trim();
		if (!q.isEmpty())
		{
			List<ItemPrice> matches = prices.search(q, MAX_MATCHES);
			list.add(UiUtil.header("Pick an item"));
			if (matches.isEmpty())
			{
				list.add(UiUtil.emptyState("Nothing on the GE matches \"" + esc(q) + "\"."));
			}
			for (ItemPrice p : matches)
			{
				ItemRow row = new ItemRow(itemManager, p.getId(), p.getName(), UiUtil.compact(p.typical()),
					ColorScheme.GRAND_EXCHANGE_PRICE, "Click to make this your goal", null, null);
				row.onClick(() -> setItem(p.getName()));
				UiUtil.addRow(list, row);
			}
			return;
		}
		if (goalItem == null)
		{
			list.add(UiUtil.emptyState("Search for something you want to buy.<br><br>"
				+ "Or click a piece of gear on the <b>Boss</b> tab and choose <b>Plan how to afford this</b>."));
			return;
		}

		GearPricer.Price price = plugin.getGearPricer().price(goalItem, prices);
		list.add(UiUtil.header("Your goal"));
		if (!price.isTradeable() || price.getCost() <= 0)
		{
			list.add(UiUtil.emptyState("<b>" + esc(goalItem) + "</b> can't be bought on the GE right now."
				+ (price.getNote() != null ? "<br>" + esc(price.getNote()) : "")));
			addClear();
			return;
		}

		long cost = price.getCost();
		ItemRow goal = new ItemRow(itemManager, price.getIconId(), goalItem, UiUtil.compact(cost),
			ColorScheme.GRAND_EXCHANGE_PRICE, UiUtil.gp(cost) + " gp", price.getNote(), null);
		UiUtil.addRow(list, goal);

		Map<String, Gear.Owned> owned = plugin.getOwned();
		Gear.Owned mine = plugin.isLoggedIn() ? BossCheck.owned(goalItem, owned) : null;
		if (mine != null)
		{
			UiUtil.addRow(list, text("You already own this (" + mine.getWhere() + ").", UiUtil.PROFIT));
			addClear();
			return;
		}
		long need = addMoney(cost, owned);
		Places.Place ge = plugin.getPlaces().find("place", "Grand Exchange");
		if (ge != null)
		{
			ItemRow way = new ItemRow(itemManager, -1, "Show the way to the GE", null, null,
				"Draws the route on the map", null, null);
			way.onClick(() -> plugin.routeTo(ge, "place"));
			UiUtil.addRow(list, way);
		}
		if (need > 0)
		{
			addEarners(prices, need, 0);
		}
		addClear();
	}

	// ---------------------------------------------------------------- going somewhere

	private void placeGoal()
	{
		String q = placeSearch.getText() == null ? "" : placeSearch.getText().trim();
		Places places = plugin.getPlaces();
		boolean perks = plugin.canRoutePerks();
		if (q.isEmpty())
		{
			list.add(UiUtil.header("Quick routes"));
			for (Places.Place p : places.all())
			{
				if ("place".equals(p.getKind()))
				{
					addPlace(p);
				}
			}
			list.add(UiUtil.emptyState("Search for a quest to be shown the way to where it starts.<br><br>"
				+ "Or right-click anywhere on the <b>world map</b> and choose <b>Route here</b>."
				+ (plugin.routePerkHint() != null ? "<br><br>" + esc(plugin.routePerkHint()) : "")));
			return;
		}
		String lower = q.toLowerCase(Locale.ROOT);
		list.add(UiUtil.header("Pick where to go"));
		int shown = 0;
		for (Places.Place p : places.search(null, q, MAX_MATCHES))
		{
			boolean perk = "boss".equals(p.getKind()) || "farm".equals(p.getKind());
			if ("quest".equals(p.getKind()) || (perk && !perks))
			{
				continue;
			}
			addPlace(p);
			shown++;
		}
		// every quest in the game, not only the ones in the plugin's own list
		for (net.runelite.api.Quest quest : net.runelite.api.Quest.values())
		{
			if (shown >= MAX_MATCHES)
			{
				break;
			}
			String name = quest.getName();
			if (name.toLowerCase(Locale.ROOT).contains(lower))
			{
				ItemRow row = new ItemRow(itemManager, -1, name, null, null, "Quest start · click for the route", null, null);
				row.onClick(() -> plugin.routeToQuest(name));
				UiUtil.addRow(list, row);
				shown++;
			}
		}
		if (shown == 0)
		{
			list.add(UiUtil.emptyState("No quest or place matches \"" + esc(q) + "\"."));
		}
	}

	private void addPlace(Places.Place p)
	{
		String kind = "boss".equals(p.getKind()) ? "Boss" : "farm".equals(p.getKind()) ? "Farm patch" : "Place";
		ItemRow row = new ItemRow(itemManager, -1, p.getName(), null, null, kind + " · click for the route",
			p.getNote().isEmpty() ? null : p.getNote(), null);
		row.onClick(() -> plugin.routeTo(p, p.getKind()));
		UiUtil.addRow(list, row);
	}

	// ---------------------------------------------------------------- reaching a level

	private void levelGoal(PriceBook prices)
	{
		String sk = (String) skill.getSelectedItem();
		if (sk == null)
		{
			list.add(UiUtil.emptyState("No training data loaded."));
			return;
		}
		int target = (Integer) level.getValue();
		long[] mine = plugin.getSkill(sk);
		int fromLevel = mine != null ? (int) mine[0] : 1;
		long fromXp = mine != null ? mine[1] : 0;

		list.add(UiUtil.header("Your goal"));
		if (fromLevel >= target)
		{
			boolean reached = (sk + ":" + target).equals(plugin.getGoalDone());
			UiUtil.addRow(list, text(reached
				? "<b>Goal reached!</b> You're level " + fromLevel + " " + pretty(sk) + ". Pick your next level above."
				: "You're already level " + fromLevel + " " + pretty(sk) + ". Pick a higher level.", UiUtil.PROFIT));
			return;
		}

		MoneyMakerConfig config = plugin.getConfig();
		boolean iron = plugin.isIronman();
		Training.Filter filter = new Training.Filter();
		filter.ironman = iron;
		filter.members = config.includeMembers();
		List<Training.Step> steps = plugin.getTraining().plan(sk, fromXp, target, filter);
		if (steps.isEmpty())
		{
			list.add(UiUtil.emptyState("No training methods found for " + pretty(sk) + " at level " + fromLevel + "."));
			return;
		}
		double trainHours = GoalPlan.hours(steps);
		long cost = iron ? 0 : GoalPlan.suppliesCost(steps, prices, config.priceMode());

		StringBuilder sb = new StringBuilder("<b>").append(pretty(sk)).append(' ').append(fromLevel).append(" → ").append(target).append("</b>");
		sb.append("<br>Training: about ").append(time(trainHours)).append(" on the fastest route");
		if (mine == null)
		{
			sb.append("<br><i>Log in to start from your own level.</i>");
		}
		UiUtil.addRow(list, text(sb.toString(), Color.WHITE));
		ItemRow train = new ItemRow(itemManager, -1, "Open the plan on the Train tab", null, null,
			"Each step, and the way to get there", null, null);
		train.onClick(showTrain);
		UiUtil.addRow(list, train);

		if (iron)
		{
			UiUtil.addRow(list, text("Ironman mode: you gather the supplies yourself, so there's nothing to buy.", UiUtil.MUTED));
			return;
		}
		if (cost <= 0)
		{
			UiUtil.addRow(list, text("This route has no supplies to buy.", UiUtil.PROFIT));
			return;
		}
		long need = addMoney(cost, plugin.getOwned());
		if (need > 0)
		{
			addEarners(prices, need, trainHours);
		}
	}

	// ---------------------------------------------------------------- shared

	/** Show what it costs, what you have and what's left. Returns how much you still need. */
	private long addMoney(long cost, Map<String, Gear.Owned> owned)
	{
		StringBuilder sb = new StringBuilder("Costs about <b>").append(UiUtil.compact(cost)).append("</b>");
		long need = cost;
		if (plugin.isLoggedIn())
		{
			long have = GoalPlan.coins(owned);
			need = Math.max(0, cost - have);
			sb.append("<br>You have ").append(UiUtil.compact(have))
				.append(plugin.isBankSeen() ? " (bag and bank)" : " (bag only: open your bank once to count it)");
		}
		else
		{
			sb.append("<br><i>Log in to count the coins you already have.</i>");
		}
		UiUtil.addRow(list, text(sb.toString(), Color.WHITE));
		UiUtil.addRow(list, need > 0
			? text("Still need <b>" + UiUtil.compact(need) + "</b>", new Color(255, 200, 70))
			: text("You can afford it now.", UiUtil.PROFIT));
		return need;
	}

	/** The quickest jobs you can do right now to earn the amount. */
	private void addEarners(PriceBook prices, long need, double extraHours)
	{
		if (plugin.isIronman())
		{
			list.add(UiUtil.emptyState("Ironman mode: GE money makers don't apply to you."));
			return;
		}
		MoneyMakerConfig config = plugin.getConfig();
		List<JobResult> jobs = new ArrayList<>();
		for (Recipe r : plugin.getRecipes())
		{
			if (!config.includeMembers() && r.isMembers())
			{
				continue;
			}
			if (!plugin.meetsReqs(r))
			{
				continue;
			}
			jobs.add(JobResult.evaluate(r, prices, config.priceMode(), config.speed(), 0, -1, -1));
		}
		List<GoalPlan.Earner> earners = GoalPlan.earners(jobs, need, MAX_EARNERS);

		list.add(UiUtil.header("Fastest ways to earn it"));
		if (earners.isEmpty())
		{
			list.add(UiUtil.emptyState("No money makers found that you can do."));
			return;
		}
		if (extraHours > 0)
		{
			UiUtil.addRow(list, text("About <b>" + time(extraHours + earners.get(0).getHours())
				+ "</b> in total: earning the money with the best job, then training.", UiUtil.MUTED));
		}
		for (GoalPlan.Earner e : earners)
		{
			JobResult j = e.getJob();
			Recipe rec = j.getRecipe();
			int icon = j.getOutputLines().isEmpty() ? -1 : j.getOutputLines().get(0).getItemId();
			ItemRow row = new ItemRow(itemManager, icon, rec.getName(), time(e.getHours()), Color.WHITE,
				UiUtil.signedGp(j.getGpPerHour()) + "/h", rec.getCategory() + (plugin.isLoggedIn() ? "" : " · log in to check levels"), null);
			if (!rec.getUrl().isEmpty())
			{
				row.action("Open wiki guide", () -> LinkBrowser.browse(rec.getUrl()));
			}
			UiUtil.addRow(list, row);
		}
	}

	private void addClear()
	{
		JButton clear = new JButton("Clear goal");
		clear.setFocusable(false);
		clear.addActionListener(e -> setItem(null));
		JPanel wrap = new JPanel(new GridLayout(1, 1));
		wrap.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrap.setBorder(new EmptyBorder(6, 0, 0, 0));
		wrap.add(clear);
		UiUtil.addRow(list, wrap);
	}

	/** A wrapped block of text on a card. */
	private static JLabel text(String html, Color color)
	{
		JLabel l = UiUtil.label("<html><div style='width:" + TEXT_WIDTH + "px'>" + html + "</div></html>", color, false);
		l.setOpaque(true);
		l.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		l.setBorder(new EmptyBorder(5, 6, 5, 6));
		return l;
	}

	/** 45m, 12h 30m, 3d 4h */
	static String time(double hours)
	{
		long mins = Math.max(1, Math.round(hours * 60));
		if (mins < 60)
		{
			return mins + "m";
		}
		if (mins < 48 * 60)
		{
			return (mins / 60) + "h" + (mins % 60 == 0 ? "" : " " + (mins % 60) + "m");
		}
		return (mins / 1440) + "d " + (mins % 1440) / 60 + "h";
	}

	private static String emptyToNull(String s)
	{
		return s == null || s.trim().isEmpty() ? null : s.trim();
	}

	private static String pretty(String skill)
	{
		return skill.isEmpty() ? skill : skill.charAt(0) + skill.substring(1).toLowerCase(Locale.ROOT);
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
