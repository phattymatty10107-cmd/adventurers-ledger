package com.moneymaker.ui;

import com.moneymaker.MoneyMakerConfig;
import com.moneymaker.MoneyMakerPlugin;
import com.moneymaker.data.JobResult;
import com.moneymaker.data.PriceBook;
import com.moneymaker.data.Recipe;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.LinkBrowser;

/** Money-making jobs ranked by gp/hr, with what it takes to reach your next level. */
class JobsPanel extends JPanel
{
	private static final String ALL = "All jobs";
	private static final int MAX_ROWS = 150;
	private static final String[] SORTS = {"GP per hour", "Profit each", "Profit to next level", "Fastest to next level"};

	private final MoneyMakerPlugin plugin;
	private final ItemManager itemManager;
	private final IconTextField search = new IconTextField();
	private final JComboBox<String> category = new JComboBox<>();
	private final JComboBox<String> sort = new JComboBox<>(SORTS);
	private final JLabel status = UiUtil.label(" ", UiUtil.MUTED, false);
	private final JPanel list = UiUtil.verticalList();

	JobsPanel(MoneyMakerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 4));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(new EmptyBorder(0, 0, 2, 0));

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(0, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refresh();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refresh();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refresh();
			}
		});
		controls.add(search);

		Set<String> cats = new java.util.TreeSet<>();
		for (Recipe r : plugin.getRecipes())
		{
			cats.add(r.getCategory());
		}
		category.addItem(ALL);
		for (String c : cats)
		{
			category.addItem(c);
		}
		category.setFocusable(false);
		category.addActionListener(e -> refresh());
		controls.add(category);

		JPanel sortRow = new JPanel(new BorderLayout(4, 0));
		sortRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		sort.setFocusable(false);
		sort.addActionListener(e -> refresh());
		sortRow.add(sort, BorderLayout.CENTER);
		JButton reload = new JButton("Refresh");
		reload.setFocusable(false);
		reload.addActionListener(e ->
		{
			status.setText("Refreshing...");
			plugin.refreshPricesNow();
		});
		sortRow.add(reload, BorderLayout.EAST);
		controls.add(sortRow);
		controls.add(status);

		add(controls, BorderLayout.NORTH);
		add(UiUtil.scroll(list), BorderLayout.CENTER);
		refresh();
	}

	void refresh()
	{
		list.removeAll();
		PriceBook prices = plugin.getPrices();
		MoneyMakerConfig config = plugin.getConfig();

		if (prices.isEmpty())
		{
			String err = plugin.getPriceError();
			status.setText(err != null ? "Couldn't load prices" : "Loading prices...");
			list.add(UiUtil.emptyState(err != null ? "Couldn't reach the OSRS Wiki price API.<br>" + err : "Fetching prices..."));
			repaintList();
			return;
		}

		String q = search.getText() == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
		String cat = (String) category.getSelectedItem();
		boolean loggedIn = false;

		List<JobResult> results = new ArrayList<>();
		for (Recipe r : plugin.getRecipes())
		{
			if (cat != null && !ALL.equals(cat) && !cat.equals(r.getCategory()))
			{
				continue;
			}
			if (!q.isEmpty() && !r.getName().toLowerCase(Locale.ROOT).contains(q))
			{
				continue;
			}
			if (!config.includeMembers() && r.isMembers())
			{
				continue;
			}

			long[] skill = r.hasSkill() ? plugin.getSkill(r.getSkill()) : null;
			loggedIn |= plugin.isLoggedIn();
			int level = skill != null ? (int) skill[0] : 0;
			long xp = skill != null ? skill[1] : -1;
			boolean canDo = missingReq(r) == null;
			if (config.onlyDoable() && !canDo)
			{
				continue;
			}

			JobResult res = JobResult.evaluate(r, prices, config.priceMode(), config.speed(),
				canDo ? level : 0, canDo ? xp : -1, MoneyMakerPlugin.xpForNextLevel(level));
			if (res == null || (config.hideLowVolume() && res.isLowVolume()))
			{
				continue;
			}
			results.add(res);
		}

		results.sort(comparator());

		status.setText(results.size() + " jobs · prices " + UiUtil.ago(prices.getUpdatedAt())
			+ (plugin.getPriceError() != null ? " (refresh failed)" : "")
			+ (loggedIn ? "" : " · log in for level info"));

		if (results.isEmpty())
		{
			list.add(UiUtil.emptyState("No jobs match."));
		}
		if (plugin.isIronman())
		{
			list.add(UiUtil.emptyState("Ironman mode: GE prices don't apply to you.<br>Use the <b>Train</b> tab for the fastest route to your next milestone."));
		}
		int shown = 0;
		for (JobResult r : results)
		{
			if (++shown > MAX_ROWS)
			{
				list.add(UiUtil.emptyState("Showing the top " + MAX_ROWS + " of " + results.size()
					+ ".<br>Pick a category or search to see the rest."));
				break;
			}
			UiUtil.addRow(list, row(r));
		}
		repaintList();
	}

	private Comparator<JobResult> comparator()
	{
		switch (Math.max(0, sort.getSelectedIndex()))
		{
			case 1:
				return Comparator.comparingLong(JobResult::getProfitEach).reversed();
			case 2:
				return Comparator.comparingLong((JobResult r) -> r.getNext() != null ? r.getNext().getProfit() : Long.MIN_VALUE).reversed();
			case 3:
				return Comparator.comparingDouble((JobResult r) -> r.getNext() != null ? r.getNext().getHours() : Double.MAX_VALUE);
			default:
				return Comparator.comparingLong(JobResult::getGpPerHour).reversed();
		}
	}

	private ItemRow row(JobResult r)
	{
		Recipe rec = r.getRecipe();
		int icon = r.getOutputLines().isEmpty() ? -1 : r.getOutputLines().get(0).getItemId();
		long gpHr = r.getGpPerHour();

		String line2 = UiUtil.signedGp(r.getProfitEach()) + "/" + unit(rec, 1) + " · " + UiUtil.compact(Math.round(r.getActionsPerHour())) + "/hr"
			+ (r.isLimitCapped() ? " (limit)" : "");

		String line3;
		Color line3Color = UiUtil.MUTED;
		long[] skill = rec.hasSkill() ? plugin.getSkill(rec.getSkill()) : null;
		Recipe.Req missing = missingReq(rec);
		if (missing != null)
		{
			long[] have = plugin.getSkill(missing.getSkill());
			line3 = "Needs " + missing.getLevel() + " " + pretty(plugin.resolveSkill(missing.getSkill())) + " (you: " + (have != null ? have[0] : "?") + ")";
			line3Color = UiUtil.LOSS;
		}
		else if (!rec.hasSkill())
		{
			line3 = rec.getCategory() + (rec.isMembers() ? " · members" : "");
		}
		else if (r.getNext() != null)
		{
			JobResult.NextLevel n = r.getNext();
			line3 = pretty(plugin.resolveSkill(rec.getSkill())) + " " + n.getTargetLevel() + ": " + UiUtil.compact(n.getActions()) + " " + unit(rec, n.getActions()) + " · "
				+ UiUtil.signedGp(n.getProfit()) + " · " + hours(n.getHours());
		}
		else
		{
			line3 = rec.getLevel() + " " + pretty(plugin.resolveSkill(rec.getSkill())) + (rec.getXp() > 0 ? " · " + num(rec.getXp()) + " xp/" + unit(rec, 1) : " · no xp");
		}

		ItemRow row = new ItemRow(itemManager, icon, rec.getName(), UiUtil.signedGp(gpHr) + "/h", UiUtil.profitColor(gpHr),
			line2, line3, tooltip(r));
		row.setLine3Color(line3Color);
		if (!rec.getUrl().isEmpty())
		{
			row.action("Open wiki guide", () -> LinkBrowser.browse(rec.getUrl()));
		}
		if (!r.getOutputLines().isEmpty())
		{
			int id = r.getOutputLines().get(0).getItemId();
			row.action("Open price chart", () -> LinkBrowser.browse("https://prices.runescape.wiki/osrs/item/" + id));
		}
		return row;
	}

	private String tooltip(JobResult r)
	{
		Recipe rec = r.getRecipe();
		StringBuilder sb = new StringBuilder("<html><b>").append(esc(rec.getName())).append("</b>");
		if (!rec.getReqs().isEmpty())
		{
			sb.append("<br>Needs ");
			for (int i = 0; i < rec.getReqs().size(); i++)
			{
				Recipe.Req q = rec.getReqs().get(i);
				sb.append(i > 0 ? ", " : "").append(q.getLevel()).append(' ').append(pretty(plugin.resolveSkill(q.getSkill())));
			}
		}
		if (rec.hasSkill())
		{
			sb.append("<br>").append(rec.getXp() > 0 ? num(rec.getXp()) + " " + pretty(plugin.resolveSkill(rec.getSkill())) + " xp per " + unit(rec, 1) : "No xp");
		}
		sb.append("<br><br><b>Buy (per ").append(unit(rec, 1)).append(")</b>");
		for (JobResult.Line l : r.getInputLines())
		{
			sb.append("<br>").append(num(l.getQty())).append(" × ").append(esc(l.getItem())).append(" @ ").append(UiUtil.gp(l.getPriceEach()));
		}
		if (rec.getCoinsPerAction() > 0)
		{
			sb.append("<br>+ ").append(num(rec.getCoinsPerAction())).append(" gp in fees/supplies");
		}
		sb.append("<br><b>Get</b>");
		if (rec.getCoinsPerAction() < 0)
		{
			sb.append("<br>").append(num(-rec.getCoinsPerAction())).append(" coins (or untradeable loot valued in coins)");
		}
		int shown = 0;
		for (JobResult.Line l : r.getOutputLines())
		{
			if (++shown > 12)
			{
				sb.append("<br>... and ").append(r.getOutputLines().size() - 12).append(" more drops");
				break;
			}
			sb.append("<br>").append(num(l.getQty())).append(" × ").append(esc(l.getItem())).append(" @ ").append(UiUtil.gp(l.getPriceEach()));
		}
		sb.append("<br>Profit per ").append(unit(rec, 1)).append(" after 2% tax: <b>").append(UiUtil.signedGp(r.getProfitEach())).append("</b>");
		sb.append("<br>~").append(UiUtil.gp(Math.round(r.getActionsPerHour()))).append(" per hour = <b>")
			.append(UiUtil.signedGp(r.getGpPerHour())).append(" gp/hr</b>");
		if (r.isLimitCapped())
		{
			sb.append("<br><i>Slowed by the GE buy limit on ").append(esc(r.getCappedBy())).append("</i>");
		}
		if (r.isLowVolume())
		{
			sb.append("<br><i>Low trade volume: selling may take a while</i>");
		}

		JobResult.NextLevel n = r.getNext();
		if (n != null)
		{
			sb.append("<br><br><b>To reach ").append(pretty(plugin.resolveSkill(rec.getSkill()))).append(' ').append(n.getTargetLevel()).append("</b> (")
				.append(UiUtil.gp(n.getXpNeeded())).append(" xp)");
			sb.append("<br>").append(UiUtil.gp(n.getActions())).append(' ').append(unit(rec, n.getActions())).append(", about ").append(hours(n.getHours()));
			sb.append("<br>Shopping list:");
			for (JobResult.Line l : n.getShopping())
			{
				sb.append("<br>&nbsp;&nbsp;").append(UiUtil.gp(Math.round(l.getQty()))).append(" × ").append(esc(l.getItem()))
					.append(" = ").append(UiUtil.gp(Math.round(l.getQty() * l.getPriceEach())));
			}
			sb.append("<br>Cash needed: ").append(UiUtil.gp(n.getShoppingCost()));
			sb.append("<br>Profit by the time you level: <b>").append(UiUtil.signedGp(n.getProfit())).append("</b>");
		}
		sb.append("<br><br>Rates are rough estimates; set your speed in the plugin settings.</html>");
		return sb.toString();
	}

	/** First skill requirement you don't meet, or null (also null when logged out). */
	private Recipe.Req missingReq(Recipe r)
	{
		for (Recipe.Req q : r.getReqs())
		{
			long[] have = plugin.getSkill(q.getSkill());
			if (have != null && have[0] < q.getLevel())
			{
				return q;
			}
		}
		return null;
	}

	private static String unit(Recipe r, long n)
	{
		String u = r.getUnit().isEmpty() ? "action" : r.getUnit();
		if (n == 1 || u.equals("min"))
		{
			return u;
		}
		return u.endsWith("sh") || u.endsWith("s") ? u : u + "s";
	}

	/** 3, 2.5, 0.12, 0.0004 */
	private static String num(double v)
	{
		if (v == Math.rint(v) && Math.abs(v) < 1e9)
		{
			return UiUtil.gp((long) v);
		}
		if (Math.abs(v) >= 10)
		{
			return String.format(Locale.ROOT, "%,.1f", v);
		}
		return new java.math.BigDecimal(v).round(new java.math.MathContext(2)).stripTrailingZeros().toPlainString();
	}

	private static String hours(double h)
	{
		if (h <= 0)
		{
			return "-";
		}
		long mins = Math.round(h * 60);
		return mins < 60 ? mins + "m" : (mins / 60) + "h " + (mins % 60) + "m";
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
