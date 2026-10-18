package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

/**
 * Ice balls (an ice block makes 4): thrown like snowballs, they hurt what they hit by how fast they were going,
 * 1.395 times their speed in blocks a tick, rounded up (3 up close, 1 from afar): they slow down sharply, so
 * they hit hardest at close range. No extra damage to blazes. Whoever threw one can be hit by it ("was pummeled
 * by"). Brewed into potions of Freezing (Potions2); some frostbites throw them.
 */
public final class IceBall {
	public static final Item ITEM = Registry.register(Registries.ITEM, EternalReturn.id("ice_ball"), new BallItem(new Item.Settings().maxCount(16)));
	public static final EntityType<Ball> ENTITY = Registry.register(Registries.ENTITY_TYPE, EternalReturn.id("ice_ball"),
			EntityType.Builder.<Ball>create(Ball::new, SpawnGroup.MISC).dimensions(0.25F, 0.25F).maxTrackingRange(4).trackingTickInterval(10)
					.build(EternalReturn.id("ice_ball").toString()));
	/** What's left of its speed each tick, on top of a snowball's own drag. */
	public static final double SLOWING = 0.88;

	private IceBall() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.addAfter(Items.SNOWBALL, ITEM));
	}

	/** Thrown at a target by a mob (frostbites). */
	public static void throwAt(LivingEntity thrower, LivingEntity target) {
		Ball ball = new Ball(thrower.getWorld(), thrower);
		double dx = target.getX() - thrower.getX();
		double dy = target.getBodyY(0.6) - ball.getY();
		double dz = target.getZ() - thrower.getZ();
		ball.setVelocity(dx, dy + Math.sqrt(dx * dx + dz * dz) * 0.15, dz, 1.6F, 6.0F);
		thrower.playSound(SoundEvents.ENTITY_SNOWBALL_THROW, 1.0F, 0.6F);
		thrower.getWorld().spawnEntity(ball);
	}

	public static class BallItem extends Item {
		public BallItem(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack stack = user.getStackInHand(hand);
			world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.NEUTRAL, 0.5F,
					0.4F / (world.getRandom().nextFloat() * 0.4F + 0.8F));
			if (!world.isClient) {
				Ball ball = new Ball(world, user);
				ball.setItem(stack);
				ball.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.5F, 1.0F);
				world.spawnEntity(ball);
			}
			user.incrementStat(Stats.USED.getOrCreateStat(this));
			stack.decrementUnlessCreative(1, user);
			return TypedActionResult.success(stack, world.isClient());
		}
	}

	public static class Ball extends ThrownItemEntity {
		public Ball(EntityType<? extends Ball> type, World world) {
			super(type, world);
		}

		public Ball(World world, LivingEntity owner) {
			super(ENTITY, owner, world);
		}

		@Override
		protected Item getDefaultItem() {
			return ITEM;
		}

		@Override
		public void tick() {
			super.tick();
			if (!this.isRemoved()) {
				this.setVelocity(this.getVelocity().multiply(SLOWING));
			}
		}

		/** The damage it does at its speed now: 1.395 times its speed in blocks a tick, rounded up. */
		public float damage() {
			return (float) MathHelper.ceil(1.395 * this.getVelocity().length());
		}

		@Override
		protected void onEntityHit(EntityHitResult hit) {
			super.onEntityHit(hit);
			Entity entity = hit.getEntity();
			entity.damage(this.getDamageSources().thrown(this, this.getOwner()), this.damage());
		}

		@Override
		public void handleStatus(byte status) {
			if (status == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) {
				for (int i = 0; i < 8; i++) {
					this.getWorld().addParticle(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(ITEM)), this.getX(), this.getY(), this.getZ(),
							0.0, 0.0, 0.0);
				}
			}
		}

		@Override
		protected void onCollision(HitResult hit) {
			super.onCollision(hit);
			if (!this.getWorld().isClient) {
				this.getWorld().sendEntityStatus(this, EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES);
				this.playSound(SoundEvents.BLOCK_GLASS_BREAK, 0.5F, 1.6F);
				this.discard();
			}
		}
	}
}
