package com.eternalreturn.client;

import com.eternalreturn.duck.MovedData;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.nbt.NbtCompound;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws the block entity of a block a piston is moving (Redstone moves them), from a stand-in made from the
 * moving block's state and the data it carries, so chests, signs and the like stay visible as they slide.
 */
public final class MovingBlockEntities {
	private static final Map<PistonBlockEntity, BlockEntity> STAND_INS = new WeakHashMap<>();

	private MovingBlockEntities() {
	}

	public static void render(PistonBlockEntity piston, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light, int overlay) {
		BlockState pushed = piston.getPushedBlock();
		if (!pushed.hasBlockEntity() || piston.getWorld() == null || !(pushed.getBlock() instanceof BlockEntityProvider provider)) {
			return;
		}
		BlockEntity standIn = STAND_INS.computeIfAbsent(piston, key -> {
			BlockEntity created = provider.createBlockEntity(piston.getPos(), pushed);
			if (created != null) {
				created.setWorld(piston.getWorld());
				NbtCompound data = ((MovedData) piston).eternalreturn$movedData();
				if (data != null) {
					created.read(data, piston.getWorld().getRegistryManager());
				}
			}
			return created;
		});
		if (standIn == null) {
			return;
		}
		matrices.push();
		matrices.translate(piston.getRenderOffsetX(tickDelta), piston.getRenderOffsetY(tickDelta), piston.getRenderOffsetZ(tickDelta));
		MinecraftClient.getInstance().getBlockEntityRenderDispatcher().renderEntity(standIn, matrices, vertices, light, overlay);
		matrices.pop();
	}
}
