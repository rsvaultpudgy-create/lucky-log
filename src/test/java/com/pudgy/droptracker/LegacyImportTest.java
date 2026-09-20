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
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/**
 * Exercises the pre-1.2 shared-store import against an in-memory stand-in for the two
 * config scopes (shared group vs. per-account rsprofile).
 */
public class LegacyImportTest
{
	/** Same string round-trip ConfigManager does, so typed reads behave like the real thing. */
	static final class StorePlugin extends DropTrackerPlugin
	{
		final Map<String, String> legacy = new HashMap<>();
		final Map<String, String> profile = new HashMap<>();
		boolean loggedIn = true;

		@Override
		<T> T pget(String key, Class<T> type)
		{
			return convert(profile.get(key), type);
		}

		@Override
		void pset(String key, Object value)
		{
			profile.put(key, String.valueOf(value));
		}

		@Override
		void punset(String key)
		{
			profile.remove(key);
		}

		@Override
		boolean hasProfile()
		{
			return loggedIn;
		}

		@Override
		String legacyRaw(String key)
		{
			return legacy.get(key);
		}

		@Override
		Set<String> legacyKeyNames()
		{
			return new HashSet<>(legacy.keySet());
		}

		@Override
		void legacyUnset(String key)
		{
			legacy.remove(key);
		}

		@SuppressWarnings("unchecked")
		private static <T> T convert(String v, Class<T> type)
		{
			if (v == null)
			{
				return null;
			}
			if (type == Integer.class)
			{
				return (T) Integer.valueOf(v.trim());
			}
			if (type == Double.class)
			{
				return (T) Double.valueOf(v.trim());
			}
			if (type == Boolean.class)
			{
				return (T) Boolean.valueOf(v.trim());
			}
			return (T) v;
		}
	}

	private StorePlugin p;
	private BossRegistry.Boss vork;
	private BossRegistry.Boss zul;
	private String vk;

	@Before
	public void setUp() throws Exception
	{
		p = new StorePlugin();
		Field g = DropTrackerPlugin.class.getDeclaredField("gson");
		g.setAccessible(true);
		g.set(p, new Gson());
		vork = BossRegistry.byLootName("Vorkath");
		zul = BossRegistry.byLootName("Zulrah");
		vk = "vorkath";

		// a shared store as 1.1 would have left it: Vorkath with a history, totals, one
		// unique at KC 40, one pre-tracking unique, and a goal
		p.legacy.put("kc_" + vk, "100");
		p.legacy.put("goal_" + vk, "Dragonbone necklace");
		p.legacy.put("hist_" + vk, "[{\"kc\":99,\"items\":[{\"id\":1,\"name\":\"Coins\",\"qty\":5}]},{\"kc\":100,\"items\":[{\"id\":2,\"name\":\"Bones\",\"qty\":1}]}]");
		p.legacy.put("totals_" + vk, "{\"1\":{\"id\":1,\"name\":\"Coins\",\"total\":500,\"count\":90},\"2\":{\"id\":2,\"name\":\"Bones\",\"total\":100,\"count\":100}}");
		p.legacy.put("ukc_" + vk + "_dragonbone_necklace", "[40]");
		p.legacy.put("unk_" + vk + "_skeletal_visage", "1");
		p.legacy.put("ibase_" + vk + "_skeletal_visage", "60");
		// and a setting that is not per-boss data
		p.legacy.put("showOdds", "false");
	}

	@Test
	public void legacyBossesListsOnlyBossesWithData()
	{
		Map<BossRegistry.Boss, Integer> lb = p.legacyBosses();
		assertEquals(1, lb.size());
		assertEquals(Integer.valueOf(100), lb.get(vork));
		assertTrue(p.hasLegacyData());
	}

	@Test
	public void importIntoEmptyAccountCopiesEverything()
	{
		p.importLegacy(vork);
		assertEquals(100, p.getKc(vork));
		assertEquals("Dragonbone necklace", p.getGoal(vork));
		List<LootEntry> h = p.getHistory(vork);
		assertEquals(2, h.size());
		assertEquals(99, h.get(0).kc);
		assertEquals(100, h.get(1).kc);
		assertEquals(Arrays.asList(40), p.getUniqueKcs(vork, "Dragonbone necklace"));
		assertEquals(1, p.getUnknownCount(vork, "Skeletal visage"));
		assertEquals(60, p.importBaselineKc(vork, "Skeletal visage"));
		Map<Integer, ItemTotal> t = p.getTotalsMap(vork);
		assertEquals(500, t.get(1).total);
		assertEquals(100, t.get(2).count);
		// untouched bosses stay empty, shared copy stays put for the next account
		assertEquals(0, p.getKc(zul));
		assertTrue(p.legacy.containsKey("kc_" + vk));
	}

