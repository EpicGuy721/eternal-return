package com.eternalreturn.mobs;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Boids (Craig Reynolds' flocking rules), shared by flocking birds (com.eternalreturn.birds) and schooling fish
 * (FishSchoolGoal). Each animal steers by its nearest flockmates:
 * <ul>
 * <li>separation: away from those closer than the separation distance, harder the closer they are;</li>
 * <li>alignment: toward the flock's average velocity;</li>
 * <li>cohesion: toward the flock's centre.</li>
 * </ul>
 * The callers add their own pulls (a destination, obstacles, staying in the water or above the ground) and cap the
 * turn and speed.
 */
public final class Boids {
	/** How far flockmates are seen (blocks), and how many of the nearest count. */
	public record Rules(double sight, int neighbours, double separation, double separationWeight, double alignmentWeight, double cohesionWeight) {
	}

	private Boids() {
	}

	/** The nearest flockmates within sight (not the animal itself). */
	public static <T extends Entity> List<T> neighbours(T self, Class<T> kind, Rules rules, Predicate<T> mate) {
		Box sight = self.getBoundingBox().expand(rules.sight());
		return self.getWorld().getEntitiesByClass(kind, sight, other -> other != self && other.isAlive() && mate.test(other)).stream()
				.sorted(Comparator.comparingDouble(self::squaredDistanceTo)).limit(rules.neighbours()).toList();
	}

	/** The steering (a change of velocity) the three rules give. */
	public static Vec3d steer(Entity self, List<? extends Entity> mates, Rules rules) {
		if (mates.isEmpty()) {
			return Vec3d.ZERO;
		}
		Vec3d position = self.getPos();
		Vec3d velocity = self.getVelocity();
		Vec3d apart = Vec3d.ZERO;
		Vec3d heading = Vec3d.ZERO;
		Vec3d centre = Vec3d.ZERO;
		for (Entity mate : mates) {
			Vec3d offset = position.subtract(mate.getPos());
			double distance = offset.length();
			if (distance < rules.separation() && distance > 1.0E-4) {
				apart = apart.add(offset.multiply((rules.separation() - distance) / (distance * rules.separation())));
			}
			heading = heading.add(mate.getVelocity());
			centre = centre.add(mate.getPos());
		}
		heading = heading.multiply(1.0 / mates.size()).subtract(velocity);
		centre = centre.multiply(1.0 / mates.size()).subtract(position);
		return apart.multiply(rules.separationWeight()).add(heading.multiply(rules.alignmentWeight())).add(centre.multiply(rules.cohesionWeight()));
	}

	/** The velocity after steering: the turn capped at maxTurn a tick, the speed held between minSpeed and maxSpeed. */
	public static Vec3d apply(Vec3d velocity, Vec3d steering, double maxTurn, double minSpeed, double maxSpeed) {
		double turn = steering.length();
		if (turn > maxTurn) {
			steering = steering.multiply(maxTurn / turn);
		}
		Vec3d next = velocity.add(steering);
		double speed = next.length();
		if (speed < 1.0E-6) {
			return next;
		}
		if (speed > maxSpeed) {
			return next.multiply(maxSpeed / speed);
		}
		if (speed < minSpeed) {
			return next.multiply(minSpeed / speed);
		}
		return next;
	}
}
