package com.eternalreturn.fishing;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import com.eternalreturn.mobs.OceanSpawns;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.DrownedEntity;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Hostile catches (fishing.hostileCatchChance and fishing.hostileCatches in the config): now and then a
 * bite in water is a monster rather than items. It comes up at the hook with a splash and goes for the
 * angler. A drowned is yanked out toward the angler like a catch; guardians stay in the water. The
 * jockeys are a drowned with a trident riding a guardian or an elder guardian, which swims on its own
 * while the drowned throws. Elder guardians caught this way despawn like other monsters (the ones in
 * ocean monuments stay put).
 */
public final class HostileCatches {
	private static final String JOCKEY_PREFIX = "drowned_";
	private static final String JOCKEY_SUFFIX = "_jockey";

	private HostileCatches() {
	}

	/**
	 * Rolls for a hostile catch at the hook (Slayer on the rod adds to the chance) and, if one bites and
	 * there is room for it, spawns it.
	 * Returns whether a mob was caught (then no items come up).
	 */
	public static boolean tryCatch(ServerWorld world, FishingBobberEntity hook, PlayerEntity angler, ItemStack rod, Random random) {
		EternalReturnConfig.FishingTweaks cfg = EternalReturnConfig.get().fishing;
		if (world.getDifficulty() == Difficulty.PEACEFUL || random.nextDouble() >= Slayer.hostileCatchChance(rod, cfg.hostileCatchChance, random)) {
			return false;
		}
		String id = pick(cfg.hostileCatches, world.getBiome(hook.getBlockPos()).isIn(OceanSpawns.OCEAN_WATERS), random);
		return id != null && spawn(world, hook, angler, id) != null;
	}

	/** A weighted pick among the catches allowed here, or null if none is. */
	@Nullable
	static String pick(Map<String, EternalReturnConfig.HostileCatch> catches, boolean ocean, Random random) {
		int total = 0;
		for (EternalReturnConfig.HostileCatch entry : catches.values()) {
			if (entry.weight > 0 && (ocean || !entry.oceanOnly)) {
				total += entry.weight;
			}
		}
		if (total <= 0) {
			return null;
		}
		int roll = random.nextInt(total);
		for (Map.Entry<String, EternalReturnConfig.HostileCatch> entry : catches.entrySet()) {
			EternalReturnConfig.HostileCatch value = entry.getValue();
			if (value.weight > 0 && (ocean || !value.oceanOnly)) {
				roll -= value.weight;
				if (roll < 0) {
					return entry.getKey();
				}
			}
		}
		return null;
	}

	/**
	 * Spawns the catch named id at the hook, aimed at the angler: a mob id ("drowned", "guardian",
	 * "minecraft:elder_guardian") or "drowned_<mount>_jockey". Returns the mob (the mount, for a
	 * jockey), or null if it is unknown or doesn't fit there.
	 */
	@Nullable
	public static MobEntity spawn(ServerWorld world, FishingBobberEntity hook, PlayerEntity angler, String id) {
		boolean jockey = id.startsWith(JOCKEY_PREFIX) && id.endsWith(JOCKEY_SUFFIX) && id.length() > JOCKEY_PREFIX.length() + JOCKEY_SUFFIX.length();
		String mobId = jockey ? id.substring(JOCKEY_PREFIX.length(), id.length() - JOCKEY_SUFFIX.length()) : id;
		MobEntity mob = create(world, mobId);
		if (mob == null) {
			return null;
		}
		Vec3d at = hook.getPos();
		double dx = angler.getX() - at.x;
		double dz = angler.getZ() - at.z;
		float yaw = (float) (MathHelper.atan2(dz, dx) * 180.0 / Math.PI) - 90.0F;
		boolean yanked = mob instanceof DrownedEntity && !jockey;
		// Guardians come up with their middle at the hook; a drowned with its head above the water.
		double y = yanked ? at.y - 1.0 : at.y - mob.getHeight() * 0.5;
		DrownedEntity rider = jockey ? (DrownedEntity) create(world, "drowned") : null;
		if (jockey && rider == null) {
			return null;
		}
		double room = rider == null ? 0.0 : rider.getHeight();
		// In shallow water there may be no room that low: rise half a block at a time, up to the hook.
		boolean fits = false;
		for (double tryY = y; !fits && tryY <= at.y; tryY += 0.5) {
			y = tryY;
			mob.refreshPositionAndAngles(at.x, y, at.z, yaw, 0.0F);
			fits = world.isSpaceEmpty(mob, mob.getBoundingBox().stretch(0.0, room, 0.0));
		}
		if (!fits) {
			return null;
		}
		initialize(world, mob);
		if (rider != null) {
			rider.refreshPositionAndAngles(at.x, y + mob.getHeight(), at.z, yaw, 0.0F);
			initialize(world, rider);
			rider.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.TRIDENT));
			if (!rider.startRiding(mob, true)) {
				return null;
			}
			rider.setTarget(angler);
		}
		mob.setTarget(angler);
		if (yanked) {
			double dy = angler.getY() - at.y;
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			mob.setVelocity(dx * 0.12, dy * 0.12 + Math.sqrt(distance) * 0.12, dz * 0.12);
		} else {
			mob.setVelocity(0.0, 0.25, 0.0);
		}
		world.spawnEntityAndPassengers(mob);
		world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 1.0F, 0.8F + world.random.nextFloat() * 0.4F);
		world.spawnParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 30, mob.getWidth() * 0.5, 0.2, mob.getWidth() * 0.5, 0.1);
		return mob;
	}

	@Nullable
	private static MobEntity create(ServerWorld world, String id) {
		Identifier key = Identifier.tryParse(id);
		if (key == null || !Registries.ENTITY_TYPE.containsId(key)) {
			return null;
		}
		EntityType<?> type = Registries.ENTITY_TYPE.get(key);
		return type.create(world) instanceof MobEntity mob ? mob : null;
	}

	private static void initialize(ServerWorld world, MobEntity mob) {
		// Zombie kinds: no chicken jockeys out of the water.
		ZombieEntity.ZombieData data = mob instanceof ZombieEntity ? new ZombieEntity.ZombieData(ZombieEntity.shouldBeBaby(world.random), false) : null;
		mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.EVENT, data);
		if (mob instanceof ElderGuardianEntity) {
			((MobEntityAccessor) mob).eternalreturn$setPersistent(false);
		}
	}
}
