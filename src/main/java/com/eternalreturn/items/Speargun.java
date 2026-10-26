package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * The speargun (Sunkens carry one, com.eternalreturn.mobs.entity.SunkenEntity): a crossbow, loaded with arrows only,
 * whose shot is a harpoon. A harpoon flies as an arrow does in the air but keeps its speed under water (a trident's
 * drag there, not an arrow's), and what it hits stays hooked: the harpoon stuck in it and a line back to whoever shot
 * it, for HOOK_TIME ticks or until the line, longer than LINE_LENGTH, snaps. Holding use with the speargun (a player)
 * reels the catch in; a mob reels for MOB_REEL_TIME ticks after the hit. Enchanted as a crossbow is (Quick Charge,
 * Multishot, Piercing, Unbreaking, Mending).
 */
public final class Speargun {
	public static final int HOOK_TIME = 200;
	public static final double LINE_LENGTH = 32.0;
	public static final int MOB_REEL_TIME = 40;
	/** Reeling: how much faster the catch comes each tick, in blocks a tick, and how fast at most. */
	private static final double REEL_PULL = 0.12;
	private static final double REEL_TOP_SPEED = 0.8;
	/** It's reeled in once it's this close. */
	private static final double REELED_IN = 2.0;

	public static final Item SPEARGUN = Registry.register(Registries.ITEM, EternalReturn.id("speargun"), new SpeargunItem(new Item.Settings().maxCount(1)
			.maxDamage(465).component(DataComponentTypes.CHARGED_PROJECTILES, ChargedProjectilesComponent.DEFAULT)));
	public static final EntityType<Harpoon> HARPOON = Registry.register(Registries.ENTITY_TYPE, EternalReturn.id("harpoon"),
			EntityType.Builder.<Harpoon>create(Harpoon::new, SpawnGroup.MISC).dimensions(0.5F, 0.5F).eyeHeight(0.13F).maxTrackingRange(4)
					.trackingTickInterval(20).build(EternalReturn.id("harpoon").toString()));

	private Speargun() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.addAfter(Items.CROSSBOW, SPEARGUN));
	}

	/** The harpoon this shooter has hooked something with, if any. */
	@Nullable
	public static Harpoon hooked(LivingEntity shooter) {
		return shooter.getWorld().getEntitiesByClass(Harpoon.class, shooter.getBoundingBox().expand(LINE_LENGTH),
				harpoon -> harpoon.getOwner() == shooter && harpoon.getHooked() != null).stream().findFirst().orElse(null);
	}

	public static class SpeargunItem extends CrossbowItem {
		/** Who is reeling in with a speargun, rather than drawing it (on each side, client and server). */
		private static final Map<LivingEntity, Boolean> REELING = new WeakHashMap<>();

		public SpeargunItem(Settings settings) {
			super(settings);
		}

		@Override
		public Predicate<ItemStack> getHeldProjectiles() {
			return BOW_PROJECTILES;
		}

		@Override
		public Predicate<ItemStack> getProjectiles() {
			return BOW_PROJECTILES;
		}

		@Override
		protected ProjectileEntity createArrowEntity(World world, LivingEntity shooter, ItemStack weaponStack, ItemStack projectileStack, boolean critical) {
			Harpoon harpoon = new Harpoon(world, shooter, projectileStack.copyWithCount(1), weaponStack);
			harpoon.setCritical(critical);
			harpoon.setSound(SoundEvents.ITEM_CROSSBOW_HIT);
			return harpoon;
		}

		/** With something hooked (and not loaded), use reels it in; otherwise it's a crossbow. */
		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			if (!isCharged(stack) && hooked(user) != null) {
				REELING.put(user, true);
				user.setCurrentHand(hand);
				return TypedActionResult.consume(stack);
			}
			REELING.remove(user);
			return super.use(world, user, hand);
		}

		@Override
		public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
			if (REELING.containsKey(user)) {
				Harpoon harpoon = hooked(user);
				if (harpoon != null) {
					harpoon.reel();
				} else {
					REELING.remove(user);
					user.stopUsingItem();
				}
				return;
			}
			super.usageTick(world, user, stack, remainingUseTicks);
		}

		@Override
		public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
			if (REELING.remove(user) == null) {
				super.onStoppedUsing(stack, world, user, remainingUseTicks);
			}
		}

		@Override
		public int getMaxUseTime(ItemStack stack, LivingEntity user) {
			return REELING.containsKey(user) ? 72000 : super.getMaxUseTime(stack, user);
		}
	}

	public static class Harpoon extends PersistentProjectileEntity {
		/** What it's stuck in (its id plus one; 0 for nothing), for the line drawn to it. */
		private static final TrackedData<Integer> HOOKED = DataTracker.registerData(Harpoon.class, TrackedDataHandlerRegistry.INTEGER);
		private int hookedTicks;
		/** In the middle of its hit: an arrow is discarded at the end of a hit, but a harpoon that hooks stays. */
		private boolean hitting;
		private boolean discardAfterHit;
		@Nullable
		private LivingEntity landed;

		public Harpoon(EntityType<? extends Harpoon> type, World world) {
			super(type, world);
		}

		public Harpoon(World world, LivingEntity owner, ItemStack arrow, @Nullable ItemStack weapon) {
			super(HARPOON, owner, world, arrow, weapon);
		}

		@Override
		protected void initDataTracker(DataTracker.Builder builder) {
			super.initDataTracker(builder);
			builder.add(HOOKED, 0);
		}

		@Override
		protected ItemStack getDefaultItemStack() {
			return new ItemStack(Items.ARROW);
		}

		/** It cuts through water as a trident does. */
		@Override
		protected float getDragInWater() {
			return 0.99F;
		}

		@Nullable
		public Entity getHooked() {
			int id = this.dataTracker.get(HOOKED) - 1;
			return id < 0 ? null : this.getWorld().getEntityById(id);
		}

		/** Stuck in a block (no line then). */
		public boolean isStuck() {
			return this.inGround;
		}

		@Override
		protected void onEntityHit(EntityHitResult hit) {
			this.hitting = true;
			this.discardAfterHit = false;
			this.landed = null;
			try {
				super.onEntityHit(hit);
			} finally {
				this.hitting = false;
			}
			if (this.landed != null && this.landed.isAlive() && !this.getWorld().isClient) {
				this.dataTracker.set(HOOKED, this.landed.getId() + 1);
				this.hookedTicks = 0;
				this.setVelocity(Vec3d.ZERO);
			} else if (this.discardAfterHit) {
				this.discard();
			}
		}

		@Override
		protected void onHit(LivingEntity target) {
			super.onHit(target);
			this.landed = target;
		}

		@Override
		public void remove(RemovalReason reason) {
			if (this.hitting && reason == RemovalReason.DISCARDED) {
				this.discardAfterHit = true;
				return;
			}
			super.remove(reason);
		}

		/** Hooked, it rides along in its catch until it works loose or the line snaps; a mob reels its catch in at once. */
		@Override
		public void tick() {
			Entity hooked = this.getHooked();
			if (hooked == null) {
				super.tick();
				return;
			}
			this.setPosition(hooked.getX(), hooked.getBodyY(0.6), hooked.getZ());
			this.setVelocity(Vec3d.ZERO);
			if (this.getWorld().isClient) {
				return;
			}
			this.hookedTicks++;
			Entity owner = this.getOwner();
			boolean mob = !(owner instanceof PlayerEntity);
			if (!hooked.isAlive() || owner == null || !owner.isAlive() || owner.getWorld() != this.getWorld()
					|| owner.squaredDistanceTo(hooked) > LINE_LENGTH * LINE_LENGTH || this.hookedTicks > (mob ? MOB_REEL_TIME : HOOK_TIME)) {
				this.discard();
				return;
			}
			if (mob) {
				this.reel();
			}
		}

		/** A pull on the line: the catch comes toward whoever shot it, until it's close. */
		public void reel() {
			Entity hooked = this.getHooked();
			Entity owner = this.getOwner();
			if (hooked == null || owner == null || this.getWorld().isClient) {
				return;
			}
			Vec3d to = owner.getPos().add(0.0, owner.getHeight() * 0.4, 0.0).subtract(hooked.getPos());
			if (to.length() < REELED_IN) {
				this.discard();
				return;
			}
			Vec3d velocity = hooked.getVelocity().add(to.normalize().multiply(REEL_PULL));
			if (velocity.length() > REEL_TOP_SPEED) {
				velocity = velocity.normalize().multiply(REEL_TOP_SPEED);
			}
			hooked.setVelocity(velocity);
			hooked.velocityModified = true;
			hooked.fallDistance = 0.0F;
			if (this.hookedTicks % 4 == 0) {
				this.getWorld().playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, owner.getSoundCategory(),
						0.6F, 0.8F + this.random.nextFloat() * 0.4F);
			}
		}
	}
}
