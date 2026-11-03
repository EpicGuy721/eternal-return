package com.eternalreturn.gametest;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.blocks.CutBlocks;
import com.eternalreturn.blocks.MixedSlabs;
import com.eternalreturn.blocks.SnowCover;
import com.eternalreturn.items.SubtlePotions;
import com.eternalreturn.survival.RandomSpawns;
import com.eternalreturn.worldgen.structures.ShipwreckTreasure;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTables;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.particle.EntityEffectParticleEffect;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.Heightmap;
import net.minecraft.world.TeleportTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * The block, brewing and world rules of the owner's eleventh list: stone-like blocks with stairs, slab and wall alike,
 * pistons crushing against obsidian, subtle potions, coordinates hidden in survival, the shipwrecks' buried treasure
 * chest, and random spawns (in their own batch: they switch a rule on for the whole server).
 */
public class CraftRulesTests implements FabricGameTest {
	private static final String BATCH = "craft_rules";
	private static final String SPAWN_BATCH = "random_spawns";
	/** 16 by 40 blocks of stone floor (at y 1 here: stand on y 2), 14 high. */
	private static final String ARENA = "eternalreturn_test:arena";

	private static Block block(String namespace, String path) {
		Identifier id = Identifier.of(namespace, path);
		return Registries.BLOCK.containsId(id) ? Registries.BLOCK.get(id) : null;
	}

	/** A block family's piece in either namespace (vanilla's, or this mod's), or null. */
	private static Block piece(String base, String suffix) {
		Block vanilla = block("minecraft", base + suffix);
		return vanilla != null ? vanilla : block(EternalReturn.MOD_ID, base + suffix);
	}

