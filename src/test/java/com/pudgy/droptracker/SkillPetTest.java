/*
 * Copyright (c) 2025, Pudgy
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
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

/** Skilling-pet roll recognition and the running "would have it by now" maths. */
public class SkillPetTest
{
	private LegacyImportTest.StorePlugin p;

	@Before
	public void setUp() throws Exception
	{
		p = new LegacyImportTest.StorePlugin();
		Field g = DropTrackerPlugin.class.getDeclaredField("gson");
		g.setAccessible(true);
		g.set(p, new Gson());
	}

	@Test
	public void xpMatchesTheRarestActionOnACollision()
	{
		// yew and ironwood both give 175 wc xp; yew (B 145,013) beats ironwood (72,321)
		SkillPetRegistry.Method m = SkillPetRegistry.match(Skill.WOODCUTTING, 175);
		assertEquals("Yew", m.label);
		assertEquals(145013, m.base, 0);
		// coal is 50 xp at 290,640; sandstone/granite 50 at 741,600 wins
		assertEquals(741600, SkillPetRegistry.match(Skill.MINING, 50).base, 0);
		// fractional xp shows as either rounding
		assertEquals("Oak", SkillPetRegistry.match(Skill.WOODCUTTING, 37).label);
		assertEquals("Oak", SkillPetRegistry.match(Skill.WOODCUTTING, 38).label);
		// no rolling action gives this much wc xp in one drop
		assertNull(SkillPetRegistry.match(Skill.WOODCUTTING, 999));
		// farmer's outfit scales the oak check-health drop 467.3 -> 479.0
		assertEquals("Oak tree", SkillPetRegistry.match(Skill.FARMING, 479).label);
		// Soup: salvage sorting is flat 1/800k
		SkillPetRegistry.Method sort = SkillPetRegistry.match(Skill.SAILING, 24);
		assertTrue(sort.flat);
		assertEquals(800000, sort.base, 0);
	}

	@Test
	public void itemGainedOrThievingTargetTellsSameXpActionsApart()
	{
		java.util.List<String> yew = java.util.Arrays.asList("yew logs");
		java.util.List<String> iron = java.util.Arrays.asList("ironwood logs");
		assertEquals("Yew", SkillPetRegistry.match(Skill.WOODCUTTING, 175, yew).label);
		assertEquals("Ironwood", SkillPetRegistry.match(Skill.WOODCUTTING, 175, iron).label);
		assertEquals(72321, SkillPetRegistry.match(Skill.WOODCUTTING, 175, iron).base, 0);
		// felling-axe swing with no log: no hint, rarest wins
		assertEquals("Yew", SkillPetRegistry.match(Skill.WOODCUTTING, 175, java.util.Collections.emptyList()).label);
		// unrelated inventory noise (a nest) does not break it
		assertEquals("Ironwood", SkillPetRegistry.match(Skill.WOODCUTTING, 175, java.util.Arrays.asList("bird nest", "ironwood logs")).label);

		assertEquals("Coal", SkillPetRegistry.match(Skill.MINING, 50, java.util.Arrays.asList("coal")).label);
		assertEquals("Motherlode pay-dirt", SkillPetRegistry.match(Skill.MINING, 60, java.util.Arrays.asList("pay-dirt")).label);
		assertEquals("Gem rock", SkillPetRegistry.match(Skill.MINING, 65, java.util.Arrays.asList("uncut sapphire")).label);
		assertEquals("Gold", SkillPetRegistry.match(Skill.MINING, 65, java.util.Arrays.asList("gold ore")).label);

		assertEquals("Swordfish", SkillPetRegistry.match(Skill.FISHING, 100, java.util.Arrays.asList("raw swordfish")).label);
		assertEquals("Bass", SkillPetRegistry.match(Skill.FISHING, 100, java.util.Arrays.asList("raw bass")).label);
		assertEquals("Trout", SkillPetRegistry.match(Skill.FISHING, 50, java.util.Arrays.asList("raw trout")).label);
		assertEquals(923616, SkillPetRegistry.match(Skill.FISHING, 50, java.util.Arrays.asList("raw trout")).base, 0);
		assertEquals("Leaping trout", SkillPetRegistry.match(Skill.FISHING, 50, java.util.Arrays.asList("leaping trout")).label);
		assertEquals("Tuna", SkillPetRegistry.match(Skill.FISHING, 80, java.util.Arrays.asList("raw tuna")).label);
		assertEquals("Anglerfish", SkillPetRegistry.match(Skill.FISHING, 120, java.util.Arrays.asList("raw anglerfish")).label);

		// thieving: the clicked target name is the hint
		assertEquals("Watchman", SkillPetRegistry.match(Skill.THIEVING, 137, java.util.Arrays.asList("Watchman")).label);
		assertEquals("Menaphite thug", SkillPetRegistry.match(Skill.THIEVING, 138, java.util.Arrays.asList("Menaphite Thug")).label);
		assertEquals("Tea stall", SkillPetRegistry.match(Skill.THIEVING, 16, java.util.Arrays.asList("Tea stall")).label);
		assertEquals("Seed stall", SkillPetRegistry.match(Skill.THIEVING, 10, java.util.Arrays.asList("Seed stall")).label);
		// stale target from a different action: falls back to the rarest
		assertEquals("Menaphite thug", SkillPetRegistry.match(Skill.THIEVING, 137, java.util.Arrays.asList("Guard")).label);

		// end to end through the plugin seam
		assertEquals(1, p.skillXp(SkillPetRegistry.BEAVER, 175, 1, null, 99, false, iron));
		assertEquals("Ironwood", p.skillPetState(SkillPetRegistry.BEAVER).last);
		assertEquals(Math.log(1 - 1.0 / (72321 - 25 * 99)), p.skillPetState(SkillPetRegistry.BEAVER).lnq, 1e-12);
	}

