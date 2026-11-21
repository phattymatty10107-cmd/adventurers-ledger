package com.moneymaker.ui;

import com.moneymaker.MoneyMakerPlugin;
import com.moneymaker.data.BossCheck;
import com.moneymaker.data.BossQuests;
import com.moneymaker.route.Places;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import com.moneymaker.data.BossSetup;
import com.moneymaker.data.Gear;
import com.moneymaker.data.GearPricer;
import com.moneymaker.data.PriceBook;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.api.QuestState;
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
	private final JButton way = new JButton("Show the way there");

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
		way.setFocusable(false);
		way.addActionListener(e ->
		{
			BossSetup s = (BossSetup) setups.getSelectedItem();
			Places.Place p = s == null ? null : plugin.getPlaces().find("boss", s.getBoss());
			if (p != null && plugin.canRoutePerks())
			{
				plugin.routeTo(p, "boss");
			}
		});
		top.add(way);
		top.setBorder(new EmptyBorder(0, 0, 4, 0));

		add(top, BorderLayout.NORTH);
		add(UiUtil.scroll(list), BorderLayout.CENTER);
		refresh();
	}

	void refresh()
	{
		list.removeAll();
		BossSetup setup = (BossSetup) setups.getSelectedItem();
		showWayButton(setup);
		PriceBook prices = plugin.getPrices();
		GearPricer pricer = plugin.getGearPricer();

		if (setup == null)
		{
			list.add(UiUtil.emptyState("No boss data loaded."));
			repaintList();
			return;
		}
		addCheck(setup, !prices.isEmpty());
		addQuests(setup);
		if (prices.isEmpty())
		{
			list.add(UiUtil.emptyState("Fetching prices..."));
			repaintList();
			return;
		}

		Map<String, Gear.Owned> owned = plugin.isLoggedIn() ? plugin.getOwned() : java.util.Collections.emptyMap();
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

			list.add(UiUtil.header(slot.getName() + "  (weakest to strongest)"));
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
				Gear.Owned mine = BossCheck.ownedInTier(alts, owned);
				if (mine != null)
				{
					line3 = "You own this (" + mine.getWhere() + ")";
				}

				ItemRow row = new ItemRow(itemManager, p.getIconId(), title, value, color, tier, line3, tooltip(alts, p));
				if (mine != null)
				{
					row.setLine3Color(UiUtil.PROFIT);
				}
				else if (p.isTradeable())
				{
					String wanted = p.getName();
					row.action("Plan how to afford this", () -> plugin.planFor(wanted));
				}
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

	/**
	 * "Can you fight it?": your levels and quests against what the boss needs, and how much of
	 * the gear you already own. Needs you to be logged in.
	 */
	private void addCheck(BossSetup setup, boolean withGear)
	{
		List<BossCheck.SkillReq> skills = plugin.getBossCheck().skillsFor(setup.getBoss());
		BossQuests quests = plugin.getBossQuests();

		list.add(UiUtil.header("Can you fight it?"));
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(new EmptyBorder(5, 6, 5, 6));

		if (!plugin.isLoggedIn())
		{
			StringBuilder sb = new StringBuilder("Log in to check your levels, quests and gear.");
			for (BossCheck.SkillReq r : skills)
			{
				sb.append("<br>Needs ").append(r.getLevel()).append(' ').append(pretty(r.getSkill()));
			}
			box.add(checkLine(sb.toString(), UiUtil.MUTED));
			UiUtil.addRow(list, box);
			return;
		}

		int missing = 0;
		for (BossCheck.SkillReq r : skills)
		{
			long[] have = plugin.getSkill(r.getSkill());
			boolean ok = have != null && have[0] >= r.getLevel();
			missing += ok ? 0 : 1;
			box.add(checkLine((ok ? "&#10003; " : "&#10007; ") + r.getLevel() + " " + pretty(r.getSkill())
				+ " (you: " + (have != null ? have[0] : "?") + ")"
				+ (r.getNote().isEmpty() ? "" : "<br><i>" + esc(r.getNote()) + "</i>"), ok ? UiUtil.PROFIT : UiUtil.LOSS));
		}

		LinkedHashSet<String> todo = new LinkedHashSet<>();
		boolean needsQuests = false;
		for (BossQuests.Req r : quests.forBoss(setup.getBoss()))
		{
			needsQuests = true;
			if (plugin.getQuestState(r.getQuest()) == QuestState.FINISHED)
			{
				continue;
			}
			for (String q : quests.lineFor(r.getQuest()))
			{
				if (plugin.getQuestState(q) != QuestState.FINISHED)
				{
					todo.add(q);
				}
			}
			todo.add(r.getQuest());
		}
		if (needsQuests)
		{
			missing += todo.isEmpty() ? 0 : 1;
			box.add(todo.isEmpty() ? checkLine("&#10003; Quests done", UiUtil.PROFIT)
				: checkLine("&#10007; " + todo.size() + (todo.size() == 1 ? " quest" : " quests") + " to do (listed below)", UiUtil.LOSS));
		}
		if (skills.isEmpty() && !needsQuests)
		{
			box.add(checkLine("&#10003; No level or quest needed", UiUtil.PROFIT));
		}

		if (withGear)
		{
			BossCheck.Kit kit = BossCheck.kit(setup, plugin.getGearPricer(), plugin.getPrices(), plugin.getOwned());
			StringBuilder sb = new StringBuilder("Gear: you own something for <b>").append(kit.getSlotsOwned())
				.append(" of ").append(kit.getSlots()).append("</b> slots");
			if (kit.getCostToFill() > 0)
			{
				sb.append("<br>About <b>").append(UiUtil.compact(kit.getCostToFill()))
					.append("</b> to fill the rest with the cheapest options");
			}
			if (!kit.getToEarn().isEmpty())
			{
				sb.append("<br>Can't be bought: ").append(esc(String.join(", ", kit.getToEarn())));
			}
			if (!plugin.isBankSeen())
			{
				sb.append("<br><i>Open your bank once so it's counted.</i>");
			}
			box.add(checkLine(sb.toString(), Color.WHITE));
		}

		JLabel verdict = UiUtil.label(missing == 0 ? "Yes, you meet the requirements" : "Not yet", missing == 0 ? UiUtil.PROFIT : IN_PROGRESS, true);
		verdict.setAlignmentX(LEFT_ALIGNMENT);
		verdict.setBorder(new EmptyBorder(0, 0, 3, 0));
		box.add(verdict, 0);
		UiUtil.addRow(list, box);
	}

	private static JLabel checkLine(String html, Color color)
	{
		JLabel l = UiUtil.label("<html><div style='width:" + QUEST_TEXT_WIDTH + "px'>" + html + "</div></html>", color, false);
		l.setAlignmentX(LEFT_ALIGNMENT);
		l.setBorder(new EmptyBorder(1, 0, 1, 0));
		return l;
	}

	private static String pretty(String skill)
	{
		return skill.isEmpty() ? skill : skill.charAt(0) + skill.substring(1).toLowerCase(Locale.ROOT);
	}

	/** Width the quest text wraps at, in HTML pixels (the side panel is narrow). */
	private static final int QUEST_TEXT_WIDTH = 140;
	private static final Color IN_PROGRESS = new Color(255, 200, 70);

	/**
	 * The quests this boss needs, each with everything to do before it, in order. Shows how far
	 * you are when logged in; click any quest to open its guide on the wiki.
	 */
	private void addQuests(BossSetup setup)
	{
		BossQuests data = plugin.getBossQuests();
		List<BossQuests.Req> reqs = data.forBoss(setup.getBoss());
		if (reqs.isEmpty())
		{
			return;
		}

		// the whole to-do list: each needed quest after the quests it needs, nothing twice
		LinkedHashSet<String> all = new LinkedHashSet<>();
		for (BossQuests.Req r : reqs)
		{
			all.addAll(data.lineFor(r.getQuest()));
			all.add(r.getQuest());
		}
		int done = 0;
		boolean known = false;
		for (String q : all)
		{
			QuestState s = plugin.getQuestState(q);
			known |= s != null;
			if (s == QuestState.FINISHED)
			{
				done++;
			}
		}

		list.add(UiUtil.header("Quests needed"));
		String summary = !known ? all.size() + (all.size() == 1 ? " quest" : " quests") + " · log in to see your progress"
			: done == all.size() ? "All done"
			: done + " of " + all.size() + " done · click a quest for its guide";
		JLabel top = UiUtil.label(summary, known && done == all.size() ? UiUtil.PROFIT : UiUtil.MUTED, false);
		top.setBorder(new EmptyBorder(0, 2, 4, 2));
		UiUtil.addRow(list, top);

		for (BossQuests.Req r : reqs)
		{
			QuestState state = plugin.getQuestState(r.getQuest());
			UiUtil.addRow(list, questRow(r.getQuest(), r.getNote(), state, true, 0));
			// once the quest itself is done there's no point listing what led up to it
			if (state == QuestState.FINISHED)
			{
				continue;
			}
			int step = 0;
			for (String q : data.lineFor(r.getQuest()))
			{
				UiUtil.addRow(list, questRow(q, "", plugin.getQuestState(q), false, ++step));
			}
		}
	}

	/** The "show the way" button: there when this copy does boss routes and knows where the boss is. */
	private void showWayButton(BossSetup setup)
	{
		Places.Place p = setup == null ? null : plugin.getPlaces().find("boss", setup.getBoss());
		boolean perks = plugin.canRoutePerks();
		way.setVisible(p != null && (perks || plugin.routePerkHint() != null));
		way.setEnabled(p != null && perks);
		way.setToolTipText(p == null ? null : !perks ? plugin.routePerkHint()
			: p.getNote().isEmpty() ? "Draws the route on the map" : p.getNote());
	}

	private JLabel questRow(String quest, String note, QuestState state, boolean main, int step)
	{
		String mark = state == QuestState.FINISHED ? "&#10003; " : step > 0 ? step + ". " : "";
		StringBuilder sb = new StringBuilder("<html><div style='width:").append(QUEST_TEXT_WIDTH).append("px'>")
			.append(main ? "<b>" : "").append(mark).append(esc(quest)).append(main ? "</b>" : "");
		if (state == QuestState.IN_PROGRESS)
		{
			sb.append(" (started)");
		}
		if (!note.isEmpty())
		{
			sb.append("<br><i>").append(esc(note)).append("</i>");
		}
		sb.append("</div></html>");

		Color color = state == QuestState.FINISHED ? UiUtil.PROFIT : state == QuestState.IN_PROGRESS ? IN_PROGRESS
			: main ? Color.WHITE : UiUtil.MUTED;
		JLabel row = UiUtil.label(sb.toString(), color, false);
		row.setBorder(new EmptyBorder(1, main ? 2 : 10, 1, 2));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText("Click for the route to where " + quest + " starts, or its guide on the OSRS Wiki");
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				JPopupMenu menu = new JPopupMenu();
				JMenuItem go = new JMenuItem("Show the way to the start");
				go.addActionListener(a -> plugin.routeToQuest(quest));
				menu.add(go);
				JMenuItem guide = new JMenuItem("Open the wiki guide");
				guide.addActionListener(a -> LinkBrowser.browse(BossQuests.wikiUrl(quest)));
				menu.add(guide);
				menu.show(row, e.getX(), e.getY());
			}
		});
		return row;
	}

	private static String esc(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
