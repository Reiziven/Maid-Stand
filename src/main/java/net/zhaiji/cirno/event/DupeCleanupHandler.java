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

import static net.zhaiji.cirno.event.CirnoStateAccess.EQUIPMENT_SLOTS;

public class DupeCleanupHandler {

    /**
     * Periodically clears stray Cirno storage items (smart slab / photo) belonging
     * to this player while a live Cirno already exists, across ALL loaded dimensions.
     *
     * Current dimension: the player's loaded view area (only loaded data, no chunk
     * forcing).
     * Other dimensions: iterates all loaded entities and block entities in those levels
     * — still only what's already ticking, no chunk loading forced.
     *
     * Smart slabs become the empty variant; photos are removed.
     * The current dimension is scanned every {@link CirnoStateAccess#DUPE_SCAN_INTERVAL_TICKS} ticks
     * (1 s) per player; other dimensions every {@link CirnoStateAccess#DUPE_SCAN_OTHER_DIM_EVERY}
     * of those scans (the player can't touch anything there, so it isn't urgent).
     */
     static void tickDupeCleanup(Player player) {
        // Pure tickCount arithmetic: no per-tick NBT write on every player, and the
        // player's entity id staggers the scans so they don't all land on the same tick.
        int phase = player.tickCount + player.getId();
        if (phase % CirnoStateAccess.DUPE_SCAN_INTERVAL_TICKS != 0) return;
        boolean scanOtherDimensions = (phase / CirnoStateAccess.DUPE_SCAN_INTERVAL_TICKS) % CirnoStateAccess.DUPE_SCAN_OTHER_DIM_EVERY == 0;

        // A summon requested while its storage item was in an unloaded chunk is
        // resumed only after that exact item becomes reachable and is consumed.
        // Drop deferred-restore records written by older builds. Summoning no
        // longer waits for an unloaded storage item to become reachable.
        player.getPersistentData().remove(CirnoStateAccess.NBT_PENDING_STORAGE_RESTORES);

        // Collect UUIDs of Cirnos that are genuinely alive right now (command
        // slots + curio companion). Only a storage item whose stored entity UUID
        // matches one of THESE is a true stray duplicate. With multiple Cirnos,
        // a legitimately-stored smart slab/photo for a different (stored) slot
        // must never be wiped just because another Cirno is still live.
        List<UUID> liveUuids = new ArrayList<>();
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CirnoEntity live = CommandCirnoManager.findLiveCommandCirnoAtPublic(player, i);
            if (live != null) liveUuids.add(live.getUUID());
        }
        // Storage identity UUIDs whose items could not be reached when fallback
        // summon was requested. Treat those items as duplicates when their chunk
        // enters this loaded-area scan; this never loads a chunk itself.
        CompoundTag pendingCleanup = player.getPersistentData();
        if (pendingCleanup.contains(CirnoStateAccess.NBT_PENDING_STORAGE_UUID_CLEANUP, 9)) {
            net.minecraft.nbt.ListTag pendingIds = pendingCleanup.getList(CirnoStateAccess.NBT_PENDING_STORAGE_UUID_CLEANUP, 10);
            for (int i = 0; i < pendingIds.size(); i++) {
                CompoundTag entry = pendingIds.getCompound(i);
                if (entry.hasUUID("UUID")) liveUuids.add(entry.getUUID("UUID"));
            }
        }
        ItemStack cirnoStack = CirnoStateAccess.getCirnoStack(player);
        if (cirnoStack != null && !CirnoStateAccess.getCompanionHidden(cirnoStack)) {
            UUID curiosUuid = CirnoStateAccess.getCompanionUUID(cirnoStack);
            if (curiosUuid != null) liveUuids.add(curiosUuid);
        }
        if (liveUuids.isEmpty()) return;

        UUID ownerUUID = player.getUUID();

        // ── Player's own inventory (hotbar, main, offhand, armor) ────────────
        // Cheapest check — fixed-size lists, no world queries. Catches the case
        // where the player picked up a stray storage item directly.
        scanInventoryList(player.getInventory().items, ownerUUID, liveUuids);
        scanInventoryList(player.getInventory().offhand, ownerUUID, liveUuids);
        scanInventoryList(player.getInventory().armor, ownerUUID, liveUuids);

        // ── Current dimension: bounded scan around the player ─────────────────
        int loadedAreaRadius = CirnoStorageHandler.nearbyChunkRadius(player) * 16;
        scanLevelBounded(player.level(), ownerUUID, liveUuids,
                player.getBoundingBox().inflate(loadedAreaRadius),
                player.blockPosition(), loadedAreaRadius);

