package net.zhaiji.cirno.event;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidAndItemTransformEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.compat.CompatManager;
import net.zhaiji.cirno.compat.TLMCompat;
import net.zhaiji.cirno.entity.CirnoEntity;
import net.zhaiji.cirno.init.InitEntity;
import net.zhaiji.cirno.init.InitItem;
import net.zhaiji.cirno.network.PacketManager;
import net.zhaiji.cirno.network.client.packet.PlayerDeathPacket;
import net.zhaiji.cirno.network.client.packet.SyncCompanionVisibilityPacket;
import net.zhaiji.cirno.util.CirnoChunkLoadingManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PlayerTickHandler {

    /**
     * Last "has command Cirno" state actually pushed to each player's client via
     * {@link net.zhaiji.cirno.network.client.packet.SyncTKAccessPacket}, so the
     * sync packet only goes out when it changes instead of every tick. Keyed by
     * player UUID rather than kept on the player itself since it's purely a
     * network-dedup cache, not game state.
     */
    private static final java.util.Map<UUID, Boolean> lastSyncedCommandCirnoTK = new java.util.concurrent.ConcurrentHashMap<>();

    /** Drops the dedup-cache entry for a player on logout so the map doesn't grow forever. */
    public static void clearSyncedCommandCirnoTKCache(Player player) {
        lastSyncedCommandCirnoTK.remove(player.getUUID());
    }

    // ── Player tick: shield repel + telekinesis control tick ─────────────────

    public static void handlerPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        // Resolve the Curios lookup ONCE per tick. It walks every curio slot, and this
        // handler used to repeat it up to five times per player per tick (hasCurio x3
        // via hasTKAccess, plus CirnoStateAccess.getCirnoStack inside TelekinesisHandler.tickCirnoRetarget).
        ItemStack curioStack = CirnoStateAccess.getCirnoStack(player);
        boolean curioEquipped = curioStack != null;
        // Telekinesis needs an actually-summoned command Cirno, not just a reserved/
        // stored slot — matches CirnoStateAccess#hasTKAccess, which the server itself
        // uses to gate the shield, mode toggle, and mob control.
        boolean hasLiveCommandCirno = CommandCirnoManager.findAnyLiveCommandCirnoPublic(player) != null;
        boolean tkSourcePresent = curioEquipped || hasLiveCommandCirno;

        // Push the command-slot half of TK eligibility to the client whenever it
        // changes. The Curios-equipped half is already visible to the client for
        // free via the Curios API itself, but a command-spawned Cirno's slots live
        // only in this ServerPlayer's persistent NBT — without this, the G-key
        // handler on the client (which has no other way to see that NBT) always
        // believes a command-only Cirno owner has no Cirno at all.
        if (player instanceof ServerPlayer serverPlayer) {
            Boolean previous = lastSyncedCommandCirnoTK.get(player.getUUID());
            if (previous == null || previous != hasLiveCommandCirno) {
                lastSyncedCommandCirnoTK.put(player.getUUID(), hasLiveCommandCirno);
                PacketManager.sendToClient(
                        new net.zhaiji.cirno.network.client.packet.SyncTKAccessPacket(hasLiveCommandCirno),
                        serverPlayer);
            }
        }
        boolean curioBuffsActive = curioEquipped && TelekinesisHandler.buffsAndTKAllowed();
        // Shield follows the same curio-OR-command-Cirno access rule as active TK
        // control below — it used to be curio-only, which silently denied the
        // shield to command-spawned Cirno owners even though its buffs and TK
        // control both already treated the two sources as equivalent.
        boolean shieldActive = tkSourcePresent && TelekinesisHandler.buffsAndTKAllowed();

        if (shieldActive) {
            TelekinesisHandler.tickShield(player);
        }

        // TK works with curio OR command Cirno
        if (tkSourcePresent) {
            TelekinesisHandler.tickTelekinesisControl(player);
        }

        // Post-login: keep looking for the already-summoned Cirno before ever replacing her.
        PlayerLifecycleHandler.tickLoginVerify(player);

        // Cirno revive only relevant when curio is equipped
        if (curioBuffsActive) {
            CompanionLifecycleHandler.tickCirnoRevive(player);
        }

        // Retarget covers every live Cirno the owner has out — curio companion OR
        // command-spawned — so it runs off tkSourcePresent (same curio-OR-command
        // rule as shield/TK control above), not curioBuffsActive. Previously this
        // was gated on curioBuffsActive and passed only the curio stack, so a
        // command-spawned Cirno's attackers were never retargeted at all.
        if (tkSourcePresent) {
            TelekinesisHandler.tickCirnoRetarget(player);
        }

        // Command-slot revive timers run regardless of curio state — they have
        // nothing to do with it.
        CommandCirnoManager.tickCommandCirnoRevive(player);

        DupeCleanupHandler.tickDupeCleanup(player);

        watchdogRecoverStuckCirnos(player);
    }



    /**
     * Reasserts the chunk force-load ticket for any of the player's tracked Cirnos
     * that are resident but stuck — either not actually ticking (chunk-unload
     * "ghost") or found to have silently lost their ticket (see
     * {@link CirnoChunkLoadingManager}'s class doc for why that can happen).
     * <p>
     * This exists because the protection ticket is normally only ever refreshed
     * from {@code CirnoEntity#tick()} itself — a chicken-and-egg problem if she
     * ever does end up stuck, since nothing is ticking her to fix that. The
     * player's own tick always runs regardless of where any of her Cirnos are, so
     * running the check from here closes that loop: reasserting the ticket makes
     * the chunk start ticking again, which resumes her tick() (including the
     * leash-distance recall in CirnoEntity), all without waiting on a full world
     * reload.
     */
    /** Every 10 ticks per player, staggered by entity id so they don't all land on the same tick. */
    private static final int WATCHDOG_INTERVAL_TICKS = 10;

    private static void watchdogRecoverStuckCirnos(Player player) {
        if (!CirnoCommonConfig.cirnoUnloadProtection) return;
        if ((player.tickCount + player.getId()) % WATCHDOG_INTERVAL_TICKS != 0) return;
        if (player.getServer() == null) return;

        CompoundTag data = player.getPersistentData();
        if (!data.contains(CirnoStateAccess.NBT_CIRNO_LAST_LOCATIONS, 9)) return;

        net.minecraft.nbt.ListTag locations = data.getList(CirnoStateAccess.NBT_CIRNO_LAST_LOCATIONS, 10);
        for (int i = 0; i < locations.size(); i++) {
            CompoundTag location = locations.getCompound(i);
            if (!location.hasUUID("UUID")) continue;
            UUID uuid = location.getUUID("UUID");

            // Only reach out for identities this player is actually still tracking
            // as a live Cirno right now — a stale last-known-location entry for
            // one that's since been stored/removed/replaced/dead isn't something
            // we should be loading chunks to go poke at.
            if (!CirnoStateAccess.isTrackedCompanionUUID(player, uuid)) continue;

            ServerLevel level;
            try {
                net.minecraft.resources.ResourceKey<Level> dimension = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        new ResourceLocation(location.getString("Dimension")));
                level = player.getServer().getLevel(dimension);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (level == null) continue;

            Entity entity = level.getEntity(uuid);
            if (!(entity instanceof CirnoEntity)) {
                // Not currently resident in memory — give Minecraft a chance to
                // restore her from the chunk's own saved data. This only loads
                // the single chunk she was last seen in; no replacement entity
                // is ever created here.
                level.getChunk(location.getInt("ChunkX"), location.getInt("ChunkZ"));
                entity = level.getEntity(uuid);
            }
            if (!(entity instanceof CirnoEntity cirno) || !cirno.isAlive()) continue;

            ChunkPos chunkPos = cirno.chunkPosition();
            boolean ticking = level.isPositionEntityTicking(cirno.blockPosition());
            if (!ticking || !CirnoChunkLoadingManager.isTracked(uuid, level, chunkPos)) {
                CirnoChunkLoadingManager.update(uuid, level, chunkPos);
            }
        }
    }
}
