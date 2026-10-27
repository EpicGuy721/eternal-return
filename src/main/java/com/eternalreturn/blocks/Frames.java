package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.BlockState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

/**
 * Item frame additions:
 * <ul>
 * <li>glass item frames: an empty one shows a thin glass frame; once it holds something the frame
 * disappears and only the item shows, flat against the wall. Right-clicking one that holds an item opens
 * the chest (furnace, barrel...) it hangs on, instead of turning the item; sneak to turn it. Crafted from
 * 8 glass panes around leather; a glowing glass item frame is one plus a glowstone (as a glow item frame is now
 * an item frame plus a glowstone, not a glow ink sac);</li>
 * <li>item frames of every kind can be dyed in a crafting grid like leather armour (any mix of dyes), the dye
 * colouring the back (client FrameModels); the frame keeps its colour when placed and when picked up again.</li>
 * </ul>
 */
public final class Frames {
	public static final EntityType<GlassItemFrameEntity> GLASS_ITEM_FRAME = registerType("glass_item_frame");
	public static final EntityType<GlassItemFrameEntity> GLOWING_GLASS_ITEM_FRAME = registerType("glowing_glass_item_frame");
	public static final Item GLASS_ITEM_FRAME_ITEM = Registry.register(Registries.ITEM, EternalReturn.id("glass_item_frame"), new GlassFrameItem(false, new Item.Settings()));
	public static final Item GLOWING_GLASS_ITEM_FRAME_ITEM = Registry.register(Registries.ITEM, EternalReturn.id("glowing_glass_item_frame"),
			new GlassFrameItem(true, new Item.Settings()));

	private Frames() {
	}

	private static EntityType<GlassItemFrameEntity> registerType(String id) {
		return Registry.register(Registries.ENTITY_TYPE, EternalReturn.id(id),
				EntityType.Builder.<GlassItemFrameEntity>create(GlassItemFrameEntity::new, SpawnGroup.MISC)
						.dimensions(0.5F, 0.5F).eyeHeight(0.0F).maxTrackingRange(10).trackingTickInterval(Integer.MAX_VALUE)
						.build(EternalReturn.id(id).toString()));
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> entries.addAfter(Items.GLOW_ITEM_FRAME, GLASS_ITEM_FRAME_ITEM, GLOWING_GLASS_ITEM_FRAME_ITEM));
	}

	/** A frame's colour (Frames, dyed), or -1 for none. */
	public interface Dyed {
		int eternalreturn$color();

		void eternalreturn$setColor(int color);
	}

	/** The item a frame drops, coloured like the frame. */
	public static ItemStack colored(ItemFrameEntity frame, ItemStack stack) {
		int color = ((Dyed) frame).eternalreturn$color();
		if (color >= 0 && !stack.isEmpty()) {
			stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color, true));
		}
		return stack;
	}

	/** A frame takes the colour of the item it was placed from. */
	public static void colorFrom(ItemFrameEntity frame, ItemStack stack) {
		DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
		if (dyed != null) {
			((Dyed) frame).eternalreturn$setColor(dyed.rgb() & 0xFFFFFF);
		}
	}

	public static class GlassItemFrameEntity extends ItemFrameEntity {
		public GlassItemFrameEntity(EntityType<? extends ItemFrameEntity> type, World world) {
			super(type, world);
		}

		public GlassItemFrameEntity(EntityType<? extends ItemFrameEntity> type, World world, BlockPos pos, Direction facing) {
			super(type, world, pos, facing);
		}

		/** The glowing kind (not Entity.isGlowing, the Glowing effect's outline). */
		public boolean isGlowingFrame() {
			return this.getType() == GLOWING_GLASS_ITEM_FRAME;
		}

		/** Holding something, the frame itself isn't drawn: the item sits flat on the wall, as in an invisible frame. */
		@Override
		public boolean isInvisible() {
			return super.isInvisible() || !this.getHeldItemStack().isEmpty();
		}

		@Override
		protected ItemStack getAsItemStack() {
			return colored(this, new ItemStack(this.isGlowingFrame() ? GLOWING_GLASS_ITEM_FRAME_ITEM : GLASS_ITEM_FRAME_ITEM));
		}

		/** With an item in it, a click goes through to the container behind (sneak to turn the item instead). */
		@Override
		public ActionResult interact(PlayerEntity player, Hand hand) {
			if (!player.isSneaking() && !this.getHeldItemStack().isEmpty()) {
				Direction facing = this.getHorizontalFacing();
				BlockPos behind = this.getAttachedBlockPos().offset(facing.getOpposite());
				World world = this.getWorld();
				BlockState state = world.getBlockState(behind);
				if (state.createScreenHandlerFactory(world, behind) != null) {
					if (!world.isClient) {
						state.onUse(world, player, new BlockHitResult(Vec3d.ofCenter(behind), facing, behind, false));
					}
					return ActionResult.success(world.isClient);
				}
			}
			return super.interact(player, hand);
		}
	}

	public static class GlassFrameItem extends Item {
		private final boolean glowing;

		public GlassFrameItem(boolean glowing, Settings settings) {
			super(settings);
			this.glowing = glowing;
		}

		/** Placed like a vanilla item frame, on any face. */
		@Override
		public ActionResult useOnBlock(ItemUsageContext context) {
			Direction side = context.getSide();
			BlockPos pos = context.getBlockPos().offset(side);
			PlayerEntity player = context.getPlayer();
			ItemStack stack = context.getStack();
			World world = context.getWorld();
			if (player != null && (world.isOutOfHeightLimit(pos) || !player.canPlaceOn(pos, side, stack))) {
				return ActionResult.FAIL;
			}
			GlassItemFrameEntity frame = new GlassItemFrameEntity(this.glowing ? GLOWING_GLASS_ITEM_FRAME : GLASS_ITEM_FRAME, world, pos, side);
			NbtComponent data = stack.getOrDefault(DataComponentTypes.ENTITY_DATA, NbtComponent.DEFAULT);
			if (!data.isEmpty()) {
				EntityType.loadFromEntityNbt(world, player, frame, data);
			}
			colorFrom(frame, stack);
			if (!frame.canStayAttached()) {
				return ActionResult.CONSUME;
			}
			if (!world.isClient) {
				frame.onPlace();
				world.emitGameEvent(player, GameEvent.ENTITY_PLACE, frame.getPos());
				world.spawnEntity(frame);
			}
			stack.decrement(1);
			return ActionResult.success(world.isClient);
		}
	}
}
