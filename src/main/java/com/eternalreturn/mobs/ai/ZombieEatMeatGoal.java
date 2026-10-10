package com.eternalreturn.mobs.ai;

import com.eternalreturn.EternalReturn;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;

import java.util.EnumSet;
import java.util.List;

/**
 * A hurt adult zombie goes for raw meat lying nearby (item tag eternalreturn:zombie_meat: raw beef,
 * porkchop, chicken, mutton and rabbit) and eats it, healing twice the meat's food value (raw beef 6
 * health). It does this when it has nothing to fight or its target is far off.
 */
public class ZombieEatMeatGoal extends Goal {
	public static final TagKey<Item> ZOMBIE_MEAT = TagKey.of(RegistryKeys.ITEM, EternalReturn.id("zombie_meat"));
	private final ZombieEntity zombie;
	private ItemEntity meat;
	private int searchCooldown;

	public ZombieEatMeatGoal(ZombieEntity zombie) {
		this.zombie = zombie;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	@Override
	public boolean canStart() {
		if (this.zombie.isBaby() || this.zombie.getHealth() >= this.zombie.getMaxHealth() || --this.searchCooldown > 0) {
			return false;
		}
		this.searchCooldown = 20;
		if (this.zombie.getTarget() != null && this.zombie.squaredDistanceTo(this.zombie.getTarget()) < 100.0) {
			return false;
		}
		List<ItemEntity> found = this.zombie.getWorld().getEntitiesByClass(ItemEntity.class, this.zombie.getBoundingBox().expand(10.0, 3.0, 10.0),
				item -> item.isAlive() && item.getStack().isIn(ZOMBIE_MEAT));
		this.meat = found.stream().min((a, b) -> Double.compare(this.zombie.squaredDistanceTo(a), this.zombie.squaredDistanceTo(b))).orElse(null);
		return this.meat != null;
	}

	@Override
	public boolean shouldContinue() {
		return this.meat != null && this.meat.isAlive() && this.zombie.getHealth() < this.zombie.getMaxHealth()
				&& (this.zombie.getTarget() == null || this.zombie.squaredDistanceTo(this.zombie.getTarget()) >= 100.0);
	}

	@Override
	public void start() {
		this.zombie.getNavigation().startMovingTo(this.meat, 1.0);
	}

	@Override
	public void stop() {
		this.meat = null;
	}

	@Override
	public void tick() {
		if (this.meat == null) {
			return;
		}
		this.zombie.getLookControl().lookAt(this.meat);
		if (this.zombie.squaredDistanceTo(this.meat) > 2.25) {
			if (this.zombie.getNavigation().isIdle()) {
				this.zombie.getNavigation().startMovingTo(this.meat, 1.0);
			}
			return;
		}
		eat(this.zombie, this.meat);
		this.meat = null;
	}

	/** Eats one piece of the meat item: heals twice its food value. */
	public static void eat(ZombieEntity zombie, ItemEntity meat) {
		ItemStack stack = meat.getStack();
		FoodComponent food = stack.get(DataComponentTypes.FOOD);
		zombie.heal(food == null ? 2.0F : food.nutrition() * 2.0F);
		if (zombie.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, stack.copyWithCount(1)), zombie.getX(), zombie.getEyeY() - 0.2, zombie.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
		}
		zombie.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 0.8F);
		stack.decrement(1);
		if (stack.isEmpty()) {
			meat.discard();
		} else {
			meat.setStack(stack);
		}
	}
}
