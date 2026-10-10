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
		armZombies(entity);
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

	/**
	 * Zombies from spawners (dungeons) always hold a pickaxe, stone or iron, at any depth, so they dig
	 * toward players they can't reach (MineTowardTargetGoal; mobs.spawnerZombiesDig).
	 */
	static void armZombies(Entity entity) {
		if (!EternalReturnConfig.get().mobs.spawnerZombiesDig) {
			return;
		}
		for (Entity part : entity.streamSelfAndPassengers().toList()) {
			if (part instanceof net.minecraft.entity.mob.ZombieEntity zombie) {
				zombie.equipStack(EquipmentSlot.MAINHAND, new net.minecraft.item.ItemStack(zombie.getRandom().nextBoolean()
						? net.minecraft.item.Items.IRON_PICKAXE : net.minecraft.item.Items.STONE_PICKAXE));
			}
		}
	}

	public static boolean dropsNothing(Entity entity) {
		return entity instanceof Holder holder && holder.eternalreturn$fromSpawner() && EternalReturnConfig.get().survival.spawnerMobsDropNothing;
	}
}
