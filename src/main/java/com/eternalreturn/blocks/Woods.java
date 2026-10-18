package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.items.WoodBoats;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.type.BlockSetTypeBuilder;
import net.fabricmc.fabric.api.object.builder.v1.block.type.WoodTypeBuilder;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.fabricmc.fabric.api.registry.CompostingChanceRegistry;
import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.fabric.api.registry.StrippableBlockRegistry;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.FlowerPotBlock;
import net.minecraft.block.FlowerbedBlock;
import net.minecraft.block.HangingSignBlock;
import net.minecraft.block.LeavesBlock;
import net.minecraft.block.MapColor;
import net.minecraft.block.PillarBlock;
import net.minecraft.block.PressurePlateBlock;
import net.minecraft.block.SaplingBlock;
import net.minecraft.block.SaplingGenerator;
import net.minecraft.block.SignBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.WallHangingSignBlock;
import net.minecraft.block.WallSignBlock;
import net.minecraft.block.WoodType;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HangingSignItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.Items;
import net.minecraft.item.SignItem;
import net.minecraft.item.TallBlockItem;
import net.minecraft.particle.ParticleUtil;
import net.minecraft.particle.SimpleParticleType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Maple and pale oak, the autumnal forest's woods: each a full wood set like cherry's (log, wood, stripped log and
 * wood, planks, stairs, slab, fence, fence gate, door, trapdoor, pressure plate, button, sign, hanging sign,
 * leaves, sapling, potted sapling, boat and chest boat; WoodBoats), plus maple petals, fallen maple leaves that lie
 * on the ground like pink petals. Maple leaves shed falling leaves as cherry leaves do; pale oak's leaves come in
 * red, orange and yellow, and its sapling grows a tree of any of the three. Resources from tools/woods/build_woods.py.
 */
public final class Woods {
	public static final SimpleParticleType MAPLE_LEAF_PARTICLE = Registry.register(Registries.PARTICLE_TYPE, EternalReturn.id("maple_leaves"),
			FabricParticleTypes.simple());

	public static final Wood MAPLE = new Wood("maple", MapColor.ORANGE, MapColor.BROWN, List.of("maple_leaves"), true, true);
	public static final Wood PALE_OAK = new Wood("pale_oak", MapColor.OFF_WHITE, MapColor.LIGHT_GRAY,
			List.of("red_pale_oak_leaves", "orange_pale_oak_leaves", "yellow_pale_oak_leaves"), false, true);
	/** Azalea: its trees wear vanilla's azalea leaves, and vanilla's azalea bush is its sapling, so it has neither. */
	public static final Wood AZALEA = new Wood("azalea", MapColor.LIME, MapColor.TERRACOTTA_BROWN, List.of(), false, false);

	public static final Block MAPLE_PETALS = block("maple_petals", new FlowerbedBlock(AbstractBlock.Settings.copy(Blocks.PINK_PETALS).mapColor(MapColor.ORANGE)));
	public static final Item MAPLE_PETALS_ITEM = item("maple_petals", new BlockItem(MAPLE_PETALS, new Item.Settings()));

	private Woods() {
	}

	/** Leaves that now and then drop a falling maple leaf below them, as cherry leaves do their petals. */
	public static class MapleLeavesBlock extends LeavesBlock {
		public MapleLeavesBlock(Settings settings) {
			super(settings);
		}

