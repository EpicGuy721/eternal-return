package com.eternalreturn.mixin.tweaks;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.mob.VexEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mobs that pass through blocks (vexes) can't pass crying obsidian: a move that would take one into it stops short on
 * that axis.
 */
@Mixin(Entity.class)
public abstract class VexCryingObsidianMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3d eternalreturn$blockedByCryingObsidian(Vec3d movement, MovementType type) {
		Entity self = (Entity) (Object) this;
		if (!(self instanceof VexEntity) || !self.noClip || movement.lengthSquared() < 1.0E-7) {
			return movement;
		}
		World world = self.getWorld();
		Box box = self.getBoundingBox();
		double x = eternalreturn$enters(world, box.offset(movement.x, 0.0, 0.0)) ? 0.0 : movement.x;
		Box afterX = box.offset(x, 0.0, 0.0);
		double y = eternalreturn$enters(world, afterX.offset(0.0, movement.y, 0.0)) ? 0.0 : movement.y;
		Box afterY = afterX.offset(0.0, y, 0.0);
		double z = eternalreturn$enters(world, afterY.offset(0.0, 0.0, movement.z)) ? 0.0 : movement.z;
		if (x != movement.x || y != movement.y || z != movement.z) {
			Vec3d velocity = self.getVelocity();
			self.setVelocity(x == 0.0 ? 0.0 : velocity.x, y == 0.0 ? 0.0 : velocity.y, z == 0.0 ? 0.0 : velocity.z);
			return new Vec3d(x, y, z);
		}
		return movement;
	}

	@Unique
	private static boolean eternalreturn$enters(World world, Box box) {
		for (BlockPos pos : BlockPos.iterate(BlockPos.ofFloored(box.minX, box.minY, box.minZ), BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
			if (world.getBlockState(pos).isOf(Blocks.CRYING_OBSIDIAN)) {
				return true;
			}
		}
		return false;
	}
}
