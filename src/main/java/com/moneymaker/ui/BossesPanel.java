package com.moneymaker.ui;

import com.moneymaker.MoneyMakerPlugin;
import com.moneymaker.data.BossSetup;
import com.moneymaker.data.GearPricer;
import com.moneymaker.data.PriceBook;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.LinkBrowser;

/** Gear for each boss, per slot from starter to best in slot (OSRS Wiki ranking), at live GE prices. */
class BossesPanel extends JPanel
{
	private final MoneyMakerPlugin plugin;
	private final ItemManager itemManager;
	private final JComboBox<BossSetup> setups = new JComboBox<>();
	private final JLabel budget = stat();
	private final JLabel mid = stat();
	private final JLabel best = stat();
	private final JLabel untradeables = UiUtil.label(" ", UiUtil.MUTED, false);
	private final JPanel list = UiUtil.verticalList();

	BossesPanel(MoneyMakerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = new JPanel(new GridLayout(0, 1, 0, 4));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (BossSetup s : plugin.getBosses())
		{
			setups.addItem(s);
		}
		setups.setFocusable(false);
		setups.addActionListener(e -> refresh());
		top.add(setups);

		JPanel totals = new JPanel(new GridLayout(1, 3, 4, 0));
		totals.setBackground(ColorScheme.DARK_GRAY_COLOR);
		totals.add(box("Budget", budget));
		totals.add(box("Mid", mid));
		totals.add(box("Best", best));
		top.add(totals);
		top.add(untradeables);

		JButton wiki = new JButton("Open wiki strategy page");
		wiki.setFocusable(false);
		wiki.addActionListener(e ->
		{
			BossSetup s = (BossSetup) setups.getSelectedItem();
			if (s != null)
			{
				LinkBrowser.browse(s.getUrl());
			}
		});
		top.add(wiki);
		top.setBorder(new EmptyBorder(0, 0, 4, 0));

		add(top, BorderLayout.NORTH);
		add(UiUtil.scroll(list), BorderLayout.CENTER);
		refresh();
	}

	void refresh()
	{
		list.removeAll();
		BossSetup setup = (BossSetup) setups.getSelectedItem();
		PriceBook prices = plugin.getPrices();
		GearPricer pricer = plugin.getGearPricer();

		if (setup == null)
		{
			list.add(UiUtil.emptyState("No boss data loaded."));
			repaintList();
			return;
		}
		if (prices.isEmpty())
		{
			list.add(UiUtil.emptyState("Fetching prices..."));
			repaintList();
			return;
		}

		long budgetTotal = 0;
		long midTotal = 0;
		long bestTotal = 0;
		List<String> unpriced = new ArrayList<>();

		for (BossSetup.Slot slot : setup.getSlots())
		{
			List<List<String>> tiers = slot.getTiers(); // best first
			int n = tiers.size();
			List<GearPricer.Price> priced = new ArrayList<>();
			for (List<String> tier : tiers)
			{
				priced.add(pricer.cheapestOf(tier, prices));
			}

			budgetTotal += cost(priced.get(n - 1));
			midTotal += cost(priced.get(n / 2));
			bestTotal += cost(priced.get(0));
			if (!priced.get(0).isTradeable())
			{
				unpriced.add(priced.get(0).getName());
			}

			list.add(UiUtil.header(slot.getName() + "  (weakest → strongest)"));
			// cheapest (worst) first, up to best in slot
			for (int i = n - 1; i >= 0; i--)
			{
				GearPricer.Price p = priced.get(i);
				List<String> alts = tiers.get(i);
				String title = String.join(" / ", alts);
				String tier = i == 0 ? "Best in slot" : i == n - 1 ? "Starter" : "Upgrade " + (n - 1 - i);
				String value = p.isTradeable() ? UiUtil.compact(p.getCost()) : "—";
				Color color = i == 0 ? UiUtil.PROFIT : p.isTradeable() ? ColorScheme.GRAND_EXCHANGE_PRICE : UiUtil.MUTED;
				String line3 = p.getNote() != null ? p.getNote() : alts.size() > 1 ? "Cheapest of these: " + p.getName() : null;

				ItemRow row = new ItemRow(itemManager, p.getIconId(), title, value, color, tier, line3, tooltip(alts, p));
				UiUtil.addRow(list, row);
			}
		}

		budget.setText(UiUtil.compact(budgetTotal));
		mid.setText(UiUtil.compact(midTotal));
		best.setText(UiUtil.compact(bestTotal));
		untradeables.setText(unpriced.isEmpty()
			? "Totals are GE prices for tradeable gear only."
			: "<html>Totals cover tradeable gear only. Best-in-slot also needs: "
				+ String.join(", ", unpriced) + "</html>");
		repaintList();
	}

	private static long cost(GearPricer.Price p)
	{
		return p != null && p.isTradeable() ? p.getCost() : 0;
	}

	private static String tooltip(List<String> alts, GearPricer.Price p)
	{
		StringBuilder sb = new StringBuilder("<html>");
		sb.append(alts.size() > 1 ? "Equally good options:<br>" : "");
		for (String a : alts)
		{
			sb.append("• ").append(a.replace("&", "&amp;")).append("<br>");
		}
		if (p.isTradeable())
		{
			sb.append("Cheapest: ").append(p.getName()).append(" ≈ ").append(UiUtil.gp(p.getCost())).append(" gp");
		}
		if (p.getNote() != null)
		{
			sb.append("<br>").append(p.getNote());
		}
		return sb.append("</html>").toString();
	}

	private static JLabel stat()
	{
		return UiUtil.label("-", Color.WHITE, true);
	}

	private static JPanel box(String title, JLabel value)
	{
		JPanel p = new JPanel(new GridLayout(2, 1));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(new EmptyBorder(4, 6, 4, 6));
		p.add(UiUtil.label(title, UiUtil.MUTED, false));
		p.add(value);
		return p;
	}

	private void repaintList()
	{
		list.revalidate();
		list.repaint();
	}
}
