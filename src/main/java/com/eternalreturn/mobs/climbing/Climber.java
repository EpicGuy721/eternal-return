package com.eternalreturn.mobs.climbing;

import com.eternalreturn.config.EternalReturnConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Walking on floors, walls and ceilings. The mob clings to one side of its hitbox: its feet (the floor, vanilla
 * walking), a wall, or its back (the ceiling). Where it's going (its target, or a spot SurfaceWanderGoal picked) is
 * reached by vanilla walking when vanilla's pathfinding gets there; otherwise, or once off the floor, by a route over
 * floors, walls and ceilings (SurfacePaths), worked out again now and then, which it follows step by step (on the
 * floor through its move control). Off the floor it moves along the surface toward the next step (else straight
 * toward where it's going, else where its move control was last sent), with a pull toward the surface in place of
 * gravity:
 * <ul>
 * <li>walking into a surface ahead of it it steps onto it (floor to wall, wall to ceiling, wall down to floor);</li>
 * <li>walking off an edge (the top of a wall, the lip of a ceiling, round a corner) it wraps round onto the face behind
 * the edge;</li>
 * <li>above its target on a ceiling it lets go and drops on it; stuck, or in water, or dead, it lets go and falls.</li>
 * </ul>
 * It doesn't step straight back onto the surface it just left (TURN_BACK_TICKS), so it can't flip between two in a
 * corner. Its legs keep walking with its movement up and down. On the client it keeps a smoothed orientation (up away from the
 * surface, forward the way it moves) that the renderer turns the model by (ClimberRendering), so it tips smoothly
 * from floor to wall to ceiling. Which side it clings to is synced through a tracked value the mob supplies.
 */
public final class Climber {
	/** Off the floor, how strongly it's pulled against its surface (blocks per tick per tick; gravity is 0.08). */
	private static final double CLING = 0.08;
	/** Its speed off the floor, as a share of its movement speed attribute (about a spider's walking pace). */
	private static final double SURFACE_SPEED = 0.4;
	/** Ticks a move control order steers it. */
	private static final int STEER_TICKS = 5;
	/** Ticks out of touch with any surface before it falls. */
	private static final int LOOSE_LIMIT = 6;
	/** Ticks without getting anywhere before it lets go. */
	private static final int STUCK_LIMIT = 60;
	/** Ticks between working out routes. */
	private static final int REROUTE_TICKS = 20;
	/** How fast the drawn body turns toward its new orientation each tick (share of the way). */
	private static final float TURN_RATE = 0.3F;
	/** Ticks before it may step back onto the surface it just left (else in a corner it flips between the two, shaking). */
	private static final int TURN_BACK_TICKS = 20;
	private final MobEntity mob;
	private final Supplier<Byte> read;
	private final Consumer<Byte> write;
	@Nullable
	private Vec3d steer;
	private double steerSpeed = 1.0;
	private int steerAge = -100;
	private int loose;
	private int stuck;
	/** Ticks on the floor pressed against a wall. */
	private int bumped;
	@Nullable
	private Vec3d destination;
	private double destinationSpeed = 1.0;
	private int destinationAge = -100;
	@Nullable
	private List<SurfacePaths.Step> route;
	private int step;
	private int rerouteAt;
	/** The surface it last stepped off, and when. */
	@Nullable
	private Direction left;
	private int leftAge = -100;
	@Nullable
	private Vec3d routedTo;
	// Client side: the drawn orientation.
	private final Quaternionf orientation = new Quaternionf();
	private final Quaternionf prevOrientation = new Quaternionf();
	private Vec3d forward = new Vec3d(0.0, 0.0, 1.0);
	private Vec3d up = new Vec3d(0.0, 1.0, 0.0);
	private Vec3d prevUp = new Vec3d(0.0, 1.0, 0.0);
	private boolean primed;

	/** read and write: the tracked byte holding the side it clings to (Direction id; 0, down, is the floor). */
	public Climber(MobEntity mob, Supplier<Byte> read, Consumer<Byte> write) {
		this.mob = mob;
		this.read = read;
		this.write = write;
	}

	public static boolean enabled() {
		return EternalReturnConfig.get().mobs.surfaceWalkingSpiders;
	}

	/** The side of its hitbox it clings to: DOWN for the floor. */
	public Direction attach() {
		return Direction.byId(this.read.get());
	}

	public void setAttach(Direction side) {
		if (side != this.attach()) {
			this.left = this.attach();
			this.leftAge = this.mob.age;
			this.write.accept((byte) side.getId());
			this.loose = 0;
			this.stuck = 0;
		}
	}

	/** Whether it may step onto this surface of its own accord: not straight back onto the one it just left. */
	private boolean mayStepOnto(Direction side) {
		return side != this.left || this.mob.age - this.leftAge > TURN_BACK_TICKS;
	}

	/** On a wall or ceiling (not the floor). */
	public boolean offFloor() {
		return this.attach() != Direction.DOWN;
	}

	/** Its move control was sent toward this spot. */
	public void steerTo(double x, double y, double z, double speed) {
		this.steer = new Vec3d(x, y, z);
		this.steerSpeed = speed;
		this.steerAge = this.mob.age;
	}

	/** Go to this spot (SurfaceWanderGoal: sent each tick while it wants it). */
	public void setDestination(Vec3d spot, double speed) {
		this.destination = spot;
		this.destinationSpeed = speed;
		this.destinationAge = this.mob.age;
	}

	/** Where it's going: its target, else a spot it was sent to (if lately); null if nowhere. */
	@Nullable
	private Vec3d destination() {
		LivingEntity target = this.mob.getTarget();
		if (target != null && target.isAlive()) {
			return target.getBoundingBox().getCenter();
		}
		return this.destination != null && this.mob.age - this.destinationAge <= STEER_TICKS ? this.destination : null;
	}

	/** The next step of its route, if it's following one. */
	@Nullable
	private SurfacePaths.Step nextStep() {
		return this.route != null && this.step < this.route.size() ? this.route.get(this.step) : null;
	}

	/** On the floor, following a route: where its move control should take its feet next (else null, and vanilla steers). */
	@Nullable
	public Vec3d floorWaypoint() {
		SurfacePaths.Step next = this.nextStep();
		return next != null && !this.offFloor() ? next.feet() : null;
	}

	public double speed() {
		return this.mob.getTarget() != null ? 1.0 : this.destinationSpeed;
	}

	/** Where it's headed off the floor: its route's next step, else where it's going, else its last move order (if recent). */
	@Nullable
	private Vec3d goal() {
		SurfacePaths.Step next = this.nextStep();
		if (next != null) {
			Direction turn = next.place().side();
			if (turn.getAxis() != this.attach().getAxis() && !this.touching(turn, 0.1)) {
				BlockPos here = SurfacePaths.anchor(this.mob.getWorld(), this.mob.getBoundingBox(), this.attach());
				if (next.place().cell().offset(turn).equals(here.offset(this.attach()))) {
					// Round an edge of the block it's on: carry on past the edge till its surface runs out (then it wraps round).
					return next.center().subtract(Vec3d.of(turn.getVector()).multiply(0.8));
				}
			}
			return next.center();
		}
		Vec3d destination = this.destination();
		if (destination != null) {
			return destination;
		}
		return this.steer != null && this.mob.age - this.steerAge <= STEER_TICKS ? this.steer : null;
	}

	/** Whether vanilla's pathfinding has a way to its target over the ground. */
	private boolean walkable() {
		if (this.mob.getTarget() == null) {
			return false;
		}
		Path path = this.mob.getNavigation().getCurrentPath();
		return path != null && path.reachesTarget() && !path.isFinished();
	}

	/** Works out (now and then) and follows its route over the surfaces, stepping from surface to surface as the route turns. */
	private void route() {
		Vec3d destination = this.destination();
		if (destination == null || (!this.offFloor() && this.walkable())) {
			this.route = null;
			return;
		}
		if (this.route == null || this.mob.age >= this.rerouteAt || this.routedTo == null || this.routedTo.squaredDistanceTo(destination) > 2.25) {
			double reach = this.mob.getTarget() != null ? 0.6 + this.mob.getWidth() : 1.0;
			this.route = SurfacePaths.find(this.mob, this.attach(), destination, reach);
			this.step = 0;
			this.rerouteAt = this.mob.age + REROUTE_TICKS;
			this.routedTo = destination;
		}
		SurfacePaths.Step next = this.nextStep();
		if (next == null) {
			return;
		}
		Vec3d center = this.mob.getBoundingBox().getCenter();
		Direction side = next.place().side();
		if (side != this.attach() && this.touching(side, 0.1) && this.mayStepOnto(side)) {
			this.setAttach(side);
		}
		double distance = center.squaredDistanceTo(next.center());
		if (distance < 0.36 || (side == this.attach() && distance < 0.64)) {
			this.step++;
		}
	}

	private boolean touching(Direction side, double reach) {
		Box box = this.mob.getBoundingBox().offset(Vec3d.of(side.getVector()).multiply(reach));
		return !this.mob.getWorld().isSpaceEmpty(this.mob, box);
	}

	/** Whether it should let go of any wall or ceiling (or never take one): dead, riding or ridden (a jockey's rider), in water or lava, leashed. */
	private boolean grounded() {
		return !enabled() || !this.mob.isAlive() || this.mob.hasVehicle() || this.mob.hasPassengers() || this.mob.isTouchingWater() || this.mob.isInLava() || this.mob.isLeashed();
	}

	/**
	 * In place of LivingEntity.travel: off the floor it moves along its surface and returns true; on the floor (or
	 * letting go) it returns false and walks as usual.
	 */
	public boolean travel() {
		if (!this.offFloor()) {
			return false;
		}
		if (this.grounded()) {
			this.setAttach(Direction.DOWN);
			return false;
		}
		if (this.mob.getWorld().isClient) {
			this.mob.updateLimbs(true);
			return true;
		}
		Direction side = this.attach();
		Vec3d n = Vec3d.of(side.getVector());
		Vec3d center = this.mob.getBoundingBox().getCenter();
		Vec3d goal = this.goal();
		Vec3d want = goal == null ? Vec3d.ZERO : goal.subtract(center);
		LivingEntity target = this.mob.getTarget();
		// On a ceiling right above its target: drop on it.
		if (side == Direction.UP && target != null && target.isAlive() && target.getY() < this.mob.getY()
				&& MathHelper.square(want.x) + MathHelper.square(want.z) < 1.5) {
			this.setAttach(Direction.DOWN);
			return false;
		}
		Vec3d along = want.subtract(n.multiply(want.dotProduct(n)));
		double length = along.length();
		double speed = this.mob.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * (this.destination() != null ? this.speed() : this.steerSpeed)
				* SURFACE_SPEED;
		Vec3d push = length > 0.05 ? along.multiply(speed * Math.min(1.0, length) / length) : Vec3d.ZERO;
		Vec3d velocity = this.mob.getVelocity().multiply(0.5).add(push.multiply(0.5)).add(n.multiply(CLING));
		Vec3d before = this.mob.getPos();
		this.mob.move(MovementType.SELF, velocity);
		Vec3d moved = this.mob.getPos().subtract(before);
		this.mob.setVelocity(moved.subtract(n.multiply(moved.dotProduct(n))));
		this.mob.fallDistance = 0.0F;
		this.mob.updateLimbs(true);
		// Walked into a surface it wants to go through: step onto it.
		if (push.lengthSquared() > 1.0E-4) {
			Direction blocked = blockedSide(velocity, moved, side);
			if (blocked != null && want.normalize().dotProduct(Vec3d.of(blocked.getVector())) > 0.2 && this.touching(blocked, 0.05) && this.mayStepOnto(blocked)) {
				this.setAttach(blocked);
				return true;
			}
		}
		// Walked off its surface (over an edge): wrap round onto the face behind the edge.
		if (!this.touching(side, 0.05)) {
			this.loose++;
			if (this.loose == 1) {
				Vec3d motion = moved.subtract(n.multiply(moved.dotProduct(n)));
				if (motion.lengthSquared() < 1.0E-6) {
					motion = push;
				}
				Direction behind = motion.lengthSquared() < 1.0E-6 ? null : Direction.getFacing(-motion.x, -motion.y, -motion.z);
				if (behind != null && behind.getAxis() != side.getAxis() && this.mayStepOnto(behind)) {
					// Round the edge: on toward where the surface was, then cling to the face behind.
					this.mob.move(MovementType.SELF, n.multiply(0.4));
					this.mob.setVelocity(n.multiply(0.1));
					this.setAttach(behind);
					return true;
				}
			}
			if (this.loose > LOOSE_LIMIT) {
				this.setAttach(Direction.DOWN);
			}
		} else {
			this.loose = 0;
		}
		// Getting nowhere: let go.
		if (goal != null && moved.lengthSquared() < 2.5E-5 && push.lengthSquared() > 1.0E-4) {
			if (++this.stuck > STUCK_LIMIT) {
				this.setAttach(Direction.DOWN);
			}
		} else {
			this.stuck = 0;
		}
		return true;
	}

	/** The side (not along the clinging axis) on which it was stopped short: wanted to move, barely did. */
	@Nullable
	private static Direction blockedSide(Vec3d wanted, Vec3d moved, Direction side) {
		Direction best = null;
		double most = 0.0;
		for (Direction.Axis axis : Direction.Axis.values()) {
			if (axis == side.getAxis()) {
				continue;
			}
			double want = wanted.getComponentAlongAxis(axis);
			double got = moved.getComponentAlongAxis(axis);
			if (Math.abs(want) > 0.01 && Math.abs(got) < Math.abs(want) * 0.25 && Math.abs(want) > most) {
				most = Math.abs(want);
				best = Direction.from(axis, want > 0.0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
			}
		}
		return best;
	}

	/** After the mob's tick: on the server, steps from the floor onto a wall it walked into on its way; on the client, turns the drawn body. */
	public void tick() {
		World world = this.mob.getWorld();
		if (world.isClient) {
			this.turn();
			return;
		}
		if (this.grounded()) {
			this.route = null;
			return;
		}
		this.route();
		this.bumped = !this.offFloor() && this.mob.horizontalCollision ? this.bumped + 1 : 0;
		// (bumping a wall on the way somewhere it can walk to, it walks round, unless it's caught on it)
		if (this.offFloor() || !this.mob.horizontalCollision || this.nextStep() != null || (this.walkable() && this.bumped < 10)) {
			return;
		}
		Vec3d goal = this.destination() != null ? this.destination() : this.goal();
		if (goal == null) {
			return;
		}
		Vec3d want = goal.subtract(this.mob.getBoundingBox().getCenter());
		if (want.lengthSquared() < 1.0E-4) {
			return;
		}
		want = want.normalize();
		Direction best = null;
		double most = 0.2;
		for (Direction side : Direction.Type.HORIZONTAL) {
			double toward = want.dotProduct(Vec3d.of(side.getVector()));
			if (toward > most && this.touching(side, 0.05) && this.mayStepOnto(side)) {
				most = toward;
				best = side;
			}
		}
		if (best != null) {
			this.setAttach(best);
		}
	}

	public void writeNbt(NbtCompound nbt) {
		nbt.putByte("EternalReturnClings", (byte) this.attach().getId());
	}

	public void readNbt(NbtCompound nbt) {
		this.write.accept((byte) MathHelper.clamp(nbt.getByte("EternalReturnClings"), 0, 5));
	}

	// Client side: the drawn orientation.

	/** One tick of turning the drawn body toward its surface (up away from it) and its way (forward). */
	private void turn() {
		Vec3d targetUp = Vec3d.of(this.attach().getOpposite().getVector());
		Vec3d targetForward;
		if (!this.offFloor()) {
			float yaw = this.mob.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
			targetForward = new Vec3d(-MathHelper.sin(yaw), 0.0, MathHelper.cos(yaw));
		} else {
			Vec3d motion = new Vec3d(this.mob.getX() - this.mob.prevX, this.mob.getY() - this.mob.prevY, this.mob.getZ() - this.mob.prevZ);
			Vec3d flat = motion.subtract(targetUp.multiply(motion.dotProduct(targetUp)));
			if (flat.lengthSquared() > 1.0E-4) {
				targetForward = flat.normalize();
			} else {
				Vec3d kept = this.forward.subtract(targetUp.multiply(this.forward.dotProduct(targetUp)));
				// Facing straight into its new surface (stepping onto it): it goes on the way its back faced.
				targetForward = kept.lengthSquared() > 1.0E-3 ? kept.normalize() : this.up.subtract(targetUp.multiply(this.up.dotProduct(targetUp))).normalize();
				if (!Double.isFinite(targetForward.x) || targetForward.lengthSquared() < 0.5) {
					targetForward = Math.abs(targetUp.y) < 0.5 ? new Vec3d(0.0, 1.0, 0.0) : new Vec3d(0.0, 0.0, 1.0);
				}
			}
		}
		this.forward = targetForward;
		Quaternionf target = basis(targetForward, targetUp);
		this.prevOrientation.set(this.orientation);
		this.prevUp = this.up;
		if (!this.primed) {
			this.orientation.set(target);
			this.prevOrientation.set(target);
			this.primed = true;
		} else if (!this.offFloor() && this.up.y > 0.999) {
			// Settled on the floor: exactly as vanilla turns it (no lag behind its body's turning).
			this.orientation.set(target);
		} else {
			this.orientation.slerp(target, TURN_RATE);
		}
		Vector3f drawnUp = this.orientation.transform(new Vector3f(0.0F, 1.0F, 0.0F));
		this.up = new Vec3d(drawnUp.x, drawnUp.y, drawnUp.z);
	}

	/** The rotation taking the model's frame (forward -z, up +y) to forward and up: as vanilla's 180 - yaw turn on the floor. */
	private static Quaternionf basis(Vec3d forward, Vec3d up) {
		Vector3f u = new Vector3f((float) up.x, (float) up.y, (float) up.z).normalize();
		Vector3f f = new Vector3f((float) forward.x, (float) forward.y, (float) forward.z);
		f.sub(new Vector3f(u).mul(f.dot(u))).normalize();
		Vector3f x = new Vector3f(f).cross(u);
		Vector3f z = new Vector3f(f).negate();
		return new Quaternionf().setFromNormalized(new Matrix3f(x, u, z));
	}

	/** Whether the drawn body is turned from vanilla's upright pose (so the renderer must turn it). */
	public boolean turned() {
		return this.primed && (this.offFloor() || this.up.y < 0.999 || this.prevUp.y < 0.999);
	}

	/** The drawn body's rotation (model frame to world), between ticks. */
	public Quaternionf rotation(float tickDelta) {
		return new Quaternionf(this.prevOrientation).slerp(this.orientation, tickDelta);
	}

	/** The drawn body's up, between ticks. */
	public Vec3d up(float tickDelta) {
		return this.prevUp.lerp(this.up, tickDelta);
	}

	/** How far the drawn body is turned onto a wall: 0 upright or upside down, 1 flat on a wall. */
	public float wallness(float tickDelta) {
		Vec3d up = this.up(tickDelta);
		return (float) Math.min(1.0, Math.sqrt(up.x * up.x + up.z * up.z));
	}
}
