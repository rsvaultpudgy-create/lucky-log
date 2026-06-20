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

import com.google.gson.Gson;
import com.google.inject.Provides;
import com.google.gson.reflect.TypeToken;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;
import net.runelite.client.util.ImageUtil;
import net.runelite.http.api.item.ItemPrice;

@Slf4j
@PluginDescriptor(
	name = "Lucky Log",
	description = "Per-boss drop rates with a dry-streak calculator, a per-kill loot feed, all-time totals with live GE value, and KC history for every unique.",
	tags = {"drop", "rate", "dry", "luck", "loot", "boss", "collection", "kc", "log"}
)
public class DropTrackerPlugin extends Plugin
{
	static final String GROUP = "bossdroprates";
	private static final int HISTORY_CAP = 80;

	@Inject private Client client;
	@Inject private ClientToolbar clientToolbar;
	@Inject private ConfigManager configManager;
	@Inject private Gson gson;
	@Inject private ItemManager itemManager;
	@Inject private ClientThread clientThread;
	@Inject private DropTrackerConfig config;

	private final Map<Integer, Long> valueCache = new ConcurrentHashMap<>();
	private final Map<String, Integer> iconIdCache = new ConcurrentHashMap<>();
	private final Map<String, BufferedImage> bossImgCache = new ConcurrentHashMap<>();
	private volatile boolean itemsIndexed = false;
	private static final Map<String, Integer> PET_IDS = new LinkedHashMap<>();
	static
	{
		PET_IDS.put("butch", 28248);
		PET_IDS.put("pet general graardor", 12650);
		PET_IDS.put("pet zilyana", 12651);
		PET_IDS.put("pet kree'arra", 12649);
		PET_IDS.put("pet k'ril tsutsaroth", 12652);
		PET_IDS.put("hellpuppy", 13247);
		PET_IDS.put("pet kraken", 12655);
		PET_IDS.put("abyssal orphan", 13262);
		PET_IDS.put("ikkle hydra", 22746);
		PET_IDS.put("pet snakeling", 12921);
		PET_IDS.put("lil'viathan", 28252);
		PET_IDS.put("pet dark core", 12816);
		PET_IDS.put("callisto cub", 13178);
		PET_IDS.put("venenatis spiderling", 13177);
		PET_IDS.put("vet'ion jr.", 13179);
		PET_IDS.put("baby mole", 12646);
		PET_IDS.put("kalphite princess", 12647);
		PET_IDS.put("prince black dragon", 12653);
		PET_IDS.put("sraracha", 23495);
		PET_IDS.put("scorpia's offspring", 13181);
		PET_IDS.put("scurry", 28801);
		PET_IDS.put("skotos", 21273);
		PET_IDS.put("pet smoke devil", 12648);
		PET_IDS.put("noon", 21748);
		PET_IDS.put("tangleroot", 20661);
		PET_IDS.put("pet chaos elemental", 11995);
		PET_IDS.put("nid", 29836);
		PET_IDS.put("moxi", 30154);
		PET_IDS.put("little nightmare", 24491);
		PET_IDS.put("nexling", 26348);
		PET_IDS.put("pet dagannoth rex", 12645);
		PET_IDS.put("pet dagannoth prime", 12644);
		PET_IDS.put("pet dagannoth supreme", 12643);
		PET_IDS.put("phoenix", 20693);
		PET_IDS.put("tiny tempor", 25602);
		PET_IDS.put("smolcano", 23760);
		PET_IDS.put("abyssal protector", 26901);
		PET_IDS.put("huberte", 30152);
		PET_IDS.put("bran", 30622);
		PET_IDS.put("dom", 31130);
		PET_IDS.put("yami", 30888);
		PET_IDS.put("smol heredit", 28960);
		PET_IDS.put("gull", 31285);
		PET_IDS.put("olmlet", 20851);
		PET_IDS.put("lil' zik", 22473);
		PET_IDS.put("tumeken's guardian", 27352);
		PET_IDS.put("tzrek-jad", 13225);
		PET_IDS.put("jal-nib-rek", 21291);
		PET_IDS.put("youngllef", 23757);
		PET_IDS.put("bloodhound", 19730);
		// untradeable / special notable items (not found by tradeable name search)
		PET_IDS.put("ultor vestige", 28285);
		PET_IDS.put("magus vestige", 28281);
		PET_IDS.put("venator vestige", 28283);
		PET_IDS.put("leviathan's lure", 28325);
		PET_IDS.put("crystal weapon seed", 4207);
		PET_IDS.put("crystal armour seed", 23956);
		PET_IDS.put("enhanced crystal weapon seed", 25859);
		PET_IDS.put("metamorphic dust", 22386);
		PET_IDS.put("twisted ancestral colour kit", 24670);
		PET_IDS.put("sanguine dust", 25746);
		PET_IDS.put("sanguine ornament kit", 25744);
		PET_IDS.put("holy ornament kit", 25742);
		PET_IDS.put("unsired", 13273);
		PET_IDS.put("slepey tablet", 25837);
		PET_IDS.put("parasitic egg", 25838);
		PET_IDS.put("pendant of ates (inert)", 29892);
		PET_IDS.put("dizana's quiver (uncharged)", 28947);
		PET_IDS.put("tumeken's shadow (uncharged)", 27277);
		PET_IDS.put("scythe of vitur (uncharged)", 22486);
		PET_IDS.put("sanguinesti staff (uncharged)", 22481);
		PET_IDS.put("eye of ayak (uncharged)", 31115);
		PET_IDS.put("oathplate shards", 30765);
		PET_IDS.put("mokhaiotl cloth", 31109);
		PET_IDS.put("tome of fire", 20716);
		PET_IDS.put("tome of water", 25576);
		PET_IDS.put("tome of earth (empty)", 30066);
		PET_IDS.put("soulflame horn", 30759);
		PET_IDS.put("burning claw", 29574);
		PET_IDS.put("tormented synapse", 29580);
		PET_IDS.put("smouldering gland", 29587);
	}