	@Test
	public void chanceUsesBaseLevelCappedAt99AndFlatRatesIgnoreLevel()
	{
		assertEquals(1.0 / (145013 - 25 * 99), SkillPetRegistry.chance(145013, false, 99, false), 1e-15);
		assertEquals(1.0 / (145013 - 25 * 60), SkillPetRegistry.chance(145013, false, 60, false), 1e-15);
		assertEquals(1.0 / (145013 - 25 * 99), SkillPetRegistry.chance(145013, false, 120, false), 1e-15);
		assertEquals(15.0 / (145013 - 25 * 99), SkillPetRegistry.chance(145013, false, 99, true), 1e-15);
		assertEquals(1.0 / 800000, SkillPetRegistry.chance(800000, true, 99, false), 1e-15);
		assertEquals(1.0 / 800000, SkillPetRegistry.chance(800000, true, 99, true), 1e-15);
	}

	@Test
	public void wintertodtAndTemporossNeverRoll()
	{
		assertEquals(0, p.skillXp(SkillPetRegistry.BEAVER, 175, SkillPetRegistry.REGION_WINTERTODT, null, 99, false));
		assertEquals(0, p.skillXp(SkillPetRegistry.HERON, 10, SkillPetRegistry.REGION_TEMPOROSS, null, 99, false));
		assertEquals(1, p.skillXp(SkillPetRegistry.BEAVER, 175, 12345, null, 99, false));
		assertEquals(1, p.skillPetState(SkillPetRegistry.BEAVER).n);
	}

	@Test
	public void agilityLapCountsOnlyOnTheCourseEndTile()
	{
		int seers = 10806;
		WorldPoint end = new WorldPoint(2704, 3464, 0);
		WorldPoint mid = new WorldPoint(2720, 3480, 0);
		assertEquals(0, p.skillXp(SkillPetRegistry.GIANT_SQUIRREL, 435, seers, mid, 99, false));
		assertEquals(1, p.skillXp(SkillPetRegistry.GIANT_SQUIRREL, 435, seers, end, 99, false));
		assertEquals(0, p.skillXp(SkillPetRegistry.GIANT_SQUIRREL, 435, 1, end, 99, false));
		DropTrackerPlugin.SkillPetState st = p.skillPetState(SkillPetRegistry.GIANT_SQUIRREL);
		assertEquals(1, st.n);
		assertEquals("Seers' Village rooftop", st.last);
		assertEquals(Math.log(1 - 1.0 / (35205 - 25 * 99)), st.lnq, 1e-12);
	}

