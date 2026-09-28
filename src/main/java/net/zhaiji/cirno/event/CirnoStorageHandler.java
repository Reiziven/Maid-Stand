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

public class CirnoStorageHandler {

    private static void addIndexedListSlots(List<CirnoStateAccess.CirnoSlot> slots, List<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            slots.add(new CirnoStateAccess.CirnoSlot(() -> list.get(index), replacement -> list.set(index, replacement)));
        }
    }



    private static void addContainerSlots(List<CirnoStateAccess.CirnoSlot> slots, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            int index = i;
            slots.add(new CirnoStateAccess.CirnoSlot(() -> container.getItem(index), replacement -> {
                container.setItem(index, replacement);
                container.setChanged();
            }));
        }
    }



    /**
     * How many chunks (each side, from the player's own chunk) count as "the
     * loaded area" for on-demand storage-item lookups — matches the server's
     * configured view distance, i.e. every chunk that's actually loaded and
     * interactable, not an arbitrary small box. Falls back to a sane default
     * if a view distance can't be read for some reason.
     */
    static int nearbyChunkRadius(Player player) {
        if (player instanceof ServerPlayer sp && sp.getServer() != null) {
            return sp.getServer().getPlayerList().getViewDistance() + 1;
        }
        return 8;
    }



    /**
     * Every chunk within {@link #nearbyChunkRadius} of the player that is
     * ALREADY loaded. getChunkNow never loads or generates a chunk itself
     * (unlike getBlockEntity/getChunkAt), so this can never force chunk
     * loading — it only ever sees what's already ticking.
     */
    private static List<LevelChunk> loadedChunksAroundPlayer(Player player) {
        List<LevelChunk> chunks = new ArrayList<>();
        Level level = player.level();
        int r = nearbyChunkRadius(player);
        ChunkPos center = player.chunkPosition();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                if (chunk != null) chunks.add(chunk);
            }
        }
        return chunks;
    }



    /**
     * Every placed storage block (chest, barrel, shulker box, hopper,
     * dispenser/dropper, furnace, brewing stand, ...) and inventory-carrying
     * entity (minecart chest, chest boat, laden llama/donkey, ...) anywhere in
     * the player's currently loaded chunks — not just a small fixed-size cube
     * around them. Storage items get carried around, placed across a build, or
     * left in a chest on the far side of a base, and all of that is still
     * fully "loaded and interactable" as far as the player is concerned, so
     * on-demand lookups (H-toggle, /cirno, right-click extraction) now cover
     * the same ground the periodic dupe-cleanup sweep already does: every
     * already-loaded chunk's block entities via
     * {@link LevelChunk#getBlockEntities()} (a handful of lookups, never a
     * per-position probe), rather than being capped at
     * {@link #NEARBY_STORAGE_RADIUS} blocks. Still only what's already
     * ticking — no chunk loading is ever forced — and this only runs at
     * explicit call sites, never per-tick, so it can't become a background
     * world scan.
     */
    private static List<Container> collectNearbyContainers(Player player) {
        List<Container> containers = new ArrayList<>();

        for (LevelChunk chunk : loadedChunksAroundPlayer(player)) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof Container container) {
                    containers.add(container);
                }
            }
        }

        double r = nearbyChunkRadius(player) * 16.0;
        for (Entity entity : player.level().getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(r))) {
            if (entity instanceof Container container) {
                containers.add(container);
            }
        }

        return containers;
    }



    private static void addItemHandlerSlots(List<CirnoStateAccess.CirnoSlot> slots, IItemHandler handler) {
        for (int i = 0; i < handler.getSlots(); i++) {
            int index = i;
            slots.add(new CirnoStateAccess.CirnoSlot(() -> handler.getStackInSlot(index), replacement ->
                    DupeCleanupHandler.replaceItemHandlerStack(handler, index, handler.getStackInSlot(index), replacement)));
        }
    }



    /**
     * Every entity in the player's loaded chunks that can hold a
     * Cirno-storage item directly, outside a {@link Container}: an item
     * frame's single displayed item, and a living entity's worn/held
     * equipment (a mob can pick up a dropped photo/smart slab and wear or
     * wield it — a smart slab is a block item, so it can even end up in a
     * mob's helmet slot). Other players are skipped here on purpose: their
     * own equipment shouldn't be silently cleared by someone else's cleanup,
     * and the acting player's own equipment is already covered by
     * {@link #collectCirnoSlots} via their inventory/offhand/armor lists.
     * Same loaded-area, on-demand-only shape as {@link #collectNearbyContainers}.
     */
    private static void addNearbyEntityInventorySlots(List<CirnoStateAccess.CirnoSlot> slots, Player player) {
        double r = nearbyChunkRadius(player) * 16.0;
        for (Entity entity : player.level().getEntitiesOfClass(
                Entity.class, player.getBoundingBox().inflate(r))) {
            if (entity instanceof ItemFrame itemFrame) {
                slots.add(new CirnoStateAccess.CirnoSlot(itemFrame::getItem, itemFrame::setItem));
            } else if (entity instanceof LivingEntity living && !(living instanceof Player)) {
                for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                    slots.add(new CirnoStateAccess.CirnoSlot(
                            () -> living.getItemBySlot(equipmentSlot),
                            replacement -> living.setItemSlot(equipmentSlot, replacement)));
                }
            }

            if (!(entity instanceof Container) && !(entity instanceof Player)
                    && !(entity instanceof ItemFrame)) {
                entity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler ->
                        addItemHandlerSlots(slots, handler));
            }
        }
    }



    /**
     * Every location a Cirno-storage item could currently be sitting, reachable
     * from the player right now: main inventory, offhand, armor, ender chest,
     * whatever container menu is open, any nearby placed container, item frame,
     * or living entity's equipment, and any dropped item nearby. Used identically
     * by both the read-only "is she stored somewhere?" check and the "find her
     * and clear it" extraction, so the two can never see a different picture of
     * the world from each other.
     */
    static List<CirnoStateAccess.CirnoSlot> collectCirnoSlots(Player player) {
        List<CirnoStateAccess.CirnoSlot> slots = new ArrayList<>();

        addIndexedListSlots(slots, player.getInventory().items);
        addIndexedListSlots(slots, player.getInventory().offhand);
        addIndexedListSlots(slots, player.getInventory().armor);

        if (player.containerMenu != null) {
            for (Slot slot : player.containerMenu.slots) {
                slots.add(new CirnoStateAccess.CirnoSlot(slot::getItem, slot::set));
            }
        }

        addContainerSlots(slots, player.getEnderChestInventory());

        for (Container container : collectNearbyContainers(player)) {
            addContainerSlots(slots, container);
        }

        for (LevelChunk chunk : loadedChunksAroundPlayer(player)) {
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (be instanceof Container) continue;
                be.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler ->
                        addItemHandlerSlots(slots, handler));
            }
        }

        addNearbyEntityInventorySlots(slots, player);

        UUID ownerUUID = player.getUUID();
        List<ItemEntity> nearbyItems = player.level().getEntitiesOfClass(
                ItemEntity.class, player.getBoundingBox().inflate(nearbyChunkRadius(player) * 16.0),
                e -> CirnoStateAccess.isCirnoStorageItem(e.getItem(), ownerUUID));
        for (ItemEntity itemEntity : nearbyItems) {
            slots.add(new CirnoStateAccess.CirnoSlot(itemEntity::getItem, replacement -> {
                if (replacement.isEmpty()) {
                    itemEntity.discard();
                } else {
                    itemEntity.setItem(replacement);
                }
            }));
        }

        return slots;
    }



    /**
     * Returns true if Cirno is currently stored in a smart slab or photo item
     * anywhere reachable from the player right now — inventory, offhand, armor,
     * ender chest, whatever container menu is open, any nearby chest/barrel/
     * shulker/other placed container or inventory entity, or a dropped item.
     */
    static boolean isStoredInExternalItem(Player player) {
        UUID ownerUUID = player.getUUID();
        for (CirnoStateAccess.CirnoSlot slot : collectCirnoSlots(player)) {
            if (CirnoStateAccess.isCirnoStorageItem(slot.getter().get(), ownerUUID)) return true;
        }
        return false;
    }



    /**
     * Opportunistic, event-driven cleanup for storage locations our bounded
     * on-demand scans can't reach proactively — the main real-world case being a
     * chested horse's (llama/donkey/mule) saddlebags, which don't implement the
     * public {@link Container} interface the rest of this file relies on (unlike
     * chest minecarts and chest boats, which do and are already covered). Rather
     * than reaching into that private inventory via reflection — fragile, and
     * liable to silently break across Minecraft/Forge versions — this piggybacks
     * on the fact that whatever menu the game opens for ANY container exposes its
     * slots publicly through {@link net.minecraft.world.inventory.AbstractContainerMenu#slots},
     * regardless of what the underlying container actually is. So instead of
     * scanning proactively, we just check the menu the instant the player opens
     * it — an event, not a scan, so there's nothing to optimize away.
     * <p>
     * Only acts when Cirno is ALSO genuinely alive elsewhere right now: a smart
     * slab or photo tagged as this player's Cirno can only legitimately exist
     * unpurged while she's stored (not live), so finding one while she's alive is
     * proof it's a stray duplicate left behind by something outside our on-demand
     * scans — never a copy we'd otherwise want to keep. That keeps this handler
     * from ever surprising the player by touching an item they didn't ask about.
     */
    public static void handlerContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        // Collect the identity of every Cirno that's genuinely alive elsewhere right
        // now (each command slot that's live, plus the Curios companion if any).
        // Only an item matching one of THESE specific UUIDs is a stray duplicate —
        // with multiple slots possible, a legitimately-stored slot's own backup
        // item must never be swept up just because a different slot is alive.
        List<UUID> liveElsewhereUUIDs = new ArrayList<>();
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CirnoEntity live = CommandCirnoManager.findLiveCommandCirnoAtPublic(player, i);
            if (live != null) liveElsewhereUUIDs.add(live.getUUID());
        }
        ItemStack cirnoStack = CirnoStateAccess.getCirnoStack(player);
        if (cirnoStack != null && !CirnoStateAccess.getCompanionHidden(cirnoStack)) {
            UUID curiosUuid = CirnoStateAccess.getCompanionUUID(cirnoStack);
            if (curiosUuid != null) liveElsewhereUUIDs.add(curiosUuid);
        }
        if (liveElsewhereUUIDs.isEmpty()) return;

        UUID ownerUUID = player.getUUID();
        for (Slot slot : event.getContainer().slots) {
            for (UUID uuid : liveElsewhereUUIDs) {
                if (CirnoStateAccess.tryExtractCirnoFromStackForUuid(slot.getItem(), ownerUUID, uuid, slot::set) != null) break;
            }
        }
    }

}