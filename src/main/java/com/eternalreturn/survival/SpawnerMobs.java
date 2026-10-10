package com.eternalreturn.survival;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.MobEntity;

/**
 * Mobs from monster spawners drop no items when they die (survival.spawnerMobsDropNothing): no loot, and
 * none of the gear they spawned with. They still drop experience, and anything they picked up (a player's
 * lost sword, say) as usual. A mob is marked as it leaves the spawner, riders included.
 */
public final class SpawnerMobs {
	/** What a mob keeps (MobEntitySurvivalMixin). */
	public interface Holder {
		boolean eternalreturn$fromSpawner();

		void eternalreturn$setFromSpawner(boolean fromSpawner);
	}

	private SpawnerMobs() {
	}

	/** From the spawner, just before the mob (and any riders) go into the world. */
	public static void mark(Entity entity) {
		if (!EternalReturnConfig.get().survival.spawnerMobsDropNothing) {
			return;
		}
		for (Entity part : entity.streamSelfAndPassengers().toList()) {
			if (part instanceof MobEntity mob) {
				((Holder) mob).eternalreturn$setFromSpawner(true);
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					mob.setEquipmentDropChance(slot, 0.0F);
				}
			}
		}
	}

	public static boolean dropsNothing(Entity entity) {
		return entity instanceof Holder holder && holder.eternalreturn$fromSpawner() && EternalReturnConfig.get().survival.spawnerMobsDropNothing;
	}
}
