package net.zhaiji.cirno.util;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.entity.CirnoEntity;
import org.slf4j.Logger;

/**
 * Single choke point for putting one of OUR Cirnos into a level.
 * <p>
 * In a large modpack, other mods routinely cancel {@link EntityJoinLevelEvent} for entities they
 * don't recognise (entity-limit / spawn-control / claim / "only allow these mobs" mods, and even
 * maid add-ons that assume every {@code EntityMaid} is TLM's own maid type). A normal TLM maid
 * passes those filters because they were written with it in mind; a Cirno is a different entity
 * type, so she can be silently refused while the maid next to her spawns fine.
 * <p>
 * While one of our own spawns is in flight, a LOWEST-priority listener that still receives
 * cancelled events un-cancels the join for the CirnoEntity being added (if
 * {@code cirnoForceJoin} is on, which is the default) and logs that it happened.
 */
public final class CirnoSpawnGuard {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    /** Set by the last {@link #add} so diagnostics can report that a join was force-allowed. */
    private static volatile boolean lastJoinWasOverridden = false;
    private static boolean loggedOverride = false;

    private CirnoSpawnGuard() {
    }

    /** Adds {@code entity} to {@code level}; returns whether it actually joined. */
    public static boolean add(ServerLevel level, Entity entity) {
        lastJoinWasOverridden = false;
        DEPTH.set(DEPTH.get() + 1);
        try {
            return level.addFreshEntity(entity);
        } finally {
            DEPTH.set(Math.max(0, DEPTH.get() - 1));
        }
    }

    public static boolean lastJoinWasOverridden() {
        return lastJoinWasOverridden;
    }

    /** Registered at LOWEST priority with receiveCancelled = true. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.isCanceled()) return;
        if (!(event.getEntity() instanceof CirnoEntity)) return;
        if (DEPTH.get() <= 0) return; // only our own deliberate spawns, never a vanilla/other-mod path
        if (!CirnoCommonConfig.cirnoForceJoin) return;

        event.setCanceled(false);
        lastJoinWasOverridden = true;
        if (!loggedOverride) {
            loggedOverride = true;
            LOGGER.warn("Cirno: another mod cancelled her EntityJoinLevelEvent; overriding it so she can spawn "
                    + "(config 'cirnoForceJoin'). A mod in this pack is filtering unknown entity types — "
                    + "this is why a normal maid spawns but Cirno did not.");
        }
    }
}
