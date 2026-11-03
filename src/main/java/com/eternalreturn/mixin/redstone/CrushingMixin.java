package com.eternalreturn.mixin.redstone;

import com.eternalreturn.blocks.Crushing;
import com.eternalreturn.duck.CrushingPush;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A push that fails against obsidian crushes the block against it (Crushing): that block leaves the moved blocks for
 * the broken ones (which the piston breaks before it moves the rest), and the push goes ahead.
 */
@Mixin(PistonHandler.class)
public abstract class CrushingMixin implements CrushingPush {
	@Shadow
	@Final
	private World world;
	@Shadow
	@Final
	private boolean retracted;
	@Shadow
	@Final
	private BlockPos posTo;
	@Shadow
	@Final
	private Direction motionDirection;
	@Shadow
	@Final
	private List<BlockPos> movedBlocks;
	@Shadow
	@Final
	private List<BlockPos> brokenBlocks;

	@Unique
	private final Set<BlockPos> eternalreturn$crushed = new HashSet<>();

	@Shadow
	private static boolean isBlockSticky(BlockState state) {
		throw new AssertionError();
	}

	@Override
	public Set<BlockPos> eternalreturn$crushed() {
		return this.eternalreturn$crushed;
	}

	@Inject(method = "calculatePush", at = @At("RETURN"), cancellable = true)
	private void eternalreturn$crush(CallbackInfoReturnable<Boolean> cir) {
		this.eternalreturn$crushed.clear();
		// "retracted" is true for a piston extending (pushing).
		if (cir.getReturnValueZ() || !this.retracted || this.movedBlocks.isEmpty() || !Crushing.enabled()) {
			return;
		}
		for (BlockPos pos : this.movedBlocks) {
			if (isBlockSticky(this.world.getBlockState(pos))) {
				return;
			}
		}
		// The row from the piston on: the first place past it that isn't moving is what stopped the push.
		BlockPos front = this.posTo;
		while (this.movedBlocks.contains(front)) {
			front = front.offset(this.motionDirection);
		}
		BlockPos last = front.offset(this.motionDirection.getOpposite());
		BlockState lastState = this.world.getBlockState(last);
		if (!this.world.getBlockState(front).isIn(Crushing.ANVILS) || !this.movedBlocks.contains(last)
				|| !Crushing.crushable(this.world, last, lastState)) {
			return;
		}
		this.movedBlocks.remove(last);
		this.brokenBlocks.add(last);
		this.eternalreturn$crushed.add(last.toImmutable());
		cir.setReturnValue(true);
	}
}
