package com.eternalreturn.mobs.ai;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.config.EternalReturnConfig;
import com.eternalreturn.mobs.climbing.SurfaceWalker;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Monsters see players only in a cone in front of their head (mobs.sightConeDegrees across), or smell them close by
 * whichever way they face (mobs.smellRange by entity id, else mobs.defaultSmellRange: zombies far, skeletons hardly):
 * spotting a player to target, or to turn and look at (vanilla still wants a line of sight either way). A
 * player it already hunts, or one who hurt it, it keeps track of all round. Some see all round (entity tag
 * eternalreturn:all_seeing), and so does a spider or gimp on a wall or ceiling. Checked where vanilla checks a mob
 * can see a player it might target (TargetPredicate, by VisionConeMixin).
 */
public final class VisionCone {
	public static final TagKey<EntityType<?>> ALL_SEEING = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("all_seeing"));
	/** Blind: they notice players only by smell (and, the grimlock, by hearing: GrimlockEntity). */
	public static final TagKey<EntityType<?>> BLIND = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("blind"));

	private VisionCone() {
	}

	/** Whether base, looking for targets, can see target for its cone (true when the cone doesn't apply). */
	public static boolean sees(LivingEntity base, LivingEntity target) {
		if (!(base instanceof MobEntity mob) || !(base instanceof Monster) || !(target instanceof PlayerEntity)) {
			return true;
		}
		EternalReturnConfig.MobTweaks config = EternalReturnConfig.get().mobs;
		if (base.getType().isIn(BLIND)) {
			return mob.getTarget() == target || mob.getAttacker() == target || base.squaredDistanceTo(target) <= MathHelper.square(smellRange(base.getType()));
		}
		if (config.sightConeDegrees <= 0 || config.sightConeDegrees >= 360 || base.getType().isIn(ALL_SEEING)) {
			return true;
		}
		if (mob.getTarget() == target || mob.getAttacker() == target) {
			return true;
		}
		if (base instanceof SurfaceWalker walker && walker.eternalreturn$climber().offFloor()) {
			return true;
		}
		Vec3d to = target.getEyePos().subtract(base.getEyePos());
		double distance = to.length();
		if (distance <= smellRange(base.getType()) || distance < 1.0E-4) {
			return true;
		}
		float yaw = base.getHeadYaw() * MathHelper.RADIANS_PER_DEGREE;
		float pitch = base.getPitch() * MathHelper.RADIANS_PER_DEGREE;
		Vec3d look = new Vec3d(-MathHelper.sin(yaw) * MathHelper.cos(pitch), -MathHelper.sin(pitch), MathHelper.cos(yaw) * MathHelper.cos(pitch));
		return look.dotProduct(to) / distance >= Math.cos(config.sightConeDegrees * 0.5 * MathHelper.RADIANS_PER_DEGREE);
	}

	/** How far (blocks) this kind of monster smells a player. */
	public static double smellRange(EntityType<?> type) {
		EternalReturnConfig.MobTweaks config = EternalReturnConfig.get().mobs;
		String id = Registries.ENTITY_TYPE.getId(type).toString();
		Double range = config.smellRange.get(id);
		return range != null ? range : EternalReturnConfig.defaultSmellRange().getOrDefault(id, config.defaultSmellRange);
	}
}
