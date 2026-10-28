package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.fabricmc.fabric.api.blockview.v2.RenderDataBlockEntity;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeveledCauldronBlock;
import net.minecraft.block.RedstoneLampBlock;
import net.minecraft.block.cauldron.CauldronBehavior;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.DyeColor;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Dyeing with water and lamps in any colour:
 * <ul>
 * <li>a dye in a water cauldron colours the water (a dyed water cauldron), and more dyes mix in as dyes mix on leather;
 * a dyeable item (leather armor, item frames, redstone lamps: item tag minecraft:dyeable) dipped in takes the water's
 * colour, a level of it used up; a water bucket washes the dye out, a bucket takes the full cauldron's water back;</li>
 * <li>a redstone lamp dyed in a crafting grid (it's dyeable, as leather is) or in a cauldron places as a dyed redstone
 * lamp, which keeps the colour (in its block entity) and works as a lamp, its glass lit or unlit in that colour, and
 * drops (and picks) as the dyed lamp item (RedstoneLampItemMixin).</li>
 * </ul>
 */
public final class Dyeing {
	public static final CauldronBehavior.CauldronBehaviorMap DYED_WATER_BEHAVIOR = CauldronBehavior.createMap("eternalreturn_dyed_water");
	public static final Block DYED_WATER_CAULDRON = Registry.register(Registries.BLOCK, EternalReturn.id("dyed_water_cauldron"),
			new DyedCauldronBlock(AbstractBlock.Settings.copy(Blocks.WATER_CAULDRON)));
	public static final Block DYED_REDSTONE_LAMP = Registry.register(Registries.BLOCK, EternalReturn.id("dyed_redstone_lamp"),
			new DyedLampBlock(AbstractBlock.Settings.copy(Blocks.REDSTONE_LAMP)));
	public static final BlockEntityType<DyedBlockEntity> DYED_BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, EternalReturn.id("dyed"),
			BlockEntityType.Builder.create(DyedBlockEntity::new, DYED_WATER_CAULDRON, DYED_REDSTONE_LAMP).build());

	private Dyeing() {
	}

	public static void register() {
		for (DyeColor color : DyeColor.values()) {
			DyeItem dye = DyeItem.byColor(color);
			CauldronBehavior.WATER_CAULDRON_BEHAVIOR.map().put(dye, (state, world, pos, player, hand, stack) -> {
				if (!world.isClient) {
					world.setBlockState(pos, DYED_WATER_CAULDRON.getDefaultState().with(LeveledCauldronBlock.LEVEL, state.get(LeveledCauldronBlock.LEVEL)));
					if (world.getBlockEntity(pos) instanceof DyedBlockEntity dyed) {
						dyed.setColor(color.getEntityColor());
					}
					used(world, pos, player, stack);
				}
				return ItemActionResult.success(world.isClient);
			});
			DYED_WATER_BEHAVIOR.map().put(dye, (state, world, pos, player, hand, stack) -> {
				if (!world.isClient && world.getBlockEntity(pos) instanceof DyedBlockEntity dyed) {
					dyed.setColor(mix(dyed.color(), color.getEntityColor()));
					used(world, pos, player, stack);
				}
				return ItemActionResult.success(world.isClient);
			});
		}
		DYED_WATER_BEHAVIOR.map().put(Items.WATER_BUCKET, CauldronBehavior.FILL_WITH_WATER);
		DYED_WATER_BEHAVIOR.map().put(Items.BUCKET, (state, world, pos, player, hand, stack) -> CauldronBehavior.emptyCauldron(state, world, pos, player, hand,
				stack, new ItemStack(Items.WATER_BUCKET), full -> full.get(LeveledCauldronBlock.LEVEL) == 3, SoundEvents.ITEM_BUCKET_FILL));
		// Anything dyeable dipped in takes the colour (the map's default: the tag isn't known yet; the map is fastutil's).
		((Object2ObjectMap<Item, CauldronBehavior>) DYED_WATER_BEHAVIOR.map()).defaultReturnValue((state, world, pos, player, hand, stack) -> {
			if (!stack.isIn(ItemTags.DYEABLE)) {
				return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
			}
			if (!world.isClient && world.getBlockEntity(pos) instanceof DyedBlockEntity dyed) {
				stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(dyed.color(), true));
				player.incrementStat(Stats.USE_CAULDRON);
				world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 0.6F, 1.4F);
				LeveledCauldronBlock.decrementFluidLevel(state, world, pos);
			}
			return ItemActionResult.success(world.isClient);
		});
	}

	private static void used(World world, BlockPos pos, net.minecraft.entity.player.PlayerEntity player, ItemStack dye) {
		dye.decrementUnlessCreative(1, player);
		player.incrementStat(Stats.USE_CAULDRON);
		world.playSound(null, pos, SoundEvents.ITEM_DYE_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
		world.emitGameEvent(player, GameEvent.BLOCK_CHANGE, pos);
	}

	/** Two colours mixed as dyes mix on leather (DyedColorComponent): averaged, as bright as their brightest channels. */
	public static int mix(int a, int b) {
		int r = (a >> 16 & 255) + (b >> 16 & 255);
		int g = (a >> 8 & 255) + (b >> 8 & 255);
		int bl = (a & 255) + (b & 255);
		float brightest = (Math.max(a >> 16 & 255, Math.max(a >> 8 & 255, a & 255)) + Math.max(b >> 16 & 255, Math.max(b >> 8 & 255, b & 255))) / 2.0F;
		r /= 2;
		g /= 2;
		bl /= 2;
		float max = Math.max(r, Math.max(g, bl));
		if (max > 0.0F) {
			r = (int) (r * brightest / max);
			g = (int) (g * brightest / max);
			bl = (int) (bl * brightest / max);
		}
		return r << 16 | g << 8 | bl;
	}

	/**
	 * A colour kept by a block: a dyed water cauldron's or a dyed lamp's (drawn by tinting: getRenderData). On the
	 * client it's UNKNOWN until the server's data comes (a cauldron just dyed draws its water plain meanwhile, not white).
	 */
	public static class DyedBlockEntity extends BlockEntity implements RenderDataBlockEntity {
		public static final int UNKNOWN = -1;
		private int color = UNKNOWN;

		public DyedBlockEntity(BlockPos pos, BlockState state) {
			super(DYED_BLOCK_ENTITY, pos, state);
		}

		public int color() {
			return this.color == UNKNOWN ? 0xFFFFFF : this.color;
		}

		public void setColor(int color) {
			this.color = color & 0xFFFFFF;
			this.markDirty();
			if (this.world != null) {
				this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
			}
		}

		@Override
		protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			super.writeNbt(nbt, registries);
			if (this.color != UNKNOWN) {
				nbt.putInt("Color", this.color);
			}
		}

		@Override
		protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
			super.readNbt(nbt, registries);
			this.color = nbt.contains("Color") ? nbt.getInt("Color") : UNKNOWN;
			// (on the client, drawn again in the new colour)
			if (this.world != null && this.world.isClient) {
				this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), Block.NOTIFY_ALL);
			}
		}

		@Override
		protected void readComponents(BlockEntity.ComponentsAccess components) {
			super.readComponents(components);
			DyedColorComponent dyed = components.get(DataComponentTypes.DYED_COLOR);
			if (dyed != null) {
				this.color = dyed.rgb() & 0xFFFFFF;
			}
		}

		@Override
		protected void addComponents(ComponentMap.Builder builder) {
			super.addComponents(builder);
			builder.add(DataComponentTypes.DYED_COLOR, new DyedColorComponent(this.color(), true));
		}

		@Nullable
		@Override
		public Packet<ClientPlayPacketListener> toUpdatePacket() {
			return BlockEntityUpdateS2CPacket.create(this);
		}

		@Override
		public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
			return this.createNbt(registries);
		}

		@Nullable
		@Override
		public Object getRenderData() {
			return this.color == UNKNOWN ? null : this.color;
		}
	}

	/** Water with dye in it. */
	public static class DyedCauldronBlock extends LeveledCauldronBlock implements BlockEntityProvider {
		public DyedCauldronBlock(Settings settings) {
			super(Biome.Precipitation.RAIN, DYED_WATER_BEHAVIOR, settings);
		}

		@Override
		public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
			return new DyedBlockEntity(pos, state);
		}
	}

	/** A redstone lamp in a colour of its own. */
	public static class DyedLampBlock extends RedstoneLampBlock implements BlockEntityProvider {
		public DyedLampBlock(Settings settings) {
			super(settings);
		}

		@Override
		public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
			return new DyedBlockEntity(pos, state);
		}

		@Override
		public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
			ItemStack stack = new ItemStack(Items.REDSTONE_LAMP);
			if (world.getBlockEntity(pos) instanceof DyedBlockEntity dyed) {
				stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(dyed.color(), true));
			}
			return stack;
		}
	}
}
