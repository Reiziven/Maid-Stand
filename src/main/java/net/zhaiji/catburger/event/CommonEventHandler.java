package net.zhaiji.catburger.event;

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
import net.minecraft.world.food.FoodData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.TickEvent;
import net.zhaiji.catburger.config.CatBurgerCommonConfig;
import net.zhaiji.catburger.compat.CompatManager;
import net.zhaiji.catburger.compat.TLMCompat;
import net.zhaiji.catburger.entity.CirnoEntity;
import net.zhaiji.catburger.init.InitEntity;
import net.zhaiji.catburger.init.InitItem;
import net.zhaiji.catburger.network.PacketManager;
import net.zhaiji.catburger.network.client.packet.PlayerDeathPacket;
import net.zhaiji.catburger.network.client.packet.SyncCompanionVisibilityPacket;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CommonEventHandler {

    // ── NBT keys stored on the ItemStack ─────────────────────────────────────
    /** UUID of the companion entity linked to this specific item stack. */
    private static final String ITEM_NBT_COMPANION_UUID   = "CatBurgerCompanionUUID";
    /** Whether the companion is hidden for this item stack. */
    private static final String ITEM_NBT_COMPANION_HIDDEN = "CatBurgerCompanionHidden";
    /**
     * Whether the hidden state was set specifically by the player's H key press.
     * Only set by toggleCompanionVisibility(). If this is false but HIDDEN is true,
     * Cirno was stored by an external system (smart slab, camera, etc.) and H-to-show
     * must be blocked to avoid spawning a duplicate alongside the stored entity.
     */
    private static final String ITEM_NBT_HIDDEN_BY_PLAYER = "CatBurgerHiddenByPlayer";
    /** Full entity NBT snapshot of Cirno, used to restore her inventory across dimension changes. */
    private static final String ITEM_NBT_COMPANION_DATA   = "CatBurgerCompanionData";

    // ── NBT keys stored on player PersistentData (transient per-session state) ──
    private static final String NBT_SHIELD_TICKS_LEFT       = "CatBurgerShieldTicksLeft";
    private static final String NBT_SHIELD_COOLDOWN         = "CatBurgerShieldCooldown";
    private static final String NBT_TK_MODE_ACTIVE          = "CatBurgerTKModeActive";
    private static final String NBT_TK_CONTROLLED_UUID      = "CatBurgerTKControlledUUID";
    private static final String NBT_TK_CONTROL_TICKS_LEFT   = "CatBurgerTKControlTicksLeft";
    private static final String NBT_TK_CONTROL_COOLDOWN     = "CatBurgerTKControlCooldown";
    private static final String NBT_TK_HOLD_ACTIVE          = "CatBurgerTKHoldActive";
    private static final String NBT_CIRNO_REVIVE_TICKS_LEFT = "CatBurgerCirnoReviveTicksLeft";
    private static final String NBT_LAST_KNOWN_CIRNO_DATA = "CatBurgerLastKnownCirnoData";
    private static final String NBT_LAST_KNOWN_CIRNO_TIME = "CatBurgerLastKnownCirnoTime";

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
    private static final String[] SLOT_KEYS = {
            "CatBurgerCirnoSlot1", "CatBurgerCirnoSlot2", "CatBurgerCirnoSlot3", "CatBurgerCirnoSlot4"
    };
    private static final String SLOT_UUID    = "Uuid";
    private static final String SLOT_STORED  = "Stored";
    private static final String SLOT_DATA    = "Data";
    private static final String SLOT_VISIBLE = "Visible";

    /**
     * Deferred discards for command Cirnos whose chunk wasn't loaded at the time
     * we tried to store/remove them — a ListTag of {@code {Uuid, Slot}} entries.
     * A list (rather than the old single UUID) because more than one slot can be
     * mid-unload at once now that there can be up to 3 of her.
     */
    private static final String NBT_PENDING_DISCARDS = "CatBurgerPendingDiscards";

    // Legacy (pre-multi-Cirno) single-slot keys — migrated into slot 1 the first
    // time this player's command-Cirno state is touched after updating.
    private static final String LEGACY_UUID    = "CatBurgerCommandCirnoUUID";
    private static final String LEGACY_DATA    = "CatBurgerCommandCirnoData";
    private static final String LEGACY_VISIBLE = "CatBurgerCommandCirnoVisible";
    private static final String LEGACY_STORED  = "CatBurgerCommandCirnoStored";
    private static final String LEGACY_PENDING_DISCARD_UUID = "CatBurgerPendingDiscardUUID";

    // Attribute modifier UUIDs (stable, unique per modifier)
    private static final UUID ATTACK_MODIFIER_UUID = UUID.fromString("a1b2c3d4-e5f6-7890-ab12-cd34ef567890");
    private static final UUID HEALTH_MODIFIER_UUID = UUID.fromString("b2c3d4e5-f6a7-8901-bc23-de45fa678901");

    // ── Death prevention ──────────────────────────────────────────────────────

    public static void handlerLivingDeathEvent(LivingDeathEvent event) {
        if (!CatBurgerCommonConfig.totemEffectActive) return;
        Item item = InitItem.CAT_BURGER.get();
        if (event.getEntity() instanceof Player player && !player.getCooldowns().isOnCooldown(item)) {
            CuriosApi.getCuriosInventory(player).ifPresent(iCuriosItemHandler -> {
                if (iCuriosItemHandler.findFirstCurio(item).isPresent()) {
                    FoodData foodData = player.getFoodData();
                    if (CatBurgerCommonConfig.usePercentageHealthRestoration) {
                        float maxHealth = player.getMaxHealth();
                        float restoreAmount = maxHealth * ((float) CatBurgerCommonConfig.percentageHealthRestoration / 100.0f);
                        player.setHealth(restoreAmount);
                    } else {
                        player.setHealth(CatBurgerCommonConfig.healthRestorationFromTotem);
                    }
                    foodData.setFoodLevel(CatBurgerCommonConfig.foodRestorationFromTotem);
                    foodData.setSaturation(CatBurgerCommonConfig.saturationRestorationFromTotem);
                    player.getCooldowns().addCooldown(item, CatBurgerCommonConfig.totemCooldown);
                    player.level().broadcastEntityEvent(player, (byte) 35);
                    PacketManager.sendToClient(new PlayerDeathPacket(), (ServerPlayer) player);
                    event.setCanceled(true);
                }
            });
        }

        if (!event.isCanceled() && event.getEntity() instanceof Player player) {
            removeAttributeBuffs(player);
            if (CatBurgerCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()) {
                if (CatBurgerCommonConfig.cirnoPartOfOwner) {
                    removeCompanion(player);
                } else {
                    // Not bound to the owner's fate: leave her alive in the world —
                    // just snapshot her current data as a durable backup.
                    snapshotCompanionWithoutRemoving(player);
                }
            }
            applyPartOfOwnerPolicyToCommandCirnos(player);
        }
    }

    // ── Kill credit: Cirno's kills count as the owner's ───────────────────────
    //
    // Vanilla only special-cases Wolf for this (LivingEntity#hurt sets
    // lastHurtByPlayer to the owner when a *tamed Wolf* deals the damage).
    // Every other tamed/owned mob — Cirno included — is left as a plain mob
    // kill, so loot conditions like "killed_by_player" (wither/skeleton skulls,
    // charged-creeper heads, equipped-item drop bonus), the player's mob-kill
    // statistic, and kill-based advancements never trigger from her attacks.
    // We restore that behaviour ourselves for her specifically.
    public static void handlerLivingHurtEvent(LivingHurtEvent event) {
        if (event.getAmount() <= 0.0F) return;
        LivingEntity target = event.getEntity();
        if (target instanceof Player) return; // don't let her "friendly fire" credit herself onto a player

        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();
        if (!(attacker instanceof CirnoEntity cirno)) return;

        Player owner = cirno.getOwnerPlayer();
        if (owner != null) {
            target.setLastHurtByPlayer(owner);
        }
    }

    // ── Telekinesis shield: block incoming projectiles physically ────────────

    public static void handlerProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hitResult)) return;
        if (!(hitResult.getEntity() instanceof ServerPlayer player)) return;
        if (!CatBurgerCommonConfig.telekinesisShieldEnabled) return;
        if (!hasCurio(player)) return;
        if (!buffsAndTKAllowed()) return;
        if (getShieldTicksLeft(player) <= 0) return;

        // Block all projectiles except those fired by the shielded player themselves
        Entity owner = event.getProjectile().getOwner();
        if (owner != null && owner.getUUID().equals(player.getUUID())) return;

        event.setCanceled(true);
        event.getProjectile().discard();
    }

    // ── Pre-damage: telekinesis shield auto-trigger ───────────────────────────

    public static void handlerLivingDamageEvent(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CatBurgerCommonConfig.telekinesisShieldEnabled) return;
        if (!hasCurio(player)) return;
        if (!buffsAndTKAllowed()) return;

        float hp = player.getHealth();
        float max = player.getMaxHealth();
        boolean lowHp = (hp / max) <= 0.30f;
        boolean fatalDamage = event.getAmount() >= hp;

        if ((lowHp || fatalDamage) && getShieldCooldown(player) <= 0 && getShieldTicksLeft(player) <= 0) {
            activateTelekinesisShield(player);
        }

        if (getShieldTicksLeft(player) > 0) {
            if (event.getSource().getDirectEntity() instanceof Projectile) {
                event.setCanceled(true);
            }
        }
    }

    // ── Player tick: shield repel + telekinesis control tick ─────────────────

    public static void handlerPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Player player = event.player;
        if (player.level().isClientSide) return;

        // Shield and buffs require the curio item
        if (hasCurio(player) && buffsAndTKAllowed()) {
            tickShield(player);
        }

        // TK works with curio OR command Cirno
        if (hasTKAccess(player)) {
            tickTelekinesisControl(player);
        }

        // Cirno retarget/revive only relevant when curio is equipped
        if (hasCurio(player) && buffsAndTKAllowed()) {
            tickCirnoRetarget(player);
            tickCirnoRevive(player);
        }

        tickDupeCleanup(player);
    }

    /**
     * Periodically clears stray Cirno storage items (smart slab / photo) belonging
     * to this player while a live Cirno already exists, across ALL loaded dimensions.
     *
     * Current dimension: bounded 64-block AABB / cube around the player (only loaded
     * data, no chunk forcing).
     * Other dimensions: iterates all loaded entities and block entities in those levels
     * — still only what's already ticking, no chunk loading forced.
     *
     * Smart slabs become the empty variant; photos are removed.
     * Runs every 20 ticks (1 s) per player.
     */
    private static void tickDupeCleanup(Player player) {
        CompoundTag data = player.getPersistentData();
        int scanTick = data.getInt(NBT_DUPE_SCAN_TICK);
        if (scanTick > 0) {
            data.putInt(NBT_DUPE_SCAN_TICK, scanTick - 1);
            return;
        }
        data.putInt(NBT_DUPE_SCAN_TICK, 20);

        // Collect UUIDs of Cirnos that are genuinely alive right now (command
        // slots + curio companion). Only a storage item whose stored entity UUID
        // matches one of THESE is a true stray duplicate. With multiple Cirnos,
        // a legitimately-stored smart slab/photo for a different (stored) slot
        // must never be wiped just because another Cirno is still live.
        List<UUID> liveUuids = new ArrayList<>();
        migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CirnoEntity live = findLiveCommandCirnoAtPublic(player, i);
            if (live != null) liveUuids.add(live.getUUID());
        }
        ItemStack catBurgerStack = getCatBurgerStack(player);
        if (catBurgerStack != null && !getCompanionHidden(catBurgerStack)) {
            UUID curiosUuid = getCompanionUUID(catBurgerStack);
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
        scanLevelBounded(player.level(), ownerUUID, liveUuids,
                player.getBoundingBox().inflate(DUPE_SCAN_RADIUS),
                player.blockPosition(), (int) DUPE_SCAN_RADIUS);

        // ── Other dimensions: walk everything that's already loaded ───────────
        if (player.getServer() != null) {
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
        if (!isCirnoStorageItem(stack, ownerUUID)) return false;
        CompoundTag maidData = stack.getTag().getCompound("MaidInfo");
        if (!maidData.hasUUID("UUID")) return false;
        UUID stored = maidData.getUUID("UUID");
        for (UUID live : liveUuids) {
            if (live.equals(stored)) return true;
        }
        return false;
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
                    itemEntity.setItem(emptyStorageReplacement(stack));
                }
            } else {
                scanEntityInventory(entity, ownerUUID, liveUuids);
            }
        }

        // Block-entity containers
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container container)) continue;
            boolean changed = false;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                    container.setItem(i, emptyStorageReplacement(stack));
                    changed = true;
                }
            }
            if (changed) container.setChanged();
        }
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
                    itemEntity.setItem(emptyStorageReplacement(stack));
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
            for (BlockEntity be : chunk.getBlockEntities().values()) {
                if (!(be instanceof Container container)) continue;
                boolean changed = false;
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack stack = container.getItem(i);
                    if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                        container.setItem(i, emptyStorageReplacement(stack));
                        changed = true;
                    }
                }
                if (changed) container.setChanged();
            }
        }
    }

    /** Scans a flat inventory list (main/offhand/armor) for stray storage items. */
    private static void scanInventoryList(List<ItemStack> list, UUID ownerUUID, List<UUID> liveUuids) {
        for (int i = 0; i < list.size(); i++) {
            ItemStack stack = list.get(i);
            if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                list.set(i, emptyStorageReplacement(stack));
            }
        }
    }

    /** Checks item frames and non-player living entity equipment for stray storage items. */
    private static void scanEntityInventory(Entity entity, UUID ownerUUID, List<UUID> liveUuids) {
        if (entity instanceof ItemFrame itemFrame) {
            ItemStack stack = itemFrame.getItem();
            if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                itemFrame.setItem(emptyStorageReplacement(stack));
            }
        } else if (entity instanceof LivingEntity living && !(living instanceof Player)) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack stack = living.getItemBySlot(slot);
                if (isStrayCirnoStorageItem(stack, ownerUUID, liveUuids)) {
                    living.setItemSlot(slot, emptyStorageReplacement(stack));
                }
            }
        }
    }

    /**
     * Returns the "emptied" replacement for a stray Cirno storage item:
     * smart slabs become the empty slab variant (preserving the physical block),
     * photos just disappear (no empty variant exists for them).
     * The empty-slab Item is resolved once and cached to avoid repeated registry lookups.
     */
    private static ItemStack emptyStorageReplacement(ItemStack stack) {
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

    // ── Curio equip / unequip ─────────────────────────────────────────────────

    public static void handlerCurioChangeEvent(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        Item catBurger = InitItem.CAT_BURGER.get();
        ItemStack fromStack = event.getFrom();
        ItemStack toStack = event.getTo();
        boolean wasEquipped = !fromStack.isEmpty() && fromStack.getItem() == catBurger;
        boolean isEquipped  = !toStack.isEmpty()   && toStack.getItem()   == catBurger;

        if (!wasEquipped && isEquipped) {
            // Equipped: apply attribute buffs regardless of companion mode
            applyAttributeBuffs(player);
            // Spawn companion only when enabled and TLM is loaded,
            // but not while the death-revive cooldown is still ticking — she'll
            // spawn automatically once the timer fires in tickCirnoRevive().
            if (CatBurgerCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()
                    && player.getPersistentData().getInt(NBT_CIRNO_REVIVE_TICKS_LEFT) <= 0) {
                spawnCompanion(player, toStack);
            }
        } else if (wasEquipped && !isEquipped) {
            // Unequipped: always strip buffs and clear telekinesis
            removeAttributeBuffs(player);
            clearTelekinesisControl(player);
            if (CatBurgerCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()) {
                removeCompanion(player, fromStack);
            }
        }
    }

    // ── Dimension change ──────────────────────────────────────────────────────

    public static void handlerPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!CatBurgerCommonConfig.companionEntityEnabled) return;
        if (!CompatManager.isTLMLoad()) return;
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack stack = getCatBurgerStack(player);
        if (stack == null) return;

        // If hidden, no entity lives in the world — nothing to move
        if (getCompanionHidden(stack)) return;

        // The companion entity lives in the old dimension — snapshot it, then
        // move it to the new level.
        UUID existingUUID = getCompanionUUID(stack);
        if (existingUUID != null && player.getServer() != null) {
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity e = level.getEntity(existingUUID);
                if (e instanceof CirnoEntity cirno) {
                    CompoundTag snapshot = new CompoundTag();
                    cirno.addAdditionalSaveData(snapshot);
                    setCompanionSavedData(stack, snapshot);
                    cirno.allowNextRemoval();
                    cirno.discard();
                    break;
                }
            }
            clearCompanionUUID(stack);
        }

        spawnCompanion(player, stack);
    }

    // ── Wake up ───────────────────────────────────────────────────────────────

    public static void handlerPlayerWakeUpEvent(PlayerWakeUpEvent event) {
        if (!CatBurgerCommonConfig.wakeUpCanResetCooldown) return;
        Player player = event.getEntity();
        Item item = InitItem.CAT_BURGER.get();
        CuriosApi.getCuriosInventory(player).ifPresent(iCuriosItemHandler -> {
            if (iCuriosItemHandler.findFirstCurio(item).isPresent()) {
                player.getCooldowns().removeCooldown(item);
            }
        });
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    public static void handlerPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        removeAttributeBuffs(player);
        clearTelekinesisControl(player);

        if (CatBurgerCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()) {
            if (CatBurgerCommonConfig.cirnoPartOfOwner) {
                removeCompanion(player);
            } else {
                // Not bound to the owner's fate: leave her alive in the world, waiting
                // for the owner to log back in — just snapshot her as a durable backup.
                snapshotCompanionWithoutRemoving(player);
            }
        }
        applyPartOfOwnerPolicyToCommandCirnos(player);
    }

    public static void handlerPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack stack = getCatBurgerStack(player);
        if (stack == null) return;

        // Always apply attribute buffs when curio is equipped
        applyAttributeBuffs(player);

        // Companion entity only when enabled + TLM loaded
        if (!CatBurgerCommonConfig.companionEntityEnabled || !CompatManager.isTLMLoad()) return;

        // If hidden, no entity should exist — nothing to do
        if (getCompanionHidden(stack)) return;

        UUID existingUUID = getCompanionUUID(stack);
        if (existingUUID != null && player.getServer() != null) {
            ServerLevel serverLevel = (ServerLevel) player.level();
            Entity e = serverLevel.getEntity(existingUUID);
            if (e instanceof CirnoEntity cirno && cirno.isAlive()) {
                cirno.setOwnerPlayer(player);
                cirno.applyModel();
                return;
            }
            clearCompanionUUID(stack);
        }

        spawnCompanion(player, stack);
    }

    // ── Death / respawn NBT carry-over ────────────────────────────────────────

    /**
     * Vanilla's player respawn creates a brand-new ServerPlayer object and does
     * NOT copy {@link Player#getPersistentData()} onto it — that's the standard,
     * well-known Forge gotcha for any mod that stores its own data there instead
     * of on an ItemStack or a Capability. Without this handler, everything the
     * command-mode Cirno system stores on the player (all 3 slots, plus pending
     * discard breadcrumbs) is silently wiped the moment the player dies, even
     * though applyPartOfOwnerPolicyToCommandCirnos() correctly wrote it just before that —
     * which is exactly why they were "gone forever, can't summon back" on death.
     * The Curios-equipped companion doesn't have this problem since its state
     * lives on the curio ItemStack itself, which survives death via the normal
     * inventory-keeping rules instead.
     */
    public static void handlerPlayerClone(net.minecraftforge.event.entity.player.PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        CompoundTag oldData = event.getOriginal().getPersistentData();
        if (oldData.isEmpty()) return;
        event.getEntity().getPersistentData().merge(oldData);
    }

    // ── Public action handlers (called by packets) ────────────────────────────

    public static void toggleCompanionVisibility(Player player) {
        if (player.level().isClientSide) return;

        migrateLegacySlotIfNeeded(player);

        // NOTE: previously, having a curio equipped made this method return early
        // after handling ONLY the curio's own Cirno, completely ignoring any
        // command-spawned ones. That's why H used to hide/show just one companion
        // (whichever the curio branch reached) instead of everyone the player has.
        // Both systems are now always evaluated together, driven by ONE shared
        // "is anything currently visible" decision, so H hides/shows all of them
        // as a single group no matter which combination the player has.
        ItemStack curioStack = hasCurio(player) ? getCatBurgerStack(player) : null;
        boolean hasCommandCirno = false;
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            if (getSlotTag(player, i) != null) { hasCommandCirno = true; break; }
        }

        if (curioStack == null && !hasCommandCirno) return;

        // If companion entity is disabled entirely, H can only hide (already-live entities
        // may exist from before the config was changed), never summon.
        if (!CatBurgerCommonConfig.companionEntityEnabled) {
            // Only act if something is currently visible — hide it and sync, then stop.
            boolean anyVisibleNow = false;
            if (curioStack != null && !getCompanionHidden(curioStack)) anyVisibleNow = true;
            if (!anyVisibleNow) {
                for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
                    if (isCommandCirnoSlotLivePublic(player, i)) { anyVisibleNow = true; break; }
                }
            }
            if (anyVisibleNow) {
                if (curioStack != null && !getCompanionHidden(curioStack)) {
                    removeCompanion(player, curioStack);
                    setCompanionHidden(curioStack, true);
                    setHiddenByPlayer(curioStack, true);
                }
                storeAllCommandCirno(player);
                sendVisibilitySync(player, true);
            }
            // Nothing visible → H does nothing
            return;
        }

        boolean curioVisible = curioStack != null && !getCompanionHidden(curioStack);
        boolean anyCommandLive = false;
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            if (isCommandCirnoSlotLivePublic(player, i)) {
                anyCommandLive = true;
                break;
            }
        }
        boolean hidingEverything = curioVisible || anyCommandLive;

        if (curioStack != null) {
            if (hidingEverything) {
                if (curioVisible) {
                    // ── H pressed while visible: hide Cirno ──────────────────
                    removeCompanion(player, curioStack);
                    setCompanionHidden(curioStack, true);
                    setHiddenByPlayer(curioStack, true);
                }
                // else: curio's Cirno is already hidden — nothing to do for her,
                // command-mode slots below still get stored.
            } else {
                // ── H pressed while hidden: only show if WE are the ones who hid her ──
                if (!isHiddenByPlayer(curioStack)) {
                    setHiddenByPlayer(curioStack, true);
                    // Still no visible change for the curio — but command-mode
                    // slots below may still have something to restore.
                } else if (player.getPersistentData().getInt(NBT_CIRNO_REVIVE_TICKS_LEFT) <= 0) {
                    // Not blocked by a death revive in progress.
                    setCompanionHidden(curioStack, false);
                    setHiddenByPlayer(curioStack, false);

                    CompoundTag externalNBT = extractAndClearExternallyStoredCirno(player);
                    if (externalNBT != null) {
                        setCompanionSavedData(curioStack, externalNBT);
                    }

                    spawnCompanion(player, curioStack);
                }
            }
        }

        if (hasCommandCirno) {
            if (hidingEverything) {
                storeAllCommandCirno(player);
            } else {
                restoreAllCommandCirno(player);
            }
        }

        sendVisibilitySync(player, hidingEverything);
    }

    /** Sends the authoritative hidden state to the client so it never drifts. */
    private static void sendVisibilitySync(Player player, boolean hidden) {
        if (player instanceof ServerPlayer sp) {
            PacketManager.sendToClient(new SyncCompanionVisibilityPacket(hidden), sp);
        }
    }

    /**
     * Called by the "open menu" packet (client sends this instead of the plain
     * toggle when the player holds Shift while pressing H). Lists every command
     * Cirno slot (1..{@link #MAX_COMMAND_CIRNOS}) as a clickable chat line so the
     * player can summon/store/remove one specific Cirno, or add a new one, without
     * a dedicated screen. This is entirely about the command-mode slots and has
     * nothing to do with the Curios-equipped companion, so it works the same way
     * whether or not a curio happens to be equipped — the previous "does nothing
     * if a curio is equipped" gate here was a bug, not intentional scoping.
     */
    public static void openCirnoMenu(Player player) {
        if (player.level().isClientSide) return;

        migrateLegacySlotIfNeeded(player);

        player.sendSystemMessage(Component.literal("§b[Cirno] ── Companion control ──"));

        // ── Curious Cirno slot (curio item) ──────────────────────────────────
        if (hasCurio(player)) {
            ItemStack curioStack = getCatBurgerStack(player);
            boolean curioHidden = curioStack == null || getCompanionHidden(curioStack);
            net.minecraft.network.chat.MutableComponent curioLine = Component.literal(
                    "§7Curio: " + (curioHidden ? "§eStored" : "§aSummoned"));
            if (curioHidden) {
                curioLine.append(Component.literal("  §a[Summon]")
                        .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/cirno curio true"))));
            } else {
                curioLine.append(Component.literal("  §c[Store]")
                        .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/cirno curio false"))));
            }
            player.sendSystemMessage(curioLine);
        }

        // ── Command Cirno slots ───────────────────────────────────────────────
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            final int slotIndex = i;
            boolean used = isCommandCirnoSlotUsedPublic(player, i);
            boolean live = isCommandCirnoSlotLivePublic(player, i);
            String prefix = "§7Slot " + i + ": ";

            net.minecraft.network.chat.MutableComponent line = Component.literal(prefix +
                    (!used ? "§8(empty)" : (live ? "§aSummoned" : "§eStored")));

            if (!used) {
                line.append(Component.literal("  §b[+ Add]")
                        .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/cirno add true true"))));
            } else if (live) {
                line.append(Component.literal("  §c[Store]")
                        .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/cirno toggle " + slotIndex + " false true"))));
                line.append(Component.literal("  §4[Remove]")
                        .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/cirno remove " + slotIndex))));
            } else {
                line.append(Component.literal("  §a[Summon]")
                        .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/cirno toggle " + slotIndex + " true true"))));
                line.append(Component.literal("  §4[Remove]")
                        .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                                net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                                "/cirno remove " + slotIndex))));
            }
            player.sendSystemMessage(line);
        }
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
    private static void applyPartOfOwnerPolicyToCommandCirnos(Player player) {
        migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = getSlotTag(player, i);
            if (slot == null) continue;
            // Already stored (H/toggle was used before) — nothing to do for this slot.
            if (slot.getBoolean(SLOT_STORED)) continue;
            // Slot exists but has no live UUID at all (shouldn't normally happen) — skip.
            if (!slot.hasUUID(SLOT_UUID)) continue;

            if (CatBurgerCommonConfig.cirnoPartOfOwner) {
                discardTrackedCirnoAt(player, i, slot.getUUID(SLOT_UUID));
            } else {
                CirnoEntity cirno = findLiveCommandCirnoAtPublic(player, i);
                if (cirno != null) snapshotCirnoDurable(player, cirno);
            }
        }
    }

    // ── Multi-slot command-Cirno primitives ─────────────────────────────────────

    /**
     * Folds the old single-Cirno NBT keys (from before multi-Cirno support) into
     * slot 1 the first time this player's command-Cirno state is touched, then
     * removes the legacy keys so this only ever runs once per player.
     */
    private static void migrateLegacySlotIfNeeded(Player player) {
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
            addPendingDiscard(player, data.getUUID(LEGACY_PENDING_DISCARD_UUID), 1);
            data.remove(LEGACY_PENDING_DISCARD_UUID);
        }
    }

    private static boolean isValidSlotIndex(int index) {
        return index >= 1 && index <= MAX_COMMAND_CIRNOS;
    }

    @Nullable
    private static CompoundTag getSlotTag(Player player, int index) {
        if (!isValidSlotIndex(index)) return null;
        CompoundTag data = player.getPersistentData();
        String key = SLOT_KEYS[index - 1];
        return data.contains(key, 10) ? data.getCompound(key) : null;
    }

    private static void putSlotTag(Player player, int index, CompoundTag slot) {
        if (!isValidSlotIndex(index)) return;
        player.getPersistentData().put(SLOT_KEYS[index - 1], slot);
    }

    private static void clearSlotTag(Player player, int index) {
        if (!isValidSlotIndex(index)) return;
        player.getPersistentData().remove(SLOT_KEYS[index - 1]);
    }

    public static int getUsedCommandCirnoSlotCountPublic(Player player) {
        migrateLegacySlotIfNeeded(player);
        int count = 0;
        // Count Curious Cirno as one of the slots
        if (hasCurio(player)) {
            count = 1;
        }
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            if (getSlotTag(player, i) != null) count++;
        }
        return count;
    }

    public static boolean isCommandCirnoSlotUsedPublic(Player player, int index) {
        return getSlotTag(player, index) != null;
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
        CompoundTag slot = getSlotTag(player, index);
        if (slot == null || !slot.hasUUID(SLOT_UUID) || player.getServer() == null) return null;
        UUID uuid = slot.getUUID(SLOT_UUID);
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof CirnoEntity cirno && cirno.isAlive()) return cirno;
        }
        return null;
    }

    /** First live command-spawned Cirno across all slots, or null. Used by the V-key GUI. */
    @Nullable
    public static CirnoEntity findAnyLiveCommandCirnoPublic(Player player) {
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CirnoEntity cirno = findLiveCommandCirnoAtPublic(player, i);
            if (cirno != null) return cirno;
        }
        return null;
    }

    /**
     * All of this owner's currently live Cirnos, in a stable activation order: the
     * Curios-equipped companion first (if she's alive), then each occupied command
     * slot from 1..{@link #MAX_COMMAND_CIRNOS} in order — slots are always handed
     * out lowest-first when a new command Cirno is created, so slot order already
     * matches creation order. Used by {@link CirnoEntity}'s multi-Cirno follow
     * formation: an entity's index in this list picks its spot (left / right /
     * behind), and because the list is rebuilt fresh from who's actually alive
     * right now, storing or removing one of them automatically reflows everyone
     * else into the next spot up.
     */
    public static List<CirnoEntity> getOrderedLiveCirnosPublic(Player owner) {
        List<CirnoEntity> result = new ArrayList<>();
        ItemStack curioStack = getCatBurgerStack(owner);
        if (curioStack != null) {
            UUID curioUUID = getCompanionUUID(curioStack);
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
        migrateLegacySlotIfNeeded(owner);
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CirnoEntity cirno = findLiveCommandCirnoAtPublic(owner, i);
            if (cirno != null) result.add(cirno);
        }
        return result;
    }

    /**
     * Discards the live entity tracked by this slot (snapshotting her NBT into the
     * slot first), or — if her chunk simply isn't loaded right now, which is NOT
     * the same as her being gone — leaves the same "pending discard" breadcrumb
     * used elsewhere (removeCompanion(), CirnoEntity#tick()) so she's cleaned up
     * for real the moment her chunk loads, instead of surviving as a live,
     * untracked ghost. Always leaves the slot marked Stored=true.
     */
    private static void discardTrackedCirnoAt(Player player, int index, UUID uuid) {
        CompoundTag slot = getSlotTag(player, index);
        if (slot == null) slot = new CompoundTag();

        CirnoEntity cirno = findLiveCommandCirnoAtPublic(player, index);
        if (cirno != null) {
            CompoundTag snapshot = new CompoundTag();
            cirno.addAdditionalSaveData(snapshot);
            slot.put(SLOT_DATA, snapshot);
            cirno.allowNextRemoval();
            cirno.discard();
        } else {
            // Chunk not loaded — can't snapshot her directly right now. Fall back to
            // the shared "last known" auto-save snapshot as a best-effort restore
            // point (imperfect if multiple slots are unloaded at once, but strictly
            // better than losing her inventory outright).
            if (!slot.contains(SLOT_DATA, 10)) {
                CompoundTag durable = getDurableLastKnownCirnoData(player);
                if (durable != null) slot.put(SLOT_DATA, durable.copy());
            }
            addPendingDiscard(player, uuid, index);
        }
        slot.remove(SLOT_UUID);
        slot.putBoolean(SLOT_STORED, true);
        putSlotTag(player, index, slot);
    }

    /**
     * Stores (discards) this slot's Cirno, breadcrumb-safe even when her chunk
     * isn't currently loaded. Does nothing (returns false) if the slot isn't in
     * use or is already stored.
     */
    public static boolean storeCommandCirnoAt(Player player, int index) {
        CompoundTag slot = getSlotTag(player, index);
        if (slot == null || slot.getBoolean(SLOT_STORED)) return false;
        UUID uuid = slot.hasUUID(SLOT_UUID) ? slot.getUUID(SLOT_UUID) : null;
        if (uuid == null) {
            // No live UUID recorded — just flip the flag.
            slot.putBoolean(SLOT_STORED, true);
            putSlotTag(player, index, slot);
            return true;
        }
        discardTrackedCirnoAt(player, index, uuid);
        return true;
    }

    /** Stores every currently-live command Cirno slot. */
    public static void storeAllCommandCirno(Player player) {
        migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
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
        CompoundTag slot = getSlotTag(player, index);
        if (slot == null) return null;

        // Already alive — just re-apply the visibility preference instead of duplicating her.
        CirnoEntity already = findLiveCommandCirnoAtPublic(player, index);
        if (already != null) {
            already.setInvisible(!visible);
            slot.putBoolean(SLOT_VISIBLE, visible);
            slot.putBoolean(SLOT_STORED, false);
            putSlotTag(player, index, slot);
            return already;
        }

        UUID lastKnownUuid = slot.hasUUID(SLOT_UUID) ? slot.getUUID(SLOT_UUID) : null;
        CompoundTag snapshot = lastKnownUuid != null
                ? extractAndClearExternallyStoredCirnoForUuid(player, lastKnownUuid)
                : null;
        if (snapshot == null && slot.contains(SLOT_DATA, 10)) {
            snapshot = slot.getCompound(SLOT_DATA);
        }

        ServerLevel level = (ServerLevel) player.level();
        CirnoEntity cirno = InitEntity.CIRNO.get().create(level);
        if (cirno == null) return null;

        cirno.setOwnerPlayer(player);
        cirno.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
        cirno.applyModel();

        if (snapshot != null) {
            cirno.readAdditionalSaveData(snapshot);
            cirno.setOwnerUUID(player.getUUID());
            cirno.setTame(true);
            cirno.setOwnerPlayer(player);

            // If unload protection kept the original entity alive when it was stored
            // into the slab/photo, discard that ghost by UUID before addFreshEntity
            // so Minecraft doesn't see a duplicate and discard the new one instead.
            if (CatBurgerCommonConfig.cirnoUnloadProtection && snapshot.hasUUID("UUID")) {
                UUID ghostUUID = snapshot.getUUID("UUID");
                for (ServerLevel lvl : player.getServer().getAllLevels()) {
                    Entity ghost = lvl.getEntity(ghostUUID);
                    if (ghost instanceof CirnoEntity ghostCirno && ghostCirno.isAlive()) {
                        ghostCirno.allowNextRemoval();
                        ghostCirno.discard();
                        break;
                    }
                }
            }
        }

        cirno.setInvisible(!visible);
        level.addFreshEntity(cirno);

        slot.putUUID(SLOT_UUID, cirno.getUUID());
        slot.putBoolean(SLOT_STORED, false);
        slot.putBoolean(SLOT_VISIBLE, visible);
        slot.remove(SLOT_DATA);
        putSlotTag(player, index, slot);
        return cirno;
    }

    /** Restores every stored-but-in-use command Cirno slot. Returns how many were restored. */
    public static int restoreAllCommandCirno(Player player) {
        migrateLegacySlotIfNeeded(player);
        int restored = 0;
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = getSlotTag(player, i);
            if (slot == null || !slot.getBoolean(SLOT_STORED)) continue;
            boolean visible = slot.getBoolean(SLOT_VISIBLE);
            if (spawnOrRestoreCommandCirnoAt(player, i, visible) != null) restored++;
        }
        return restored;
    }

    /**
     * Creates a brand-new command Cirno in the first free slot (1..{@link #MAX_COMMAND_CIRNOS}).
     * Returns the 1-based slot index on success, {@code -1} if all slots are already
     * in use, or {@code -2} if entity creation itself failed while spawning.
     */
    public static int addCommandCirno(Player player, boolean spawn, boolean visible) {
        migrateLegacySlotIfNeeded(player);

        // Count Curious Cirno as one of the slots
        int usedSlots = 0;
        if (hasCurio(player)) {
            usedSlots = 1;
        }
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            if (getSlotTag(player, i) != null) {
                usedSlots++;
            }
        }
        if (usedSlots >= MAX_COMMAND_CIRNOS) return -1;

        int index = -1;
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            if (getSlotTag(player, i) == null) {
                index = i;
                break;
            }
        }
        if (index == -1) return -1;

        CompoundTag slot = new CompoundTag();
        slot.putBoolean(SLOT_VISIBLE, visible);
        slot.putBoolean(SLOT_STORED, true);
        putSlotTag(player, index, slot);

        if (spawn) {
            CirnoEntity cirno = spawnOrRestoreCommandCirnoAt(player, index, visible);
            if (cirno == null) {
                clearSlotTag(player, index);
                return -2;
            }
        }
        return index;
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
        CompoundTag slot = getSlotTag(player, index);
        if (slot == null) return false;

        if (slot.hasUUID(SLOT_UUID)) {
            UUID uuid = slot.getUUID(SLOT_UUID);
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
            extractAndClearExternallyStoredCirnoForUuid(player, uuid);
        }

        clearSlotTag(player, index);
        return true;
    }

    /** Removes every command Cirno slot. Returns how many were actually in use. */
    public static int removeAllCommandCirno(Player player) {
        migrateLegacySlotIfNeeded(player);
        int removed = 0;
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            if (removeCommandCirnoAt(player, i)) removed++;
        }
        return removed;
    }

    /** Short, human-readable status for one slot — used by {@code /cirno list}. */
    public static String describeCommandCirnoSlotPublic(Player player, int index) {
        CompoundTag slot = getSlotTag(player, index);
        if (slot == null) return "empty";
        boolean live = isCommandCirnoSlotLivePublic(player, index);
        boolean visiblePref = slot.getBoolean(SLOT_VISIBLE);
        if (live) {
            return "summoned (" + (visiblePref ? "visible" : "invisible") + ")";
        }
        return "stored (will be " + (visiblePref ? "visible" : "invisible") + " when summoned)";
    }

    /** Appends a deferred-discard breadcrumb; see {@link #NBT_PENDING_DISCARDS}. */
    private static void addPendingDiscard(Player player, UUID uuid, int slotIndex) {
        CompoundTag data = player.getPersistentData();
        net.minecraft.nbt.ListTag list = data.contains(NBT_PENDING_DISCARDS, 9)
                ? data.getList(NBT_PENDING_DISCARDS, 10)
                : new net.minecraft.nbt.ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID(SLOT_UUID, uuid);
        entry.putInt("Slot", slotIndex);
        list.add(entry);
        data.put(NBT_PENDING_DISCARDS, list);
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
        if (!data.contains(NBT_PENDING_DISCARDS, 9)) return;
        net.minecraft.nbt.ListTag list = data.getList(NBT_PENDING_DISCARDS, 10);
        UUID uuid = cirno.getUUID();

        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID(SLOT_UUID) || !entry.getUUID(SLOT_UUID).equals(uuid)) continue;

            int slotIndex = entry.getInt("Slot");
            CompoundTag slot = getSlotTag(player, slotIndex);
            if (slot == null) slot = new CompoundTag();

            CompoundTag snapshot = new CompoundTag();
            cirno.addAdditionalSaveData(snapshot);
            slot.put(SLOT_DATA, snapshot);
            slot.remove(SLOT_UUID);
            slot.putBoolean(SLOT_STORED, true);
            putSlotTag(player, slotIndex, slot);

            list.remove(i);
            data.put(NBT_PENDING_DISCARDS, list);

            cirno.allowNextRemoval();
            cirno.discard();
            return;
        }
    }

    public static void toggleTelekinesisMode(Player player) {
        if (!CatBurgerCommonConfig.telekinesisControlEnabled) return;
        if (!hasTKAccess(player)) return;
        if (!tkControlAllowed(player)) return;
        CompoundTag data = player.getPersistentData();
        boolean active = data.getBoolean(NBT_TK_MODE_ACTIVE);
        data.putBoolean(NBT_TK_MODE_ACTIVE, !active);
    }

    /** Enables telekinesis mode for command-spawned Cirno (bypasses curio requirement). */
    public static void enableTelekinesisMode(Player player) {
        if (!CatBurgerCommonConfig.telekinesisControlEnabled) return;
        player.getPersistentData().putBoolean(NBT_TK_MODE_ACTIVE, true);
    }

    public static void startTelekinesisControl(Player player, UUID targetUUID) {
        if (!CatBurgerCommonConfig.telekinesisControlEnabled) return;
        if (!hasTKAccess(player)) return;
        if (!tkControlAllowed(player)) return;
        if (player.level().isClientSide) return;

        if (targetUUID.equals(new UUID(0, 0))) {
            clearTelekinesisControl(player);
            return;
        }

        if (getTKControlCooldown(player) > 0) {
            int remaining = getTKControlCooldown(player) / 20;
            player.displayClientMessage(
                    Component.literal("§c[Cirno] Telekinesis on cooldown (" + remaining + "s)"),
                    true
            );
            return;
        }

        Entity target = ((ServerLevel) player.level()).getEntity(targetUUID);
        if (target == null || !target.isAlive() || target instanceof Player) return;
        // Reject entities that open a GUI on interact (e.g. TLM maids)
        if (target instanceof LivingEntity le && CompatManager.isTLMLoad() && TLMCompat.canRender(le)) return;

        CompoundTag data = player.getPersistentData();

        // If already controlling this exact mob, just refresh the hold flag — don't reset the timer
        if (data.hasUUID(NBT_TK_CONTROLLED_UUID) && targetUUID.equals(data.getUUID(NBT_TK_CONTROLLED_UUID))) {
            data.putBoolean(NBT_TK_HOLD_ACTIVE, true);
            return;
        }

        data.putUUID(NBT_TK_CONTROLLED_UUID, targetUUID);
        data.putInt(NBT_TK_CONTROL_TICKS_LEFT, CatBurgerCommonConfig.telekinesisControlDuration);
        data.putBoolean(NBT_TK_HOLD_ACTIVE, true);
        if (target instanceof Mob mob) mob.setNoAi(true);
        player.displayClientMessage(
                Component.literal("§b[Cirno] Controlling: " + target.getName().getString()),
                true
        );
    }

    public static void holdTelekinesisTarget(Player player, Vec3 eyePos, Vec3 lookDir) {
        if (player.level().isClientSide) return;
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(NBT_TK_CONTROLLED_UUID)) return;

        UUID controlledUUID = data.getUUID(NBT_TK_CONTROLLED_UUID);
        Entity controlled = ((ServerLevel) player.level()).getEntity(controlledUUID);
        if (controlled == null || (controlled instanceof LivingEntity le && !le.isAlive()) || controlled.isRemoved()) {
            clearTelekinesisControl(player);
            return;
        }

        double holdDist = 4.0;
        Vec3 targetPos = eyePos.add(lookDir.normalize().scale(holdDist));
        double targetX = targetPos.x;
        double targetY = targetPos.y - (controlled.getBbHeight() / 2.0);
        double targetZ = targetPos.z;

        // Check block collision at the desired position — don't suffocate mobs inside blocks
        AABB targetBB = controlled.getBoundingBox().move(
                targetX - controlled.getX(),
                targetY - controlled.getY(),
                targetZ - controlled.getZ()
        );
        boolean blocked = !player.level().noCollision(controlled, targetBB);
        if (!blocked) {
            controlled.teleportTo(targetX, targetY, targetZ);
        }
        // If blocked, entity stays at its current position (no move)

        controlled.setDeltaMovement(Vec3.ZERO);
        if (controlled instanceof Mob mob) mob.fallDistance = 0f;
        if (controlled instanceof Projectile proj) {
            // Keep projectile frozen — prevent it from hitting anything while held
            proj.setDeltaMovement(Vec3.ZERO);
        }
        controlled.hurtMarked = true;
        data.putBoolean(NBT_TK_HOLD_ACTIVE, true);
    }

    // ── Companion-required gate ───────────────────────────────────────────────

    /** Returns true if buffs are allowed given current config (requires curio companion). */
    private static boolean buffsAndTKAllowed() {
        if (CatBurgerCommonConfig.requireCompanionForBuffs) {
            return CatBurgerCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad();
        }
        return true;
    }

    /**
     * Returns true if telekinesis control is allowed for this player.
     * requireCompanionForBuffs=false: curio item alone is enough.
     * requireCompanionForBuffs=true: needs an active non-hidden Cirno
     *   (curio companion or any live command-slot Cirno).
     */
    private static boolean tkControlAllowed(Player player) {
        if (!CatBurgerCommonConfig.requireCompanionForBuffs) return true;
        // Curio companion must exist and not be hidden
        if (hasCurio(player)) {
            ItemStack stack = getCatBurgerStack(player);
            if (stack != null && getCompanionUUID(stack) != null && !getCompanionHidden(stack)) return true;
        }
        // Any live command-slot Cirno also counts
        return findAnyLiveCommandCirnoPublic(player) != null;
    }

    // ── Attribute buffs ───────────────────────────────────────────────────────

    public static void applyAttributeBuffs(Player player) {
        if (!CatBurgerCommonConfig.attributeBuffsEnabled) return;
        if (!buffsAndTKAllowed()) return;

        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && attack.getModifier(ATTACK_MODIFIER_UUID) == null) {
            attack.addPermanentModifier(new AttributeModifier(
                    ATTACK_MODIFIER_UUID, "catburger_attack_buff",
                    CatBurgerCommonConfig.bonusAttackDamage,
                    AttributeModifier.Operation.ADDITION));
        }

        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getModifier(HEALTH_MODIFIER_UUID) == null) {
            health.addPermanentModifier(new AttributeModifier(
                    HEALTH_MODIFIER_UUID, "catburger_health_buff",
                    CatBurgerCommonConfig.bonusMaxHealth,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    public static void removeAttributeBuffs(Player player) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.removeModifier(ATTACK_MODIFIER_UUID);

        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.removeModifier(HEALTH_MODIFIER_UUID);
    }

    // ── Telekinesis shield internals ──────────────────────────────────────────

    private static void activateTelekinesisShield(Player player) {
        CompoundTag data = player.getPersistentData();
        data.putInt(NBT_SHIELD_TICKS_LEFT, CatBurgerCommonConfig.telekinesisShieldDuration);
    }

    private static void tickShield(Player player) {
        CompoundTag data = player.getPersistentData();

        int cooldown = data.getInt(NBT_SHIELD_COOLDOWN);
        if (cooldown > 0) {
            data.putInt(NBT_SHIELD_COOLDOWN, cooldown - 1);
        }

        int ticksLeft = data.getInt(NBT_SHIELD_TICKS_LEFT);
        if (ticksLeft <= 0) return;

        data.putInt(NBT_SHIELD_TICKS_LEFT, ticksLeft - 1);

        if (ticksLeft - 1 <= 0) {
            data.putInt(NBT_SHIELD_COOLDOWN, CatBurgerCommonConfig.telekinesisShieldCooldown);
        }

        double range = CatBurgerCommonConfig.telekinesisRepelRange;
        Level level = player.level();
        Vec3 playerPos = player.position();
        Vec3 playerLook = player.getLookAngle();

        List<LivingEntity> nearby = level.getEntitiesOfClass(
                LivingEntity.class,
                new AABB(playerPos, playerPos).inflate(range),
                e -> e != player && !(e instanceof CirnoEntity) && e.isAlive()
        );

        for (LivingEntity entity : nearby) {
            Vec3 entityPos = entity.position();
            Vec3 diff = entityPos.subtract(playerPos);
            double dist = diff.length();
            if (dist < 0.01) continue;

            Vec3 repelTarget = playerPos.add(diff.normalize().scale(range + 1.0));
            entity.teleportTo(repelTarget.x, repelTarget.y, repelTarget.z);
            Vec3 knockback = diff.normalize().scale(1.5);
            entity.setDeltaMovement(knockback.x, 0.4, knockback.z);
        }
    }

    // ── Telekinesis control internals ─────────────────────────────────────────

    private static void tickTelekinesisControl(Player player) {
        CompoundTag data = player.getPersistentData();

        int cooldown = data.getInt(NBT_TK_CONTROL_COOLDOWN);
        if (cooldown > 0) {
            data.putInt(NBT_TK_CONTROL_COOLDOWN, cooldown - 1);
        }

        if (!data.hasUUID(NBT_TK_CONTROLLED_UUID)) return;

        boolean holdActive = data.getBoolean(NBT_TK_HOLD_ACTIVE);
        if (!holdActive) {
            clearTelekinesisControl(player);
            return;
        }
        data.putBoolean(NBT_TK_HOLD_ACTIVE, false);

        int ticksLeft = data.getInt(NBT_TK_CONTROL_TICKS_LEFT);
        if (ticksLeft <= 0) {
            clearTelekinesisControl(player);
        } else {
            data.putInt(NBT_TK_CONTROL_TICKS_LEFT, ticksLeft - 1);
            if (ticksLeft - 1 <= 0) {
                clearTelekinesisControl(player);
            }
        }
    }

    // ── Cirno mob retarget (proximity scan) ──────────────────────────────────

    /**
     * Every tick, find any Mob within 8 blocks of Cirno that is NOT targeting the owner,
     * and force-set its target to the owner. This handles the case where mobs can't
     * naturally target Cirno (canBeSeenAsEnemy = false) but are wandering nearby.
     * Only runs when cirnoRetargetAttackers is enabled.
     */
    private static void tickCirnoRetarget(Player player) {
        if (!CatBurgerCommonConfig.cirnoRetargetAttackers) return;
        if (!CatBurgerCommonConfig.companionEntityEnabled) return;

        ItemStack stack = getCatBurgerStack(player);
        if (stack == null) return;
        UUID cirnoUUID = getCompanionUUID(stack);
        if (cirnoUUID == null) return;

        ServerLevel level = (ServerLevel) player.level();
        Entity cirnoEntity = level.getEntity(cirnoUUID);
        if (!(cirnoEntity instanceof CirnoEntity cirno) || !cirno.isAlive()) return;

        Vec3 pos = cirno.position();
        List<Mob> nearby = level.getEntitiesOfClass(
                Mob.class,
                new AABB(pos, pos).inflate(8.0),
                mob -> mob.isAlive() && mob.getTarget() == cirno
        );
        for (Mob mob : nearby) {
            mob.setTarget(player);
        }
    }

    private static void clearTelekinesisControl(Player player) {
        CompoundTag data = player.getPersistentData();
        if (data.hasUUID(NBT_TK_CONTROLLED_UUID)) {
            UUID uuid = data.getUUID(NBT_TK_CONTROLLED_UUID);
            if (!player.level().isClientSide) {
                Entity e = ((ServerLevel) player.level()).getEntity(uuid);
                if (e instanceof Mob mob) {
                    mob.setNoAi(false);
                    mob.setDeltaMovement(Vec3.ZERO);
                } else if (e instanceof Projectile) {
                    // Release projectile — let gravity and momentum apply naturally
                    e.setDeltaMovement(player.getLookAngle().scale(1.5));
                }
            }
            data.remove(NBT_TK_CONTROLLED_UUID);
        }
        data.remove(NBT_TK_CONTROL_TICKS_LEFT);
        data.remove(NBT_TK_HOLD_ACTIVE);
        data.putInt(NBT_TK_CONTROL_COOLDOWN, CatBurgerCommonConfig.telekinesisControlCooldown);
    }

    // ── Companion helpers ─────────────────────────────────────────────────────

    /**
     * Ensures a companion exists for the player.
     * If the UUID on the stack already points to a live CirnoEntity, we just re-adopt it.
     * Only spawns a new entity when none is found, restoring NBT snapshot if available.
     * Blocks spawning if Cirno is stored in an external item (smart slab / photo).
     */
    public static void spawnCompanion(Player player, ItemStack stack) {
        if (player.level().isClientSide || player.getServer() == null) return;

        // Block spawn if Cirno is stored externally — she's not missing, just in a different container
        if (isStoredInExternalItem(player)) {
            return;
        }

        // Prefer a freshly re-queried, genuinely-live stack over whatever was passed in —
        // callers sometimes only have a copy handed to them by a Forge/Curios event.
        // If the re-query fails (e.g. CurioChangeEvent fires before Curios commits the slot),
        // fall back to the passed-in stack so we still have something to write the UUID onto.
        ItemStack liveStack = getCatBurgerStack(player);
        if (liveStack != null) {
            stack = liveStack;
        }

        UUID existingUUID = getCompanionUUID(stack);
        if (existingUUID != null) {
            // Look for the entity in any loaded level
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity e = level.getEntity(existingUUID);
                if (e instanceof CirnoEntity cirno && cirno.isAlive()) {
                    // Already alive — just re-assert ownership and model
                    cirno.setOwnerPlayer(player);
                    cirno.applyModel();
                    if (getCompanionHidden(stack)) cirno.setInvisible(true);
                    return;
                }
            }
            // UUID is stale — entity no longer exists
            clearCompanionUUID(stack);
        }

        // No adoption of random owned Cirnos (that used to steal command-slot
        // Cirnos when multiple were present). Right-click release of smart
        // slab/photo already writes the new UUID onto the correct tracker.

        // Spawn a brand-new entity
        ServerLevel serverLevel = (ServerLevel) player.level();
        CirnoEntity cirno = InitEntity.CIRNO.get().create(serverLevel);
        if (cirno == null) return;
        cirno.setOwnerPlayer(player);
        cirno.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0f);
        cirno.applyModel();

        // Restore inventory/state from snapshot if present, without overwriting identity
        CompoundTag savedData = getCompanionSavedData(stack);
        if (savedData == null) {
            savedData = getDurableLastKnownCirnoData(player);
        }
        if (savedData != null) {
            UUID newUUID = cirno.getUUID();
            UUID ownerUUID = player.getUUID();
            cirno.readAdditionalSaveData(savedData);
            // Re-apply identity — readAdditionalSaveData may have clobbered these
            cirno.setOwnerUUID(ownerUUID);
            cirno.setTame(true);
            // UUID is final on entities, so the new UUID sticks automatically
            // Re-stamp ownerPlayerUUID field via setOwnerPlayer
            cirno.setOwnerPlayer(player);
        }

        serverLevel.addFreshEntity(cirno);
        // Write UUID to the stack we have, then also try the live stack in case
        // Curios committed the slot after the event fired (covers both timing cases).
        setCompanionUUID(stack, cirno.getUUID());
        ItemStack nowLive = getCatBurgerStack(player);
        if (nowLive != null && nowLive != stack) {
            setCompanionUUID(nowLive, cirno.getUUID());
        }
    }

    /** Central, durable snapshot of Cirno's NBT — see CirnoEntity#remove / autosave. */
    public static void snapshotCirnoDurable(Player player, CirnoEntity cirno) {
        if (player == null || cirno == null) return;
        if (player.level().isClientSide) return;

        CompoundTag snapshot = new CompoundTag();
        cirno.addAdditionalSaveData(snapshot);

        CompoundTag pdata = player.getPersistentData();
        pdata.put(NBT_LAST_KNOWN_CIRNO_DATA, snapshot.copy());
        pdata.putLong(NBT_LAST_KNOWN_CIRNO_TIME, player.level().getGameTime());

        ItemStack liveStack = getCatBurgerStack(player);
        if (liveStack != null && cirno.getUUID().equals(getCompanionUUID(liveStack))) {
            setCompanionSavedData(liveStack, snapshot.copy());
        }

        migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = getSlotTag(player, i);
            if (slot != null && slot.hasUUID(SLOT_UUID) && cirno.getUUID().equals(slot.getUUID(SLOT_UUID))) {
                slot.put(SLOT_DATA, snapshot.copy());
                putSlotTag(player, i, slot);
                break;
            }
        }
    }

    /**
     * Called by CirnoEntity#remove() when she was just discarded by something
     * outside our own trusted call sites — in practice, TLM capturing her directly
     * into a smart slab / photo (or backpack, marriage, etc.). At this point the
     * live entity is really gone and TLM already has her data stored in the item,
     * but our own tracking (the Curios item's companion UUID/hidden flags, or the
     * command-mode UUID/stored flag) still thinks she's alive under the old UUID.
     * Left uncorrected, that stale state makes H-to-show, dimension change, login
     * and /cirno toggle either try to act on a dead UUID or spawn a duplicate
     * alongside the copy TLM just stored — which is why she "doesn't disappear"
     * (a phantom tracked instance lingers) when captured while command-spawned.
     */
    public static void handleExternalCapture(Player player, CirnoEntity cirno) {
        if (player.level().isClientSide) return;
        UUID uuid = cirno.getUUID();

        // Curios-equipped Cirno: mark the item hidden (not by player) and drop the
        // now-dead UUID so nothing tries to re-find her by it.
        ItemStack stack = getCatBurgerStack(player);
        if (stack != null && uuid.equals(getCompanionUUID(stack))) {
            setCompanionHidden(stack, true);
            setHiddenByPlayer(stack, false);
            clearCompanionUUID(stack);
            return;
        }

        // Command-spawned Cirno (no Curios item): find whichever slot was tracking
        // this exact entity and flip it to "stored" — but, unlike a normal player
        // store, KEEP her UUID on the slot rather than clearing it. That UUID is
        // now the only way to later find and match the specific smart-slab/photo
        // TLM just captured her into (see extractAndClearExternallyStoredCirnoForUuid),
        // which matters once more than one Cirno can be stored externally at once.
        migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = getSlotTag(player, i);
            if (slot != null && slot.hasUUID(SLOT_UUID) && uuid.equals(slot.getUUID(SLOT_UUID))) {
                slot.putBoolean(SLOT_STORED, true);
                putSlotTag(player, i, slot);
                return;
            }
        }
    }

    @Nullable
    public static CompoundTag getDurableLastKnownCirnoDataPublic(Player player) {
        return getDurableLastKnownCirnoData(player);
    }

    /** Public wrapper so the /cirno command (no Curios item) can reuse the same duplicate-spawn guard. */
    public static boolean isStoredInExternalItemPublic(Player player) {
        return isStoredInExternalItem(player);
    }

    /** Public wrapper so the /cirno command (no Curios item) can reuse the same storage-clear logic. */
    @Nullable
    public static CompoundTag extractAndClearExternallyStoredCirnoPublic(Player player) {
        return extractAndClearExternallyStoredCirno(player);
    }

    @Nullable
    private static CompoundTag getDurableLastKnownCirnoData(Player player) {
        CompoundTag pdata = player.getPersistentData();
        if (!pdata.contains(NBT_LAST_KNOWN_CIRNO_DATA, 10)) return null;
        return pdata.getCompound(NBT_LAST_KNOWN_CIRNO_DATA);
    }

    public static void removeCompanion(Player player, ItemStack stack) {
        UUID uuid = getCompanionUUID(stack);
        if (uuid == null) return;
        // Snapshot Cirno's entity data before discarding so inventory survives
        if (!player.level().isClientSide && player.getServer() != null) {
            boolean found = false;
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity e = level.getEntity(uuid);
                if (e instanceof CirnoEntity cirno) {
                    CompoundTag snapshot = new CompoundTag();
                    cirno.addAdditionalSaveData(snapshot);
                    setCompanionSavedData(stack, snapshot);
                    // Also write to durable player data — the stack passed in may be a
                    // Curios event copy that gets discarded, so this is the reliable path.
                    snapshotCirnoDurable(player, cirno);
                    if (e instanceof CirnoEntity c) c.allowNextRemoval();
                    e.discard();
                    found = true;
                    break;
                }
            }
            if (!found) {
                // Her chunk isn't loaded — level.getEntity() can't see her, so we can't
                // discard her right now. Leave a breadcrumb: CirnoEntity#tick() checks
                // this UUID as soon as her chunk loads again and discards herself then.
                // Without this she's left behind as a live, orphaned "ghost" entity while
                // we spawn a fresh one from the NBT snapshot below.
                player.getPersistentData().putUUID("CatBurgerPendingDiscardUUID", uuid);
            }
        }
        clearCompanionUUID(stack);
    }

    /**
     * Snapshots the live companion's NBT into durable player data WITHOUT discarding
     * her — she stays alive and visible in the world. Used on both death and logout
     * when cirnoPartOfOwner is false: a durable backup is still taken in case the
     * server stops or her chunk is later cleaned up by something else, but she isn't
     * actively removed.
     */
    private static void snapshotCompanionWithoutRemoving(Player player) {
        ItemStack stack = getCatBurgerStack(player);
        if (stack == null) return;
        UUID uuid = getCompanionUUID(stack);
        if (uuid == null || player.getServer() == null) return;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity e = level.getEntity(uuid);
            if (e instanceof CirnoEntity cirno) {
                snapshotCirnoDurable(player, cirno);
                break;
            }
        }
    }

    /**
     * Removes companion using only the player (scans item stacks for the UUID).
     * Used for death / logout where the stack reference may not be handy.
     */
    public static void removeCompanion(Player player) {
        ItemStack stack = getCatBurgerStack(player);
        if (stack != null) {
            removeCompanion(player, stack);
        }
    }

    /**
     * Called by CirnoEntity when she "dies" (only reachable when cirnoCanDie is enabled).
     * Rather than leaving a lingering invisible entity in the world with its own private
     * revive timer, death is treated exactly like the player-toggled Hidden state: she's
     * snapshotted into the item's NBT and discarded, and a revive countdown is tracked on
     * the player (see tickCirnoRevive) until she's respawned from that snapshot.
     */
    public static void handleCompanionDeath(Player player) {
        if (player.level().isClientSide) return;
        ItemStack stack = getCatBurgerStack(player);
        if (stack == null) return;

        // Guard against re-entering the death/storage flow if she's already hidden
        // (e.g. already dead-and-reviving, or manually hidden by the player) — without
        // this, a second die() call could re-snapshot a discarded entity or reset an
        // in-progress revive countdown.
        if (getCompanionHidden(stack)) return;

        removeCompanion(player, stack); // snapshots her NBT into the item and discards her
        setCompanionHidden(stack, true);
        // Death-hide is NOT a player H press — clear the flag so H-to-show stays blocked
        // until the revive timer fires and she's properly restored.
        setHiddenByPlayer(stack, false);

        int cooldown = CatBurgerCommonConfig.cirnoReviveCooldown;
        player.getPersistentData().putInt(NBT_CIRNO_REVIVE_TICKS_LEFT, cooldown);
        player.displayClientMessage(
                Component.literal("§b[Cirno] She'll be back in " + (cooldown / 20) + "s..."),
                true
        );
    }

    /** Counts down the death revive timer and respawns her from the stored snapshot when it elapses. */
    private static void tickCirnoRevive(Player player) {
        CompoundTag data = player.getPersistentData();
        int ticksLeft = data.getInt(NBT_CIRNO_REVIVE_TICKS_LEFT);
        if (ticksLeft <= 0) return;

        ticksLeft--;
        if (ticksLeft > 0) {
            data.putInt(NBT_CIRNO_REVIVE_TICKS_LEFT, ticksLeft);
            return;
        }

        data.remove(NBT_CIRNO_REVIVE_TICKS_LEFT);

        ItemStack stack = getCatBurgerStack(player);
        if (stack == null) return; // unequipped mid-revive — stays stored/hidden until re-equipped

        setCompanionHidden(stack, false);
        setHiddenByPlayer(stack, false);
        if (CatBurgerCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()) {
            spawnCompanion(player, stack);
        }
        player.displayClientMessage(Component.literal("§b[Cirno] She's back!"), true);
    }

    // ── Curio check ───────────────────────────────────────────────────────────

    private static boolean hasCurio(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(InitItem.CAT_BURGER.get()).isPresent())
                .orElse(false);
    }

    /** True if the player has at least one active command-spawned Cirno slot. */
    private static boolean hasCommandCirno(Player player) {
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            if (getSlotTag(player, i) != null) return true;
        }
        return false;
    }

    /** True if the player has TK access via curio OR command Cirno. */
    private static boolean hasTKAccess(Player player) {
        return hasCurio(player) || hasCommandCirno(player);
    }

    public static boolean hasCurioPublic(Player player) {
        return hasCurio(player);
    }

    public static boolean hasTKAccessPublic(Player player) {
        return hasTKAccess(player);
    }

    /** Summons (shows) the Curious Cirno if she's currently stored/hidden. */
    public static void summonCurioCompanion(Player player) {
        ItemStack stack = getCatBurgerStack(player);
        if (stack == null) return;
        if (player.getPersistentData().getInt(NBT_CIRNO_REVIVE_TICKS_LEFT) > 0) return;
        setCompanionHidden(stack, false);
        setHiddenByPlayer(stack, false);
        CompoundTag externalNBT = extractAndClearExternallyStoredCirno(player);
        if (externalNBT != null) setCompanionSavedData(stack, externalNBT);
        spawnCompanion(player, stack);
        sendVisibilitySync(player, false);
    }

    /** Stores (hides) the Curious Cirno if she's currently summoned. */
    public static void storeCurioCompanion(Player player) {
        ItemStack stack = getCatBurgerStack(player);
        if (stack == null) return;
        if (!getCompanionHidden(stack)) {
            removeCompanion(player, stack);
            setCompanionHidden(stack, true);
            setHiddenByPlayer(stack, true);
            sendVisibilitySync(player, true);
        }
    }

    /**
     * Returns the equipped CatBurger ItemStack, or null if not equipped.
     */
    @Nullable
    public static ItemStack getCatBurgerStack(Player player) {
        // LazyOptional doesn't have flatMap — resolve it manually
        Optional<SlotResult> result = CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.findFirstCurio(InitItem.CAT_BURGER.get()))
                .orElse(Optional.empty());
        return result.map(SlotResult::stack).orElse(null);
    }

    // ── ItemStack NBT helpers ─────────────────────────────────────────────────

    private static void setCompanionUUID(ItemStack stack, UUID uuid) {
        stack.getOrCreateTag().putUUID(ITEM_NBT_COMPANION_UUID, uuid);
    }

    @Nullable
    private static UUID getCompanionUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        return tag.hasUUID(ITEM_NBT_COMPANION_UUID) ? tag.getUUID(ITEM_NBT_COMPANION_UUID) : null;
    }

    private static void clearCompanionUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null) tag.remove(ITEM_NBT_COMPANION_UUID);
    }

    private static boolean getCompanionHidden(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(ITEM_NBT_COMPANION_HIDDEN);
    }

    private static void setCompanionHidden(ItemStack stack, boolean hidden) {
        stack.getOrCreateTag().putBoolean(ITEM_NBT_COMPANION_HIDDEN, hidden);
    }

    private static boolean isHiddenByPlayer(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(ITEM_NBT_HIDDEN_BY_PLAYER);
    }

    private static void setHiddenByPlayer(ItemStack stack, boolean value) {
        stack.getOrCreateTag().putBoolean(ITEM_NBT_HIDDEN_BY_PLAYER, value);
    }

    @Nullable
    private static CompoundTag getCompanionSavedData(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ITEM_NBT_COMPANION_DATA)) return null;
        return tag.getCompound(ITEM_NBT_COMPANION_DATA);
    }

    private static void setCompanionSavedData(ItemStack stack, CompoundTag data) {
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

    /** Public accessor used by packets. Returns UUID from either Curios or command-spawned Cirno. */
    public static UUID getCompanionUUIDPublic(Player player) {
        // First check Curios-based Cirno
        ItemStack stack = getCatBurgerStack(player);
        if (stack != null) {
            UUID curiosUUID = getCompanionUUID(stack);
            if (curiosUUID != null) return curiosUUID;
        }

        // Fallback: command-spawned Cirno, only when she's actually in the world.
        // With up to MAX_COMMAND_CIRNOS of her now possible, this opens the first
        // live one found — the V-key GUI has no per-slot picker of its own.
        migrateLegacySlotIfNeeded(player);
        CirnoEntity anyLive = findAnyLiveCommandCirnoPublic(player);
        if (anyLive != null) {
            return anyLive.getUUID();
        }

        return null;
    }

    // ── Player-PersistentData NBT helpers ─────────────────────────────────────

    private static int getShieldTicksLeft(Player player) {
        return player.getPersistentData().getInt(NBT_SHIELD_TICKS_LEFT);
    }

    private static int getShieldCooldown(Player player) {
        return player.getPersistentData().getInt(NBT_SHIELD_COOLDOWN);
    }

    private static int getTKControlCooldown(Player player) {
        return player.getPersistentData().getInt(NBT_TK_CONTROL_COOLDOWN);
    }

    // ── External storage cleanup ──────────────────────────────────────────────

    private static final String SMART_SLAB_HAS_MAID_ID = "touhou_little_maid:smart_slab_has_maid";
    private static final String SMART_SLAB_EMPTY_ID     = "touhou_little_maid:smart_slab_empty";
    private static final String PHOTO_ID                = "touhou_little_maid:photo";

    /**
     * Cached empty-slab Item resolved lazily on first use. Avoids a registry
     * lookup + ResourceLocation allocation on every emptyStorageReplacement call.
     */
    @Nullable
    private static Item cachedEmptySlabItem = null;
    private static boolean emptySlabLookupDone = false;

    /**
     * True if the stack is a smart slab / photo holding Cirno's data, and (when an
     * owner was recorded) it belongs to this player. Shared by every check/clear
     * path below so the item-id / NBT rules only live in one place.
     */
    private static boolean isCirnoStorageItem(ItemStack stack, UUID ownerUUID) {
        if (stack == null || stack.isEmpty()) return false;

        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return false;
        String itemId = id.toString();
        if (!SMART_SLAB_HAS_MAID_ID.equals(itemId) && !PHOTO_ID.equals(itemId)) return false;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("MaidInfo", 10)) return false;
        CompoundTag maidData = tag.getCompound("MaidInfo");
        if (!maidData.getBoolean("IsCatBurgerCompanion")) return false;

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
    private static boolean isCirnoStorageItemForUuid(ItemStack stack, UUID ownerUUID, UUID targetUuid) {
        if (!isCirnoStorageItem(stack, ownerUUID)) return false;
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
    private record CirnoSlot(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {}

    /** How far around the player to look for placed containers / dropped items. */
    private static final double NEARBY_STORAGE_RADIUS = 4.0;

    /**
     * How far to scan for stray dropped storage items during the per-tick dupe
     * cleanup. Covers the full loaded area around the player (~4 chunk radii) so
     * any ItemEntity that's actually loaded and could be right-clicked is caught.
     * Items beyond this can't be interacted with anyway.
     */
    private static final double DUPE_SCAN_RADIUS = 64.0;
    private static final String NBT_DUPE_SCAN_TICK = "CatBurgerDupeScanTick";

    private static void addIndexedListSlots(List<CirnoSlot> slots, List<ItemStack> list) {
        for (int i = 0; i < list.size(); i++) {
            int index = i;
            slots.add(new CirnoSlot(() -> list.get(index), replacement -> list.set(index, replacement)));
        }
    }

    private static void addContainerSlots(List<CirnoSlot> slots, Container container) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            int index = i;
            slots.add(new CirnoSlot(() -> container.getItem(index), replacement -> {
                container.setItem(index, replacement);
                container.setChanged();
            }));
        }
    }

    /**
     * Every placed storage block (chest, barrel, shulker box, hopper,
     * dispenser/dropper, furnace, brewing stand, ...) and inventory-carrying
     * entity (minecart chest, chest boat, laden llama/donkey, ...) within a small
     * bounded radius of the player. Plain getBlockEntity() map lookups over a
     * fixed-size cube plus one AABB entity query — no chunk loading is forced for
     * unloaded chunks, and this only runs at explicit call sites (H-toggle,
     * /cirno), never per-tick, so it can't become a background world scan.
     */
    private static List<Container> collectNearbyContainers(Player player) {
        List<Container> containers = new ArrayList<>();
        Level level = player.level();
        int r = (int) NEARBY_STORAGE_RADIUS;
        BlockPos center = player.blockPosition();

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof Container container) {
                containers.add(container);
            }
        }

        for (Entity entity : level.getEntitiesOfClass(Entity.class, player.getBoundingBox().inflate(NEARBY_STORAGE_RADIUS))) {
            if (entity instanceof Container container) {
                containers.add(container);
            }
        }

        return containers;
    }

    /**
     * Every nearby entity that can hold a Cirno-storage item directly, outside a
     * {@link Container}: an item frame's single displayed item, and a living
     * entity's worn/held equipment (a mob can pick up a dropped photo/smart slab
     * and wear or wield it — a smart slab is a block item, so it can even end up
     * in a mob's helmet slot). Other players are skipped here on purpose: their
     * own equipment shouldn't be silently cleared by someone else's cleanup, and
     * the acting player's own equipment is already covered by
     * {@link #collectCirnoSlots} via their inventory/offhand/armor lists.
     * Same bounded-radius, on-demand-only shape as {@link #collectNearbyContainers}.
     */
    private static void addNearbyEntityInventorySlots(List<CirnoSlot> slots, Player player) {
        for (Entity entity : player.level().getEntitiesOfClass(
                Entity.class, player.getBoundingBox().inflate(NEARBY_STORAGE_RADIUS))) {
            if (entity instanceof ItemFrame itemFrame) {
                slots.add(new CirnoSlot(itemFrame::getItem, itemFrame::setItem));
            } else if (entity instanceof LivingEntity living && !(living instanceof Player)) {
                for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                    slots.add(new CirnoSlot(
                            () -> living.getItemBySlot(equipmentSlot),
                            replacement -> living.setItemSlot(equipmentSlot, replacement)));
                }
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
    private static List<CirnoSlot> collectCirnoSlots(Player player) {
        List<CirnoSlot> slots = new ArrayList<>();

        addIndexedListSlots(slots, player.getInventory().items);
        addIndexedListSlots(slots, player.getInventory().offhand);
        addIndexedListSlots(slots, player.getInventory().armor);

        if (player.containerMenu != null) {
            for (Slot slot : player.containerMenu.slots) {
                slots.add(new CirnoSlot(slot::getItem, slot::set));
            }
        }

        addContainerSlots(slots, player.getEnderChestInventory());

        for (Container container : collectNearbyContainers(player)) {
            addContainerSlots(slots, container);
        }

        addNearbyEntityInventorySlots(slots, player);

        UUID ownerUUID = player.getUUID();
        List<ItemEntity> nearbyItems = player.level().getEntitiesOfClass(
                ItemEntity.class, player.getBoundingBox().inflate(NEARBY_STORAGE_RADIUS),
                e -> isCirnoStorageItem(e.getItem(), ownerUUID));
        for (ItemEntity itemEntity : nearbyItems) {
            slots.add(new CirnoSlot(itemEntity::getItem, replacement -> {
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
    private static boolean isStoredInExternalItem(Player player) {
        UUID ownerUUID = player.getUUID();
        for (CirnoSlot slot : collectCirnoSlots(player)) {
            if (isCirnoStorageItem(slot.getter().get(), ownerUUID)) return true;
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
        migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            CirnoEntity live = findLiveCommandCirnoAtPublic(player, i);
            if (live != null) liveElsewhereUUIDs.add(live.getUUID());
        }
        ItemStack catBurgerStack = getCatBurgerStack(player);
        if (catBurgerStack != null && !getCompanionHidden(catBurgerStack)) {
            UUID curiosUuid = getCompanionUUID(catBurgerStack);
            if (curiosUuid != null) liveElsewhereUUIDs.add(curiosUuid);
        }
        if (liveElsewhereUUIDs.isEmpty()) return;

        UUID ownerUUID = player.getUUID();
        for (Slot slot : event.getContainer().slots) {
            for (UUID uuid : liveElsewhereUUIDs) {
                if (tryExtractCirnoFromStackForUuid(slot.getItem(), ownerUUID, uuid, slot::set) != null) break;
            }
        }
    }

    /**
     * If the stack is Cirno's storage item, extracts her NBT and replaces the stack
     * via {@code setter} (empty smart slab, or nothing for a photo). Returns the
     * extracted NBT, or null if the stack didn't match.
     */
    @Nullable
    private static CompoundTag tryExtractCirnoFromStack(ItemStack stack, UUID ownerUUID,
                                                        java.util.function.Consumer<ItemStack> setter) {
        if (!isCirnoStorageItem(stack, ownerUUID)) return null;
        return extractCirnoStorageStack(stack, setter);
    }

    /**
     * Same as {@link #tryExtractCirnoFromStack}, but only extracts if the stored
     * entity's own saved UUID matches {@code targetUuid} — see
     * {@link #isCirnoStorageItemForUuid}.
     */
    @Nullable
    private static CompoundTag tryExtractCirnoFromStackForUuid(ItemStack stack, UUID ownerUUID, UUID targetUuid,
                                                               java.util.function.Consumer<ItemStack> setter) {
        if (!isCirnoStorageItemForUuid(stack, ownerUUID, targetUuid)) return null;
        return extractCirnoStorageStack(stack, setter);
    }

    private static CompoundTag extractCirnoStorageStack(ItemStack stack, java.util.function.Consumer<ItemStack> setter) {
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
     * Intercepts smart slab / photo right-click to spawn Cirno directly instead
     * of spawning a plain touhou_little_maid:maid when the stored maid has the
     * IsCatBurgerCompanion tag.
     */
    public static void handlerRightClickBlockForCirnoStorage(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;

        Item item = stack.getItem();
        String itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item).toString();

        // Only intercept smart slab (has maid) and photo items
        boolean isSmartSlab = SMART_SLAB_HAS_MAID_ID.equals(itemId);
        boolean isPhoto = PHOTO_ID.equals(itemId);
        if (!isSmartSlab && !isPhoto) return;

        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("MaidInfo", 10)) return;

        CompoundTag maidData = tag.getCompound("MaidInfo");
        if (!maidData.getBoolean("IsCatBurgerCompanion")) return;

        // Work out, BEFORE touching the event, which tracking system this stored
        // Cirno actually belongs to — matched by her own saved identity, NOT by
        // whether the player merely happens to have a curio equipped right now.
        // The old check routed to the curio branch whenever `getCatBurgerStack()`
        // was non-null, which silently "converted" an untouched command-mode
        // Cirno into the curio's own tracked companion the instant its owner
        // equipped a curio — and once that curio was later hidden/removed, the
        // command slot's own UUID pointed at nothing, losing that Cirno for good.
        ItemStack catBurgerStackPre = getCatBurgerStack(player);
        UUID storedUuid = maidData.hasUUID("UUID") ? maidData.getUUID("UUID") : null;
        UUID curioTrackedUuid = catBurgerStackPre != null ? getCompanionUUID(catBurgerStackPre) : null;

        boolean routeToCurio;
        int targetSlot = -1;

        if (storedUuid != null && curioTrackedUuid != null && storedUuid.equals(curioTrackedUuid)) {
            // Exact match: this really is the curio's own tracked companion.
            routeToCurio = true;
        } else {
            migrateLegacySlotIfNeeded(player);
            if (storedUuid != null) {
                for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
                    CompoundTag slot = getSlotTag(player, i);
                    if (slot != null && slot.hasUUID(SLOT_UUID) && storedUuid.equals(slot.getUUID(SLOT_UUID))) {
                        targetSlot = i;
                        break;
                    }
                }
            }
            if (targetSlot != -1) {
                // Exact match against a command slot's own last-known identity.
                routeToCurio = false;
            } else {
                // Completely untracked / brand-new capture with no identity to match
                // anywhere — fall back to the original heuristic: the curio if one
                // is equipped, otherwise command mode with the usual slot limit.
                routeToCurio = catBurgerStackPre != null;
                if (!routeToCurio) {
                    // Check total slots including Curious Cirno
                    int usedSlots = 0;
                    if (hasCurio(player)) {
                        usedSlots = 1;
                    }
                    for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
                        if (getSlotTag(player, i) != null) {
                            usedSlots++;
                        }
                    }

                    if (usedSlots >= MAX_COMMAND_CIRNOS) {
                        player.sendSystemMessage(Component.literal(
                                "§c[Cirno] Limit reached (" + MAX_COMMAND_CIRNOS + " total slots) — can't summon another."));
                        return;
                    }

                    for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
                        if (getSlotTag(player, i) == null) {
                            targetSlot = i;
                            break;
                        }
                    }
                }
            }
        }

        // This is Cirno stored in an external item — cancel the event and spawn her as CirnoEntity
        event.setCanceled(true);

        ServerLevel level = (ServerLevel) player.level();
        CirnoEntity cirno = InitEntity.CIRNO.get().create(level);
        if (cirno == null) return;

        // load() restores the entity's UUID from the stored NBT, giving the new
        // entity the same UUID as the original ghost that protection kept alive.
        // We must discard that ghost BEFORE addFreshEntity so Minecraft doesn't
        // see a duplicate UUID and discard the newly spawned one instead.
        cirno.load(maidData);
        cirno.setOwnerPlayer(player);

        // Discard the ghost with that exact UUID now that we've read it from the NBT.
        if (CatBurgerCommonConfig.cirnoUnloadProtection) {
            UUID ghostUUID = cirno.getUUID();
            for (ServerLevel lvl : player.getServer().getAllLevels()) {
                Entity ghost = lvl.getEntity(ghostUUID);
                if (ghost instanceof CirnoEntity ghostCirno && ghostCirno.isAlive()) {
                    ghostCirno.allowNextRemoval();
                    ghostCirno.discard();
                    break;
                }
            }
        }

        // Place her at the clicked location
        Vec3 clickPos = event.getHitVec().getLocation();
        cirno.moveTo(clickPos.x, clickPos.y, clickPos.z, player.getYRot(), 0f);

        // ── External storage cleanup ──────────────────────────────────────────
        // The smart slab stores Cirno as a touhou_little_maid:maid entity, so the
        // UUID in the slab's NBT belongs to that TLM type — it will never match a
        // live CirnoEntity.  When summoned, a fresh CirnoEntity is created and its
        // new UUID gets written to the tracking system (curio item / command slot).
        //
        // Dupe scenario: the player has two copies of the same slab and clicks both.
        // The FIRST click spawns Cirno and writes her new UUID into the tracker.
        // The SECOND click must not spawn a second Cirno.
        //
        // Fix: check the UUID already recorded in the tracking system (NOT the slab
        // UUID). If a live CirnoEntity with that UUID exists we have a dupe slab —
        // consume it silently and bail out.
        {
            UUID trackedUuid = null;
            if (routeToCurio && catBurgerStackPre != null) {
                // Curio branch: UUID the item currently tracks
                trackedUuid = getCompanionUUID(catBurgerStackPre);
            } else if (!routeToCurio && targetSlot != -1) {
                // Command branch: UUID the resolved slot currently tracks
                CompoundTag slotTag = getSlotTag(player, targetSlot);
                if (slotTag != null && slotTag.hasUUID(SLOT_UUID)) {
                    trackedUuid = slotTag.getUUID(SLOT_UUID);
                }
            }

            if (trackedUuid != null && player.getServer() != null) {
                for (ServerLevel lvl : player.getServer().getAllLevels()) {
                    Entity existing = lvl.getEntity(trackedUuid);
                    if (existing instanceof CirnoEntity existingCirno && existingCirno.isAlive()) {
                        // Tracker already points at a live Cirno — this slab is a dupe.
                        // Re-assert ownership, consume the slab, done.
                        existingCirno.setOwnerPlayer(player);
                        existingCirno.applyModel();
                        if (isSmartSlab) {
                            try {
                                Item emptySlabItem = net.minecraftforge.registries.ForgeRegistries.ITEMS
                                        .getValue(new ResourceLocation(SMART_SLAB_EMPTY_ID));
                                if (emptySlabItem != null) {
                                    player.setItemInHand(event.getHand(), new ItemStack(emptySlabItem));
                                }
                            } catch (Exception e) {
                                stack.shrink(1);
                            }
                        } else {
                            stack.shrink(1);
                        }
                        return; // keep the actual Cirno, discard the dupe
                    }
                }
            }
        }

        level.addFreshEntity(cirno);
        cirno.spawnExplosionParticle();
        cirno.playSound(net.minecraft.sounds.SoundEvents.PLAYER_SPLASH, 1.0F,
                level.random.nextFloat() * 0.1F + 0.9F);

        // Convert smart slab to empty, or delete photo
        if (isSmartSlab) {
            try {
                Item emptySlabItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(
                        new ResourceLocation(SMART_SLAB_EMPTY_ID)
                );
                if (emptySlabItem != null) {
                    player.setItemInHand(event.getHand(), new ItemStack(emptySlabItem));
                }
            } catch (Exception e) {
                stack.shrink(1);
            }
        } else {
            // Photo
            stack.shrink(1);
        }

        // Track this newly-spawned Cirno in whichever ONE tracking system was
        // actually resolved above by identity — never both, and never just because
        // a curio happens to be equipped.
        if (routeToCurio) {
            // Curios companion — track her on the item.
            setCompanionUUID(catBurgerStackPre, cirno.getUUID());
            setCompanionHidden(catBurgerStackPre, false);
            setHiddenByPlayer(catBurgerStackPre, false);
        } else {
            // No curio equipped — command-mode companion. Write her into the exact
            // slot resolved above so /cirno toggle and /cirno remove for that slot
            // get the fresh UUID instead of a stale/dead one.
            CompoundTag slot = getSlotTag(player, targetSlot);
            if (slot == null) slot = new CompoundTag();
            slot.putUUID(SLOT_UUID, cirno.getUUID());
            slot.putBoolean(SLOT_STORED, false);
            slot.putBoolean(SLOT_VISIBLE, true);
            slot.remove(SLOT_DATA);
            putSlotTag(player, targetSlot, slot);
        }
    }

    /**
     * Scans for a smart slab / photo item containing Cirno's IsCatBurgerCompanion
     * data and clears it wherever it currently is — using the exact same set of
     * locations as isStoredInExternalItem() (see collectCirnoSlots()), so the
     * check that blocks a duplicate spawn and the cleanup that follows can never
     * disagree about where she is.
     * Returns the extracted NBT, or null if not found.
     */
    @Nullable
    private static CompoundTag extractAndClearExternallyStoredCirno(Player player) {
        UUID ownerUUID = player.getUUID();
        for (CirnoSlot slot : collectCirnoSlots(player)) {
            CompoundTag extracted = tryExtractCirnoFromStack(slot.getter().get(), ownerUUID, slot.setter());
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
    private static CompoundTag extractAndClearExternallyStoredCirnoForUuid(Player player, UUID uuid) {
        UUID ownerUUID = player.getUUID();
        for (CirnoSlot slot : collectCirnoSlots(player)) {
            CompoundTag extracted = tryExtractCirnoFromStackForUuid(slot.getter().get(), ownerUUID, uuid, slot.setter());
            if (extracted != null) return extracted;
        }
        return null;
    }

    // ── Quick-Select screen data ──────────────────────────────────────────────

    /**
     * Builds a {@link net.zhaiji.catburger.network.client.packet.SlotSyncPacket} reflecting
     * the current live state of all slots for the given player.
     * Called server-side in response to {@link net.zhaiji.catburger.network.server.packet.RequestSlotSyncPacket}
     * and after any {@link net.zhaiji.catburger.network.server.packet.SlotActionPacket}.
     */
    public static net.zhaiji.catburger.network.client.packet.SlotSyncPacket buildSlotSyncPacket(Player player) {
        migrateLegacySlotIfNeeded(player);
        boolean hasCurio = hasCurio(player);
        java.util.List<net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotInfo> slots = new java.util.ArrayList<>();

        // Index 0: curio slot
        if (hasCurio) {
            ItemStack curioStack = getCatBurgerStack(player);
            boolean hidden = curioStack == null || getCompanionHidden(curioStack);
            net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotState curioState =
                    hidden
                            ? net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotState.STORED
                            : net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotState.SUMMONED;
            // Try to find the curio's live entity for model info
            String curioModelId = "";
            String curioYsmModelId = "";
            if (curioStack != null) {
                UUID uuid = getCompanionUUID(curioStack);
                CirnoEntity liveCurio = null;
                if (uuid != null && player.getServer() != null) {
                    outer:
                    for (ServerLevel lvl : player.getServer().getAllLevels()) {
                        net.minecraft.world.entity.Entity e = lvl.getEntity(uuid);
                        if (e instanceof CirnoEntity c) { liveCurio = c; break outer; }
                    }
                }
                if (liveCurio != null) {
                    curioModelId    = getCirnoModelId(liveCurio);
                    curioYsmModelId = getCirnoYsmModelId(liveCurio);
                } else {
                    // Stored — try snapshot saved on the item stack
                    CompoundTag snapshot = getCompanionSavedData(curioStack);
                    if (snapshot != null) {
                        curioModelId = snapshot.getString("ModelId");
                        curioYsmModelId = snapshot.getBoolean("IsYsmModel")
                                ? snapshot.getString("YsmModelId") : "";
                    }
                }
            }
            slots.add(new net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotInfo(
                    0, true, curioState, curioModelId, curioYsmModelId));
        }

        // Indices 1–4: command slots
        for (int i = 1; i <= MAX_COMMAND_CIRNOS; i++) {
            boolean used = isCommandCirnoSlotUsedPublic(player, i);
            if (!used) {
                slots.add(new net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotInfo(i, false,
                        net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotState.EMPTY));
            } else {
                boolean live = isCommandCirnoSlotLivePublic(player, i);
                CirnoEntity liveCirno = findLiveCommandCirnoAtPublic(player, i);
                String modelId;
                String ysmModelId;
                if (liveCirno != null) {
                    modelId    = getCirnoModelId(liveCirno);
                    ysmModelId = getCirnoYsmModelId(liveCirno);
                } else {
                    // Stored — read from snapshot NBT
                    CompoundTag slot = getSlotTag(player, i);
                    CompoundTag snapshot = (slot != null && slot.contains(SLOT_DATA, 10))
                            ? slot.getCompound(SLOT_DATA) : null;
                    modelId    = snapshot != null ? snapshot.getString("ModelId")    : "";
                    ysmModelId = (snapshot != null && snapshot.getBoolean("IsYsmModel"))
                            ? snapshot.getString("YsmModelId") : "";
                }
                slots.add(new net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotInfo(i, false,
                        live ? net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotState.SUMMONED
                                : net.zhaiji.catburger.network.client.packet.SlotSyncPacket.SlotState.STORED,
                        modelId, ysmModelId));
            }
        }

        return new net.zhaiji.catburger.network.client.packet.SlotSyncPacket(hasCurio, slots);
    }

    /** Extracts the TLM modelId string from a live CirnoEntity. Falls back to "" if unavailable. */
    private static String getCirnoModelId(CirnoEntity cirno) {
        try { return cirno.getModelId(); } catch (Exception e) { return ""; }
    }

    /** Extracts the YSM modelId from a live entity. Returns "" if not a YSM model. */
    private static String getCirnoYsmModelId(CirnoEntity cirno) {
        try {
            if (cirno.isYsmModel()) return cirno.getYsmModelId();
        } catch (Exception ignored) { }
        return "";
    }
}