		@Override
		public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
			super.randomDisplayTick(state, world, pos, random);
			if (random.nextInt(10) == 0) {
				BlockPos below = pos.down();
				if (!isFaceFullSquare(world.getBlockState(below).getCollisionShape(world, below), Direction.UP)) {
					ParticleUtil.spawnParticle(world, pos, random, MAPLE_LEAF_PARTICLE);
				}
			}
		}
	}

	/** One wood's blocks and items. */
	public static final class Wood {
		public final String name;
		public final BlockSetType setType;
		public final WoodType woodType;
		public final Block log;
		public final Block wood;
		public final Block strippedLog;
		public final Block strippedWood;
		public final Block planks;
		public final Block stairs;
		public final Block slab;
		public final Block fence;
		public final Block fenceGate;
		public final Block door;
		public final Block trapdoor;
		public final Block pressurePlate;
		public final Block button;
		public final Block sign;
		public final Block wallSign;
		public final Block hangingSign;
		public final Block wallHangingSign;
		public final List<Block> leaves = new ArrayList<>();
		/** Null for azalea (vanilla's azalea bush). */
		@org.jetbrains.annotations.Nullable
		public final Block sapling;
		@org.jetbrains.annotations.Nullable
		public final Block pottedSapling;
		public final Item signItem;
		public final Item hangingSignItem;
		public final Item boat;
		public final Item chestBoat;
		/** Building blocks, in creative-tab order. */
		final List<Item> building = new ArrayList<>();

		Wood(String name, MapColor planksColor, MapColor barkColor, List<String> leafNames, boolean fallingLeaves, boolean ownSapling) {
			this.name = name;
			this.setType = BlockSetTypeBuilder.copyOf(BlockSetType.OAK).register(EternalReturn.id(name));
			this.woodType = WoodTypeBuilder.copyOf(WoodType.OAK).register(EternalReturn.id(name), this.setType);
			this.log = blockWithItem(name + "_log", new PillarBlock(AbstractBlock.Settings.copy(Blocks.OAK_LOG)
					.mapColor(state -> state.get(PillarBlock.AXIS) == Direction.Axis.Y ? planksColor : barkColor)));
			this.wood = blockWithItem(name + "_wood", new PillarBlock(AbstractBlock.Settings.copy(Blocks.OAK_WOOD).mapColor(barkColor)));
			this.strippedLog = blockWithItem("stripped_" + name + "_log", new PillarBlock(AbstractBlock.Settings.copy(Blocks.STRIPPED_OAK_LOG).mapColor(planksColor)));
			this.strippedWood = blockWithItem("stripped_" + name + "_wood", new PillarBlock(AbstractBlock.Settings.copy(Blocks.STRIPPED_OAK_WOOD).mapColor(planksColor)));
			this.planks = blockWithItem(name + "_planks", new Block(AbstractBlock.Settings.copy(Blocks.OAK_PLANKS).mapColor(planksColor)));
			this.stairs = blockWithItem(name + "_stairs", new StairsBlock(this.planks.getDefaultState(), AbstractBlock.Settings.copy(this.planks)));
			this.slab = blockWithItem(name + "_slab", new SlabBlock(AbstractBlock.Settings.copy(this.planks)));
			this.fence = blockWithItem(name + "_fence", new FenceBlock(AbstractBlock.Settings.copy(Blocks.OAK_FENCE).mapColor(planksColor)));
			this.fenceGate = blockWithItem(name + "_fence_gate", new FenceGateBlock(this.woodType, AbstractBlock.Settings.copy(Blocks.OAK_FENCE_GATE).mapColor(planksColor)));
			this.door = block(name + "_door", new DoorBlock(this.setType, AbstractBlock.Settings.copy(Blocks.OAK_DOOR).mapColor(planksColor)));
			item(name + "_door", new TallBlockItem(this.door, new Item.Settings()));
			this.building.add(this.door.asItem());
			this.trapdoor = blockWithItem(name + "_trapdoor", new TrapdoorBlock(this.setType, AbstractBlock.Settings.copy(Blocks.OAK_TRAPDOOR).mapColor(planksColor)));
			this.pressurePlate = blockWithItem(name + "_pressure_plate",
					new PressurePlateBlock(this.setType, AbstractBlock.Settings.copy(Blocks.OAK_PRESSURE_PLATE).mapColor(planksColor)));
			this.button = blockWithItem(name + "_button", new ButtonBlock(this.setType, 30, AbstractBlock.Settings.copy(Blocks.OAK_BUTTON)));
			this.sign = block(name + "_sign", new SignBlock(this.woodType, AbstractBlock.Settings.copy(Blocks.OAK_SIGN).mapColor(planksColor)));
			this.wallSign = block(name + "_wall_sign", new WallSignBlock(this.woodType, AbstractBlock.Settings.copy(Blocks.OAK_WALL_SIGN)
					.mapColor(planksColor).dropsLike(this.sign)));
			this.hangingSign = block(name + "_hanging_sign", new HangingSignBlock(this.woodType, AbstractBlock.Settings.copy(Blocks.OAK_HANGING_SIGN)
					.mapColor(planksColor)));
			this.wallHangingSign = block(name + "_wall_hanging_sign", new WallHangingSignBlock(this.woodType,
					AbstractBlock.Settings.copy(Blocks.OAK_WALL_HANGING_SIGN).mapColor(planksColor).dropsLike(this.hangingSign)));
			this.signItem = item(name + "_sign", new SignItem(new Item.Settings().maxCount(16), this.sign, this.wallSign));
			this.hangingSignItem = item(name + "_hanging_sign", new HangingSignItem(this.hangingSign, this.wallHangingSign, new Item.Settings().maxCount(16)));
			for (String leafName : leafNames) {
				AbstractBlock.Settings settings = AbstractBlock.Settings.copy(Blocks.OAK_LEAVES).mapColor(planksColor);
				this.leaves.add(blockWithItem(leafName, fallingLeaves ? new MapleLeavesBlock(settings) : new LeavesBlock(settings)));
			}
			this.building.removeAll(this.leaves.stream().map(Block::asItem).toList());
			if (ownSapling) {
				RegistryKey<net.minecraft.world.gen.feature.ConfiguredFeature<?, ?>> tree = RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, EternalReturn.id(name));
				SaplingGenerator generator = new SaplingGenerator(EternalReturn.id(name).toString(), Optional.empty(), Optional.of(tree), Optional.empty());
				this.sapling = block(name + "_sapling", new SaplingBlock(generator, AbstractBlock.Settings.copy(Blocks.OAK_SAPLING)));
				item(name + "_sapling", new BlockItem(this.sapling, new Item.Settings()));
				this.pottedSapling = block("potted_" + name + "_sapling", new FlowerPotBlock(this.sapling, AbstractBlock.Settings.copy(Blocks.POTTED_OAK_SAPLING)));
			} else {
				this.sapling = null;
				this.pottedSapling = null;
			}
			WoodBoats.register(name);
			this.boat = item(name + "_boat", new WoodBoats.BoatItem(name, false, new Item.Settings().maxCount(1)));
			this.chestBoat = item(name + "_chest_boat", new WoodBoats.BoatItem(name, true, new Item.Settings().maxCount(1)));
		}

		private Block blockWithItem(String id, Block block) {
			block(id, block);
			this.building.add(item(id, new BlockItem(block, new Item.Settings())));
			return block;
		}

		void register() {
			BlockEntityType.SIGN.addSupportedBlock(this.sign);
			BlockEntityType.SIGN.addSupportedBlock(this.wallSign);
			BlockEntityType.HANGING_SIGN.addSupportedBlock(this.hangingSign);
			BlockEntityType.HANGING_SIGN.addSupportedBlock(this.wallHangingSign);
			StrippableBlockRegistry.register(this.log, this.strippedLog);
			StrippableBlockRegistry.register(this.wood, this.strippedWood);
			FlammableBlockRegistry flammable = FlammableBlockRegistry.getDefaultInstance();
			for (Block block : List.of(this.log, this.wood, this.strippedLog, this.strippedWood)) {
				flammable.add(block, 5, 5);
			}
			for (Block block : List.of(this.planks, this.stairs, this.slab, this.fence, this.fenceGate)) {
				flammable.add(block, 5, 20);
			}
			for (Block leaf : this.leaves) {
				flammable.add(leaf, 30, 60);
				CompostingChanceRegistry.INSTANCE.add(leaf, 0.3F);
			}
			if (this.sapling != null) {
				CompostingChanceRegistry.INSTANCE.add(this.sapling, 0.3F);
			}
		}
	}

	private static Block block(String id, Block block) {
		return Registry.register(Registries.BLOCK, EternalReturn.id(id), block);
	}

	private static Item item(String id, Item item) {
		return Registry.register(Registries.ITEM, EternalReturn.id(id), item);
	}

	public static List<Wood> all() {
		return List.of(MAPLE, PALE_OAK, AZALEA);
	}

	public static void register() {
		for (Wood wood : all()) {
			wood.register();
		}
		FlammableBlockRegistry.getDefaultInstance().add(MAPLE_PETALS, 60, 100);
		CompostingChanceRegistry.INSTANCE.add(MAPLE_PETALS, 0.3F);
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> {
			Item after = Items.CHERRY_BUTTON;
			for (Wood wood : all()) {
				for (Item item : wood.building) {
					entries.addAfter(after, item);
					after = item;
				}
			}
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.NATURAL).register(entries -> {
			Item log = Items.CHERRY_LOG;
			Item leaves = Items.CHERRY_LEAVES;
			Item sapling = Items.CHERRY_SAPLING;
			for (Wood wood : all()) {
				entries.addAfter(log, wood.log);
				log = wood.log.asItem();
				for (Block leaf : wood.leaves) {
					entries.addAfter(leaves, leaf);
					leaves = leaf.asItem();
				}
				if (wood.sapling != null) {
					entries.addAfter(sapling, wood.sapling);
					sapling = wood.sapling.asItem();
				}
			}
			entries.addAfter(Items.PINK_PETALS, MAPLE_PETALS_ITEM);
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
			Item after = Items.CHERRY_HANGING_SIGN;
			for (Wood wood : all()) {
				entries.addAfter(after, wood.signItem, wood.hangingSignItem);
				after = wood.hangingSignItem;
			}
		});
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
			Item after = Items.CHERRY_CHEST_BOAT;
			for (Wood wood : all()) {
				entries.addAfter(after, wood.boat, wood.chestBoat);
				after = wood.chestBoat;
			}
		});
	}
}
