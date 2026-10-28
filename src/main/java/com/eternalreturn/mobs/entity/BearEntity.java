package com.eternalreturn.mobs.entity;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.fishing.Overfishing;
import com.eternalreturn.mobs.ai.Reach;
import net.minecraft.block.BeehiveBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SweetBerryBushBlock;
import net.minecraft.block.entity.BeehiveBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.AnimalMateGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.MoveToTargetPosGoal;
import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.BeeEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.passive.PolarBearEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.PointOfInterestTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.poi.PointOfInterest;
import net.minecraft.world.poi.PointOfInterestStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Brown, black and sun bears (com.eternalreturn.mobs.ForestMobs): polar bears (neutral, they fight back when hit, a
 * mother goes for anyone near her cub, they drop fish) but for their coat, size and where they live, and their habits
 * (mobs.bearHabits): a bear within HIVE_RANGE of a beehive or bee nest goes right up to it, rears up looking at it and
 * breaks it open for the honey (Reach: no reaching from afar or through walls), its bees
 * coming out after it (now and then: RAID_COOLDOWN); it strips sweet berry bushes back to bare; and by day it wades
 * into a river and swipes fish out onto the bank, as many as a fisherman would catch there (the spot gets overfished
 * for both: Overfishing). Honeycomb (or honey) lures a bear; a honey bottle breeds two (the bottle given back), and a
 * cub of a pair bred so trusts the player who bred them, as a fox does: it never turns on them, and goes for whatever
 * hurts them. A wild bear never trusts anyone.
 */
public class BearEntity extends PolarBearEntity {
	public static final int HIVE_RANGE = 24;
	private static final int RAID_COOLDOWN = 6000;
	private static final int FISHING_COOLDOWN = 2400;
	private static final RegistryKey<LootTable> FISH = RegistryKey.of(RegistryKeys.LOOT_TABLE, Identifier.of("eternalreturn", "gameplay/fishing/fish"));

	private final List<UUID> trusted = new ArrayList<>();
	/** World time before which it won't look for a hive (or a river) again. */
	private long nextRaid;
	private long nextFishing;

	public BearEntity(EntityType<? extends PolarBearEntity> type, World world) {
		super(type, world);
	}

	private static boolean habits() {
		return EternalReturnConfig.get().mobs.bearHabits;
	}

	@Override
	protected void initGoals() {
		super.initGoals();
		this.goalSelector.add(2, new AnimalMateGoal(this, 1.0));
		this.goalSelector.add(3, new TemptGoal(this, 1.1, Ingredient.ofItems(Items.HONEYCOMB, Items.HONEY_BOTTLE), false));
		this.goalSelector.add(4, new RaidHiveGoal());
		this.goalSelector.add(4, new FishGoal());
		this.goalSelector.add(5, new EatBerriesGoal());
		this.targetSelector.add(1, new DefendTrustedGoal());
	}

	/** A cub is a bear of its parents' kind. */
	@Override
	public PassiveEntity createChild(ServerWorld world, PassiveEntity mate) {
		return (PassiveEntity) this.getType().create(world);
	}

	@Override
	public boolean isBreedingItem(ItemStack stack) {
		return stack.isOf(Items.HONEY_BOTTLE);
	}

