package com.eternalreturn.logic;

import com.eternalreturn.EternalReturn;
import com.eternalreturn.items.Spears;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * How spears fight (Spears): the jab, the charge, and mobs charging with them (SpearMobMixin).
 */
public final class SpearCombat {
	/** Mobs that charge with a spear now and then; the rest only jab with it. */
	public static final TagKey<EntityType<?>> CHARGERS = TagKey.of(RegistryKeys.ENTITY_TYPE, EternalReturn.id("spear_chargers"));
	public static final float KNOCKBACK_SPEED = 5.1F;
	public static final float DAMAGE_SPEED = 4.6F;
	public static final double MIN_REACH = 2.0;
	public static final double MAX_REACH = 4.5;
	public static final double MAX_CREATIVE_REACH = 6.5;
	/** Mobs reach half as far. */
	public static final double MOB_FACTOR = 0.5;
	private static final double MARGIN = 0.125;
	/** The same thing isn't struck by one charge twice within this many ticks. */
	private static final int REHIT_TICKS = 10;
	/** A charging mob's speed, about a sprinting player's. */
	private static final double MOB_CHARGE_SPEED = 0.36;
	private static final Identifier MOB_CHARGE = EternalReturn.id("spear_charge");

	/** Per charging entity, when it last struck each thing (by entity id, in its own age). */
	private static final Map<LivingEntity, Int2IntMap> STRUCK = new WeakHashMap<>();
	/** Players' movement over the last tick, which the server doesn't otherwise know. */
	private static final Map<UUID, Vec3d> LAST_POS = new HashMap<>();
	private static final Map<UUID, Vec3d> PLAYER_VELOCITY = new HashMap<>();

	private SpearCombat() {
	}

	/** The client's jab: the attack key with a spear. */
	public record JabPayload() implements CustomPayload {
		public static final CustomPayload.Id<JabPayload> ID = new CustomPayload.Id<>(EternalReturn.id("spear_jab"));
		public static final PacketCodec<RegistryByteBuf, JabPayload> CODEC = PacketCodec.unit(new JabPayload());

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	public static void register() {
		PayloadTypeRegistry.playC2S().register(JabPayload.ID, JabPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(JabPayload.ID, (payload, context) -> jab(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				Vec3d pos = player.getPos();
				Vec3d last = LAST_POS.put(player.getUuid(), pos);
				PLAYER_VELOCITY.put(player.getUuid(), last == null ? Vec3d.ZERO : pos.subtract(last));
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			LAST_POS.remove(handler.player.getUuid());
			PLAYER_VELOCITY.remove(handler.player.getUuid());
		});
	}

	/** How far something moved over the last tick, in blocks: players as tracked, a rider with its mount. */
	public static Vec3d velocity(Entity entity) {
		if (entity instanceof ServerPlayerEntity player) {
			return PLAYER_VELOCITY.getOrDefault(player.getUuid(), Vec3d.ZERO);
		}
		Entity mount = entity.getRootVehicle();
		return mount == entity ? entity.getVelocity() : velocity(mount);
	}

	/** The things a spear in this entity's hands reaches along its line of sight, up to the first block it can't pass. */
	public static List<Entity> reached(LivingEntity user, boolean livingOnly) {
		boolean creative = user instanceof PlayerEntity player && player.isCreative();
		double factor = user instanceof PlayerEntity ? 1.0 : MOB_FACTOR;
		double min = MIN_REACH * factor;
		double max = (creative ? MAX_CREATIVE_REACH : MAX_REACH) * factor;
		Vec3d eyes = user.getEyePos();
		Vec3d look = user.getRotationVec(1.0F);
		HitResult block = user.getWorld().raycast(new RaycastContext(eyes, eyes.add(look.multiply(max)), RaycastContext.ShapeType.COLLIDER,
				RaycastContext.FluidHandling.NONE, user));
		double limit = block.getType() == HitResult.Type.MISS ? max : eyes.distanceTo(block.getPos());
		Vec3d end = eyes.add(look.multiply(limit));
		List<Entity> reached = new ArrayList<>();
		for (Entity entity : user.getWorld().getOtherEntities(user, new Box(eyes, end).expand(1.0), entity -> entity.canHit() && entity.isAttackable()
				&& !entity.isSpectator() && !(entity instanceof ProjectileEntity) && !user.isConnectedThroughVehicle(entity)
				&& (!livingOnly || entity instanceof LivingEntity))) {
			Box box = entity.getBoundingBox().expand(MARGIN + entity.getTargetingMargin());
			Optional<Vec3d> in = box.contains(eyes) ? Optional.of(eyes) : box.raycast(eyes, end);
			if (in.isEmpty()) {
				continue;
			}
			// Where the line leaves it: it must reach past the near limit.
			double out = box.raycast(end, eyes).map(eyes::distanceTo).orElse(limit);
			if (out >= min) {
				reached.add(entity);
			}
		}
		return reached;
	}

	private static DamageSource source(ServerWorld world, LivingEntity user) {
		return user instanceof PlayerEntity player ? world.getDamageSources().playerAttack(player) : world.getDamageSources().mobAttack(user);
	}

	/** A player's jab (the attack key with a spear): only at a full attack charge. */
	public static void jab(ServerPlayerEntity player) {
		ItemStack stack = player.getMainHandStack();
		if (!(stack.getItem() instanceof Spears.Spear) || !player.isAlive() || player.isSpectator() || player.getAttackCooldownProgress(0.5F) < 0.9F) {
			return;
		}
		ServerWorld world = player.getServerWorld();
		float base = (float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_DAMAGE);
		boolean struck = false;
		for (Entity target : reached(player, false)) {
			DamageSource source = source(world, player);
			if (!target.damage(source, EnchantmentHelper.getDamage(world, stack, target, source, base))) {
				continue;
			}
			struck = true;
			float knockback = EnchantmentHelper.modifyKnockback(world, stack, target, source,
					(float) player.getAttributeValue(EntityAttributes.GENERIC_ATTACK_KNOCKBACK));
			if (knockback > 0.0F && target instanceof LivingEntity living) {
				living.takeKnockback(knockback * 0.5F, MathHelper.sin(player.getYaw() * MathHelper.RADIANS_PER_DEGREE),
						-MathHelper.cos(player.getYaw() * MathHelper.RADIANS_PER_DEGREE));
			}
			EnchantmentHelper.onTargetDamaged(world, target, source);
			if (target instanceof LivingEntity living) {
				player.onAttacking(living);
			}
		}
		if (struck) {
			stack.damage(1, player, EquipmentSlot.MAINHAND);
			player.addExhaustion(0.1F);
		}
		world.playSound(null, player.getX(), player.getY(), player.getZ(), struck ? SoundEvents.ITEM_TRIDENT_HIT : SoundEvents.ITEM_TRIDENT_THROW.value(),
				SoundCategory.PLAYERS, struck ? 0.8F : 0.5F, 1.3F + world.random.nextFloat() * 0.2F);
		player.resetLastAttackedTicks();
	}

	/** The spear is lowered for a charge. */
	public static void lowered(LivingEntity user) {
		user.getWorld().playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_ARMOR_EQUIP_IRON.value(), user.getSoundCategory(), 0.6F,
				0.8F);
	}

