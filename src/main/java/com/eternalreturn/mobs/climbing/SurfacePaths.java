package com.eternalreturn.mobs.climbing;

import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Routes over floors, walls and ceilings for a surface walker (Climber): A* over places it can cling, each a block it
 * fills and the side it clings to (a solid face of the block beside it on that side), where its hitbox fits. From a
 * place it can go on along the same surface, turn onto a surface in its way (an inside corner: floor to wall, wall to
 * ceiling), or round an edge onto the face behind it (an outside corner: over the top of a wall, off a ledge and down
 * its side). A wide hitbox may sit up to its overhang off the block's middle to fit.
 */
public final class SurfacePaths {
	/** Places looked at before it settles for the best route found. */
	private static final int SEARCH_LIMIT = 400;
	private static final double EDGE_COST = 1.4;

	private SurfacePaths() {
	}

	/** A place: the block its hitbox anchors in, and the side it clings to. */
	public record Place(BlockPos cell, Direction side) {
	}

	/** A step of a route: the place, and the hitbox there. */
	public record Step(Place place, Box box) {
		public Vec3d center() {
			return this.box.getCenter();
		}

		/** Where its feet go (what a move control steers to on the floor). */
		public Vec3d feet() {
			return new Vec3d(this.box.getCenter().x, this.box.minY, this.box.getCenter().z);
		}
	}

	private record Open(Place place, double cost, double estimate) {
	}

	/**
	 * A route from where the mob is (clinging to side) to within reach blocks of goal, or toward the closest it can
	 * get if it can't reach it (only if that's closer than it is); null if it can't get closer. Excludes its start.
	 */
	@Nullable
	public static List<Step> find(MobEntity mob, Direction side, Vec3d goal, double reach) {
		Search search = new Search(mob);
		Place start = new Place(anchor(mob.getWorld(), mob.getBoundingBox(), side), side);
		Map<Place, Place> from = new HashMap<>();
		Map<Place, Double> costs = new HashMap<>();
		PriorityQueue<Open> open = new PriorityQueue<>((a, b) -> Double.compare(a.cost + a.estimate, b.cost + b.estimate));
		costs.put(start, 0.0);
		open.add(new Open(start, 0.0, mob.getBoundingBox().getCenter().distanceTo(goal)));
		Place best = null;
		double bestDistance = mob.getBoundingBox().getCenter().distanceTo(goal) - 1.0;
		int looked = 0;
		while (!open.isEmpty() && looked++ < SEARCH_LIMIT) {
			Open next = open.poll();
			if (next.cost > costs.getOrDefault(next.place, Double.MAX_VALUE)) {
				continue;
			}
			if (next.place != start) {
				double distance = search.box(next.place).getCenter().distanceTo(goal);
				if (distance <= reach) {
					best = next.place;
					break;
				}
				if (distance < bestDistance) {
					bestDistance = distance;
					best = next.place;
				}
			}
			for (Map.Entry<Place, Double> step : search.neighbours(next.place).entrySet()) {
				double cost = next.cost + step.getValue();
				if (cost < costs.getOrDefault(step.getKey(), Double.MAX_VALUE)) {
					costs.put(step.getKey(), cost);
					from.put(step.getKey(), next.place);
					Box box = search.box(step.getKey());
					open.add(new Open(step.getKey(), cost, box.getCenter().distanceTo(goal)));
				}
			}
		}
		if (best == null) {
			return null;
		}
		List<Step> route = new ArrayList<>();
		for (Place place = best; place != null && !place.equals(start); place = from.get(place)) {
			route.add(new Step(place, search.box(place)));
		}
		Collections.reverse(route);
		return route;
	}

	/**
	 * The block a hitbox clinging to side holds on from: of the block half a block in from the middle of its face on that
	 * side and those around it on that face, the nearest with a solid block on that side (it may hang half off an edge).
	 */
	public static BlockPos anchor(World world, Box box, Direction side) {
		BlockPos middle = cellOf(box, side);
		Vec3d center = box.getCenter();
		BlockPos best = middle;
		double nearest = Double.MAX_VALUE;
		for (Direction way : Direction.values()) {
			if (way.getAxis() == side.getAxis()) {
				continue;
			}
			for (BlockPos cell : new BlockPos[] {middle, middle.offset(way)}) {
				BlockPos held = cell.offset(side);
				if (world.getBlockState(held).getCollisionShape(world, held).isEmpty()) {
					continue;
				}
				double distance = Vec3d.ofCenter(cell).squaredDistanceTo(center);
				if (cell.equals(middle)) {
					distance -= 0.01;
				}
				if (distance < nearest) {
					nearest = distance;
					best = cell;
				}
			}
		}
		return best;
	}

