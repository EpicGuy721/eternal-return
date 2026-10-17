package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Boats and chest boats of this mod's woods (com.eternalreturn.blocks.Woods). Vanilla's boat woods are a fixed
 * list, so each wood's boats are their own entity types (eternalreturn:maple_boat, ...), vanilla boats in every
 * way but the item they break into and their look (client WoodBoatRenderer).
 */
public final class WoodBoats {
	private static final Map<String, EntityType<Boat>> BOATS = new HashMap<>();
	private static final Map<String, EntityType<ChestBoat>> CHEST_BOATS = new HashMap<>();

	private WoodBoats() {
	}

	public static EntityType<Boat> boat(String wood) {
		return BOATS.get(wood);
	}

	public static EntityType<ChestBoat> chestBoat(String wood) {
		return CHEST_BOATS.get(wood);
	}

	/** The wood's boat entity types, as its items are registered. */
	public static void register(String wood) {
		String boat = wood + "_boat";
		BOATS.put(wood, Registry.register(Registries.ENTITY_TYPE, EternalReturn.id(boat),
				EntityType.Builder.<Boat>create(Boat::new, SpawnGroup.MISC).dimensions(1.375F, 0.5625F).eyeHeight(0.5625F).maxTrackingRange(10)
						.build(EternalReturn.id(boat).toString())));
		String chest = wood + "_chest_boat";
		CHEST_BOATS.put(wood, Registry.register(Registries.ENTITY_TYPE, EternalReturn.id(chest),
				EntityType.Builder.<ChestBoat>create(ChestBoat::new, SpawnGroup.MISC).dimensions(1.375F, 0.5625F).eyeHeight(0.5625F).maxTrackingRange(10)
						.build(EternalReturn.id(chest).toString())));
	}

	/** The wood a boat entity type is made of: its id without "_boat" or "_chest_boat". */
	public static String woodOf(Entity boat) {
		String path = Registries.ENTITY_TYPE.getId(boat.getType()).getPath();
		return path.substring(0, path.length() - (path.endsWith("_chest_boat") ? "_chest_boat".length() : "_boat".length()));
	}

	public static class Boat extends BoatEntity {
		public Boat(EntityType<? extends BoatEntity> type, World world) {
			super(type, world);
		}

		@Override
		public Item asItem() {
			return Registries.ITEM.get(EternalReturn.id(woodOf(this) + "_boat"));
		}
	}

	public static class ChestBoat extends ChestBoatEntity {
		public ChestBoat(EntityType<? extends BoatEntity> type, World world) {
			super(type, world);
		}

		@Override
		public Item asItem() {
			return Registries.ITEM.get(EternalReturn.id(woodOf(this) + "_chest_boat"));
		}
	}

	/** Places the wood's boat, as vanilla's boat items do. */
	public static class BoatItem extends Item {
		private static final Predicate<Entity> RIDERS = EntityPredicates.EXCEPT_SPECTATOR.and(Entity::canHit);
		private final String wood;
		private final boolean chest;

		public BoatItem(String wood, boolean chest, Settings settings) {
			super(settings);
			this.wood = wood;
			this.chest = chest;
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			HitResult hit = raycast(world, user, RaycastContext.FluidHandling.ANY);
			if (hit.getType() != HitResult.Type.BLOCK) {
				return TypedActionResult.pass(stack);
			}
			Vec3d look = user.getRotationVec(1.0F);
			List<Entity> near = world.getOtherEntities(user, user.getBoundingBox().stretch(look.multiply(5.0)).expand(1.0), RIDERS);
			Vec3d eye = user.getEyePos();
			for (Entity entity : near) {
				Box box = entity.getBoundingBox().expand(entity.getTargetingMargin());
				if (box.contains(eye)) {
					return TypedActionResult.pass(stack);
				}
			}
			BoatEntity boat = (this.chest ? chestBoat(this.wood) : boat(this.wood)).create(world);
			if (boat == null) {
				return TypedActionResult.fail(stack);
			}
			Vec3d pos = hit.getPos();
			boat.refreshPositionAndAngles(pos.x, pos.y, pos.z, user.getYaw(), 0.0F);
			if (world instanceof ServerWorld server) {
				EntityType.<BoatEntity>copier(server, stack, user).accept(boat);
			}
			if (!world.isSpaceEmpty(boat, boat.getBoundingBox())) {
				return TypedActionResult.fail(stack);
			}
			if (!world.isClient) {
				world.spawnEntity(boat);
				world.emitGameEvent(user, GameEvent.ENTITY_PLACE, pos);
				stack.decrementUnlessCreative(1, user);
			}
			user.incrementStat(Stats.USED.getOrCreateStat(this));
			return TypedActionResult.success(stack, world.isClient());
		}
	}
}
