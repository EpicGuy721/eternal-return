package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Asylum papers, which every wandering trader sells: right-click a block (or the air) and a villager arrives,
 * an adult with no job yet, dressed for the biome. The papers are used up.
 */
public final class AsylumPapers extends Item {
	public static final Item ITEM = Registry.register(Registries.ITEM, EternalReturn.id("asylum_papers"),
			new AsylumPapers(new Item.Settings().maxCount(16).rarity(Rarity.UNCOMMON)));

	private AsylumPapers(Settings settings) {
		super(settings);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> entries.addAfter(Items.VILLAGER_SPAWN_EGG, ITEM));
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		BlockPos at = context.getBlockPos();
		if (!context.getWorld().getBlockState(at).getCollisionShape(context.getWorld(), at).isEmpty()) {
			at = at.offset(context.getSide());
		}
		return this.arrive(context.getWorld(), context.getPlayer(), context.getStack(), at);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		Vec3d ahead = user.getPos().add(user.getRotationVector().multiply(1.5).withAxis(net.minecraft.util.math.Direction.Axis.Y, 0.0));
		ActionResult result = this.arrive(world, user, stack, BlockPos.ofFloored(ahead));
		return new TypedActionResult<>(result, stack);
	}

	private ActionResult arrive(World world, PlayerEntity user, ItemStack stack, BlockPos at) {
		if (!(world instanceof ServerWorld server)) {
			return ActionResult.SUCCESS;
		}
		VillagerEntity villager = EntityType.VILLAGER.spawn(server, at, SpawnReason.SPAWN_EGG);
		if (villager == null) {
			return ActionResult.FAIL;
		}
		villager.setBreedingAge(0);
		server.spawnParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getBodyY(0.5), villager.getZ(), 12, 0.4, 0.6, 0.4, 0.0);
		world.playSound(null, at, SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
		if (user != null) {
			stack.decrementUnlessCreative(1, user);
		}
		return ActionResult.CONSUME;
	}
}
