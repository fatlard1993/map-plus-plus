package justfatlard.map_plus_plus;

import justfatlard.map_plus_plus.inventory.MapPlusPlusInventory;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

/**
 * Block Magnet: a compass that, used on a block, points to the nearest other block of that kind.
 *
 * <p>Each level lets it feel for one more kind at once, up to three. Used on a kind it is not
 * feeling for, it takes that kind up too, letting go of the one it took up first when it has no
 * room; used on the same kind three times running, it lets go of all the others and feels for
 * that one alone. The needle points at the nearest of any of them; the radar and the minimap show
 * each kind in its own colour, and the compass lists them in those colours.
 *
 * <p>It points the way a lodestone compass does, with the same component, so the needle in a
 * player's hand is vanilla's and needs nothing on the client, and in the compass slot the
 * minimap and the needle beside it follow it too. The block it was used on does not count, nor
 * the blocks of its kind touching it: used on one ore of a vein it points to the next vein, not to
 * the ore beside the one in front of you.
 *
 * <p>It looks again every second while it is in a hand or the compass slot, so it keeps up as
 * the holder moves and as what it pointed at is dug out. It looks only in chunks already loaded,
 * within {@link #REACH} blocks. Finding nothing, it spins, as a compass does with nothing to point
 * at. Used in the air while sneaking, it forgets, and is a compass again.
 *
 * <p>In the compass slot it also keeps the nearest {@link #MOST_SEEN} it found, for the radar and
 * the minimap to show them all: see {@link #seenBy}.
 */
public final class BlockMagnet {
	private BlockMagnet() {}

	/** How far it feels for its block, in blocks. */
	static final int REACH = 32;
	/** A vein bigger than this is ground, not a vein, and only the block used on is left out. */
	private static final int MOST_VEIN = 32;
	private static final int EVERY_TICKS = 20;
	/** Most blocks of each kind it keeps for the radar and the minimap, nearest first. */
	static final int MOST_SEEN = 64;
	/** Uses on the same kind, one after another, that make it feel for that kind alone. */
	private static final int ALONE_AFTER = 3;
	/** Each kind's colour, by the order it was taken up: on the radar, the minimap and the compass's own list. */
	private static final int[] COLOURS = {0xFFE040FB, 0xFF18FFFF, 0xFFFFFFFF};
	private static final String KEY = Main.MOD_ID + ":block_magnet";

	/** One kind of block a magnet compass feels for, and the blocks of it that it leaves out. */
	private record Kind(Block block, Set<BlockPos> except) {}

	/** Everything a magnet compass feels for, first taken up first, and the kind it was used on last and how many times running. */
	private record Seeking(List<Kind> kinds, Block last, int running) {}

	/** A block a magnet felt, and which of the kinds it feels for it is. */
	public record Sighting(BlockPos pos, int kind) {}

	/** What the magnet in each player's compass slot found last, nearest first. */
	private static final Map<UUID, List<Sighting>> seen = new HashMap<>();

	/** The colour a kind is shown in, by its place in the order the magnet took them up. */
	public static int colourOf(int kind) {
		return COLOURS[Math.floorMod(kind, COLOURS.length)];
	}

