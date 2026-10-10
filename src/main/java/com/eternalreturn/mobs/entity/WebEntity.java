package com.eternalreturn.mobs.entity;

import com.eternalreturn.mobs.ModEntities;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A ball of web a spider flings (SpiderWebGoal). When it hits a mob or player, a cobweb appears where they
 * stand (if there is room), and stays until broken; when it misses, it leaves nothing.
 */
public class WebEntity extends ThrownItemEntity {
	public WebEntity(EntityType<? extends WebEntity> type, World world) {
		super(type, world);
	}

	public WebEntity(World world, LivingEntity owner) {
		super(ModEntities.WEB, owner, world);
	}

	@Override
	protected Item getDefaultItem() {
		return Items.COBWEB;
	}

	@Override
	protected void onEntityHit(EntityHitResult hit) {
		super.onEntityHit(hit);
		if (this.getWorld() instanceof ServerWorld world && hit.getEntity() instanceof LivingEntity victim && victim != this.getOwner()) {
			BlockPos feet = victim.getBlockPos();
			if (world.getBlockState(feet).isReplaceable() && world.getFluidState(feet).isEmpty()) {
				world.setBlockState(feet, Blocks.COBWEB.getDefaultState());
			}
			world.playSound(null, feet, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.HOSTILE, 1.0F, 0.6F);
		}
	}

	@Override
	protected void onCollision(HitResult hit) {
		super.onCollision(hit);
		if (this.getWorld() instanceof ServerWorld world) {
			world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.COBWEB.getDefaultState()), this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
			this.discard();
		}
	}
}
