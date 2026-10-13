package com.eternalreturn.mobs.ai;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.EnumSet;

/**
 * Zombies put out the lights (mobs.zombiesBreakLights): with nothing to chase, a zombie looks for a torch
 * within 8 blocks (block tag eternalreturn:zombie_torches), walks up to it and knocks it off. A zombie with
 * a pickaxe or shovel also goes for other lights and beds (eternalreturn:zombie_tool_targets: lanterns,
 * glowstone, sea lanterns, jack o'lanterns, froglights, lamps, end rods, beds...), digging them out as fast
 * as a player with that tool would. Needs mobGriefing.
 */
public class BreakLightsGoal extends Goal {
	public static final TagKey<Block> TORCHES = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("zombie_torches"));
	public static final TagKey<Block> TOOL_TARGETS = TagKey.of(RegistryKeys.BLOCK, EternalReturn.id("zombie_tool_targets"));
	private static final int RANGE = 8;
	private final ZombieEntity zombie;
	private BlockPos target;
	private float progress;
	private int lastStage = -1;
	private int searchCooldown;
	private int giveUp;
	/** The last light it couldn't get to, skipped from then on. */
	private BlockPos unreachable;

	public BreakLightsGoal(ZombieEntity zombie) {
		this.zombie = zombie;
		this.setControls(EnumSet.of(Control.MOVE, Control.LOOK));
	}

	private boolean wants(BlockState state) {
		return state.isIn(TORCHES) || MineTowardTargetGoal.holdsDiggingTool(this.zombie) && (state.isIn(TOOL_TARGETS) || state.isIn(BlockTags.BEDS));
	}

	@Override
	public boolean canStart() {
		if (--this.searchCooldown > 0 || this.zombie.getTarget() != null || !EternalReturnConfig.get().mobs.zombiesBreakLights
				|| com.eternalreturn.mobs.LargeMobs.isLarge(this.zombie) || com.eternalreturn.mobs.Servants.isServant(this.zombie)
				|| !this.zombie.getWorld().getGameRules().getBoolean(GameRules.DO_MOB_GRIEFING)) {
			return false;
		}
		this.searchCooldown = 40 + this.zombie.getRandom().nextInt(20);
		World world = this.zombie.getWorld();
		BlockPos center = this.zombie.getBlockPos();
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos pos : BlockPos.iterate(center.add(-RANGE, -3, -RANGE), center.add(RANGE, 3, RANGE))) {
			if (this.wants(world.getBlockState(pos)) && !pos.equals(this.unreachable)) {
				double distance = pos.getSquaredDistance(center);
				if (distance < bestDistance) {
					bestDistance = distance;
					best = pos.toImmutable();
				}
			}
		}
		this.target = best;
		return best != null;
	}

	@Override
	public boolean shouldContinue() {
		return this.target != null && this.giveUp > 0 && this.zombie.getTarget() == null && this.wants(this.zombie.getWorld().getBlockState(this.target));
	}

	@Override
	public void start() {
		this.progress = 0.0F;
		this.lastStage = -1;
		this.giveUp = 200;
		this.zombie.getNavigation().startMovingTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, 1.0);
	}

	@Override
	public void stop() {
		if (this.target != null && this.giveUp <= 0) {
			this.unreachable = this.target;
		}
		if (this.target != null) {
			this.zombie.getWorld().setBlockBreakingInfo(this.zombie.getId(), this.target, -1);
		}
		this.target = null;
	}

	@Override
	public boolean shouldRunEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		// Runs every tick, but the selector only checks shouldContinue every other one: the light may be gone already.
		if (this.target == null) {
			return;
		}
		this.giveUp--;
		World world = this.zombie.getWorld();
		this.zombie.getLookControl().lookAt(this.target.getX() + 0.5, this.target.getY() + 0.5, this.target.getZ() + 0.5);
		// Within reach of its arms: 3 blocks from its eyes.
		if (this.zombie.getEyePos().squaredDistanceTo(this.target.toCenterPos()) > 9.0) {
			if (this.zombie.getNavigation().isIdle()) {
				this.zombie.getNavigation().startMovingTo(this.target.getX() + 0.5, this.target.getY(), this.target.getZ() + 0.5, 1.0);
			}
			return;
		}
		this.zombie.getNavigation().stop();
		BlockState state = world.getBlockState(this.target);
		ItemStack tool = this.zombie.getMainHandStack();
		float hardness = state.getHardness(world, this.target);
		if (hardness > 0.0F) {
			boolean right = tool.isSuitableFor(state) || !state.isToolRequired() && tool.getMiningSpeedMultiplier(state) > 1.0F;
			this.progress += tool.getMiningSpeedMultiplier(state) / hardness / (right ? 30.0F : 100.0F);
			int stage = (int) (this.progress * 10.0F);
			if (stage != this.lastStage) {
				world.setBlockBreakingInfo(this.zombie.getId(), this.target, Math.min(stage, 9));
				this.lastStage = stage;
			}
			if (this.zombie.age % 5 == 0) {
				this.zombie.swingHand(Hand.MAIN_HAND);
			}
			if (this.progress < 1.0F) {
				return;
			}
		}
		this.zombie.swingHand(Hand.MAIN_HAND);
		world.setBlockBreakingInfo(this.zombie.getId(), this.target, -1);
		world.breakBlock(this.target, true, this.zombie);
		this.target = null;
	}
}
