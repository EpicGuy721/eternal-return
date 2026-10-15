package com.eternalreturn.villager;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.BlockPosLookTarget;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.potion.Potions;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.village.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * Villagers' chores, by day, when they aren't trading, sleeping or fleeing (villagers.chores):
 * <ul>
 * <li>armorers, weaponsmiths and toolsmiths walk over to a hurt iron golem within 16 blocks and repair it (25 health,
 * as an iron ingot does), every 30 seconds at most;</li>
 * <li>clerics throw a splash potion of Healing at a hurt villager within 12 blocks (one being attacked first),
 * every 8 seconds at most;</li>
 * <li>farmers feed a baby animal within 12 blocks with its food from their pockets, which helps it grow up as a
 * player's feeding does;</li>
 * <li>shepherds now and then (every 2 to 4 minutes) shear a woolly sheep within 12 blocks;</li>
 * <li>fishermen go fishing once a day: they walk to the edge of water within 24 blocks, fish for half a minute or
 * so with a rod in hand, and pocket what they catch (cod, salmon, now and then a tropical fish).</li>
 * </ul>
 * A chore they can't reach in 30 seconds is given up.
 */
public final class VillagerChores {
	public enum Kind { REPAIR, HEAL, FEED, SHEAR, FISH }

	/** What a villager keeps while busy (VillagerEntityMixin); not saved: a chore just starts over. */
	public static final class State {
		@Nullable
		Kind kind;
		@Nullable
		Entity target;
		@Nullable
		BlockPos shore;
		@Nullable
		BlockPos water;
		int startedAge;
		int doneAge;
		int nextChoreAge;
		int nextShearAge;
		long lastFishingDay = -1;
	}

	public interface Holder {
		State eternalreturn$chores();
	}

	private VillagerChores() {
	}

	public static void tick(VillagerEntity villager) {
		if (!(villager.getWorld() instanceof ServerWorld world) || !EternalReturnConfig.get().villagers.chores) {
			return;
		}
		State state = ((Holder) villager).eternalreturn$chores();
		boolean free = !villager.isBaby() && !villager.isSleeping() && !villager.hasCustomer() && villager.getAttacker() == null
				&& world.getTimeOfDay() % 24000L < 12000L;
		if (!free) {
			finish(villager, state);
			return;
		}
		if (state.kind == null) {
			if (villager.age >= state.nextChoreAge && villager.age % 20 == 0) {
				pick(villager, world, state);
			}
			return;
		}
		if (villager.age - state.startedAge > 600 && state.doneAge == 0) {
			finish(villager, state);
			state.nextChoreAge = villager.age + 200;
			return;
		}
		switch (state.kind) {
			case REPAIR -> reach(villager, state, 2.5, () -> {
				IronGolemEntity golem = (IronGolemEntity) state.target;
				golem.heal(25.0F);
				world.playSound(null, golem.getBlockPos(), SoundEvents.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.NEUTRAL, 1.0F, 1.0F);
				state.nextChoreAge = villager.age + 600;
			});
			case HEAL -> reach(villager, state, 8.0, () -> {
				throwHealing(villager, (LivingEntity) state.target);
				state.nextChoreAge = villager.age + 160;
			});
			case FEED -> reach(villager, state, 2.5, () -> {
				AnimalEntity baby = (AnimalEntity) state.target;
				ItemStack food = food(villager, baby);
				if (!food.isEmpty() && baby.isBaby()) {
					food.decrement(1);
					baby.growUp(PassiveEntity.toGrowUpAge(-baby.getBreedingAge()), true);
					world.playSound(null, baby.getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.NEUTRAL, 0.6F, 1.2F);
				}
				state.nextChoreAge = villager.age + 200;
			});
			case SHEAR -> reach(villager, state, 2.5, () -> {
				SheepEntity sheep = (SheepEntity) state.target;
				if (sheep.isShearable()) {
					sheep.sheared(SoundCategory.NEUTRAL);
				}
				state.nextShearAge = villager.age + 2400 + villager.getRandom().nextInt(2400);
				state.nextChoreAge = villager.age + 100;
			});
			case FISH -> fish(villager, world, state);
		}
	}