	@Test
	public void runningOddsAccumulateAcrossManyRolls()
	{
		// 100,000 magic logs at 99: P(pet) = 1 - (1 - 1/69846)^100000 ≈ 76.1%
		p.recordSkillRolls(SkillPetRegistry.BEAVER, "Magic", SkillPetRegistry.chance(72321, false, 99, false), 100000, 5);
		DropTrackerPlugin.SkillPetState st = p.skillPetState(SkillPetRegistry.BEAVER);
		assertEquals(100000, st.n);
		double pct = 1 - Math.exp(st.slnq);
		assertEquals(0.761, pct, 0.002);
		// state survives the JSON round trip
		assertNotNull(p.profile.get("spet_beaver"));
		assertEquals(100000, p.skillPetState(SkillPetRegistry.BEAVER).n);
	}

	@Test
	public void claimUndoAndResetOnlyTouchThatPet()
	{
		p.recordSkillRolls(SkillPetRegistry.HERON, "Shark", 0.00001, 10, 1);
		p.recordSkillRolls(SkillPetRegistry.ROCKY, "Elf", 0.00001, 3, 1);
		p.claimSkillPet(SkillPetRegistry.HERON, true);
		assertEquals(1, p.skillPetState(SkillPetRegistry.HERON).got);
		assertEquals(10, p.skillPetState(SkillPetRegistry.HERON).n);   // claim never edits rolls
		assertEquals(0, p.skillPetState(SkillPetRegistry.ROCKY).got);
		p.claimSkillPet(SkillPetRegistry.HERON, false);
		assertEquals(0, p.skillPetState(SkillPetRegistry.HERON).got);
		p.resetSkillPet(SkillPetRegistry.HERON);
		assertEquals(0, p.skillPetState(SkillPetRegistry.HERON).n);
		assertEquals(3, p.skillPetState(SkillPetRegistry.ROCKY).n);
	}

	@Test
	public void anyPetCounterResetsOnlyWhenLuckyLogSeesAPetLand()
	{
		p.recordSkillRolls(SkillPetRegistry.BEAVER, "Yew", 0.0001, 100, 10);
		p.recordSkillRolls(SkillPetRegistry.HERON, "Shark", 0.0001, 50, 20);
		DropTrackerPlugin.AnyPetState any = p.anyPetState();
		assertEquals(150, any.n);
		assertNull(any.lastPet);
		// a claim is not a sighting: headline stays "by now"
		p.claimSkillPet(SkillPetRegistry.ROCKY, true);
		assertNull(p.anyPetState().lastPet);
		// the funny-feeling message within the window after a heron roll
		p.onSkillPetMessage(25);
		any = p.anyPetState();
		assertEquals("Heron", any.lastPet);
		assertEquals(0, any.n);
		assertEquals(1, p.skillPetState(SkillPetRegistry.HERON).got);
		assertEquals(0, p.skillPetState(SkillPetRegistry.HERON).sn);
		assertEquals(100, p.skillPetState(SkillPetRegistry.BEAVER).sn);  // beaver streak untouched
		// too late after the last roll: ignored
		p.recordSkillRolls(SkillPetRegistry.BEAVER, "Yew", 0.0001, 5, 30);
		p.onSkillPetMessage(60);
		assertEquals("Heron", p.anyPetState().lastPet);
		assertEquals(5, p.anyPetState().n);
	}

	@Test
	public void everyPetHasAtLeastOneRollingActionOrCourse()
	{
		for (SkillPetRegistry.Pet pet : SkillPetRegistry.PETS)
		{
			if (pet.skill == Skill.AGILITY)
			{
				assertNotNull(SkillPetRegistry.courseAt(10806));
			}
			else if (pet.skill == Skill.RUNECRAFT)
			{
				assertTrue(SkillPetRegistry.RC_BASE > SkillPetRegistry.RC_BLOOD);
			}
			else
			{
				boolean any = false;
				for (int xp = 1; xp < 30000 && !any; xp++)
				{
					any = SkillPetRegistry.match(pet.skill, xp) != null;
				}
				assertTrue(pet.name + " has no rolling action", any);
			}
		}
	}
}
