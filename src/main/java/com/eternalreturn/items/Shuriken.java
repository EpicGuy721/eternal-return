package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.DispenserBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ProjectileItem;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;

/**
 * Shurikens (5 iron nuggets in an X make 4): thrown fast and flat, they cut each mob they hit for 4 and fly on through
 * it, up to 3; one that hits a block, or its third mob, breaks for good. Dispensers throw them too.
 */
public final class Shuriken extends Item implements ProjectileItem {
	public static final float DAMAGE = 4.0F;
	public static final int PIERCES = 3;
	public static final Item ITEM = Registry.register(Registries.ITEM, EternalReturn.id("shuriken"), new Shuriken(new Item.Settings().maxCount(16)));
	public static final EntityType<Thrown> ENTITY = Registry.register(Registries.ENTITY_TYPE, EternalReturn.id("shuriken"),
			EntityType.Builder.<Thrown>create(Thrown::new, SpawnGroup.MISC).dimensions(0.25F, 0.25F).maxTrackingRange(4).trackingTickInterval(10)
					.build(EternalReturn.id("shuriken").toString()));

	private Shuriken(Item.Settings settings) {
		super(settings);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.addAfter(IceBall.ITEM, ITEM));
		DispenserBlock.registerProjectileBehavior(ITEM);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 0.5F,
				1.6F + world.getRandom().nextFloat() * 0.2F);
		if (!world.isClient) {
			Thrown shuriken = new Thrown(world, user);
			shuriken.setItem(stack);
			shuriken.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 2.0F, 0.5F);
			world.spawnEntity(shuriken);
		}
		user.getItemCooldownManager().set(this, 5);
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		stack.decrementUnlessCreative(1, user);
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public ProjectileEntity createEntity(World world, Position pos, ItemStack stack, Direction direction) {
		Thrown shuriken = new Thrown(ENTITY, world);
		shuriken.setPosition(pos.getX(), pos.getY(), pos.getZ());
		shuriken.setItem(stack);
		return shuriken;
	}

	public static class Thrown extends ThrownItemEntity {
		/** The mobs it has gone through, which it doesn't hit again. */
		private final IntSet pierced = new IntOpenHashSet();

		public Thrown(EntityType<? extends Thrown> type, World world) {
			super(type, world);
		}

		public Thrown(World world, LivingEntity owner) {
			super(ENTITY, owner, world);
		}

		@Override
		protected Item getDefaultItem() {
			return ITEM;
		}

		/** Flatter than a snowball's 0.03. */
		@Override
		protected double getGravity() {
			return 0.015;
		}

		@Override
		protected boolean canHit(Entity entity) {
			return super.canHit(entity) && !this.pierced.contains(entity.getId());
		}

		@Override
		protected void onEntityHit(EntityHitResult hit) {
			super.onEntityHit(hit);
			Entity entity = hit.getEntity();
			this.pierced.add(entity.getId());
			if (!this.getWorld().isClient) {
				entity.damage(this.getDamageSources().thrown(this, this.getOwner()), DAMAGE);
				this.playSound(SoundEvents.ENTITY_ARROW_HIT, 0.6F, 1.5F);
				if (this.pierced.size() >= PIERCES) {
					this.shatter();
				}
			}
		}

		@Override
		protected void onBlockHit(BlockHitResult hit) {
			super.onBlockHit(hit);
			if (!this.getWorld().isClient) {
				this.shatter();
			}
		}

		private void shatter() {
			this.getWorld().sendEntityStatus(this, EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES);
			this.playSound(SoundEvents.ENTITY_ITEM_BREAK, 0.6F, 1.4F);
			this.discard();
		}

		@Override
		public void handleStatus(byte status) {
			if (status == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) {
				for (int i = 0; i < 8; i++) {
					this.getWorld().addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, this.getStack()), this.getX(), this.getY(), this.getZ(),
							(this.random.nextDouble() - 0.5) * 0.15, this.random.nextDouble() * 0.15, (this.random.nextDouble() - 0.5) * 0.15);
				}
			}
		}
	}
}
