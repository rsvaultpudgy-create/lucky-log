/*
 * Copyright (c) 2026, Pudgy
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.pudgy.droptracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;

/**
 * Paints shareable 1200x675 PNG cards (Discord/Twitter embed ratio) from data the
 * plugin already tracks. Pure Java2D over bundled resources and cached item sprites —
 * no network access. Renders must run OFF the EDT (sprite loading blocks briefly).
 */
class ShareCardRenderer
{
	static final int W = 1200;
	static final int H = 675;

	// palette
	private static final Color BG_TOP = new Color(0x24, 0x1c, 0x12);
	private static final Color BG_BOTTOM = new Color(0x12, 0x0d, 0x08);
	private static final Color PANEL = new Color(255, 255, 255, 14);
	private static final Color PANEL_LINE = new Color(0xd4, 0xaf, 0x5e, 60);
	private static final Color BORDER_OUT = new Color(0x6b, 0x56, 0x26);
	private static final Color BORDER_IN = new Color(0xd4, 0xaf, 0x5e);
	private static final Color GOLD = new Color(0xd4, 0xaf, 0x5e);
	private static final Color CREAM = new Color(0xe8, 0xdc, 0xc0);
	private static final Color GREY = new Color(0x9a, 0x91, 0x7f);
	private static final Color GREEN = new Color(0x9a, 0xcd, 0x32);
	private static final Color PET_GOLD = new Color(0xff, 0xd7, 0x00);
	private static final Color DRY_RED = new Color(0xe0, 0x7a, 0x5f);

	private final DropTrackerPlugin plugin;
	private final ItemManager itemManager;

	ShareCardRenderer(DropTrackerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
	}

	// =========================================================================
	// Boss card
	// =========================================================================

