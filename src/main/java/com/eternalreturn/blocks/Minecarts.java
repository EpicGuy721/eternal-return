package com.eternalreturn.blocks;

import com.eternalreturn.config.EternalReturnConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minecarts.
 * <ul>
 * <li>Faster without derailing (redstone.minecartSpeedMultiplier, 3): each tick a cart on rails takes that many of
 * vanilla's steps, none longer than vanilla's, so it keeps to curves and slopes and still trips detector and activator
 * rails, at 3 times the speed (MinecartMixin). Off the rails it moves as in vanilla. Players get a cart's position
 * every tick, smoothed over less time, so a fast cart doesn't stutter.</li>
 * <li>Chains (redstone.minecartChains): right-click a minecart with a chain, then another within 4 blocks: the second
 * is chained behind the first (a chain is used up). The chain is rigid, a coupling rather than a rope: it holds the
 * two 1.6 blocks apart, centre to centre, pulling or pushing either one, so they move as one. A cart pulls at most
 * one other. Shears cut a cart's chains, breaking a cart drops its chains, and a chain forced more than 8 blocks
 * long breaks.</li>
 * </ul>
 */
public final class Minecarts {
	public static final double SPACING = 1.6;
	public static final double BREAK = 8.0;
	public static final double REACH = 4.0;
	/** How much of a gap that is off the chain's length is made up over one tick's steps (1: all of it). */
	private static final double HOLD = 0.75;
	/** Per player: the cart they clicked first with a chain. */
	private static final Map<UUID, UUID> FIRST = new HashMap<>();
	private static Set<EntityType<?>> types;

	/** What a minecart keeps: the cart it is chained behind, and that cart's entity id for the client. */
	public interface Linked {
		@Nullable
		UUID eternalreturn$leader();

		void eternalreturn$setLeader(@Nullable UUID leader);

		int eternalreturn$leaderId();

		void eternalreturn$setLeaderId(int id);
	}

