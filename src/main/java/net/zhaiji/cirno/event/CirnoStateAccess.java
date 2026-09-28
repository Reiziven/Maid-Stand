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

public class CirnoStateAccess {

    // ── NBT keys stored on the ItemStack ─────────────────────────────────────
    /** UUID of the companion entity linked to this specific item stack. */
    static final String ITEM_NBT_COMPANION_UUID   = "CirnoCompanionUUID";


    /** Whether the companion is hidden for this item stack. */
    static final String ITEM_NBT_COMPANION_HIDDEN = "CirnoCompanionHidden";


    /**
     * Whether the hidden state was set specifically by the player's H key press.
     * Only set by CompanionLifecycleHandler.toggleCompanionVisibility(). If this is false but HIDDEN is true,
     * Cirno was stored by an external system (smart slab, camera, etc.) and H-to-show
     * must be blocked to avoid spawning a duplicate alongside the stored entity.
     */
    static final String ITEM_NBT_HIDDEN_BY_PLAYER = "CirnoHiddenByPlayer";


    /**
     * Set (true) whenever HIDDEN was caused by an external capture (smart slab /
     * photo / film) or by a real tombstone death (cirnoSpawnTombstoneOnDeath = true),
     * as opposed to the normal auto-revive-after-a-timer death or a plain H-key hide.
     * While this is true, every revival path (summonCurioCompanion,
     * CompanionLifecycleHandler.toggleCompanionVisibility, spawnCompanion) must find and consume an actual
     * matching storage item before respawning her — none of them are allowed to
     * hand back a free blank replacement just because the hidden flag is off or a
     * cooldown reached zero. Cleared the moment a real revival succeeds.
     */
    static final String ITEM_NBT_REQUIRES_ITEM_REVIVE = "CirnoRequiresItemRevive";


    /** Full entity NBT snapshot of Cirno, used to restore her inventory across dimension changes. */
    static final String ITEM_NBT_COMPANION_DATA   = "CirnoCompanionData";



    // ── NBT keys stored on player PersistentData (transient per-session state) ──
    static final String NBT_SHIELD_TICKS_LEFT       = "CirnoShieldTicksLeft";


    static final String NBT_SHIELD_COOLDOWN         = "CirnoShieldCooldown";


    static final String NBT_TK_MODE_ACTIVE          = "CirnoTKModeActive";


    static final String NBT_TK_CONTROLLED_UUID      = "CirnoTKControlledUUID";


    static final String NBT_TK_CONTROL_TICKS_LEFT   = "CirnoTKControlTicksLeft";


    static final String NBT_TK_CONTROL_COOLDOWN     = "CirnoTKControlCooldown";


    static final String NBT_TK_HOLD_ACTIVE          = "CirnoTKHoldActive";


    static final String NBT_CIRNO_REVIVE_TICKS_LEFT = "CirnoReviveTicksLeft";

    /** Game-time (ticks) before which the curio Cirno can't be revived from a film/photo/slab
     *  after a real (tombstone-mode) death. Absolute timestamp, so it keeps running offline
     *  and needs no per-tick countdown. */
    static final String NBT_CIRNO_REVIVE_READY_AT   = "CirnoReviveReadyAt";


    static final String NBT_LAST_KNOWN_CIRNO_DATA = "CirnoLastKnownData";


    static final String NBT_LAST_KNOWN_CIRNO_TIME = "CirnoLastKnownTime";


    static final String NBT_TLM_FILM_REVIVAL = "CirnoTlmFilmRevival";
    /**
     * Full copy of the film's stored maid data, stashed on the temporary TLM maid so
     * handlerEntityJoinLevel can rebuild Cirno from it. A plain EntityMaid drops
     * IsCirnoCompanion / OwnerPlayerUUID when it reads or saves its data, so this is
     * the only place those keys survive until the maid joins the level.
     */
    static final String NBT_TLM_FILM_DATA = "CirnoTlmFilmData";


    /** Last chunk for each live Cirno; lets a dimension transfer find an unloaded entity. */
    static final String NBT_CIRNO_LAST_LOCATIONS = "CirnoLastLocations";



    // ── Command-mode multi-Cirno slots (player PersistentData) ─────────────────
    /**
     * Command-spawned (no Curios item) Cirnos now live in up to
     * {@link #MAX_COMMAND_CIRNOS} fixed, independently-addressable slots instead
     * of a single UUID. Each slot is its own CompoundTag under one of
     * {@link #SLOT_KEYS}, containing:
     *   Uuid    — live/last-known entity UUID (present while spawned, or kept
     *             around after an external capture so we can find her again)
     *   Stored  — true while not currently spawned in the world
     *   Data    — full entity NBT snapshot used to restore her (internal store)
     *   Visible — visibility preference applied on next restore
     * A slot is "in use" iff its CompoundTag key is present at all; /cirno remove
     * deletes the key outright to free the slot back up.
     */
    public static final int MAX_COMMAND_CIRNOS = 4;