	/** Walks to the chore's target; once within reach, does it and the chore is over. */
	private static void reach(VillagerEntity villager, State state, double reach, Runnable action) {
		Entity target = state.target;
		if (target == null || !target.isAlive() || target.getWorld() != villager.getWorld()) {
			finish(villager, state);
			return;
		}
		villager.getBrain().remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(target, true));
		if (villager.squaredDistanceTo(target) > reach * reach || (reach > 3.0 && !villager.canSee(target))) {
			if (villager.age % 10 == 0) {
				villager.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.6F, 1));
			}
			return;
		}
		action.run();
		finish(villager, state);
	}

	private static void finish(VillagerEntity villager, State state) {
		if (state.kind == Kind.FISH && villager.getMainHandStack().isOf(Items.FISHING_ROD)) {
			villager.equipStack(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		state.kind = null;
		state.target = null;
		state.shore = null;
		state.water = null;
		state.doneAge = 0;
	}

	private static void start(VillagerEntity villager, State state, Kind kind, @Nullable Entity target) {
		state.kind = kind;
		state.target = target;
		state.startedAge = villager.age;
		state.doneAge = 0;
	}

	private static void pick(VillagerEntity villager, ServerWorld world, State state) {
		VillagerProfession job = villager.getVillagerData().getProfession();
		if (job == VillagerProfession.ARMORER || job == VillagerProfession.WEAPONSMITH || job == VillagerProfession.TOOLSMITH) {
			IronGolemEntity golem = nearest(villager, IronGolemEntity.class, 16.0, g -> g.getHealth() < g.getMaxHealth());
			if (golem != null) {
				start(villager, state, Kind.REPAIR, golem);
			}
		} else if (job == VillagerProfession.CLERIC) {
			List<VillagerEntity> hurt = world.getEntitiesByClass(VillagerEntity.class, villager.getBoundingBox().expand(12.0),
					v -> v != villager && v.isAlive() && v.getHealth() < v.getMaxHealth());
			hurt.sort(Comparator.comparing((VillagerEntity v) -> v.getAttacker() == null).thenComparingDouble(villager::squaredDistanceTo));
			if (!hurt.isEmpty()) {
				start(villager, state, Kind.HEAL, hurt.get(0));
			}
		} else if (job == VillagerProfession.FARMER) {
			AnimalEntity baby = nearest(villager, AnimalEntity.class, 12.0, a -> a.isBaby() && !food(villager, a).isEmpty());
			if (baby != null) {
				start(villager, state, Kind.FEED, baby);
			}
		} else if (job == VillagerProfession.SHEPHERD && villager.age >= state.nextShearAge) {
			SheepEntity sheep = nearest(villager, SheepEntity.class, 12.0, SheepEntity::isShearable);
			if (sheep != null) {
				start(villager, state, Kind.SHEAR, sheep);
			}
		} else if (job == VillagerProfession.FISHERMAN) {
			long day = world.getTimeOfDay() / 24000L;
			long time = world.getTimeOfDay() % 24000L;
			if (day != state.lastFishingDay && time >= 2000L && time < 9000L && findShore(villager, world, state)) {
				state.lastFishingDay = day;
				start(villager, state, Kind.FISH, null);
			}
		}
	}

	@Nullable
	private static <T extends LivingEntity> T nearest(VillagerEntity villager, Class<T> type, double range, java.util.function.Predicate<T> wanted) {
		return villager.getWorld().getEntitiesByClass(type, villager.getBoundingBox().expand(range), e -> e.isAlive() && wanted.test(e)).stream()
				.min(Comparator.comparingDouble(villager::squaredDistanceTo)).orElse(null);
	}

	/** Something from the villager's pockets this baby eats, or EMPTY. */
	private static ItemStack food(VillagerEntity villager, AnimalEntity animal) {
		var inventory = villager.getInventory();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.getStack(i);
			if (!stack.isEmpty() && animal.isBreedingItem(stack)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	private static void throwHealing(VillagerEntity villager, LivingEntity target) {
		double dx = target.getX() + target.getVelocity().x - villager.getX();
		double dy = target.getEyeY() - 1.1 - villager.getY();
		double dz = target.getZ() + target.getVelocity().z - villager.getZ();
		double flat = Math.sqrt(dx * dx + dz * dz);
		PotionEntity potion = new PotionEntity(villager.getWorld(), villager);
		potion.setItem(PotionContentsComponent.createStack(Items.SPLASH_POTION, Potions.HEALING));
		potion.setPitch(potion.getPitch() + 20.0F);
		potion.setVelocity(dx, dy + flat * 0.2, dz, 0.75F, 2.0F);
		villager.getWorld().playSound(null, villager.getX(), villager.getY(), villager.getZ(), SoundEvents.ENTITY_WITCH_THROW, SoundCategory.NEUTRAL, 1.0F, 1.2F);
		villager.getWorld().spawnEntity(potion);
	}

	/** Dry ground right next to open water (water with air above), within 24 blocks; the nearest of a few tries. */
	private static boolean findShore(VillagerEntity villager, ServerWorld world, State state) {
		BlockPos home = villager.getBlockPos();
		BlockPos bestShore = null;
		BlockPos bestWater = null;
		double best = Double.MAX_VALUE;
		BlockPos.Mutable pos = new BlockPos.Mutable();
		for (int x = -24; x <= 24; x++) {
			for (int z = -24; z <= 24; z++) {
				for (int y = -4; y <= 4; y++) {
					pos.set(home.getX() + x, home.getY() + y, home.getZ() + z);
					if (!world.getFluidState(pos).isIn(FluidTags.WATER) || !world.isAir(pos.up())) {
						continue;
					}
					for (Direction side : Direction.Type.HORIZONTAL) {
						BlockPos stand = pos.offset(side).up();
						if (world.isAir(stand) && world.isAir(stand.up()) && world.getBlockState(stand.down()).isSolidBlock(world, stand.down())) {
							double d = stand.getSquaredDistance(home);
							if (d < best) {
								best = d;
								bestShore = stand.toImmutable();
								bestWater = pos.toImmutable();
							}
						}
					}
				}
			}
		}
		state.shore = bestShore;
		state.water = bestWater;
		return bestShore != null;
	}

	private static void fish(VillagerEntity villager, ServerWorld world, State state) {
		if (state.shore == null || !world.getFluidState(state.water).isIn(FluidTags.WATER)) {
			finish(villager, state);
			return;
		}
		villager.getBrain().remember(MemoryModuleType.LOOK_TARGET, new BlockPosLookTarget(state.water));
		if (state.doneAge == 0) {
			if (villager.getBlockPos().getSquaredDistance(state.shore) > 2.0) {
				if (villager.age % 10 == 0) {
					villager.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(state.shore, 0.6F, 0));
				}
				return;
			}
			// Arrived: out with the rod, for half a minute or so.
			villager.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
			state.doneAge = villager.age + 500 + villager.getRandom().nextInt(300);
		}
		villager.getBrain().remember(MemoryModuleType.WALK_TARGET, new WalkTarget(state.shore, 0.6F, 0));
		if (villager.getRandom().nextInt(100) == 0) {
			world.spawnParticles(ParticleTypes.SPLASH, state.water.getX() + 0.5, state.water.getY() + 1.0, state.water.getZ() + 0.5, 12, 0.3, 0.0, 0.3, 0.1);
			world.playSound(null, state.water, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.NEUTRAL, 0.4F, 1.0F);
			int roll = villager.getRandom().nextInt(20);
			ItemStack catchStack = new ItemStack(roll < 12 ? Items.COD : roll < 18 ? Items.SALMON : Items.TROPICAL_FISH);
			ItemStack left = villager.getInventory().addStack(catchStack);
			if (!left.isEmpty()) {
				villager.dropStack(left);
			}
		}
		if (villager.age >= state.doneAge) {
			finish(villager, state);
			state.nextChoreAge = villager.age + 200;
		}
	}
}
