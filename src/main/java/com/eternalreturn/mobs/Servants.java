package com.eternalreturn.mobs;

import com.eternalreturn.mixin.mobs.MobEntityAccessor;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.TrackTargetGoal;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * Undead servants, raised with the Necronomicon: a random zombie, husk, drowned, skeleton, stray, bogged,
 * wither skeleton or zombified piglin, with the gear it would spawn with. A servant follows its master, goes
 * for whatever attacks its master and whatever its master attacks, and fights back when hit (not when its
 * master hits it); otherwise it leaves every mob and player alone. It never despawns, burns in daylight like
 * any undead, and drops nothing when it dies, not even experience. A master can have 3 at once.
 */
public final class Servants {
	public static final int MAX = 3;
	private static final List<EntityType<? extends MobEntity>> KINDS = List.of(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED, EntityType.SKELETON,
			EntityType.STRAY, EntityType.BOGGED, EntityType.WITHER_SKELETON, EntityType.ZOMBIFIED_PIGLIN);

	/** What a mob keeps (MobEntityServantMixin): its master, saved with it. */
	public interface Holder {
		@Nullable
		UUID eternalreturn$master();

		void eternalreturn$setMaster(@Nullable UUID master);
	}

	private Servants() {
	}

	public static boolean isServant(LivingEntity mob) {
		return mob instanceof Holder holder && holder.eternalreturn$master() != null;
	}

	@Nullable
	public static PlayerEntity master(MobEntity mob) {
		UUID id = mob instanceof Holder holder ? holder.eternalreturn$master() : null;
		return id == null ? null : mob.getWorld().getPlayerByUuid(id);
	}

	/** The player's living servants, in every world. */
	public static int count(PlayerEntity player) {
		int n = 0;
		for (ServerWorld world : ((ServerWorld) player.getWorld()).getServer().getWorlds()) {
			for (var entity : world.iterateEntities()) {
				if (entity instanceof MobEntity mob && mob.isAlive() && entity instanceof Holder holder && player.getUuid().equals(holder.eternalreturn$master())) {
					n++;
				}
			}
		}
		return n;
	}

	@Nullable
	public static MobEntity summon(ServerWorld world, PlayerEntity master, Vec3d at) {
		return summon(world, master, at, KINDS.get(world.random.nextInt(KINDS.size())));
	}

	/** A servant of the given kind (Necromancy's stored mobs). */
	@Nullable
	public static MobEntity summon(ServerWorld world, PlayerEntity master, Vec3d at, EntityType<? extends MobEntity> kind) {
		MobEntity mob = kind.create(world);
		if (mob == null) {
			return null;
		}
		mob.refreshPositionAndAngles(at.x, at.y, at.z, master.getYaw() + 180.0F, 0.0F);
		mob.initialize(world, world.getLocalDifficulty(mob.getBlockPos()), SpawnReason.MOB_SUMMONED, null);
		// No hive or TNT on its head, and never a large one.
		ItemStack head = mob.getEquippedStack(EquipmentSlot.HEAD);
		if (head.isOf(Items.TNT) || head.isOf(Items.BEEHIVE)) {
			mob.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
		}
		var scale = mob.getAttributeInstance(EntityAttributes.GENERIC_SCALE);
		if (scale != null) {
			scale.setBaseValue(1.0);
		}
		((Holder) mob).eternalreturn$setMaster(master.getUuid());
		setUp(mob);
		world.spawnEntityAndPassengers(mob);
		world.spawnParticles(ParticleTypes.SOUL, mob.getX(), mob.getBodyY(0.5), mob.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
		world.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 1.0F, 0.6F);
		return mob;
	}

