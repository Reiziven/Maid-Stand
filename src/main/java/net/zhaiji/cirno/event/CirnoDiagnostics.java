package net.zhaiji.cirno.event;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.zhaiji.cirno.compat.CompatManager;
import net.zhaiji.cirno.compat.CuriosCompat;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.entity.CirnoEntity;
import net.zhaiji.cirno.init.InitEntity;
import net.zhaiji.cirno.util.CirnoSpawnGuard;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /cirnodiag}: answers "why won't Cirno spawn for me?" in one command, and writes the same
 * report to latest.log. It checks every gate the real summon path checks, then performs a
 * harmless test spawn (create -> add to the world -> discard immediately) and reports exactly
 * which phase fails and, for an exception, which classes it came through (that usually names the
 * offending mod).
 */
public final class CirnoDiagnostics {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CirnoDiagnostics() {
    }

    public static int run(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        out.add("§b[Cirno] ── Diagnostics ──");

        // ── Environment / config gates ──
        out.add(line("Touhou Little Maid loaded", CompatManager.isTLMLoad()));
        out.add(line("Curios loaded", CuriosCompat.isLoaded()));
        out.add(line("config companionEntityEnabled", CirnoCommonConfig.companionEntityEnabled));
        out.add(line("config cirnoForceJoin", CirnoCommonConfig.cirnoForceJoin));
        out.add(line("config cirnoUnloadProtection", CirnoCommonConfig.cirnoUnloadProtection));
        out.add(line("config cirnoCanDie / tombstone", CirnoCommonConfig.cirnoCanDie + " / "
                + CirnoCommonConfig.cirnoSpawnTombstoneOnDeath));

        // ── Curio state ──
        ItemStack stack = CirnoStateAccess.getCirnoStack(player);
        out.add(line("Cirno curio equipped", stack != null));
        if (stack != null) {
            UUID uuid = CirnoStateAccess.getCompanionUUID(stack);
            boolean hidden = CirnoStateAccess.getCompanionHidden(stack);
            boolean needsItem = CirnoStateAccess.requiresItemRevive(stack);
            out.add(line("  hidden", hidden + (hidden ? "  (summon is blocked while hidden — press H)" : "")));
            out.add(line("  requires item revive", needsItem
                    + (needsItem && hidden ? "  <-- THIS BLOCKS SUMMONING (she died/was captured; revive her from a film/slab)" : "")));
            out.add(line("  revive countdown (ticks)",
                    player.getPersistentData().getInt(CirnoStateAccess.NBT_CIRNO_REVIVE_TICKS_LEFT)));
            out.add(line("  tracked UUID", uuid));
            if (uuid != null) {
                String where = "not found in any loaded level";
                for (ServerLevel level : player.getServer().getAllLevels()) {
                    Entity e = level.getEntity(uuid);
                    if (e != null) {
                        where = e.getClass().getSimpleName() + " in " + level.dimension().location()
                                + " at " + e.blockPosition() + (e.isAlive() ? "" : " (dead)");
                        break;
                    }
                }
                out.add(line("  tracked entity", where));
            }
        }

        // ── External storage scan (the "verification" step) ──
        long t0 = System.nanoTime();
        try {
            boolean stored = CirnoStorageHandler.isStoredInExternalItem(player);
            out.add(line("stored in smart slab/photo/film", stored
                    + (stored ? "  <-- THIS BLOCKS SUMMONING (an item holding her is reachable)" : "")
                    + "  [" + (System.nanoTime() - t0) / 1_000_000 + " ms]"));
        } catch (RuntimeException | LinkageError e) {
            out.add(line("storage scan", "THREW " + describe(e)));
            LOGGER.error("Cirno diagnostics: storage scan threw", e);
        }

        // ── Test spawn ──
        out.add("§7Test spawn (created, added, and removed again immediately):");
        CirnoEntity test = null;
        try {
            if (!(player.level() instanceof ServerLevel level)) {
                out.add(line("  result", "player is not in a server level"));
            } else {
                test = InitEntity.CIRNO.get().create(level);
                if (test == null) {
                    out.add(line("  create()", "returned null (entity type disabled by a feature flag?)"));
                } else {
                    out.add(line("  create()", "OK"));
                    test.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
                    test.setInvisible(true);
                    test.setSilent(true);
                    boolean joined = CirnoSpawnGuard.add(level, test);
                    if (joined) {
                        out.add(line("  join level", "OK" + (CirnoSpawnGuard.lastJoinWasOverridden()
                                ? "  (ANOTHER MOD CANCELLED IT; cirnoForceJoin overrode it)" : "")));
                    } else {
                        out.add(line("  join level", "REFUSED  <-- something cancelled EntityJoinLevelEvent "
                                + "and cirnoForceJoin did not (or could not) override it"));
                    }
                }
            }
        } catch (RuntimeException | LinkageError e) {
            out.add(line("  test spawn", "THREW " + describe(e)));
            LOGGER.error("Cirno diagnostics: test spawn threw", e);
        } finally {
            if (test != null) {
                try {
                    test.discardDuplicate();
                } catch (RuntimeException | LinkageError ignored) {
                    // best effort
                }
            }
        }

        for (String s : out) {
            player.sendSystemMessage(Component.literal(s));
            LOGGER.info("Cirno diagnostics [{}]: {}", player.getGameProfile().getName(), s.replaceAll("§.", ""));
        }
        return 1;
    }

    private static String line(String label, Object value) {
        return "§7" + label + ": §f" + value;
    }

    /** Exception + the first few non-JDK frames: the frames usually name the mod that caused it. */
    private static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder(t.toString());
        int shown = 0;
        for (StackTraceElement f : t.getStackTrace()) {
            String c = f.getClassName();
            if (c.startsWith("java.") || c.startsWith("jdk.") || c.startsWith("sun.")) continue;
            sb.append(" @ ").append(c).append('.').append(f.getMethodName());
            if (++shown >= 4) break;
        }
        return sb.toString();
    }
}