	/** The block a hitbox clinging to side anchors in: half a block in from the middle of its face on that side. */
	public static BlockPos cellOf(Box box, Direction side) {
		Vec3d center = box.getCenter();
		double x = center.x;
		double y = center.y;
		double z = center.z;
		switch (side) {
			case DOWN -> y = box.minY + 0.5;
			case UP -> y = box.maxY - 0.5;
			case NORTH -> z = box.minZ + 0.5;
			case SOUTH -> z = box.maxZ - 0.5;
			case WEST -> x = box.minX + 0.5;
			case EAST -> x = box.maxX - 0.5;
		}
		return BlockPos.ofFloored(x, y, z);
	}

	/** One search's view of the world, remembering which places fit. */
	private static final class Search {
		private final MobEntity mob;
		private final World world;
		private final double width;
		private final double height;
		private final Map<Place, Box> fits = new HashMap<>();

		Search(MobEntity mob) {
			this.mob = mob;
			this.world = mob.getWorld();
			this.width = mob.getWidth();
			this.height = mob.getHeight();
		}

		private boolean solid(BlockPos pos) {
			return !this.world.getBlockState(pos).getCollisionShape(this.world, pos).isEmpty();
		}

		/** Its hitbox at a place (fitted, or as placed if it doesn't fit). */
		Box box(Place place) {
			Box box = this.fit(place);
			return box != null ? box : placed(place, 0.0, 0.0, 0.0);
		}

		/** Its hitbox clinging at a place, its face flush with the cell's face on that side, centred on the cell along the others. */
		private Box placed(Place place, double dx, double dy, double dz) {
			BlockPos cell = place.cell;
			double ex = this.width / 2.0;
			double ey = this.height / 2.0;
			double cx = cell.getX() + 0.5 + dx;
			double cy = cell.getY() + 0.5 + dy;
			double cz = cell.getZ() + 0.5 + dz;
			switch (place.side) {
				case DOWN -> cy = cell.getY() + ey;
				case UP -> cy = cell.getY() + 1.0 - ey;
				case NORTH -> cz = cell.getZ() + ex;
				case SOUTH -> cz = cell.getZ() + 1.0 - ex;
				case WEST -> cx = cell.getX() + ex;
				case EAST -> cx = cell.getX() + 1.0 - ex;
			}
			return new Box(cx - ex, cy - ey, cz - ex, cx + ex, cy + ey, cz + ex);
		}

		/** Its hitbox at a place where it fits, shifted off the middle by up to its overhang if need be; null if there's no surface or no room. */
		@Nullable
		Box fit(Place place) {
			if (this.fits.containsKey(place)) {
				return this.fits.get(place);
			}
			Box fitted = null;
			if (this.solid(place.cell.offset(place.side)) && !this.solid(place.cell)) {
				Direction.Axis along = place.side.getAxis();
				double sx = along == Direction.Axis.X ? 0.0 : Math.max(0.0, (this.width - 1.0) / 2.0);
				double sy = along == Direction.Axis.Y ? 0.0 : Math.max(0.0, (this.height - 1.0) / 2.0);
				double sz = along == Direction.Axis.Z ? 0.0 : Math.max(0.0, (this.width - 1.0) / 2.0);
				search:
				for (double dx : sx > 0.0 ? new double[] {0.0, -sx, sx} : new double[] {0.0}) {
					for (double dy : sy > 0.0 ? new double[] {0.0, -sy, sy} : new double[] {0.0}) {
						for (double dz : sz > 0.0 ? new double[] {0.0, -sz, sz} : new double[] {0.0}) {
							Box box = this.placed(place, dx, dy, dz);
							if (this.world.isSpaceEmpty(this.mob, box.contract(0.02))) {
								fitted = box;
								break search;
							}
						}
					}
				}
			}
			this.fits.put(place, fitted);
			return fitted;
		}

		/** Where it can go from a place in one step, and what each step costs. */
		Map<Place, Double> neighbours(Place place) {
			Map<Place, Double> steps = new HashMap<>();
			Direction side = place.side;
			for (Direction way : Direction.values()) {
				if (way.getAxis() == side.getAxis()) {
					continue;
				}
				BlockPos ahead = place.cell.offset(way);
				if (this.solid(ahead)) {
					// Something in the way: turn onto it (an inside corner).
					Place turned = new Place(place.cell, way);
					if (this.fit(turned) != null) {
						steps.put(turned, 1.0);
					}
					continue;
				}
				Place on = new Place(ahead, side);
				if (this.fit(on) != null) {
					steps.put(on, 1.0);
				} else if (!this.solid(ahead.offset(side))) {
					// The surface ends: round the edge onto the face behind it (an outside corner).
					Place round = new Place(ahead.offset(side), way.getOpposite());
					if (this.fit(round) != null) {
						steps.put(round, EDGE_COST);
					}
				}
			}
			return steps;
		}
	}
}
