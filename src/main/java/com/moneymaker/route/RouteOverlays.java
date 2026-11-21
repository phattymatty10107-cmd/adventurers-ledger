package com.moneymaker.route;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.image.BufferedImage;
import java.util.Collection;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;

/** Draws the route: a line across the world map, the minimap and the ground in front of you. */
public final class RouteOverlays
{
	/** Only this much of the route ahead of you is drawn in the game view and on the minimap. */
	private static final int AHEAD = 120;
	private static final Stroke LINE = new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Stroke MAP_LINE = new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Stroke MAP_HOP = new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 5f}, 0f);

	private RouteOverlays()
	{
	}

	/** The tile to draw at, in the scene you're in (instances are copies, so the tile is looked up there). */
	private static LocalPoint local(WorldView view, int tile)
	{
		WorldPoint wp = new WorldPoint(Pos.x(tile), Pos.y(tile), Pos.plane(tile));
		if (view.isInstance())
		{
			Collection<WorldPoint> copies = WorldPoint.toLocalInstance(view, wp);
			if (copies.isEmpty())
			{
				return null;
			}
			wp = copies.iterator().next();
		}
		return LocalPoint.fromWorld(view, wp);
	}

	/** The line on the ground, with a marked tile wherever you use a door, boat or teleport. */
	public static final class Scene extends Overlay
	{
		private final Client client;
		private final RouteService routes;
		private final Supplier<Color> color;

		public Scene(Client client, RouteService routes, Supplier<Color> color)
		{
			this.client = client;
			this.routes = routes;
			this.color = color;
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_SCENE);
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			Route route = routes.getRoute();
			WorldView view = client.getTopLevelWorldView();
			if (route == null || view == null)
			{
				return null;
			}
			int[] tiles = route.getTiles();
			int plane = view.getPlane();
			int from = Math.max(0, routes.getProgress());
			int to = Math.min(tiles.length - 1, from + AHEAD);
			Color c = color.get();
			if (c == null)
			{
				// turned off in the settings
				return null;
			}
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setStroke(LINE);
			Point prev = null;
			for (int i = from; i <= to; i++)
			{
				int tile = tiles[i];
				LocalPoint lp = Pos.plane(tile) == plane ? local(view, tile) : null;
				Point at = lp == null ? null : Perspective.localToCanvas(client, lp, plane);
				if (at != null && prev != null && route.hopInto(i) == null)
				{
					g.setColor(c);
					g.drawLine(prev.getX(), prev.getY(), at.getX(), at.getY());
				}
				prev = at;
				boolean last = i == tiles.length - 1;
				Transport next = last ? null : route.hopInto(i + 1);
				if (lp != null && (next != null || last))
				{
					Polygon poly = Perspective.getCanvasTilePoly(client, lp);
					if (poly != null)
					{
						g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 60));
						g.fill(poly);
						g.setColor(c);
						g.draw(poly);
					}
					String text = last ? (route.isReached() ? routes.getName() : null) : next.label();
					if (at != null && text != null)
					{
						label(g, text, at.getX(), at.getY() - 18);
					}
				}
			}
			return null;
		}

		private static void label(Graphics2D g, String text, int cx, int y)
		{
			FontMetrics fm = g.getFontMetrics();
			int x = cx - fm.stringWidth(text) / 2;
			g.setColor(Color.BLACK);
			g.drawString(text, x + 1, y + 1);
			g.setColor(Color.WHITE);
			g.drawString(text, x, y);
		}
	}

	/** The same line on the minimap. */
	public static final class Minimap extends Overlay
	{
		private final Client client;
		private final RouteService routes;
		private final Supplier<Color> color;

		public Minimap(Client client, RouteService routes, Supplier<Color> color)
		{
			this.client = client;
			this.routes = routes;
			this.color = color;
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.ABOVE_WIDGETS);
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			Route route = routes.getRoute();
			WorldView view = client.getTopLevelWorldView();
			if (route == null || view == null)
			{
				return null;
			}
			int[] tiles = route.getTiles();
			int plane = view.getPlane();
			int from = Math.max(0, routes.getProgress());
			int to = Math.min(tiles.length - 1, from + AHEAD);
			Color c = color.get();
			if (c == null)
			{
				return null;
			}
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setStroke(LINE);
			g.setColor(c);
			Point prev = null;
			for (int i = from; i <= to; i++)
			{
				int tile = tiles[i];
				LocalPoint lp = Pos.plane(tile) == plane ? local(view, tile) : null;
				Point at = lp == null ? null : Perspective.localToMinimap(client, lp);
				if (at != null && prev != null && route.hopInto(i) == null)
				{
					g.drawLine(prev.getX(), prev.getY(), at.getX(), at.getY());
				}
				prev = at;
			}
			return null;
		}
	}

	/** The whole route on the world map: solid where you walk, dashed where something carries you. */
	public static final class WorldMap extends Overlay
	{
		private final Client client;
		private final RouteService routes;
		private final WorldMapOverlay mapOverlay;
		private final Supplier<Color> color;

		public WorldMap(Client client, RouteService routes, WorldMapOverlay mapOverlay, Supplier<Color> color)
		{
			this.client = client;
			this.routes = routes;
			this.mapOverlay = mapOverlay;
			this.color = color;
			setPosition(OverlayPosition.DYNAMIC);
			setLayer(OverlayLayer.MANUAL);
			drawAfterInterface(InterfaceID.WORLDMAP);
		}

		@Override
		public Dimension render(Graphics2D g)
		{
			Route route = routes.getRoute();
			Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
			if (route == null || map == null || map.isHidden())
			{
				return null;
			}
			Rectangle bounds = map.getBounds();
			Shape oldClip = g.getClip();
			g.setClip(bounds);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			Color c = color.get();
			int[] tiles = route.getTiles();
			Point prev = null;
			for (int i = Math.max(0, routes.getProgress()); i < tiles.length; i++)
			{
				int tile = tiles[i];
				Transport hop = route.hopInto(i);
				boolean ends = hop != null || i == tiles.length - 1 || route.hopInto(i + 1) != null;
				// every third tile is plenty for a line on the map, as long as the corners at each hop are kept
				if (!ends && i % 3 != 0)
				{
					continue;
				}
				Point at = mapOverlay.mapWorldPointToGraphicsPoint(new WorldPoint(Pos.x(tile), Pos.y(tile), Pos.plane(tile)));
				if (at != null && prev != null)
				{
					g.setColor(hop == null ? c : Color.WHITE);
					g.setStroke(hop == null ? MAP_LINE : MAP_HOP);
					g.drawLine(prev.getX(), prev.getY(), at.getX(), at.getY());
				}
				prev = at;
			}
			g.setClip(oldClip);
			return null;
		}
	}

	/** A small flag for the destination's marker on the world map. */
	public static BufferedImage flag(Color color)
	{
		BufferedImage img = new BufferedImage(15, 18, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.fillRect(2, 1, 3, 17);
		g.setColor(Color.WHITE);
		g.fillRect(3, 2, 1, 15);
		Polygon cloth = new Polygon(new int[]{5, 14, 5}, new int[]{2, 6, 10}, 3);
		g.setColor(color);
		g.fill(cloth);
		g.setColor(Color.BLACK);
		g.draw(cloth);
		g.dispose();
		return img;
	}
}