	/**
	 * A tick of a charge, the given number of ticks after the spear was lowered: once level, it strikes what it meets
	 * closing fast enough, by the closing speed.
	 */
	public static void charge(LivingEntity user, ItemStack stack, int ticks) {
		if (!(stack.getItem() instanceof Spears.Spear spear) || !(user.getWorld() instanceof ServerWorld world)) {
			return;
		}
		Spears.Tier tier = spear.tier();
		int level = ticks - tier.delay();
		if (level < 0 || level >= tier.damageTicks()) {
			return;
		}
		Vec3d look = user.getRotationVec(1.0F);
		Vec3d moving = velocity(user);
		Int2IntMap struck = STRUCK.computeIfAbsent(user, entity -> new Int2IntOpenHashMap());
		for (Entity entity : reached(user, true)) {
			LivingEntity target = (LivingEntity) entity;
			if (struck.containsKey(target.getId()) && user.age - struck.get(target.getId()) < REHIT_TICKS) {
				continue;
			}
			double closing = moving.subtract(velocity(target)).dotProduct(look) * 20.0;
			if (closing < DAMAGE_SPEED) {
				continue;
			}
			struck.put(target.getId(), user.age);
			DamageSource source = source(world, user);
			float damage = EnchantmentHelper.getDamage(world, stack, target, source, Math.max(1.0F, (float) (tier.multiplier() * closing)));
			if (!target.damage(source, damage)) {
				continue;
			}
			if (level < tier.knockbackTicks() && closing >= KNOCKBACK_SPEED) {
				target.takeKnockback(0.4 + closing * 0.04, -look.x, -look.z);
			}
			if (level < tier.dismountTicks() && closing >= tier.dismountSpeed() && target.hasVehicle()) {
				target.stopRiding();
			}
			EnchantmentHelper.onTargetDamaged(world, target, source);
			user.onAttacking(target);
			EquipmentSlot slot = user.isUsingItem() && user.getActiveItem() == stack ? LivingEntity.getSlotForHand(user.getActiveHand()) : EquipmentSlot.MAINHAND;
			stack.damage(1, user, slot);
			world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ITEM_TRIDENT_HIT, user.getSoundCategory(), 1.0F, 0.9F);
		}
	}

	/** A mob lowers its spear and runs at its target. */
	public static void startMobCharge(MobEntity mob) {
		lowered(mob);
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null && speed.getBaseValue() < MOB_CHARGE_SPEED && !speed.hasModifier(MOB_CHARGE)) {
			speed.addTemporaryModifier(new EntityAttributeModifier(MOB_CHARGE, MOB_CHARGE_SPEED / speed.getBaseValue() - 1.0,
					EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		}
	}

	public static void endMobCharge(MobEntity mob) {
		EntityAttributeInstance speed = mob.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
		if (speed != null) {
			speed.removeModifier(MOB_CHARGE);
		}
		STRUCK.remove(mob);
	}

	/** Whether this mob holds a spear in its main hand. */
	public static boolean armed(LivingEntity mob) {
		return mob.getStackInHand(Hand.MAIN_HAND).getItem() instanceof Spears.Spear;
	}
}
