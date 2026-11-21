package com.moneymaker.ui;

import com.moneymaker.MoneyMakerPlugin;
import com.moneymaker.route.Route;
import com.moneymaker.route.RouteService;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;

/** The box above the tabs while a route is on: where it goes, the steps, and a button to clear it. */
class RouteBar extends JPanel
{
	/** Width the text wraps at, in HTML pixels (the side panel is narrow). */
	private static final int TEXT_WIDTH = 150;
	private static final int MAX_STEPS = 8;
	private static final Color NOTE = new Color(255, 200, 70);

	private final MoneyMakerPlugin plugin;
	private final JLabel title = UiUtil.label(" ", Color.WHITE, true);
	private final JLabel body = UiUtil.label(" ", UiUtil.MUTED, false);
	private final JLabel note = UiUtil.label(" ", NOTE, false);
	private final JButton clear = new JButton("Clear route");

	RouteBar(MoneyMakerPlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout(0, 4));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(new EmptyBorder(6, 6, 6, 6));

		JPanel text = new JPanel();
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		text.setOpaque(false);
		for (JLabel l : new JLabel[]{title, body, note})
		{
			l.setAlignmentX(Component.LEFT_ALIGNMENT);
			text.add(l);
		}
		add(text, BorderLayout.CENTER);

		clear.setFocusable(false);
		clear.addActionListener(e -> plugin.clearRoute());
		add(clear, BorderLayout.SOUTH);
		refresh();
	}

	void refresh()
	{
		RouteService routes = plugin.getRoutes();
		String name = routes == null ? null : routes.getName();
		String status = routes == null ? null : routes.getStatus();
		if (name == null && status == null)
		{
			setVisible(false);
			return;
		}
		setVisible(true);
		title.setText(wrap(name == null ? "Route" : "Route to " + esc(name)));
		Route route = routes.getRoute();
		body.setVisible(route != null);
		if (route != null)
		{
			body.setText(wrap(steps(route)));
		}
		note.setVisible(status != null);
		if (status != null)
		{
			note.setText(wrap(esc(status)));
		}
		// "You've arrived" stays up with nothing to clear but the message itself
		clear.setText(name == null ? "OK" : "Clear route");
		revalidate();
		repaint();
	}

	private static String steps(Route route)
	{
		List<Route.Step> steps = route.getSteps();
		StringBuilder sb = new StringBuilder();
		int shown = Math.min(steps.size(), MAX_STEPS);
		for (int i = 0; i < shown; i++)
		{
			Route.Step s = steps.get(i);
			sb.append(i == 0 ? "" : "<br>").append(i + 1).append(". ");
			sb.append(s.isTransport() ? "<font color='#ffffff'>" + esc(s.getText()) + "</font>" : esc(s.getText()));
		}
		if (steps.size() > shown)
		{
			sb.append("<br>...and ").append(steps.size() - shown).append(" more");
		}
		if (steps.isEmpty())
		{
			sb.append("You're right next to it.");
		}
		return sb.toString();
	}

	private static String wrap(String html)
	{
		return "<html><div style='width:" + TEXT_WIDTH + "px'>" + html + "</div></html>";
	}

	private static String esc(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
