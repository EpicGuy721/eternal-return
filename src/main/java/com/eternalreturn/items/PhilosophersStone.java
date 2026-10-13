package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AxolotlEntity;
import net.minecraft.entity.passive.FoxEntity;
import net.minecraft.entity.passive.FrogEntity;
import net.minecraft.entity.passive.HorseColor;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.entity.passive.HorseMarking;
import net.minecraft.entity.passive.LlamaEntity;
import net.minecraft.entity.passive.MooshroomEntity;
import net.minecraft.entity.passive.RabbitEntity;
import net.minecraft.entity.passive.SheepEntity;
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
import net.minecraft.state.property.Property;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The philosopher's stone (a diamond ringed by 4 lapis and 4 amethyst shards; 777 uses). Right-click
 * transmutes:
 * <ul>
 * <li>blocks, each into its partner and back (glass and sand, grass and dirt, cobblestone and stone, ice
 * and packed ice, clay and terracotta, melon and pumpkin, obsidian and crying obsidian...), or around a
 * ring (the woods' logs and planks, granite to diorite to andesite, the sixteen wools); concrete and its
 * powder, same colour. Only into blocks an Eternal Return world can give (no mangrove or bamboo wood, no moss);</li>
 * <li>creatures: a pig into a zombified piglin and back, a cow into a mooshroom and back, a villager into a
 * witch and back, zombie to husk to drowned, skeleton to stray to bogged, squid and glow squid, cod and
 * salmon, slime and magma cube, spider and cave spider, donkey and mule; a sheep gets random wool, a
 * creeper is charged (or uncharged), a horse gets a new coat colour or new markings (one or the other, at
 * random), and rabbits, foxes, frogs, axolotls and llamas change their looks.</li>
 * </ul>
 * Each use costs one point of durability (none in creative). The stone goes before a creature's own right-click
 * (trading, mounting), see MobInteractMixin.
 */
public final class PhilosophersStone extends Item {
	public static final Item ITEM = Registry.register(Registries.ITEM, EternalReturn.id("philosophers_stone"),
			new PhilosophersStone(new Item.Settings().maxDamage(777).rarity(Rarity.RARE)));

	private static final Map<Block, Block> BLOCKS = new HashMap<>();
	private static final Map<EntityType<?>, EntityType<? extends MobEntity>> CREATURES = new HashMap<>();

	static {
		pair(Blocks.GLASS, Blocks.SAND);
		pair(Blocks.GRASS_BLOCK, Blocks.DIRT);
		pair(Blocks.COBBLESTONE, Blocks.STONE);
		pair(Blocks.STONE_BRICKS, Blocks.CRACKED_STONE_BRICKS);
		pair(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_STONE_BRICKS);
		pair(Blocks.ICE, Blocks.PACKED_ICE);
		pair(Blocks.CLAY, Blocks.TERRACOTTA);
		pair(Blocks.MELON, Blocks.PUMPKIN);
		pair(Blocks.OBSIDIAN, Blocks.CRYING_OBSIDIAN);
		pair(Blocks.SANDSTONE, Blocks.RED_SANDSTONE);
		pair(Blocks.SOUL_SAND, Blocks.SOUL_SOIL);
		pair(Blocks.NETHER_BRICKS, Blocks.RED_NETHER_BRICKS);
		pair(Blocks.COARSE_DIRT, Blocks.PODZOL);
		pair(Blocks.GRAVEL, Blocks.TUFF);
		pair(Blocks.NETHERRACK, Blocks.BLACKSTONE);
		pair(Blocks.MUD, Blocks.PACKED_MUD);
		pair(Blocks.SNOW_BLOCK, Blocks.POWDER_SNOW);
		ring(Blocks.GRANITE, Blocks.DIORITE, Blocks.ANDESITE);
		ring(Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG, Blocks.CHERRY_LOG);
		ring(Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS, Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS, Blocks.CHERRY_PLANKS);
		ring(Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.CHERRY_LEAVES);
		for (DyeColor color : DyeColor.values()) {
			pair(Registries.BLOCK.get(Identifier.ofVanilla(color.getName() + "_concrete")), Registries.BLOCK.get(Identifier.ofVanilla(color.getName() + "_concrete_powder")));
		}
		ring(Blocks.WHITE_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.GRAY_WOOL, Blocks.BLACK_WOOL, Blocks.BROWN_WOOL, Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL,
				Blocks.LIME_WOOL, Blocks.GREEN_WOOL, Blocks.CYAN_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.BLUE_WOOL, Blocks.PURPLE_WOOL, Blocks.MAGENTA_WOOL, Blocks.PINK_WOOL);

		pairCreature(EntityType.PIG, EntityType.ZOMBIFIED_PIGLIN);
		pairCreature(EntityType.COW, EntityType.MOOSHROOM);
		pairCreature(EntityType.VILLAGER, EntityType.WITCH);
		pairCreature(EntityType.SQUID, EntityType.GLOW_SQUID);
		pairCreature(EntityType.COD, EntityType.SALMON);
		pairCreature(EntityType.SLIME, EntityType.MAGMA_CUBE);
		pairCreature(EntityType.SPIDER, EntityType.CAVE_SPIDER);
		pairCreature(EntityType.DONKEY, EntityType.MULE);
		CREATURES.put(EntityType.ZOMBIE, EntityType.HUSK);
		CREATURES.put(EntityType.HUSK, EntityType.DROWNED);
		CREATURES.put(EntityType.DROWNED, EntityType.ZOMBIE);
		CREATURES.put(EntityType.SKELETON, EntityType.STRAY);
		CREATURES.put(EntityType.STRAY, EntityType.BOGGED);
		CREATURES.put(EntityType.BOGGED, EntityType.SKELETON);
	}

	public PhilosophersStone(Settings settings) {
		super(settings);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.add(ITEM));
	}

	private static void pair(Block a, Block b) {
		BLOCKS.put(a, b);
		BLOCKS.put(b, a);
	}

	private static void ring(Block... blocks) {
		for (int i = 0; i < blocks.length; i++) {
			BLOCKS.put(blocks[i], blocks[(i + 1) % blocks.length]);
		}
	}

	private static void pairCreature(EntityType<? extends MobEntity> a, EntityType<? extends MobEntity> b) {
		CREATURES.put(a, b);
		CREATURES.put(b, a);
	}

	/** What a block turns into, or null. */
	public static Block transmute(Block block) {
		return BLOCKS.get(block);
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		World world = context.getWorld();
		BlockPos pos = context.getBlockPos();
		BlockState state = world.getBlockState(pos);
		Block into = BLOCKS.get(state.getBlock());
		if (into == null) {
			return ActionResult.PASS;
		}
		BlockState result = into.getDefaultState();
		for (Property<?> property : state.getProperties()) {
			result = copy(state, result, property);
		}
		// As its neighbours leave it: concrete powder made beside water sets straight back into concrete.
		result = Block.postProcessState(result, world, pos);
		if (result == state) {
			return ActionResult.PASS;
		}
		if (!world.isClient) {
			world.setBlockState(pos, result, Block.NOTIFY_ALL);
			sparkle(world, Vec3d.ofCenter(pos));
			context.getStack().damage(1, context.getPlayer(), LivingEntity.getSlotForHand(context.getHand()));
		}
		return ActionResult.success(world.isClient);
	}

	private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
		return to.contains(property) ? to.with(property, from.get(property)) : to;
	}

	@Override
	public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
		if (!(entity instanceof MobEntity mob) || !mob.isAlive() || !canTransmute(mob)) {
			return ActionResult.PASS;
		}
		World world = user.getWorld();
		if (world.isClient) {
			return ActionResult.SUCCESS;
		}
		boolean changed = true;
		var random = world.random;
		if (mob instanceof SheepEntity sheep) {
			DyeColor color;
			do {
				color = DyeColor.byId(random.nextInt(16));
			} while (color == sheep.getColor());
			sheep.setColor(color);
		} else if (mob instanceof CreeperEntity creeper) {
			setCharged(creeper, !creeper.getDataTracker().get(com.eternalreturn.mixin.mobs.CreeperEntityAccessor.eternalreturn$charged()));
		} else if (mob instanceof HorseEntity horse) {
			HorseColor color = horse.getVariant();
			HorseMarking marking = horse.getMarking();
			if (random.nextBoolean()) {
				do {
					color = HorseColor.byId(random.nextInt(HorseColor.values().length));
				} while (color == horse.getVariant());
			} else {
				do {
					marking = HorseMarking.byIndex(random.nextInt(HorseMarking.values().length));
				} while (marking == horse.getMarking());
			}
			((com.eternalreturn.mixin.combat.HorseEntityInvoker) horse).eternalreturn$setHorseVariant(color, marking);
		} else if (mob instanceof RabbitEntity rabbit) {
			List<RabbitEntity.RabbitType> types = List.of(RabbitEntity.RabbitType.BROWN, RabbitEntity.RabbitType.WHITE, RabbitEntity.RabbitType.BLACK,
					RabbitEntity.RabbitType.WHITE_SPLOTCHED, RabbitEntity.RabbitType.GOLD, RabbitEntity.RabbitType.SALT);
			rabbit.setVariant(types.get(random.nextInt(types.size())));
		} else if (mob instanceof FoxEntity fox) {
			fox.setVariant(fox.getVariant() == FoxEntity.Type.RED ? FoxEntity.Type.SNOW : FoxEntity.Type.RED);
		} else if (mob instanceof AxolotlEntity axolotl) {
			axolotl.setVariant(AxolotlEntity.Variant.byId(random.nextInt(AxolotlEntity.Variant.values().length)));
		} else if (mob instanceof LlamaEntity llama) {
			llama.setVariant(LlamaEntity.Variant.byId(random.nextInt(LlamaEntity.Variant.values().length)));
		} else if (mob instanceof FrogEntity frog) {
			var variants = world.getRegistryManager().get(net.minecraft.registry.RegistryKeys.FROG_VARIANT).streamEntries().toList();
			frog.setVariant(variants.get(random.nextInt(variants.size())));
		} else {
			EntityType<? extends MobEntity> into = CREATURES.get(mob.getType());
			if (into == null) {
				changed = false;
			} else {
				int size = mob instanceof SlimeEntity slime ? slime.getSize() : -1;
				MobEntity converted = mob.convertTo(into, true);
				if (converted instanceof SlimeEntity slime && size > 0) {
					slime.setSize(size, true);
				}
				if (converted instanceof MooshroomEntity mooshroom) {
					mooshroom.setVariant(MooshroomEntity.Type.RED);
				}
				changed = converted != null;
			}
		}
		if (!changed) {
			return ActionResult.PASS;
		}
		sparkle(world, mob.getPos().add(0.0, mob.getHeight() / 2.0, 0.0));
		stack.damage(1, user, LivingEntity.getSlotForHand(hand));
		return ActionResult.SUCCESS;
	}

	/** Whether the stone does anything to this creature (on either side, so the client knows too). */
	public static boolean canTransmute(MobEntity mob) {
		return mob instanceof SheepEntity || mob instanceof CreeperEntity || mob instanceof HorseEntity || mob instanceof RabbitEntity || mob instanceof FoxEntity
				|| mob instanceof AxolotlEntity || mob instanceof LlamaEntity || mob instanceof FrogEntity || CREATURES.containsKey(mob.getType());
	}

	private static void setCharged(CreeperEntity creeper, boolean charged) {
		creeper.getDataTracker().set(com.eternalreturn.mixin.mobs.CreeperEntityAccessor.eternalreturn$charged(), charged);
	}

	private static void sparkle(World world, Vec3d at) {
		if (world instanceof ServerWorld server) {
			server.spawnParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.6);
			server.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
		}
		world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.2F);
	}
}