        // ── Other dimensions: walk everything that's already loaded ───────────
        if (scanOtherDimensions && player.getServer() != null) {
            for (ServerLevel other : player.getServer().getAllLevels()) {
                if (other == player.level()) continue;
                scanLevelFull(other, ownerUUID, liveUuids);
            }
        }
    }



    /**
     * True if this stack is a Cirno storage item for {@code ownerUUID} whose
     * stored entity UUID matches any entry in {@code liveUuids}.
     */
    private static boolean isStrayCirnoStorageItem(ItemStack stack, UUID ownerUUID, List<UUID> liveUuids) {
        if (!CirnoStateAccess.isCirnoStorageItem(stack, ownerUUID)) return false;
        CompoundTag maidData = stack.getTag().getCompound("MaidInfo");
        if (!maidData.hasUUID("UUID")) return false;
        UUID stored = maidData.getUUID("UUID");
        for (UUID live : liveUuids) {
            if (live.equals(stored)) return true;
        }
        return false;
    }



     static void markStorageUuidForCleanup(Player player, @Nullable UUID uuid) {
        if (uuid == null) return;
        CompoundTag data = player.getPersistentData();
        net.minecraft.nbt.ListTag pending = data.contains(CirnoStateAccess.NBT_PENDING_STORAGE_UUID_CLEANUP, 9)
                ? data.getList(CirnoStateAccess.NBT_PENDING_STORAGE_UUID_CLEANUP, 10) : new net.minecraft.nbt.ListTag();
        for (int i = 0; i < pending.size(); i++) {
            CompoundTag entry = pending.getCompound(i);
            if (entry.hasUUID("UUID") && entry.getUUID("UUID").equals(uuid)) return;
        }
        CompoundTag entry = new CompoundTag();
        entry.putUUID("UUID", uuid);
        pending.add(entry);
        data.put(CirnoStateAccess.NBT_PENDING_STORAGE_UUID_CLEANUP, pending);
    }



    /** Bounded scan for the player's current dimension. */
    private static void scanLevelBounded(Level level, UUID ownerUUID, List<UUID> liveUuids,
                                         AABB scanBox, BlockPos center, int r) {
        // Single entity query — dispatch by type inside to avoid scanning the same
        // AABB twice (ItemEntity is a subtype of Entity, so two separate queries
        // would visit dropped items twice).
        for (Entity entity : level.getEntitiesOfClass(Entity.class, scanBox)) {
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                    itemEntity.setItem(CirnoStateAccess.emptyStorageReplacement(stack));
                }
            } else {
                scanEntityInventory(entity, ownerUUID, liveUuids);
            }
        }

        // Block-entity containers.
        //
        // This used to probe every block position in the (2r+1)^3 cube with
        // Level#getBlockEntity — ~2.1 million calls at r=64, every scan — which is what
        // caused the recurring tick-time spike. Instead, ask each already-loaded chunk that
        // overlaps the cube for the block entities it actually holds (a handful) and
        // range-check those. getChunkNow never loads or generates chunks (getBlockEntity
        // could, via getChunkAt), which is what the "only loaded data" note above promises.
        BoundingBox cube = new BoundingBox(
                center.getX() - r, center.getY() - r, center.getZ() - r,
                center.getX() + r, center.getY() + r, center.getZ() + r);
        int minChunkX = (center.getX() - r) >> 4;
        int maxChunkX = (center.getX() + r) >> 4;
        int minChunkZ = (center.getZ() - r) >> 4;
        int maxChunkZ = (center.getZ() + r) >> 4;
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk != null) scanChunkContainers(chunk, ownerUUID, liveUuids, cube);
            }
        }
    }



    /**
     * Replaces stray Cirno storage items in every {@link Container} block entity of
     * {@code chunk}, optionally restricted to block entities inside {@code range}
     * (inclusive, like the old BlockPos.betweenClosed cube). Strays are collected first
     * and modified afterwards so setChanged() side effects can never disturb the
     * chunk's block-entity map mid-iteration.
     */
    private static void scanChunkContainers(LevelChunk chunk, UUID ownerUUID, List<UUID> liveUuids,
                                            @Nullable BoundingBox range) {
        List<Container> strays = null;
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (!(be instanceof Container container)) continue;
            if (range != null && !range.isInside(be.getBlockPos())) continue;
            if (!containsStray(container, ownerUUID, liveUuids)) continue;
            if (strays == null) strays = new ArrayList<>();
            strays.add(container);
        }
        if (strays == null) return;
        for (Container container : strays) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                    container.setItem(i, CirnoStateAccess.emptyStorageReplacement(stack));
                }
            }
            container.setChanged();
        }

        // Forge item-handler capabilities cover inventories exposed by modded
        // block entities that do not implement vanilla Container.
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof Container || (range != null && !range.isInside(be.getBlockPos()))) continue;
            be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler ->
                    scanItemHandler(handler, ownerUUID, liveUuids));
        }
    }



    private static void scanItemHandler(IItemHandler handler, UUID ownerUUID, List<UUID> liveUuids) {
        for (int i = 0; i < handler.getSlots(); i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                replaceItemHandlerStack(handler, i, stack, CirnoStateAccess.emptyStorageReplacement(stack));
            }
        }
    }



     static void replaceItemHandlerStack(IItemHandler handler, int slot, ItemStack oldStack,
                                                ItemStack replacement) {
        if (handler instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, replacement);
            return;
        }
        // Most capability handlers are modifiable. For read/extract/insert-only
        // handlers, remove the old stack and attempt to put the replacement back.
        ItemStack extracted = handler.extractItem(slot, oldStack.getCount(), false);
        if (extracted.isEmpty()) return;
        if (!replacement.isEmpty()) {
            ItemStack remainder = handler.insertItem(slot, replacement, false);
            if (!remainder.isEmpty()) {
                // Preserve the original if this handler rejects the empty slab.
                handler.insertItem(slot, extracted, false);
            }
        }
    }



    private static boolean containsStray(Container container, UUID ownerUUID, List<UUID> liveUuids) {
        for (int i = 0, n = container.getContainerSize(); i < n; i++) {
            if (isStrayCirnoStorageItem(container.getItem(i), ownerUUID, liveUuids)) return true;
        }
        return false;
    }



    /**
     * Full scan of a dimension's already-loaded entities and block entities.
     * Used for dimensions the player isn't currently in — only touches what's
     * already ticking, no chunk loading is forced.
     */
    private static void scanLevelFull(ServerLevel level, UUID ownerUUID, List<UUID> liveUuids) {
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof ItemEntity itemEntity) {
                ItemStack stack = itemEntity.getItem();
                if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                    itemEntity.setItem(CirnoStateAccess.emptyStorageReplacement(stack));
                }
            } else {
                scanEntityInventory(entity, ownerUUID, liveUuids);
            }
        }

        // Collect all chunk positions reachable by any online player in this
        // dimension, plus any force-loaded chunks (e.g. chunk loaders).
        // This covers everything a player could actually interact with — no
        // reflection needed and no private API accessed.
        Set<ChunkPos> positions = new HashSet<>();
        int viewDistance = level.getServer().getPlayerList().getViewDistance() + 1;
        for (ServerPlayer p : level.players()) {
            ChunkPos center = p.chunkPosition();
            for (int dx = -viewDistance; dx <= viewDistance; dx++) {
                for (int dz = -viewDistance; dz <= viewDistance; dz++) {
                    positions.add(new ChunkPos(center.x + dx, center.z + dz));
                }
            }
        }
        level.getForcedChunks().forEach(packed ->
                positions.add(new ChunkPos(packed)));

        for (ChunkPos chunkPos : positions) {
            net.minecraft.world.level.chunk.LevelChunk chunk =
                    level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
            if (chunk == null) continue;
            scanChunkContainers(chunk, ownerUUID, liveUuids, null);
        }
    }



    /** Scans a flat inventory list (main/offhand/armor) for stray storage items. */
    private static void scanInventoryList(List<ItemStack> list, UUID ownerUUID, List<UUID> liveUuids) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                list.set(i, CirnoStateAccess.emptyStorageReplacement(stack));
            }
        }
    }



    /** Checks item frames and non-player living entity equipment for stray storage items. */
    private static void scanEntityInventory(Entity entity, UUID ownerUUID, List<UUID> liveUuids) {
        if (entity instanceof ItemFrame itemFrame) {
            ItemStack stack = itemFrame.getItem();
            if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                itemFrame.setItem(CirnoStateAccess.emptyStorageReplacement(stack));
            }
        } else if (entity instanceof LivingEntity living && !(living instanceof Player)) {
            for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
                ItemStack stack = living.getItemBySlot(slot);
                if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                    living.setItemSlot(slot, CirnoStateAccess.emptyStorageReplacement(stack));
                }
            }
        }

        // Horse, donkey, and mule inventories are vanilla entity inventories,
        // not Container entities. Read them directly so they work even before
        // a player opens the animal's inventory menu.
        if (!(entity instanceof Container) && !(entity instanceof Player) && !(entity instanceof ItemFrame)) {
            entity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler ->
                    scanItemHandler(handler, ownerUUID, liveUuids));
        }
    }
}