	/** Fed a honey bottle, the player keeps the bottle. */
	@Override
	protected void eat(PlayerEntity player, Hand hand, ItemStack stack) {
		player.setStackInHand(hand, ItemUsage.exchangeStack(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
	}

	/** A cub of bears a player bred trusts that player (the other parent's breeder too). */
	@Override
	public void breed(ServerWorld world, AnimalEntity other, @Nullable PassiveEntity baby) {
		if (baby instanceof BearEntity cub) {
			if (this.getLovingPlayer() != null) {
				cub.trust(this.getLovingPlayer().getUuid());
			}
			if (other.getLovingPlayer() != null) {
				cub.trust(other.getLovingPlayer().getUuid());
			}
		}
		super.breed(world, other, baby);
	}

	public void trust(UUID player) {
		if (!this.trusted.contains(player)) {
			this.trusted.add(player);
		}
	}

	public boolean trusts(@Nullable LivingEntity entity) {
		return entity instanceof PlayerEntity && this.trusted.contains(entity.getUuid());
	}

	@Override
	public void setTarget(@Nullable LivingEntity target) {
		super.setTarget(this.trusts(target) ? null : target);
	}

	@Override
	public void setAngryAt(@Nullable UUID angryAt) {
		super.setAngryAt(angryAt != null && this.trusted.contains(angryAt) ? null : angryAt);
	}

	@Override
	public boolean shouldAngerAt(LivingEntity entity) {
		return !this.trusts(entity) && super.shouldAngerAt(entity);
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		NbtList list = new NbtList();
		for (UUID id : this.trusted) {
			list.add(NbtHelper.fromUuid(id));
		}
		nbt.put("Trusted", list);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.trusted.clear();
		for (NbtElement id : nbt.getList("Trusted", NbtElement.INT_ARRAY_TYPE)) {
			this.trusted.add(NbtHelper.toUuid(id));
		}
	}

	private boolean mayGrief() {
		return this.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING);
	}

	/** Busy with something more pressing than a habit: a fight, a breeder, a cub to follow. */
	private boolean busy() {
		return this.isBaby() || this.getTarget() != null || this.hasAngerTime() || this.isInLove() || this.getAttacker() != null;
	}

	/** The nearest beehive or bee nest within HIVE_RANGE, or null. */
	@Nullable
	public BlockPos findHive() {
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return null;
		}
		return world.getPointOfInterestStorage()
				.getInSquare(type -> type.isIn(PointOfInterestTypeTags.BEE_HOME), this.getBlockPos(), HIVE_RANGE, PointOfInterestStorage.OccupationStatus.ANY)
				.map(PointOfInterest::getPos)
				.filter(pos -> pos.isWithinDistance(this.getPos(), HIVE_RANGE) && world.getBlockState(pos).getBlock() instanceof BeehiveBlock)
				.min(Comparator.comparingDouble(pos -> pos.getSquaredDistance(this.getPos())))
				.orElse(null);
	}

	/** Breaks the hive open: its bees (and those about) come out after the bear, and it eats what honey there was. */
	public void raid(BlockPos hive) {
		World world = this.getWorld();
		BlockState state = world.getBlockState(hive);
		if (!(state.getBlock() instanceof BeehiveBlock)) {
			return;
		}
		int honey = BeehiveBlockEntity.getHoneyLevel(state);
		if (world.getBlockEntity(hive) instanceof BeehiveBlockEntity entity) {
			entity.angerBees(null, state, BeehiveBlockEntity.BeeState.EMERGENCY);
		}
		world.breakBlock(hive, false, this);
		for (BeeEntity bee : world.getEntitiesByClass(BeeEntity.class, this.getBoundingBox().expand(12.0), bee -> true)) {
			bee.setAngryAt(this.getUuid());
			bee.chooseRandomAngerTime();
			bee.setTarget(this);
		}
		if (honey > 0) {
			Block.dropStack(world, hive, new ItemStack(Items.HONEYCOMB, 1 + this.random.nextInt(1 + honey / 2)));
			this.heal(honey * 2.0F);
			this.playSound(SoundEvents.BLOCK_BEEHIVE_DRIP, 1.0F, 1.0F);
		}
	}

