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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;

/**
 * The nine skilling pets and every action the wiki says rolls for them.
 *
 * Most pets roll at 1 / (B - 25 * level) per action, where B depends on the method (tree,
 * ore, fish, course...) and level is the player's BASE level (boosts do not count, capped at
 * 99; 200M xp in the skill multiplies the chance by 15). Soup rolls at flat per-action rates.
 *
 * Actions are recognised from the xp drop: RuneLite reports whole xp, so a 37.5 drop shows as
 * 37 or 38 and a match accepts either. When two rolling actions share an xp value, the item
 * that landed in the inventory that tick (or the Thieving target clicked) tells them apart;
 * only when no such hint exists does the rarer rate win, so the "would have it by now" figure
 * never overstates the player's luck.
 * Actions the wiki says do NOT roll (Wintertodt roots, Tempoross fish, GotR altars, sepulchre
 * stairs, ticket redemption...) are either absent from the tables or excluded by region.
 *
 * Sources (wiki, Sep 2026): the pet pages, Calculator:Skill_pet_chance, and the skill pages.
 */
final class SkillPetRegistry
{
	private SkillPetRegistry()
	{
	}

	/** One skilling pet: the tracked skill, the item name used for its icon, and what rolls it. */
	static final class Pet
	{
		final String name;
		final Skill skill;
		final String key;
		final String source;

		Pet(String name, Skill skill, String source)
		{
			this.name = name;
			this.skill = skill;
			this.key = name.toLowerCase().replaceAll("[^a-z0-9]+", "_");
			this.source = source;
		}
	}

	/** One rolling action recognised from its xp drop. base == B, or the flat 1/x when flat. */
	static final class Method
	{
		final Pet pet;
		final String label;
		final double xp;
		final double base;
		final boolean flat;
		/** Lower-case item that lands in the inventory on this action, or NPC/stall name for Thieving; null when unknown. */
		final String hint;

		Method(Pet pet, String label, double xp, double base, boolean flat, String hint)
		{
			this.pet = pet;
			this.label = label;
			this.xp = xp;
			this.base = base;
			this.flat = flat;
			this.hint = hint == null ? null : hint.toLowerCase();
		}

		/** Whole-xp drop d matches a fractional table value at either rounding. */
		boolean matches(int d)
		{
			return d == (int) Math.floor(xp) || d == (int) Math.ceil(xp);
		}
	}

	/** An Agility course: a lap ends when Agility xp lands while standing on one of the end tiles. */
	static final class Course
	{
		final String label;
		final int regionId;
		final double base;
		final WorldPoint[] ends;

		Course(String label, int regionId, double base, WorldPoint... ends)
		{
			this.label = label;
			this.regionId = regionId;
			this.base = base;
			this.ends = ends;
		}
	}

	static final Pet BABY_CHINCHOMPA = new Pet("Baby chinchompa", Skill.HUNTER, "catching chinchompas");
	static final Pet BEAVER = new Pet("Beaver", Skill.WOODCUTTING, "chopping trees");
	static final Pet GIANT_SQUIRREL = new Pet("Giant squirrel", Skill.AGILITY, "completing laps");
	static final Pet HERON = new Pet("Heron", Skill.FISHING, "catching fish");
	static final Pet RIFT_GUARDIAN = new Pet("Rift guardian", Skill.RUNECRAFT, "per essence crafted");
	static final Pet ROCK_GOLEM = new Pet("Rock golem", Skill.MINING, "mining ore");
	static final Pet ROCKY = new Pet("Rocky", Skill.THIEVING, "pickpockets and stalls");
	static final Pet SOUP = new Pet("Soup", Skill.SAILING, "sailing actions");
	static final Pet TANGLEROOT = new Pet("Tangleroot", Skill.FARMING, "checking tree health");

	static final List<Pet> PETS = Collections.unmodifiableList(java.util.Arrays.asList(
		BABY_CHINCHOMPA, BEAVER, GIANT_SQUIRREL, HERON, RIFT_GUARDIAN, ROCK_GOLEM, ROCKY, SOUP, TANGLEROOT));