	@Test
	public void importStacksOnTopOfKillsRecordedSinceTheSplit()
	{
		// this account did 10 Vorkath after updating: kills 1..10, a necklace at 7
		p.profile.put("kc_" + vk, "10");
		p.profile.put("hist_" + vk, "[{\"kc\":3,\"items\":[{\"id\":2,\"name\":\"Bones\",\"qty\":1}]},{\"kc\":10,\"items\":[{\"id\":2,\"name\":\"Bones\",\"qty\":1}]}]");
		p.profile.put("totals_" + vk, "{\"2\":{\"id\":2,\"name\":\"Bones\",\"total\":10,\"count\":10}}");
		p.profile.put("ukc_" + vk + "_dragonbone_necklace", "[7]");
		p.profile.put("goal_" + vk, "Skeletal visage");

		p.importLegacy(vork);

		assertEquals(110, p.getKc(vork));
		// own goal wins
		assertEquals("Skeletal visage", p.getGoal(vork));
		// legacy feed first, own entries shifted by the shared KC
		List<LootEntry> h = p.getHistory(vork);
		assertEquals(4, h.size());
		assertEquals(99, h.get(0).kc);
		assertEquals(100, h.get(1).kc);
		assertEquals(103, h.get(2).kc);
		assertEquals(110, h.get(3).kc);
		assertEquals(Arrays.asList(40, 107), p.getUniqueKcs(vork, "Dragonbone necklace"));
		Map<Integer, ItemTotal> t = p.getTotalsMap(vork);
		assertEquals(500, t.get(1).total);
		assertEquals(110, t.get(2).total);
		assertEquals(110, t.get(2).count);
	}

	@Test
	public void importDoesNothingWhenLoggedOut()
	{
		p.loggedIn = false;
		p.importLegacy(vork);
		assertTrue(p.profile.isEmpty());
	}

	@Test
	public void deleteRemovesOnlyBossDataFromTheSharedStore()
	{
		p.deleteLegacyData();
		assertFalse(p.hasLegacyData());
		assertNull(p.legacy.get("kc_" + vk));
		assertNull(p.legacy.get("ukc_" + vk + "_dragonbone_necklace"));
		// plugin settings live in the same group and must survive
		assertEquals("false", p.legacy.get("showOdds"));
	}

	@Test
	public void perAccountWritesNeverTouchTheSharedStore()
	{
		Map<String, String> before = new LinkedHashMap<>(p.legacy);
		p.setKc(zul, 5);
		p.setGoal(zul, "Tanzanite fang");
		p.setUnknownCount(zul, "Magic fang", 2);
		p.resetBoss(vork);
		assertEquals(before, p.legacy);
		assertEquals(5, p.getKc(zul));
	}

	@Test
	public void ownUniqueShiftsEvenWhenSharedStoreNeverSawIt()
	{
		p.profile.put("kc_" + vk, "10");
		p.profile.put("ukc_" + vk + "_skeletal_visage", "[7]");
		p.importLegacy(vork);
		assertEquals(110, p.getKc(vork));
		assertEquals(Arrays.asList(107), p.getUniqueKcs(vork, "Skeletal visage"));
		// dry streak since that visage is 3 kills, not 103
		assertEquals(3, p.getKc(vork) - p.getLastDropKc(vork, "Skeletal visage"));
	}

	@Test
	public void ownSnapshotsMoveUpByTheSharedBase()
	{
		BossRegistry.Boss cox = BossRegistry.byLootName("Chambers of Xeric");
		String ck = "chambers_of_xeric";
		p.legacy.put("kc_" + ck, "50");
		p.legacy.put("rsum_" + ck, "5.0");
		p.legacy.put("rcnt_" + ck, "50");
		p.legacy.put("esum_" + ck + "_twisted_bow", "0.5");
		// own: 10 raids, a tbow just dropped (snapshot == sum), own import baseline at 5
		p.profile.put("kc_" + ck, "10");
		p.profile.put("rsum_" + ck, "1.0");
		p.profile.put("rcnt_" + ck, "10");
		p.profile.put("rsnap_" + ck + "_twisted_bow", "1.0");
		p.profile.put("esum_" + ck + "_twisted_bow", "0.1");
		p.profile.put("esnap_" + ck + "_twisted_bow", "0.1");
		p.profile.put("ibase_" + ck + "_twisted_bow", "5");

		p.importLegacy(cox);

		assertEquals(6.0, p.getRaidSum(cox), 1e-9);
		assertEquals(60, p.getRaidCount(cox));
		assertEquals(6.0, Double.parseDouble(p.profile.get("rsnap_" + ck + "_twisted_bow")), 1e-9);
		assertEquals(0.6, p.getEsum(cox, "Twisted bow"), 1e-9);
		assertEquals(0.6, Double.parseDouble(p.profile.get("esnap_" + ck + "_twisted_bow")), 1e-9);
		assertEquals(55, p.importBaselineKc(cox, "Twisted bow"));
		// nothing has happened since the tbow, so the smart chance stays ~0
		assertEquals(0.0, p.smartChanceHave(cox, "Twisted bow"), 1e-9);
	}

	@Test
	public void legacyFeedWithoutStoredTotalsStillCounts()
	{
		p.legacy.remove("totals_" + vk);
		p.profile.put("kc_" + vk, "1");
		p.profile.put("totals_" + vk, "{\"2\":{\"id\":2,\"name\":\"Bones\",\"total\":1,\"count\":1}}");
		p.importLegacy(vork);
		Map<Integer, ItemTotal> t = p.getTotalsMap(vork);
		assertEquals(5, t.get(1).total);
		assertEquals(2, t.get(2).total);
	}

