package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * Launch, Reeling, Spread of Ailments and Curse of Ascension:
 * <ul>
 * <li>Launch (melee weapons, I to V): a hit throws the target straight up instead of away (higher each
 * level), with no sideways knockback. Can't share a weapon with Knockback;</li>
 * <li>Reeling (bows and crossbows, I to V): an arrow that hits a mob or player pulls it toward the shooter, a
 * fifth of the way per level (all the way at V). Can't share a bow with Punch;</li>
 * <li>Spread of Ailments (bows and crossbows, one level): every arrow carries the shooter's active effects,
 * with the time they have left, and passes them on to what it hits, like a tipped arrow;</li>
 * <li>Curse of Ascension (anything): a dropped item with it drifts slowly up into the sky, about a block
 * every two seconds.</li>
 * </ul>
 */
public final class NewEnchantments {
	public static final Identifier LAUNCH = EternalReturn.id("launch");
	public static final Identifier REELING = EternalReturn.id("reeling");
	public static final Identifier SPREAD_OF_AILMENTS = EternalReturn.id("spread_of_ailments");
	public static final Identifier ASCENSION_CURSE = EternalReturn.id("ascension_curse");

	private NewEnchantments() {
	}

	public static int level(@Nullable ItemStack stack, Identifier id) {
		if (stack == null || stack.isEmpty()) {
			return 0;
		}
		ItemEnchantmentsComponent enchantments = stack.getOrDefault(DataComponentTypes.ENCHANTMENTS, ItemEnchantmentsComponent.DEFAULT);
		for (var entry : enchantments.getEnchantmentEntries()) {
			if (entry.getKey().matchesId(id)) {
				return entry.getIntValue();
			}
		}
		return 0;
	}

	/** After a melee hit lands: Launch on the weapon throws the target up. */
	public static void launch(LivingEntity attacker, Entity target) {
		int level = level(attacker.getMainHandStack(), LAUNCH);
		if (level > 0 && target instanceof LivingEntity living && living.isAlive()) {
			living.setVelocity(0.0, 0.3 + 0.15 * level, 0.0);
			living.velocityModified = true;
		}
	}

	/** An arrow from a Reeling bow hit something: pull it toward the shooter. */
	public static void reel(PersistentProjectileEntity arrow, Entity hit) {
		int level = level(arrow.getWeaponStack(), REELING);
		if (level <= 0 || !(arrow.getOwner() instanceof LivingEntity shooter) || !(hit instanceof LivingEntity target) || !target.isAlive() || target == shooter) {
			return;
		}
		Vec3d offset = shooter.getPos().subtract(target.getPos());
		Vec3d flat = new Vec3d(offset.x, 0.0, offset.z);
		double distance = flat.length();
		if (distance < 1.5) {
			return;
		}
		// A short hop toward the shooter: level V lands it about a block and a half away, lower levels a fifth of the way per level.
		// (A mob given a hop of 0.3 travels about 7 times its sideways speed, through the air and sliding to a stop.)
		Vec3d push = flat.normalize().multiply((distance - 1.5) * level / 5.0 / 7.0);
		target.setVelocity(push.x, 0.3 + MathHelper.clamp(offset.y * 0.08, 0.0, 0.5), push.z);
		target.velocityModified = true;
	}

	/** An arrow is made by a bow or crossbow: with Spread of Ailments, it takes the shooter's effects. */
	public static void spread(LivingEntity shooter, ItemStack weapon, ProjectileEntity projectile) {
		if (level(weapon, SPREAD_OF_AILMENTS) <= 0 || !(projectile instanceof ArrowEntity arrow)) {
			return;
		}
		for (StatusEffectInstance effect : shooter.getStatusEffects()) {
			arrow.addEffect(new StatusEffectInstance(effect.getEffectType(), effect.getDuration(), effect.getAmplifier(), effect.isAmbient(), effect.shouldShowParticles(), effect.shouldShowIcon()));
		}
	}

	/** Each tick of a dropped item: with Curse of Ascension, it drifts up. */
	public static void ascend(ItemEntity item) {
		if (level(item.getStack(), ASCENSION_CURSE) > 0 && EternalReturnConfig.get().enchantmentsExtra.ascensionCurseRises) {
			Vec3d velocity = item.getVelocity();
			item.setNoGravity(true);
			item.setVelocity(velocity.x * 0.9, 0.025, velocity.z * 0.9);
		}
	}
}
