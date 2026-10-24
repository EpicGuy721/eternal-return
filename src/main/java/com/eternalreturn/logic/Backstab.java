package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Backstabber (melee weapons, I to V): a hit from behind (the side of the target's back, past the line across its
 * shoulders) does 10% more damage per level. Struck hits only, not a thrown trident (BackstabMixin).
 */
public final class Backstab {
	public static final Identifier ID = EternalReturn.id("backstabber");
	private static final float PER_LEVEL = 0.1F;

	private Backstab() {
	}

	/** Whether the attacker stands behind the target. */
	public static boolean behind(Entity attacker, LivingEntity target) {
		Vec3d facing = Vec3d.fromPolar(0.0F, target.getBodyYaw());
		Vec3d toAttacker = attacker.getPos().subtract(target.getPos());
		return facing.x * toAttacker.x + facing.z * toAttacker.z < 0.0;
	}

	/** The damage of a hit with this weapon, with the Backstabber bonus if it lands from behind. */
	public static float damage(ItemStack weapon, Entity target, DamageSource source, float damage) {
		int level = NewEnchantments.level(weapon, ID);
		if (level > 0 && source.isDirect() && source.getAttacker() != null && target instanceof LivingEntity living && behind(source.getAttacker(), living)) {
			return damage * (1.0F + PER_LEVEL * level);
		}
		return damage;
	}
}