	private static final Map<Skill, List<Method>> METHODS = new EnumMap<>(Skill.class);
	private static final List<Course> COURSES = new ArrayList<>();

	// Regions where the skill gives xp but the wiki says the pet is never rolled.
	static final int REGION_WINTERTODT = 6462;
	static final int REGION_TEMPOROSS = 12078;
	static final int REGION_GOTR = 14484;
	// Runecraft altars with their own B.
	static final int REGION_BLOOD_ALTAR = 6715;
	static final int REGION_SOUL_ALTAR = 7228;
	static final int REGION_OURANIA = 12119;

	static final double RC_BASE = 1795758;
	static final double RC_BLOOD = 804984;
	static final double RC_SOUL = 782999;
	static final double RC_OURANIA = 1487213;

	private static void m(Pet p, String label, double xp, double base)
	{
		m(p, label, xp, base, null);
	}

	/** hint: the item gained (or, for Thieving, the NPC / stall name) that tells this action apart from same-xp ones. */
	private static void m(Pet p, String label, double xp, double base, String hint)
	{
		METHODS.computeIfAbsent(p.skill, k -> new ArrayList<>()).add(new Method(p, label, xp, base, false, hint));
	}

	private static void flat(Pet p, String label, double xp, double oneIn)
	{
		METHODS.computeIfAbsent(p.skill, k -> new ArrayList<>()).add(new Method(p, label, xp, oneIn, true, null));
	}