	private Minecarts() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register(Minecarts::use);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
			if (entity instanceof AbstractMinecartEntity cart) {
				removed(cart, world);
			}
		});
	}

	/** Vanilla steps a cart on rails takes each tick. */
	public static int steps() {
		return Math.max(1, (int) Math.round(EternalReturnConfig.get().redstone.minecartSpeedMultiplier));
	}

	public static boolean chains() {
		return EternalReturnConfig.get().redstone.minecartChains;
	}

	public static boolean isMinecart(EntityType<?> type) {
		if (types == null) {
			types = Set.of(EntityType.MINECART, EntityType.CHEST_MINECART, EntityType.FURNACE_MINECART, EntityType.HOPPER_MINECART,
					EntityType.TNT_MINECART, EntityType.SPAWNER_MINECART, EntityType.COMMAND_BLOCK_MINECART);
		}
		return types.contains(type);
	}

	/** The rail a cart runs on, found as vanilla's tick finds it. */
	public static BlockPos railPos(Entity cart) {
		BlockPos pos = BlockPos.ofFloored(cart.getX(), cart.getY(), cart.getZ());
		return cart.getWorld().getBlockState(pos.down()).isIn(BlockTags.RAILS) ? pos.down() : pos;
	}

	public static ActionResult use(PlayerEntity player, World world, Hand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (hit != null || !(entity instanceof AbstractMinecartEntity cart) || player.isSpectator() || !chains()) {
			return ActionResult.PASS;
		}
		ItemStack stack = player.getStackInHand(hand);
		if (stack.isOf(Items.CHAIN)) {
			if (world instanceof ServerWorld server) {
				link(player, cart, stack, server);
			}
			return ActionResult.SUCCESS;
		}
		if (stack.isOf(Items.SHEARS) && chained(cart)) {
			if (world instanceof ServerWorld server) {
				cut(cart, server, true);
				world.playSound(null, cart.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.NEUTRAL, 1.0F, 1.0F);
				player.sendMessage(Text.translatable("message.eternalreturn.minecart.cut"), true);
			}
			return ActionResult.SUCCESS;
		}
		return ActionResult.PASS;
	}

	private static void link(PlayerEntity player, AbstractMinecartEntity cart, ItemStack stack, ServerWorld world) {
		UUID first = FIRST.remove(player.getUuid());
		Entity found = first == null ? null : world.getEntity(first);
		if (found == cart) {
			player.sendMessage(Text.translatable("message.eternalreturn.minecart.cancelled"), true);
			return;
		}
		if (!(found instanceof AbstractMinecartEntity leader) || leader.isRemoved()) {
			FIRST.put(player.getUuid(), cart.getUuid());
			player.sendMessage(Text.translatable("message.eternalreturn.minecart.first"), true);
			return;
		}
		if (leader.distanceTo(cart) > REACH) {
			player.sendMessage(Text.translatable("message.eternalreturn.minecart.too_far"), true);
			return;
		}
		if (((Linked) cart).eternalreturn$leader() != null || !followers(leader, world).isEmpty() || leadsTo(leader, cart, world)) {
			player.sendMessage(Text.translatable("message.eternalreturn.minecart.taken"), true);
			return;
		}
		((Linked) cart).eternalreturn$setLeader(leader.getUuid());
		((Linked) cart).eternalreturn$setLeaderId(leader.getId());
		stack.decrementUnlessCreative(1, player);
		world.playSound(null, cart.getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
		player.sendMessage(Text.translatable("message.eternalreturn.minecart.chained"), true);
	}

	/** Whether following leader's own chain forward reaches cart (chaining cart behind leader would close a loop). */
	private static boolean leadsTo(AbstractMinecartEntity leader, AbstractMinecartEntity cart, ServerWorld world) {
		Entity at = leader;
		for (int i = 0; i < 64 && at instanceof Linked linked; i++) {
			UUID next = linked.eternalreturn$leader();
			if (next == null) {
				return false;
			}
			if (next.equals(cart.getUuid())) {
				return true;
			}
			at = world.getEntity(next);
		}
		return false;
	}

	/** The carts chained behind this one (on the client, by the ids it was sent). */
	public static List<AbstractMinecartEntity> followers(AbstractMinecartEntity cart, World world) {
		Box box = cart.getBoundingBox().expand(BREAK + 2.0);
		return world.getEntitiesByClass(AbstractMinecartEntity.class, box, other -> other != cart && (world.isClient
				? ((Linked) other).eternalreturn$leaderId() == cart.getId()
				: cart.getUuid().equals(((Linked) other).eternalreturn$leader())));
	}

	private static boolean chained(AbstractMinecartEntity cart) {
		Linked linked = (Linked) cart;
		boolean leads = cart.getWorld().isClient ? linked.eternalreturn$leaderId() >= 0 : linked.eternalreturn$leader() != null;
		return leads || !followers(cart, cart.getWorld()).isEmpty();
	}

	/** Takes off a cart's chains, front and back; with drop, each comes off as a chain item. */
	private static void cut(AbstractMinecartEntity cart, ServerWorld world, boolean drop) {
		if (((Linked) cart).eternalreturn$leader() != null) {
			unlink(cart, world, drop);
		}
		for (AbstractMinecartEntity follower : followers(cart, world)) {
			unlink(follower, world, drop);
		}
	}

	private static void unlink(AbstractMinecartEntity cart, ServerWorld world, boolean drop) {
		((Linked) cart).eternalreturn$setLeader(null);
		((Linked) cart).eternalreturn$setLeaderId(-1);
		if (drop && world.getGameRules().getBoolean(GameRules.DO_ENTITY_DROPS)) {
			ItemEntity item = new ItemEntity(world, cart.getX(), cart.getY() + 0.5, cart.getZ(), new ItemStack(Items.CHAIN));
			item.setToDefaultPickupDelay();
			world.spawnEntity(item);
		}
	}

	/** A cart broken (its chains drop) or removed some other way (they go with it); not one only unloaded. */
	private static void removed(AbstractMinecartEntity cart, ServerWorld world) {
		Entity.RemovalReason reason = cart.getRemovalReason();
		if (reason != null && reason.shouldDestroy()) {
			cut(cart, world, reason == Entity.RemovalReason.KILLED);
		}
	}

	/**
	 * Each tick, from a chained cart: the two carts get the same speed along the chain, plus what makes up over about
	 * a tick for the gap being longer or shorter than the chain, shared so that what one gains the other loses.
	 */
	public static void follow(AbstractMinecartEntity cart) {
		Linked linked = (Linked) cart;
		UUID id = linked.eternalreturn$leader();
		if (id == null || !(cart.getWorld() instanceof ServerWorld world)) {
			return;
		}
		if (!(world.getEntity(id) instanceof AbstractMinecartEntity leader) || leader.isRemoved()) {
			// Not loaded (or gone): nothing to pull for now.
			linked.eternalreturn$setLeaderId(-1);
			return;
		}
		if (linked.eternalreturn$leaderId() != leader.getId()) {
			linked.eternalreturn$setLeaderId(leader.getId());
		}
		Vec3d gap = leader.getPos().subtract(cart.getPos());
		double length = gap.length();
		if (length > BREAK) {
			unlink(cart, world, true);
			world.playSound(null, cart.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.NEUTRAL, 1.0F, 1.0F);
			return;
		}
		if (gap.horizontalLengthSquared() < 1.0E-6) {
			return;
		}
		Vec3d dir = new Vec3d(gap.x, 0.0, gap.z).normalize();
		double separating = leader.getVelocity().dotProduct(dir) - cart.getVelocity().dotProduct(dir);
		// Velocities are per step, and a cart takes steps() of them a tick.
		double wanted = -(length - SPACING) * HOLD / steps();
		double change = separating - wanted;
		if (Math.abs(change) < 1.0E-4) {
			return;
		}
		push(cart, dir, change / 2.0);
		push(leader, dir, -change / 2.0);
	}

	/** Adds a pull to a cart's speed, only along its rail if it is on one (a sideways pull would just speed it up). */
	private static void push(AbstractMinecartEntity cart, Vec3d dir, double amount) {
		Vec3d axis = railAxis(cart);
		Vec3d add = axis == null ? dir.multiply(amount) : axis.multiply(amount * dir.dotProduct(axis));
		cart.setVelocity(cart.getVelocity().add(add));
		cart.velocityDirty = true;
	}

	@Nullable
	private static Vec3d railAxis(AbstractMinecartEntity cart) {
		BlockState state = cart.getWorld().getBlockState(railPos(cart));
		if (!(state.getBlock() instanceof AbstractRailBlock rail)) {
			return null;
		}
		RailShape shape = state.get(rail.getShapeProperty());
		double s = MathHelper.SQUARE_ROOT_OF_TWO / 2.0;
		return switch (shape) {
			case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new Vec3d(0.0, 0.0, 1.0);
			case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new Vec3d(1.0, 0.0, 0.0);
			case SOUTH_EAST, NORTH_WEST -> new Vec3d(s, 0.0, -s);
			case SOUTH_WEST, NORTH_EAST -> new Vec3d(s, 0.0, s);
		};
	}
}
