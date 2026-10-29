package com.eternalreturn.gametest;

import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.items.Speargun;
import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import com.eternalreturn.mobs.Jockeys;
import com.eternalreturn.mobs.NewMobs;
import com.eternalreturn.mobs.ai.Lounging;
import com.eternalreturn.mobs.entity.SunkenEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.DolphinEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.EntityTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;

import java.util.List;

/**
 * The Sunken and its speargun: a skeleton of the sea with a speargun; harpoons that keep their speed under water, hook
 * what they hit and reel it in; skeletons that drown into Sunkens; Sunkens riding dolphins; dolphins spawning again.
 */
public class SunkenTests implements FabricGameTest {
	private static final String BATCH = "sunken";
	private static final String ARENA = "eternalreturn_test:arena";

	/** A Sunken as from a spawn egg; not one of the large ones (one skeleton in 200), which have no weapon. */
	private static SunkenEntity sunken(TestContext ctx, BlockPos pos) {
		ServerWorld world = ctx.getWorld();
		SunkenEntity sunken = ctx.spawnEntity(NewMobs.SUNKEN, pos);
		double large = EternalReturnConfig.get().mobs.largeVariantChance;
		EternalReturnConfig.get().mobs.largeVariantChance = 0.0;
		try {
			sunken.initialize(world, world.getLocalDifficulty(sunken.getBlockPos()), SpawnReason.SPAWN_EGG, null);
		} finally {
			EternalReturnConfig.get().mobs.largeVariantChance = large;
		}
		return sunken;
	}

	/** A glass tank of water on the arena's floor (its top at y 1): inside from (x0, 2, z0) to (x1, top, z1). */
	private static void tank(TestContext ctx, int x0, int z0, int x1, int z1, int top) {
		for (int x = x0 - 1; x <= x1 + 1; x++) {
			for (int z = z0 - 1; z <= z1 + 1; z++) {
				for (int y = 2; y <= top + 1; y++) {
					boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1 && y <= top;
					ctx.setBlockState(new BlockPos(x, y, z), inside ? Blocks.WATER : Blocks.GLASS);
				}
			}
		}
	}

