package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * The laser blaster (Sandowns carry one, com.eternalreturn.mobs.entity.SandownEntity, and drop it one time in 100): it
 * shoots a laser bolt that flies straight and fast and hits for DAMAGE (a projectile's damage: armor and Projectile
 * Protection soften it, and from a mob it scales with difficulty). No ammunition; COOLDOWN ticks between shots, and it
 * wears by 1 a shot.
 */
public final class LaserBlaster {
	public static final float DAMAGE = 6.0F;
	public static final int COOLDOWN = 15;
	/** Blocks a tick. */
	public static final float SPEED = 3.0F;
	/** A bolt fizzles out after this many ticks (about 100 blocks). */
	private static final int LIFE = 35;
	private static final DustParticleEffect GLOW = new DustParticleEffect(new Vector3f(1.0F, 0.25F, 0.2F), 0.8F);

	public static final Item BLASTER = Registry.register(Registries.ITEM, EternalReturn.id("laser_blaster"), new BlasterItem(new Item.Settings().maxDamage(250)));
	public static final EntityType<Bolt> BOLT = Registry.register(Registries.ENTITY_TYPE, EternalReturn.id("laser_bolt"),
			EntityType.Builder.<Bolt>create(Bolt::new, SpawnGroup.MISC).dimensions(0.2F, 0.2F).maxTrackingRange(8).trackingTickInterval(1)
					.build(EternalReturn.id("laser_bolt").toString()));

	private LaserBlaster() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.addAfter(Firearms.RIFLE, BLASTER));
	}

	/** A bolt from this point along the direction, with this much spread (in the units ProjectileEntity.setVelocity takes). */
	public static Bolt fire(LivingEntity shooter, Vec3d from, Vec3d direction, float spread) {
		World world = shooter.getWorld();
		Bolt bolt = new Bolt(world, shooter);
		bolt.setPosition(from);
		bolt.setVelocity(direction.x, direction.y, direction.z, SPEED, spread);
		world.spawnEntity(bolt);
		world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_SHULKER_SHOOT, shooter.getSoundCategory(), 1.2F, 1.7F);
		world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, shooter.getSoundCategory(), 1.5F, 2.0F);
		return bolt;
	}

	public static class BlasterItem extends Item {
		public BlasterItem(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack blaster = user.getStackInHand(hand);
			if (!world.isClient) {
				fire(user, new Vec3d(user.getX(), user.getEyeY() - 0.1, user.getZ()), user.getRotationVector(), 0.0F);
				blaster.damage(1, user, LivingEntity.getSlotForHand(hand));
			}
			user.getItemCooldownManager().set(this, COOLDOWN);
			user.incrementStat(Stats.USED.getOrCreateStat(this));
			return TypedActionResult.success(blaster, world.isClient());
		}
	}

	public static class Bolt extends ThrownEntity {
		public Bolt(EntityType<? extends Bolt> type, World world) {
			super(type, world);
		}

		public Bolt(World world, LivingEntity owner) {
			super(BOLT, owner, world);
		}

		@Override
		protected void initDataTracker(DataTracker.Builder builder) {
		}

		/** Straight: no drop. */
		@Override
		protected double getGravity() {
			return 0.0;
		}

		@Override
		public void tick() {
			super.tick();
			if (this.getWorld().isClient) {
				this.getWorld().addParticle(GLOW, this.prevX, this.prevY, this.prevZ, 0.0, 0.0, 0.0);
			} else if (this.age > LIFE) {
				this.discard();
			}
		}

		/** The hit: its damage. */
		public void strike(Entity entity) {
			DamageSource source = this.getOwner() instanceof LivingEntity owner ? this.getDamageSources().mobProjectile(this, owner)
					: this.getDamageSources().thrown(this, this.getOwner());
			entity.damage(source, DAMAGE);
		}

		@Override
		protected void onEntityHit(EntityHitResult hit) {
			super.onEntityHit(hit);
			this.strike(hit.getEntity());
		}

		@Override
		public void handleStatus(byte status) {
			if (status == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) {
				for (int i = 0; i < 6; i++) {
					this.getWorld().addParticle(GLOW, this.getX(), this.getY(), this.getZ(), 0.0, 0.0, 0.0);
				}
				this.getWorld().addParticle(ParticleTypes.SMALL_FLAME, this.getX(), this.getY(), this.getZ(), 0.0, 0.02, 0.0);
			}
		}

		@Override
		protected void onCollision(HitResult hit) {
			super.onCollision(hit);
			if (!this.getWorld().isClient) {
				this.getWorld().sendEntityStatus(this, EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES);
				if (this.getWorld() instanceof ServerWorld server) {
					server.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLOCK_FIRE_EXTINGUISH, this.getSoundCategory(), 0.3F, 2.0F);
				}
				this.discard();
			}
		}
	}
}
