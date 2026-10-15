package com.eternalreturn.mobs.ai;

import com.eternalreturn.mobs.ZombieVillagerJobs;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.sound.SoundEvents;

import java.util.EnumSet;
import java.util.List;

/**
 * A zombie cleric (ZombieVillagerJobs) throws a splash potion of Harming every few seconds: at its target from up to
 * 10 blocks, or, failing that, at a hurt undead mob nearby, which Harming heals.
 */
public class ZombiePotionGoal extends Goal {
	private final ZombieEntity zombie;
	private int cooldown;

	public ZombiePotionGoal(ZombieEntity zombie) {
		this.zombie = zombie;
		this.setControls(EnumSet.noneOf(Control.class));
	}

	@Override
	public boolean canStart() {
		return ZombieVillagerJobs.isCleric(this.zombie);
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (--this.cooldown > 0) {
			return;
		}
		this.cooldown = 10;
		LivingEntity target = this.zombie.getTarget();
		if (target == null || !target.isAlive() || this.zombie.squaredDistanceTo(target) > 100.0 || !this.zombie.getVisibilityCache().canSee(target)) {
			List<MobEntity> hurt = this.zombie.getWorld().getEntitiesByClass(MobEntity.class, this.zombie.getBoundingBox().expand(10.0),
					mob -> mob != this.zombie && mob.isAlive() && mob.getType().isIn(EntityTypeTags.UNDEAD) && mob.getHealth() < mob.getMaxHealth() - 2.0F
							&& this.zombie.getVisibilityCache().canSee(mob));
			target = hurt.isEmpty() ? null : hurt.get(0);
		}
		if (target == null) {
			return;
		}
		throwAt(this.zombie, target);
		this.cooldown = 60 + this.zombie.getRandom().nextInt(40);
	}

	/** As a witch throws: a little ahead of where the target is going. */
	static void throwAt(MobEntity thrower, LivingEntity target) {
		double dx = target.getX() + target.getVelocity().x - thrower.getX();
		double dy = target.getEyeY() - 1.1 - thrower.getY();
		double dz = target.getZ() + target.getVelocity().z - thrower.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		PotionEntity potion = new PotionEntity(thrower.getWorld(), thrower);
		potion.setItem(PotionContentsComponent.createStack(Items.SPLASH_POTION, Potions.HARMING));
		potion.setPitch(potion.getPitch() + 20.0F);
		potion.setVelocity(dx, dy + flat * 0.2, dz, 0.75F, 8.0F);
		thrower.getWorld().playSound(null, thrower.getX(), thrower.getY(), thrower.getZ(), SoundEvents.ENTITY_WITCH_THROW, thrower.getSoundCategory(), 1.0F,
				0.8F + thrower.getRandom().nextFloat() * 0.4F);
		thrower.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		thrower.getWorld().spawnEntity(potion);
	}
}
