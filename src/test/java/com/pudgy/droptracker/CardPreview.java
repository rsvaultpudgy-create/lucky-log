package com.pudgy.droptracker;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;

/**
 * Dev-only preview harness: renders both share cards with FAKE data and generated
 * placeholder item sprites so the layout can be checked without a running client.
 * Not part of the plugin build path (test sources only).
 */
public final class CardPreview
{
	public static void main(String[] args) throws Exception
	{
		FakePlugin plugin = new FakePlugin();
		PreviewRenderer r = new PreviewRenderer(plugin);

		BossRegistry.Boss zulrah = BossRegistry.byLootName("Zulrah");
		ImageIO.write(r.renderBossCard(zulrah), "png", new File(args.length > 0 ? args[0] : "boss_card.png"));
		ImageIO.write(r.renderOverviewCard(), "png", new File(args.length > 1 ? args[1] : "overview_card.png"));
		System.out.println("done");
	}

	static final class PreviewRenderer extends ShareCardRenderer
	{
		PreviewRenderer(DropTrackerPlugin p)
		{
			super(p, null);
		}

		@Override
		Map<Integer, BufferedImage> loadSprites(Set<Integer> ids)
		{
			Map<Integer, BufferedImage> m = new HashMap<>();
			for (int id : ids)
			{
				m.put(id, fakeSprite(id));
			}
			return m;
		}

		@Override
		Map<Integer, BufferedImage> loadSpritesWithQty(Map<Integer, Integer> idQty)
		{
			Map<Integer, BufferedImage> m = new HashMap<>();
			for (Map.Entry<Integer, Integer> e : idQty.entrySet())
			{
				BufferedImage img = fakeSprite(e.getKey());
				if (e.getValue() > 1)
				{
					Graphics2D g = img.createGraphics();
					g.setColor(new Color(0xFF, 0xFF, 0x00));
					g.setFont(g.getFont().deriveFont(10f));
					g.drawString(qtyText(e.getValue()), 1, 9);
					g.dispose();
				}
				m.put(e.getKey(), img);
			}
			return m;
		}

		private static String qtyText(long q)
		{
			if (q >= 10000000)
			{
				return (q / 1000000) + "M";
			}
			if (q >= 100000)
			{
				return (q / 1000) + "K";
			}
			return String.valueOf(q);
		}
	}

	private static BufferedImage fakeSprite(int seed)
	{
		int s = 36;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int hue = (seed * 47) % 360;
		Color c = Color.getHSBColor(hue / 360f, 0.55f, 0.75f);
		Color dark = c.darker().darker();
		int[] xs = {s / 2, s - 5, s / 2, 5};
		int[] ys = {4, s / 2, s - 4, s / 2};
		g.setColor(c);
		g.fillPolygon(xs, ys, 4);
		g.setColor(dark);
		g.drawPolygon(xs, ys, 4);
		g.setColor(new Color(255, 255, 255, 90));
		g.drawLine(s / 2, 8, s - 9, s / 2);
		g.dispose();
		return img;
	}

	/** Overrides every data accessor the renderer touches; never hits injected fields. */
	static final class FakePlugin extends DropTrackerPlugin
	{
		private final Map<String, Integer> kcs = new LinkedHashMap<>();
		private final Map<String, List<Integer>> gotten = new LinkedHashMap<>();
		private final Map<String, Long> prices = new HashMap<>();
		private final Map<String, List<ItemTotal>> totals = new LinkedHashMap<>();
		private int nextIcon = 100;
		private final Map<String, Integer> icons = new HashMap<>();
		private final Map<Integer, Long> priceById = new HashMap<>();