    static final String[] SLOT_KEYS = {
            "CirnoSlot1", "CirnoSlot2", "CirnoSlot3", "CirnoSlot4"
    };


    static final String SLOT_UUID    = "Uuid";


    static final String SLOT_STORED  = "Stored";


    static final String SLOT_DATA    = "Data";


    static final String SLOT_VISIBLE = "Visible";


    static final String SLOT_RESTORE_ON_LOGIN = "RestoreOnLogin";


    /** True while a command Cirno has died for real (cirnoSpawnTombstoneOnDeath = true)
     *  and hasn't been recovered from an actual TLM item (tombstone/photo/film) yet —
     *  SLOT_DATA is absent in this state, so spawnOrRestoreCommandCirnoAt must refuse
     *  to hand back a fresh blank replacement while this is set. */
    static final String SLOT_DEAD = "Dead";


    /** Ticks left before a command Cirno that died with cirnoSpawnTombstoneOnDeath =
     *  false can be summoned again — the per-slot equivalent of NBT_CIRNO_REVIVE_TICKS_LEFT. */
    static final String SLOT_REVIVE_TICKS_LEFT = "ReviveTicksLeft";

    /** Per-slot equivalent of NBT_CIRNO_REVIVE_READY_AT (tombstone-mode death). */
    static final String SLOT_REVIVE_READY_AT = "ReviveReadyAt";



    /**
     * Deferred discards for command Cirnos whose chunk wasn't loaded at the time
     * we tried to store/remove them — a ListTag of {@code {Uuid, Slot}} entries.
     * A list (rather than the old single UUID) because more than one slot can be
     * mid-unload at once now that there can be up to 3 of her.
     */
    static final String NBT_PENDING_DISCARDS = "CirnoPendingDiscards";


    static final String NBT_PENDING_STORAGE_RESTORES = "CirnoPendingStorageRestores";


    /**
     * Ticks left in the post-login grace window during which the Curious Cirno's
     * tracked entity is still being looked for before we conclude she is gone and
     * spawn a replacement. Entities in freshly (re)loading chunks are not
     * resolvable at PlayerLoggedInEvent time, so replacing her immediately is what
     * used to duplicate an already-summoned Cirno on every join.
     */
    static final String NBT_LOGIN_VERIFY_TICKS = "CirnoLoginVerifyTicks";


    static final String NBT_PENDING_STORAGE_UUID_CLEANUP = "CirnoPendingStorageUuidCleanup";



    // Legacy (pre-multi-Cirno) single-slot keys — migrated into slot 1 the first
    // time this player's command-Cirno state is touched after updating.
    static final String LEGACY_UUID    = "CirnoCommandUUID";


    static final String LEGACY_DATA    = "CirnoCommandData";


    static final String LEGACY_VISIBLE = "CirnoCommandVisible";


    static final String LEGACY_STORED  = "CirnoCommandStored";


    static final String LEGACY_PENDING_DISCARD_UUID = "CirnoPendingDiscardUUID";



    // Attribute modifier UUIDs (stable, unique per modifier)
    static final UUID ATTACK_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-ab12-cd34ef567890");


    static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bc23-de45fa678901");



