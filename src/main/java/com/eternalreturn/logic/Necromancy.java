package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.Servants;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Necromancy, a sword enchantment, I to V: an undead mob the sword kills is stored in it, one per level (the Wither
 * can't be, nor anything that doesn't walk, like phantoms). The tooltip lists them ([Stored: Zombie]). Sneak and
 * right-click to raise the first one stored as a servant (com.eternalreturn.mobs.Servants): it follows you and fights
 * for you. Kept in the sword's custom data as eternalreturn_souls.
 */
public final class Necromancy {
	public static final Identifier ID = EternalReturn.id("necromancy");
	private static final String KEY = "eternalreturn_souls";

	private Necromancy() {
	}

	public static List<EntityType<?>> stored(ItemStack stack) {
		List<EntityType<?>> types = new ArrayList<>();
		NbtComponent data = stack.get(DataComponentTypes.CUSTOM_DATA);
		if (data == null) {
			return types;
		}
		NbtList list = data.copyNbt().getList(KEY, NbtElement.STRING_TYPE);
		for (int i = 0; i < list.size(); i++) {
			Identifier id = Identifier.tryParse(list.getString(i));
			if (id != null && Registries.ENTITY_TYPE.containsId(id)) {
				types.add(Registries.ENTITY_TYPE.get(id));
			}
		}
		return types;
	}

	private static void store(ItemStack stack, List<EntityType<?>> types) {
		NbtList list = new NbtList();
		for (EntityType<?> type : types) {
			list.add(NbtString.of(Registries.ENTITY_TYPE.getId(type).toString()));
		}
		NbtComponent.set(DataComponentTypes.CUSTOM_DATA, stack, nbt -> {
			if (list.isEmpty()) {
				nbt.remove(KEY);
			} else {
				nbt.put(KEY, list);
			}
		});
	}

	public static boolean storable(LivingEntity entity) {
		return entity.getType().isIn(EntityTypeTags.UNDEAD) && entity.getType() != EntityType.WITHER && entity instanceof PathAwareEntity
				&& !Servants.isServant(entity);
	}

	/** Lines for the sword's tooltip: one per stored mob. */
	public static List<Text> tooltip(ItemStack stack) {
		List<Text> lines = new ArrayList<>();
		for (EntityType<?> type : stored(stack)) {
			lines.add(Text.translatable("tooltip.eternalreturn.necromancy.stored", type.getName()).formatted(Formatting.DARK_PURPLE));
		}
		return lines;
	}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getAttacker() instanceof PlayerEntity player) || !storable(entity)) {
				return;
			}
			ItemStack sword = player.getMainHandStack();
			int level = NewEnchantments.level(sword, ID);
			List<EntityType<?>> types = stored(sword);
			if (level > 0 && types.size() < level) {
				types.add(entity.getType());
				store(sword, types);
			}
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getStackInHand(hand);
			if (!player.isSneaking() || NewEnchantments.level(stack, ID) <= 0) {
				return TypedActionResult.pass(stack);
			}
			List<EntityType<?>> types = stored(stack);
			if (types.isEmpty()) {
				return TypedActionResult.pass(stack);
			}
			if (world instanceof ServerWorld server) {
				@SuppressWarnings("unchecked")
				EntityType<? extends MobEntity> kind = (EntityType<? extends MobEntity>) types.remove(0);
				MobEntity raised = Servants.summon(server, player, spot(player), kind);
				if (raised != null) {
					store(stack, types);
				}
			}
			player.swingHand(hand);
			return TypedActionResult.success(stack, world.isClient);
		});
	}

	/** Where to raise it: on the block looked at within 5 blocks, or 2 blocks ahead. */
	private static Vec3d spot(PlayerEntity player) {
		Vec3d eye = player.getEyePos();
		Vec3d look = player.getRotationVec(1.0F);
		BlockHitResult hit = player.getWorld().raycast(new RaycastContext(eye, eye.add(look.multiply(5.0)), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, player));
		if (hit.getType() == HitResult.Type.BLOCK) {
			return Vec3d.ofBottomCenter(hit.getBlockPos().offset(hit.getSide()));
		}
		Vec3d ahead = player.getPos().add(look.x * 2.0, 0.0, look.z * 2.0);
		return new Vec3d(ahead.x, player.getY(), ahead.z);
	}
}
