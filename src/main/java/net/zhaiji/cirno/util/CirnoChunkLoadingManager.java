package net.zhaiji.cirno.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.world.ForgeChunkManager;
import net.zhaiji.cirno.Cirno;
import org.slf4j.Logger;

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
 * still technically resident, but not properly ticking.
 *
 * <p>One ticket per Cirno at a time, moved as she moves. Deliberately NOT restored
 * across server restarts (see {@link #registerCallback()}) — she's tied to an online
 * owner, and the existing NBT snapshot/respawn system already recovers her cleanly on
 * next login, so there's nothing worth persisting here. That also means this can never
 * turn into a permanently-forced chunk left over from a mod that's since been removed
 * or an item that's since been discarded.
 *
 * <p>Because ticking is what makes {@link #update} run again in the first place (it's
 * called from {@code CirnoEntity#tick()}), a Cirno who ever does end up in the frozen
 * ghost state has no way to heal herself — nothing is ticking her to reassert the
 * ticket. {@code PlayerTickHandler}'s watchdog closes that loop from the OWNER's tick
 * (which always runs) by calling {@link #update} directly against her last known
 * location whenever {@link #isTracked} says she isn't currently holding a valid ticket
 * there; forcing the chunk back open makes it start ticking again, which resumes her
 * own tick() and lets everything else (including the leash-distance recall in
 * CirnoEntity) take over from there.
 */
public final class CirnoChunkLoadingManager {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CirnoChunkLoadingManager() {
    }

    private record Loaded(ServerLevel level, ChunkPos chunk) {
    }

    private static final Map<UUID, Loaded> ACTIVE = new ConcurrentHashMap<>();

    /**
     * Register once from FMLCommonSetupEvent so Forge doesn't warn about an unhandled
     * ticket owner on world load. Intentionally a no-op: leftover tickets from an
     * unclean shutdown are just left unforced, and protection re-establishes itself
     * the moment the relevant Cirno starts ticking again (or the watchdog reasserts it).
     */
    public static void registerCallback() {
        ForgeChunkManager.setForcedChunkLoadingCallback(Cirno.MOD_ID, (tickets, level) -> {
            // No re-forcing on load — see class javadoc.
        });
    }

    /**
     * Moves (or creates) the force-load ticket to Cirno's current chunk. Cheap no-op
     * when she hasn't changed chunks/dimensions since the last call, so this is safe
     * to call every tick from {@code CirnoEntity#tick()} — and safe to call from the
     * watchdog too, since it's idempotent either way.
     */
    public static void update(UUID cirnoId, ServerLevel level, ChunkPos currentChunk) {
        Loaded existing = ACTIVE.get(cirnoId);
        if (existing != null && existing.level() == level && existing.chunk().equals(currentChunk)) {
            return;
        }

        // Claim the NEW chunk FIRST, and only release the old ticket once the new one
        // is confirmed. The previous release-then-add order left a window where a
        // failed add (Forge's per-mod ticket cap, a transient chunk-source hiccup,
        // the dimension not being ready yet, ...) dropped protection entirely instead
        // of simply keeping the old, still-valid ticket — silently leaving her fully
        // unprotected until she happened to move chunks again. That gap is exactly how
        // a chunk-unload "ghost" could occur despite this whole system existing.
        boolean added = ForgeChunkManager.forceChunk(level, Cirno.MOD_ID, cirnoId,
                currentChunk.x, currentChunk.z, true, true);

        if (!added) {
            LOGGER.warn("Cirno: failed to force-load chunk {} in {} to protect Cirno {} — " +
                            "keeping her previous ticket (if any) rather than dropping protection.",
                    currentChunk, level.dimension().location(), cirnoId);
            return;
        }

        if (existing != null) {
            ForgeChunkManager.forceChunk(existing.level(), Cirno.MOD_ID, cirnoId,
                    existing.chunk().x, existing.chunk().z, false, true);
        }
        ACTIVE.put(cirnoId, new Loaded(level, currentChunk));
    }

    /**
     * Releases Cirno's held ticket, if any. MUST be called whenever she's legitimately
     * removed (H hide, smart slab / camera storage, death, dimension change) — without
     * this the chunk she was last standing in stays force-loaded forever.
     */
    public static void release(UUID cirnoId) {
        Loaded existing = ACTIVE.remove(cirnoId);
        if (existing != null) {
            ForgeChunkManager.forceChunk(existing.level(), Cirno.MOD_ID, cirnoId,
                    existing.chunk().x, existing.chunk().z, false, true);
        }
    }

    /**
     * True if this Cirno currently holds a confirmed force-load ticket matching
     * {@code level}/{@code chunk} — i.e. whether {@link #update} last succeeded there.
     * Lets the watchdog tell "properly protected" apart from "somehow lost her ticket"
     * without needing her to be ticking in order to ask.
     */
    public static boolean isTracked(UUID cirnoId, ServerLevel level, ChunkPos chunk) {
        Loaded existing = ACTIVE.get(cirnoId);
        return existing != null && existing.level() == level && existing.chunk().equals(chunk);
    }
}
