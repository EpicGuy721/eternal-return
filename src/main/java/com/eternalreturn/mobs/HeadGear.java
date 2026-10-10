package com.eternalreturn.mobs;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

/**
 * Things on zombies' and skeletons' heads (every kind: husks, drowned, strays, wither skeletons...):
 * <ul>
 * <li>in birch forests and cherry groves (biome tag eternalreturn:beehive_mob_biomes), 10% wear a beehive
 * (mobs.beehiveHeadChance); when one dies, the hive breaks open and 3 angry bees come out after its
 * killer (or the nearest player);</li>
 * <li>anywhere, very rarely (0.1%, mobs.tntHeadChance), one wears TNT, and explodes like TNT when it dies.</li>
 * </ul>
 * Neither is ever dropped, and like any helmet they keep the mob from burning in daylight.
 */
public final class HeadGear {
	public static final TagKey<Biome> BEEHIVE_MOB_BIOMES = TagKey.of(RegistryKeys.BIOME, EternalReturn.id("beehive_mob_biomes"));

	private HeadGear() {
	}

	public static boolean wears(LivingEntity mob) {
		return mob instanceof ZombieEntity || mob instanceof AbstractSkeletonEntity;
	}

	/** As a zombie or skeleton spawns: maybe a beehive or TNT, if the head is still bare. */
	public static void roll(MobEntity mob, Random random) {
		if (!wears(mob) || !mob.getEquippedStack(EquipmentSlot.HEAD).isEmpty()) {
			return;
		}
		EternalReturnConfig.MobTweaks cfg = EternalReturnConfig.get().mobs;
		ItemStack head = ItemStack.EMPTY;
		if (random.nextDouble() < cfg.tntHeadChance) {
			head = new ItemStack(Items.TNT);
		} else if (mob.getWorld().getBiome(mob.getBlockPos()).isIn(BEEHIVE_MOB_BIOMES) && random.nextDouble() < cfg.beehiveHeadChance) {
			head = new ItemStack(Items.BEEHIVE);
		}
		if (!head.isEmpty()) {
			mob.equipStack(EquipmentSlot.HEAD, head);
			mob.setEquipmentDropChance(EquipmentSlot.HEAD, 0.0F);
		}
	}

	/** As a zombie or skeleton dies: the hive breaks open, or the TNT goes off. */
	public static void died(LivingEntity mob, DamageSource source) {
		if (!wears(mob) || !(mob.getWorld() instanceof ServerWorld world)) {
			return;
		}
		ItemStack head = mob.getEquippedStack(EquipmentSlot.HEAD);
		if (head.isOf(Items.BEEHIVE)) {
			mob.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			LivingEntity target = source.getAttacker() instanceof LivingEntity attacker ? attacker : world.getClosestPlayer(mob, 16.0);
			world.playSound(null, mob.getBlockPos(), SoundEvents.BLOCK_BEEHIVE_DRIP, SoundCategory.HOSTILE, 1.0F, 1.0F);
			for (int i = 0; i < 3; i++) {
				BeeEntity bee = EntityType.BEE.create(world);
				if (bee == null) {
					continue;
				}
				bee.refreshPositionAndAngles(mob.getX(), mob.getEyeY(), mob.getZ(), world.random.nextFloat() * 360.0F, 0.0F);
				if (target != null) {
					bee.setTarget(target);
					bee.chooseRandomAngerTime();
					if (target instanceof PlayerEntity player) {
						bee.setAngryAt(player.getUuid());
					}
				}
				world.spawnEntity(bee);
			}
		} else if (head.isOf(Items.TNT)) {
			mob.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
			world.createExplosion(mob, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 4.0F, World.ExplosionSourceType.TNT);
		}
	}
}