	BufferedImage renderBossCard(BossRegistry.Boss b)
	{
		int kc = plugin.getKc(b);
		List<ItemTotal> totals = plugin.getTotals(b);
		List<BossRegistry.Drop> notables = b.notableDrops();

		// preload every sprite the card needs before painting
		Set<Integer> ids = new LinkedHashSet<>();
		for (BossRegistry.Drop d : notables)
		{
			int id = plugin.iconId(d.name);
			if (id > 0)
			{
				ids.add(id);
			}
		}
		Map<Integer, Integer> lootQty = new LinkedHashMap<>();
		for (ItemTotal t : totals)
		{
			if (t.id > 0)
			{
				lootQty.put(t.id, (int) Math.min(t.total, Integer.MAX_VALUE));
			}
		}
		Map<Integer, BufferedImage> sprites = loadSprites(ids);
		Map<Integer, BufferedImage> lootSprites = loadSpritesWithQty(lootQty);

		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		paintBase(g);
		boolean reward = isReward(b);
		paintHeader(g, reward ? "REWARD CARD" : "BOSS CARD");

		// boss render, top right
		BufferedImage bossImg = plugin.bossImage(b);
		if (bossImg != null)
		{
			drawFitted(g, bossImg, 950, 104, 210, 236);
		}

		// boss name
		g.setFont(bold(42f));
		g.setColor(GOLD);
		drawTruncated(g, b.display, 40, 148, 890);

		// stat blocks
		long grand = 0;
		int drops = 0;
		for (ItemTotal t : totals)
		{
			grand += plugin.unitPrice(t.id) * t.total;
			drops += t.count;
		}
		int got = 0;
		for (BossRegistry.Drop d : notables)
		{
			if (!plugin.getUniqueKcs(b, d.name).isEmpty() || plugin.getUnknownCount(b, d.name) > 0)
			{
				got++;
			}
		}
		int bx = 40;
		bx = statBlock(g, bx, 176, reward ? "PULLS" : "KILL COUNT", String.format("%,d", kc), CREAM);
		bx = statBlock(g, bx, 176, "TOTAL DROPS", String.format("%,d", drops), CREAM);
		bx = statBlock(g, bx, 176, "GP EARNED", fmtGp(grand), valColor(grand));
		Double avg = plugin.avgPurpleOneInX(b);
		if (avg != null)
		{
			statBlock(g, bx, 176, "YOUR AVG PURPLE", "~1/" + fmtRate(avg), new Color(0x87, 0xce, 0xfa));
		}
		else
		{
			statBlock(g, bx, 176, "UNIQUES", got + " / " + notables.size(), got > 0 ? GREEN : CREAM);
		}

		// notable drops grid
		g.setFont(bold(17f));
		g.setColor(GOLD);
		g.drawString("NOTABLE DROPS", 40, 286);
		int cols = 3;
		int cellW = 296;
		int cellH = 50;
		int gridY = 300;
		int maxRows = 4;
		int maxCells = cols * maxRows;
		int shown = Math.min(notables.size(), notables.size() > maxCells ? maxCells - 1 : maxCells);
		for (int i = 0; i < shown; i++)
		{
			BossRegistry.Drop d = notables.get(i);
			int cx = 40 + (i % cols) * cellW;
			int cy = gridY + (i / cols) * cellH;
			paintUniqueCell(g, b, d, kc, sprites, cx, cy, cellW - 16);
		}
		if (notables.size() > shown)
		{
			int cx = 40 + (shown % cols) * cellW;
			int cy = gridY + (shown / cols) * cellH;
			g.setFont(plain(15f));
			g.setColor(GREY);
			g.drawString("+" + (notables.size() - shown) + " more uniques", cx + 6, cy + 30);
		}

		// all-loot strip — flows up under the uniques grid so short cards have no dead band
		int gridRows = (int) Math.ceil((shown + (notables.size() > shown ? 1 : 0)) / (double) cols);
		int titleY = Math.max(392, gridY + gridRows * cellH + 30);
		g.setFont(bold(17f));
		g.setColor(GOLD);
		g.drawString("ALL LOOT RECEIVED", 40, titleY);
		g.setFont(plain(13f));
		g.setColor(GREY);
		g.drawString("sorted by value", 218, titleY);
		int itemsY = titleY + 12;
		int cell = 44;
		int perRow = (W - 80) / cell; // 25
		int lootRows = Math.max(1, Math.min(2, (620 - itemsY) / cell));
		int maxItems = perRow * lootRows;
		int lshown = Math.min(totals.size(), totals.size() > maxItems ? maxItems - 1 : maxItems);
		for (int i = 0; i < lshown; i++)
		{
			ItemTotal t = totals.get(i);
			int cx = 40 + (i % perRow) * cell;
			int cy = itemsY + (i / perRow) * cell;
			BufferedImage sp = lootSprites.get(t.id);
			if (sp != null)
			{
				drawFitted(g, sp, cx + 2, cy + 2, cell - 6, cell - 6);
			}
			else
			{
				g.setColor(PANEL);
				g.fillRoundRect(cx + 4, cy + 4, cell - 10, cell - 10, 6, 6);
			}
		}
		if (totals.size() > lshown)
		{
			int cx = 40 + (lshown % perRow) * cell;
			int cy = itemsY + (lshown / perRow) * cell;
			g.setFont(bold(14f));
			g.setColor(GREY);
			g.drawString("+" + (totals.size() - lshown), cx + 6, cy + 26);
		}
		if (totals.isEmpty())
		{
			g.setFont(plain(15f));
			g.setColor(GREY);
			g.drawString("No loot tracked yet — kills logged with Lucky Log will fill this in.", 40, itemsY + 24);
		}

		paintFooter(g);
		g.dispose();
		return img;
	}

	private void paintUniqueCell(Graphics2D g, BossRegistry.Boss b, BossRegistry.Drop d, int kc,
		Map<Integer, BufferedImage> sprites, int x, int y, int w)
	{
		BufferedImage sp = sprites.get(plugin.iconId(d.name));
		if (sp != null)
		{
			drawFitted(g, sp, x, y + 6, 34, 34);
		}
		int tx = x + 42;
		g.setFont(bold(16f));
		g.setColor(d.pet ? PET_GOLD : CREAM);
		drawTruncated(g, d.name, tx, y + 18, w - 42);

		List<Integer> gotKcs = plugin.getUniqueKcs(b, d.name);
		int unknown = plugin.getUnknownCount(b, d.name);
		String status;
		Color sc;
		if (!gotKcs.isEmpty() || unknown > 0)
		{
			int n = gotKcs.size() + unknown;
			StringBuilder s = new StringBuilder(n > 1 ? "×" + n + " · " : "");
			if (!gotKcs.isEmpty())
			{
				s.append(isReward(b) ? "pull " : "KC ");
				for (int i = 0; i < Math.min(3, gotKcs.size()); i++)
				{
					if (i > 0)
					{
						s.append(", ");
					}
					s.append(String.format("%,d", gotKcs.get(gotKcs.size() - Math.min(3, gotKcs.size()) + i)));
				}
			}
			else
			{
				s.append("pre-tracking");
			}
			status = s.toString();
			sc = GREEN;
		}
		else if (kc > 0 && d.oneInX > 0)
		{
			int since = kc - plugin.getLastDropKc(b, d.name);
			double p = 1.0 - Math.pow(1.0 - 1.0 / d.oneInX, Math.max(0, since));
			status = String.format("%.1f%% would have it by now", p * 100);
			sc = fadeWhiteToGreen(p);
		}
		else
		{
			status = "—";
			sc = GREY;
		}
		g.setFont(plain(13f));
		g.setColor(GREY);
		String rate = "1/" + fmtRate(d.oneInX) + "  ";
		g.drawString(rate, tx, y + 36);
		g.setColor(sc);
		drawTruncated(g, status, tx + g.getFontMetrics().stringWidth(rate), y + 36, w - 42 - g.getFontMetrics().stringWidth(rate));
	}