	/** Every stone-like family (pickaxe blocks, not copper or concrete) with a slab or stairs has a wall, and every wall its slab and stairs. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void stoneFamiliesComplete(TestContext ctx) {
		ctx.assertTrue(CutBlocks.STONE_WALLS.size() == 14 && CutBlocks.MORE_STAIRS.size() == 3, "14 walls and 3 stairs");
		List<String> missing = new ArrayList<>();
		for (Block block : Registries.BLOCK) {
			Identifier id = Registries.BLOCK.getId(block);
			String path = id.getPath();
			if (block instanceof MixedSlabs.MixedSlabBlock || path.contains("copper") || path.contains("concrete") || path.contains("petrified")) {
				continue;
			}
			String base = null;
			if (block instanceof SlabBlock && path.endsWith("_slab")) {
				base = path.substring(0, path.length() - "_slab".length());
			} else if (block instanceof StairsBlock && path.endsWith("_stairs")) {
				base = path.substring(0, path.length() - "_stairs".length());
			} else if (block instanceof WallBlock && path.endsWith("_wall")) {
				base = path.substring(0, path.length() - "_wall".length());
			}
			if (base == null || !block.getDefaultState().isIn(BlockTags.PICKAXE_MINEABLE)) {
				continue;
			}
			for (String suffix : new String[]{"_slab", "_stairs", "_wall"}) {
				if (piece(base, suffix) == null && !missing.contains(base + suffix)) {
					missing.add(base + suffix);
				}
			}
		}
		ctx.assertTrue(missing.isEmpty(), "missing " + missing);
		for (CutBlocks.Extra wall : CutBlocks.STONE_WALLS) {
			ctx.assertTrue(wall.block() instanceof WallBlock && wall.block().getDefaultState().contains(SnowCover.SNOWY)
					&& wall.block().getDefaultState().isIn(BlockTags.WALLS) && wall.block().getDefaultState().isIn(BlockTags.PICKAXE_MINEABLE)
					&& wall.block().asItem() != Items.AIR, Registries.BLOCK.getId(wall.block()) + ": a snow-holding wall, a pickaxe block, with an item");
			Identifier id = Registries.BLOCK.getId(wall.block());
			ctx.assertTrue(ctx.getWorld().getRecipeManager().get(id).isPresent(), "a recipe for " + id);
		}
		for (CutBlocks.Extra stairs : CutBlocks.MORE_STAIRS) {
			ctx.assertTrue(stairs.block().getDefaultState().isIn(BlockTags.STAIRS), Registries.BLOCK.getId(stairs.block()) + " is stairs");
		}
		ctx.complete();
	}

	/** Stone, cobblestone and gravel pushed into obsidian: the one against it is crushed one step down the chain; the rest move up. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void pistonsCrush(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		// Three rows along x: piston at 2, then the pushed blocks, then obsidian at 5.
		Block[][] rows = {{Blocks.STONE, Blocks.STONE}, {Blocks.STONE, Blocks.COBBLESTONE}, {Blocks.STONE, Blocks.GRAVEL}};
		Block[] expected = {Blocks.COBBLESTONE, Blocks.GRAVEL, Blocks.SAND};
		for (int i = 0; i < rows.length; i++) {
			int z = 6 + i * 6;
			ctx.setBlockState(new BlockPos(2, 2, z), Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST));
			ctx.setBlockState(new BlockPos(3, 2, z), rows[i][0].getDefaultState());
			ctx.setBlockState(new BlockPos(4, 2, z), rows[i][1].getDefaultState());
			ctx.setBlockState(new BlockPos(5, 2, z), (i == 1 ? Blocks.CRYING_OBSIDIAN : Blocks.OBSIDIAN).getDefaultState());
			ctx.setBlockState(new BlockPos(1, 2, z), Blocks.REDSTONE_BLOCK.getDefaultState());
		}
		// A row against bedrock still can't move.
		ctx.setBlockState(new BlockPos(2, 2, 26), Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST));
		ctx.setBlockState(new BlockPos(3, 2, 26), Blocks.STONE.getDefaultState());
		ctx.setBlockState(new BlockPos(4, 2, 26), Blocks.BEDROCK.getDefaultState());
		ctx.setBlockState(new BlockPos(1, 2, 26), Blocks.REDSTONE_BLOCK.getDefaultState());
		ctx.runAtTick(20, () -> {
			for (int i = 0; i < rows.length; i++) {
				int z = 6 + i * 6;
				ctx.expectBlock(Blocks.PISTON_HEAD, new BlockPos(3, 2, z));
				ctx.expectBlock(Blocks.STONE, new BlockPos(4, 2, z));
				// Anywhere in the arena (each row's drop is its own item): drops fly, and the moving blocks push them on.
				Box around = new Box(Vec3d.of(ctx.getAbsolutePos(new BlockPos(-1, 1, -1))), Vec3d.of(ctx.getAbsolutePos(new BlockPos(17, 14, 41))));
				Block crushedTo = expected[i];
				ctx.assertTrue(!world.getEntitiesByClass(ItemEntity.class, around, item -> item.getStack().isOf(crushedTo.asItem())).isEmpty(),
						"row " + i + ": the crushed " + Registries.BLOCK.getId(rows[i][1]) + " gave " + Registries.BLOCK.getId(crushedTo) + "; items there: "
								+ world.getEntitiesByClass(ItemEntity.class, around.expand(6.0), item -> true).stream().map(item -> item.getStack().toString()).toList()
								+ ", blocks " + world.getBlockState(ctx.getAbsolutePos(new BlockPos(4, 2, z))) + " " + world.getBlockState(ctx.getAbsolutePos(new BlockPos(5, 2, z))));
			}
			ctx.expectBlock(Blocks.STONE, new BlockPos(3, 2, 26));
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	/** A crushed chest spills its contents and drops itself. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 60)
	public void crushedChestsSpill(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ctx.setBlockState(new BlockPos(2, 2, 8), Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST));
		ctx.setBlockState(new BlockPos(3, 2, 8), Blocks.CHEST.getDefaultState());
		ctx.setBlockState(new BlockPos(4, 2, 8), Blocks.OBSIDIAN.getDefaultState());
		if (world.getBlockEntity(ctx.getAbsolutePos(new BlockPos(3, 2, 8))) instanceof ChestBlockEntity chest) {
			chest.setStack(0, new ItemStack(Items.DIAMOND, 3));
		}
		ctx.setBlockState(new BlockPos(1, 2, 8), Blocks.REDSTONE_BLOCK.getDefaultState());
		ctx.runAtTick(20, () -> {
			Box around = new Box(Vec3d.of(ctx.getAbsolutePos(new BlockPos(0, 2, 5))), Vec3d.of(ctx.getAbsolutePos(new BlockPos(9, 5, 12))));
			ctx.assertTrue(!world.getEntitiesByClass(ItemEntity.class, around, item -> item.getStack().isOf(Items.DIAMOND)).isEmpty(), "the diamonds spilled");
			ctx.assertTrue(!world.getEntitiesByClass(ItemEntity.class, around, item -> item.getStack().isOf(Items.CHEST)).isEmpty(), "and the chest dropped");
			ctx.killAllEntities();
			ctx.complete();
		});
	}

	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void subtlePotions(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BrewingRecipeRegistry brewing = world.getBrewingRecipeRegistry();
		ItemStack amethyst = new ItemStack(Items.AMETHYST_SHARD);
		ItemStack swiftness = PotionContentsComponent.createStack(Items.POTION, Potions.LONG_SWIFTNESS);
		ctx.assertTrue(brewing.isValidIngredient(amethyst), "amethyst goes in the stand");
		ctx.assertTrue(brewing.hasRecipe(swiftness, amethyst), "it brews a potion with effects");
		ctx.assertTrue(!brewing.hasRecipe(PotionContentsComponent.createStack(Items.POTION, Potions.WATER), amethyst), "not water");
		ItemStack subtle = brewing.craft(amethyst, swiftness);
		PotionContentsComponent contents = subtle.get(DataComponentTypes.POTION_CONTENTS);
		ctx.assertTrue(SubtlePotions.isSubtle(subtle) && subtle.isOf(Items.POTION), "it comes out subtle");
		for (StatusEffectInstance effect : contents.getEffects()) {
			ctx.assertTrue(!effect.shouldShowParticles() && effect.getDuration() == 9600, "its effects show no particles, as long as before: " + effect);
		}
		ctx.assertTrue(!brewing.hasRecipe(subtle, amethyst), "not twice");
		ctx.assertTrue(subtle.getName().getContent() instanceof TranslatableTextContent name && name.getKey().equals("item.eternalreturn.subtle")
				&& name.getArgs()[0] instanceof net.minecraft.text.Text arg && arg.getContent() instanceof TranslatableTextContent inner
				&& inner.getKey().equals("item.minecraft.potion.effect.swiftness"), "named Subtle Potion of Swiftness: " + subtle.getName());
		ItemStack splash = brewing.craft(new ItemStack(Items.GUNPOWDER), subtle);
		ctx.assertTrue(splash.isOf(Items.SPLASH_POTION) && SubtlePotions.isSubtle(splash), "gunpowder makes it a subtle splash potion: " + splash);
		ItemStack lingering = brewing.craft(new ItemStack(Items.DRAGON_BREATH), splash);
		ctx.assertTrue(lingering.isOf(Items.LINGERING_POTION) && SubtlePotions.isSubtle(lingering), "dragon's breath a subtle lingering one");
		ctx.assertTrue(!brewing.hasRecipe(subtle, new ItemStack(Items.GLOWSTONE_DUST)), "it can't be strengthened any more");
		// The lingering cloud is invisible, a tipped arrow flies plain.
		AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(world, 0.0, 0.0, 0.0);
		cloud.setPotionContents(lingering.get(DataComponentTypes.POTION_CONTENTS));
		ctx.assertTrue(cloud.getParticleType() instanceof EntityEffectParticleEffect effect && effect.getAlpha() == 0.0F, "the cloud's particles are clear");
		AreaEffectCloudEntity plainCloud = new AreaEffectCloudEntity(world, 0.0, 0.0, 0.0);
		plainCloud.setPotionContents(new PotionContentsComponent(Potions.SWIFTNESS));
		ctx.assertTrue(plainCloud.getParticleType() instanceof EntityEffectParticleEffect effect && effect.getAlpha() == 1.0F, "a plain cloud's aren't");
		ItemStack arrowStack = new ItemStack(Items.TIPPED_ARROW);
		arrowStack.set(DataComponentTypes.POTION_CONTENTS, lingering.get(DataComponentTypes.POTION_CONTENTS));
		ArrowEntity arrow = new ArrowEntity(world, 0.0, 0.0, 0.0, arrowStack, null);
		ctx.assertTrue(arrow.getColor() == -1, "a subtle arrow leaves no trail");
		ctx.complete();
	}

	/** Survival players get reduced debug info (no coordinates in F3); creative ones don't. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 40)
	public void coordinatesHiddenInSurvival(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 8));
		ctx.assertTrue(new EntityStatusS2CPacket(player, EntityStatuses.USE_FULL_DEBUG_INFO).getStatus() == EntityStatuses.USE_REDUCED_DEBUG_INFO,
				"survival: reduced");
		player.changeGameMode(GameMode.CREATIVE);
		ctx.assertTrue(new EntityStatusS2CPacket(player, EntityStatuses.USE_FULL_DEBUG_INFO).getStatus() == EntityStatuses.USE_FULL_DEBUG_INFO,
				"creative: full");
		FishingTests.disconnect(player);
		ctx.complete();
	}

	/** The shipwrecks' treasure: a buried treasure chest one block down in the ground, sand where water would touch it. */
	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, skyAccess = true)
	public void shipwreckTreasureBuried(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		BlockPos origin = ctx.getAbsolutePos(BlockPos.ORIGIN);
		int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, origin.getX(), origin.getZ());
		ShipwreckTreasure.Piece piece = new ShipwreckTreasure.Piece(new BlockPos(origin.getX(), top, origin.getZ()));
		ChunkPos chunk = new ChunkPos(origin);
		piece.generate(world, world.getStructureAccessor(), world.getChunkManager().getChunkGenerator(), world.getRandom(),
				new net.minecraft.util.math.BlockBox(chunk.getStartX(), world.getBottomY(), chunk.getStartZ(), chunk.getEndX(), world.getTopY(), chunk.getEndZ()),
				chunk, origin);
		BlockPos chestPos = new BlockPos(origin.getX(), top - 2, origin.getZ());
		ctx.assertTrue(world.getBlockEntity(chestPos) instanceof ChestBlockEntity chest && LootTables.BURIED_TREASURE_CHEST.equals(chest.getLootTable()),
				"a buried treasure chest at " + chestPos + ": " + world.getBlockState(chestPos));
		ctx.assertTrue(!world.getBlockState(chestPos.up()).isAir(), "covered");
		ctx.complete();
	}

	/** With random spawns on, a dead player with no bed comes back somewhere within 5,000 blocks of 0, 0. */
	@GameTest(templateName = ARENA, batchId = SPAWN_BATCH, tickLimit = 200)
	public void randomRespawns(TestContext ctx) {
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 8));
		TeleportTarget vanilla = player.getRespawnTarget(false, TeleportTarget.NO_OP);
		RandomSpawns.testEverywhere = true;
		try {
			List<Vec3d> spots = new ArrayList<>();
			for (int i = 0; i < 3; i++) {
				TeleportTarget target = player.getRespawnTarget(false, TeleportTarget.NO_OP);
				double distance = Math.sqrt(target.pos().x * target.pos().x + target.pos().z * target.pos().z);
				ctx.assertTrue(distance <= 5001.0, "within 5,000 blocks: " + target.pos());
				ctx.assertTrue(!target.world().getBlockState(BlockPos.ofFloored(target.pos()).down()).isAir(), "on the ground: " + target.pos());
				spots.add(target.pos());
			}
			ctx.assertTrue(!spots.get(0).equals(spots.get(1)) || !spots.get(1).equals(spots.get(2)), "somewhere new each time: " + spots);
		} finally {
			RandomSpawns.testEverywhere = false;
		}
		ctx.assertTrue(player.getRespawnTarget(false, TeleportTarget.NO_OP).pos().squaredDistanceTo(vanilla.pos()) < 100.0 * 100.0,
				"off: by the world spawn again");
		FishingTests.disconnect(player);
		ctx.complete();
	}
}
