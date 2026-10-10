package com.eternalreturn.mixin.villager;

import com.eternalreturn.duck.NeedyVillager;
import com.eternalreturn.villager.TradeRules;
import com.eternalreturn.villager.VillagerNeeds;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradeOffers;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(VillagerEntity.class)
public abstract class VillagerEntityMixin implements NeedyVillager {
	@Unique
	private static final String NEEDS_KEY = "EternalReturnNeeds";

	/** When each need (VillagerNeeds.Need, in order) was last met on the day clock; MIN_VALUE until the first tick. */
	@Unique
	private final long[] eternalreturn$lastMet = eternalreturn$unset();
	@Unique
	@Nullable
	private Vec3d eternalreturn$walkStart;
	@Unique
	private int eternalreturn$stillTicks;

	@Unique
	private static long[] eternalreturn$unset() {
		long[] times = new long[VillagerNeeds.Need.values().length];
		Arrays.fill(times, Long.MIN_VALUE);
		return times;
	}

	/**
	 * Filters the trade pool right before the villager picks from it, so it covers both trade tables
	 * (normal and the experimental rebalance) and buy trades other mods register. The villager then
	 * picks its trades from what is left, so it still gets a full set where the pool allows.
	 */
	@WrapOperation(method = "fillRecipes", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/entity/passive/VillagerEntity;fillRecipesFromPool(Lnet/minecraft/village/TradeOfferList;[Lnet/minecraft/village/TradeOffers$Factory;I)V"))
	private void eternalreturn$dropBuyTrades(VillagerEntity villager, TradeOfferList offers, TradeOffers.Factory[] pool, int count, Operation<Void> original) {
		original.call(villager, offers, TradeRules.filter(villager.getVillagerData().getProfession(), pool), count);
	}

	// ---------------------------------------------------------------- needs (VillagerNeeds)

	@Override
	public long eternalreturn$lastMet(VillagerNeeds.Need need) {
		return this.eternalreturn$lastMet[need.ordinal()];
	}

	@Override
	public void eternalreturn$setLastMet(VillagerNeeds.Need need, long time) {
		this.eternalreturn$lastMet[need.ordinal()] = time;
	}

	@Override
	@Nullable
	public Vec3d eternalreturn$walkStart() {
		return this.eternalreturn$walkStart;
	}

	@Override
	public void eternalreturn$setWalkStart(@Nullable Vec3d start) {
		this.eternalreturn$walkStart = start;
	}

	@Override
	public int eternalreturn$stillTicks() {
		return this.eternalreturn$stillTicks;
	}

	@Override
	public void eternalreturn$setStillTicks(int ticks) {
		this.eternalreturn$stillTicks = ticks;
	}

	@Inject(method = "mobTick", at = @At("HEAD"))
	private void eternalreturn$tickNeeds(CallbackInfo ci) {
		VillagerNeeds.tick((VillagerEntity) (Object) this);
	}

	@Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
	private void eternalreturn$saveNeeds(NbtCompound nbt, CallbackInfo ci) {
		NbtCompound needs = new NbtCompound();
		for (VillagerNeeds.Need need : VillagerNeeds.Need.values()) {
			long time = this.eternalreturn$lastMet[need.ordinal()];
			if (time != Long.MIN_VALUE) {
				needs.putLong(need.key(), time);
			}
		}
		nbt.put(NEEDS_KEY, needs);
	}

	@Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
	private void eternalreturn$loadNeeds(NbtCompound nbt, CallbackInfo ci) {
		NbtCompound needs = nbt.getCompound(NEEDS_KEY);
		for (VillagerNeeds.Need need : VillagerNeeds.Need.values()) {
			this.eternalreturn$lastMet[need.ordinal()] = needs.contains(need.key(), NbtElement.LONG_TYPE) ? needs.getLong(need.key()) : Long.MIN_VALUE;
		}
	}

	/** Gossip only counts when it really happens (vanilla lets each pair share at most once a minute). */
	@Inject(method = "talkWithVillager", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/village/VillagerGossips;shareGossipFrom(Lnet/minecraft/village/VillagerGossips;Lnet/minecraft/util/math/random/Random;I)V"))
	private void eternalreturn$gossiped(ServerWorld world, VillagerEntity other, long time, CallbackInfo ci) {
		VillagerNeeds.gossiped((VillagerEntity) (Object) this);
		VillagerNeeds.gossiped(other);
	}

	/** Shop hours and refusals: when trading isn't allowed, the trade screen doesn't open. */
	@Inject(method = "beginTradeWith", at = @At("HEAD"), cancellable = true)
	private void eternalreturn$checkNeeds(PlayerEntity customer, CallbackInfo ci) {
		if (!VillagerNeeds.allowTrade((VillagerEntity) (Object) this, customer)) {
			ci.cancel();
		}
	}

	@Inject(method = "prepareOffersFor", at = @At("TAIL"))
	private void eternalreturn$raisePrices(PlayerEntity player, CallbackInfo ci) {
		VillagerNeeds.raisePrices((VillagerEntity) (Object) this);
	}
}