    /**
     * Returns the "emptied" replacement for a stray Cirno storage item:
     * smart slabs become the empty slab variant (preserving the physical block),
     * photos just disappear (no empty variant exists for them).
     * The empty-slab Item is resolved once and cached to avoid repeated registry lookups.
     */
    static ItemStack emptyStorageReplacement(ItemStack stack) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && SMART_SLAB_HAS_MAID_ID.equals(id.toString())) {
            if (!emptySlabLookupDone) {
                cachedEmptySlabItem = net.minecraftforge.registries.ForgeRegistries.ITEMS
                        .getValue(new ResourceLocation(SMART_SLAB_EMPTY_ID));
                emptySlabLookupDone = true;
            }
            if (cachedEmptySlabItem != null) return new ItemStack(cachedEmptySlabItem);
        }
        return ItemStack.EMPTY;
    }



    /**
     * Called on player death AND on logout — applies the same cirnoPartOfOwner
     * rule used for the Curios companion to every currently-live, not-yet-stored
     * command-spawned Cirno slot, so command Cirnos follow the exact same
     * owner-fate policy instead of a separate, drifting set of rules:
     *   - cirnoPartOfOwner = true: discard her (snapshot + mark slot stored) so
     *     no slot's UUID keeps pointing at a dead/left-behind entity.
     *   - cirnoPartOfOwner = false: leave her alive in the world, just refreshing
     *     her durable backup snapshot.
     * Note this is independent of cirnoUnloadProtection, which only governs
     * whether untrusted code (chunk unload, other mods) can remove her at all —
     * it has no say in whether death/logout itself should.
     */
    static void applyPartOfOwnerPolicyToCommandCirnos(Player player) {
        migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = getSlotTag(player, i);
            if (slot == null) continue;
            // Already stored (H/toggle was used before) — nothing to do for this slot.
            if (slot.getBoolean(SLOT_STORED)) continue;
            // Slot exists but has no live UUID at all (shouldn't normally happen) — skip.
            if (!slot.hasUUID(SLOT_UUID)) continue;

            if (CirnoCommonConfig.cirnoPartOfOwner) {
                CommandCirnoManager.discardTrackedCirnoAt(player, i, slot.getUUID(SLOT_UUID));
                // This discard is only because the owner logged out/reloaded.
                // Restore this slot automatically when that player logs back in.
                CompoundTag updatedSlot = getSlotTag(player, i);
                if (updatedSlot != null) {
                    updatedSlot.putBoolean(SLOT_RESTORE_ON_LOGIN, true);
                    putSlotTag(player, i, updatedSlot);
                }
            } else {
                CirnoEntity cirno = CommandCirnoManager.findLiveCommandCirnoAtPublic(player, i);
                if (cirno != null) CompanionLifecycleHandler.snapshotCirnoDurable(player, cirno);
            }
        }
    }



    // ── Multi-slot command-Cirno primitives ─────────────────────────────────────

    /**
     * Folds the old single-Cirno NBT keys (from before multi-Cirno support) into
     * slot 1 the first time this player's command-Cirno state is touched, then
     * removes the legacy keys so this only ever runs once per player.
     */
    static void migrateLegacySlotIfNeeded(Player player) {
        CompoundTag data = player.getPersistentData();
        boolean hadLegacy = data.hasUUID(LEGACY_UUID) || data.contains(LEGACY_DATA, 10)
                || data.getBoolean(LEGACY_STORED);
        if (!hadLegacy) return;

        CompoundTag slot = new CompoundTag();
        if (data.hasUUID(LEGACY_UUID)) slot.putUUID(SLOT_UUID, data.getUUID(LEGACY_UUID));
        if (data.contains(LEGACY_DATA, 10)) slot.put(SLOT_DATA, data.getCompound(LEGACY_DATA));
        slot.putBoolean(SLOT_VISIBLE, data.getBoolean(LEGACY_VISIBLE));
        slot.putBoolean(SLOT_STORED, data.getBoolean(LEGACY_STORED));
        data.put(SLOT_KEYS[0], slot);

        data.remove(LEGACY_UUID);
        data.remove(LEGACY_DATA);
        data.remove(LEGACY_VISIBLE);
        data.remove(LEGACY_STORED);

        if (data.hasUUID(LEGACY_PENDING_DISCARD_UUID)) {
            CommandCirnoManager.addPendingDiscard(player, data.getUUID(LEGACY_PENDING_DISCARD_UUID), 1);
            data.remove(LEGACY_PENDING_DISCARD_UUID);
        }
    }



    static boolean isValidSlotIndex(int index) {
        return index >= 1 && index <= MAX_COMMAND_CIRNOS;
    }



    @Nullable
    static CompoundTag getSlotTag(Player player, int index) {
        if (!isValidSlotIndex(index)) return null;
        CompoundTag data = player.getPersistentData();
        String key = SLOT_KEYS[index - 1];
        return data.contains(key, 10) ? data.getCompound(key) : null;
    }



    static void putSlotTag(Player player, int index, CompoundTag slot) {
        if (!isValidSlotIndex(index)) return;
        player.getPersistentData().put(SLOT_KEYS[index - 1], slot);
    }



    static void clearSlotTag(Player player, int index) {
        if (!isValidSlotIndex(index)) return;
        player.getPersistentData().remove(SLOT_KEYS[index - 1]);
    }




    // ── Oathpin: convert a looked-at maid into a command Cirno ──────────────

    /** How far (in blocks) the Oathpin conversion can reach. */
    static final double OATHPIN_REACH = 6.0;



    // ── Curio check ───────────────────────────────────────────────────────────

    static boolean hasCurio(Player player) {
        return net.zhaiji.cirno.compat.CuriosCompat.hasCirno(player);
    }



    /** True if the player has at least one active command-spawned Cirno slot. */
    static boolean hasCommandCirno(Player player) {
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            if (getSlotTag(player, i) != null) return true;
        }
        return false;
    }



    /**
     * True if the player has TK access via curio OR command Cirno.
     * <p>
     * Unlike {@link #hasCommandCirno}, which only asks whether a command slot is
     * in use at all (spawned OR merely reserved/stored), telekinesis specifically
     * requires an actually-summoned command Cirno — a reserved-but-stored slot
     * shouldn't keep granting the shield/mode-toggle/mob-control powers once she's
     * been put away.
     */
    static boolean hasTKAccess(Player player) {
        return hasCurio(player) || CommandCirnoManager.findAnyLiveCommandCirnoPublic(player) != null;
    }



    public static boolean hasCurioPublic(Player player) {
        return hasCurio(player);
    }



    public static boolean hasTKAccessPublic(Player player) {
        return hasTKAccess(player);
    }



    @Nullable
    static Entity findLoadedEntity(Player player, UUID uuid) {
        if (player.getServer() == null) return null;
        for (ServerLevel serverLevel : player.getServer().getAllLevels()) {
            Entity entity = serverLevel.getEntity(uuid);
            if (entity != null) return entity;
        }
        // Not resident in memory anywhere right now — before treating that as "she's
        // gone" (which is what every caller of this method does: spawn a fresh
        // snapshot-restored replacement), give Minecraft an actual chance to recover
        // her first. A Cirno who's simply dormant in a chunk nobody has visited since
        // the last time she ticked — normal, harmless, unloaded-entity behaviour, NOT
        // a "ghost" — would otherwise get silently declared stale here and replaced,
        // leaving the original sitting fully intact (and fully up to date) on disk to
        // reappear later as a genuine duplicate, items and all, the moment that one
        // chunk is ever loaded again for any other reason.
        return recoverFromLastKnownLocation(player, uuid);
    }



    /**
     * Loads the ONE chunk this exact Cirno was last recorded ticking in (see
     * {@code PlayerLifecycleHandler#recordCirnoLocation}) and checks again whether
     * she's there. This never creates a replacement entity and never force-loads
     * anything beyond that single chunk — it only gives vanilla's own entity/chunk
     * storage a chance to deserialize her from disk into memory, exactly the way she'd
     * come back if a player simply walked into that chunk.
     */
    @Nullable
    private static Entity recoverFromLastKnownLocation(Player player, UUID uuid) {
        if (player.getServer() == null) return null;
        CompoundTag data = player.getPersistentData();
        if (!data.contains(NBT_CIRNO_LAST_LOCATIONS, 9)) return null;

        net.minecraft.nbt.ListTag locations = data.getList(NBT_CIRNO_LAST_LOCATIONS, 10);
        for (int i = 0; i < locations.size(); i++) {
            CompoundTag location = locations.getCompound(i);
            if (!location.hasUUID("UUID") || !uuid.equals(location.getUUID("UUID"))) continue;
            try {
                net.minecraft.resources.ResourceKey<Level> dimension = net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION,
                        new ResourceLocation(location.getString("Dimension")));
                ServerLevel source = player.getServer().getLevel(dimension);
                if (source == null) return null;
                source.getChunk(location.getInt("ChunkX"), location.getInt("ChunkZ"));
                return source.getEntity(uuid);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }



    /**
     * Returns the equipped Cirno ItemStack, or null if not equipped.
     */
    @Nullable
    public static ItemStack getCirnoStack(Player player) {
        // Curios is optional: returns null when it isn't installed.
        return net.zhaiji.cirno.compat.CuriosCompat.getCirnoStack(player);
    }



    // ── ItemStack NBT helpers ─────────────────────────────────────────────────

    static void setCompanionUUID(ItemStack stack, UUID uuid) {
        stack.getOrCreateTag().putUUID(ITEM_NBT_COMPANION_UUID, uuid);
    }



    @Nullable
    static UUID getCompanionUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        return tag.hasUUID(ITEM_NBT_COMPANION_UUID) ? tag.getUUID(ITEM_NBT_COMPANION_UUID) : null;
    }



    static void clearCompanionUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) tag.remove(ITEM_NBT_COMPANION_UUID);
    }



    static boolean getCompanionHidden(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(ITEM_NBT_COMPANION_HIDDEN);
    }



    static void setCompanionHidden(ItemStack stack, boolean hidden) {
        stack.getOrCreateTag().putBoolean(ITEM_NBT_COMPANION_HIDDEN, hidden);
    }



    static boolean isHiddenByPlayer(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(ITEM_NBT_HIDDEN_BY_PLAYER);
    }



    static void setHiddenByPlayer(ItemStack stack, boolean value) {
        stack.getOrCreateTag().putBoolean(ITEM_NBT_HIDDEN_BY_PLAYER, value);
    }



    static boolean requiresItemRevive(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(ITEM_NBT_REQUIRES_ITEM_REVIVE);
    }



    static void setRequiresItemRevive(ItemStack stack, boolean value) {
        if (value) {
            stack.getOrCreateTag().putBoolean(ITEM_NBT_REQUIRES_ITEM_REVIVE, true);
        } else {
            CompoundTag tag = stack.getTag();
            if (tag != null) tag.remove(ITEM_NBT_REQUIRES_ITEM_REVIVE);
        }
    }



    @Nullable
    static CompoundTag getCompanionSavedData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ITEM_NBT_COMPANION_DATA)) return null;
        return tag.getCompound(ITEM_NBT_COMPANION_DATA);
    }



    static void setCompanionSavedData(ItemStack stack, CompoundTag data) {
        stack.getOrCreateTag().put(ITEM_NBT_COMPANION_DATA, data);
    }



    /**
     * Public accessor used by packets. Returns the live entity UUID for a specific
     * command slot (1–4), or null if the slot is empty or stored.
     */
    @Nullable
    public static UUID getCommandCirnoUUID(Player player, int slotIndex) {
        migrateLegacySlotIfNeeded(player);
        CompoundTag slot = getSlotTag(player, slotIndex);
        if (slot == null || slot.getBoolean(SLOT_STORED)) return null;
        return slot.hasUUID(SLOT_UUID) ? slot.getUUID(SLOT_UUID) : null;
    }



    /**
     * True if {@code uuid} is the identity CURRENTLY tracked as this player's live
     * Cirno — the Curios companion slot or one of the command slots, checked by
     * stored UUID rather than by asking any particular entity object.
     * <p>
     * This is the authoritative "who actually is Cirno right now" check, independent
     * of whether some other entity object also happens to answer isAlive()/isOwnedBy()
     * truthfully. It exists specifically so a stale duplicate — for example a chunk-
     * unload "ghost" that a replacement has since been spawned in place of — can be
     * told apart from the real thing: her tracker was already repointed at the new
     * UUID the moment the replacement was created, so the old one simply stops
     * matching here even though the ghost object itself is still "alive".
     * {@link com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer}'s
     * distance check is bypassed for CirnoEntity (see AbstractMaidContainerMixin) so
     * the Maid Panel can stay open across any distance — this is what keeps that same
     * bypass from also keeping a decommissioned duplicate's inventory screen open and
     * editable forever, which is exactly what was producing duplicated items.
     */
    public static boolean isTrackedCompanionUUID(Player player, UUID uuid) {
        ItemStack curio = getCirnoStack(player);
        if (curio != null && uuid.equals(getCompanionUUID(curio))) return true;

        migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = getSlotTag(player, i);
            if (slot != null && slot.hasUUID(SLOT_UUID) && uuid.equals(slot.getUUID(SLOT_UUID))) {
                return true;
            }
        }
        return false;
    }



    /** Public accessor used by packets. Returns UUID from either Curios or command-spawned Cirno. */
    public static UUID getCompanionUUIDPublic(Player player) {
        // First check Curios-based Cirno
        ItemStack stack = getCirnoStack(player);
        if (stack != null) {
            UUID curiosUUID = getCompanionUUID(stack);
            if (curiosUUID != null) return curiosUUID;
        }

        // Fallback: command-spawned Cirno, only when she's actually in the world.
        // With up to MAX_COMMAND_CIRNOS of her now possible, this opens the first
        // live one found — the V-key GUI has no per-slot picker of its own.
        migrateLegacySlotIfNeeded(player);
        CirnoEntity anyLive = CommandCirnoManager.findAnyLiveCommandCirnoPublic(player);
        if (anyLive != null) {
            return anyLive.getUUID();
        }

        return null;
    }



    // ── Player-PersistentData NBT helpers ─────────────────────────────────────

    static int getShieldTicksLeft(Player player) {
        return player.getPersistentData().getInt(NBT_SHIELD_TICKS_LEFT);
    }



    static int getShieldCooldown(Player player) {
        return player.getPersistentData().getInt(NBT_SHIELD_COOLDOWN);
    }



    static int getTKControlCooldown(Player player) {
        return player.getPersistentData().getInt(NBT_TK_CONTROL_COOLDOWN);
    }



    // ── External storage cleanup ──────────────────────────────────────────────

    static final String SMART_SLAB_HAS_MAID_ID = "touhou_little_maid:smart_slab_has_maid";


    static final String SMART_SLAB_EMPTY_ID     = "touhou_little_maid:smart_slab_empty";


    static final String PHOTO_ID                = "touhou_little_maid:photo";


    static final String FILM_ID                 = "touhou_little_maid:film";


    static final ResourceLocation SMART_SLAB_HAS_MAID_RL = new ResourceLocation(SMART_SLAB_HAS_MAID_ID);


    static final ResourceLocation PHOTO_RL               = new ResourceLocation(PHOTO_ID);


    static final ResourceLocation FILM_RL                = new ResourceLocation(FILM_ID);


    static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();



    /** Ticks left on the curio Cirno's post-death revive cooldown (0 = ready). */
    static long curioReviveReadyRemaining(Player player) {
        long readyAt = player.getPersistentData().getLong(NBT_CIRNO_REVIVE_READY_AT);
        return Math.max(0L, readyAt - player.level().getGameTime());
    }

    /** Ticks left on a command slot's post-death revive cooldown (0 = ready). */
    static long slotReviveReadyRemaining(Player player, @Nullable CompoundTag slot) {
        if (slot == null) return 0L;
        return Math.max(0L, slot.getLong(SLOT_REVIVE_READY_AT) - player.level().getGameTime());
    }

    static Component reviveCooldownMessage(long ticksLeft) {
        return Component.literal("§c[Cirno] Still recovering — " + ((ticksLeft + 19) / 20) + "s left.");
    }



    /**
     * Whether OUR summon paths (H key, panel, /cirno, curio) may revive her from a
     * film. Tombstone mode only, and only when cirnoCanSummonFromFilm is enabled.
     * This never affects a revive performed through TLM's own items or Shrine.
     */
    static boolean canSummonFromFilm() {
        return CirnoCommonConfig.cirnoSpawnTombstoneOnDeath
                && CirnoCommonConfig.cirnoCanSummonFromFilm;
    }



    /**
     * True if a Cirno-tagged film is currently reachable (inventory, offhand,
     * armor, open menu, nearby container, ...) for this identity, but film
     * summoning is disabled, so she must be left stored rather than revived.
     * <p>
     * This exists because {@code extractAndClearExternallyStoredCirno*(player, false)}
     * correctly refuses to hand back the film's data when disabled — but every
     * caller of that method then falls back to a durable/slot-cached snapshot,
     * on the assumption that a {@code null} result only ever means "the real
     * storage item is in an unloaded chunk, use the backup instead". That
     * assumption is wrong when the item IS reachable and simply gated: the
     * fallback snapshot silently revives her anyway, bypassing
     * {@code cirnoCanSummonFromFilm} entirely. Callers must check this first
     * and skip their fallback when it returns true.
     *
     * @param targetUuid restrict the check to this specific stored identity, or
     *                    {@code null} to match any Cirno film belonging to the player.
     */
    static boolean hasGatedFilmReachable(Player player, @Nullable UUID targetUuid) {
        if (canSummonFromFilm()) return false;
        UUID ownerUUID = player.getUUID();
        for (CirnoSlot slot : CirnoStorageHandler.collectCirnoSlots(player)) {
            ItemStack stack = slot.getter().get();
            if (stack == null || stack.isEmpty()) continue;
            // allowFilm=true here on purpose: we WANT to see the gated film to
            // detect it, unlike the extraction helpers this feeds into.
            if (!isCirnoStorageItem(stack, ownerUUID, true)) continue;
            ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (!FILM_RL.equals(id)) continue;
            if (targetUuid != null && !isCirnoStorageItemForUuid(stack, ownerUUID, targetUuid, true)) continue;
            return true;
        }
        return false;
    }



    /**
     * Cached empty-slab Item resolved lazily on first use. Avoids a registry
     * lookup + ResourceLocation allocation on every emptyStorageReplacement call.
     */
    @Nullable
    static Item cachedEmptySlabItem = null;


    static boolean emptySlabLookupDone = false;



    /**
     * True if the stack is a smart slab / photo / film holding Cirno's data, and
     * (when an owner was recorded) it belongs to this player. Shared by every
     * check/clear path below so the item-id / NBT rules only live in one place.
     */
    static boolean isCirnoStorageItem(ItemStack stack, UUID ownerUUID) {
        return isCirnoStorageItem(stack, ownerUUID, true);
    }



    /**
     * Same storage identity test, but {@code allowFilm=false} makes film invisible
     * to actual summon/revive extraction while remaining visible to the unconditional
     * duplicate-prevention scan above. That distinction is important: a disabled
     * film-revive config must stop H/panel from resurrecting her, but it must NOT make
     * a stored film cease to count as "Cirno is already externally stored".
     */
    static boolean isCirnoStorageItem(ItemStack stack, UUID ownerUUID, boolean allowFilm) {
        if (stack == null || stack.isEmpty()) return false;

        // Cheapest reject first: nearly every stack the dupe scan looks at has no NBT, or
        // none with MaidInfo, so bail before touching the registry. All conditions are
        // ANDed, so order doesn't change the result.
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("MaidInfo", 10)) return false;

        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        // ResourceLocation.equals — no id.toString() String allocation per stack.
        boolean isSmartSlabOrPhoto = SMART_SLAB_HAS_MAID_RL.equals(id) || PHOTO_RL.equals(id);
        boolean isAllowedFilm = allowFilm && FILM_RL.equals(id);
        if (!isSmartSlabOrPhoto && !isAllowedFilm) return false;

        CompoundTag maidData = tag.getCompound("MaidInfo");
        if (!maidData.getBoolean("IsCirnoCompanion")) return false;

        // Only claim it as *this* player's copy if an owner was actually recorded,
        // so we never touch another player's stashed Cirno in a shared chest.
        return !maidData.hasUUID("OwnerPlayerUUID") || maidData.getUUID("OwnerPlayerUUID").equals(ownerUUID);
    }



    /**
     * Same as {@link #isCirnoStorageItem(ItemStack, UUID)}, but additionally
     * requires the stored entity's own saved UUID to match {@code targetUuid}.
     * Needed now that a player can have up to {@link #MAX_COMMAND_CIRNOS} Cirnos
     * stashed externally at once — restoring/removing one specific slot must never
     * grab a different slot's storage item just because it's the first one found.
     */
    static boolean isCirnoStorageItemForUuid(ItemStack stack, UUID ownerUUID, UUID targetUuid) {
        return isCirnoStorageItemForUuid(stack, ownerUUID, targetUuid, true);
    }



    static boolean isCirnoStorageItemForUuid(ItemStack stack, UUID ownerUUID, UUID targetUuid, boolean allowFilm) {
        if (!isCirnoStorageItem(stack, ownerUUID, allowFilm)) return false;
        CompoundTag maidData = stack.getTag().getCompound("MaidInfo");
        return maidData.hasUUID("UUID") && maidData.getUUID("UUID").equals(targetUuid);
    }



    /**
     * A single reachable Cirno-storage-item location: reads the current stack and,
     * if it turns out to be her, replaces it. Every location we scan — a player
     * inventory slot, an open menu slot, a slot in a chest/barrel/shulker/etc., or
     * a dropped item entity — is represented the same way so the scan and the
     * extract-and-clear logic can't drift apart the way the two duplicated
     * command-tracking code paths did before.
     */
    record CirnoSlot(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {}



    /** How often (ticks) the dupe cleanup scans the player's current dimension. */
    static final int DUPE_SCAN_INTERVAL_TICKS = 20;


    /**
     * Dimensions the player is NOT in are only fully scanned every this-many current-dimension
     * scans (i.e. every 5 s by default). Set to 1 to scan them every second like before.
     */
    static final int DUPE_SCAN_OTHER_DIM_EVERY = 5;



    /**
     * If the stack is Cirno's storage item, extracts her NBT and replaces the stack
     * via {@code setter} (empty smart slab, or nothing for a photo). Returns the
     * extracted NBT, or null if the stack didn't match.
     */
    @Nullable
    static CompoundTag tryExtractCirnoFromStack(ItemStack stack, UUID ownerUUID,
                                                java.util.function.Consumer<ItemStack> setter) {
        return tryExtractCirnoFromStack(stack, ownerUUID, setter, true);
    }



    @Nullable
    static CompoundTag tryExtractCirnoFromStack(ItemStack stack, UUID ownerUUID,
                                                java.util.function.Consumer<ItemStack> setter,
                                                boolean allowFilm) {
        if (!isCirnoStorageItem(stack, ownerUUID, allowFilm)) return null;
        return extractCirnoStorageStack(stack, setter);
    }



    /**
     * Same as {@link #tryExtractCirnoFromStack}, but only extracts if the stored
     * entity's own saved UUID matches {@code targetUuid} — see
     * {@link #isCirnoStorageItemForUuid}.
     */
    @Nullable
    static CompoundTag tryExtractCirnoFromStackForUuid(ItemStack stack, UUID ownerUUID, UUID targetUuid,
                                                       java.util.function.Consumer<ItemStack> setter) {
        return tryExtractCirnoFromStackForUuid(stack, ownerUUID, targetUuid, setter, true);
    }



    @Nullable
    static CompoundTag tryExtractCirnoFromStackForUuid(ItemStack stack, UUID ownerUUID, UUID targetUuid,
                                                       java.util.function.Consumer<ItemStack> setter,
                                                       boolean allowFilm) {
        if (!isCirnoStorageItemForUuid(stack, ownerUUID, targetUuid, allowFilm)) return null;
        return extractCirnoStorageStack(stack, setter);
    }



    static CompoundTag extractCirnoStorageStack(ItemStack stack, java.util.function.Consumer<ItemStack> setter) {
        CompoundTag maidData = stack.getTag().getCompound("MaidInfo");
        CompoundTag extracted = maidData.copy();

        String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();
        if (SMART_SLAB_HAS_MAID_ID.equals(itemId)) {
            try {
                Item emptySlabItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation(SMART_SLAB_EMPTY_ID));
                setter.accept(emptySlabItem != null ? new ItemStack(emptySlabItem) : ItemStack.EMPTY);
            } catch (Exception e) {
                setter.accept(ItemStack.EMPTY);
            }
        } else {
            setter.accept(ItemStack.EMPTY);
        }
        return extracted;
    }



    /**
     * Scans for a smart slab / photo item containing Cirno's IsCirnoCompanion
     * data and clears it wherever it currently is — using the exact same set of
     * locations as CirnoStorageHandler.isStoredInExternalItem() (see CirnoStorageHandler.collectCirnoSlots()), so the
     * check that blocks a duplicate spawn and the cleanup that follows can never
     * disagree about where she is.
     * Returns the extracted NBT, or null if not found.
     */
    @Nullable
    static CompoundTag extractAndClearExternallyStoredCirno(Player player) {
        return extractAndClearExternallyStoredCirno(player, true);
    }



    @Nullable
    static CompoundTag extractAndClearExternallyStoredCirno(Player player, boolean allowFilm) {
        UUID ownerUUID = player.getUUID();
        for (CirnoSlot slot : CirnoStorageHandler.collectCirnoSlots(player)) {
            CompoundTag extracted = tryExtractCirnoFromStack(slot.getter().get(), ownerUUID, slot.setter(), allowFilm);
            if (extracted != null) return extracted;
        }
        return null;
    }



    /**
     * Same as {@link #extractAndClearExternallyStoredCirno}, but only extracts a
     * storage item whose saved Cirno matches {@code uuid} exactly. Used by every
     * per-slot command-Cirno operation (restore, remove) now that more than one of
     * her can be stashed externally at the same time — without this, restoring
     * slot 1 could accidentally consume slot 2's smart slab just because it was
     * the first Cirno-storage item found in the player's inventory.
     */
    @Nullable
    static CompoundTag extractAndClearExternallyStoredCirnoForUuid(Player player, UUID uuid) {
        return extractAndClearExternallyStoredCirnoForUuid(player, uuid, true);
    }



    @Nullable
    static CompoundTag extractAndClearExternallyStoredCirnoForUuid(Player player, UUID uuid, boolean allowFilm) {
        UUID ownerUUID = player.getUUID();
        for (CirnoSlot slot : CirnoStorageHandler.collectCirnoSlots(player)) {
            CompoundTag extracted = tryExtractCirnoFromStackForUuid(
                    slot.getter().get(), ownerUUID, uuid, slot.setter(), allowFilm);
            if (extracted != null) return extracted;
        }
        return null;
    }



    /** Extracts the TLM modelId string from a live CirnoEntity. Falls back to "" if unavailable. */
    static String getCirnoModelId(CirnoEntity cirno) {
        try { return cirno.getModelId(); } catch (Exception e) { return ""; }
    }



    /** Extracts the YSM modelId from a live entity. Returns "" if not a YSM model. */
    static String getCirnoYsmModelId(CirnoEntity cirno) {
        try {
            if (cirno.isYsmModel()) return cirno.getYsmModelId();
        } catch (Exception ignored) { }
        return "";
    }
}