	/**
	 * A husk (it doesn't burn, and lives through a player's critical shot) that stays put but can be moved: no goals,
	 * rather than no AI (a mob without AI doesn't move at all).
	 */
	private static ZombieEntity stillTarget(TestContext ctx, BlockPos pos) {
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, pos);
		((MobEntityAccessor) husk).eternalreturn$goalSelector().clear(goal -> true);
		((MobEntityAccessor) husk).eternalreturn$targetSelector().clear(goal -> true);
		return husk;
	}

	/** Aimed at the husk's middle, raised by as much as a shot this fast drops on the way (gravity 0.05 a tick). */
	private static Vec3d lead(Vec3d from, LivingEntity husk, double speed) {
		Vec3d to = new Vec3d(husk.getX(), husk.getBodyY(0.5), husk.getZ()).subtract(from);
		double ticks = to.horizontalLength() / speed;
		return to.add(0.0, 0.025 * ticks * ticks + 0.05 * ticks, 0.0);
	}

	@GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
	public void speargunVariantsAndSpawns(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SunkenEntity sunken = sunken(ctx, new BlockPos(2, 1, 2));
		ctx.assertTrue(sunken.getMainHandStack().isOf(Speargun.SPEARGUN), "a speargun in hand: " + sunken.getMainHandStack());
		ctx.assertTrue(sunken.getMaxHealth() == 20.0F, "a skeleton's health: " + sunken.getMaxHealth());
		ctx.assertTrue(sunken.getType().isIn(EntityTypeTags.UNDEAD) && sunken.canBreatheInWater(), "undead, so it doesn't drown");
		sunken.setVariant(2);
		NbtCompound nbt = new NbtCompound();
		sunken.writeNbt(nbt);
		SunkenEntity copy = NewMobs.SUNKEN.create(world);
		copy.readNbt(nbt);
		ctx.assertTrue(copy.getVariant() == 2 && "bubble".equals(nbt.getString("Variant")), "its coral kept: " + nbt.getString("Variant"));
		var biomes = world.getRegistryManager().get(RegistryKeys.BIOME);
		Biome ocean = biomes.get(BiomeKeys.DEEP_OCEAN);
		Biome warm = biomes.get(BiomeKeys.WARM_OCEAN);
		ctx.assertTrue(ocean.getSpawnSettings().getSpawnEntries(SpawnGroup.MONSTER).getEntries().stream().anyMatch(e -> e.type == NewMobs.SUNKEN),
				"Sunkens spawn in the sea");
		ctx.assertTrue(warm.getSpawnSettings().getSpawnEntries(SpawnGroup.WATER_CREATURE).getEntries().stream().anyMatch(e -> e.type == EntityType.DOLPHIN),
				"and dolphins spawn again");
		sunken.discard();
		ctx.complete();
	}

	/** Shot side by side under water, a harpoon keeps its speed and an arrow doesn't. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void harpoonKeepsSpeedUnderWater(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		tank(ctx, 4, 4, 10, 30, 7);
		Vec3d start = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(5, 3, 5)));
		Vec3d other = Vec3d.ofCenter(ctx.getAbsolutePos(new BlockPos(9, 3, 5)));
		Speargun.Harpoon harpoon = new Speargun.Harpoon(Speargun.HARPOON, world);
		harpoon.setPosition(start);
		harpoon.setVelocity(0.0, 0.0, 1.0, 2.0F, 0.0F);
		ArrowEntity arrow = new ArrowEntity(world, other.x, other.y, other.z, new ItemStack(Items.ARROW), null);
		arrow.setVelocity(0.0, 0.0, 1.0, 2.0F, 0.0F);
		world.spawnEntity(harpoon);
		world.spawnEntity(arrow);
		ctx.runAtTick(6, () -> {
			double harpoonSpeed = harpoon.getVelocity().length();
			double arrowSpeed = arrow.getVelocity().length();
			ctx.assertTrue(harpoonSpeed > 1.5 && harpoonSpeed > arrowSpeed * 4.0, "under water the harpoon keeps its speed: " + harpoonSpeed + " to the arrow's " + arrowSpeed);
			harpoon.discard();
			arrow.discard();
			ctx.complete();
		});
	}

	/**
	 * A Sunken draws its speargun at a target in range and shoots it (and keeps after it while it draws). Its shots scatter
	 * as a skeleton's do: it has time for ten or so, as now and then the first five all miss.
	 */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 500)
	public void shootsItsTarget(TestContext ctx) {
		SunkenEntity sunken = sunken(ctx, new BlockPos(8, 2, 4));
		// A helmet, so the sun doesn't send it looking for shade.
		sunken.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		ZombieEntity husk = ctx.spawnEntity(EntityType.HUSK, new BlockPos(8, 2, 14));
		husk.setAiDisabled(true);
		float health = husk.getHealth();
		int[] seen = new int[5];
		ctx.runAtEveryTick(() -> {
			Lounging.stand(sunken);
			if (sunken.getTarget() == null) {
				seen[4]++;
				sunken.setTarget(husk);
			}
			if (sunken.isCharging()) {
				seen[0]++;
			}
			if (sunken.isUsingItem()) {
				seen[1]++;
			}
			if (net.minecraft.item.CrossbowItem.isCharged(sunken.getMainHandStack())) {
				seen[2]++;
			}
			seen[3] = Math.max(seen[3], world(ctx).getEntitiesByClass(Speargun.Harpoon.class, new Box(sunken.getBlockPos()).expand(40.0), h -> true).size());
			if (husk.getHealth() < health) {
				sunken.discard();
				husk.discard();
				// Its own (the other tests here may still be shooting theirs).
				world(ctx).getEntitiesByClass(Speargun.Harpoon.class, new Box(sunken.getBlockPos()).expand(40.0), h -> h.getOwner() == sunken).forEach(h -> h.discard());
				ctx.complete();
			}
		});
		ctx.runAtTick(480, () -> ctx.assertTrue(husk.getHealth() < health, "it shot the target: " + husk.getHealth() + " of " + health + " (charging "
				+ seen[0] + ", using " + seen[1] + ", loaded " + seen[2] + ", harpoons " + seen[3] + ", target lost " + seen[4] + ", hand " + sunken.getMainHandStack()
				+ ")"));
	}

	private static ServerWorld world(TestContext ctx) {
		return ctx.getWorld();
	}

	/** A Sunken's harpoon hooks a husk, and the Sunken reels it in. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void harpoonHooksAndReels(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		SunkenEntity sunken = ctx.spawnEntity(NewMobs.SUNKEN, new BlockPos(8, 2, 4));
		sunken.setAiDisabled(true);
		ZombieEntity husk = stillTarget(ctx, new BlockPos(8, 2, 16));
		Speargun.Harpoon harpoon = new Speargun.Harpoon(world, sunken, new ItemStack(Items.ARROW), new ItemStack(Speargun.SPEARGUN));
		Vec3d from = sunken.getEyePos();
		Vec3d aim = lead(from, husk, 2.5);
		harpoon.setPosition(from);
		harpoon.setVelocity(aim.x, aim.y, aim.z, 2.5F, 0.0F);
		world.spawnEntity(harpoon);
		double before = sunken.distanceTo(husk);
		boolean[] hooked = {false};
		double[] nearest = {before};
		ctx.runAtEveryTick(() -> {
			hooked[0] |= harpoon.getHooked() == husk;
			nearest[0] = Math.min(nearest[0], sunken.distanceTo(husk));
		});
		ctx.runAtTick(Speargun.MOB_REEL_TIME + 30, () -> {
			ctx.assertTrue(hooked[0], "the harpoon hooked the husk (husk health " + husk.getHealth() + ", harpoon removed " + harpoon.isRemoved()
					+ " at " + harpoon.getPos() + ", husk at " + husk.getPos() + ", stuck " + harpoon.isStuck() + ", velocity " + harpoon.getVelocity() + ")");
			ctx.assertTrue(nearest[0] < before - 4.0, "and the Sunken reeled it in: from " + before + " to " + nearest[0]);
			sunken.discard();
			husk.discard();
			harpoon.discard();
			ctx.complete();
		});
	}

	/** A player's loaded speargun shoots a harpoon; with the husk hooked, using the speargun reels it in. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 200)
	public void playerReelsIn(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		ServerPlayerEntity player = FishingTests.survivalPlayer(ctx, new BlockPos(8, 2, 4));
		ZombieEntity husk = stillTarget(ctx, new BlockPos(8, 2, 14));
		ItemStack speargun = new ItemStack(Speargun.SPEARGUN);
		speargun.set(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.of(new ItemStack(Items.ARROW)));
		player.setStackInHand(Hand.MAIN_HAND, speargun);
		Vec3d aim = lead(player.getEyePos(), husk, 3.15);
		player.setYaw((float) Math.toDegrees(Math.atan2(-aim.x, aim.z)));
		player.setPitch((float) -Math.toDegrees(Math.atan2(aim.y, aim.horizontalLength())));
		player.setHeadYaw(player.getYaw());
		speargun.use(world, player, Hand.MAIN_HAND);
		double before = player.distanceTo(husk);
		boolean[] reeling = {false};
		ctx.runAtTick(20, () -> {
			try {
				Speargun.Harpoon harpoon = Speargun.hooked(player);
				ctx.assertTrue(harpoon != null && harpoon.getHooked() == husk, "the player's harpoon hooked the husk");
				speargun.use(world, player, Hand.MAIN_HAND);
				reeling[0] = true;
			} catch (RuntimeException e) {
				FishingTests.disconnect(player);
				throw e;
			}
		});
		// The test player isn't ticked: hold use for it, a tick at a time.
		int[] reels = {0};
		ctx.runAtEveryTick(() -> {
			if (reeling[0]) {
				speargun.getItem().usageTick(world, player, speargun, 72000);
				reels[0]++;
			}
		});
		ctx.runAtTick(60, () -> {
			try {
				ctx.assertTrue(player.distanceTo(husk) < before - 3.0, "reeled in: from " + before + " to " + player.distanceTo(husk) + " (reels " + reels[0]
						+ ", still hooked " + (Speargun.hooked(player) != null) + ", husk velocity " + husk.getVelocity() + ", using " + player.isUsingItem() + ")");
			} finally {
				reeling[0] = false;
				FishingTests.disconnect(player);
				husk.discard();
				world.getEntitiesByClass(Speargun.Harpoon.class, new Box(player.getBlockPos()).expand(40.0), h -> true).forEach(h -> h.discard());
			}
			ctx.complete();
		});
	}

	/** A skeleton long under water becomes a Sunken, its bow a speargun. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 400)
	public void skeletonsDrownIntoSunkens(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		tank(ctx, 7, 7, 9, 9, 5);
		SkeletonEntity skeleton = ctx.spawnEntity(EntityType.SKELETON, new BlockPos(8, 2, 8));
		skeleton.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		// Nearly long enough under water already (set once it has ticked: its first tick can't tell it's under water yet).
		ctx.runAtTick(3, () -> {
			NbtCompound nbt = new NbtCompound();
			skeleton.writeNbt(nbt);
			nbt.putInt("EternalReturnUnderwaterTime", 590);
			skeleton.readNbt(nbt);
		});
		ctx.runAtTick(360, () -> {
			List<SunkenEntity> sunkens = world.getEntitiesByClass(SunkenEntity.class, new Box(ctx.getAbsolutePos(new BlockPos(8, 2, 8))).expand(4.0), e -> true);
			ctx.assertTrue(skeleton.isRemoved(), "the skeleton is gone");
			ctx.assertTrue(sunkens.size() == 1, "a Sunken in its place: " + sunkens.size());
			ctx.assertTrue(sunkens.get(0).getMainHandStack().isOf(Speargun.SPEARGUN), "with a speargun for its bow: " + sunkens.get(0).getMainHandStack());
			sunkens.forEach(SunkenEntity::discard);
			ctx.complete();
		});
	}

	/** A Sunken in water can come riding a dolphin, which it steers, and which doesn't run out of air. */
	@GameTest(templateName = ARENA, batchId = BATCH, tickLimit = 100)
	public void dolphinJockeys(TestContext ctx) {
		ServerWorld world = ctx.getWorld();
		tank(ctx, 5, 5, 11, 11, 7);
		SunkenEntity sunken = ctx.spawnEntity(NewMobs.SUNKEN, new BlockPos(8, 3, 8));
		double chance = EternalReturnConfig.get().mobs.sunkenDolphinJockeyChance;
		EternalReturnConfig.get().mobs.sunkenDolphinJockeyChance = 1.0;
		try {
			Jockeys.tryDolphinMount(sunken, world, world.getLocalDifficulty(sunken.getBlockPos()), SpawnReason.NATURAL);
		} finally {
			EternalReturnConfig.get().mobs.sunkenDolphinJockeyChance = chance;
		}
		ctx.assertTrue(sunken.getVehicle() instanceof DolphinEntity, "riding a dolphin: " + sunken.getVehicle());
		DolphinEntity dolphin = (DolphinEntity) sunken.getVehicle();
		ctx.assertTrue(dolphin.getControllingPassenger() == sunken, "which it steers");
		dolphin.setAir(10);
		ctx.runAtTick(5, () -> {
			ctx.assertTrue(dolphin.getAir() == dolphin.getMaxAir(), "and which never runs out of air: " + dolphin.getAir());
			sunken.discard();
			dolphin.discard();
			ctx.complete();
		});
	}
}
