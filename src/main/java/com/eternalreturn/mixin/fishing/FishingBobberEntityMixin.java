package com.eternalreturn.mixin.fishing;

import com.eternalreturn.duck.BaitedHook;
import com.eternalreturn.fishing.Bait;
import com.eternalreturn.fishing.FishingLoot;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootTable;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.ReloadableRegistries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberEntityMixin extends ProjectileEntity implements BaitedHook {
	@Unique
	private static final int MIN_WAIT_TICKS = 20;

	/** The bait the current catch is being reeled in with; set when the rod is pulled. */
	@Unique
	private ItemStack eternalreturn$bait = ItemStack.EMPTY;

	@Shadow
	private int waitCountdown;

	protected FishingBobberEntityMixin(EntityType<? extends ProjectileEntity> entityType, World world) {
		super(entityType, world);
	}

	@Shadow
	@Nullable
	public abstract PlayerEntity getPlayerOwner();

	@Override
	public ItemStack eternalreturn$getBait() {
		return this.eternalreturn$bait;
	}

	/** Bobbers sit in lava for Nether fishing; without this they would burn there. */
	@Override
	public boolean isFireImmune() {
		return true;
	}

	/** A bobbing hook rides just above the top of its fluid block about half the time, so check below too. */
	@Unique
	private boolean eternalreturn$inLava() {
		World world = this.getWorld();
		BlockPos pos = this.getBlockPos();
		FluidState here = world.getFluidState(pos);
		boolean lava = here.isIn(FluidTags.LAVA) || (here.isEmpty() && world.getFluidState(pos.down()).isIn(FluidTags.LAVA));
		return lava && FishingLoot.lavaFishingAllowed(world);
	}

	// ---------------------------------------------------------------- floating and biting in lava

	/** tick() only floats and runs the fishing logic in water; let lava count where lava fishing is allowed. */
	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/fluid/FluidState;isIn(Lnet/minecraft/registry/tag/TagKey;)Z"))
	private boolean eternalreturn$lavaCountsAsWater(FluidState state, TagKey<Fluid> tag, Operation<Boolean> original) {
		return original.call(state, tag)
				|| (tag == FluidTags.WATER && state.isIn(FluidTags.LAVA) && FishingLoot.lavaFishingAllowed(this.getWorld()));
	}

	/** The fish-approach and splash effects only draw over water blocks. */
	@WrapOperation(method = "tickFishingLogic", at = @At(value = "INVOKE", target = "Lnet/minecraft/block/BlockState;isOf(Lnet/minecraft/block/Block;)Z"))
	private boolean eternalreturn$lavaSurface(BlockState state, Block block, Operation<Boolean> original) {
		return original.call(state, block) || (block == Blocks.WATER && state.isOf(Blocks.LAVA) && this.eternalreturn$inLava());
	}

	@WrapOperation(method = "tickFishingLogic", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/server/world/ServerWorld;spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I"))
	private int eternalreturn$lavaParticles(ServerWorld world, ParticleEffect particle, double x, double y, double z, int count,
			double dx, double dy, double dz, double speed, Operation<Integer> original) {
		ParticleEffect effect = this.eternalreturn$inLava() ? FishingLoot.lavaParticle(particle) : particle;
		return original.call(world, effect, x, y, z, count, dx, dy, dz, speed);
	}

	@WrapOperation(method = "tickFishingLogic", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/projectile/FishingBobberEntity;playSound(Lnet/minecraft/sound/SoundEvent;FF)V"))
	private void eternalreturn$lavaBiteSound(FishingBobberEntity self, SoundEvent sound, float volume, float pitch, Operation<Void> original) {
		original.call(self, this.eternalreturn$inLava() ? SoundEvents.BLOCK_LAVA_POP : sound, this.eternalreturn$inLava() ? 0.6F : volume, pitch);
	}

	/** Fishing under a roof bites half as often; the Nether is all roof, so lava ignores it. */
	@WrapOperation(method = "tickFishingLogic", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;isSkyVisible(Lnet/minecraft/util/math/BlockPos;)Z"))
	private boolean eternalreturn$lavaIgnoresRoof(World world, BlockPos pos, Operation<Boolean> original) {
		return this.eternalreturn$inLava() || original.call(world, pos);
	}

	// ---------------------------------------------------------------- bait

	/**
	 * Bait shortens the wait for a bite, on top of Lure. At this point waitCountdown holds the fresh
	 * 100-600 tick roll. Vanilla only moves on to a bite if the wait stays above zero, and rerolls
	 * otherwise, so a reduction of 600+ ticks (Lure V with any bait, Lure IV with chum) would mean
	 * nothing ever bites. Capping the reduction keeps every wait at least MIN_WAIT_TICKS long.
	 */
	@ModifyExpressionValue(method = "tickFishingLogic", at = @At(value = "FIELD",
			target = "Lnet/minecraft/entity/projectile/FishingBobberEntity;waitTimeReductionTicks:I"))
	private int eternalreturn$baitLure(int lureTicks) {
		PlayerEntity owner = this.getPlayerOwner();
		int reduction = owner == null ? lureTicks : lureTicks + Bait.lureTicks(Bait.find(owner, this.eternalreturn$inLava()), this.getWorld());
		return Math.min(reduction, Math.max(0, this.waitCountdown - MIN_WAIT_TICKS));
	}

	@Inject(method = "use", at = @At("HEAD"))
	private void eternalreturn$pickBait(ItemStack usedItem, CallbackInfoReturnable<Integer> cir) {
		PlayerEntity owner = this.getPlayerOwner();
		this.eternalreturn$bait = owner == null || this.getWorld().isClient ? ItemStack.EMPTY : Bait.find(owner, this.eternalreturn$inLava()).copyWithCount(1);
	}

	@WrapOperation(method = "use", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/registry/ReloadableRegistries$Lookup;getLootTable(Lnet/minecraft/registry/RegistryKey;)Lnet/minecraft/loot/LootTable;"))
	private LootTable eternalreturn$lavaLootTable(ReloadableRegistries.Lookup lookup, RegistryKey<LootTable> key, Operation<LootTable> original) {
		return original.call(lookup, this.eternalreturn$inLava() ? FishingLoot.LAVA_FISHING : key);
	}

	/** Bait is used up by a catch, not by casting, and Thriftiness on the rod can save it. */
	@ModifyExpressionValue(method = "use", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/loot/LootTable;generateLoot(Lnet/minecraft/loot/context/LootContextParameterSet;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
	private ObjectArrayList<ItemStack> eternalreturn$consumeBait(ObjectArrayList<ItemStack> loot, @Local(argsOnly = true) ItemStack rod) {
		PlayerEntity owner = this.getPlayerOwner();
		if (owner != null && !loot.isEmpty()) {
			Bait.consumeAfterCatch(owner, this.eternalreturn$bait, rod, this.random);
		}
		return loot;
	}

	/** A catch launched out of lava would burn up before it reached the player. */
	@WrapOperation(method = "use", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
	private boolean eternalreturn$fireproofCatch(World world, Entity entity, Operation<Boolean> original) {
		if (entity instanceof ItemEntity && this.eternalreturn$inLava()) {
			entity.setInvulnerable(true);
		}
		return original.call(world, entity);
	}
}