	/**
	 * The compass, used on a block, takes its kind up alongside what it already feels for, as
	 * many kinds as its level allows; used on the same kind {@link #ALONE_AFTER} times running,
	 * it feels for that kind alone.
	 */
	public static void takeUp(ServerPlayer holder, ItemStack compass, BlockPos clicked) {
		ServerLevel level = holder.level();
		Block block = level.getBlockState(clicked).getBlock();
		Seeking was = seeking(compass).orElse(new Seeking(List.of(), null, 0));
		int running = block == was.last() ? was.running() + 1 : 1;
		// The vein in front of you now is the one to leave out, not one used on before
		Kind kind = new Kind(block, vein(level, clicked, block));
		List<Kind> kinds = new ArrayList<>(was.kinds());
		int already = indexOf(kinds, block);
		boolean alone = false;
		if (already >= 0) {
			kinds.set(already, kind);
			if (running >= ALONE_AFTER && kinds.size() > 1) {
				kinds = new ArrayList<>(List.of(kind));
				alone = true;
			}
		} else {
			kinds.add(kind);
			while (kinds.size() > Math.max(1, levelOf(holder, compass))) kinds.remove(0);
		}
		Seeking now = new Seeking(kinds, block, alone ? 0 : running);
		remember(compass, now);
		Optional<BlockPos> found = aim(holder, compass, now).stream().findFirst().map(Sighting::pos);
		level.playSound(null, clicked, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, alone ? 0.8F : 1.0F);

		Component seekingNow = names(kinds);
		Component message = found
			.map(at -> Component.translatableWithFallback("message.map-plus-plus.block_magnet.found",
				"Seeking %s: the nearest is %s blocks away", seekingNow,
				Math.round(Math.sqrt(at.distSqr(holder.blockPosition())))))
			.orElseGet(() -> Component.translatableWithFallback("message.map-plus-plus.block_magnet.none",
				"Seeking %s: none within %s blocks", seekingNow, REACH));
		if (alone) {
			message = Component.translatableWithFallback("message.map-plus-plus.block_magnet.alone",
				"Now seeking only %s", block.getName());
		} else if (running == ALONE_AFTER - 1 && kinds.size() > 1) {
			message = Component.translatableWithFallback("message.map-plus-plus.block_magnet.once_more",
				"%s (once more to seek only this)", message);
		}
		holder.sendOverlayMessage(message);
	}

	private static int indexOf(List<Kind> kinds, Block block) {
		for (int i = 0; i < kinds.size(); i++) if (kinds.get(i).block() == block) return i;
		return -1;
	}

	/** The kinds' names, each in its own colour: the same colours they have on the radar. */
	private static Component names(List<Kind> kinds) {
		var out = Component.empty();
		for (int i = 0; i < kinds.size(); i++) {
			if (i > 0) out.append(", ");
			int colour = colourOf(i);
			out.append(kinds.get(i).block().getName().withStyle(style -> style.withColor(colour & 0xFFFFFF)));
		}
		return out;
	}

