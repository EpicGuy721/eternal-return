package com.eternalreturn.mobs.ai;

import net.minecraft.block.BlockState;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Mining zombies (MiningZombies): a zombie holding a pickaxe or shovel that can't find a path to its target
 * digs toward it, one block at a time: the block ahead at head and foot height, or the block under its
 * feet or over its head when the target is below or above. A block takes as long as it would for a player
 * with that tool (much longer with the wrong one), shows its cracks while being dug, and drops as if
 * mined. It needs mobGriefing, and won't dig bedrock, obsidian or anything holding data (chests and the
 * like), or anything that would let water or lava in.
 */
public class MineTowardTargetGoal extends Goal {
	private static final int REPATH_TICKS = 20;
	private final ZombieEntity zombie;
	private BlockPos digging;
	private float progress;
	private int lastStage = -1;
	private int checkCooldown;

	public MineTowardTargetGoal(ZombieEntity zombie) {
		this.zombie = zombie;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	public static boolean holdsDiggingTool(LivingEntity mob) {
		ItemStack tool = mob.getMainHandStack();
		return tool.isIn(ItemTags.PICKAXES) || tool.isIn(ItemTags.SHOVELS);
	}

	@Override
	public boolean canStart() {
		if (--this.checkCooldown > 0) {
			return false;
		}
		this.checkCooldown = REPATH_TICKS;
		LivingEntity target = this.zombie.getTarget();
		World world = this.zombie.getWorld();
		if (target == null || !target.isAlive() || !holdsDiggingTool(this.zombie) || !world.getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)
				|| this.zombie.squaredDistanceTo(target) > 32 * 32 || this.zombie.squaredDistanceTo(target) < 2.0) {
			return false;
		}
		Path path = this.zombie.getNavigation().findPathTo(target, 0);
		if (path != null && path.reachesTarget()) {
			return false;
		}
		this.digging = this.pickBlock(target);
		return this.digging != null;
	}

	@Override
	public boolean shouldContinue() {
		LivingEntity target = this.zombie.getTarget();
		return this.digging != null && target != null && target.isAlive() && holdsDiggingTool(this.zombie)
				&& canDig(this.zombie.getWorld(), this.digging) && this.zombie.squaredDistanceTo(this.digging.toCenterPos()) < 9.0;
	}

	@Override
	public void start() {
		this.progress = 0.0F;
		this.lastStage = -1;
		this.zombie.getNavigation().stop();
	}

	@Override
	public void stop() {
		if (this.digging != null) {
			this.zombie.getWorld().setBlockBreakingInfo(this.zombie.getId(), this.digging, -1);
		}
		this.digging = null;
		this.checkCooldown = 0;
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		if (this.digging == null) {
			return;
		}
		World world = this.zombie.getWorld();
		this.zombie.getLookControl().lookAt(this.digging.getX() + 0.5, this.digging.getY() + 0.5, this.digging.getZ() + 0.5);
		BlockState state = world.getBlockState(this.digging);
		ItemStack tool = this.zombie.getMainHandStack();
		float hardness = state.getHardness(world, this.digging);
		boolean right = tool.isSuitableFor(state) || !state.isToolRequired() && tool.getMiningSpeedMultiplier(state) > 1.0F;
		this.progress += tool.getMiningSpeedMultiplier(state) / Math.max(hardness, 0.05F) / (right ? 30.0F : 100.0F);
		if (this.zombie.age % 5 == 0) {
			this.zombie.swingHand(net.minecraft.util.Hand.MAIN_HAND);
		}
		int stage = (int) (this.progress * 10.0F);
		if (stage != this.lastStage) {
			world.setBlockBreakingInfo(this.zombie.getId(), this.digging, Math.min(stage, 9));
			this.lastStage = stage;
		}
		if (this.progress >= 1.0F) {
			world.setBlockBreakingInfo(this.zombie.getId(), this.digging, -1);
			world.breakBlock(this.digging, true, this.zombie);
			tool.damage(1, this.zombie, EquipmentSlot.MAINHAND);
			LivingEntity target = this.zombie.getTarget();
			this.digging = target == null ? null : this.pickBlock(target);
			this.progress = 0.0F;
			this.lastStage = -1;
			if (this.digging == null && target != null) {
				this.zombie.getNavigation().startMovingTo(target, 1.0);
			}
		}
	}

	/** The next block to dig toward the target, or null if the way ahead is open. */
	private BlockPos pickBlock(LivingEntity target) {
		World world = this.zombie.getWorld();
		BlockPos feet = this.zombie.getBlockPos();
		double dx = target.getX() - this.zombie.getX();
		double dz = target.getZ() - this.zombie.getZ();
		double dy = target.getY() - this.zombie.getY();
		Direction ahead = Direction.getFacing(dx, 0.0, dz);
		List<BlockPos> candidates = new ArrayList<>();
		boolean far = dx * dx + dz * dz > 1.5;
		if (dy < -1.5 && !far) {
			candidates.add(feet.down());
		}
		if (dy > 1.5 && !far) {
			candidates.add(feet.up(2));
		}
		if (far) {
			BlockPos front = feet.offset(ahead);
			candidates.add(front.up());
			candidates.add(front);
			if (dy < -1.5) {
				candidates.add(front.down());
			} else if (dy > 1.5) {
				candidates.add(feet.up(2));
			}
		}
		for (BlockPos pos : candidates) {
			if (canDig(world, pos)) {
				return pos;
			}
		}
		return null;
	}

	static boolean canDig(World world, BlockPos pos) {
		BlockState state = world.getBlockState(pos);
		if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getHardness(world, pos) < 0 || state.getHardness(world, pos) >= 50.0F
				|| state.getCollisionShape(world, pos).isEmpty() || state.isIn(BlockTags.WITHER_IMMUNE)) {
			return false;
		}
		for (Direction direction : Direction.values()) {
			if (!world.getFluidState(pos.offset(direction)).isEmpty() && direction != Direction.DOWN) {
				return false;
			}
		}
		return true;
	}
}
