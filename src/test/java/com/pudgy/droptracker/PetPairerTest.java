package com.pudgy.droptracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PetPairerTest
{
	private static final BossRegistry.Boss GOTR = BossRegistry.byLootName("Guardians of the Rift");
	private static final BossRegistry.Boss ZULRAH = BossRegistry.byLootName("Zulrah");

	@Test
	public void recognisesTheThreeGameMessages()
	{
		assertTrue(PetPairer.isPetMessage("You have a funny feeling like you're being followed."));
		assertTrue(PetPairer.isPetMessage("You feel something weird sneaking into your backpack."));
		assertTrue(PetPairer.isPetMessage("You have a funny feeling like you would have been followed..."));
		assertFalse(PetPairer.isPetMessage("You have a funny feeling about this."));
		assertFalse(PetPairer.isPetMessage("Your Zulrah kill count is: 500."));
		assertFalse(PetPairer.isPetMessage(null));
	}

	@Test
	public void lootThenPetMessagePairs()
	{
		PetPairer p = new PetPairer();
		assertNull(p.onLoot(GOTR, 100));
		assertEquals(GOTR, p.onPetMessage(103));
	}

	@Test
	public void petMessageThenLootPairs()
	{
		PetPairer p = new PetPairer();
		assertNull(p.onPetMessage(100));
		assertEquals(ZULRAH, p.onLoot(ZULRAH, 101));
	}

	@Test
	public void outsideWindowDoesNotPair()
	{
		PetPairer p = new PetPairer();
		assertNull(p.onLoot(GOTR, 100));
		assertNull(p.onPetMessage(100 + PetPairer.WINDOW_TICKS + 1));

		PetPairer q = new PetPairer();
		assertNull(q.onPetMessage(100));
		assertNull(q.onLoot(GOTR, 100 + PetPairer.WINDOW_TICKS + 1));
	}

	@Test
	public void eachPairingIsConsumedOnce()
	{
		PetPairer p = new PetPairer();
		p.onLoot(GOTR, 100);
		assertEquals(GOTR, p.onPetMessage(101));
		assertNull("second message must not double-count", p.onPetMessage(102));

		PetPairer q = new PetPairer();
		q.onPetMessage(100);
		assertEquals(ZULRAH, q.onLoot(ZULRAH, 101));
		assertNull("next kill must not re-record the pet", q.onLoot(ZULRAH, 102));
	}

	@Test
	public void petPairsWithMostRecentBoss()
	{
		PetPairer p = new PetPairer();
		p.onLoot(ZULRAH, 50);
		p.onLoot(GOTR, 100);
		assertEquals(GOTR, p.onPetMessage(102));
	}

	@Test
	public void everyRegisteredPetBossHasExactlyOnePet()
	{
		for (BossRegistry.Boss b : BossRegistry.all())
		{
			int pets = 0;
			for (BossRegistry.Drop d : b.drops)
			{
				if (d.pet)
				{
					pets++;
				}
			}
			assertTrue(b.display + " has " + pets + " pets flagged", pets <= 1);
		}
	}
}