	@Test
	public void ownCollectionLogImportAlreadyCoversLegacyTrackedObtains()
	{
		// col-log says 3 necklaces; shared store tracked 1 (KC 40); this account imported
		// the log after the split and recorded all 3 as unknown
		p.profile.put("unk_" + vk + "_dragonbone_necklace", "3");
		p.importLegacy(vork);
		assertEquals(2, p.getUnknownCount(vork, "Dragonbone necklace"));
		assertEquals(Arrays.asList(40), p.getUniqueKcs(vork, "Dragonbone necklace"));
	}

	@Test
	public void importIsMarkedDoneAndResetClearsIt()
	{
		assertFalse(p.legacyImported(vork));
		p.importLegacy(vork);
		assertTrue(p.legacyImported(vork));
		p.resetBoss(vork);
		assertFalse(p.legacyImported(vork));
		assertEquals(0, p.getKc(vork));
	}

	@Test
	public void oneTimeDropsStopBeingDryOnceOwnedButPetsKeepRolling()
	{
		BossRegistry.Boss whisp = BossRegistry.byLootName("The Whisperer");
		BossRegistry.Drop tablet = null, staff = null, wisp = null;
		for (BossRegistry.Drop d : whisp.drops)
		{
			if (d.name.equals("Sirenic tablet"))
			{
				tablet = d;
			}
			if (d.name.equals("Siren's staff"))
			{
				staff = d;
			}
			if (d.pet)
			{
				wisp = d;
			}
		}
		assertTrue(tablet.once);
		assertFalse(tablet.repeatable());
		assertTrue(staff.repeatable());
		// pets re-roll after the first ("you would have been followed"), so they are never "done"
		assertTrue(wisp.repeatable());
		// nothing owned yet: all three are legitimately dry
		assertFalse(p.doneForever(whisp, tablet));
		assertFalse(p.doneForever(whisp, wisp));
		// tablet at KC 7, staff at KC 9, pet imported from the collection log
		p.profile.put("ukc_the_whisperer_sirenic_tablet", "[7]");
		p.profile.put("ukc_the_whisperer_sirens_staff", "[9]");
		p.profile.put("unk_the_whisperer_wisp", "1");
		assertTrue(p.doneForever(whisp, tablet));
		assertFalse(p.doneForever(whisp, staff));
		assertFalse(p.doneForever(whisp, wisp));
	}

	@Test
	public void markObtainedMakesOneTimeNeedleDoneForeverWithoutCollectionLog()
	{
		BossRegistry.Boss gotr = BossRegistry.byLootName("Guardians of the Rift");
		BossRegistry.Drop needle = null, lantern = null;
		for (BossRegistry.Drop d : gotr.drops)
		{
			if (d.name.equals("Abyssal needle"))
			{
				needle = d;
			}
			if (d.name.equals("Abyssal lantern"))
			{
				lantern = d;
			}
		}
		// Rewards Guardian table: needle "only rolled if the player hasn't received one before";
		// the lantern has no such footnote (and is also buyable for pearls) so it stays repeatable.
		assertTrue(needle.once);
		assertTrue(lantern.repeatable());
		p.profile.put("kc_guardians_of_the_rift", "451");
		assertFalse(p.doneForever(gotr, needle));

		p.markObtained(gotr, needle.name);
		assertEquals(1, p.getUnknownCount(gotr, needle.name));
		assertEquals("451", p.profile.get("ibase_guardians_of_the_rift_abyssal_needle"));
		assertTrue(p.doneForever(gotr, needle));
		// a repeatable item marked obtained just gains an untracked obtain, never becomes "done"
		p.markObtained(gotr, lantern.name);
		p.markObtained(gotr, lantern.name);
		assertEquals(2, p.getUnknownCount(gotr, lantern.name));
		assertFalse(p.doneForever(gotr, lantern));

		p.clearObtained(gotr, needle.name);
		assertEquals(0, p.getUnknownCount(gotr, needle.name));
		assertNull(p.profile.get("ibase_guardians_of_the_rift_abyssal_needle"));
		assertFalse(p.doneForever(gotr, needle));
	}

	@Test
	public void everyVerifiedOneTimeDropIsFlagged()
	{
		String[][] expect = {
			{"The Whisperer", "Sirenic tablet"},
			{"Royal Titans", "Mystic vigour prayer scroll"},
			{"Royal Titans", "Deadeye prayer scroll"},
			{"Tempoross", "Fish barrel"},
			{"Tempoross", "Tackle box"},
		};
		for (String[] e : expect)
		{
			BossRegistry.Boss b = BossRegistry.byLootName(e[0]);
			boolean found = false;
			for (BossRegistry.Drop d : b.notableDrops())
			{
				if (d.name.equals(e[1]))
				{
					found = true;
					assertTrue(e[0] + " / " + e[1] + " should be once", d.once);
				}
			}
			assertTrue(e[0] + " / " + e[1] + " missing", found);
		}
	}
}