	static
	{
		// ---- Hunter: chinchompas only ----
		m(BABY_CHINCHOMPA, "Grey chinchompa", 198.4, 131395);
		m(BABY_CHINCHOMPA, "Red chinchompa", 265, 98373);
		m(BABY_CHINCHOMPA, "Black chinchompa", 315, 82758);

		// ---- Woodcutting: per log (felling-axe "no log" swings still roll on the xp drop) ----
		m(BEAVER, "Normal tree", 25, 317647);
		m(BEAVER, "Oak", 37.5, 361146);
		m(BEAVER, "Willow", 67.5, 289286);
		m(BEAVER, "Teak", 85, 264336);
		m(BEAVER, "Jatoba", 92, 264336);
		m(BEAVER, "Juniper", 35, 360000);
		m(BEAVER, "Maple", 100, 221918);
		m(BEAVER, "Maple (Kandarin diary)", 110, 221918);
		m(BEAVER, "Hollow tree", 82.5, 214367);
		m(BEAVER, "Mahogany", 125, 220623);
		m(BEAVER, "Arctic pine", 40, 145758);
		m(BEAVER, "Yew", 175, 145013, "Yew logs");
		m(BEAVER, "Ironwood", 175, 72321, "Ironwood logs");
		m(BEAVER, "Blisterwood", 76, 289286);
		m(BEAVER, "Sulliuscep", 127, 343000);
		m(BEAVER, "Camphor", 143.5, 145013);
		m(BEAVER, "Magic", 250, 72321);
		m(BEAVER, "Engorged bloodwood", 165, 319283);
		m(BEAVER, "Redwood", 380, 72321);
		m(BEAVER, "Rosewood", 212.5, 72321);

		// ---- Mining: per ore / pay-dirt / blast-mine excavation / VM fragment ----
		m(ROCK_GOLEM, "Clay", 5, 741600);
		m(ROCK_GOLEM, "Copper / Tin", 17.5, 741600);
		m(ROCK_GOLEM, "Limestone", 26.5, 741600);
		m(ROCK_GOLEM, "Iron", 35, 741600, "Iron ore");
		m(ROCK_GOLEM, "Volcanic sulphur", 35, 710000, "Volcanic sulphur");
		m(ROCK_GOLEM, "Silver", 40, 741600);
		m(ROCK_GOLEM, "Lead", 40.5, 741600);
		m(ROCK_GOLEM, "Barronite", 16, 741600);
		m(ROCK_GOLEM, "Barronite deposit", 32, 741600);
		m(ROCK_GOLEM, "Calcified rock", 36, 741600);
		m(ROCK_GOLEM, "Calcified rock", 33, 741600);
		m(ROCK_GOLEM, "Sandstone", 30, 741600);
		m(ROCK_GOLEM, "Sandstone", 50, 741600, "Sandstone (5kg)");
		m(ROCK_GOLEM, "Granite", 50, 741600, "Granite (500g)");
		m(ROCK_GOLEM, "Coal", 50, 290640, "Coal");
		m(ROCK_GOLEM, "Sandstone", 60, 741600, "Sandstone (10kg)");
		m(ROCK_GOLEM, "Granite", 60, 741600, "Granite (2kg)");
		m(ROCK_GOLEM, "Motherlode pay-dirt", 60, 247200, "Pay-dirt");
		m(ROCK_GOLEM, "Lovakite", 60, 245562, "Lovakite ore");
		m(ROCK_GOLEM, "Granite (5kg)", 75, 741600);
		m(ROCK_GOLEM, "Gold", 65, 296640, "Gold ore");
		m(ROCK_GOLEM, "Gem rock", 65, 211886, "Uncut");
		m(ROCK_GOLEM, "Ash pile", 10, 741600);
		m(ROCK_GOLEM, "Mithril", 80, 148320);
		m(ROCK_GOLEM, "Nickel", 80.5, 247200);
		m(ROCK_GOLEM, "Adamantite", 95, 59328);
		m(ROCK_GOLEM, "Runite", 125, 42377);
		m(ROCK_GOLEM, "Amethyst", 240, 46350);
		m(ROCK_GOLEM, "Blast mine", 20, 123600);
		m(ROCK_GOLEM, "Blast mine (prospector)", 20.5, 123600);
		m(ROCK_GOLEM, "Sunstone", 15, 741600);
		m(ROCK_GOLEM, "Crashed star", 32, 521550);
		m(ROCK_GOLEM, "Crashed star (F2P)", 16, 741600);

		// ---- Fishing: per catch (minnows / karambwanji / trawling: per xp drop) ----
		m(HERON, "Shrimps", 10, 870330);
		m(HERON, "Anchovies", 40, 870330, "Raw anchovies");
		m(HERON, "Common tench", 40, 636833, "Common tench");
		m(HERON, "Karambwanji", 5, 443697);
		m(HERON, "Sardine", 20, 1056000, "Raw sardine");
		m(HERON, "Mackerel", 20, 1147827, "Raw mackerel");
		m(HERON, "Herring", 30, 1056000);
		m(HERON, "Cod", 45, 1147827);
		m(HERON, "Bass", 100, 1147827, "Raw bass");
		m(HERON, "Swordfish", 100, 257770, "Raw swordfish");
		m(HERON, "Trout", 50, 923616, "Raw trout");
		m(HERON, "Leaping trout", 50, 1280862, "Leaping trout");
		m(HERON, "Karambwan", 50, 170874, "Raw karambwan");
		m(HERON, "Salmon", 70, 923616, "Raw salmon");
		m(HERON, "Leaping salmon", 70, 1280862, "Leaping salmon");
		m(HERON, "Pike", 60, 305792);
		m(HERON, "Rainbow fish", 80, 137739, "Raw rainbow fish");
		m(HERON, "Tuna", 80, 257770, "Raw tuna");
		m(HERON, "Cave eel", 80, 257770, "Raw cave eel");
		m(HERON, "Leaping sturgeon", 80, 1280862, "Leaping sturgeon");
		m(HERON, "Lobster", 90, 116129);
		m(HERON, "Bluegill", 11.5, 636833);
		m(HERON, "Mottled eel", 65, 636833);
		m(HERON, "Monkfish", 120, 138583, "Raw monkfish");
		m(HERON, "Anglerfish", 120, 78649, "Raw anglerfish");
		m(HERON, "Shark", 110, 82243);
		m(HERON, "Infernal eel", 95, 165000);
		m(HERON, "Anglerfish (diabolic worms)", 79.2, 78649);
		m(HERON, "Minnow", 26.1, 977778);
		m(HERON, "Dark crab", 130, 149434);
		m(HERON, "Sacred eel", 105, 99000);
		m(HERON, "Guppy", 8, 820330);
		m(HERON, "Cavefish", 16, 300792);
		m(HERON, "Tetra", 24, 257770);
		m(HERON, "Catfish", 33, 152120);
		m(HERON, "Swordtip squid", 55, 257770);
		m(HERON, "Jumbo squid", 75, 257770);
		m(HERON, "Giant krill", 22.5, 257770);
		m(HERON, "Giant krill (trawling)", 112.5, 257770);
		m(HERON, "Haddock", 25.7, 247770);
		m(HERON, "Haddock (trawling)", 128.5, 247770);
		m(HERON, "Yellowfin", 31.1, 237770);
		m(HERON, "Yellowfin (trawling)", 155.5, 237770);
		m(HERON, "Halibut", 39.1, 227770);
		m(HERON, "Halibut (trawling)", 195.5, 227770);
		m(HERON, "Bluefin", 44.1, 217770);
		m(HERON, "Bluefin (trawling)", 220.5, 217770);
		m(HERON, "Marlin", 53.1, 207770);
		m(HERON, "Marlin (trawling)", 265.5, 207770);
		m(HERON, "Leechfin", 33.2, 1847827);

		// ---- Thieving: per successful pickpocket / stall / chest (failures give no xp) ----
		m(ROCKY, "Man / Woman / Villager", 8, 257211);
		m(ROCKY, "Farmer", 14.5, 257211);
		m(ROCKY, "H.A.M. member", 22.2, 257211);
		m(ROCKY, "Warrior", 26, 257211);
		m(ROCKY, "Rogue", 36.5, 257211);
		m(ROCKY, "Cave goblin", 40, 257211, "Cave goblin");
		m(ROCKY, "Master farmer", 43, 257211);
		m(ROCKY, "Guard", 46.8, 257211);
		m(ROCKY, "Fremennik citizen / Bandit", 65, 257211);
		m(ROCKY, "Pirate", 72, 257211);
		m(ROCKY, "Bandit", 79.4, 257211);
		m(ROCKY, "Knight of Ardougne", 84.3, 257211);
		m(ROCKY, "Wealthy citizen", 96, 257211);
		m(ROCKY, "Menaphite thug", 137.5, 257211, "Menaphite Thug");
		m(ROCKY, "Watchman", 137.5, 134625, "Watchman");
		m(ROCKY, "Paladin", 131.8, 127056);
		m(ROCKY, "Gnome", 133.3, 108718);
		m(ROCKY, "Hero", 163.3, 99175);
		m(ROCKY, "Elf", 353.3, 99175);
		m(ROCKY, "Vyre", 306.9, 99175);
		m(ROCKY, "TzHaar-Hur", 103.4, 176743);
		m(ROCKY, "Stealing valuables", 45, 206777);
		m(ROCKY, "Fur stall", 45, 36490, "Fur stall");
		m(ROCKY, "Stealing valuables (shiny)", 630, 206777);
		m(ROCKY, "Veg stall", 10, 206777, "Veg stall");
		m(ROCKY, "Seed stall", 10, 36490, "Seed stall");
		m(ROCKY, "Veg stall (Port Roberts)", 5, 206777);
		m(ROCKY, "Bakery stall", 16, 124066, "Baker's stall");
		m(ROCKY, "Tea stall", 16, 68926, "Tea stall");
		m(ROCKY, "Food stall", 16, 47718, "Food stall");
		m(ROCKY, "Fruit stall", 28.5, 124066);
		m(ROCKY, "Crafting stall", 20, 47718);
		m(ROCKY, "Silk stall", 24, 68926);
		m(ROCKY, "Wine stall", 27, 36490);
		m(ROCKY, "Fur stall (Port Roberts)", 38.5, 36490);
		m(ROCKY, "Fish stall", 42, 36490);
		m(ROCKY, "Fish stall (Port Roberts)", 49, 36490);
		m(ROCKY, "Crossbow stall", 52, 36490);
		m(ROCKY, "Silver stall", 205, 36490);
		m(ROCKY, "Silver stall (Port Roberts)", 80, 36490);
		m(ROCKY, "Magic stall", 90, 36490);
		m(ROCKY, "Scimitar stall", 210, 36490);
		m(ROCKY, "Spice stall", 92, 36490);
		m(ROCKY, "Spice stall (Port Roberts)", 110, 36490);
		m(ROCKY, "Gem stall", 408, 36490);
		m(ROCKY, "Gem stall (Port Roberts)", 129.5, 36490);
		m(ROCKY, "Ore stall", 350, 36490);
		m(ROCKY, "Ore stall (Port Roberts)", 191, 36490);
		m(ROCKY, "Cannonball stall", 223, 36490);
		m(ROCKY, "Grand Gold Chest 1", 40, 41355, "Grand Gold Chest");
		m(ROCKY, "Grand Gold Chest 2", 60, 29540);
		m(ROCKY, "Grand Gold Chest 3", 100, 25847);
		m(ROCKY, "Grand Gold Chest 4", 140, 20678);
		m(ROCKY, "Grand Gold Chest 5", 200, 20678);
		m(ROCKY, "Grand Gold Chest 6", 300, 20678);
		m(ROCKY, "Grand Gold Chest 7", 450, 10339);
		m(ROCKY, "Grand Gold Chest 8", 550, 6893);

		// ---- Farming: check-health xp is unique per tree, so it is the safe trigger. Allotment,
		// herb and hop harvests give the same xp on every pull but only the LAST one rolls, so
		// they are left out rather than over-counted. Farmer's outfit (+2.5%) handled in match(). ----
		m(TANGLEROOT, "Oak tree", 467.3, 22483);
		m(TANGLEROOT, "Willow tree", 1456.5, 16059);
		m(TANGLEROOT, "Maple tree", 3403.4, 14052);
		m(TANGLEROOT, "Yew tree", 7069.9, 11242);
		m(TANGLEROOT, "Magic tree", 13768.3, 9368);
		m(TANGLEROOT, "Apple tree", 1199.5, 9000);
		m(TANGLEROOT, "Banana tree", 1750.5, 9000);
		m(TANGLEROOT, "Orange tree", 2470.2, 9000);
		m(TANGLEROOT, "Curry tree", 2906.9, 9000);
		m(TANGLEROOT, "Pineapple plant", 4605, 9000);
		m(TANGLEROOT, "Papaya tree", 6146.4, 9000);
		m(TANGLEROOT, "Palm tree", 10150.1, 9000);
		m(TANGLEROOT, "Dragonfruit tree", 17335, 9000);
		m(TANGLEROOT, "Teak tree", 7290, 5000);
		m(TANGLEROOT, "Mahogany tree", 15720, 5000);
		m(TANGLEROOT, "Camphor tree", 17840, 5000);
		m(TANGLEROOT, "Ironwood tree", 20380, 5000);
		m(TANGLEROOT, "Rosewood tree", 23100, 5000);
		m(TANGLEROOT, "Calquat tree", 12096, 6000);
		m(TANGLEROOT, "Crystal tree", 13240, 9000);
		m(TANGLEROOT, "Celastrus tree", 14130, 9000);
		m(TANGLEROOT, "Spirit tree", 19301.8, 5000);
		m(TANGLEROOT, "Redwood tree", 22450, 5000);
		m(TANGLEROOT, "Cactus", 374, 7000);
		m(TANGLEROOT, "Potato cactus", 230, 160594);
		m(TANGLEROOT, "Redberry bush", 64, 44966);
		m(TANGLEROOT, "Cadavaberry bush", 102.5, 37472);
		m(TANGLEROOT, "Dwellberry bush", 177.5, 32119);
		m(TANGLEROOT, "Jangerberry bush", 284.5, 28104);
		m(TANGLEROOT, "Whiteberry bush", 437.5, 28104);
		m(TANGLEROOT, "Poison ivy bush", 675, 28104);
		m(TANGLEROOT, "Belladonna", 512, 8000);
		m(TANGLEROOT, "Grapevine", 625, 385426);
		m(TANGLEROOT, "Hespori", 12600, 7000);

		// ---- Sailing (Soup): flat per-action rates, no level term ----
		flat(SOUP, "Salvage sorting", 5.5, 800000);
		flat(SOUP, "Salvage sorting", 9, 800000);
		flat(SOUP, "Salvage sorting", 15.5, 800000);
		flat(SOUP, "Salvage sorting", 24, 800000);
		flat(SOUP, "Salvage sorting", 31.5, 800000);
		flat(SOUP, "Salvage sorting", 63.5, 800000);
		flat(SOUP, "Salvage sorting", 75, 800000);
		flat(SOUP, "Salvage sorting", 95, 800000);
		flat(SOUP, "Small salvage", 10, 800000);
		flat(SOUP, "Fisherman's salvage", 17, 500000);
		flat(SOUP, "Barracuda salvage", 31, 300000);
		flat(SOUP, "Large salvage", 48, 280000);
		flat(SOUP, "Pirate salvage", 76, 275000);
		flat(SOUP, "Mercenary salvage", 138, 260000);
		flat(SOUP, "Fremennik salvage", 162, 230000);
		flat(SOUP, "Merchant salvage", 200, 160000);
		flat(SOUP, "Deep sea trawling", 7, 360000);
		flat(SOUP, "Deep sea trawling", 11, 360000);
		flat(SOUP, "Deep sea trawling", 15, 360000);
		flat(SOUP, "Sea charting", 35, 30000);
		flat(SOUP, "Sea charting", 50, 30000);
		flat(SOUP, "Sea charting", 125, 30000);
		flat(SOUP, "Sea charting", 175, 30000);
		flat(SOUP, "Sea charting", 250, 30000);
		flat(SOUP, "Tempor Tantrum (sword)", 385, 16000);
		flat(SOUP, "Tempor Tantrum (shark)", 650, 8000);
		flat(SOUP, "Tempor Tantrum (marlin)", 1250, 5334);
		flat(SOUP, "Jubbly Jive (sword)", 1700, 11500);
		flat(SOUP, "Jubbly Jive (shark)", 3000, 5750);
		flat(SOUP, "Jubbly Jive (marlin)", 6200, 3834);
		flat(SOUP, "Gwenith Glide (sword)", 3050, 9000);
		flat(SOUP, "Gwenith Glide (shark)", 7250, 4500);
		flat(SOUP, "Gwenith Glide (marlin)", 16050, 3000);

		// ---- Agility: region + lap-end tiles (same detection RuneLite's Agility plugin uses) ----
		COURSES.add(new Course("Gnome Stronghold", 9781, 35609, new WorldPoint(2484, 3437, 0), new WorldPoint(2487, 3437, 0)));
		COURSES.add(new Course("Shayzien basic", 6200, 31804, new WorldPoint(1554, 3640, 0)));
		COURSES.add(new Course("Draynor rooftop", 12338, 33005, new WorldPoint(3103, 3261, 0)));
		COURSES.add(new Course("Al Kharid rooftop", 13105, 26648, new WorldPoint(3299, 3194, 0)));
		COURSES.add(new Course("Agility Pyramid", 13356, 9901, new WorldPoint(3364, 2830, 0)));
		COURSES.add(new Course("Varrock rooftop", 12853, 24410, new WorldPoint(3236, 3417, 0)));
		COURSES.add(new Course("Penguin", 10559, 9779, new WorldPoint(2652, 4039, 1)));
		COURSES.add(new Course("Barbarian Outpost", 10039, 44376, new WorldPoint(2543, 3553, 0)));
		COURSES.add(new Course("Canifis rooftop", 13878, 36842, new WorldPoint(3510, 3485, 0)));
		COURSES.add(new Course("Ape Atoll", 11050, 37720, new WorldPoint(2770, 2747, 0)));
		COURSES.add(new Course("Shayzien advanced", 5944, 29738, new WorldPoint(1522, 3625, 0)));
		COURSES.add(new Course("Falador rooftop", 12084, 26806, new WorldPoint(3029, 3332, 0),
			new WorldPoint(3029, 3333, 0), new WorldPoint(3029, 3334, 0), new WorldPoint(3029, 3335, 0)));
		COURSES.add(new Course("Wilderness", 11837, 34666, new WorldPoint(2993, 3933, 0),
			new WorldPoint(2994, 3933, 0), new WorldPoint(2995, 3933, 0)));
		COURSES.add(new Course("Werewolf", 14234, 32597, new WorldPoint(3528, 9873, 0)));
		COURSES.add(new Course("Seers' Village rooftop", 10806, 35205, new WorldPoint(2704, 3464, 0)));
		COURSES.add(new Course("Pollnivneach rooftop", 13358, 33422, new WorldPoint(3363, 2998, 0)));
		COURSES.add(new Course("Rellekka rooftop", 10553, 31063, new WorldPoint(2653, 3676, 0)));
		COURSES.add(new Course("Prifddinas", 12895, 25146, new WorldPoint(3240, 6109, 0)));
		COURSES.add(new Course("Ardougne rooftop", 10547, 34440, new WorldPoint(2668, 3297, 0)));
	}