		FakePlugin()
		{
			kcs.put("zulrah", 1432);
			kcs.put("vorkath", 2210);
			kcs.put("alchemical hydra", 640);
			kcs.put("chambers of xeric", 212);
			kcs.put("cerberus", 388);
			kcs.put("grotesque guardians", 151);

			got("zulrah", "Tanzanite fang", 233, 902);
			got("zulrah", "Serpentine visage", 517);
			got("zulrah", "Uncut onyx", 640, 1105);
			got("zulrah", "Magma mutagen", 1288);
			got("vorkath", "Draconic visage", 1980);
			got("vorkath", "Vorki", 2011);
			got("vorkath", "Skeletal visage", 460);
			got("alchemical hydra", "Hydra's claw", 601);
			got("chambers of xeric", "Twisted bow", 208);
			got("cerberus", "Primordial crystal", 122, 301);

			tot("zulrah", "Tanzanite fang", 2, 2500000, 2);
			tot("zulrah", "Magic fang", 0, 1800000, 0);
			tot("zulrah", "Serpentine visage", 1, 350000, 1);
			tot("zulrah", "Zulrah's scales", 320000, 210, 1400);
			tot("zulrah", "Battlestaff", 3400, 8200, 680);
			tot("zulrah", "Dragon halberd", 190, 145000, 190);
			tot("zulrah", "Grimy toadflax", 4100, 2100, 590);
			tot("zulrah", "Mahogany logs", 15200, 410, 490);
			tot("zulrah", "Runite ore", 1900, 10800, 470);
			tot("zulrah", "Pure essence", 42000, 2, 280);
			tot("zulrah", "Snakeskin", 21000, 350, 620);
			tot("zulrah", "Coal", 30000, 160, 410);
			tot("zulrah", "Magic seed", 41, 105000, 39);
			tot("zulrah", "Palm tree seed", 62, 31000, 55);
			tot("zulrah", "Flax", 9000, 3, 210);
			tot("zulrah", "Antidote++(4)", 800, 1100, 260);
			tot("zulrah", "Swamp tar", 26000, 3, 330);
			tot("zulrah", "Uncut onyx", 2, 2700000, 2);
			tot("zulrah", "Magma mutagen", 1, 0, 1);
			tot("zulrah", "Grimy torstol", 880, 26000, 300);
			tot("zulrah", "Yew logs", 7100, 260, 350);
			tot("zulrah", "Dragonstone", 130, 12000, 120);
			tot("zulrah", "Manta ray", 5200, 1400, 460);
			tot("zulrah", "Adamantite bar", 2600, 1900, 330);
			tot("zulrah", "Law rune", 15600, 140, 380);
			tot("zulrah", "Death rune", 17400, 190, 390);
			tot("zulrah", "Chaos rune", 21500, 70, 340);
			tot("zulrah", "Grimy dwarf weed", 610, 19000, 250);
			tot("zulrah", "Jar of swamp", 1, 60000, 1);
			tot("zulrah", "Tanzanite mutagen", 0, 0, 0);

			tot("vorkath", "Superior dragon bones", 4400, 21000, 2200);
			tot("vorkath", "Draconic visage", 1, 4300000, 1);
			tot("vorkath", "Blue dragonhide", 6600, 1600, 2200);
			tot("alchemical hydra", "Hydra's claw", 1, 60000000, 1);
			tot("alchemical hydra", "Hydra leather", 2, 8000000, 2);
			tot("chambers of xeric", "Twisted bow", 1, 1550000000, 1);
			tot("chambers of xeric", "Death rune", 90000, 190, 200);
			tot("cerberus", "Primordial crystal", 2, 26000000, 2);
			tot("cerberus", "Infernal ashes", 1100, 7900, 380);
			tot("grotesque guardians", "Granite gloves", 3, 90000, 3);
		}

		private void got(String boss, String item, int... atKcs)
		{
			List<Integer> l = new ArrayList<>();
			for (int k : atKcs)
			{
				l.add(k);
			}
			gotten.put(boss + "|" + item.toLowerCase(), l);
		}

		private void tot(String boss, String item, long qty, long unit, int count)
		{
			ItemTotal t = new ItemTotal(iconOf(item), item);
			t.total = Math.max(1, qty);
			t.count = Math.max(1, count);
			priceById.put(t.id, unit);
			totals.computeIfAbsent(boss, k -> new ArrayList<>()).add(t);
		}

		private int iconOf(String item)
		{
			return icons.computeIfAbsent(item.toLowerCase(), k -> nextIcon++);
		}

		@Override
		int getKc(BossRegistry.Boss b)
		{
			Integer v = kcs.get(b.display.toLowerCase());
			return v == null ? 0 : v;
		}

		@Override
		List<ItemTotal> getTotals(BossRegistry.Boss b)
		{
			List<ItemTotal> l = totals.getOrDefault(b.display.toLowerCase(), new ArrayList<>());
			l.sort((a, c) -> Long.compare(unitPrice(c.id) * c.total, unitPrice(a.id) * a.total));
			return l;
		}

		@Override
		List<Integer> getUniqueKcs(BossRegistry.Boss b, String item)
		{
			return gotten.getOrDefault(b.display.toLowerCase() + "|" + item.toLowerCase(), new ArrayList<>());
		}

		@Override
		int getUnknownCount(BossRegistry.Boss b, String item)
		{
			return 0;
		}

		@Override
		long unitPrice(int id)
		{
			Long v = priceById.get(id);
			return v == null ? 0 : v;
		}

		@Override
		int iconId(String name)
		{
			return iconOf(name);
		}

		@Override
		Double avgPurpleOneInX(BossRegistry.Boss b)
		{
			return null;
		}

		@Override
		String cardPlayerName()
		{
			return "Pudgy";
		}
	}
}