	// =========================================================================
	// Overview card
	// =========================================================================

	private static final class Highlight
	{
		BossRegistry.Boss boss;
		String item;
		double ratio;
		int detail; // dry-streak length or the KC gap it landed in
	}

	BufferedImage renderOverviewCard()
	{
		List<BossRegistry.Boss> all = BossRegistry.all();

		long grandTotal = 0;
		long totalKills = 0;
		int uniquesObtained = 0;
		Map<BossRegistry.Boss, Long> gpByBoss = new HashMap<>();
		BossRegistry.Boss highestKcBoss = null;
		int highestKc = 0;
		Highlight curDry = null;
		Highlight allDry = null;
		Highlight lucky = null;

		for (BossRegistry.Boss b : all)
		{
			int kc = plugin.getKc(b);
			totalKills += kc;
			if (kc > highestKc)
			{
				highestKc = kc;
				highestKcBoss = b;
			}
			long gp = 0;
			for (ItemTotal t : plugin.getTotals(b))
			{
				gp += plugin.unitPrice(t.id) * t.total;
			}
			if (gp > 0)
			{
				gpByBoss.put(b, gp);
				grandTotal += gp;
			}
			for (BossRegistry.Drop d : b.notableDrops())
			{
				List<Integer> gotKcs = plugin.getUniqueKcs(b, d.name);
				uniquesObtained += gotKcs.size() + plugin.getUnknownCount(b, d.name);
				if (d.oneInX <= 0)
				{
					continue;
				}
				// current dry streak on this unique
				if (kc > 0)
				{
					int since = kc - plugin.getLastDropKc(b, d.name);
					double ratio = since / d.oneInX;
					if (since > 0 && (curDry == null || ratio > curDry.ratio))
					{
						curDry = highlight(b, d.name, ratio, since);
					}
				}
				// historical gaps between hits
				int prev = 0;
				for (int k : gotKcs)
				{
					int gap = k - prev;
					prev = k;
					if (gap < 1)
					{
						continue;
					}
					double ratio = gap / d.oneInX;
					if (allDry == null || ratio > allDry.ratio)
					{
						allDry = highlight(b, d.name, ratio, gap);
					}
					if (d.oneInX >= 20 && (lucky == null || ratio < lucky.ratio))
					{
						lucky = highlight(b, d.name, ratio, k);
					}
				}
			}
		}

		List<Map.Entry<BossRegistry.Boss, Long>> top = new ArrayList<>(gpByBoss.entrySet());
		top.sort((a, c) -> Long.compare(c.getValue(), a.getValue()));
		if (top.size() > 5)
		{
			top = top.subList(0, 5);
		}

		Set<Integer> ids = new LinkedHashSet<>();
		addHighlightIcon(ids, curDry);
		addHighlightIcon(ids, allDry);
		addHighlightIcon(ids, lucky);
		Map<Integer, BufferedImage> sprites = loadSprites(ids);

		BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = img.createGraphics();
		paintBase(g);
		paintHeader(g, "OVERVIEW");

		int bx = 40;
		bx = statBlock(g, bx, 116, "TOTAL LOOT TRACKED", fmtGp(grandTotal), valColor(grandTotal));
		bx = statBlock(g, bx, 116, "KILLS TRACKED", String.format("%,d", totalKills), CREAM);
		statBlock(g, bx, 116, "UNIQUES OBTAINED", String.format("%,d", uniquesObtained), uniquesObtained > 0 ? GREEN : CREAM);

		// three highlight panels
		int pw = 362;
		int ph = 150;
		int py = 190;
		paintHighlight(g, 40, py, pw, ph, "CURRENT DRIEST", DRY_RED, curDry, sprites,
			curDry == null ? null : String.format("%,d dry · %.1f× the rate", curDry.detail, curDry.ratio));
		paintHighlight(g, 40 + pw + 17, py, pw, ph, "ALL-TIME DRIEST", new Color(0xe8, 0xa8, 0x5c), allDry, sprites,
			allDry == null ? null : String.format("went %,d · %.1f× the rate", allDry.detail, allDry.ratio));
		paintHighlight(g, 40 + (pw + 17) * 2, py, pw, ph, "LUCKIEST HIT", GREEN, lucky, sprites,
			lucky == null ? null : String.format("hit at %.2f× the rate · KC %,d", lucky.ratio, lucky.detail));

		// top 5 most profitable bosses
		g.setFont(bold(17f));
		g.setColor(GOLD);
		g.drawString("TOP 5 MOST PROFITABLE BOSSES", 40, 386);
		long maxGp = top.isEmpty() ? 1 : top.get(0).getValue();
		int ry = 402;
		for (int i = 0; i < top.size(); i++)
		{
			BossRegistry.Boss b = top.get(i).getKey();
			long gp = top.get(i).getValue();
			int y = ry + i * 44;
			// proportional bar behind the row
			g.setColor(new Color(0xd4, 0xaf, 0x5e, 26));
			int barW = (int) Math.max(6, 620.0 * gp / maxGp);
			g.fillRoundRect(40, y, barW, 38, 8, 8);
			g.setFont(bold(18f));
			g.setColor(GOLD);
			g.drawString(String.valueOf(i + 1), 52, y + 25);
			BufferedImage bi = plugin.bossImage(b);
			if (bi != null)
			{
				drawFitted(g, bi, 74, y + 2, 34, 34);
			}
			g.setFont(bold(17f));
			g.setColor(CREAM);
			drawTruncated(g, b.display, 118, y + 25, 380);
			g.setFont(bold(17f));
			g.setColor(valColor(gp));
			drawRight(g, fmtGp(gp), 660, y + 25);
		}
		if (top.isEmpty())
		{
			g.setFont(plain(15f));
			g.setColor(GREY);
			g.drawString("No loot tracked yet.", 40, 420);
		}

		// highest KC panel, bottom right
		g.setFont(bold(17f));
		g.setColor(GOLD);
		g.drawString("HIGHEST KC", 700, 386);
		if (highestKcBoss != null)
		{
			g.setColor(PANEL);
			g.fillRoundRect(700, 398, 460, 180, 14, 14);
			g.setColor(PANEL_LINE);
			g.drawRoundRect(700, 398, 460, 180, 14, 14);
			BufferedImage bi = plugin.bossImage(highestKcBoss);
			if (bi != null)
			{
				drawFitted(g, bi, 724, 414, 130, 148);
			}
			g.setFont(bold(26f));
			g.setColor(CREAM);
			drawTruncated(g, highestKcBoss.display, 874, 462, 270);
			g.setFont(bold(40f));
			g.setColor(GOLD);
			g.drawString(String.format("%,d", highestKc), 874, 512);
			g.setFont(plain(14f));
			g.setColor(GREY);
			g.drawString(isReward(highestKcBoss) ? "reward pulls" : "kill count", 874, 534);
		}

		paintFooter(g);
		g.dispose();
		return img;
	}