	static Pet petFor(Skill s)
	{
		for (Pet p : PETS)
		{
			if (p.skill == s)
			{
				return p;
			}
		}
		return null;
	}

	static Pet byKey(String key)
	{
		for (Pet p : PETS)
		{
			if (p.key.equals(key))
			{
				return p;
			}
		}
		return null;
	}

	/**
	 * The rolling action a whole-xp drop in this skill most likely was, or null when no
	 * rolling action gives that xp. When several actions share the xp, the items that landed
	 * in the inventory that tick (or the Thieving target) pick the right one; with no usable
	 * hint the rarest rate wins so the odds are never overstated.
	 *
	 * @param hints lower-case item names gained this tick, plus the last Thieving target name
	 */
	static Method match(Skill skill, int deltaXp, java.util.Collection<String> hints)
	{
		List<Method> list = METHODS.get(skill);
		if (list == null || deltaXp <= 0)
		{
			return null;
		}
		List<Method> cands = new ArrayList<>();
		for (Method m : list)
		{
			boolean hit = m.matches(deltaXp);
			if (!hit && skill == Skill.FARMING)
			{
				// farmer's outfit scales every farming drop by 2.5%
				double scaled = m.xp * 1.025;
				hit = deltaXp == (int) Math.floor(scaled) || deltaXp == (int) Math.ceil(scaled);
			}
			if (hit)
			{
				cands.add(m);
			}
		}
		if (cands.isEmpty())
		{
			return null;
		}
		if (cands.size() > 1 && hints != null && !hints.isEmpty())
		{
			for (Method m : cands)
			{
				if (m.hint == null)
				{
					continue;
				}
				for (String h : hints)
				{
					if (h != null && h.toLowerCase().startsWith(m.hint))
					{
						return m;
					}
				}
			}
		}
		Method best = null;
		for (Method m : cands)
		{
			if (best == null || m.base > best.base)
			{
				best = m;
			}
		}
		return best;
	}

	static Method match(Skill skill, int deltaXp)
	{
		return match(skill, deltaXp, null);
	}

	static Course courseAt(int regionId)
	{
		for (Course c : COURSES)
		{
			if (c.regionId == regionId)
			{
				return c;
			}
		}
		return null;
	}

	/** Chance of the pet on one roll: flat 1/x, or 1/(B - 25*level) with level capped at 99. */
	static double chance(double base, boolean flat, int baseLevel, boolean has200m)
	{
		double denom = flat ? base : base - 25.0 * Math.min(99, Math.max(1, baseLevel));
		if (denom < 1)
		{
			denom = 1;
		}
		double p = 1.0 / denom;
		if (has200m && !flat)
		{
			p *= 15;
		}
		return Math.min(1.0, p);
	}
}
