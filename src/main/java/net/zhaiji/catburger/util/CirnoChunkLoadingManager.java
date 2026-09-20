package net.zhaiji.catburger.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.zhaiji.catburger.CatBurger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the chunk a protected Cirno is standing in force-loaded so it never becomes
 * eligible for {@code RemovalReason.UNLOADED_TO_CHUNK} in the first place — mirrors what
 * ANChorCore's real anchor-core bauble does for anchored maids (see its
 * {@code ChunkLoadingManager}), instead of trying to block the removal call after the
 * chunk has already started unloading around her. Blocking that call without actually
 * keeping the chunk loaded leaves her stuck in a frozen, half-removed "ghost" state —
 * still technically resident, but not properly ticking — which only clears itself on a
 * full world reload.
 *
 * <p>One ticket per Cirno at a time, moved as she moves. Deliberately NOT restored
 * across server restarts (see {@link #registerCallback()}) — she's tied to an online
 * owner, and the existing NBT snapshot/respawn system already recovers her cleanly on
 * next login, so there's nothing worth persisting here. That also means this can never
 * turn into a permanently-forced chunk left over from a mod that's since been removed
 * or an item that's since been discarded.
 */
public final class CirnoChunkLoadingManager {
    private CirnoChunkLoadingManager() {
    }

    private record Loaded(ServerLevel level, ChunkPos chunk) {
    }

    private static final Map<UUID, Loaded> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Register once from FMLCommonSetupEvent so Forge doesn't warn about an unhandled
     * ticket owner on world load. Intentionally a no-op: leftover tickets from an
     * unclean shutdown are just left unforced, and protection re-establishes itself
     * the moment the relevant Cirno starts ticking again.
     */
    public static void registerCallback() {
        ForgeChunkManager.setForcedChunkLoadingCallback(CatBurger.MOD_ID, (tickets, level) -> {
            // No re-forcing on load — see class javadoc.
        });
    }

    /**
     * Moves (or creates) the force-load ticket to Cirno's current chunk. Cheap no-op
     * when she hasn't changed chunks/dimensions since the last call, so this is safe
     * to call every tick from {@code CirnoEntity#tick()}.
     */
    public static void update(UUID cirnoId, ServerLevel level, ChunkPos currentChunk) {
        Loaded existing = ACTIVE.get(cirnoId);
        if (existing != null && existing.level() == level && existing.chunk().equals(currentChunk)) {
            return;
        }
        if (existing != null) {
            ForgeChunkManager.forceChunk(existing.level(), CatBurger.MOD_ID, cirnoId,
                    existing.chunk().x, existing.chunk().z, false, true);
        }
        boolean added = ForgeChunkManager.forceChunk(level, CatBurger.MOD_ID, cirnoId,
                currentChunk.x, currentChunk.z, true, true);
        if (added) {
            ACTIVE.put(cirnoId, new Loaded(level, currentChunk));
        } else {
            ACTIVE.remove(cirnoId);
        }
    }

    /**
     * Releases Cirno's held ticket, if any. MUST be called whenever she's legitimately
     * removed (H hide, smart slab / camera storage, death, dimension change) — without
     * this the chunk she was last standing in stays force-loaded forever.
     */
    public static void release(UUID cirnoId) {
        Loaded existing = ACTIVE.remove(cirnoId);
        if (existing != null) {
            ForgeChunkManager.forceChunk(existing.level(), CatBurger.MOD_ID, cirnoId,
                    existing.chunk().x, existing.chunk().z, false, true);
        }
    }
}
