package com.moneymaker.ui;

import com.moneymaker.MoneyMakerConfig;
import com.moneymaker.MoneyMakerPlugin;
import com.moneymaker.data.Gear;
import com.moneymaker.data.ItemPrice;
import com.moneymaker.data.PriceBook;
import com.moneymaker.data.Recipe;
import com.moneymaker.data.Training;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/** Fastest way to a milestone level: best method at every level, how long it takes and what you need. */
class TrainPanel extends JPanel
{
	private final MoneyMakerPlugin plugin;
	private final ItemManager itemManager;
	private final JComboBox<String> skill = new JComboBox<>();
	private final JSpinner from = new JSpinner(new SpinnerNumberModel(1, 1, 98, 1));
	private final JComboBox<Target> target = new JComboBox<>();
	private final JCheckBox noSupplies = new JCheckBox("Only methods with no supplies");
	private final JCheckBox intense = new JCheckBox("Include click-intensive methods", true);
	private final JLabel summary = UiUtil.label(" ", Color.WHITE, false);
	private final JPanel list = UiUtil.verticalList();
	private final List<String> skills;
	private boolean updating;
	/** Last skill/level we filled the controls from, so logging in fills them once. */
	private String filledFor = "";

	private static class Target
	{
		final int level;
		final String text;

		Target(int level, String text)
		{
			this.level = level;
			this.text = text;
		}

		@Override
		public String toString()
		{
			return level + " · " + text;
		}
	}