	private DropTrackerPanel panel;
	private NavigationButton nav;

	@Override
	protected void startUp()
	{
		panel = new DropTrackerPanel(this, itemManager);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/com/pudgy/droptracker/icon.png");
		nav = NavigationButton.builder().tooltip("Lucky Log").icon(icon).priority(7).panel(panel).build();
		clientToolbar.addNavigation(nav);
		SwingUtilities.invokeLater(panel::rebuild);
	}

	@Override
	protected void shutDown()
	{
		if (nav != null)
		{
			clientToolbar.removeNavigation(nav);
		}
	}

	@Provides
	DropTrackerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DropTrackerConfig.class);
	}

	boolean showOdds()
	{
		return config == null || config.showOdds();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (GROUP.equals(e.getGroup()) && panel != null)
		{
			SwingUtilities.invokeLater(panel::rerender);
		}
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		BossRegistry.Boss boss = BossRegistry.byLootName(event.getName());
		if (boss == null)
		{
			return;
		}

		int kc = getKc(boss) + 1;
		setKc(boss, kc);

		List<LootEntry.Item> items = new ArrayList<>();
		Map<Integer, ItemTotal> totals = getTotalsMap(boss);
		if (event.getItems() != null)
		{
			for (ItemStack is : event.getItems())
			{
				String itemName;
				try
				{
					itemName = client.getItemDefinition(is.getId()).getName();
				}
				catch (Exception ex)
				{
					continue;
				}
				items.add(new LootEntry.Item(is.getId(), itemName, is.getQuantity()));
				valueCache.put(is.getId(), safePrice(is.getId()));
				iconIdCache.put(itemName.toLowerCase(), is.getId());
				ItemTotal t = totals.get(is.getId());
				if (t == null)
				{
					t = new ItemTotal(is.getId(), itemName);
					totals.put(is.getId(), t);
				}
				t.total += is.getQuantity();
				t.count++;
				for (BossRegistry.Drop d : boss.drops)
				{
					if (d.notable && d.name.equalsIgnoreCase(itemName))
					{
						addUniqueKc(boss, d.name, kc);
						setRaidSnap(boss, d.name);
					}
				}
			}
		}
		saveTotals(boss, totals);
		List<LootEntry> hist = getHistory(boss);
		hist.add(new LootEntry(kc, items));
		saveHistory(boss, hist);

		SwingUtilities.invokeLater(() -> panel.onKill(boss));
	}

	// --- keys ---
	private String key(BossRegistry.Boss b)
	{
		return b.display.toLowerCase().replace(' ', '_');
	}
	private String dkey(String drop)
	{
		return drop.toLowerCase().replace(' ', '_').replace("'", "");
	}

	// --- KC ---
	int getKc(BossRegistry.Boss b)
	{
		Integer v = configManager.getConfiguration(GROUP, "kc_" + key(b), Integer.class);
		return v == null ? 0 : v;
	}
	void setKc(BossRegistry.Boss b, int v)
	{
		configManager.setConfiguration(GROUP, "kc_" + key(b), Math.max(0, v));
	}

	// --- goal + dry ---
	String getGoal(BossRegistry.Boss b)
	{
		return configManager.getConfiguration(GROUP, "goal_" + key(b), String.class);
	}
	void setGoal(BossRegistry.Boss b, String drop)
	{
		if (drop == null)
		{
			configManager.unsetConfiguration(GROUP, "goal_" + key(b));
		}
		else
		{
			configManager.setConfiguration(GROUP, "goal_" + key(b), drop);
		}
	}
	int getLastDropKc(BossRegistry.Boss b, String drop)
	{
		List<Integer> kcs = getUniqueKcs(b, drop);
		return kcs.isEmpty() ? 0 : kcs.get(kcs.size() - 1);
	}

	// --- per-unique KC history ---
	List<Integer> getUniqueKcs(BossRegistry.Boss b, String item)
	{
		String json = configManager.getConfiguration(GROUP, "ukc_" + key(b) + "_" + dkey(item), String.class);
		if (json == null || json.isEmpty())
		{
			return new ArrayList<>();
		}
		try
		{
			List<Integer> l = gson.fromJson(json, new TypeToken<List<Integer>>()
			{
			}.getType());
			return l == null ? new ArrayList<>() : l;
		}
		catch (Exception e)
		{
			return new ArrayList<>();
		}
	}
	private void addUniqueKc(BossRegistry.Boss b, String item, int kc)
	{
		List<Integer> l = getUniqueKcs(b, item);
		l.add(kc);
		configManager.setConfiguration(GROUP, "ukc_" + key(b) + "_" + dkey(item), gson.toJson(l));
	}

	// --- per-kill loot feed history ---
	List<LootEntry> getHistory(BossRegistry.Boss b)
	{
		String json = configManager.getConfiguration(GROUP, "hist_" + key(b), String.class);
		if (json == null || json.isEmpty())
		{
			return new ArrayList<>();
		}
		try
		{
			List<LootEntry> l = gson.fromJson(json, new TypeToken<List<LootEntry>>()
			{
			}.getType());
			return l == null ? new ArrayList<>() : l;
		}
		catch (Exception e)
		{
			return new ArrayList<>();
		}
	}
	private void saveHistory(BossRegistry.Boss b, List<LootEntry> h)
	{
		while (h.size() > HISTORY_CAP)
		{
			h.remove(0);
		}
		configManager.setConfiguration(GROUP, "hist_" + key(b), gson.toJson(h));
	}

	// --- all-time totals (uncapped; one entry per distinct item) ---
	Map<Integer, ItemTotal> getTotalsMap(BossRegistry.Boss b)
	{
		String json = configManager.getConfiguration(GROUP, "totals_" + key(b), String.class);
		Map<Integer, ItemTotal> m = new LinkedHashMap<>();
		if (json != null && !json.isEmpty())
		{
			try
			{
				Map<Integer, ItemTotal> parsed = gson.fromJson(json, new TypeToken<Map<Integer, ItemTotal>>()
				{
				}.getType());
				if (parsed != null)
				{
					m = parsed;
				}
			}
			catch (Exception ignored)
			{
			}
		}
		if (m.isEmpty())
		{
			for (LootEntry e : getHistory(b))
			{
				if (e.items == null)
				{
					continue;
				}
				for (LootEntry.Item it : e.items)
				{
					if (it.id <= 0)
					{
						continue;
					}
					ItemTotal t = m.get(it.id);
					if (t == null)
					{
						t = new ItemTotal(it.id, it.name);
						m.put(it.id, t);
					}
					t.total += it.qty;
					t.count++;
				}
			}
		}
		return m;
	}
	private void saveTotals(BossRegistry.Boss b, Map<Integer, ItemTotal> m)
	{
		configManager.setConfiguration(GROUP, "totals_" + key(b), gson.toJson(m));
	}

	List<ItemTotal> getTotals(BossRegistry.Boss b)
	{
		List<ItemTotal> l = new ArrayList<>(getTotalsMap(b).values());
		l.sort((a, c) -> Integer.compare(c.count, a.count));
		return l;
	}

	// ===== Smart raid dry-calc (points / level / mode aware) =====
	private static final Map<String, Map<String, Integer>> RAID_WEIGHTS = new LinkedHashMap<>();
	static
	{
		Map<String, Integer> cox = new LinkedHashMap<>();
		cox.put("dexterous prayer scroll", 20); cox.put("arcane prayer scroll", 20);
		cox.put("twisted buckler", 4); cox.put("dragon hunter crossbow", 4);
		cox.put("dinh's bulwark", 3); cox.put("ancestral hat", 3); cox.put("ancestral robe top", 3);
		cox.put("ancestral robe bottom", 3); cox.put("dragon claws", 3);
		cox.put("elder maul", 2); cox.put("kodai insignia", 2); cox.put("twisted bow", 2);
		RAID_WEIGHTS.put("chambers of xeric", cox);
		RAID_WEIGHTS.put("chambers of xeric challenge mode", cox);

		Map<String, Integer> tob = new LinkedHashMap<>();
		tob.put("avernic defender hilt", 8); tob.put("ghrazi rapier", 2); tob.put("sanguinesti staff", 2);
		tob.put("justiciar faceguard", 2); tob.put("justiciar chestguard", 2); tob.put("justiciar legguards", 2);
		tob.put("scythe of vitur (uncharged)", 1);
		RAID_WEIGHTS.put("theatre of blood", tob);
		RAID_WEIGHTS.put("theatre of blood hard mode", tob);

		Map<String, Integer> toa = new LinkedHashMap<>();
		toa.put("tumeken's shadow (uncharged)", 1); toa.put("osmumten's fang", 7); toa.put("lightbearer", 7);
		toa.put("elidinis' ward", 3); toa.put("masori mask", 2); toa.put("masori body", 2); toa.put("masori chaps", 2);
		RAID_WEIGHTS.put("tombs of amascut", toa);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType t = event.getType();
		if (t != ChatMessageType.GAMEMESSAGE && t != ChatMessageType.SPAM)
		{
			return;
		}
		String msg = Text.removeTags(event.getMessage());
		try
		{
			if (msg.startsWith("Congratulations - your raid is complete"))
			{
				int pts = client.getVarpValue(4609); // RAIDS_PLAYERSCORE (personal CoX points)
				if (pts > 0)
				{
					recordRaid("Chambers of Xeric", clamp(pts, 0, 570000) / 867600.0);
				}
			}
			else if (msg.contains("completed Tombs of Amascut"))
			{
				int rl = client.getVarbitValue(14380);
				int slot = client.getVarbitValue(14354);
				int pts = (slot >= 0 && slot < 8) ? client.getVarbitValue(14346 + slot) : 0;
				double p = toaPurple(pts, rl);
				if (p > 0)
				{
					recordRaid("Tombs of Amascut", p);
				}
			}
			else if (msg.contains("completed Theatre of Blood"))
			{
				boolean hard = msg.contains("Hard Mode");
				recordRaid(hard ? "Theatre of Blood Hard Mode" : "Theatre of Blood", hard ? 1.0 / 7.7 : 1.0 / 9.1);
			}
		}
		catch (Exception ignored)
		{
		}
	}

	private static double clamp(double v, double lo, double hi)
	{
		return Math.max(lo, Math.min(hi, v));
	}

	private double toaPurple(int points, int raidLevel)
	{
		if (points <= 0)
		{
			return -1;
		}
		double pts = Math.min(points, 64000);
		double erl = raidLevel <= 400 ? raidLevel : 400 + (Math.min(raidLevel, 550) - 400) / 3.0;
		double per1 = 10500 - 20 * erl;
		if (per1 <= 0)
		{
			return 0.55;
		}
		return Math.min(pts / (100.0 * per1), 0.55);
	}

	private void recordRaid(String raidDisplay, double p)
	{
		if (p <= 0)
		{
			return;
		}
		BossRegistry.Boss b = BossRegistry.byLootName(raidDisplay);
		if (b == null)
		{
			return;
		}
		configManager.setConfiguration(GROUP, "rsum_" + key(b), getRaidSum(b) + p);
		configManager.setConfiguration(GROUP, "rcnt_" + key(b), getRaidCount(b) + 1);
		SwingUtilities.invokeLater(() -> panel.onKill(b));
	}

	double getRaidSum(BossRegistry.Boss b)
	{
		Double v = configManager.getConfiguration(GROUP, "rsum_" + key(b), Double.class);
		return v == null ? 0.0 : v;
	}
	int getRaidCount(BossRegistry.Boss b)
	{
		Integer v = configManager.getConfiguration(GROUP, "rcnt_" + key(b), Integer.class);
		return v == null ? 0 : v;
	}
	private double getRaidSnap(BossRegistry.Boss b, String item)
	{
		Double v = configManager.getConfiguration(GROUP, "rsnap_" + key(b) + "_" + dkey(item), Double.class);
		return v == null ? 0.0 : v;
	}
	private void setRaidSnap(BossRegistry.Boss b, String item)
	{
		if (RAID_WEIGHTS.containsKey(b.display.toLowerCase()))
		{
			configManager.setConfiguration(GROUP, "rsnap_" + key(b) + "_" + dkey(item), getRaidSum(b));
		}
	}

	Double smartChanceHave(BossRegistry.Boss b, String goal)
	{
		Map<String, Integer> w = RAID_WEIGHTS.get(b.display.toLowerCase());
		if (w == null || goal == null || getRaidCount(b) <= 0)
		{
			return null;
		}
		Integer wi = w.get(goal.toLowerCase());
		if (wi == null)
		{
			return null;
		}
		int wsum = 0; for (int x : w.values())
		{
			wsum += x;
		}
		double sinceSum = Math.max(0.0, getRaidSum(b) - getRaidSnap(b, goal));
		return 1.0 - Math.exp(-sinceSum * wi / (double) wsum);
	}

	Double avgPurpleOneInX(BossRegistry.Boss b)
	{
		double sum = getRaidSum(b); int cnt = getRaidCount(b);
		if (cnt <= 0 || sum <= 0)
		{
			return null;
		}
		return cnt / sum;
	}

	// ===== live GE value (computed on the client thread, read by the panel) =====
	private long safePrice(int id)
	{
		try
		{
			return Math.max(0, itemManager.getItemPrice(id));
		}
		catch (Exception e)
		{
			return 0L;
		}
	}
	long unitPrice(int id)
	{
		Long v = valueCache.get(id);
		return v == null ? 0L : v;
	}
	// One-time pass over the game item definitions to build a complete name -> id map,
	// so every notable (tradeable or not) resolves an icon without curated tables.
	private void indexItemsOnce()
	{
		if (itemsIndexed || clientThread == null)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			if (itemsIndexed)
			{
				return;
			}
			int found = 0;
			for (int id = 0; id < 32000; id++)
			{
				try
				{
					ItemComposition c = itemManager.getItemComposition(id);
					if (c == null || c.getNote() != -1 || c.getPlaceholderTemplateId() != -1)
					{
						continue;
					}
					String nm = c.getName();
					if (nm == null || nm.isEmpty() || "null".equalsIgnoreCase(nm))
					{
						continue;
					}
					iconIdCache.putIfAbsent(nm.toLowerCase(), id);
					found++;
				}
				catch (Exception ignored)
				{
				}
			}
			if (found > 1000)
			{
				itemsIndexed = true;
				if (panel != null)
				{
					SwingUtilities.invokeLater(panel::rerender);
				}
			}
		});
	}

	void warmPrices(BossRegistry.Boss b)
	{
		if (clientThread == null || b == null)
		{
			return;
		}
		indexItemsOnce();
		clientThread.invoke(() ->
		{
			boolean changed = false;
			for (BossRegistry.Drop d : b.notableDrops())
			{
				if (resolveIcon(d.name))
				{
					changed = true;
				}
			}
			String goal = getGoal(b);
			if (goal != null && resolveIcon(goal))
			{
				changed = true;
			}
			for (ItemTotal t : getTotals(b))
			{
				long pr = safePrice(t.id);
				Long old = valueCache.put(t.id, pr);
				if (old == null || old != pr)
				{
					changed = true;
				}
			}
			if (changed)
			{
				SwingUtilities.invokeLater(() -> panel.onKill(b));
			}
		});
	}

	private boolean resolveIcon(String name)
	{
		String k = name == null ? "" : name.toLowerCase();
		if (k.isEmpty())
		{
			return false;
		}
		Integer cur = iconIdCache.get(k);
		if ((cur != null && cur > 0) || PET_IDS.containsKey(k))
		{
			return false;
		}
		int id = 0;
		try
		{
			List<ItemPrice> res = itemManager.search(name);
			if (res != null)
			{
				for (ItemPrice ip : res)
				{
					if (ip.getName().equalsIgnoreCase(name))
					{
						id = ip.getId();
						break;
					}
				}
				if (id == 0 && !res.isEmpty())
				{
					id = res.get(0).getId();
				}
			}
		}
		catch (Exception ignored)
		{
		}
		if (id > 0)
		{
			iconIdCache.put(k, id);
			return true;
		}
		return false;
	}
	int iconId(String name)
	{
		if (name == null)
		{
			return 0;
		}
		String k = name.toLowerCase();
		Integer v = iconIdCache.get(k);
		if (v != null && v > 0)
		{
			return v;
		}
		Integer pid = PET_IDS.get(k);
		if (pid != null)
		{
			return pid;
		}
		return v == null ? 0 : v;
	}

	// ===== bundled boss render images (from /resources/com/pudgy/droptracker/bosses/<key>.png) =====
	static String imgKey(String display)
	{
		String s = display.toLowerCase().replace("'", "");
		s = s.replaceAll("[^a-z0-9]+", "_");
		return s.replaceAll("^_+", "").replaceAll("_+$", "");
	}
	BufferedImage supportImage()
	{
		String path = "/com/pudgy/droptracker/support.png";
		if (getClass().getResource(path) == null)
		{
			return null;
		}
		try
		{
			return ImageUtil.loadImageResource(getClass(), path);
		}
		catch (Exception e)
		{
			return null;
		}
	}
	BufferedImage bossImage(BossRegistry.Boss b)
	{
		if (b == null)
		{
			return null;
		}
		String k = imgKey(b.display);
		BufferedImage cached = bossImgCache.get(k);
		if (cached != null)
		{
			return cached;
		}
		String path = "/com/pudgy/droptracker/bosses/" + k + ".png";
		if (getClass().getResource(path) == null)
		{
			return null;
		}
		try
		{
			BufferedImage img = ImageUtil.loadImageResource(getClass(), path);
			if (img != null)
			{
				bossImgCache.put(k, img);
			}
			return img;
		}
		catch (Exception e)
		{
			return null;
		}
	}
}
