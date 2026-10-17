package com.eternalreturn.mobs;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.entity.VampireBatEntity;
import com.eternalreturn.mobs.entity.VampireEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Vampires (VampireEntity, VampireBatEntity): their bats, their bat form, and the bite that turns a villager into a
 * vampire (mobs.vampireTurnSeconds, 30, after the bite; the villager keeps its name). A turning villager gives off
 * red specks. The villager's countdown is saved with it (VillagerVampireMixin).
 */
public final class Vampires {
	public static final int MAX_BATS = 4;
	private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.6F, 0.05F, 0.05F), 1.0F);

	/** What a villager keeps: ticks until it turns (0: not bitten). */
	public interface Bitten {
		int eternalreturn$turnTicks();

		void eternalreturn$setTurnTicks(int ticks);
	}

	private Vampires() {
	}

	public static boolean isBitten(VillagerEntity villager) {
		return ((Bitten) villager).eternalreturn$turnTicks() > 0;
	}

	/** Whether a vampire (or its bats) still goes after this: not a villager that is already turning. */
	public static boolean biteable(LivingEntity entity) {
		return !(entity instanceof VillagerEntity villager && isBitten(villager));
	}

	/** A bite on a villager: it starts turning, unless it already is. True if this bite did it. */
	public static boolean infect(VillagerEntity villager) {
		if (isBitten(villager) || villager.getWorld().isClient || !villager.isAlive()) {
			return false;
		}
		((Bitten) villager).eternalreturn$setTurnTicks(Math.max(1, EternalReturnConfig.get().mobs.vampireTurnSeconds * 20));
		villager.getWorld().playSound(null, villager.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_VILLAGER_CURE, SoundCategory.HOSTILE, 0.5F, 0.6F);
		return true;
	}

	/** Every tick of a bitten villager: red specks, and at the end of the count, a vampire in its place. */
	public static void tickBitten(VillagerEntity villager) {
		Bitten bitten = (Bitten) villager;
		int ticks = bitten.eternalreturn$turnTicks();
		if (ticks <= 0 || !(villager.getWorld() instanceof ServerWorld world) || !villager.isAlive()) {
			return;
		}
		if (villager.age % 10 == 0) {
			world.spawnParticles(BLOOD, villager.getX(), villager.getBodyY(0.6), villager.getZ(), 2, 0.3, 0.4, 0.3, 0.0);
		}
		bitten.eternalreturn$setTurnTicks(ticks - 1);
		if (ticks - 1 == 0) {
			VampireEntity vampire = villager.convertTo(ModEntities.VAMPIRE, false);
			if (vampire != null) {
				vampire.initialize(world, world.getLocalDifficulty(vampire.getBlockPos()), SpawnReason.CONVERSION, null);
				world.spawnParticles(ParticleTypes.LARGE_SMOKE, vampire.getX(), vampire.getBodyY(0.5), vampire.getZ(), 20, 0.3, 0.6, 0.3, 0.02);
				world.playSound(null, vampire.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 1.0F, 0.6F);
			}
		}
	}

	/** The nearest player within range who can be seen and fought (not in creative or spectator), or null. */
	@Nullable
	public static PlayerEntity playerNear(Entity from, double range) {
		return from.getWorld().getClosestPlayer(from.getX(), from.getY(), from.getZ(), range,
				entity -> entity instanceof PlayerEntity player && !player.isSpectator() && !player.isCreative());
	}

	/** How many of its bats a vampire has out. */
	public static int batsOf(VampireEntity vampire) {
		return vampire.getWorld().getEntitiesByClass(VampireBatEntity.class, vampire.getBoundingBox().expand(48.0),
				bat -> vampire.getUuid().equals(bat.owner())).size();
	}

	/** Two or three bats out of the vampire, sent at its target. */
	public static void summonBats(VampireEntity vampire, LivingEntity target) {
		if (!(vampire.getWorld() instanceof ServerWorld world)) {
			return;
		}
		int count = Math.min(MAX_BATS - batsOf(vampire), 2 + vampire.getRandom().nextInt(2));
		for (int i = 0; i < count; i++) {
			VampireBatEntity bat = ModEntities.VAMPIRE_BAT.create(world);
			if (bat == null) {
				return;
			}
			bat.refreshPositionAndAngles(vampire.getX() + vampire.getRandom().nextDouble() - 0.5, vampire.getEyeY(),
					vampire.getZ() + vampire.getRandom().nextDouble() - 0.5, vampire.getRandom().nextFloat() * 360.0F, 0.0F);
			bat.summonedBy(vampire, target);
			world.spawnEntity(bat);
		}
		vampire.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		world.spawnParticles(ParticleTypes.SMOKE, vampire.getX(), vampire.getEyeY(), vampire.getZ(), 12, 0.4, 0.4, 0.4, 0.02);
		world.playSound(null, vampire.getBlockPos(), SoundEvents.ENTITY_EVOKER_PREPARE_SUMMON, SoundCategory.HOSTILE, 1.0F, 1.4F);
	}

	/** A summoned bat's bite: a little damage; poison for a player, turning for a villager. */
	public static void batBite(VampireBatEntity bat, LivingEntity target) {
		if (target.damage(bat.getDamageSources().mobAttack(bat), 2.0F)) {
			if (target instanceof PlayerEntity) {
				target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, EternalReturnConfig.get().mobs.vampireBatPoisonSeconds * 20, 0), bat);
			} else if (target instanceof VillagerEntity villager) {
				infect(villager);
			}
			bat.getWorld().playSound(null, bat.getBlockPos(), SoundEvents.ENTITY_BAT_HURT, SoundCategory.HOSTILE, 0.6F, 1.6F);
		}
		Vec3d back = bat.getPos().subtract(target.getPos()).normalize().multiply(0.4);
		bat.setVelocity(back.x, 0.3, back.z);
	}

	/** A summoned bat whose time is up: gone in a puff. */
	public static void vanish(VampireBatEntity bat) {
		if (bat.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(ParticleTypes.SMOKE, bat.getX(), bat.getY(), bat.getZ(), 6, 0.2, 0.2, 0.2, 0.01);
		}
		bat.discard();
	}

	/** The vampire becomes a bat (carrying everything about it), which flies off. */
	public static void toBat(VampireEntity vampire) {
		if (!(vampire.getWorld() instanceof ServerWorld world)) {
			return;
		}
		VampireBatEntity bat = ModEntities.VAMPIRE_BAT.create(world);
		if (bat == null) {
			return;
		}
		NbtCompound saved = new NbtCompound();
		vampire.saveSelfNbt(saved);
		bat.refreshPositionAndAngles(vampire.getX(), vampire.getEyeY() - 0.4, vampire.getZ(), vampire.getYaw(), 0.0F);
		bat.becomeForm(saved);
		if (vampire.hasCustomName()) {
			bat.setCustomName(vampire.getCustomName());
		}
		world.spawnEntity(bat);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, vampire.getX(), vampire.getBodyY(0.5), vampire.getZ(), 24, 0.3, 0.6, 0.3, 0.02);
		world.playSound(null, vampire.getBlockPos(), SoundEvents.ENTITY_BAT_TAKEOFF, SoundCategory.HOSTILE, 1.0F, 0.7F);
		vampire.discard();
	}

	/** A vampire in bat form, safe again: the vampire back where the bat is (on the ground below it, if near). */
	public static void fromBat(VampireBatEntity bat, NbtCompound saved) {
		if (!(bat.getWorld() instanceof ServerWorld world)) {
			return;
		}
		VampireEntity vampire = ModEntities.VAMPIRE.create(world);
		if (vampire == null) {
			bat.discard();
			return;
		}
		NbtCompound data = saved.copy();
		data.remove("UUID");
		data.remove("Pos");
		data.remove("Motion");
		vampire.readNbt(data);
		BlockPos ground = bat.getBlockPos();
		for (int i = 0; i < 12 && world.isAir(ground.down()) && ground.getY() > world.getBottomY(); i++) {
			ground = ground.down();
		}
		vampire.refreshPositionAndAngles(bat.getX(), ground.getY(), bat.getZ(), bat.getYaw(), 0.0F);
		vampire.setFireTicks(bat.getFireTicks());
		vampire.delayBatForm(600);
		world.spawnEntity(vampire);
		world.spawnParticles(ParticleTypes.LARGE_SMOKE, vampire.getX(), vampire.getBodyY(0.5), vampire.getZ(), 24, 0.3, 0.6, 0.3, 0.02);
		world.playSound(null, vampire.getBlockPos(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.HOSTILE, 1.0F, 0.7F);
		bat.discard();
	}
}
