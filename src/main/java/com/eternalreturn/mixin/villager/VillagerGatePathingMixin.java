package com.eternalreturn.mixin.villager;

import com.eternalreturn.villager.FenceGates;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathContext;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A villager's pathfinding takes a closed fence gate for a closed wooden door, which it can open (FenceGates). */
@Mixin(LandPathNodeMaker.class)
public abstract class VillagerGatePathingMixin extends PathNodeMaker {
	@WrapOperation(method = "getCollidingNodeTypes", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/ai/pathing/LandPathNodeMaker;getDefaultNodeType(Lnet/minecraft/entity/ai/pathing/PathContext;III)Lnet/minecraft/entity/ai/pathing/PathNodeType;"))
	private PathNodeType eternalreturn$gateIsADoor(LandPathNodeMaker maker, PathContext context, int x, int y, int z, Operation<PathNodeType> original) {
		PathNodeType type = original.call(maker, context, x, y, z);
		if (type == PathNodeType.FENCE && this.entity instanceof VillagerEntity && FenceGates.enabled()
				&& FenceGates.isClosedGate(context.getBlockState(new BlockPos(x, y, z)))) {
			return PathNodeType.DOOR_WOOD_CLOSED;
		}
		return type;
	}
}
