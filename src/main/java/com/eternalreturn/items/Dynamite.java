package com.eternalreturn.items;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mobs.ModEntities;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.DispenserBlock;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.ProjectileItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Position;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Dynamite (2 gunpowder, a paper and a string): thrown like a snowball (or shot from a dispenser), it bounces
 * to a stop and goes off 2 seconds after it was thrown, a little weaker than TNT (power 3, TNT's is 4), and
 * breaks blocks as TNT does.
 */
public final class Dynamite extends Item implements ProjectileItem {
	public static final int FUSE = 40;
	public static final float POWER = 3.0F;
	public static final Item ITEM = Registry.register(Registries.ITEM, EternalReturn.id("dynamite"), new Dynamite(new Item.Settings().maxCount(16)));

	private Dynamite(Item.Settings settings) {
		super(settings);
	}

	public static void register() {
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.addAfter(Items.TNT, ITEM));
		ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> entries.addAfter(Items.TNT_MINECART, ITEM));
		DispenserBlock.registerProjectileBehavior(ITEM);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.PLAYERS, 0.6F, 1.4F);
		if (!world.isClient) {
			Lit lit = new Lit(world, user);
			lit.setItem(stack);
			lit.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.0F, 1.0F);
			world.spawnEntity(lit);
		}
		user.getItemCooldownManager().set(this, 10);
		user.incrementStat(Stats.USED.getOrCreateStat(this));
		stack.decrementUnlessCreative(1, user);
		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public ProjectileEntity createEntity(World world, Position pos, ItemStack stack, Direction direction) {
		Lit lit = new Lit(world, pos.getX(), pos.getY(), pos.getZ());
		lit.setItem(stack);
		return lit;
	}

	/** A lit stick of dynamite in flight or lying where it landed. */
	public static class Lit extends ThrownItemEntity {
		private int fuse = FUSE;

		public Lit(EntityType<? extends Lit> type, World world) {
			super(type, world);
		}

		public Lit(World world, LivingEntity owner) {
			super(ModEntities.DYNAMITE, owner, world);
		}

		public Lit(World world, double x, double y, double z) {
			super(ModEntities.DYNAMITE, x, y, z, world);
		}

		@Override
		protected Item getDefaultItem() {
			return ITEM;
		}

		public int getFuse() {
			return this.fuse;
		}

		@Override
		public void tick() {
			super.tick();
			if (this.isRemoved()) {
				return;
			}
			World world = this.getWorld();
			if (world.isClient) {
				world.addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.2, this.getZ(), 0.0, 0.02, 0.0);
			} else if (--this.fuse <= 0) {
				this.discard();
				world.createExplosion(this, this.getX(), this.getBodyY(0.0625), this.getZ(), POWER, World.ExplosionSourceType.TNT);
			}
		}

		/** Bounces off what it hits, losing most of its speed, rather than breaking like a snowball. */
		@Override
		protected void onBlockHit(BlockHitResult hit) {
			super.onBlockHit(hit);
			Vec3d velocity = this.getVelocity();
			Direction side = hit.getSide();
			Vec3d bounced = switch (side.getAxis()) {
				case X -> new Vec3d(-velocity.x * 0.3, velocity.y * 0.5, velocity.z * 0.5);
				case Y -> new Vec3d(velocity.x * 0.5, -velocity.y * 0.3, velocity.z * 0.5);
				case Z -> new Vec3d(velocity.x * 0.5, velocity.y * 0.5, -velocity.z * 0.3);
			};
			if (bounced.lengthSquared() < 0.01) {
				bounced = Vec3d.ZERO;
			}
			this.setVelocity(bounced);
			Vec3d at = hit.getPos().add(Vec3d.of(side.getVector()).multiply(0.06));
			this.setPosition(at.x, at.y, at.z);
		}

		@Override
		protected void onEntityHit(EntityHitResult hit) {
			super.onEntityHit(hit);
			this.setVelocity(this.getVelocity().multiply(-0.2, 0.2, -0.2));
		}

		@Override
		public void writeCustomDataToNbt(NbtCompound nbt) {
			super.writeCustomDataToNbt(nbt);
			nbt.putShort("Fuse", (short) this.fuse);
		}

		@Override
		public void readCustomDataFromNbt(NbtCompound nbt) {
			super.readCustomDataFromNbt(nbt);
			this.fuse = nbt.contains("Fuse") ? nbt.getShort("Fuse") : FUSE;
		}
	}
}
