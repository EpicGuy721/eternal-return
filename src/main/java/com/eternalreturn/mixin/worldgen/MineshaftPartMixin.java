package com.eternalreturn.mixin.worldgen;

import com.eternalreturn.worldgen.structures.MineshaftWoods;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.structure.StructureContext;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.util.math.BlockBox;
import net.minecraft.world.gen.structure.MineshaftStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every mineshaft piece remembers its wood (null: vanilla's), set when the piece is laid out in an
 * Eternal Return world and saved with it, and builds its planks, logs and fences from it.
 */
@Mixin(targets = "net.minecraft.structure.MineshaftGenerator$MineshaftPart")
public abstract class MineshaftPartMixin implements MineshaftWoods.Holder {
	@Unique
	private static final String WOOD_KEY = "eternalreturn_wood";
	@Unique
	private MineshaftWoods.Wood eternalreturn$wood;

	@Override
	public MineshaftWoods.Wood eternalreturn$wood() {
		return this.eternalreturn$wood;
	}

	@Inject(method = "<init>(Lnet/minecraft/structure/StructurePieceType;ILnet/minecraft/world/gen/structure/MineshaftStructure$Type;Lnet/minecraft/util/math/BlockBox;)V", at = @At("RETURN"))
	private void eternalreturn$takeWood(StructurePieceType type, int chainLength, MineshaftStructure.Type mineshaftType, BlockBox box, CallbackInfo ci) {
		this.eternalreturn$wood = MineshaftWoods.current();
	}

	@Inject(method = "<init>(Lnet/minecraft/structure/StructurePieceType;Lnet/minecraft/nbt/NbtCompound;)V", at = @At("RETURN"))
	private void eternalreturn$readWood(StructurePieceType type, NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$wood = nbt.contains(WOOD_KEY) ? MineshaftWoods.byName(nbt.getString(WOOD_KEY)) : null;
	}

	@Inject(method = "writeNbt", at = @At("RETURN"))
	private void eternalreturn$writeWood(StructureContext context, NbtCompound nbt, CallbackInfo ci) {
		if (this.eternalreturn$wood != null) {
			nbt.putString(WOOD_KEY, this.eternalreturn$wood.name());
		}
	}

	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/structure/MineshaftStructure$Type;getPlanks()Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$planks(MineshaftStructure.Type type, Operation<BlockState> original) {
		return this.eternalreturn$wood != null ? this.eternalreturn$wood.planksState() : original.call(type);
	}

	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/structure/MineshaftStructure$Type;getLog()Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$log(MineshaftStructure.Type type, Operation<BlockState> original) {
		return this.eternalreturn$wood != null ? this.eternalreturn$wood.logState() : original.call(type);
	}

	@WrapOperation(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/structure/MineshaftStructure$Type;getFence()Lnet/minecraft/block/BlockState;"))
	private BlockState eternalreturn$fence(MineshaftStructure.Type type, Operation<BlockState> original) {
		return this.eternalreturn$wood != null ? this.eternalreturn$wood.fenceState() : original.call(type);
	}
}
