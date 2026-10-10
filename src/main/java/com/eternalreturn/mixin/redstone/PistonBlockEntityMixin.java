package com.eternalreturn.mixin.redstone;

import com.eternalreturn.blocks.Redstone;
import com.eternalreturn.duck.MovedData;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The moving block carries the moved block's data and puts it back when it lands (Redstone). */
@Mixin(PistonBlockEntity.class)
public abstract class PistonBlockEntityMixin extends BlockEntity implements MovedData {
	@Unique
	private static final String DATA_KEY = "EternalReturnMovedData";
	@Unique
	@Nullable
	private NbtCompound eternalreturn$movedData;

	protected PistonBlockEntityMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	@Nullable
	public NbtCompound eternalreturn$movedData() {
		return this.eternalreturn$movedData;
	}

	@Override
	public void eternalreturn$setMovedData(@Nullable NbtCompound data) {
		this.eternalreturn$movedData = data;
	}

	@Unique
	private void eternalreturn$land() {
		if (this.eternalreturn$movedData != null && this.world != null && !this.world.isClient) {
			NbtCompound data = this.eternalreturn$movedData;
			this.eternalreturn$movedData = null;
			Redstone.putData(this.world, this.pos, ((PistonBlockEntity) (Object) this).getPushedBlock(), data);
		}
	}

	@Inject(method = "tick", at = @At("RETURN"))
	private static void eternalreturn$landed(World world, BlockPos pos, BlockState state, PistonBlockEntity piston, CallbackInfo ci) {
		if (piston.isRemoved()) {
			((PistonBlockEntityMixin) (Object) piston).eternalreturn$land();
		}
	}

	@Inject(method = "finish", at = @At("RETURN"))
	private void eternalreturn$finished(CallbackInfo ci) {
		this.eternalreturn$land();
	}

	@Inject(method = "readNbt", at = @At("TAIL"))
	private void eternalreturn$loadData(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
		this.eternalreturn$movedData = nbt.contains(DATA_KEY) ? nbt.getCompound(DATA_KEY) : null;
	}

	@Inject(method = "writeNbt", at = @At("TAIL"))
	private void eternalreturn$saveData(NbtCompound nbt, RegistryWrapper.WrapperLookup registries, CallbackInfo ci) {
		if (this.eternalreturn$movedData != null) {
			nbt.put(DATA_KEY, this.eternalreturn$movedData);
		}
	}
}
