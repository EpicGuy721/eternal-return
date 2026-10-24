package com.eternalreturn.mobs.entity;

import com.eternalreturn.mobs.ai.Lounging;
import com.eternalreturn.mobs.ai.VisionCone;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.goal.ZombieAttackGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.LightType;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The grimlock: a grey, eyeless zombie of the deep caves (below y 0, in the dark), shirtless in tattered trousers.
 * Blind (entity tag eternalreturn:blind), it finds players as the warden does: it smells one close by (its smell
 * range, keener than a zombie's) and hears noises within HEARING blocks (what a sculk sensor hears, not a sneaking
 * player's steps), and walks to where it heard one. It hunts a target only while it can smell it: out of its smell
 * (for more than LOST ticks) it loses it and goes to where it last smelled it. A noise elsewhere draws it off a target
 * (unless the target is within CLOSE blocks), and a hit from afar sends it to where the hit came from rather than
 * straight to the attacker. It burns in the sun like any zombie, and shuns light: it backs out
 * of anywhere lit (block light LIGHT or more, or daylight), won't chase a player standing in the light, and wanders
 * toward the dark. A zombie's drops; no gear, no turning into a drowned.
 */
public class GrimlockEntity extends ZombieEntity {
	/** Blocks within which it hears a noise. */
	public static final double HEARING = 16.0;
	/** Block light at which a place is too bright for it. */
	public static final int LIGHT = 7;
	/** Ticks a noise stays worth going to see. */
	public static final int MEMORY = 100;
	/** Ticks out of smell before it loses a target. */
	public static final int LOST = 20;
	/** Ticks a noise elsewhere keeps its nose off a target. */
	public static final int DISTRACTED = 60;
	/** A target this close (blocks) it won't be drawn off. */
	public static final double CLOSE = 3.0;
	private static final Map<ServerWorld, Set<GrimlockEntity>> LISTENING = new WeakHashMap<>();
	@Nullable
	private Vec3d noise;
	private int heardAt = -1000;
	/** Where, and when, it last smelled its target. */
	@Nullable
	private Vec3d scent;
	private int smelledAt = -1000;
	private int distractedUntil = -1000;
	/** It had a target last tick (to notice a hunt ending, however it ended). */
	private boolean hunting;

	public GrimlockEntity(EntityType<? extends ZombieEntity> type, World world) {
		super(type, world);
	}

	public static DefaultAttributeContainer.Builder createGrimlockAttributes() {
		return ZombieEntity.createZombieAttributes().add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
	}

	/** Dark, and deep: below y 0. */
	public static boolean canSpawn(EntityType<GrimlockEntity> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
		return (pos.getY() < 0 || reason != SpawnReason.NATURAL) && HostileEntity.canSpawnInDark(type, world, reason, pos, random);
	}

	/** Too bright for a grimlock: lamp or torch light, or daylight. */
	public static boolean lit(World world, BlockPos pos) {
		return world.getLightLevel(LightType.BLOCK, pos) >= LIGHT || world.isDay() && world.getLightLevel(LightType.SKY, pos) > 10;
	}

	@Override
	protected void initCustomGoals() {
		this.goalSelector.add(1, new ShunLightGoal(this));
		this.goalSelector.add(2, new DarkAttackGoal(this));
		this.goalSelector.add(5, new InvestigateNoiseGoal(this));
		this.goalSelector.add(7, new DarkWanderGoal(this));
		this.targetSelector.add(2, new SmellTargetGoal<>(this, PlayerEntity.class));
		this.targetSelector.add(3, new SmellTargetGoal<>(this, IronGolemEntity.class));
	}

	/** Within its smelling range (no sight needed: it has none). */
	boolean smells(LivingEntity entity) {
		double range = VisionCone.smellRange(this.getType());
		return this.squaredDistanceTo(entity) <= range * range;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.getWorld() instanceof ServerWorld world && this.isAlive()) {
			LISTENING.computeIfAbsent(world, w -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
		}
	}

	/** It hunts a target only while it can smell it; out of its smell for long, it goes to where it last did. */
	@Override
	protected void mobTick() {
		super.mobTick();
		LivingEntity target = this.getTarget();
		if (target != null) {
			if (this.smells(target) && this.age >= this.distractedUntil) {
				this.scent = target.getPos();
				this.smelledAt = this.age;
			} else if (this.age - this.smelledAt > LOST) {
				this.setTarget(null);
				target = null;
			}
		}
		if (target == null && this.hunting && this.scent != null && this.age >= this.distractedUntil) {
			// The hunt is over (out of its smell, or too far off to keep after): it goes to look where it last smelled them.
			this.noise = this.scent;
			this.heardAt = this.age;
		}
		this.hunting = target != null;
		// Never into the light, wherever it is heading (a noise beyond a lit stretch, say): in the dark, it gives up a
		// path whose next step is lit. (Out of the light it goes by ShunLightGoal.)
		net.minecraft.entity.ai.pathing.Path path = this.getNavigation().getCurrentPath();
		if (path != null && !path.isFinished() && !lit(this.getWorld(), this.getBlockPos()) && lit(this.getWorld(), path.getCurrentNodePos())) {
			this.getNavigation().stop();
			this.getMoveControl().moveTo(this.getX(), this.getY(), this.getZ(), 0.0);
		}
	}

	/** Hit: an attacker it can smell it turns on; one further off (an arrow) only tells it where to go and look. */
	@Override
	public boolean damage(DamageSource source, float amount) {
		boolean hurt = super.damage(source, amount);
		if (hurt && !this.getWorld().isClient && source.getAttacker() instanceof LivingEntity attacker && attacker != this) {
			if (this.smells(attacker)) {
				this.setTarget(attacker);
				this.scent = attacker.getPos();
				this.smelledAt = this.age;
			} else if (this.getTarget() == attacker) {
				this.setTarget(null);
				this.noise = attacker.getPos();
				this.heardAt = this.age;
			} else if (this.getTarget() == null) {
				this.noise = attacker.getPos();
				this.heardAt = this.age;
			}
		}
		return hurt;
	}

	/**
	 * A noise in the world (a game event): grimlocks in earshot note where. One on the hunt is drawn off its target by a
	 * noise somewhere else, unless the target is right by it.
	 */
	public static void hear(ServerWorld world, RegistryEntry<GameEvent> event, Vec3d at, GameEvent.Emitter emitter) {
		Set<GrimlockEntity> listening = LISTENING.get(world);
		if (listening == null || listening.isEmpty() || !Lounging.isNoise(event, emitter)) {
			return;
		}
		for (GrimlockEntity grimlock : List.copyOf(listening)) {
			if (grimlock.isRemoved() || !grimlock.isAlive()) {
				listening.remove(grimlock);
				continue;
			}
			if (grimlock.squaredDistanceTo(at) > HEARING * HEARING) {
				continue;
			}
			LivingEntity target = grimlock.getTarget();
			if (target != null) {
				if (emitter.sourceEntity() == target || grimlock.squaredDistanceTo(target) <= CLOSE * CLOSE) {
					continue;
				}
				grimlock.setTarget(null);
				grimlock.hunting = false;
				grimlock.distractedUntil = grimlock.age + DISTRACTED;
			}
			grimlock.noise = at;
			grimlock.heardAt = grimlock.age;
		}
	}

	/** Wandering, it heads for the dark. */
	@Override
	public float getPathfindingFavor(BlockPos pos, WorldView world) {
		return world instanceof World level && lit(level, pos) ? -10.0F : super.getPathfindingFavor(pos, world);
	}

	@Override
	protected boolean canConvertInWater() {
		return false;
	}

	@Override
	protected void initEquipment(Random random, LocalDifficulty difficulty) {
	}

	@Override
	public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
		data = super.initialize(world, difficulty, reason, data);
		this.setCanPickUpLoot(false);
		return data;
	}

	/** A zombie's drops. */
	@Override
	public RegistryKey<LootTable> getLootTableId() {
		return EntityType.ZOMBIE.getLootTableId();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.ENTITY_ZOMBIE_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource source) {
		return SoundEvents.ENTITY_ZOMBIE_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.ENTITY_ZOMBIE_DEATH;
	}

	/** Lower than a zombie's. */
	@Override
	public float getSoundPitch() {
		return super.getSoundPitch() * 0.75F;
	}

	/** Spots a target by smell only (not while a noise has drawn it off); mobTick lets it go once out of smell. */
	static class SmellTargetGoal<T extends LivingEntity> extends ActiveTargetGoal<T> {
		private final GrimlockEntity grimlock;

		SmellTargetGoal(GrimlockEntity grimlock, Class<T> targetClass) {
			super(grimlock, targetClass, 10, false, false, grimlock::smells);
			this.grimlock = grimlock;
		}

		@Override
		public boolean canStart() {
			return this.grimlock.age >= this.grimlock.distractedUntil && super.canStart();
		}

		@Override
		public void start() {
			super.start();
			this.grimlock.smelledAt = this.grimlock.age;
		}
	}

	/** A zombie's attack, but never after a target standing in the light. */
	static class DarkAttackGoal extends ZombieAttackGoal {
		private final GrimlockEntity grimlock;

		DarkAttackGoal(GrimlockEntity grimlock) {
			super(grimlock, 1.0, false);
			this.grimlock = grimlock;
		}

		private boolean inTheDark() {
			LivingEntity target = this.grimlock.getTarget();
			return target != null && !lit(this.grimlock.getWorld(), target.getBlockPos());
		}

		@Override
		public boolean canStart() {
			return this.inTheDark() && super.canStart();
		}

		@Override
		public boolean shouldContinue() {
			return this.inTheDark() && super.shouldContinue();
		}
	}

	/** Standing in the light: it backs off to the nearest dark place it can stand (searching out to SHUN_RANGE blocks). */
	static class ShunLightGoal extends Goal {
		private static final int SHUN_RANGE = 16;
		private final GrimlockEntity grimlock;
		private int nextSearch;

		ShunLightGoal(GrimlockEntity grimlock) {
			this.grimlock = grimlock;
			this.setControls(EnumSet.of(Goal.Control.MOVE));
		}

		@Override
		public boolean canStart() {
			World world = this.grimlock.getWorld();
			BlockPos at = this.grimlock.getBlockPos();
			if (!lit(world, at) || this.grimlock.age < this.nextSearch) {
				return false;
			}
			this.nextSearch = this.grimlock.age + 20;
			// Ring by ring outward: the first dark spot it can stand on and get to.
			for (int r = 1; r <= SHUN_RANGE; r++) {
				for (int dx = -r; dx <= r; dx++) {
					for (int dz = -r; dz <= r; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
							continue;
						}
						for (int dy = -3; dy <= 3; dy++) {
							BlockPos spot = at.add(dx, dy, dz);
							if (world.getBlockState(spot.down()).isSolidBlock(world, spot.down()) && world.isAir(spot) && world.isAir(spot.up())
									&& !lit(world, spot) && this.grimlock.getNavigation().startMovingTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 1.3)) {
								return true;
							}
						}
					}
				}
			}
			return false;
		}

		@Override
		public boolean shouldContinue() {
			return !this.grimlock.getNavigation().isIdle();
		}
	}

	/** Wandering: only when it has nothing to hunt (lurking by a target in the light, it waits), and only to dark spots. */
	static class DarkWanderGoal extends WanderAroundFarGoal {
		private final GrimlockEntity grimlock;

		DarkWanderGoal(GrimlockEntity grimlock) {
			super(grimlock, 1.0);
			this.grimlock = grimlock;
		}

		@Override
		public boolean canStart() {
			return this.grimlock.getTarget() == null && super.canStart();
		}

		@Nullable
		@Override
		protected Vec3d getWanderTarget() {
			for (int i = 0; i < 5; i++) {
				Vec3d spot = super.getWanderTarget();
				if (spot != null && !lit(this.grimlock.getWorld(), BlockPos.ofFloored(spot))) {
					return spot;
				}
			}
			return null;
		}
	}

	/** It heard something lately, and has no target: it goes to see, for up to 15 seconds (unless the noise was somewhere lit). */
	static class InvestigateNoiseGoal extends Goal {
		private final GrimlockEntity grimlock;
		private int headingFor = -1;
		private int ticks;

		InvestigateNoiseGoal(GrimlockEntity grimlock) {
			this.grimlock = grimlock;
			this.setControls(EnumSet.of(Goal.Control.MOVE));
		}

		/** A noise to go to: not in the light, and nothing to hunt meanwhile. */
		private boolean worthIt() {
			return this.grimlock.noise != null && this.grimlock.getTarget() == null && !lit(this.grimlock.getWorld(), BlockPos.ofFloored(this.grimlock.noise));
		}

		@Override
		public boolean canStart() {
			return this.worthIt() && this.grimlock.age - this.grimlock.heardAt < MEMORY && this.grimlock.squaredDistanceTo(this.grimlock.noise) > 4.0;
		}

		@Override
		public void start() {
			this.ticks = 0;
			this.go();
		}

		private void go() {
			this.headingFor = this.grimlock.heardAt;
			Vec3d at = this.grimlock.noise;
			this.grimlock.getNavigation().startMovingTo(at.x, at.y, at.z, 1.0);
		}

		@Override
		public boolean shouldContinue() {
			return this.worthIt() && ++this.ticks < 300 && !this.grimlock.getNavigation().isIdle();
		}

		@Override
		public void tick() {
			if (this.grimlock.heardAt != this.headingFor) {
				this.go();
			}
		}
	}

	@Override
	public void remove(Entity.RemovalReason reason) {
		super.remove(reason);
		if (this.getWorld() instanceof ServerWorld world && LISTENING.containsKey(world)) {
			LISTENING.get(world).remove(this);
		}
	}
}
