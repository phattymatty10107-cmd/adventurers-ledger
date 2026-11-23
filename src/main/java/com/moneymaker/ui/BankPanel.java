package com.moneymaker.ui;

import com.moneymaker.MoneyMakerPlugin;
import com.moneymaker.data.BankBook;
import com.moneymaker.data.BankBook.Category;
import com.moneymaker.data.BankBook.Entry;
import com.moneymaker.data.BankBook.Idea;
import com.moneymaker.data.ItemPrice;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;

/**
 * Your bank sorted by type, with tag tabs to paste into RuneLite's Bank Tags plugin and ideas for
 * what to sell, alch or throw out.
 */
class BankPanel extends JPanel
{
	private static final String SORTED = "Sorted by type";
	private static final String CLEAR_OUT = "Clear-out ideas";
	private static final String[] CLUTTER_LABELS = {"Clutter under 1k", "Clutter under 10k", "Clutter under 100k"};
	private static final long[] CLUTTER_LIMITS = {1_000, 10_000, 100_000};
	/** Rows shown per type or per idea list before "and N more". */
	private static final int MAX_ROWS = 60;
	/** Width wrapped text is laid out at, in HTML pixels (the side panel is narrow). */
	private static final int TEXT_WIDTH = 150;
	private static final String HOW_TO_IMPORT = "Copied. In your bank, right-click the + (new tag tab) button "
		+ "on the left and pick <b>Import tag tab</b>. Needs RuneLite's Bank Tags plugin (on by default).";

	private final MoneyMakerPlugin plugin;
	private final ItemManager itemManager;
	private final JComboBox<String> view = new JComboBox<>(new String[]{SORTED, CLEAR_OUT});
	private final JComboBox<String> clutterLimit = new JComboBox<>(CLUTTER_LABELS);
	private final JButton copy = new JButton();
	private final JLabel status = UiUtil.label(" ", UiUtil.MUTED, false);
	private final JLabel note = UiUtil.label(" ", UiUtil.MUTED, false);
	private final JPanel list = UiUtil.verticalList();
	/** Types opened up to show their items. */
	private final Set<Category> open = EnumSet.noneOf(Category.class);
	/** What the copy button puts on the clipboard right now. */
	private String toCopy;