	private static Highlight highlight(BossRegistry.Boss b, String item, double ratio, int detail)
	{
		Highlight h = new Highlight();
		h.boss = b;
		h.item = item;
		h.ratio = ratio;
		h.detail = detail;
		return h;
	}

	private void addHighlightIcon(Set<Integer> ids, Highlight h)
	{
		if (h != null)
		{
			int id = plugin.iconId(h.item);
			if (id > 0)
			{
				ids.add(id);
			}
		}
	}

	private void paintHighlight(Graphics2D g, int x, int y, int w, int h, String title, Color accent,
		Highlight hl, Map<Integer, BufferedImage> sprites, String detail)
	{
		g.setColor(PANEL);
		g.fillRoundRect(x, y, w, h, 14, 14);
		g.setColor(PANEL_LINE);
		g.drawRoundRect(x, y, w, h, 14, 14);
		g.setColor(accent);
		g.fillRoundRect(x, y, w, 4, 4, 4);
		g.setFont(bold(15f));
		g.setColor(accent);
		g.drawString(title, x + 18, y + 32);
		if (hl == null)
		{
			g.setFont(plain(14f));
			g.setColor(GREY);
			g.drawString("Not enough data yet", x + 18, y + 70);
			return;
		}
		BufferedImage sp = sprites.get(plugin.iconId(hl.item));
		if (sp != null)
		{
			drawFitted(g, sp, x + 18, y + 46, 42, 42);
		}
		g.setFont(bold(18f));
		g.setColor(CREAM);
		drawTruncated(g, hl.item, x + 72, y + 62, w - 90);
		g.setFont(plain(14f));
		g.setColor(GREY);
		drawTruncated(g, hl.boss.display, x + 72, y + 82, w - 90);
		g.setFont(bold(18f));
		g.setColor(accent);
		drawTruncated(g, detail, x + 18, y + 120, w - 36);
	}

