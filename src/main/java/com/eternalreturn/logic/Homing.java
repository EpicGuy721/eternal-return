package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.mixin.combat.PersistentProjectileAccessor;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Curse of Homing (armor, I only; found like the other curses, never in the table): a projectile someone else shot
 * (arrows, tridents, fireballs, snowballs, potions, bullets, webs, another player's shots; not a fishing line) that
 * comes within 1 block of the wearer per cursed piece they wear (four pieces: 4 blocks) turns straight for them, at
 * the speed it had (HomingMixin, every tick of the projectile, on both sides so it's drawn as it flies). The wearer's
 * own shots fly true.
 */
public final class Homing {
	public static final Identifier ID = EternalReturn.id("homing_curse");
	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** The widest pull: four cursed pieces. */
	private static final double MAX_RADIUS = 4.0;

	private Homing() {
	}

	/** The pull's radius around this player: 1 block per cursed piece worn. */
	public static int radius(PlayerEntity player) {
		int pieces = 0;
		for (EquipmentSlot slot : ARMOR) {
			if (NewEnchantments.level(player.getEquippedStack(slot), ID) > 0) {
				pieces++;
			}
		}
		return pieces;
	}

	/** The cursed player this projectile homes on, or null. */
	@Nullable
	public static PlayerEntity target(ProjectileEntity projectile) {
		if (projectile instanceof FishingBobberEntity
				|| projectile instanceof PersistentProjectileEntity arrow && ((PersistentProjectileAccessor) arrow).eternalreturn$inGround()
				|| projectile.getVelocity().lengthSquared() < 1.0E-4) {
			return null;
		}
		PlayerEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		Vec3d pos = projectile.getPos();
		for (PlayerEntity player : projectile.getWorld().getPlayers()) {
			if (player.isSpectator() || !player.isAlive() || player == projectile.getOwner()) {
				continue;
			}
			double distance = player.getBoundingBox().getCenter().squaredDistanceTo(pos);
			if (distance > 64.0 || distance >= bestDistance) {
				continue;
			}
			int radius = radius(player);
			// Measured to the nearest point of the player, so a pull of 1 block reaches past the edge of their body.
			if (radius > 0 && player.getBoundingBox().expand(radius).contains(pos)) {
				best = player;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Turns the projectile toward a cursed player near it, keeping its speed. */
	public static void steer(ProjectileEntity projectile) {
		PlayerEntity target = target(projectile);
		if (target == null) {
			return;
		}
		Vec3d toward = target.getBoundingBox().getCenter().subtract(projectile.getPos());
		if (toward.lengthSquared() < 1.0E-6) {
			return;
		}
		projectile.setVelocity(toward.normalize().multiply(projectile.getVelocity().length()));
		projectile.velocityDirty = true;
	}
}