	TrainPanel(MoneyMakerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.skills = plugin.getTraining().getSkills();
		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 4));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);

		for (String s : skills)
		{
			skill.addItem(pretty(s));
		}
		skill.setFocusable(false);
		skill.addActionListener(e ->
		{
			if (!updating)
			{
				fillFromLevel();
				fillTargets();
				refresh();
			}
		});
		controls.add(skill);

		JPanel fromRow = new JPanel(new BorderLayout(4, 0));
		fromRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		fromRow.add(UiUtil.label("From level", UiUtil.MUTED, false), BorderLayout.WEST);
		fromRow.add(from, BorderLayout.CENTER);
		from.addChangeListener(e ->
		{
			if (!updating)
			{
				fillTargets();
				refresh();
			}
		});
		controls.add(fromRow);

		target.setFocusable(false);
		target.addActionListener(e ->
		{
			if (!updating)
			{
				refresh();
			}
		});
		controls.add(target);

		for (JCheckBox c : new JCheckBox[]{noSupplies, intense})
		{
			c.setBackground(ColorScheme.DARK_GRAY_COLOR);
			c.setFocusable(false);
			c.addActionListener(e -> refresh());
			controls.add(c);
		}
		controls.add(summary);

		add(controls, BorderLayout.NORTH);
		add(UiUtil.scroll(list), BorderLayout.CENTER);

		fillFromLevel();
		fillTargets();
		refresh();
	}

	private String selectedSkill()
	{
		int i = skill.getSelectedIndex();
		return i < 0 || i >= skills.size() ? "" : skills.get(i);
	}

	/** Your level when logged in, else whatever was typed. */
	private void fillFromLevel()
	{
		long[] s = plugin.getSkill(selectedSkill());
		if (s != null)
		{
			updating = true;
			from.setValue((int) Math.max(1, Math.min(98, s[0])));
			updating = false;
		}
	}

	private void fillTargets()
	{
		updating = true;
		Target keep = (Target) target.getSelectedItem();
		target.removeAllItems();
		int fromLvl = (Integer) from.getValue();
		List<Target> ts = new ArrayList<>();
		for (Training.Milestone m : plugin.getTraining().getMilestones(selectedSkill()))
		{
			if (m.getLevel() > fromLvl)
			{
				ts.add(new Target(m.getLevel(), m.getText()));
			}
		}
		if (ts.isEmpty() || ts.get(0).level != fromLvl + 1)
		{
			ts.add(0, new Target(fromLvl + 1, "Next level"));
		}
		if (ts.get(ts.size() - 1).level != 99)
		{
			ts.add(new Target(99, "Skill cape"));
		}
		Target select = ts.size() > 1 && ts.get(0).text.equals("Next level") ? ts.get(1) : ts.get(0);
		for (Target t : ts)
		{
			target.addItem(t);
			if (keep != null && keep.level == t.level)
			{
				select = t;
			}
		}
		target.setSelectedItem(select);
		updating = false;
	}

	/** Called when skills or prices change. */
	void refresh()
	{
		String sk = selectedSkill();
		long[] mine = plugin.getSkill(sk);
		if (mine != null && !filledFor.equals(sk + mine[0]))
		{
			filledFor = sk + mine[0];
			fillFromLevel();
			fillTargets();
		}

		list.removeAll();
		Target t = (Target) target.getSelectedItem();
		if (sk.isEmpty() || t == null)
		{
			repaintList();
			return;
		}
		MoneyMakerConfig config = plugin.getConfig();
		boolean iron = plugin.isIronman();
		int fromLvl = (Integer) from.getValue();
		long fromXp = mine != null && mine[0] == fromLvl ? mine[1] : Training.xpForLevel(fromLvl);

		Training.Filter filter = new Training.Filter();
		filter.ironman = iron;
		filter.members = config.includeMembers();
		filter.intense = intense.isSelected();
		Training training = plugin.getTraining();
		filter.noSupplies = noSupplies.isSelected();
		List<Training.Step> steps = training.plan(sk, fromXp, t.level, filter);

		double hours = 0;
		double days = 0;
		for (Training.Step s : steps)
		{
			hours += s.getHours();
			days += s.getDays();
		}
		long xpNeeded = Training.xpForLevel(t.level) - fromXp;
		summary.setText("<html><b>" + pretty(sk) + " " + fromLvl + " → " + t.level + "</b> (" + esc(t.text) + ")<br>"
			+ UiUtil.gp(xpNeeded) + " xp · about " + time(hours) + (days > 0 ? " of runs over ~" + Math.round(days) + " days" : "")
			+ (iron ? "<br><i>Ironman mode: shows what to gather</i>" : "") + "</html>");

		addGear(sk, mine != null ? (int) mine[0] : fromLvl, mine != null);

		list.add(UiUtil.header("Fastest route"));
		if (steps.isEmpty())
		{
			list.add(UiUtil.emptyState("No methods match your filters at this level."));
		}
		for (Training.Step s : steps)
		{
			UiUtil.addRow(list, stepRow(s, iron));
		}

		boolean hasRuns = false;
		for (Training.Method m : training.getMethods(sk))
		{
			hasRuns |= m.getCycleMinutes() > 0;
		}
		if (hasRuns)
		{
			Training.Filter runs = new Training.Filter();
			runs.members = filter.members;
			runs.timedOnly = true;
			List<Training.Step> runSteps = training.plan(sk, fromXp, t.level, runs);
			list.add(UiUtil.header("Or with farm runs (best tree each level)"));
			for (Training.Step s : runSteps)
			{
				UiUtil.addRow(list, stepRow(s, iron));
			}
			list.add(UiUtil.emptyState("Tip: do fruit tree, hardwood and herb runs at the same time to level faster."));
		}

		list.add(UiUtil.header("All methods at level " + fromLvl));
		List<Training.Method> all = new ArrayList<>(training.getMethods(sk));
		all.sort(Comparator.comparing((Training.Method m) -> m.getLevel() > fromLvl)
			.thenComparing(Comparator.comparingDouble((Training.Method m) -> m.xpPerHour(Math.max(fromLvl, m.getLevel()))).reversed()));
		for (Training.Method m : all)
		{
			if (!filter.members && m.isMembers())
			{
				continue;
			}
			UiUtil.addRow(list, methodRow(m, fromLvl, iron));
		}
		repaintList();
	}

	private ItemRow stepRow(Training.Step s, boolean iron)
	{
		Training.Method m = s.getMethod();
		String title = "Lv " + s.getFromLevel() + "–" + s.getToLevel() + ": " + m.getName();
		String line2 = "~" + UiUtil.compact(Math.round(m.xpPerHour(s.getFromLevel()))) + " xp/h · " + UiUtil.compact(Math.round(s.getXp())) + " xp"
			+ (s.getActions() > 0 ? " · " + UiUtil.gp(s.getActions()) + " " + plural(m.getUnit(), s.getActions()) : "");
		String line3 = supplies(m, s.getActions(), iron);
		String value = s.getDays() > 0 ? "~" + Math.round(s.getDays()) + "d" : time(s.getHours());
		ItemRow row = new ItemRow(itemManager, icon(m), title, value, Color.WHITE, line2, line3, tooltip(m, s, iron));
		row.setLine3Color(line3.startsWith("Needs") ? UiUtil.LOSS : UiUtil.MUTED);
		return row;
	}

	private ItemRow methodRow(Training.Method m, int fromLvl, boolean iron)
	{
		boolean locked = m.getLevel() > fromLvl;
		double rate = m.xpPerHour(Math.max(fromLvl, m.getLevel()));
		String line2 = (locked ? "Unlocks at " + m.getLevel() : "Level " + m.getLevel() + "+")
			+ (m.isMembers() ? "" : " · F2P") + (m.isIntense() ? " · intense" : "")
			+ (m.getNeeds().isEmpty() ? " · no supplies" : "");
		String line3 = !m.getRequirements().isEmpty() ? "Needs: " + m.getRequirements() : m.getNote();
		ItemRow row = new ItemRow(itemManager, icon(m), m.getName(), UiUtil.compact(Math.round(rate)) + "/h",
			locked ? UiUtil.MUTED : UiUtil.PROFIT, line2, line3.isEmpty() ? " " : line3, tooltip(m, null, iron));
		row.setLine3Color(UiUtil.MUTED);
		return row;
	}

	/** Your tools, outfit and useful items for the skill, from your bag, worn gear and bank. */
	private void addGear(String sk, int level, boolean loggedIn)
	{
		List<Gear.Line> lines = plugin.getGear().check(sk, level, plugin.getOwned());
		if (lines.isEmpty())
		{
			return;
		}
		list.add(UiUtil.header("Your gear for " + pretty(sk)));
		if (!loggedIn)
		{
			list.add(UiUtil.emptyState("Log in so I can check your bag, worn gear and bank."));
			return;
		}
		JPanel box = new JPanel(new GridLayout(0, 1, 0, 2));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(new EmptyBorder(6, 6, 6, 6));
		for (Gear.Line l : lines)
		{
			Color c = l.getKind() == Gear.Line.Kind.GOOD ? UiUtil.PROFIT
				: l.getKind() == Gear.Line.Kind.UPGRADE ? new Color(255, 200, 70)
				: l.getKind() == Gear.Line.Kind.MISSING ? UiUtil.MUTED : Color.WHITE;
			String mark = l.getKind() == Gear.Line.Kind.GOOD ? "✔ " : l.getKind() == Gear.Line.Kind.UPGRADE ? "▲ " : l.getKind() == Gear.Line.Kind.MISSING ? "✘ " : "• ";
			JLabel label = UiUtil.label("<html><body style='width:165px'>" + mark + esc(l.getText()) + "</body></html>", c, false);
			box.add(label);
		}
		if (!plugin.isBankSeen())
		{
			box.add(UiUtil.label("<html><i>Open your bank once so I can include it.</i></html>", UiUtil.MUTED, false));
		}
		UiUtil.addRow(list, box);
	}

	/** "Gather: 2,000 × Feather" (ironman) or "Buy: ... ≈ 40k gp". */
	private String supplies(Training.Method m, long actions, boolean iron)
	{
		if (m.getNeeds().isEmpty() || actions <= 0)
		{
			if (!m.getRequirements().isEmpty())
			{
				return "Needs: " + m.getRequirements();
			}
			return m.getNote().isEmpty() ? " " : m.getNote();
		}
		StringBuilder sb = new StringBuilder(iron ? "Gather: " : "Buy: ");
		java.util.Map<String, Gear.Owned> owned = plugin.getOwned();
		long cost = 0;
		PriceBook prices = plugin.getPrices();
		for (int i = 0; i < m.getNeeds().size(); i++)
		{
			Recipe.Stack st = m.getNeeds().get(i);
			long qty = (long) Math.ceil(st.getQty() * actions);
			sb.append(i > 0 ? ", " : "").append(UiUtil.compact(qty)).append(' ').append(st.getItem());
			Gear.Owned have = Gear.find(owned, st.getItem());
			if (have != null)
			{
				sb.append(" (have ").append(UiUtil.compact(have.getQty())).append(')');
			}
			ItemPrice p = prices.get(st.getItem());
			if (p != null)
			{
				cost += qty * p.buyPrice(plugin.getConfig().priceMode());
			}
		}
		if (!iron && cost > 0)
		{
			sb.append(" ≈ ").append(UiUtil.compact(cost)).append(" gp");
		}
		return sb.toString();
	}

	private String tooltip(Training.Method m, Training.Step s, boolean iron)
	{
		StringBuilder sb = new StringBuilder("<html><b>").append(esc(m.getName())).append("</b>");
		sb.append("<br>From level ").append(m.getLevel()).append(m.isMembers() ? " (members)" : " (F2P)");
		if (m.getXpEach() > 0)
		{
			sb.append("<br>").append(num(m.getXpEach())).append(" xp per ").append(m.getUnit());
		}
		sb.append("<br>~").append(UiUtil.gp(Math.round(m.xpPerHour(m.getLevel())))).append(" xp/h at ").append(m.getLevel())
			.append(", ~").append(UiUtil.gp(Math.round(m.xpPerHour(99)))).append(" at 99");
		if (!m.getRequirements().isEmpty())
		{
			sb.append("<br>Needs: ").append(esc(m.getRequirements()));
		}
		if (s != null && s.getActions() > 0)
		{
			long a = s.getActions();
			if (!m.getNeeds().isEmpty())
			{
				sb.append("<br><br><b>").append(iron ? "Gather" : "Buy").append(" for this stretch</b>");
				for (Recipe.Stack st : m.getNeeds())
				{
					sb.append("<br>").append(UiUtil.gp((long) Math.ceil(st.getQty() * a))).append(" × ").append(esc(st.getItem()));
				}
			}
			if (!m.getGives().isEmpty())
			{
				sb.append("<br><b>You'll end up with</b>");
				for (Recipe.Stack st : m.getGives())
				{
					sb.append("<br>").append(UiUtil.gp((long) Math.floor(st.getQty() * a))).append(" × ").append(esc(st.getItem()));
				}
			}
			if (m.getCycleMinutes() > 0)
			{
				sb.append("<br><br>").append(UiUtil.gp(a)).append(" runs, one every ").append(time(m.getCycleMinutes() / 60.0))
					.append(" (~").append(Math.round(s.getDays())).append(" days)");
			}
		}
		if (!m.getNote().isEmpty())
		{
			sb.append("<br><br><i>").append(esc(m.getNote())).append("</i>");
		}
		sb.append("<br><br>Rates are typical estimates from OSRS Wiki training guides.</html>");
		return sb.toString();
	}

	private int icon(Training.Method m)
	{
		PriceBook prices = plugin.getPrices();
		for (List<Recipe.Stack> l : List.of(m.getGives(), m.getNeeds()))
		{
			for (Recipe.Stack st : l)
			{
				ItemPrice p = prices.get(st.getItem());
				if (p != null)
				{
					return p.getId();
				}
			}
		}
		return -1;
	}

	private static String plural(String unit, long n)
	{
		if (n == 1 || unit.endsWith("s") || unit.contains(" "))
		{
			return unit;
		}
		return unit.endsWith("sh") ? unit + "es" : unit + "s";
	}

	private static String time(double hours)
	{
		if (hours <= 0)
		{
			return "-";
		}
		long mins = Math.round(hours * 60);
		if (mins < 60)
		{
			return mins + "m";
		}
		return mins < 6000 ? (mins / 60) + "h " + (mins % 60) + "m" : (mins / 60) + "h";
	}

	private static String num(double v)
	{
		return v == Math.rint(v) ? UiUtil.gp((long) v) : String.format(Locale.ROOT, "%.1f", v);
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