	/** Makes a mob with a master behave as a servant: on summoning, and again when it is loaded. */
	public static void setUp(MobEntity mob) {
		mob.setPersistent();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			mob.setEquipmentDropChance(slot, 0.0F);
		}
		if (!(mob instanceof PathAwareEntity creature)) {
			return;
		}
		MobEntityAccessor goals = (MobEntityAccessor) mob;
		goals.eternalreturn$targetSelector().clear(goal -> true);
		goals.eternalreturn$targetSelector().add(1, new DefendMaster(creature));
		goals.eternalreturn$targetSelector().add(2, new HelpMaster(creature));
		goals.eternalreturn$targetSelector().add(3, new FightBack(creature));
		goals.eternalreturn$goalSelector().add(6, new FollowMaster(creature));
	}

	/** Goes for whatever last hurt its master. */
	static final class DefendMaster extends TrackTargetGoal {
		private int lastAttackedTime = Integer.MIN_VALUE;

		DefendMaster(PathAwareEntity mob) {
			super(mob, false);
			this.setControls(EnumSet.of(Goal.Control.TARGET));
		}

		@Override
		public boolean canStart() {
			PlayerEntity master = master(this.mob);
			if (master == null) {
				return false;
			}
			LivingEntity attacker = master.getAttacker();
			return attacker != null && attacker != this.mob && !isServantOf(attacker, master) && master.getLastAttackedTime() != this.lastAttackedTime
					&& this.canTrack(attacker, TargetPredicate.DEFAULT);
		}

		@Override
		public void start() {
			PlayerEntity master = master(this.mob);
			this.mob.setTarget(master.getAttacker());
			this.lastAttackedTime = master.getLastAttackedTime();
			super.start();
		}
	}

	/** Goes for whatever its master last hit. */
	static final class HelpMaster extends TrackTargetGoal {
		private int lastAttackTime = Integer.MIN_VALUE;

		HelpMaster(PathAwareEntity mob) {
			super(mob, false);
			this.setControls(EnumSet.of(Goal.Control.TARGET));
		}

		@Override
		public boolean canStart() {
			PlayerEntity master = master(this.mob);
			if (master == null) {
				return false;
			}
			LivingEntity target = master.getAttacking();
			return target != null && target != this.mob && !isServantOf(target, master) && master.getLastAttackTime() != this.lastAttackTime
					&& this.canTrack(target, TargetPredicate.DEFAULT);
		}

		@Override
		public void start() {
			PlayerEntity master = master(this.mob);
			this.mob.setTarget(master.getAttacking());
			this.lastAttackTime = master.getLastAttackTime();
			super.start();
		}
	}

	/** Fights back against whatever hurts it, except its master and fellow servants. */
	static final class FightBack extends TrackTargetGoal {
		private int lastHurtTime = Integer.MIN_VALUE;

		FightBack(PathAwareEntity mob) {
			super(mob, true);
			this.setControls(EnumSet.of(Goal.Control.TARGET));
		}

		@Override
		public boolean canStart() {
			LivingEntity attacker = this.mob.getAttacker();
			PlayerEntity master = master(this.mob);
			return attacker != null && this.mob.getLastAttackedTime() != this.lastHurtTime && attacker != master
					&& !(master != null && isServantOf(attacker, master)) && this.canTrack(attacker, TargetPredicate.DEFAULT);
		}

		@Override
		public void start() {
			this.mob.setTarget(this.mob.getAttacker());
			this.lastHurtTime = this.mob.getLastAttackedTime();
			super.start();
		}
	}

	/** Keeps near its master: walks after it beyond 6 blocks, and catches up at once beyond 24. */
	static final class FollowMaster extends Goal {
		private final PathAwareEntity mob;
		private PlayerEntity master;
		private int repath;

		FollowMaster(PathAwareEntity mob) {
			this.mob = mob;
			this.setControls(EnumSet.of(Goal.Control.MOVE, Goal.Control.LOOK));
		}

		@Override
		public boolean canStart() {
			PlayerEntity master = master(this.mob);
			if (master == null || master.isSpectator() || this.mob.getTarget() != null || this.mob.squaredDistanceTo(master) < 36.0) {
				return false;
			}
			this.master = master;
			return true;
		}

		@Override
		public boolean shouldContinue() {
			return this.master.isAlive() && this.mob.getTarget() == null && this.mob.squaredDistanceTo(this.master) > 9.0
					&& this.master.getWorld() == this.mob.getWorld();
		}

		@Override
		public void start() {
			this.repath = 0;
		}

		@Override
		public void stop() {
			this.master = null;
			this.mob.getNavigation().stop();
		}

		@Override
		public void tick() {
			this.mob.getLookControl().lookAt(this.master, 10.0F, this.mob.getMaxLookPitchChange());
			if (--this.repath > 0) {
				return;
			}
			this.repath = 10;
			if (this.mob.squaredDistanceTo(this.master) > 24.0 * 24.0) {
				this.mob.refreshPositionAndAngles(this.master.getX(), this.master.getY(), this.master.getZ(), this.mob.getYaw(), this.mob.getPitch());
				this.mob.getNavigation().stop();
			} else {
				this.mob.getNavigation().startMovingTo(this.master, 1.1);
			}
		}
	}

	private static boolean isServantOf(LivingEntity entity, PlayerEntity master) {
		return entity instanceof Holder holder && master.getUuid().equals(holder.eternalreturn$master());
	}
}
