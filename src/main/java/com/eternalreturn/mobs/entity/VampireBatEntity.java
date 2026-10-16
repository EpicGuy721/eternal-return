package com.eternalreturn.mobs.entity;

import com.eternalreturn.mobs.Vampires;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A vampire's bat (com.eternalreturn.mobs.Vampires), one of two kinds:
 * <ul>
 * <li>called up in a fight: it flies at the vampire's target and bites (a player is poisoned, a villager turned),
 * then pulls back for a moment and comes again; it is gone after 30 seconds, or once its target is;</li>
 * <li>a vampire in bat form: it flies away from players, and once none has been within 16 blocks for 5 seconds,
 * it turns back into the vampire it was, health and all.</li>
 * </ul>
 * Either burns in daylight. With nothing to do, it is an ordinary bat.
 */
public class VampireBatEntity extends BatEntity {
	public static final int LIFETIME = 600;
	@Nullable
	private UUID owner;
	@Nullable
	private UUID targetId;
	/** The vampire it is (bat form), or null for a summoned bat. */
	@Nullable
	private NbtCompound vampire;
	private int lived;
	private int biteCooldown;
	private int safeTicks;

	public VampireBatEntity(EntityType<? extends BatEntity> type, World world) {
		super(type, world);
		this.experiencePoints = 0;
	}

	public void summonedBy(VampireEntity vampire, @Nullable LivingEntity target) {
		this.owner = vampire.getUuid();
		this.targetId = target == null ? null : target.getUuid();
		this.setRoosting(false);
	}

	public void becomeForm(NbtCompound vampire) {
		this.vampire = vampire;
		this.setRoosting(false);
	}

	public boolean isForm() {
		return this.vampire != null;
	}

	@Nullable
	public UUID owner() {
		return this.owner;
	}

	@Override
	public void tickMovement() {
		if (this.isAlive() && this.isAffectedByDaylight()) {
			this.setOnFireFor(8.0F);
		}
		super.tickMovement();
	}

	@Override
	protected void mobTick() {
		if (!(this.getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (this.vampire != null) {
			this.flee(world);
			return;
		}
		this.lived++;
		Entity found = this.targetId == null ? null : world.getEntity(this.targetId);
		LivingEntity target = found instanceof LivingEntity living && living.isAlive() && !living.isSpectator()
				&& !(living instanceof PlayerEntity player && player.isCreative()) ? living : null;
		if (this.lived > LIFETIME || (this.targetId != null && target == null)) {
			Vampires.vanish(this);
			return;
		}
		if (target == null) {
			super.mobTick();
			return;
		}
		this.setRoosting(false);
		if (this.biteCooldown > 0) {
			this.biteCooldown--;
		}
		Vec3d aim = target.getPos().add(0.0, target.getHeight() * 0.6, 0.0);
		// After a bite, it hangs back a little before going in again.
		if (this.biteCooldown > 10) {
			aim = aim.add(0.0, 2.0, 0.0);
		}
		this.steer(aim.subtract(this.getPos()), 0.35);
		if (this.biteCooldown == 0 && this.getBoundingBox().expand(0.3).intersects(target.getBoundingBox())) {
			Vampires.batBite(this, target);
			this.biteCooldown = 30;
		}
	}

	/** Bat form: away from players and up; safe for 5 seconds, it turns back. */
	private void flee(ServerWorld world) {
		PlayerEntity player = Vampires.playerNear(this, 16.0);
		if (player == null) {
			if (++this.safeTicks >= 100) {
				Vampires.fromBat(this, this.vampire);
				return;
			}
			super.mobTick();
			return;
		}
		this.safeTicks = 0;
		this.setRoosting(false);
		Vec3d away = this.getPos().subtract(player.getPos()).multiply(1.0, 0.0, 1.0);
		if (away.lengthSquared() < 1.0E-4) {
			away = new Vec3d(this.random.nextDouble() - 0.5, 0.0, this.random.nextDouble() - 0.5);
		}
		this.steer(away.normalize().add(0.0, 0.35, 0.0), 0.45);
	}

	private void steer(Vec3d direction, double speed) {
		if (direction.lengthSquared() < 1.0E-6) {
			return;
		}
		Vec3d wanted = direction.normalize().multiply(speed);
		Vec3d velocity = this.getVelocity();
		this.setVelocity(velocity.add(wanted.subtract(velocity).multiply(0.3)));
		float yaw = (float) (MathHelper.atan2(this.getVelocity().z, this.getVelocity().x) * 180.0F / (float) Math.PI) - 90.0F;
		this.setYaw(this.getYaw() + MathHelper.wrapDegrees(yaw - this.getYaw()));
		this.forwardSpeed = 0.5F;
	}

	@Override
	public boolean cannotDespawn() {
		return this.vampire != null || super.cannotDespawn();
	}

	@Override
	public void writeCustomDataToNbt(NbtCompound nbt) {
		super.writeCustomDataToNbt(nbt);
		if (this.owner != null) {
			nbt.putUuid("eternalreturn_owner", this.owner);
		}
		if (this.targetId != null) {
			nbt.putUuid("eternalreturn_target", this.targetId);
		}
		if (this.vampire != null) {
			nbt.put("eternalreturn_vampire", this.vampire);
		}
		nbt.putInt("eternalreturn_lived", this.lived);
	}

	@Override
	public void readCustomDataFromNbt(NbtCompound nbt) {
		super.readCustomDataFromNbt(nbt);
		this.owner = nbt.containsUuid("eternalreturn_owner") ? nbt.getUuid("eternalreturn_owner") : null;
		this.targetId = nbt.containsUuid("eternalreturn_target") ? nbt.getUuid("eternalreturn_target") : null;
		this.vampire = nbt.contains("eternalreturn_vampire") ? nbt.getCompound("eternalreturn_vampire") : null;
		this.lived = nbt.getInt("eternalreturn_lived");
	}
}
