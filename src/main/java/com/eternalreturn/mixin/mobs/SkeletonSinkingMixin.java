package com.eternalreturn.mixin.mobs;

import com.eternalreturn.items.Speargun;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.entity.SunkenEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.AbstractSkeletonEntity;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.world.World;
import net.minecraft.world.WorldEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A skeleton under water for UNDERWATER_TIME ticks becomes a Sunken (SunkenEntity) SINKING_TIME ticks later, as a zombie
 * becomes a drowned: its bow a speargun, the rest of its gear kept. Only skeletons (not strays, nor Sunkens themselves).
 */
@Mixin(SkeletonEntity.class)
public abstract class SkeletonSinkingMixin extends AbstractSkeletonEntity {
	@Unique
	private static final int UNDERWATER_TIME = 600;
	@Unique
	private static final int SINKING_TIME = 300;
	@Unique
	private int eternalreturn$underwaterTime;
	/** Ticks until it's a Sunken; -1 when it isn't becoming one. */
	@Unique
	private int eternalreturn$sinkingTime = -1;

	protected SkeletonSinkingMixin(EntityType<? extends AbstractSkeletonEntity> type, World world) {
		super(type, world);
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void eternalreturn$sink(CallbackInfo ci) {
		if (this.getWorld().isClient || !this.isAlive() || this.isAiDisabled() || this.getType() != EntityType.SKELETON) {
			return;
		}
		if (this.eternalreturn$sinkingTime >= 0) {
			if (--this.eternalreturn$sinkingTime < 0) {
				this.eternalreturn$becomeSunken();
			}
			return;
		}
		if (this.isSubmergedIn(FluidTags.WATER)) {
			if (++this.eternalreturn$underwaterTime >= UNDERWATER_TIME) {
				this.eternalreturn$sinkingTime = SINKING_TIME;
			}
		} else {
			this.eternalreturn$underwaterTime = 0;
		}
	}

	@Unique
	private void eternalreturn$becomeSunken() {
		SunkenEntity sunken = this.convertTo(NewMobs.SUNKEN, true);
		if (sunken == null) {
			return;
		}
		if (sunken.getMainHandStack().isOf(Items.BOW)) {
			sunken.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Speargun.SPEARGUN));
		}
		sunken.setVariant(this.random.nextInt(SunkenEntity.VARIANTS.length));
		if (!this.isSilent()) {
			this.getWorld().syncWorldEvent(null, WorldEvents.SKELETON_CONVERTS_TO_STRAY, this.getBlockPos(), 0);
		}
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$writeSinking(NbtCompound nbt, CallbackInfo ci) {
		nbt.putInt("EternalReturnUnderwaterTime", this.eternalreturn$underwaterTime);
		nbt.putInt("EternalReturnSunkenConversionTime", this.eternalreturn$sinkingTime);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$readSinking(NbtCompound nbt, CallbackInfo ci) {
		this.eternalreturn$underwaterTime = nbt.getInt("EternalReturnUnderwaterTime");
		this.eternalreturn$sinkingTime = nbt.contains("EternalReturnSunkenConversionTime") ? nbt.getInt("EternalReturnSunkenConversionTime") : -1;
	}
}