	static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() || !player.isSecondaryUseActive() || !(player instanceof ServerPlayer holder)) return InteractionResult.PASS;
			ItemStack compass = player.getItemInHand(hand);
			if (!isMagnet(holder, compass) || seeking(compass).isEmpty()) return InteractionResult.PASS;
			forget(compass);
			holder.sendOverlayMessage(Component.translatableWithFallback("message.map-plus-plus.block_magnet.forgot",
				"The compass lets go"));
			return InteractionResult.SUCCESS;
		});

		ServerTickEvents.END_SERVER_TICK.register(BlockMagnet::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> seen.remove(handler.getPlayer().getUUID()));
	}

	/** The blocks the magnet in a player's compass slot feels, nearest first; empty if it is not one. */
	public static List<Sighting> seenBy(ServerPlayer player) {
		return seen.getOrDefault(player.getUUID(), List.of());
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY_TICKS != 0) return;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.isSpectator()) continue;
			for (InteractionHand hand : InteractionHand.values()) {
				ItemStack compass = player.getItemInHand(hand);
				seeking(compass).ifPresent(seeking -> aim(player, compass, seeking));
			}
			// The slot is kept in two places; the copy the minimap reads is changed through the one the screen shows
			MapPlusPlusInventory slots = ((MapPlusPlusPlayerAccess) player).mapPlusPlus$getInventory();
			ItemStack slotted = slots.getCompassStack();
			Optional<Seeking> seeking = isMagnet(player, slotted) ? seeking(slotted) : Optional.empty();
			if (seeking.isEmpty()) {
				seen.remove(player.getUUID());
				continue;
			}
			ItemStack aimed = slotted.copy();
			seen.put(player.getUUID(), aim(player, aimed, seeking.get()));
			if (!ItemStack.isSameItemSameComponents(aimed, slotted)) {
				PandoricalApi.playerInventory().setSlot(player, Main.SLOTS_NAMESPACE, MapPlusPlusInventory.COMPASS_SLOT, aimed);
			}
		}
	}

	/** A Block Magnet that has been used on something, so has something to feel for. */
	public static boolean seeks(ServerPlayer player, ItemStack stack) {
		return isMagnet(player, stack) && seeking(stack).isPresent();
	}

	/** A compass carrying Block Magnet. */
	public static boolean isMagnet(ServerPlayer player, ItemStack stack) {
		return levelOf(player, stack) > 0;
	}

	/** The compass's Block Magnet level, which is how many kinds it feels for at once; 0 without it. */
	private static int levelOf(ServerPlayer player, ItemStack stack) {
		if (!(stack.getItem() instanceof CompassItem)) return 0;
		ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
		if (enchantments == null) return 0;
		return player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(Main.BLOCK_MAGNET)
			.map(enchantments::getLevel).orElse(0);
	}

	/**
	 * Point the compass at the nearest block of any kind it feels for, or at nothing. Returns
	 * everything it found, nearest first.
	 */
	private static List<Sighting> aim(ServerPlayer player, ItemStack compass, Seeking seeking) {
		ServerLevel level = player.level();
		BlockPos from = player.blockPosition();
		List<Sighting> found = new ArrayList<>();
		for (int k = 0; k < seeking.kinds().size(); k++) {
			Kind kind = seeking.kinds().get(k);
			for (BlockPos at : nearby(level, from, kind.block(), kind.except(), MOST_SEEN)) found.add(new Sighting(at, k));
		}
		found.sort(Comparator.comparingDouble(sighting -> sighting.pos().distSqr(from)));
		// Not tracked: there is no lodestone to go missing, only the place
		LodestoneTracker tracker = new LodestoneTracker(found.stream().findFirst().map(s -> GlobalPos.of(level.dimension(), s.pos())), false);
		if (!tracker.equals(compass.get(DataComponents.LODESTONE_TRACKER))) compass.set(DataComponents.LODESTONE_TRACKER, tracker);
		return found;
	}

	/**
	 * The nearest blocks of a kind within {@link #REACH} of a place, in loaded chunks, at most
	 * {@code most} of them, nearest first. Sections are taken nearest first and the looking stops
	 * once no section left could hold anything nearer than what is kept, and a section whose
	 * palette has never held the block is passed over unread: a rare ore costs little to look for,
	 * and a common block is found in the first few sections read.
	 */
	static List<BlockPos> nearby(ServerLevel level, BlockPos from, Block block, Set<BlockPos> except, int most) {
		List<long[]> sections = new ArrayList<>();
		int minSection = level.getMinSectionY(), maxSection = level.getMaxSectionY();
		for (int sy = SectionPos.blockToSectionCoord(from.getY() - REACH); sy <= SectionPos.blockToSectionCoord(from.getY() + REACH); sy++) {
			if (sy < minSection || sy > maxSection) continue;
			for (int sz = SectionPos.blockToSectionCoord(from.getZ() - REACH); sz <= SectionPos.blockToSectionCoord(from.getZ() + REACH); sz++) {
				for (int sx = SectionPos.blockToSectionCoord(from.getX() - REACH); sx <= SectionPos.blockToSectionCoord(from.getX() + REACH); sx++) {
					sections.add(new long[] {closest(from, sx, sy, sz), sx, sy, sz});
				}
			}
		}
		sections.sort(Comparator.comparingLong(s -> s[0]));

		long reach = (long) REACH * REACH;
		// Furthest kept on top, so it is the one a nearer find puts out
		PriorityQueue<Found> kept = new PriorityQueue<>(Comparator.comparingLong(Found::distance).reversed());
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (long[] s : sections) {
			long bar = kept.size() < most ? reach : kept.peek().distance();
			if (s[0] > bar) break;
			int sx = (int) s[1], sy = (int) s[2], sz = (int) s[3];
			LevelChunk chunk = level.getChunkSource().getChunkNow(sx, sz);
			if (chunk == null) continue;
			LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
			if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(block))) continue;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (!section.getBlockState(x, y, z).is(block)) continue;
						at.set((sx << 4) + x, (sy << 4) + y, (sz << 4) + z);
						long distance = (long) at.distSqr(from);
						if (distance > reach || except.contains(at)) continue;
						if (kept.size() < most) kept.add(new Found(at.immutable(), distance));
						else if (distance < kept.peek().distance()) {
							kept.poll();
							kept.add(new Found(at.immutable(), distance));
						}
					}
				}
			}
		}
		List<Found> nearestFirst = new ArrayList<>(kept);
		nearestFirst.sort(Comparator.comparingLong(Found::distance));
		return nearestFirst.stream().map(Found::pos).toList();
	}

	private record Found(BlockPos pos, long distance) {}

	/** The squared distance from a place to the nearest block of a section. */
	private static long closest(BlockPos from, int sx, int sy, int sz) {
		long dx = gap(from.getX(), sx), dy = gap(from.getY(), sy), dz = gap(from.getZ(), sz);
		return dx * dx + dy * dy + dz * dz;
	}

	private static long gap(int at, int section) {
		int lo = section << 4, hi = lo + 15;
		return at < lo ? lo - at : at > hi ? at - hi : 0;
	}

	/**
	 * The block used on and every block of its kind joined to it, corners included, or just the
	 * one block when that runs past {@link #MOST_VEIN}: a vein of ore, not a hillside of stone.
	 */
	private static Set<BlockPos> vein(ServerLevel level, BlockPos start, Block block) {
		Set<BlockPos> vein = new HashSet<>();
		ArrayDeque<BlockPos> next = new ArrayDeque<>();
		vein.add(start);
		next.add(start);
		while (!next.isEmpty()) {
			BlockPos at = next.poll();
			for (BlockPos beside : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 1, 1))) {
				if (vein.contains(beside) || !level.isLoaded(beside) || !level.getBlockState(beside).is(block)) continue;
				if (vein.size() >= MOST_VEIN) return Set.of(start);
				BlockPos kept = beside.immutable();
				vein.add(kept);
				next.add(kept);
			}
		}
		return vein;
	}

	/**
	 * What the compass is feeling for, if it has been used on anything. A compass set up before
	 * it could feel for more than one kind keeps its one kind at the top level, and still reads.
	 */
	static Optional<Seeking> seeking(ItemStack compass) {
		CustomData data = compass.get(DataComponents.CUSTOM_DATA);
		if (data == null) return Optional.empty();
		return data.copyTag().getCompound(KEY).flatMap(magnet -> {
			List<Kind> kinds = new ArrayList<>();
			magnet.getList("kinds").ifPresent(list -> {
				for (int i = 0; i < list.size(); i++) list.getCompound(i).flatMap(BlockMagnet::kind).ifPresent(kinds::add);
			});
			if (kinds.isEmpty()) kind(magnet).ifPresent(kinds::add);
			if (kinds.isEmpty()) return Optional.empty();
			Block last = magnet.getString("last").flatMap(id -> BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id))).orElse(null);
			return Optional.of(new Seeking(kinds, last, magnet.getIntOr("running", 0)));
		});
	}

	private static Optional<Kind> kind(CompoundTag tag) {
		return tag.getString("block").flatMap(id -> BuiltInRegistries.BLOCK.getOptional(Identifier.parse(id))).map(block -> {
			Set<BlockPos> except = new HashSet<>();
			tag.getLongArray("except").ifPresent(longs -> {
				for (long packed : longs) except.add(BlockPos.of(packed));
			});
			return new Kind(block, except);
		});
	}

	private static void remember(ItemStack compass, Seeking seeking) {
		ListTag kinds = new ListTag();
		List<Component> lines = new ArrayList<>();
		for (int i = 0; i < seeking.kinds().size(); i++) {
			Kind kind = seeking.kinds().get(i);
			CompoundTag tag = new CompoundTag();
			tag.putString("block", BuiltInRegistries.BLOCK.getKey(kind.block()).toString());
			tag.putLongArray("except", kind.except().stream().mapToLong(BlockPos::asLong).toArray());
			kinds.add(tag);
			int colour = colourOf(i);
			lines.add(Component.translatableWithFallback("item.map-plus-plus.block_magnet.seeking", "Seeks %s", kind.block().getName())
				.withStyle(style -> style.withItalic(false).withColor(colour & 0xFFFFFF)));
		}
		CompoundTag magnet = new CompoundTag();
		magnet.put("kinds", kinds);
		if (seeking.last() != null) magnet.putString("last", BuiltInRegistries.BLOCK.getKey(seeking.last()).toString());
		magnet.putInt("running", seeking.running());
		CustomData.update(DataComponents.CUSTOM_DATA, compass, tag -> tag.put(KEY, magnet));
		compass.set(DataComponents.LORE, new ItemLore(lines));
	}

	private static void forget(ItemStack compass) {
		CustomData.update(DataComponents.CUSTOM_DATA, compass, tag -> tag.remove(KEY));
		compass.remove(DataComponents.LORE);
		compass.remove(DataComponents.LODESTONE_TRACKER);
	}
}
