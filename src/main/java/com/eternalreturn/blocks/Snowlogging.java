package com.eternalreturn.blocks;

import com.eternalreturn.EternalReturn;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SnowBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import net.minecraft.world.WorldView;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/**
 * Snowlogged snow (SnowCover): kept per chunk beside the blocks it's logged in (position to layers), saved with the
 * chunk and sent to the players' games with it and as it changes (Payload: their games keep it in client ClientSnow and
 * draw it), and the snow cover block. Here too the ways snow gets on these blocks: by hand, falling, in snowfall and as
 * the world is made, and its melting.
 */
public final class Snowlogging {
	public static final Block SNOW_COVER = Registry.register(Registries.BLOCK, EternalReturn.id("snow_cover"),
			new SnowCoverBlock(AbstractBlock.Settings.copy(Blocks.SNOW).dynamicBounds().blockVision((state, world, pos) -> false)));
	public static final AttachmentType<Map<Long, Integer>> LOGGED = AttachmentRegistry.<Map<Long, Integer>>builder()
			.persistent(Entry.CODEC.listOf().xmap(Snowlogging::toMap, Snowlogging::toList))
			.buildAndRegister(EternalReturn.id("snowlogged"));
	/** The snow logged at a position in the player's own game; set by the client. */
	public static ToIntFunction<BlockPos> clientLayers = pos -> 0;
	/** Sets the snow logged at a position in the player's own game (its guess, before the server says); set by the client. */
	public static BiConsumer<BlockPos, Integer> clientSet = (pos, layers) -> {
	};

