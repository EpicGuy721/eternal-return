package com.eternalreturn.logic;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.EulerAngle;

import java.util.List;

/**
 * Armor stands as in Bedrock (survival.armorStandPoses): they always have arms (so they hold items in their hands), and
 * take one of Bedrock's 13 poses (POSES): sneaking and using one steps it on to the next, and a redstone signal into
 * the block it stands in sets the pose by its strength (1 to 12, 13 and up the last), back to the stand's own pose when
 * the signal goes (ArmorStandPosesMixin).
 */
public final class ArmorStandPoses {
	/** A pose: head, body, left arm, right arm, left leg, right leg (degrees). */
	public record Pose(String name, EulerAngle head, EulerAngle body, EulerAngle leftArm, EulerAngle rightArm, EulerAngle leftLeg,
			EulerAngle rightLeg) {
	}

	private static EulerAngle a(float pitch, float yaw, float roll) {
		return new EulerAngle(pitch, yaw, roll);
	}

	private static final EulerAngle ZERO = a(0, 0, 0);
	private static final EulerAngle LEFT_LEG = a(-1, 0, -1);
	private static final EulerAngle RIGHT_LEG = a(1, 0, 1);
	private static final EulerAngle LEFT_STRIDE = a(5, -3, -3);
	private static final EulerAngle RIGHT_STRIDE = a(-5, 3, 3);

	/** Bedrock's poses, in its order (the first is the stand's default). */
	public static final List<Pose> POSES = List.of(
			new Pose("default", ZERO, ZERO, a(-10, 0, -10), a(-15, 0, 10), LEFT_LEG, RIGHT_LEG),
			new Pose("none", ZERO, ZERO, ZERO, ZERO, ZERO, ZERO),
			new Pose("solemn", a(15, 0, 0), a(0, 0, 2), a(-30, 15, 15), a(-60, -20, -10), LEFT_LEG, RIGHT_LEG),
			new Pose("athena", a(-5, 0, 0), a(0, 0, 2), a(10, 0, -5), a(-60, 20, -10), a(-3, -3, -3), a(3, 3, 3)),
			new Pose("brandish", a(-15, 0, 0), a(0, 0, -2), a(20, 0, -10), a(-110, 50, 0), LEFT_STRIDE, RIGHT_STRIDE),
			new Pose("honor", a(-15, 0, 0), ZERO, a(-110, 35, 0), a(-110, -35, 0), LEFT_STRIDE, RIGHT_STRIDE),
			new Pose("entertain", a(-15, 0, 0), ZERO, a(-110, -35, 0), a(-110, 35, 0), LEFT_STRIDE, RIGHT_STRIDE),
			new Pose("salute", ZERO, ZERO, a(10, 0, -5), a(-70, -40, 0), LEFT_LEG, RIGHT_LEG),
			new Pose("riposte", a(16, 20, 0), ZERO, a(4, 8, 237), a(246, 0, 89), a(-14, -18, -16), a(8, 20, 4)),
			new Pose("zombie", a(-10, 0, -5), ZERO, a(-105, 0, 0), a(-100, 0, 0), a(7, 0, 0), a(-46, 0, 0)),
			new Pose("cancan_a", a(-5, 18, 0), a(0, 22, 0), a(8, 0, -114), a(0, 84, 111), a(-111, 55, 0), a(0, 23, -13)),
			new Pose("cancan_b", a(-10, -20, 0), a(0, -18, 0), a(0, 0, -112), a(8, 90, 111), a(0, 0, 13), a(-119, -42, 0)),
			new Pose("hero", a(-4, 67, 0), a(0, 8, 0), a(16, 32, -8), a(-99, 63, 0), a(0, -75, -8), a(4, 63, 8)));

	/** What an armor stand keeps (ArmorStandPosesMixin): its own pose (stepped through by players) and the signal it last had. */
	public interface Holder {
		int eternalreturn$pose();

		void eternalreturn$setPose(int pose);

		int eternalreturn$power();

		void eternalreturn$setPower(int power);
	}

	private ArmorStandPoses() {
	}

	public static boolean enabled() {
		return EternalReturnConfig.get().survival.armorStandPoses;
	}

	public static void apply(ArmorStandEntity stand, int index) {
		Pose pose = POSES.get(Math.floorMod(index, POSES.size()));
		stand.setHeadRotation(pose.head());
		stand.setBodyRotation(pose.body());
		stand.setLeftArmRotation(pose.leftArm());
		stand.setRightArmRotation(pose.rightArm());
		stand.setLeftLegRotation(pose.leftLeg());
		stand.setRightLegRotation(pose.rightLeg());
	}

	/** A player sneaking and using it: on to the next pose (unless a signal holds it). */
	public static void step(ArmorStandEntity stand) {
		Holder holder = (Holder) stand;
		int next = Math.floorMod(holder.eternalreturn$pose() + 1, POSES.size());
		holder.eternalreturn$setPose(next);
		if (holder.eternalreturn$power() == 0) {
			apply(stand, next);
		}
		stand.playSound(SoundEvents.ENTITY_ARMOR_STAND_HIT, 0.6F, 1.2F);
	}

	/** Now and then (server): the signal into its block sets its pose, or gives it back its own when it goes. */
	public static void tick(ArmorStandEntity stand) {
		if (stand.age % 4 != 0) {
			return;
		}
		Holder holder = (Holder) stand;
		int power = stand.getWorld().getReceivedRedstonePower(stand.getBlockPos());
		if (power == holder.eternalreturn$power()) {
			return;
		}
		holder.eternalreturn$setPower(power);
		apply(stand, power > 0 ? Math.min(power, POSES.size() - 1) : holder.eternalreturn$pose());
	}
}