	BankPanel(MoneyMakerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 4));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.setBorder(new EmptyBorder(0, 0, 2, 0));
		view.setFocusable(false);
		view.addActionListener(e -> refresh());
		clutterLimit.setFocusable(false);
		clutterLimit.setSelectedIndex(1);
		clutterLimit.setToolTipText("Stacks worth less than this in total count as clutter");
		clutterLimit.addActionListener(e -> refresh());
		copy.setFocusable(false);
		copy.addActionListener(e -> copyTab());

		JPanel top = new JPanel(new BorderLayout(0, 4));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.add(view);
		controls.add(clutterLimit);
		controls.add(copy);
		controls.add(status);
		top.add(controls, BorderLayout.NORTH);
		note.setBorder(new EmptyBorder(0, 2, 2, 2));
		top.add(note, BorderLayout.CENTER);

		add(top, BorderLayout.NORTH);
		add(UiUtil.scroll(list), BorderLayout.CENTER);
		refresh();
	}

	void refresh()
	{
		list.removeAll();
		boolean clearOut = CLEAR_OUT.equals(view.getSelectedItem());
		clutterLimit.setVisible(clearOut);
		setNote(null);
		toCopy = null;

		if (!plugin.isBankSeen())
		{
			status.setText("Bank not seen yet");
			copy.setVisible(false);
			list.add(UiUtil.emptyState(plugin.isLoggedIn()
				? "Open your bank once and the Ledger will sort it. It's remembered for next time."
				: "Log in and open your bank once and the Ledger will sort it."));
			repaintList();
			return;
		}

		BankBook bank = plugin.getBankBook();
		int pending = plugin.getBankItemsPending();
		String more = pending > 0 ? " (looking up " + pending + " more)" : "";
		boolean noPrices = plugin.getPrices().isEmpty();
		status.setText(bank.getEntries().size() + " items, worth " + (noPrices ? "?" : UiUtil.compact(bank.getTotalValue()))
			+ more);
		copy.setVisible(true);

		if (clearOut)
		{
			showClearOut(bank, noPrices);
		}
		else
		{
			showSorted(bank);
		}
		repaintList();
	}

	// ---------------------------------------------------------------- sorted by type

	private void showSorted(BankBook bank)
	{
		copy.setText("Copy tidy bank tab");
		copy.setToolTipText("Copies a bank tag tab called \"" + BankBook.LAYOUT_TAG + "\" with your whole bank "
			+ "laid out by type, each type on its own rows");
		toCopy = bank.getEntries().isEmpty() ? null : bank.tidyLayout();
		copy.setEnabled(toCopy != null);

		Map<Category, List<Entry>> byType = bank.byCategory();
		if (byType.isEmpty())
		{
			list.add(UiUtil.emptyState("Nothing to sort yet."));
			return;
		}
		for (Map.Entry<Category, List<Entry>> e : byType.entrySet())
		{
			Category cat = e.getKey();
			List<Entry> items = e.getValue();
			long value = 0;
			for (Entry it : items)
			{
				value += it.getValue();
			}
			boolean isOpen = open.contains(cat);
			ItemRow header = new ItemRow(itemManager, cat.getIcon(), (isOpen ? "- " : "+ ") + cat.getLabel(),
				UiUtil.compact(value), UiUtil.PROFIT,
				items.size() + (items.size() == 1 ? " item" : " items"), null,
				"Click to " + (isOpen ? "hide" : "show") + " the items. Right-click to copy this type as its own tag tab.");
			header.onClick(() ->
			{
				if (!open.remove(cat))
				{
					open.add(cat);
				}
				refresh();
			});
			header.action("Copy as its own tag tab (\"" + cat.getTag() + "\")",
				() -> copyText(BankBook.categoryTab(cat, items)));
			UiUtil.addRow(list, header);
			if (isOpen)
			{
				addItems(items);
			}
		}
	}

	private void addItems(List<Entry> items)
	{
		int shown = 0;
		for (Entry it : items)
		{
			if (shown++ >= MAX_ROWS)
			{
				list.add(UiUtil.label("  and " + (items.size() - MAX_ROWS) + " more", UiUtil.MUTED, false));
				break;
			}
			String each = it.isTradeable() ? UiUtil.gp(it.getGeEach()) + " each" : "Untradeable";
			UiUtil.addRow(list, new ItemRow(itemManager, it.getId(), it.getName(),
				it.getValue() > 0 ? UiUtil.compact(it.getValue()) : null, UiUtil.MUTED,
				"x" + UiUtil.gp(it.getQty()) + " · " + each, null, null));
		}
	}

	// ---------------------------------------------------------------- clear-out ideas

	private void showClearOut(BankBook bank, boolean noPrices)
	{
		copy.setText("Copy clear-out tab");
		copy.setToolTipText("Copies a bank tag tab called \"" + BankBook.CLEAR_OUT_TAG + "\" holding everything "
			+ "below, so you can open it in the bank and sell, alch or drop as you go");
		if (noPrices)
		{
			copy.setEnabled(false);
			list.add(UiUtil.emptyState(plugin.getPriceError() != null ? "Couldn't load prices." : "Fetching prices..."));
			return;
		}

		ItemPrice nature = plugin.getPrices().get("Nature rune");
		long natureRune = nature == null ? 0 : nature.buyPrice(plugin.getConfig().priceMode());
		List<Idea> tools = bank.outgrown(plugin.getOutgrownTools());
		List<Idea> alch = bank.betterAlched(natureRune);
		List<Idea> clutter = bank.clutter(CLUTTER_LIMITS[Math.max(0, clutterLimit.getSelectedIndex())]);

		// one stack can be on more than one list; count it once
		Set<Entry> all = new LinkedHashSet<>();
		List<Idea> allIdeas = new ArrayList<>();
		for (List<Idea> l : java.util.Arrays.asList(tools, alch, clutter))
		{
			for (Idea i : l)
			{
				if (all.add(i.getEntry()))
				{
					allIdeas.add(i);
				}
			}
		}
		long cash = 0;
		for (Entry e : all)
		{
			cash += e.getValue();
		}
		toCopy = allIdeas.isEmpty() ? null : BankBook.clearOutTab(allIdeas);
		copy.setEnabled(toCopy != null);

		if (all.isEmpty())
		{
			list.add(UiUtil.emptyState("Nothing to clear out. Your bank is in good shape!"));
			return;
		}
		setNote("Clearing these frees <b>" + all.size() + "</b> bank slot" + (all.size() == 1 ? "" : "s")
			+ " and is worth about <b>" + UiUtil.compact(cash) + "</b> gp.");

		section("Tools you've outgrown", "Keep the best one; the rest just take up space.", tools, false);
		section("Worth more alched", "High alch beats the GE price, even after the nature rune.", alch, true);
		section("Low-value clutter", plugin.isIronman()
			? "Cheap stacks you could drop, alch or sell to a shop."
			: "Cheap stacks to sell or drop. Runes, teleports, tools and clues are kept.", clutter, false);
	}

	private void section(String title, String help, List<Idea> ideas, boolean showGain)
	{
		if (ideas.isEmpty())
		{
			return;
		}
		list.add(UiUtil.header(title + " (" + ideas.size() + ")"));
		JLabel h = UiUtil.label(wrap(help), UiUtil.MUTED, false);
		h.setBorder(new EmptyBorder(0, 2, 4, 2));
		UiUtil.addRow(list, h);
		int shown = 0;
		for (Idea i : ideas)
		{
			if (shown++ >= MAX_ROWS)
			{
				list.add(UiUtil.label("  and " + (ideas.size() - MAX_ROWS) + " more", UiUtil.MUTED, false));
				break;
			}
			Entry e = i.getEntry();
			String value = showGain ? "+" + UiUtil.compact(i.getGain()) : UiUtil.compact(e.getValue());
			UiUtil.addRow(list, new ItemRow(itemManager, e.getId(), e.getName(), value,
				showGain ? UiUtil.PROFIT : UiUtil.MUTED, "x" + UiUtil.gp(e.getQty()) + " · " + e.getCategory().getLabel(),
				i.getReason(), i.getReason()));
		}
	}

	// ---------------------------------------------------------------- clipboard

	private void copyTab()
	{
		if (toCopy != null)
		{
			copyText(toCopy);
		}
	}

	private void copyText(String text)
	{
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		setNote(HOW_TO_IMPORT);
	}

	/** The line under the buttons: hidden when there's nothing to say. */
	private void setNote(String html)
	{
		note.setVisible(html != null);
		note.setText(html == null ? "" : wrap(html));
	}

	private static String wrap(String html)
	{
		return "<html><div style='width:" + TEXT_WIDTH + "px'>" + html + "</div></html>";
	}

	private void repaintList()
	{
		list.revalidate();
		list.repaint();
	}
}
