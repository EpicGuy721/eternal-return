package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.entity.ZombieHookEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModEntities {
	/** Never saved: a hook only lives for a second or two, and an orphaned one would just be discarded on load. */
	public static final EntityType<ZombieHookEntity> ZOMBIE_HOOK = Registry.register(
			Registries.ENTITY_TYPE,
			EternalReturn.id("zombie_hook"),
			EntityType.Builder.<ZombieHookEntity>create(ZombieHookEntity::new, SpawnGroup.MISC)
					.dimensions(0.25F, 0.25F)
					.maxTrackingRange(4)
					.trackingTickInterval(5)
					.disableSaving()
					.disableSummon()
					.build(EternalReturn.id("zombie_hook").toString()));

	private ModEntities() {
	}

	/** Forces the static registrations above to run during mod init. */
	public static void register() {
	}
}
