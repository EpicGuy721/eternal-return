package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.entity.WebEntity;
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

	/** A ball of web a spider flings (WebEntity). */
	public static final EntityType<WebEntity> WEB = Registry.register(
			Registries.ENTITY_TYPE,
			EternalReturn.id("web"),
			EntityType.Builder.<WebEntity>create(WebEntity::new, SpawnGroup.MISC)
					.dimensions(0.3F, 0.3F)
					.maxTrackingRange(4)
					.trackingTickInterval(10)
					.build(EternalReturn.id("web").toString()));

	/** A thrown stick of dynamite (Dynamite.Lit). */
	public static final EntityType<com.eternalreturn.items.Dynamite.Lit> DYNAMITE = Registry.register(
			Registries.ENTITY_TYPE,
			EternalReturn.id("dynamite"),
			EntityType.Builder.<com.eternalreturn.items.Dynamite.Lit>create(com.eternalreturn.items.Dynamite.Lit::new, SpawnGroup.MISC)
					.dimensions(0.25F, 0.25F)
					.maxTrackingRange(4)
					.trackingTickInterval(10)
					.build(EternalReturn.id("dynamite").toString()));

	/** The autumnal forest's vampire (VampireEntity). */
	public static final EntityType<com.eternalreturn.mobs.entity.VampireEntity> VAMPIRE = Registry.register(
			Registries.ENTITY_TYPE,
			EternalReturn.id("vampire"),
			EntityType.Builder.<com.eternalreturn.mobs.entity.VampireEntity>create(com.eternalreturn.mobs.entity.VampireEntity::new, SpawnGroup.MONSTER)
					.dimensions(0.6F, 1.95F)
					.maxTrackingRange(8)
					.build(EternalReturn.id("vampire").toString()));

	/** A vampire's bat, or a vampire in bat form (VampireBatEntity). */
	public static final EntityType<com.eternalreturn.mobs.entity.VampireBatEntity> VAMPIRE_BAT = Registry.register(
			Registries.ENTITY_TYPE,
			EternalReturn.id("vampire_bat"),
			EntityType.Builder.<com.eternalreturn.mobs.entity.VampireBatEntity>create(com.eternalreturn.mobs.entity.VampireBatEntity::new, SpawnGroup.MISC)
					.dimensions(0.5F, 0.9F)
					.maxTrackingRange(5)
					.build(EternalReturn.id("vampire_bat").toString()));

	public static final EntityType<com.eternalreturn.mobs.entity.BearEntity> BROWN_BEAR = bear("brown_bear", 1.4F, 1.4F);
	public static final EntityType<com.eternalreturn.mobs.entity.BearEntity> BLACK_BEAR = bear("black_bear", 1.25F, 1.25F);
	public static final EntityType<com.eternalreturn.mobs.entity.BearEntity> SUN_BEAR = bear("sun_bear", 1.05F, 1.05F);

	private static EntityType<com.eternalreturn.mobs.entity.BearEntity> bear(String id, float width, float height) {
		return Registry.register(Registries.ENTITY_TYPE, EternalReturn.id(id),
				EntityType.Builder.<com.eternalreturn.mobs.entity.BearEntity>create(com.eternalreturn.mobs.entity.BearEntity::new, SpawnGroup.CREATURE)
						.dimensions(width, height)
						.maxTrackingRange(10)
						.build(EternalReturn.id(id).toString()));
	}

	private ModEntities() {
	}

	/** Forces the static registrations above to run during mod init. */
	public static void register() {
	}
}
