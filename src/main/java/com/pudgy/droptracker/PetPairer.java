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

/**
 * Pets never appear in loot events — the game only announces them in chat. This pairs a
 * pet chat message with the boss whose loot arrived within a few game ticks, in either
 * order. Each pairing is consumed so one message can't count twice.
 */
final class PetPairer
{
	static final int WINDOW_TICKS = 10;
	private static final int NONE = Integer.MIN_VALUE / 2;

	static boolean isPetMessage(String msg)
	{
		return msg != null
			&& (msg.startsWith("You have a funny feeling like you")
			|| msg.startsWith("You feel something weird sneaking"));
	}

	private int petTick = NONE;
	private int lootTick = NONE;
	private BossRegistry.Boss lootBoss;

	/** Loot arrived for {@code boss}. Returns the boss if a pending pet message pairs with it. */
	BossRegistry.Boss onLoot(BossRegistry.Boss boss, int tick)
	{
		lootBoss = boss;
		lootTick = tick;
		if (tick - petTick <= WINDOW_TICKS)
		{
			petTick = NONE;
			lootTick = NONE;
			return boss;
		}
		return null;
	}

	/** A pet message arrived. Returns the boss if recent loot pairs with it, else waits for loot. */
	BossRegistry.Boss onPetMessage(int tick)
	{
		if (lootBoss != null && tick - lootTick <= WINDOW_TICKS)
		{
			lootTick = NONE;
			return lootBoss;
		}
		petTick = tick;
		return null;
	}
}