	// =========================================================================
	// shared chrome
	// =========================================================================

	private void paintBase(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, H, BG_BOTTOM));
		g.fillRect(0, 0, W, H);
		// double gold frame
		g.setColor(BORDER_OUT);
		g.setStroke(new BasicStroke(6f));
		g.drawRect(3, 3, W - 7, H - 7);
		g.setColor(new Color(BORDER_IN.getRed(), BORDER_IN.getGreen(), BORDER_IN.getBlue(), 140));
		g.setStroke(new BasicStroke(1.4f));
		g.drawRect(9, 9, W - 19, H - 19);
		g.setStroke(new BasicStroke(1f));
	}

	private void paintHeader(Graphics2D g, String subtitle)
	{
		BufferedImage logo = logo();
		if (logo != null)
		{
			drawFitted(g, logo, 40, 24, 46, 46);
		}
		g.setFont(bold(30f));
		g.setColor(GOLD);
		g.drawString("LUCKY LOG", 98, 52);
		g.setFont(bold(15f));
		g.setColor(GREY);
		g.drawString(subtitle, 98, 74);

		String name = plugin.cardPlayerName();
		if (!name.isEmpty())
		{
			g.setFont(bold(26f));
			g.setColor(CREAM);
			drawRight(g, name, W - 44, 52);
		}
		g.setFont(plain(14f));
		g.setColor(GREY);
		drawRight(g, LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy")), W - 44, 74);

		g.setColor(PANEL_LINE);
		g.drawLine(40, 90, W - 40, 90);
	}

	private void paintFooter(Graphics2D g)
	{
		g.setColor(PANEL_LINE);
		g.drawLine(40, 628, W - 40, 628);
		BufferedImage logo = logo();
		if (logo != null)
		{
			drawFitted(g, logo, 40, 638, 22, 22);
		}
		g.setFont(bold(14f));
		g.setColor(GREY);
		g.drawString("Lucky Log — free on the RuneLite Plugin Hub", 70, 654);
		g.setFont(plain(14f));
		drawRight(g, "borealiseternal.com", W - 44, 654);
	}

	private int statBlock(Graphics2D g, int x, int y, String label, String value, Color valueColor)
	{
		g.setFont(bold(13f));
		g.setColor(GREY);
		g.drawString(label, x, y);
		g.setFont(bold(30f));
		g.setColor(valueColor);
		g.drawString(value, x, y + 34);
		int w = Math.max(g.getFontMetrics(bold(30f)).stringWidth(value), g.getFontMetrics(bold(13f)).stringWidth(label));
		return x + Math.max(190, w + 46);
	}

	private BufferedImage logo()
	{
		try
		{
			return ImageUtil.loadImageResource(DropTrackerPlugin.class, "/com/pudgy/droptracker/icon.png");
		}
		catch (Exception e)
		{
			return null;
		}
	}

	// =========================================================================
	// sprite + drawing helpers
	// =========================================================================

	/**
	 * Request every sprite through ItemManager and wait (briefly) for the async loads,
	 * so the card never renders half-empty. Call from a background thread only.
	 */
	Map<Integer, BufferedImage> loadSprites(Set<Integer> ids)
	{
		Map<Integer, Integer> qty = new LinkedHashMap<>();
		for (int id : ids)
		{
			qty.put(id, 1);
		}
		return loadSpritesWithQty(qty);
	}

	/**
	 * Same as {@link #loadSprites}, but renders each sprite as an in-game style stack:
	 * the stack-size art variant plus the yellow quantity overlay when qty > 1.
	 */
	Map<Integer, BufferedImage> loadSpritesWithQty(Map<Integer, Integer> idQty)
	{
		Map<Integer, BufferedImage> out = new HashMap<>();
		CountDownLatch latch = new CountDownLatch(idQty.size());
		for (Map.Entry<Integer, Integer> e : idQty.entrySet())
		{
			try
			{
				int qty = Math.max(1, e.getValue());
				AsyncBufferedImage a = itemManager.getImage(e.getKey(), qty, qty > 1);
				out.put(e.getKey(), a);
				a.onLoaded(latch::countDown);
			}
			catch (Exception ex)
			{
				latch.countDown();
			}
		}
		try
		{
			latch.await(4, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		return out;
	}

	private static void drawFitted(Graphics2D g, BufferedImage img, int x, int y, int w, int h)
	{
		int iw = img.getWidth();
		int ih = img.getHeight();
		if (iw <= 0 || ih <= 0)
		{
			return;
		}
		double s = Math.min(w / (double) iw, h / (double) ih);
		int dw = Math.max(1, (int) Math.round(iw * s));
		int dh = Math.max(1, (int) Math.round(ih * s));
		g.drawImage(img, x + (w - dw) / 2, y + (h - dh) / 2, dw, dh, null);
	}

	private static void drawRight(Graphics2D g, String s, int rightX, int y)
	{
		g.drawString(s, rightX - g.getFontMetrics().stringWidth(s), y);
	}

	private static void drawTruncated(Graphics2D g, String s, int x, int y, int maxW)
	{
		FontMetrics fm = g.getFontMetrics();
		if (fm.stringWidth(s) <= maxW)
		{
			g.drawString(s, x, y);
			return;
		}
		String ell = "…";
		int i = s.length();
		while (i > 1 && fm.stringWidth(s.substring(0, i) + ell) > maxW)
		{
			i--;
		}
		g.drawString(s.substring(0, i) + ell, x, y);
	}

	private static Font bold(float size)
	{
		return FontManager.getRunescapeBoldFont().deriveFont(size);
	}

	private static Font plain(float size)
	{
		return FontManager.getRunescapeFont().deriveFont(size);
	}

	private static boolean isReward(BossRegistry.Boss b)
	{
		String d = b.display.toLowerCase();
		return d.equals("wintertodt") || d.equals("tempoross") || d.equals("guardians of the rift");
	}

	private static String fmtRate(double v)
	{
		if (!Double.isInfinite(v) && v == Math.floor(v))
		{
			return String.format("%,d", (long) v);
		}
		return String.format("%,.1f", v);
	}

	static String fmtGp(long v)
	{
		if (v >= 10000000000L)
		{
			return String.format("%.1fB", v / 1000000000.0);
		}
		if (v >= 1000000000L)
		{
			return String.format("%.2fB", v / 1000000000.0);
		}
		if (v >= 1000000L)
		{
			return String.format("%.2fM", v / 1000000.0);
		}
		if (v >= 100000L)
		{
			return String.format("%.0fK", v / 1000.0);
		}
		if (v >= 1000L)
		{
			return String.format("%.1fK", v / 1000.0);
		}
		return String.valueOf(v);
	}

	private static Color valColor(long v)
	{
		if (v >= 10000000000L)
		{
			return new Color(0xc0, 0x68, 0xff); // 10B+ purple
		}
		if (v >= 1000000000L)
		{
			return new Color(0x4d, 0xa6, 0xff); // 1B+ blue
		}
		if (v >= 1000000L)
		{
			return new Color(0x3f, 0xd1, 0x4d); // 1M+ green
		}
		return CREAM;
	}

	// white (0%) -> strong green (100%), same ramp as the panel
	private static Color fadeWhiteToGreen(double p)
	{
		p = Math.max(0.0, Math.min(1.0, p));
		int r = (int) Math.round(255 + p * (60 - 255));
		int gr = (int) Math.round(255 + p * (220 - 255));
		int bl = (int) Math.round(255 + p * (70 - 255));
		return new Color(r, gr, bl);
	}
}
