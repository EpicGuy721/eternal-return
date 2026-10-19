package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityStatuses;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Rifles and bullets (soldiers carry them, com.eternalreturn.mobs.entity.SoldierEntity). A bullet flies straight and
 * fast, and hits hard: DAMAGE (a projectile's damage, so armor and Projectile Protection soften it, and from a mob it
 * scales with difficulty), and it wears each armor piece the target has on by ARMOR_WEAR on top of the usual. A
 * rifle shoots one of the bullets in its holder's inventory (none needed in creative), then takes RELOAD ticks to
 * reload (reloadSounds: the bolt drawn back a second after the shot, and closed just before it's ready); it wears by 1
 * a shot.
 */
public final class Firearms {
	public static final float DAMAGE = 20.0F;
	public static final int ARMOR_WEAR = 10;
	public static final int RELOAD = 60;
	/** Blocks a tick. */
	public static final float SPEED = 3.5F;

	public static final Item BULLET = Registry.register(Registries.ITEM, EternalReturn.id("bullet"), new Item(new Item.Settings()));
	public static final Item RIFLE = Registry.register(Registries.ITEM, EternalReturn.id("rifle"), new RifleItem(new Item.Settings().maxDamage(250)));
	public static final EntityType<Bullet> BULLET_ENTITY = Registry.register(Registries.ENTITY_TYPE, EternalReturn.id("bullet"),
			EntityType.Builder.<Bullet>create(Bullet::new, SpawnGroup.MISC).dimensions(0.15F, 0.15F).maxTrackingRange(8).trackingTickInterval(1)
					.build(EternalReturn.id("bullet").toString()));

	private Firearms() {
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> {
			entries.addAfter(Items.CROSSBOW, RIFLE);
			entries.addAfter(Items.ARROW, BULLET);
		});
	}

	/** A shot from this shooter along the direction, with this much spread (in the units ProjectileEntity.setVelocity takes). */
	public static Bullet fire(LivingEntity shooter, Vec3d direction, float spread) {
		World world = shooter.getWorld();
		Bullet bullet = new Bullet(world, shooter);
		bullet.setPosition(shooter.getX(), shooter.getEyeY() - 0.15, shooter.getZ());
		bullet.setVelocity(direction.x, direction.y, direction.z, SPEED, spread);
		world.spawnEntity(bullet);
		world.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, shooter.getSoundCategory(), 2.5F, 0.5F);
		if (world instanceof ServerWorld server) {
			Vec3d muzzle = shooter.getEyePos().add(direction.normalize().multiply(1.2));
			server.spawnParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y - 0.15, muzzle.z, 6, 0.05, 0.05, 0.05, 0.01);
		}
		return bullet;
	}

	/** The reload's sounds, by ticks since the shot (out of RELOAD): the bolt drawn back, then closed. */
	public static void reloadSounds(LivingEntity shooter, int sinceShot, int reload) {
		World world = shooter.getWorld();
		if (sinceShot == 20) {
			world.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_START.value(), shooter.getSoundCategory(), 1.0F, 0.8F);
		} else if (sinceShot == reload - 10) {
			world.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.ITEM_CROSSBOW_LOADING_END.value(), shooter.getSoundCategory(), 1.0F, 0.8F);
		}
	}

	public static class RifleItem extends Item {
		public RifleItem(Settings settings) {
			super(settings);
		}

		@Override
		public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
			ItemStack rifle = user.getStackInHand(hand);
			ItemStack ammo = ItemStack.EMPTY;
			if (!user.getAbilities().creativeMode) {
				for (int i = 0; i < user.getInventory().size() && ammo.isEmpty(); i++) {
					if (user.getInventory().getStack(i).isOf(BULLET)) {
						ammo = user.getInventory().getStack(i);
					}
				}
				if (ammo.isEmpty()) {
					world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 0.6F, 1.4F);
					return TypedActionResult.fail(rifle);
				}
			}
			if (!world.isClient) {
				fire(user, user.getRotationVector(), 0.0F);
				ammo.decrement(1);
				rifle.damage(1, user, LivingEntity.getSlotForHand(hand));
			}
			user.getItemCooldownManager().set(this, RELOAD);
			user.incrementStat(Stats.USED.getOrCreateStat(this));
			return TypedActionResult.success(rifle, world.isClient());
		}

		/** The reload's sounds, while the rifle cools down after a shot. */
		@Override
		public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
			if (!world.isClient && entity instanceof PlayerEntity player) {
				float left = player.getItemCooldownManager().getCooldownProgress(this, 0.0F);
				if (left > 0.0F) {
					reloadSounds(player, RELOAD - Math.round(left * RELOAD), RELOAD);
				}
			}
		}
	}

	public static class Bullet extends ThrownItemEntity {
		public Bullet(EntityType<? extends Bullet> type, World world) {
			super(type, world);
		}

		public Bullet(World world, LivingEntity owner) {
			super(BULLET_ENTITY, owner, world);
		}

		@Override
		protected Item getDefaultItem() {
			return BULLET;
		}

		/** Straight: no drop. */
		@Override
		protected double getGravity() {
			return 0.0;
		}

		@Override
		public void tick() {
			super.tick();
			if (!this.getWorld().isClient && this.age > 100) {
				this.discard();
			}
		}

		/** The hit: its damage, and extra wear on every armor piece the target wears. */
		public void strike(Entity entity) {
			DamageSource source = this.getOwner() instanceof LivingEntity owner ? this.getDamageSources().mobProjectile(this, owner)
					: this.getDamageSources().thrown(this, this.getOwner());
			boolean hurt = entity.damage(source, DAMAGE);
			if (hurt && entity instanceof LivingEntity living) {
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					if (slot.isArmorSlot()) {
						ItemStack armor = living.getEquippedStack(slot);
						if (armor.isDamageable()) {
							armor.damage(ARMOR_WEAR, living, slot);
						}
					}
				}
			}
		}

		@Override
		protected void onEntityHit(EntityHitResult hit) {
			super.onEntityHit(hit);
			this.strike(hit.getEntity());
		}

		@Override
		public void handleStatus(byte status) {
			if (status == EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES) {
				for (int i = 0; i < 4; i++) {
					this.getWorld().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0, 0.02, 0.0);
				}
			}
		}

		@Override
		protected void onCollision(HitResult hit) {
			super.onCollision(hit);
			if (!this.getWorld().isClient) {
				this.getWorld().sendEntityStatus(this, EntityStatuses.PLAY_DEATH_SOUND_OR_ADD_PROJECTILE_HIT_PARTICLES);
				this.discard();
			}
		}
	}
}
