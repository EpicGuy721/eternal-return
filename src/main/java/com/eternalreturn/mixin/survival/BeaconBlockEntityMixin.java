package com.eternalreturn.mixin.survival;

import com.eternalreturn.survival.BeaconZones;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Working beacons tell BeaconZones the area they cover as they tick, and leave when removed or unloaded. */
@Mixin(BeaconBlockEntity.class)
public abstract class BeaconBlockEntityMixin extends BlockEntity {
	protected BeaconBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private static void eternalreturn$registerZone(World world, BlockPos pos, BlockState state, BeaconBlockEntity beacon, CallbackInfo ci) {
		if (!world.isClient) {
			BeaconZones.update(world, pos, ((BeaconBlockEntityAccessor) beacon).eternalreturn$level(), !beacon.getBeamSegments().isEmpty());
		}
	}

	@Inject(method = "markRemoved", at = @At("HEAD"))
	private void eternalreturn$dropZone(CallbackInfo ci) {
		if (this.world != null && !this.world.isClient) {
			BeaconZones.remove(this.world, this.pos);
		}
	}
}
