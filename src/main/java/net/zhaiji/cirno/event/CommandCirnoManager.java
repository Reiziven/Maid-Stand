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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CommandCirnoManager {

    public static int getUsedCommandCirnoSlotCountPublic(Player player) {
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        // The Curio Cirno has her own independent slot (index 0) and must not
        // count against the 4 command slots (see addCommandCirno).
        int count = 0;
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            if (CirnoStateAccess.getSlotTag(player, i) != null) count++;
        }
        return count;
    }



    public static boolean isCommandCirnoSlotUsedPublic(Player player, int index) {
        return CirnoStateAccess.getSlotTag(player, index) != null;
    }



    /** True only if this slot's Cirno is genuinely alive and loaded right now. */
    public static boolean isCommandCirnoSlotLivePublic(Player player, int index) {
        return findLiveCommandCirnoAtPublic(player, index) != null;
    }



    /**
     * Finds the live, currently-loaded CirnoEntity tracked by this slot, or null if
     * she's stored, her chunk isn't loaded, or the slot isn't in use at all. The
     * single place both the H-key path and /cirno look her up, so "is she really
     * there" is answered the same way everywhere instead of by separately-drifting
     * copies.
     */
    @Nullable
    public static CirnoEntity findLiveCommandCirnoAtPublic(Player player, int index) {
        CompoundTag slot = CirnoStateAccess.getSlotTag(player, index);
        if (slot == null || !slot.hasUUID(CirnoStateAccess.SLOT_UUID) || player.getServer() == null) return null;
        UUID uuid = slot.getUUID(CirnoStateAccess.SLOT_UUID);
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof CirnoEntity cirno && cirno.isAlive()) return cirno;
        }
        return null;
    }



    /** First live command-spawned Cirno across all slots, or null. Used by the V-key GUI. */
    @Nullable
    public static CirnoEntity findAnyLiveCommandCirnoPublic(Player player) {
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CirnoEntity cirno = findLiveCommandCirnoAtPublic(player, i);
            if (cirno != null) return cirno;
        }
        return null;
    }



    /**
     * All of this owner's currently live Cirnos, in a stable activation order: the
     * Curios-equipped companion first (if she's alive), then each occupied command
     * slot from 1..{@link CirnoStateAccess#MAX_COMMAND_CIRNOS} in order — slots are always handed
     * out lowest-first when a new command Cirno is created, so slot order already
     * matches creation order. Used by {@link CirnoEntity}'s multi-Cirno follow
     * formation: an entity's index in this list picks its spot (left / right /
     * behind), and because the list is rebuilt fresh from who's actually alive
     * right now, storing or removing one of them automatically reflows everyone
     * else into the next spot up.
     */
    public static List<CirnoEntity> getOrderedLiveCirnosPublic(Player owner) {
        List<CirnoEntity> result = new ArrayList<>();
        ItemStack curioStack = CirnoStateAccess.getCirnoStack(owner);
        if (curioStack != null) {
            UUID curioUUID = CirnoStateAccess.getCompanionUUID(curioStack);
            if (curioUUID != null && owner.getServer() != null) {
                for (ServerLevel level : owner.getServer().getAllLevels()) {
                    Entity e = level.getEntity(curioUUID);
                    if (e instanceof CirnoEntity cirno && cirno.isAlive()) {
                        result.add(cirno);
                        break;
                    }
                }
            }
        }
        CirnoStateAccess.migrateLegacySlotIfNeeded(owner);
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CirnoEntity cirno = findLiveCommandCirnoAtPublic(owner, i);
            if (cirno != null) result.add(cirno);
        }
        return result;
    }



    /**
     * Discards the live entity tracked by this slot (snapshotting her NBT into the
     * slot first), or — if her chunk simply isn't loaded right now, which is NOT
     * the same as her being gone — leaves the same "pending discard" breadcrumb
     * used elsewhere (CompanionLifecycleHandler.removeCompanion(), CirnoEntity#tick()) so she's cleaned up
     * for real the moment her chunk loads, instead of surviving as a live,
     * untracked ghost. Always leaves the slot marked Stored=true.
     */
    static void discardTrackedCirnoAt(Player player, int index, UUID uuid) {
        CompoundTag slot = CirnoStateAccess.getSlotTag(player, index);
        if (slot == null) slot = new CompoundTag();

        CirnoEntity cirno = findLiveCommandCirnoAtPublic(player, index);
        if (cirno != null) {
            CompoundTag snapshot = new CompoundTag();
            cirno.addAdditionalSaveData(snapshot);
            slot.put(CirnoStateAccess.SLOT_DATA, snapshot);
            cirno.allowNextRemoval();
            cirno.discard();
        } else {
            // Chunk not loaded — can't snapshot her directly right now. NOTE: this
            // used to fall back to the shared "last known" auto-save snapshot, but
            // that blob is now written exclusively for the Curios-equipped companion
            // (see CompanionLifecycleHandler.snapshotCirnoDurable()) precisely so a command slot can no longer
            // cross-adopt it — that was the same identity mix-up that let the curio
            // companion adopt/duplicate a command Cirno, just mirrored onto a command
            // slot instead. If this slot already has an older CirnoStateAccess.SLOT_DATA snapshot from
            // a previous CirnoEntity autosave, that's kept as-is; otherwise she'll be
            // restored once her chunk loads again and the pending-discard breadcrumb
            // below fires for real.
            addPendingDiscard(player, uuid, index);
        }
        slot.remove(CirnoStateAccess.SLOT_UUID);
        slot.putBoolean(CirnoStateAccess.SLOT_STORED, true);
        CirnoStateAccess.putSlotTag(player, index, slot);
    }



    /**
     * Stores (discards) this slot's Cirno, breadcrumb-safe even when her chunk
     * isn't currently loaded. Does nothing (returns false) if the slot isn't in
     * use or is already stored.
     */
    public static boolean storeCommandCirnoAt(Player player, int index) {
        CompoundTag slot = CirnoStateAccess.getSlotTag(player, index);
        if (slot == null || slot.getBoolean(CirnoStateAccess.SLOT_STORED)) return false;
        UUID uuid = slot.hasUUID(CirnoStateAccess.SLOT_UUID) ? slot.getUUID(CirnoStateAccess.SLOT_UUID) : null;
        if (uuid == null) {
            // No live UUID recorded — just flip the flag.
            slot.putBoolean(CirnoStateAccess.SLOT_STORED, true);
            slot.putBoolean(CirnoStateAccess.SLOT_RESTORE_ON_LOGIN, false);
            CirnoStateAccess.putSlotTag(player, index, slot);
            return true;
        }
        // A normal player store should remain stored across logout/reload.
        slot.putBoolean(CirnoStateAccess.SLOT_RESTORE_ON_LOGIN, false);
        CirnoStateAccess.putSlotTag(player, index, slot);
        discardTrackedCirnoAt(player, index, uuid);
        return true;
    }



    /** Stores every currently-live command Cirno slot. */
    public static void storeAllCommandCirno(Player player) {
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            storeCommandCirnoAt(player, i);
        }
    }



    /**
     * (Re)spawns the Cirno tracked by this slot. Prefers a copy stashed in a smart
     * slab / photo matching THIS slot's last-known identity (so restoring one slot
     * can never pick up a different slot's stored Cirno), clearing it so she isn't
     * left duplicated there; otherwise restores from the slot's own snapshot if one
     * exists. Points the slot's tracking at the freshly spawned entity. Returns
     * null if the slot isn't in use or entity creation itself failed.
     */
    @Nullable
    public static CirnoEntity spawnOrRestoreCommandCirnoAt(Player player, int index, boolean visible) {
        if (player.level().isClientSide || player.getServer() == null) return null;
        CompoundTag slot = CirnoStateAccess.getSlotTag(player, index);
        if (slot == null) return null;

        // Already alive — just re-apply the visibility preference instead of duplicating her.
        CirnoEntity already = findLiveCommandCirnoAtPublic(player, index);
        if (already != null) {
            already.setInvisible(!visible);
            slot.putBoolean(CirnoStateAccess.SLOT_VISIBLE, visible);
            slot.putBoolean(CirnoStateAccess.SLOT_STORED, false);
            CirnoStateAccess.putSlotTag(player, index, slot);
            // Owner summoned her (H / Maid Panel) — don't leave her asleep in the Maid Bed.
            already.wakeFromMaidBed();
            return already;
        }

        // Still recovering from a tombstone-less death (see handleCompanionDeath /
        // tickCommandCirnoRevive) — refuse until the countdown finishes.
        if (slot.getInt(CirnoStateAccess.SLOT_REVIVE_TICKS_LEFT) > 0) {
            player.displayClientMessage(
                    Component.literal("§c[Cirno] Still recovering — try again soon."), true);
            return null;
        }

        // Post-death (tombstone mode) revive cooldown — applies to the film / photo /
        // slab and to the durable-snapshot fallback alike, so it gates everything below.
        long readyLeft = CirnoStateAccess.slotReviveReadyRemaining(player, slot);
        if (readyLeft > 0) {
            player.displayClientMessage(CirnoStateAccess.reviveCooldownMessage(readyLeft), true);
            return null;
        }

        UUID lastKnownUuid = slot.hasUUID(CirnoStateAccess.SLOT_UUID) ? slot.getUUID(CirnoStateAccess.SLOT_UUID)
                : slot.contains(CirnoStateAccess.SLOT_DATA, 10) && slot.getCompound(CirnoStateAccess.SLOT_DATA).hasUUID("UUID")
                ? slot.getCompound(CirnoStateAccess.SLOT_DATA).getUUID("UUID") : null;
        boolean trulyDead = slot.getBoolean(CirnoStateAccess.SLOT_DEAD);

        // A film holding this exact Cirno is reachable right now, but film
        // summoning is disabled — leave her stored. Do NOT fall through to the
        // CirnoStateAccess.SLOT_DATA / durable-snapshot fallbacks below: those exist to cover a
        // storage item stuck in an unloaded chunk, not to quietly resurrect her
        // from a backup the moment the real (gated) item is found instead.
        if (CirnoStateAccess.hasGatedFilmReachable(player, lastKnownUuid)) {
            player.displayClientMessage(
                    Component.literal("§c[Cirno] She's held in a film — film revival is disabled."), true);
            return null;
        }

        // First try the exact external item, then use the retained last snapshot
        // if that item is beyond the currently loaded scan area.
        CompoundTag snapshot = lastKnownUuid != null
                ? CirnoStateAccess.extractAndClearExternallyStoredCirnoForUuid(
                player,
                lastKnownUuid,
                CirnoStateAccess.canSummonFromFilm())
                : null;
        boolean restoredFromStorageItem = snapshot != null;

        if (snapshot == null && slot.contains(CirnoStateAccess.SLOT_DATA, 10)) {
            snapshot = slot.getCompound(CirnoStateAccess.SLOT_DATA);
        }

        if (trulyDead && !restoredFromStorageItem && lastKnownUuid != null) {
            DupeCleanupHandler.markStorageUuidForCleanup(player, lastKnownUuid);
            if (CirnoStateAccess.findLoadedEntity(player, lastKnownUuid) == null) {
                addPendingDiscardOnly(player, lastKnownUuid, index);
            }
        }

        if (trulyDead && snapshot == null) {
            // The storage item may be in an unloaded chunk, so its NBT cannot be
            // extracted now. Fall back to the last durable snapshot where one
            // exists, otherwise a blank snapshot; flag its identity so any
            // still-unloaded storage copy is removed when a loaded-area scan can
            // reach it. Never force-load the item's chunk.
            CompoundTag durable = CompanionLifecycleHandler.getDurableLastKnownCirnoData(player);
            if (durable != null && lastKnownUuid != null && durable.hasUUID("UUID")
                    && !durable.getUUID("UUID").equals(lastKnownUuid)) durable = null;
            snapshot = durable;
            if (snapshot == null) snapshot = new CompoundTag();
            if (lastKnownUuid != null) {
                snapshot.putUUID("UUID", lastKnownUuid);
                Entity active = CirnoStateAccess.findLoadedEntity(player, lastKnownUuid);
                if (active instanceof CirnoEntity existing && existing.isAlive()) {
                    existing.setOwnerPlayer(player);
                    existing.setInvisible(!visible);
                    existing.wakeFromMaidBed();
                    slot.putBoolean(CirnoStateAccess.SLOT_STORED, false);
                    slot.putBoolean(CirnoStateAccess.SLOT_DEAD, false);
                    slot.putBoolean(CirnoStateAccess.SLOT_VISIBLE, visible);
                    CirnoStateAccess.putSlotTag(player, index, slot);
                    return existing;
                }
            }
        }

        ServerLevel level = (ServerLevel) player.level();
        CirnoEntity cirno = InitEntity.CIRNO.get().create(level);
        if (cirno == null) return null;

        cirno.setOwnerPlayer(player);
        cirno.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);

        if (snapshot != null) {
            cirno.readAdditionalSaveData(snapshot);
            cirno.setOwnerUUID(player.getUUID());
            cirno.setTame(true);
            cirno.setOwnerPlayer(player);

            // Keep an already-active Cirno with this stored identity. The newly
            // constructed candidate is discarded without saving over her state.
            if (snapshot.hasUUID("UUID")) {
                Entity active = CirnoStateAccess.findLoadedEntity(player, snapshot.getUUID("UUID"));
                if (active instanceof CirnoEntity existing && existing.isAlive()) {
                    cirno.discardDuplicate();
                    return null;
                }
            }
        }

        cirno.setInvisible(!visible);
        level.addFreshEntity(cirno);
        // The NBT snapshot restored above can carry over a sleeping pose if she
        // was captured mid-sleep — make sure a freshly (re)spawned companion
        // never appears already stuck asleep in front of the owner.
        cirno.wakeFromMaidBed();

        slot.putUUID(CirnoStateAccess.SLOT_UUID, cirno.getUUID());
        slot.putBoolean(CirnoStateAccess.SLOT_STORED, false);
        slot.putBoolean(CirnoStateAccess.SLOT_VISIBLE, visible);
        slot.putBoolean(CirnoStateAccess.SLOT_DEAD, false);
        slot.remove(CirnoStateAccess.SLOT_DATA);
        slot.remove(CirnoStateAccess.SLOT_REVIVE_TICKS_LEFT);
        CirnoStateAccess.putSlotTag(player, index, slot);
        return cirno;
    }



    /** Restores only command Cirnos that were stored automatically for owner logout/reload. */
    static int restoreCommandCirnosAfterLogin(Player player) {
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        int restored = 0;
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = CirnoStateAccess.getSlotTag(player, i);
            if (slot == null
                    || !slot.getBoolean(CirnoStateAccess.SLOT_STORED)
                    || !slot.getBoolean(CirnoStateAccess.SLOT_RESTORE_ON_LOGIN)) continue;

            boolean visible = slot.getBoolean(CirnoStateAccess.SLOT_VISIBLE);
            if (spawnOrRestoreCommandCirnoAt(player, i, visible) != null) {
                restored++;
                CompoundTag updatedSlot = CirnoStateAccess.getSlotTag(player, i);
                if (updatedSlot != null) {
                    updatedSlot.putBoolean(CirnoStateAccess.SLOT_RESTORE_ON_LOGIN, false);
                    CirnoStateAccess.putSlotTag(player, i, updatedSlot);
                }
            }
        }
        return restored;
    }



    /** Restores every stored-but-in-use command Cirno slot. Returns how many were restored. */
    public static int restoreAllCommandCirno(Player player) {
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        int restored = 0;
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = CirnoStateAccess.getSlotTag(player, i);
            if (slot == null || !slot.getBoolean(CirnoStateAccess.SLOT_STORED)) continue;
            boolean visible = slot.getBoolean(CirnoStateAccess.SLOT_VISIBLE);
            if (spawnOrRestoreCommandCirnoAt(player, i, visible) != null) restored++;
        }
        return restored;
    }



    /**
     * Creates a brand-new command Cirno in the first free slot (1..{@link CirnoStateAccess#MAX_COMMAND_CIRNOS}).
     * Returns the 1-based slot index on success, {@code -1} if all slots are already
     * in use, or {@code -2} if entity creation itself failed while spawning.
     */
    public static int addCommandCirno(Player player, boolean spawn, boolean visible) {
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);

        // The Curio Cirno lives in her own slot (index 0) and is completely
        // independent of the 4 command slots (1..MAX_COMMAND_CIRNOS), so she
        // must NOT be counted against this limit — doing so wrongly capped
        // the 4th command slot whenever a Curio Cirno was also active.
        int usedSlots = 0;
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            if (CirnoStateAccess.getSlotTag(player, i) != null) {
                usedSlots++;
            }
        }
        if (usedSlots >= CirnoStateAccess.MAX_COMMAND_CIRNOS) return -1;

        int index = -1;
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            if (CirnoStateAccess.getSlotTag(player, i) == null) {
                index = i;
                break;
            }
        }
        if (index == -1) return -1;

        CompoundTag slot = new CompoundTag();
        slot.putBoolean(CirnoStateAccess.SLOT_VISIBLE, visible);
        slot.putBoolean(CirnoStateAccess.SLOT_STORED, true);
        CirnoStateAccess.putSlotTag(player, index, slot);

        if (spawn) {
            CirnoEntity cirno = spawnOrRestoreCommandCirnoAt(player, index, visible);
            if (cirno == null) {
                CirnoStateAccess.clearSlotTag(player, index);
                return -2;
            }
        }
        return index;
    }



    /**
     * Server-side "what maid is this player looking at" check, so the client can't
     * name an arbitrary entity. Ignores Cirnos (already converted) and anything
     * behind a solid block.
     */
    @Nullable
    private static EntityMaid findLookedAtMaid(ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player,
                eye,
                end,
                box,
                e -> e instanceof EntityMaid
                        && !(e instanceof CirnoEntity)
                        && e.isAlive()
                        && !e.isSpectator(),
                reach * reach
        );
        if (hit == null) return null;

        HitResult blocked = player.level().clip(
                new ClipContext(
                        eye,
                        hit.getLocation(),
                        ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE,
                        player
                )
        );

        if (blocked.getType() != HitResult.Type.MISS) return null;
        return (EntityMaid) hit.getEntity();
    }



    /**
     * Oathpin conversion (survival "+ Add"): turns the maid the player is looking at
     * into a command Cirno in the first free slot. The maid's data (inventory, tasks,
     * etc.) is carried over, but she takes on Cirno's model rather than keeping her own.
     * Nothing is touched unless a slot is free and the Cirno actually spawns, so a
     * failed attempt never costs the player their maid. Returns true on success.
     */
    public static boolean convertLookedAtMaidToCommandCirno(ServerPlayer player) {
        EntityMaid maid = findLookedAtMaid(player, CirnoStateAccess.OATHPIN_REACH);
        if (maid == null) {
            player.sendSystemMessage(Component.literal(
                    "§c[Cirno] Look at one of your maids while holding the Oathpin."));
            return false;
        }

        if (!maid.isOwnedBy(player)) {
            player.sendSystemMessage(
                    Component.literal("§c[Cirno] That maid doesn't belong to you."));
            return false;
        }

        int index = addCommandCirno(player, false, true);
        if (index == -1) {
            player.sendSystemMessage(Component.literal(
                    "§c[Cirno] Limit reached ("
                            + CirnoStateAccess.MAX_COMMAND_CIRNOS + "/"
                            + CirnoStateAccess.MAX_COMMAND_CIRNOS
                            + ")! Remove one first with /cirno remove <index>."));
            return false;
        }

        if (index < 0) {
            player.sendSystemMessage(
                    Component.literal("§c[Cirno] Failed to create entity!"));
            return false;
        }

        // Hand the maid's data to the slot as its snapshot; spawnOrRestoreCommandCirnoAt
        // loads it into the new Cirno. Model/identity keys are stripped so she comes out
        // as Cirno instead of a copy of the maid.
        CompoundTag snapshot = maid.saveWithoutId(new CompoundTag());
        snapshot.remove("UUID");
        snapshot.remove("ModelId");
        snapshot.remove("IsYsmModel");
        snapshot.remove("YsmModelId");

        CompoundTag slot = CirnoStateAccess.getSlotTag(player, index);
        if (slot != null) {
            slot.put(CirnoStateAccess.SLOT_DATA, snapshot);
            CirnoStateAccess.putSlotTag(player, index, slot);
        }

        CirnoEntity cirno = spawnOrRestoreCommandCirnoAt(player, index, true);
        if (cirno == null) {
            CirnoStateAccess.clearSlotTag(player, index);
            player.sendSystemMessage(
                    Component.literal("§c[Cirno] Failed to create entity!"));
            return false;
        }

        cirno.moveTo(
                maid.getX(),
                maid.getY(),
                maid.getZ(),
                maid.getYRot(),
                0f
        );

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    maid.getX(),
                    maid.getY() + 1.0,
                    maid.getZ(),
                    40,
                    0.4,
                    0.6,
                    0.4,
                    0.05
            );
        }

        maid.discard();

        TelekinesisHandler.applyAttributeBuffs(player);
        TelekinesisHandler.enableTelekinesisMode(player);

        player.sendSystemMessage(Component.literal(
                "§b[Cirno] Slot " + index
                        + ": your maid was transformed into a Cirno! "
                        + "Attribute buffs and telekinesis granted."
        ));
        return true;
    }



    /**
     * Fully removes the Cirno tracked by this slot: discards her if she's live
     * (using the same pending-discard breadcrumb as storeCommandCirnoAt() when her
     * chunk isn't loaded, so she's never left behind as an orphaned, untracked
     * ghost), purges any copy stashed in a smart slab / photo matching her
     * identity, and frees the slot. Returns false (and touches nothing) if the
     * slot wasn't in use.
     */
    public static boolean removeCommandCirnoAt(Player player, int index) {
        CompoundTag slot = CirnoStateAccess.getSlotTag(player, index);
        if (slot == null) return false;

        if (slot.hasUUID(CirnoStateAccess.SLOT_UUID)) {
            UUID uuid = slot.getUUID(CirnoStateAccess.SLOT_UUID);
            CirnoEntity cirno = findLiveCommandCirnoAtPublic(player, index);

            if (cirno != null) {
                cirno.allowNextRemoval();
                cirno.discard();
            } else {
                // Chunk unloaded — she's still out there, not actually gone. Same
                // breadcrumb as storeCommandCirnoAt() so she's discarded for real
                // once her chunk loads, instead of surviving untracked forever.
                addPendingDiscard(player, uuid, index);
            }

            CirnoStateAccess.extractAndClearExternallyStoredCirnoForUuid(player, uuid);
        }

        CirnoStateAccess.clearSlotTag(player, index);

        // Attribute buffs (and active TK control) are a single shared grant, not
        // stacked per companion — curio and command Cirno both feed the same
        // modifiers. Only strip them once this was the last source left, or a
        // player with e.g. a curio AND a command Cirno would lose their buffs
        // the moment just one of the two was removed.
        if (!CirnoStateAccess.hasTKAccess(player)) {
            TelekinesisHandler.removeAttributeBuffs(player);
            TelekinesisHandler.clearTelekinesisControl(player);
        }
        return true;
    }



    /** Removes every command Cirno slot. Returns how many were actually in use. */
    public static int removeAllCommandCirno(Player player) {
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        int removed = 0;
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            if (removeCommandCirnoAt(player, i)) removed++;
        }
        return removed;
    }



    /** Short, human-readable status for one slot — used by {@code /cirno list}. */
    public static String describeCommandCirnoSlotPublic(Player player, int index) {
        CompoundTag slot = CirnoStateAccess.getSlotTag(player, index);
        if (slot == null) return "empty";

        boolean live = isCommandCirnoSlotLivePublic(player, index);
        boolean visiblePref = slot.getBoolean(CirnoStateAccess.SLOT_VISIBLE);

        if (live) {
            return "summoned (" + (visiblePref ? "visible" : "invisible") + ")";
        }

        return "stored (will be "
                + (visiblePref ? "visible" : "invisible")
                + " when summoned)";
    }



    /** Appends a deferred-discard breadcrumb; see {@link CirnoStateAccess#NBT_PENDING_DISCARDS}. */
    static void addPendingDiscard(Player player, UUID uuid, int slotIndex) {
        addPendingDiscard(player, uuid, slotIndex, false);
    }



    private static void addPendingDiscardOnly(Player player, UUID uuid, int slotIndex) {
        addPendingDiscard(player, uuid, slotIndex, true);
    }



    static void addPendingDiscard(
            Player player,
            UUID uuid,
            int slotIndex,
            boolean discardOnly
    ) {
        CompoundTag data = player.getPersistentData();
        net.minecraft.nbt.ListTag list =
                data.contains(CirnoStateAccess.NBT_PENDING_DISCARDS, 9)
                        ? data.getList(CirnoStateAccess.NBT_PENDING_DISCARDS, 10)
                        : new net.minecraft.nbt.ListTag();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag old = list.getCompound(i);

            if (old.hasUUID(CirnoStateAccess.SLOT_UUID)
                    && old.getUUID(CirnoStateAccess.SLOT_UUID).equals(uuid)
                    && old.getInt("Slot") == slotIndex
                    && old.getBoolean("DiscardOnly") == discardOnly) {
                return;
            }
        }

        CompoundTag entry = new CompoundTag();
        entry.putUUID(CirnoStateAccess.SLOT_UUID, uuid);
        entry.putInt("Slot", slotIndex);
        entry.putBoolean("DiscardOnly", discardOnly);

        list.add(entry);
        data.put(CirnoStateAccess.NBT_PENDING_DISCARDS, list);
    }



    /**
     * Called every tick by a live CirnoEntity to check whether she's the subject
     * of a deferred discard breadcrumb (her chunk wasn't loaded when a store/remove
     * was requested). If so, snapshots her into the target slot, discards her, and
     * consumes the breadcrumb. Safe to call unconditionally — it's a no-op when
     * there's nothing pending for this entity.
     */
    public static void consumePendingCommandDiscard(Player player, CirnoEntity cirno) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(CirnoStateAccess.NBT_PENDING_DISCARDS, 9)) return;

        net.minecraft.nbt.ListTag list =
                data.getList(CirnoStateAccess.NBT_PENDING_DISCARDS, 10);
        UUID uuid = cirno.getUUID();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);

            if (!entry.hasUUID(CirnoStateAccess.SLOT_UUID)
                    || !entry.getUUID(CirnoStateAccess.SLOT_UUID).equals(uuid)) {
                continue;
            }

            int slotIndex = entry.getInt("Slot");

            if (entry.getBoolean("DiscardOnly")) {
                list.remove(i);
                data.put(CirnoStateAccess.NBT_PENDING_DISCARDS, list);
                cirno.allowNextRemoval();
                cirno.discard();
                return;
            }

            CompoundTag slot =
                    CirnoStateAccess.getSlotTag(player, slotIndex);
            if (slot == null) slot = new CompoundTag();

            CompoundTag snapshot = new CompoundTag();
            cirno.addAdditionalSaveData(snapshot);

            slot.put(CirnoStateAccess.SLOT_DATA, snapshot);
            slot.remove(CirnoStateAccess.SLOT_UUID);
            slot.putBoolean(CirnoStateAccess.SLOT_STORED, true);

            CirnoStateAccess.putSlotTag(player, slotIndex, slot);

            list.remove(i);
            data.put(CirnoStateAccess.NBT_PENDING_DISCARDS, list);

            cirno.allowNextRemoval();
            cirno.discard();
            return;
        }
    }



    /**
     * Counts down each command slot's death-revive timer (the per-slot equivalent
     * of tickCirnoRevive). Unlike the Curios companion, a command slot has no
     * "equip" event to auto-respawn on, so this only clears the timer once it
     * elapses — the player then re-summons manually via /cirno or the Quick-Select
     * panel, at which point spawnOrRestoreCommandCirnoAt's own cooldown check
     * (now satisfied) lets it through.
     */
    static void tickCommandCirnoRevive(Player player) {
        if (!CirnoStateAccess.hasCommandCirno(player)) return;

        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot =
                    CirnoStateAccess.getSlotTag(player, i);
            if (slot == null) continue;

            int ticksLeft =
                    slot.getInt(CirnoStateAccess.SLOT_REVIVE_TICKS_LEFT);
            if (ticksLeft <= 0) continue;

            ticksLeft--;

            if (ticksLeft > 0) {
                slot.putInt(
                        CirnoStateAccess.SLOT_REVIVE_TICKS_LEFT,
                        ticksLeft
                );
            } else {
                slot.remove(CirnoStateAccess.SLOT_REVIVE_TICKS_LEFT);

                player.displayClientMessage(
                        Component.literal(
                                "§b[Cirno] Slot " + i
                                        + " is ready to summon again."
                        ),
                        true
                );
            }

            CirnoStateAccess.putSlotTag(player, i, slot);
        }
    }



    // ── Quick-Select screen data ──────────────────────────────────────────────

    /**
     * Builds a {@link net.zhaiji.cirno.network.client.packet.SlotSyncPacket} reflecting
     * the current live state of all slots for the given player.
     * Called server-side in response to {@link net.zhaiji.cirno.network.server.packet.RequestSlotSyncPacket}
     * and after any {@link net.zhaiji.cirno.network.server.packet.SlotActionPacket}.
     */
    public static net.zhaiji.cirno.network.client.packet.SlotSyncPacket buildSlotSyncPacket(Player player) {
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);

        // FIX: this must be a normal local variable.
        boolean hasCurio = CirnoStateAccess.hasCurio(player);

        java.util.List<net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotInfo> slots =
                new java.util.ArrayList<>();

        // Index 0: curio slot
        if (hasCurio) {
            ItemStack curioStack = CirnoStateAccess.getCirnoStack(player);

            boolean hidden =
                    curioStack == null
                            || CirnoStateAccess.getCompanionHidden(curioStack);

            net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotState curioState =
                    hidden
                            ? net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotState.STORED
                            : net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotState.SUMMONED;

            // Try to find the curio's live entity for model info
            String curioModelId = "";
            String curioYsmModelId = "";

            if (curioStack != null) {
                UUID uuid = CirnoStateAccess.getCompanionUUID(curioStack);
                CirnoEntity liveCurio = null;

                if (uuid != null && player.getServer() != null) {
                    outer:
                    for (ServerLevel lvl : player.getServer().getAllLevels()) {
                        net.minecraft.world.entity.Entity e =
                                lvl.getEntity(uuid);

                        if (e instanceof CirnoEntity c) {
                            liveCurio = c;
                            break outer;
                        }
                    }
                }

                if (liveCurio != null) {
                    curioModelId =
                            CirnoStateAccess.getCirnoModelId(liveCurio);
                    curioYsmModelId =
                            CirnoStateAccess.getCirnoYsmModelId(liveCurio);
                } else {
                    // Stored — try snapshot saved on the item stack
                    CompoundTag snapshot =
                            CirnoStateAccess.getCompanionSavedData(curioStack);

                    if (snapshot != null) {
                        curioModelId =
                                snapshot.getString("ModelId");

                        curioYsmModelId =
                                snapshot.getBoolean("IsYsmModel")
                                        ? snapshot.getString("YsmModelId")
                                        : "";
                    }
                }
            }

            slots.add(
                    new net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotInfo(
                            0,
                            true,
                            curioState,
                            curioModelId,
                            curioYsmModelId
                    )
            );
        }

        // Indices 1–4: command slots
        for (int i = 1;
             i <= CirnoStateAccess.MAX_COMMAND_CIRNOS;
             i++) {

            boolean used =
                    isCommandCirnoSlotUsedPublic(player, i);

            if (!used) {
                slots.add(
                        new net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotInfo(
                                i,
                                false,
                                net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotState.EMPTY
                        )
                );
            } else {
                boolean live =
                        isCommandCirnoSlotLivePublic(player, i);

                CirnoEntity liveCirno =
                        findLiveCommandCirnoAtPublic(player, i);

                String modelId;
                String ysmModelId;

                if (liveCirno != null) {
                    modelId =
                            CirnoStateAccess.getCirnoModelId(liveCirno);

                    ysmModelId =
                            CirnoStateAccess.getCirnoYsmModelId(liveCirno);
                } else {
                    // Stored — read from snapshot NBT
                    CompoundTag slot =
                            CirnoStateAccess.getSlotTag(player, i);

                    CompoundTag snapshot =
                            (slot != null
                                    && slot.contains(
                                    CirnoStateAccess.SLOT_DATA,
                                    10))
                                    ? slot.getCompound(
                                    CirnoStateAccess.SLOT_DATA)
                                    : null;

                    modelId =
                            snapshot != null
                                    ? snapshot.getString("ModelId")
                                    : "";

                    ysmModelId =
                            (snapshot != null
                                    && snapshot.getBoolean("IsYsmModel"))
                                    ? snapshot.getString("YsmModelId")
                                    : "";
                }

                slots.add(
                        new net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotInfo(
                                i,
                                false,
                                live
                                        ? net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotState.SUMMONED
                                        : net.zhaiji.cirno.network.client.packet.SlotSyncPacket.SlotState.STORED,
                                modelId,
                                ysmModelId
                        )
                );
            }
        }

        // FIX: use the local variable, not CirnoStateAccess.hasCurio.
        return new net.zhaiji.cirno.network.client.packet.SlotSyncPacket(
                hasCurio,
                slots
        );
    }
}