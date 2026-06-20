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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;

class DropTrackerPanel extends PluginPanel
{
	private final DropTrackerPlugin plugin;
	private final ItemManager itemManager;
	private final JComboBox<String> bossBox = new JComboBox<>();
	private final JComboBox<String> goalBox = new JComboBox<>();
	private final JTextField searchField = new JTextField();
	private final JToggleButton totalBtn = new JToggleButton("Total Loot");
	private final JButton setKcBtn = new JButton("Set KC");
	private final JLabel header = new JLabel();
	private final JLabel imageLabel = new JLabel();
	private final JPanel body = new JPanel();
	private boolean updating;

	DropTrackerPanel(DropTrackerPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
		JPanel titleRow = new JPanel();
		titleRow.setLayout(new BoxLayout(titleRow, BoxLayout.X_AXIS));
		titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
		titleRow.add(supportButton());
		titleRow.add(Box.createHorizontalStrut(4));
		titleRow.add(emailButton());
		titleRow.add(Box.createHorizontalGlue());
		top.add(titleRow);
		top.add(Box.createVerticalStrut(6));
		top.add(grey("Search:"));
		searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
		searchField.setToolTipText("Type to filter bosses, e.g. 'var' jumps to Vardorvis");
		searchField.getDocument().addDocumentListener(new DocumentListener()
		{
			public void insertUpdate(DocumentEvent e)
			{
				onSearch();
			}
			public void removeUpdate(DocumentEvent e)
			{
				onSearch();
			}
			public void changedUpdate(DocumentEvent e)
			{
				onSearch();
			}
		});
		top.add(searchField);
		top.add(Box.createVerticalStrut(6));
		top.add(grey("Boss:"));
		bossBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		bossBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		bossBox.addActionListener(e ->
		{
			if (!updating)
			{
				selectBoss();
			}
		});
		top.add(bossBox);
		top.add(Box.createVerticalStrut(6));
		top.add(grey("Goal item:"));
		goalBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		goalBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		goalBox.addActionListener(e ->
		{
			if (!updating)
			{
				selectGoal();
			}
		});
		top.add(goalBox);
		top.add(Box.createVerticalStrut(8));
		header.setFont(header.getFont().deriveFont(Font.BOLD));
		header.setForeground(Color.WHITE);
		header.setVerticalAlignment(SwingConstants.TOP);
		header.setHorizontalAlignment(SwingConstants.LEFT);
		imageLabel.setHorizontalAlignment(SwingConstants.CENTER);
		imageLabel.setVerticalAlignment(SwingConstants.TOP);
		Dimension imgZone = new Dimension(56, 92);
		imageLabel.setPreferredSize(imgZone);
		imageLabel.setMinimumSize(imgZone);
		imageLabel.setMaximumSize(imgZone);
		JPanel headerRow = new JPanel(new BorderLayout(6, 0));
		headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		headerRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));
		headerRow.add(header, BorderLayout.CENTER);
		headerRow.add(imageLabel, BorderLayout.EAST);
		top.add(headerRow);
		top.add(Box.createVerticalStrut(6));
		setKcBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		setKcBtn.setToolTipText("Set your existing kill count (or reward pulls) so the dry calc is right from the start.");
		setKcBtn.addActionListener(e -> editKc());
		top.add(setKcBtn);
		top.add(Box.createVerticalStrut(4));
		totalBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
		totalBtn.setFocusable(false);
		totalBtn.setToolTipText("Toggle between the recent-kill feed and your all-time totals for this boss.");
		totalBtn.addActionListener(e -> refresh());
		top.add(totalBtn);
		top.add(Box.createVerticalStrut(8));
		add(top, BorderLayout.NORTH);

		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		add(body, BorderLayout.CENTER);
	}

	private static JLabel grey(String s)
	{
		JLabel l = new JLabel(s);
		l.setForeground(Color.LIGHT_GRAY);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel line(String html, Color c)
	{
		JLabel l = new JLabel(html);
		l.setForeground(c);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private JButton supportButton()
	{
		BufferedImage img = plugin.supportImage();
		JButton b;
		if (img != null)
		{
			b = new JButton(new ImageIcon(img.getScaledInstance(22, 22, Image.SCALE_SMOOTH)));
		}
		else
		{
			b = new JButton("\u2665");
			b.setForeground(new Color(0xC8, 0x5A, 0xE0));
		}
		SwingUtil.removeButtonDecorations(b);
		b.setFocusable(false);
		b.setToolTipText("Support development and projects - Borealiseternal.com");
		Dimension d = new Dimension(28, 28);
		b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
		b.addActionListener(e -> LinkBrowser.browse("https://borealiseternal.com/"));
		return b;
	}

	private JButton emailButton()
	{
		JButton b = new JButton(mailIcon());
		SwingUtil.removeButtonDecorations(b);
		b.setFocusable(false);
		b.setToolTipText("Contact the developer - Borealiseternal.com");
		Dimension d = new Dimension(28, 28);
		b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
		b.addActionListener(e -> LinkBrowser.browse("https://borealiseternal.com/contact.html"));
		return b;
	}

	private static ImageIcon mailIcon()
	{
		int s = 22;
		BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0xC8, 0xDD, 0xE9));
		g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
		int x0 = 2, y0 = 6, w = s - 4, h = 11;
		g.drawRoundRect(x0, y0, w, h, 3, 3);
		g.drawLine(x0, y0, x0 + w / 2, y0 + h / 2);
		g.drawLine(x0 + w, y0, x0 + w / 2, y0 + h / 2);
		g.dispose();
		return new ImageIcon(img);
	}

	void rebuild()
	{
		updating = true;
		bossBox.removeAllItems();
		for (BossRegistry.Boss b : BossRegistry.all())
		{
			bossBox.addItem(b.display);
		}
		updating = false;
		if (bossBox.getItemCount() > 0)
		{
			selectBoss();
		}
	}

	private BossRegistry.Boss current()
	{
		Object s = bossBox.getSelectedItem();
		return s == null ? null : BossRegistry.byLootName((String) s);
	}

	private void selectBoss()
	{
		BossRegistry.Boss b = current();
		if (b == null)
		{
			return;
		}
		updating = true;
		goalBox.removeAllItems();
		goalBox.addItem("(none)");
		for (BossRegistry.Drop d : b.notableDrops())
		{
			goalBox.addItem(d.name);
		}
		String goal = plugin.getGoal(b);
		goalBox.setSelectedItem(goal == null ? "(none)" : goal);
		updating = false;
		plugin.warmPrices(b);
		refresh();
	}

	private void selectGoal()
	{
		BossRegistry.Boss b = current();
		if (b == null)
		{
			return;
		}
		Object g = goalBox.getSelectedItem();
		plugin.setGoal(b, (g == null || "(none)".equals(g)) ? null : (String) g);
		refresh();
	}

	private static boolean isReward(BossRegistry.Boss b)
	{
		String d = b.display.toLowerCase();
		return d.equals("wintertodt") || d.equals("tempoross") || d.equals("guardians of the rift");
	}
	private static String unit(BossRegistry.Boss b)
	{
		return isReward(b) ? "pulls" : "kills";
	}
	private static String unitCap(BossRegistry.Boss b)
	{
		return isReward(b) ? "Pulls" : "KC";
	}

	private void editKc()
	{
		BossRegistry.Boss b = current();
		if (b == null)
		{
			return;
		}
		String in = JOptionPane.showInputDialog(this, "Set current " + (isReward(b) ? "pulls" : "KC") + " for " + b.display + ":", plugin.getKc(b));
		if (in == null)
		{
			return;
		}
		try
		{
			plugin.setKc(b, Integer.parseInt(in.trim()));
		}
		catch (NumberFormatException ignored)
		{
			return;
		}
		refresh();
	}

	private void onSearch()
	{
		if (updating)
		{
			return;
		}
		String s = searchField.getText().trim().toLowerCase();
		java.util.List<String> starts = new java.util.ArrayList<>();
		java.util.List<String> contains = new java.util.ArrayList<>();
		for (BossRegistry.Boss b : BossRegistry.all())
		{
			String dl = b.display.toLowerCase();
			if (s.isEmpty() || dl.startsWith(s))
			{
				starts.add(b.display);
			}
			else if (dl.contains(s))
			{
				contains.add(b.display);
			}
		}
		starts.addAll(contains);
		updating = true;
		bossBox.removeAllItems();
		for (String d : starts)
		{
			bossBox.addItem(d);
		}
		boolean any = bossBox.getItemCount() > 0;
		if (any)
		{
			bossBox.setSelectedIndex(0);
		}
		updating = false;
		if (any)
		{
			selectBoss();
		}
	}

	void onKill(BossRegistry.Boss b)
	{
		Object sel = bossBox.getSelectedItem();
		if (sel != null && b.display.equals(sel))
		{
			refresh();
			return;
		}
		updating = true;
		searchField.setText("");
		bossBox.removeAllItems();
		for (BossRegistry.Boss bb : BossRegistry.all())
		{
			bossBox.addItem(bb.display);
		}
		bossBox.setSelectedItem(b.display);
		updating = false;
		selectBoss();
	}

	void rerender()
	{
		refresh();
	}

	private void refresh()
	{
		BossRegistry.Boss b = current();
		if (b == null)
		{
			return;
		}
		int kc = plugin.getKc(b);
		String goal = plugin.getGoal(b);

		setKcBtn.setText(isReward(b) ? "Set pulls" : "Set KC");
		StringBuilder h = new StringBuilder("<html><body style='width: 124px'>").append(unitCap(b)).append(": ").append(kc);
		Double avg = plugin.avgPurpleOneInX(b);
		if (avg != null)
		{
			h.append("<br><font color='#87cefa'>Your avg purple: ~1/").append(fmt(avg))
				.append(" over ").append(plugin.getRaidCount(b)).append(" raids</font>");
		}
		if (goal != null)
		{
			BossRegistry.Drop gd = null;
			for (BossRegistry.Drop d : b.drops)
			{
				if (d.name.equals(goal))
				{
					gd = d;
				}
			}
			if (gd != null)
			{
				int since = kc - plugin.getLastDropKc(b, goal);
				Double smart = plugin.smartChanceHave(b, goal);
				double p = (smart != null) ? smart : 1.0 - Math.pow(1.0 - 1.0 / gd.oneInX, Math.max(0, since));
				h.append("<br><br>Goal: ").append(goal)
					.append("<br>Rate: 1/").append(fmt(gd.oneInX))
					.append("<br>Dry: ").append(since).append(" ").append(unit(b))
					.append("<br><font color='").append(fadeWhiteToGreen(p)).append("'>")
					.append(String.format("%.1f%% would have it by now%s", p * 100, smart != null ? " (points-weighted)" : ""))
					.append("</font>");
			}
		}
		h.append("</body></html>");
		header.setText(h.toString());

		imageLabel.setIcon(null);
		if (goal != null)
		{
			int id = plugin.iconId(goal);
			if (id > 0)
			{
				setItemIcon(id);
			}
		}
		else
		{
			BufferedImage boss = plugin.bossImage(b);
			if (boss != null)
			{
				imageLabel.setIcon(new ImageIcon(fitImage(boss, 52, 88)));
			}
			else
			{
				for (BossRegistry.Drop d : b.notableDrops())
				{
					if (!d.pet && plugin.iconId(d.name) > 0)
					{
						setItemIcon(plugin.iconId(d.name));
						break;
					}
				}
			}
		}

		body.removeAll();

		if (b.display.equals("Revenants"))
		{
			renderRevenantOdds();
		}
		else
		{
			body.add(line("<html><b>Notable drops</b></html>", Color.LIGHT_GRAY));
		}
		for (BossRegistry.Drop d : b.notableDrops())
		{
			List<Integer> got = plugin.getUniqueKcs(b, d.name);
			StringBuilder s = new StringBuilder("<html>").append(d.pet ? "★ " : "")
				.append(d.name).append("  —  1/").append(fmt(d.oneInX));
			if (!got.isEmpty())
			{
				s.append("<br>&nbsp;&nbsp;<font color='#9acd32'>got at ").append(isReward(b) ? "pull " : "KC ").append(join(got)).append("</font>");
			}
			s.append("</html>");
			body.add(line(s.toString(), d.pet ? new Color(0xFF, 0xD7, 0x00) : Color.WHITE));
		}

		body.add(Box.createVerticalStrut(10));
		if (totalBtn.isSelected())
		{
			renderTotals(b);
		}
		else
		{
			renderRecent(b);
		}

		body.revalidate();
		body.repaint();
	}

	private void renderRevenantOdds()
	{
		body.add(line("<html><b>Unique / kill (any of 11)</b></html>", Color.LIGHT_GRAY));
		revRow("Revenant dragon", "1/410", "1/314");
		revRow("Revenant knight", "1/410", "1/314");
		revRow("Revenant dark beast", "1/451", "1/345");
		revRow("Revenant ork", "1/451", "1/345");
		body.add(line("<html><font color='#888888'>Skulled is ~5x the amulet/weapon rate.</font></html>", Color.GRAY));
	}

	private void revRow(String name, String unskulled, String skulled)
	{
		String html = "<html><body style='width: 128px'>" + name
			+ "<br><font color='#a9c9ff'>" + unskulled + " unskulled &nbsp; " + skulled + " skulled</font></body></html>";
		body.add(line(html, Color.WHITE));
	}

	private void renderRecent(BossRegistry.Boss b)
	{
		body.add(line("<html><b>Recent loot</b></html>", Color.LIGHT_GRAY));
		List<LootEntry> hist = plugin.getHistory(b);
		int shown = 0;
		for (int i = hist.size() - 1; i >= 0 && shown < 30; i--)
		{
			LootEntry e = hist.get(i);
			JPanel row = new JPanel();
			row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			double worst = -1;
			int icons = 0;
			if (e.items != null)
			{
				for (LootEntry.Item it : e.items)
				{
					if (it.id > 0 && icons < 12)
					{
						JLabel ic = new JLabel();
						ic.setToolTipText((it.qty > 1 ? it.qty + "× " : "") + it.name);
						AsyncBufferedImage img = itemManager.getImage(it.id, it.qty, it.qty > 1);
						img.addTo(ic);
						row.add(ic);
						icons++;
					}
					Double r = b.rateFor(it.name);
					if (r != null && r > worst)
					{
						worst = r;
					}
				}
			}
			if (icons == 0)
			{
				continue;
			}
			row.add(Box.createHorizontalGlue());
			if (plugin.showOdds() && worst > 0)
			{
				JLabel odds = new JLabel("1/" + fmt(worst) + " (" + String.format("%.1f", 100.0 / worst) + "%)");
				odds.setForeground(new Color(0xC8, 0xC8, 0x00));
				row.add(odds);
			}
			JPanel kill = new JPanel();
			kill.setLayout(new BoxLayout(kill, BoxLayout.Y_AXIS));
			kill.setAlignmentX(Component.LEFT_ALIGNMENT);
			kill.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
			kill.setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));
			JLabel kclbl = new JLabel((isReward(b) ? "Pull " : "KC ") + e.kc);
			kclbl.setForeground(new Color(0xAA, 0xAA, 0xAA));
			kclbl.setAlignmentX(Component.LEFT_ALIGNMENT);
			kill.add(kclbl);
			kill.add(row);
			body.add(kill);
			shown++;
		}
		if (shown == 0)
		{
			body.add(line("<html><font color='#888'>No kills logged yet on this version — get a kill and it'll appear here with item icons.</font></html>", Color.GRAY));
		}
	}

	private void renderTotals(BossRegistry.Boss b)
	{
		body.add(line("<html><b>Total loot (all-time)</b></html>", Color.LIGHT_GRAY));
		List<ItemTotal> totals = plugin.getTotals(b);
		if (totals.isEmpty())
		{
			body.add(line("<html><font color='#888'>Nothing tracked yet — totals build up as you kill.</font></html>", Color.GRAY));
			return;
		}
		long grand = 0;
		for (ItemTotal t : totals)
		{
			grand += plugin.unitPrice(t.id) * t.total;
		}
		if (grand > 0)
		{
			body.add(line("<html>Total value: <font color='" + valHex(grand) + "'>" + fmtGp(grand) + "</font></html>", Color.WHITE));
		}
		for (ItemTotal t : totals)
		{
			JPanel row = new JPanel();
			row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
			row.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
			if (t.id > 0)
			{
				JLabel ic = new JLabel();
				int q = (int) Math.min(t.total, Integer.MAX_VALUE);
				AsyncBufferedImage img = itemManager.getImage(t.id, Math.max(1, q), true);
				img.addTo(ic);
				ic.setToolTipText(t.total + "× " + t.name);
				row.add(ic);
				row.add(Box.createHorizontalStrut(6));
			}
			long value = plugin.unitPrice(t.id) * t.total;
			String valHtml = value > 0 ? " · <font color='" + valHex(value) + "'>" + fmtGp(value) + "</font>" : "";
			String drops = t.count + (t.count == 1 ? " drop" : " drops");
			JLabel lbl = new JLabel("<html>" + t.name + "<br><font color='#aaaaaa'>" + drops + "</font>" + valHtml + "</html>");
			lbl.setForeground(Color.WHITE);
			row.add(lbl);
			row.add(Box.createHorizontalGlue());
			body.add(row);
		}
	}

	private static String fmt(double v)
	{
		if (!Double.isInfinite(v) && v == Math.floor(v))
		{
			return String.valueOf((long) v);
		}
		return String.format("%.1f", v);
	}

	private static String join(java.util.List<Integer> l)
	{
		StringBuilder s = new StringBuilder();
		for (int i = 0; i < l.size(); i++)
		{
			if (i > 0)
			{
				s.append(", ");
			}
			s.append(l.get(i));
		}
		return s.toString();
	}

	private static String fmtGp(long v)
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

	private static String valHex(long v)
	{
		if (v >= 10000000000L)
		{
			return "#c068ff";
		}
		// 10B+  purple
		if (v >= 1000000000L)
		{
			return "#4da6ff";
		}
		// 1B+   blue
		if (v >= 1000000L)
		{
			return "#3fd14d";
		}
		// 1M+   green
		return "#ffffff";                            // <1M   white
	}

	private void setItemIcon(int id)
	{
		AsyncBufferedImage img = itemManager.getImage(id, 1, false);
		img.onLoaded(() ->
		{
			if (img.getWidth() <= 0 || img.getHeight() <= 0)
			{
				return;
			}
			imageLabel.setIcon(new ImageIcon(fitImage(img, 52, 52)));
		});
	}
	private static Image fitImage(BufferedImage img, int maxW, int maxH)
	{
		int w = img.getWidth(), hh = img.getHeight();
		double s = Math.min(maxW / (double) w, maxH / (double) hh);
		if (s == 1.0)
		{
			return img;
		}
		return img.getScaledInstance(Math.max(1, (int) Math.round(w * s)), Math.max(1, (int) Math.round(hh * s)), Image.SCALE_SMOOTH);
	}

	// white (0%) -> strong green (100%) for the dry-chance line
	private static String fadeWhiteToGreen(double p)
	{
		p = Math.max(0.0, Math.min(1.0, p));
		int r = (int) Math.round(255 + p * (60 - 255));
		int g = (int) Math.round(255 + p * (220 - 255));
		int bl = (int) Math.round(255 + p * (70 - 255));
		return String.format("#%02x%02x%02x", r, g, bl);
	}
}