	/** Snow logged at a position (packed), as saved and sent. */
	public record Entry(long pos, int layers) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				Codec.LONG.fieldOf("pos").forGetter(Entry::pos),
				Codec.INT.fieldOf("layers").forGetter(Entry::layers)).apply(instance, Entry::new));
		static final PacketCodec<ByteBuf, Entry> PACKET_CODEC = PacketCodec.tuple(PacketCodecs.VAR_LONG, Entry::pos, PacketCodecs.VAR_INT, Entry::layers, Entry::new);
	}

	/** Snow logged at positions (0 layers: none any more), for a player's game. */
	public record Payload(List<Entry> entries) implements CustomPayload {
		public static final CustomPayload.Id<Payload> ID = new CustomPayload.Id<>(EternalReturn.id("snowlogged"));
		public static final PacketCodec<RegistryByteBuf, Payload> CODEC = Entry.PACKET_CODEC.collect(PacketCodecs.toList()).xmap(Payload::new, Payload::entries).cast();

		@Override
		public Id<? extends CustomPayload> getId() {
			return ID;
		}
	}

	private Snowlogging() {
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(Payload.ID, Payload.CODEC);
	}

	private static Map<Long, Integer> toMap(List<Entry> entries) {
		Map<Long, Integer> map = new HashMap<>();
		for (Entry entry : entries) {
			if (entry.layers() > 0) {
				map.put(entry.pos(), entry.layers());
			}
		}
		return Map.copyOf(map);
	}

	private static List<Entry> toList(Map<Long, Integer> map) {
		List<Entry> entries = new ArrayList<>();
		map.forEach((pos, layers) -> entries.add(new Entry(pos, layers)));
		return entries;
	}

	@Nullable
	private static Chunk chunk(BlockView world, BlockPos pos) {
		try {
			if (world instanceof ServerWorld server) {
				// (Off the server thread, as lighting asks, none: waiting for the server there can lock it.)
				return server.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
			}
			if (world instanceof World w) {
				return w.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.FULL, false);
			}
			if (world instanceof WorldView view) {
				// A world being made.
				return view.getChunk(pos.getX() >> 4, pos.getZ() >> 4, ChunkStatus.EMPTY, false);
			}
		} catch (RuntimeException outside) {
			// A region of a world being made asked outside itself.
		}
		return null;
	}

	/** The layers of snow logged at a position (whatever block is there). */
	public static int layers(BlockView world, BlockPos pos) {
		if (world instanceof World w && w.isClient) {
			return clientLayers.applyAsInt(pos);
		}
		Chunk chunk = chunk(world, pos);
		Map<Long, Integer> logged = chunk == null ? null : chunk.getAttached(LOGGED);
		return logged == null ? 0 : logged.getOrDefault(pos.asLong(), 0);
	}

	/** Sets the layers of snow logged at a position (0: none), saved with the chunk and told to the players near; the block is left as it is. */
	public static void setLayers(WorldAccess world, BlockPos pos, int layers) {
		if (world.isClient()) {
			clientSet.accept(pos.toImmutable(), layers);
			return;
		}
		Chunk chunk = chunk(world, pos);
		if (chunk == null) {
			return;
		}
		Map<Long, Integer> logged = chunk.getAttachedOrElse(LOGGED, Map.of());
		long key = pos.asLong();
		if (logged.getOrDefault(key, 0) == layers) {
			return;
		}
		Map<Long, Integer> next = new HashMap<>(logged);
		if (layers > 0) {
			next.put(key, layers);
		} else {
			next.remove(key);
		}
		chunk.setAttached(LOGGED, next.isEmpty() ? null : Map.copyOf(next));
		chunk.setNeedsSaving(true);
		if (world instanceof ServerWorld server) {
			Payload payload = new Payload(List.of(new Entry(key, layers)));
			for (ServerPlayerEntity player : PlayerLookup.tracking(server, new ChunkPos(pos))) {
				if (ServerPlayNetworking.canSend(player, Payload.ID)) {
					ServerPlayNetworking.send(player, payload);
				}
			}
		}
	}

	/** A chunk sent to a player: the snow logged in it goes with it. */
	public static void chunkSent(ServerPlayNetworkHandler handler, WorldChunk chunk) {
		Map<Long, Integer> logged = chunk.getAttached(LOGGED);
		if (logged != null && !logged.isEmpty() && ServerPlayNetworking.canSend(handler.player, Payload.ID)) {
			ServerPlayNetworking.send(handler.player, new Payload(toList(logged)));
		}
	}

	/** A block changed in a loaded chunk: the snow logged there stays as far as the new block holds it, else goes. */
	public static void blockChanged(WorldChunk chunk, BlockPos pos, BlockState old, BlockState now) {
		if (!SnowCover.marked(old)) {
			return;
		}
		World world = chunk.getWorld();
		int layers = layers(world, pos);
		if (layers == 0) {
			return;
		}
		int kept = SnowCover.marked(now) ? Math.min(layers, SnowCover.capacity(now)) : 0;
		if (kept != layers) {
			setLayers(world, pos, kept);
		}
	}

	/** Logs layers of snow in a block (0 takes it out, more than it takes are left out) and marks the block as its snow says. Returns the layers logged. */
	public static int snowlog(WorldAccess world, BlockPos pos, int layers, int flags) {
		BlockState state = world.getBlockState(pos);
		int logged = Math.max(0, Math.min(layers, SnowCover.capacity(state)));
		int before = layers(world, pos);
		setLayers(world, pos, logged);
		BlockState marked = SnowCover.markedFor(state, logged, world.getBlockState(pos.up()));
		if (marked != state) {
			world.setBlockState(pos, marked, flags);
		} else if (before != logged && (flags & Block.NOTIFY_NEIGHBORS) != 0) {
			// The block is as it was, but what lies on it may change (a cover turning into a snow layer on a whole top).
			world.replaceWithStateForNeighborUpdate(Direction.DOWN, marked, pos.up(), pos, flags, 512);
		}
		return logged;
	}

	/** A block put in its space or changed in it: its snowy mark as the snow logged in it (as much as it still holds) and lying on it say. */
	public static BlockState settle(BlockState state, BlockView world, BlockPos pos) {
		if (!SnowCover.isHost(state)) {
			return state;
		}
		return SnowCover.markedFor(state, Math.min(layers(world, pos), SnowCover.capacity(state)), world.getBlockState(pos.up()));
	}

	/** Snow layers, measured from the ground, as the snow logged in a block put into them: what's above its open floor. */
	public static int layersOver(BlockState state, int groundLayers) {
		return Math.min(SnowCover.capacity(state), Math.max(0, groundLayers - (16 - 2 * SnowCover.capacity(state)) / 2));
	}

	/** Whether the light melts snow here, as it melts snow layers: block light above 11. */
	public static boolean meltsHere(WorldView world, BlockPos pos) {
		return world.getLightLevel(LightType.BLOCK, pos) > 11;
	}

	/** A random tick of a snowy block: its logged snow melts if the light melts it. Returns whether it did. */
	public static boolean melt(BlockState state, ServerWorld world, BlockPos pos) {
		if (SnowCover.layers(world, pos, state) == 0 || !meltsHere(world, pos)) {
			return false;
		}
		snowlog(world, pos, 0, Block.NOTIFY_ALL);
		return true;
	}

	/**
	 * Snow settling where a snow layer would (an empty space or a snow cover, cold and dark enough, in the world) but
	 * can't, on the block below: a layer more logged in it, and on the parts of it reaching the top a layer more of snow
	 * cover, each up to most (the snow-piling game rule). Returns whether any settled.
	 */
	public static boolean snowfall(WorldAccess world, Biome biome, BlockPos space, int most, int flags) {
		if (biome.doesNotSnow(space) || space.getY() < world.getBottomY() || space.getY() >= world.getTopY()
				|| world.getLightLevel(LightType.BLOCK, space) >= 10) {
			return false;
		}
		BlockState there = world.getBlockState(space);
		BlockPos pos = space.down();
		BlockState state = world.getBlockState(pos);
		if (!SnowCover.isHost(state) || !there.isAir() && !SnowCover.isCover(there)) {
			return false;
		}
		boolean settled = false;
		int layers = SnowCover.layers(world, pos, state);
		if (layers < Math.min(most, SnowCover.capacity(state))) {
			snowlog(world, pos, layers + 1, flags);
			layers++;
			settled = true;
		}
		state = world.getBlockState(pos);
		if (SnowCover.reachesTop(state) && !SnowCover.whole(state, layers)) {
			int cover = there.isAir() ? 0 : there.get(SnowBlock.LAYERS);
			if (cover < Math.min(most, SnowCover.MAX_LAYERS)) {
				world.setBlockState(space, SNOW_COVER.getDefaultState().with(SnowBlock.LAYERS, cover + 1), flags);
				settled = true;
			}
		}
		return settled;
	}

	/** Snow lying on a block's whole top: the block says so (for its drips). */
	public static void snowLiesOn(WorldAccess world, BlockPos space, int flags) {
		BlockPos pos = space.down();
		BlockState state = world.getBlockState(pos);
		if (SnowCover.isHost(state) && !SnowCover.marked(state)) {
			world.setBlockState(pos, SnowCover.mark(state, true), flags);
		}
	}

	/**
	 * A snow layer item used on a block: on the top of a block that holds snow, inside its space, a layer more logged in
	 * it; on its top, at the top of its space where it reaches it, a layer more of snow cover (a whole top takes snow
	 * layers, as vanilla's); aimed at a space with such a block in it (the ground beside a fence), a layer more logged in
	 * it. Returns whether it took it.
	 */
	public static boolean useSnow(ItemUsageContext context) {
		ItemStack stack = context.getStack();
		World world = context.getWorld();
		PlayerEntity player = context.getPlayer();
		if (!stack.isOf(Items.SNOW) || player != null && !player.canModifyBlocks()) {
			return false;
		}
		BlockPos clicked = context.getBlockPos();
		BlockState state = world.getBlockState(clicked);
		if (SnowCover.isHost(state) && context.getSide() == Direction.UP) {
			int layers = SnowCover.layers(world, clicked, state);
			if (context.getHitPos().y - clicked.getY() < 1.0 - 1.0E-4) {
				return layers < SnowCover.capacity(state) && placed(world, clicked, player, stack, () -> snowlog(world, clicked, layers + 1, Block.NOTIFY_ALL_AND_REDRAW));
			}
			if (SnowCover.whole(state, layers) || !SnowCover.reachesTop(state)) {
				return false;
			}
			BlockPos space = clicked.up();
			BlockState above = world.getBlockState(space);
			int cover = SnowCover.isCover(above) ? above.get(SnowBlock.LAYERS) : above.isAir() ? 0 : -1;
			if (cover < 0 || cover >= SnowCover.MAX_LAYERS) {
				return false;
			}
			return placed(world, space, player, stack, () -> world.setBlockState(space, SNOW_COVER.getDefaultState().with(SnowBlock.LAYERS, cover + 1), Block.NOTIFY_ALL_AND_REDRAW));
		}
		BlockPos target = new ItemPlacementContext(context).getBlockPos();
		BlockState into = world.getBlockState(target);
		if (!SnowCover.isHost(into)) {
			return false;
		}
		int layers = SnowCover.layers(world, target, into);
		return layers < SnowCover.capacity(into) && placed(world, target, player, stack, () -> snowlog(world, target, layers + 1, Block.NOTIFY_ALL_AND_REDRAW));
	}

	private static boolean placed(World world, BlockPos pos, @Nullable PlayerEntity player, ItemStack stack, Runnable place) {
		if (player != null && !world.canPlayerModifyAt(player, pos)) {
			return false;
		}
		place.run();
		BlockSoundGroup sounds = BlockSoundGroup.SNOW;
		world.playSound(player, pos, sounds.getPlaceSound(), SoundCategory.BLOCKS, (sounds.getVolume() + 1.0F) / 2.0F, sounds.getPitch() * 0.8F);
		world.emitGameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Emitter.of(player, world.getBlockState(pos)));
		stack.decrementUnlessCreative(1, player);
		return true;
	}

	/** A gate set open with snow on its rails: the snow falls off them, in a puff of snow (and lies on the ground). */
	public static void gateOpened(World world, BlockPos pos, BlockState state, @Nullable PlayerEntity player) {
		if (state.contains(net.minecraft.state.property.Properties.OPEN) && state.get(net.minecraft.state.property.Properties.OPEN)
				&& SnowCover.layers(world, pos, world.getBlockState(pos)) > 0) {
			world.syncWorldEvent(player, WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(Blocks.SNOW.getDefaultState()));
		}
	}

	/** A block broken from under its logged snow: the snow stays, as a snow layer of its own (which falls, if snow falls). */
	public static void leftBehind(World world, BlockPos pos, int layers) {
		BlockState snow = SnowCover.asSnowLayer(layers);
		if (layers > 0 && world.getBlockState(pos).isAir() && (snow.canPlaceAt(world, pos) || com.eternalreturn.config.EternalReturnConfig.get().survival.snowFalls)) {
			world.setBlockState(pos, snow, Block.NOTIFY_ALL);
		}
	}
}