	/** Strips a sweet berry bush back to bare, eating the berries. */
	public void eatBerries(BlockPos pos) {
		World world = this.getWorld();
		BlockState state = world.getBlockState(pos);
		if (!state.isOf(Blocks.SWEET_BERRY_BUSH) || state.get(SweetBerryBushBlock.AGE) < 2) {
			return;
		}
		world.setBlockState(pos, state.with(SweetBerryBushBlock.AGE, 0), Block.NOTIFY_LISTENERS);
		world.emitGameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Emitter.of(this, state));
		this.playSound(SoundEvents.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0F, 1.0F);
		this.playSound(SoundEvents.ENTITY_GENERIC_EAT, 1.0F, 0.8F);
		this.heal(2.0F);
	}

	/** Wading water to fish from: river water a block or two deep, in reach of a bank. */
	@Nullable
	public BlockPos bankNear(BlockPos water) {
		World world = this.getWorld();
		if (!world.getFluidState(water).isIn(FluidTags.WATER) || !world.getBiome(water).isIn(BiomeTags.IS_RIVER)
				|| !world.getBlockState(water.up()).isAir()) {
			return null;
		}
		BlockPos floor = water.down();
		if (!world.getBlockState(floor).isSolidBlock(world, floor)
				&& !(world.getFluidState(floor).isIn(FluidTags.WATER) && world.getBlockState(floor.down()).isSolidBlock(world, floor.down()))) {
			return null;
		}
		for (int reach = 1; reach <= 3; reach++) {
			for (Direction side : Direction.Type.HORIZONTAL) {
				BlockPos bank = water.offset(side, reach);
				for (BlockPos pos : new BlockPos[] {bank, bank.up()}) {
					if (world.getBlockState(pos.down()).isSolidBlock(world, pos.down()) && world.getBlockState(pos).isAir()
							&& world.getFluidState(pos.down()).isEmpty()) {
						return pos;
					}
				}
			}
		}
		return null;
	}

	/**
	 * One swipe at the fish: as likely to come up with one as the spot has fish left (Overfishing), which lands on the
	 * bank and counts against the spot. Whether it caught one.
	 */
	public boolean swipe(BlockPos water, BlockPos bank) {
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return false;
		}
		this.playSound(SoundEvents.ENTITY_GENERIC_SPLASH, 0.6F, 1.2F);
		Vec3d spot = Vec3d.ofCenter(water);
		if (this.random.nextFloat() >= 0.4F / Overfishing.waitMultiplier(world, spot)) {
			return false;
		}
		LootContextParameterSet params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, spot)
				.add(LootContextParameters.TOOL, ItemStack.EMPTY).add(LootContextParameters.THIS_ENTITY, this).build(LootContextTypes.FISHING);
		List<ItemStack> fish = world.getServer().getReloadableRegistries().getLootTable(FISH).generateLoot(params);
		if (fish.isEmpty()) {
			return false;
		}
		Overfishing.recordCatch(world, spot);
		Vec3d to = Vec3d.ofBottomCenter(bank).subtract(spot);
		for (ItemStack stack : fish) {
			ItemEntity item = new ItemEntity(world, spot.x, spot.y + 0.5, spot.z, stack);
			item.setVelocity(to.x * 0.12, 0.35 + Math.sqrt(to.horizontalLengthSquared()) * 0.03, to.z * 0.12);
			item.setToDefaultPickupDelay();
			world.spawnEntity(item);
		}
		return true;
	}

	/** Goes to a hive near and breaks it open. */
	class RaidHiveGoal extends Goal {
		private BlockPos hive;
		private int ticks;

		RaidHiveGoal() {
			this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public boolean canStart() {
			World world = BearEntity.this.getWorld();
			if (!habits() || BearEntity.this.busy() || !BearEntity.this.mayGrief() || world.getTime() < BearEntity.this.nextRaid
					|| BearEntity.this.random.nextInt(20) != 0) {
				return false;
			}
			this.hive = BearEntity.this.findHive();
			if (this.hive == null) {
				BearEntity.this.nextRaid = world.getTime() + 600;
			}
			return this.hive != null;
		}

		@Override
		public boolean shouldContinue() {
			return this.ticks < 600 && !BearEntity.this.busy() && BearEntity.this.getWorld().getBlockState(this.hive).getBlock() instanceof BeehiveBlock;
		}

		@Override
		public void start() {
			this.ticks = 0;
			BearEntity.this.getNavigation().startMovingTo(this.hive.getX() + 0.5, this.hive.getY(), this.hive.getZ() + 0.5, 1.1);
		}

		@Override
		public void stop() {
			BearEntity.this.setWarning(false);
			BearEntity.this.getNavigation().stop();
			BearEntity.this.nextRaid = BearEntity.this.getWorld().getTime() + RAID_COOLDOWN;
		}

		@Override
		public void tick() {
			this.ticks++;
			Vec3d center = Vec3d.ofCenter(this.hive);
			BearEntity.this.getLookControl().lookAt(center.x, center.y, center.z);
			// Right up against it (reared up, it reaches a block higher), and with nothing between (Reach).
			if (!Reach.inReach(BearEntity.this, this.hive, 1.0) || !Reach.inSight(BearEntity.this, this.hive)) {
				if (this.ticks % 20 == 0) {
					BearEntity.this.getNavigation().startMovingTo(center.x, this.hive.getY(), center.z, 1.1);
				}
				return;
			}
			BearEntity.this.getNavigation().stop();
			BearEntity.this.setWarning(true);
			// Looking at it before it claws at it.
			if (!Reach.facing(BearEntity.this, center)) {
				return;
			}
			if (this.ticks % 10 == 0) {
				BearEntity.this.playSound(SoundEvents.BLOCK_BEEHIVE_SHEAR, 1.0F, 0.7F);
			}
			if (this.ticks % 30 == 0) {
				BearEntity.this.raid(this.hive);
			}
		}
	}

	/** Goes to a sweet berry bush with berries and strips it. */
	class EatBerriesGoal extends MoveToTargetPosGoal {
		private int timer;

		EatBerriesGoal() {
			super(BearEntity.this, 1.0, 12, 2);
		}

		@Override
		public boolean canStart() {
			return habits() && !BearEntity.this.busy() && BearEntity.this.mayGrief() && super.canStart();
		}

		@Override
		public double getDesiredDistanceToTarget() {
			return 2.0;
		}

		@Override
		public boolean shouldResetPath() {
			return this.tryingTime % 100 == 0;
		}

		@Override
		protected boolean isTargetPos(WorldView world, BlockPos pos) {
			BlockState state = world.getBlockState(pos);
			return state.isOf(Blocks.SWEET_BERRY_BUSH) && state.get(SweetBerryBushBlock.AGE) >= 2;
		}

		@Override
		public void start() {
			this.timer = 0;
			super.start();
		}

		@Override
		public void tick() {
			Vec3d bush = Vec3d.ofCenter(this.targetPos);
			BearEntity.this.getLookControl().lookAt(bush.x, bush.y, bush.z);
			// At it, and looking at it (Reach), for two seconds.
			if (this.hasReached() && Reach.canReach(BearEntity.this, this.targetPos, 0.0) && ++this.timer >= 40) {
				BearEntity.this.eatBerries(this.targetPos);
			}
			super.tick();
		}
	}

	/** By day, wades into a river and swipes fish out onto the bank for a while. */
	class FishGoal extends Goal {
		private BlockPos water;
		private BlockPos bank;
		private int ticks;
		private int caught;

		FishGoal() {
			this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
		}

		@Override
		public boolean shouldRunEveryTick() {
			return true;
		}

		@Override
		public boolean canStart() {
			World world = BearEntity.this.getWorld();
			if (!habits() || BearEntity.this.busy() || !world.isDay() || world.getTime() < BearEntity.this.nextFishing
					|| BearEntity.this.random.nextInt(20) != 0) {
				return false;
			}
			BlockPos at = BearEntity.this.getBlockPos();
			for (int i = 0; i < 64; i++) {
				BlockPos column = at.add(BearEntity.this.random.nextInt(25) - 12, 0, BearEntity.this.random.nextInt(25) - 12);
				for (int dy = 3; dy >= -3; dy--) {
					BlockPos bank = BearEntity.this.bankNear(column.up(dy));
					if (bank != null) {
						this.water = column.up(dy);
						this.bank = bank;
						return true;
					}
				}
			}
			BearEntity.this.nextFishing = world.getTime() + 200;
			return false;
		}

		@Override
		public boolean shouldContinue() {
			return this.ticks < 900 && this.caught < 3 && !BearEntity.this.busy() && BearEntity.this.getWorld().isDay();
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.caught = 0;
			BearEntity.this.getNavigation().startMovingTo(this.water.getX() + 0.5, this.water.getY(), this.water.getZ() + 0.5, 1.0);
		}

		@Override
		public void stop() {
			BearEntity.this.setWarning(false);
			BearEntity.this.getNavigation().stop();
			BearEntity.this.nextFishing = BearEntity.this.getWorld().getTime() + FISHING_COOLDOWN;
		}

		@Override
		public void tick() {
			this.ticks++;
			Vec3d spot = Vec3d.ofCenter(this.water);
			if (BearEntity.this.squaredDistanceTo(spot.x, BearEntity.this.getY(), spot.z) > 1.5 * 1.5) {
				if (this.ticks % 20 == 0) {
					BearEntity.this.getNavigation().startMovingTo(spot.x, this.water.getY(), spot.z, 1.0);
				}
				if (this.ticks > 300) {
					this.ticks = 900;
				}
				return;
			}
			BearEntity.this.getNavigation().stop();
			BearEntity.this.getLookControl().lookAt(spot.x, spot.y - 0.5, spot.z);
			BearEntity.this.setWarning(this.ticks % 80 >= 70);
			if (this.ticks % 80 == 79 && BearEntity.this.swipe(this.water, this.bank)) {
				this.caught++;
			}
		}
	}

	/** Goes for whatever hurts a player it trusts, as a fox does. */
	class DefendTrustedGoal extends TrackTargetGoal {
		private LivingEntity offender;
		private int lastSeen;

		DefendTrustedGoal() {
			super(BearEntity.this, false, false);
			this.setControls(EnumSet.of(Goal.Control.TARGET));
		}

		@Override
		public boolean canStart() {
			if (BearEntity.this.trusted.isEmpty() || BearEntity.this.isBaby()) {
				return false;
			}
			for (PlayerEntity player : BearEntity.this.getWorld().getEntitiesByClass(PlayerEntity.class, BearEntity.this.getBoundingBox().expand(16.0),
					BearEntity.this::trusts)) {
				LivingEntity attacker = player.getAttacker();
				if (attacker != null && attacker != BearEntity.this && player.getLastAttackedTime() != this.lastSeen
						&& this.canTrack(attacker, TargetPredicate.DEFAULT)) {
					this.offender = attacker;
					this.lastSeen = player.getLastAttackedTime();
					return true;
				}
			}
			return false;
		}

		@Override
		public void start() {
			BearEntity.this.setTarget(this.offender);
			super.start();
		}
	}
}
