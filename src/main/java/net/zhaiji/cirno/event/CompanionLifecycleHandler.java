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

public class CompanionLifecycleHandler {

    // ── Curio equip / unequip ─────────────────────────────────────────────────

    /** Called by the Curios hook (only registered when Curios is installed). */
    public static void handleCurioChange(Player player, ItemStack fromStack, ItemStack toStack) {
        if (player.level().isClientSide) return;

        Item cirno = InitItem.CIRNO.get();
        boolean wasEquipped = !fromStack.isEmpty() && fromStack.getItem() == cirno;
        boolean isEquipped  = !toStack.isEmpty()   && toStack.getItem()   == cirno;

        if (!wasEquipped && isEquipped) {
            // Equipped: apply attribute buffs regardless of companion mode
            TelekinesisHandler.applyAttributeBuffs(player);
            // Spawn companion only when enabled and TLM is loaded,
            // but not while the death-revive cooldown is still ticking — she'll
            // spawn automatically once the timer fires in tickCirnoRevive().
            if (CirnoCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()
                    && player.getPersistentData().getInt(CirnoStateAccess.NBT_CIRNO_REVIVE_TICKS_LEFT) <= 0) {
                spawnCompanion(player, toStack);
            }
        } else if (wasEquipped && !isEquipped) {
            // Unequipped: strip buffs/telekinesis only if no command Cirno is still
            // active to keep granting them — the buff is one shared grant, not
            // stacked per source, so it should only drop once every source is gone.
            if (!CirnoStateAccess.hasCommandCirno(player)) {
                TelekinesisHandler.removeAttributeBuffs(player);
                TelekinesisHandler.clearTelekinesisControl(player);
            }
            if (CirnoCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()) {
                removeCompanion(player, fromStack);
            }
        }
    }



    // ── Public action handlers (called by packets) ────────────────────────────

    public static void toggleCompanionVisibility(Player player) {
        if (player.level().isClientSide) return;

        CirnoStateAccess.migrateLegacySlotIfNeeded(player);

        // NOTE: previously, having a curio equipped made this method return early
        // after handling ONLY the curio's own Cirno, completely ignoring any
        // command-spawned ones. That's why H used to hide/show just one companion
        // (whichever the curio branch reached) instead of everyone the player has.
        // Both systems are now always evaluated together, driven by ONE shared
        // "is anything currently visible" decision, so H hides/shows all of them
        // as a single group no matter which combination the player has.
        ItemStack curioStack = CirnoStateAccess.hasCurio(player) ? CirnoStateAccess.getCirnoStack(player) : null;
        boolean hasCommandCirno = false;
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            if (CirnoStateAccess.getSlotTag(player, i) != null) { hasCommandCirno = true; break; }
        }

        if (curioStack == null && !hasCommandCirno) return;

        // If companion entity is disabled entirely, H can only hide (already-live entities
        // may exist from before the config was changed), never summon.
        if (!CirnoCommonConfig.companionEntityEnabled) {
            // Only act if something is currently visible — hide it and sync, then stop.
            boolean anyVisibleNow = false;
            if (curioStack != null && !CirnoStateAccess.getCompanionHidden(curioStack)) anyVisibleNow = true;
            if (!anyVisibleNow) {
                for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
                    if (CommandCirnoManager.isCommandCirnoSlotLivePublic(player, i)) { anyVisibleNow = true; break; }
                }
            }
            if (anyVisibleNow) {
                if (curioStack != null && !CirnoStateAccess.getCompanionHidden(curioStack)) {
                    removeCompanion(player, curioStack);
                    CirnoStateAccess.setCompanionHidden(curioStack, true);
                    CirnoStateAccess.setHiddenByPlayer(curioStack, true);
                }
                CommandCirnoManager.storeAllCommandCirno(player);
                sendVisibilitySync(player, true);
            }
            // Nothing visible → H does nothing
            return;
        }

        boolean curioVisible = curioStack != null && !CirnoStateAccess.getCompanionHidden(curioStack);
        boolean anyCommandLive = false;
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            if (CommandCirnoManager.isCommandCirnoSlotLivePublic(player, i)) {
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
                    CirnoStateAccess.setCompanionHidden(curioStack, true);
                    CirnoStateAccess.setHiddenByPlayer(curioStack, true);
                }
                // else: curio's Cirno is already hidden — nothing to do for her,
                // command-mode slots below still get stored.
            } else {
                // ── H pressed while hidden: only show if WE are the ones who hid her ──
                if (!CirnoStateAccess.isHiddenByPlayer(curioStack)) {
                    CirnoStateAccess.setHiddenByPlayer(curioStack, true);
                    // Still no visible change for the curio — but command-mode
                    // slots below may still have something to restore.
                } else if (player.getPersistentData().getInt(CirnoStateAccess.NBT_CIRNO_REVIVE_TICKS_LEFT) <= 0) {
                    // Share the same loaded-item fallback and pending cleanup
                    // behavior as /cirno summon and the Curio command.
                    summonCurioCompanion(player);
                }
            }
        }

        if (hasCommandCirno) {
            if (hidingEverything) {
                CommandCirnoManager.storeAllCommandCirno(player);
            } else {
                CommandCirnoManager.restoreAllCommandCirno(player);
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
     * Cirno slot (1..{@link CirnoStateAccess#MAX_COMMAND_CIRNOS}) as a clickable chat line so the
     * player can summon/store/remove one specific Cirno, or add a new one, without
     * a dedicated screen. This is entirely about the command-mode slots and has
     * nothing to do with the Curios-equipped companion, so it works the same way
     * whether or not a curio happens to be equipped — the previous "does nothing
     * if a curio is equipped" gate here was a bug, not intentional scoping.
     */
    public static void openCirnoMenu(Player player) {
        if (player.level().isClientSide) return;

        CirnoStateAccess.migrateLegacySlotIfNeeded(player);

        player.sendSystemMessage(Component.literal("§b[Cirno] ── Companion control ──"));

        // ── Curious Cirno slot (curio item) ──────────────────────────────────
        if (CirnoStateAccess.hasCurio(player)) {
            ItemStack curioStack = CirnoStateAccess.getCirnoStack(player);
            boolean curioHidden = curioStack == null || CirnoStateAccess.getCompanionHidden(curioStack);
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
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            final int slotIndex = i;
            boolean used = CommandCirnoManager.isCommandCirnoSlotUsedPublic(player, i);
            boolean live = CommandCirnoManager.isCommandCirnoSlotLivePublic(player, i);
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
        if (CirnoStorageHandler.isStoredInExternalItem(player)) {
            return;
        }

        // Prefer a freshly re-queried, genuinely-live stack over whatever was passed in —
        // callers sometimes only have a copy handed to them by a Forge/Curios event.
        // If the re-query fails (e.g. CurioChangeEvent fires before Curios commits the slot),
        // fall back to the passed-in stack so we still have something to write the UUID onto.
        ItemStack liveStack = CirnoStateAccess.getCirnoStack(player);
        if (liveStack != null) {
            stack = liveStack;
        }

        // She died for real (cirnoSpawnTombstoneOnDeath = true) or was externally
        // captured, and nothing has actually revived her yet. Every legitimate
        // revival path (summonCurioCompanion, toggleCompanionVisibility, the
        // storage-item right-click handler, tickCirnoRevive) clears this flag
        // itself, right before calling us, once it's found something to revive
        // her from. If it's still set here, this is an unconditional call site
        // (curio equip, dimension change, login) that must NOT be allowed to
        // conjure a free blank replacement while she's still gone.
        if (CirnoStateAccess.getCompanionHidden(stack) && CirnoStateAccess.requiresItemRevive(stack)) {
            return;
        }

        UUID existingUUID = CirnoStateAccess.getCompanionUUID(stack);
        if (existingUUID != null) {
            // Look for the entity in any loaded level — and, if she isn't resident in
            // memory anywhere, give her last-known chunk a chance to load from disk
            // first (see findLoadedEntity's doc). Skipping that recovery step here is
            // exactly what used to let a dormant-but-still-real Cirno be declared
            // stale and replaced with a fresh snapshot copy below, while the original
            // sat fully intact elsewhere, ready to reappear later as a duplicate.
            Entity e = CirnoStateAccess.findLoadedEntity(player, existingUUID);
            if (e instanceof CirnoEntity cirno && cirno.isAlive()) {
                // Already alive — just re-assert ownership and model
                cirno.setOwnerPlayer(player);
                if (CirnoStateAccess.getCompanionHidden(stack)) cirno.setInvisible(true);
                // Owner summoned her (H / Maid Panel) — don't leave her asleep in the Maid Bed.
                cirno.wakeFromMaidBed();
                return;
            }
            // UUID is stale — entity no longer exists
            CirnoStateAccess.clearCompanionUUID(stack);
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

        // Restore inventory/state from snapshot if present, without overwriting identity.
        //
        // Prefer the durable per-player snapshot over the item's own NBT tag, not the
        // other way around. Writes to `stack` are the unreliable side here: Curios can
        // hand us an event-copy ItemStack whose tag mutations never make it back into
        // the real slot (see the comments throughout this class), so the item tag can
        // be sitting on an OLD snapshot indefinitely while the player-scoped data keeps
        // getting refreshed correctly. Trusting "item tag present" over "which one is
        // actually newer" is exactly what silently restored her from a stale backup —
        // items gained since went missing, and items removed since came back duplicated.
        CompoundTag savedData = getDurableLastKnownCirnoData(player);
        if (savedData == null) {
            savedData = CirnoStateAccess.getCompanionSavedData(stack);
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

            // A saved identity may still be alive in another loaded level (for
            // example, an item copy). Keep that entity and truly discard this
            // new candidate without running its save/capture hooks.
            if (savedData.hasUUID("UUID")) {
                Entity active = CirnoStateAccess.findLoadedEntity(player, savedData.getUUID("UUID"));
                if (active instanceof CirnoEntity existing && existing.isAlive()) {
                    cirno.discardDuplicate();
                    existing.setOwnerPlayer(player);
                    existing.setInvisible(CirnoStateAccess.getCompanionHidden(stack));
                    existing.wakeFromMaidBed();
                    CirnoStateAccess.setCompanionUUID(stack, existing.getUUID());
                    return;
                }
            }
        }

        serverLevel.addFreshEntity(cirno);
        // The NBT snapshot restored above can carry over a sleeping pose if she
        // was captured mid-sleep — make sure a freshly (re)spawned companion
        // never appears already stuck asleep in front of the owner.
        cirno.wakeFromMaidBed();
        // Write UUID to the stack we have, then also try the live stack in case
        // Curios committed the slot after the event fired (covers both timing cases).
        CirnoStateAccess.setCompanionUUID(stack, cirno.getUUID());
        ItemStack nowLive = CirnoStateAccess.getCirnoStack(player);
        if (nowLive != null && nowLive != stack) {
            CirnoStateAccess.setCompanionUUID(nowLive, cirno.getUUID());
        }
    }



    /**
     * Safety net against duplicates. True when {@code self} is a Curious-Cirno-style
     * companion that is NOT the identity currently tracked on the equipped curio,
     * while the tracked identity is itself alive somewhere. That is exactly the
     * "original woke up after a replacement was already spawned" situation, so the
     * untracked one should remove itself. Deliberately conservative: never true for
     * anything tracked by a command slot, when no curio is equipped, or when the
     * curio's Cirno is hidden/unresolved.
     */
    public static boolean isOrphanedDuplicate(Player owner, CirnoEntity self) {
        if (owner.level().isClientSide || owner.getServer() == null) return false;
        UUID id = self.getUUID();
        if (CirnoStateAccess.isTrackedCompanionUUID(owner, id)) return false;

        ItemStack curio = CirnoStateAccess.getCirnoStack(owner);
        if (curio == null || CirnoStateAccess.getCompanionHidden(curio)) return false;
        UUID tracked = CirnoStateAccess.getCompanionUUID(curio);
        if (tracked == null || tracked.equals(id)) return false;

        for (ServerLevel level : owner.getServer().getAllLevels()) {
            Entity e = level.getEntity(tracked);
            if (e instanceof CirnoEntity other && other != self && other.isAlive()) return true;
        }
        return false;
    }



    /** Central, durable snapshot of Cirno's NBT — see CirnoEntity#remove / autosave. */
    public static void snapshotCirnoDurable(Player player, CirnoEntity cirno) {
        if (player == null || cirno == null) return;
        if (player.level().isClientSide) return;

        CompoundTag snapshot = new CompoundTag();
        cirno.addAdditionalSaveData(snapshot);

        // Identify a command-slot Cirno FIRST. Slot UUIDs live directly in the
        // player's own persistent data, so this match is authoritative and immune
        // to Curios-stack timing issues (mid-removal, event-copy stacks, etc.) —
        // unlike re-resolving CirnoStateAccess.getCirnoStack() and comparing UUIDs, which can
        // transiently disagree with reality at exactly the moments this snapshot
        // matters most (removal, death). Only when this Cirno is definitely NOT
        // tracked by any command slot do we fall through and treat her as the
        // curio companion, by elimination.
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = CirnoStateAccess.getSlotTag(player, i);
            if (slot != null && slot.hasUUID(CirnoStateAccess.SLOT_UUID) && cirno.getUUID().equals(slot.getUUID(CirnoStateAccess.SLOT_UUID))) {
                // Command slot: she already has her own reliable, correctly-scoped
                // storage (CirnoStateAccess.SLOT_DATA) — deliberately NOT also mirrored into the
                // curio-only durable fallback below. That cross-contamination is
                // exactly what used to let the curio companion adopt/duplicate a
                // command Cirno.
                slot.put(CirnoStateAccess.SLOT_DATA, snapshot.copy());
                CirnoStateAccess.putSlotTag(player, i, slot);
                return;
            }
        }

        // Not a command slot — this is the curio companion. ALWAYS refresh the
        // durable per-player fallback here, unconditionally, same as before this
        // method distinguished identities at all: the ItemStack instance a caller
        // is holding (or that CirnoStateAccess.getCirnoStack() resolves right this tick) can be
        // a stale/mid-transaction Curios copy — most importantly during
        // removal/death, exactly when this snapshot matters most — so gating this
        // write on a live-stack UUID match let her latest inventory silently fail
        // to save at the worst possible moment: item loss, and a later restore
        // from that stale snapshot duplicating items already taken off her.
        CompoundTag pdata = player.getPersistentData();
        pdata.put(CirnoStateAccess.NBT_LAST_KNOWN_CIRNO_DATA, snapshot.copy());
        pdata.putLong(CirnoStateAccess.NBT_LAST_KNOWN_CIRNO_TIME, player.level().getGameTime());

        ItemStack liveStack = CirnoStateAccess.getCirnoStack(player);
        if (liveStack != null) {
            CirnoStateAccess.setCompanionSavedData(liveStack, snapshot.copy());
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
        ItemStack stack = CirnoStateAccess.getCirnoStack(player);
        if (stack != null && uuid.equals(CirnoStateAccess.getCompanionUUID(stack))) {
            CirnoStateAccess.setCompanionHidden(stack, true);
            CirnoStateAccess.setHiddenByPlayer(stack, false);
            CirnoStateAccess.setRequiresItemRevive(stack, true);
            CirnoStateAccess.clearCompanionUUID(stack);
            return;
        }

        // Command-spawned Cirno (no Curios item): find whichever slot was tracking
        // this exact entity and flip it to "stored" — but, unlike a normal player
        // store, KEEP her UUID on the slot rather than clearing it. That UUID is
        // now the only way to later find and match the specific smart-slab/photo
        // TLM just captured her into (see extractAndClearExternallyStoredCirnoForUuid),
        // which matters once more than one Cirno can be stored externally at once.
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = CirnoStateAccess.getSlotTag(player, i);
            if (slot != null && slot.hasUUID(CirnoStateAccess.SLOT_UUID) && uuid.equals(slot.getUUID(CirnoStateAccess.SLOT_UUID))) {
                slot.putBoolean(CirnoStateAccess.SLOT_STORED, true);
                slot.putBoolean(CirnoStateAccess.SLOT_DEAD, true);
                // Keep the just-saved snapshot as fallback while the external
                // item's chunk may be unloaded. Its UUID is queued for cleanup if
                // fallback revival happens before the item can be reached.
                CirnoStateAccess.putSlotTag(player, i, slot);
                return;
            }
        }
    }



    /**
     * Called by CirnoEntity#die() when cirnoSpawnTombstoneOnDeath = true, right
     * before handing off to TLM's own death handling (which drops a real
     * tombstone/photo/film via dropEquipment()). Unlike handleCompanionDeath (the
     * tombstone-off auto-revive-on-a-timer flow), there is no automatic comeback
     * here: whichever tracking system owns her — the Curios item or a command
     * slot — is flagged exactly like an external capture. Summoning first tries
     * the TLM item; if it is outside loaded scan coverage, the last snapshot is
     * used and the stored UUID is queued for cleanup when its chunk is reachable.
     */
    public static void handleTombstoneDeath(Player player, CirnoEntity cirno) {
        // Start the revive cooldown (cirnoReviveCooldown) BEFORE the capture logic
        // below clears the curio's tracked UUID — we need that UUID to know which
        // tracker (curio vs command slot) she belongs to. Until it elapses she can't
        // be brought back from the film / photo / slab, by any path.
        if (!player.level().isClientSide && CirnoCommonConfig.cirnoReviveCooldown > 0) {
            long readyAt = player.level().getGameTime() + CirnoCommonConfig.cirnoReviveCooldown;
            UUID uuid = cirno.getUUID();
            boolean marked = false;

            CirnoStateAccess.migrateLegacySlotIfNeeded(player);
            for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
                CompoundTag slot = CirnoStateAccess.getSlotTag(player, i);
                if (slot != null && slot.hasUUID(CirnoStateAccess.SLOT_UUID)
                        && uuid.equals(slot.getUUID(CirnoStateAccess.SLOT_UUID))) {
                    slot.putLong(CirnoStateAccess.SLOT_REVIVE_READY_AT, readyAt);
                    CirnoStateAccess.putSlotTag(player, i, slot);
                    marked = true;
                    break;
                }
            }
            if (!marked) {
                ItemStack curio = CirnoStateAccess.getCirnoStack(player);
                if (curio != null && uuid.equals(CirnoStateAccess.getCompanionUUID(curio))) {
                    player.getPersistentData().putLong(CirnoStateAccess.NBT_CIRNO_REVIVE_READY_AT, readyAt);
                }
            }
            player.displayClientMessage(Component.literal(
                    "§b[Cirno] She can be revived in " + (CirnoCommonConfig.cirnoReviveCooldown / 20) + "s."), true);
        }
        handleExternalCapture(player, cirno);
    }



    @Nullable
    public static CompoundTag getDurableLastKnownCirnoDataPublic(Player player) {
        return getDurableLastKnownCirnoData(player);
    }



    /** Public wrapper so the /cirno command (no Curios item) can reuse the same duplicate-spawn guard. */
    public static boolean isStoredInExternalItemPublic(Player player) {
        return CirnoStorageHandler.isStoredInExternalItem(player);
    }



    /** Public wrapper so the /cirno command (no Curios item) can reuse the same storage-clear logic. */
    @Nullable
    public static CompoundTag extractAndClearExternallyStoredCirnoPublic(Player player) {
        return CirnoStateAccess.extractAndClearExternallyStoredCirno(player);
    }



    @Nullable
    static CompoundTag getDurableLastKnownCirnoData(Player player) {
        CompoundTag pdata = player.getPersistentData();
        if (!pdata.contains(CirnoStateAccess.NBT_LAST_KNOWN_CIRNO_DATA, 10)) return null;
        return pdata.getCompound(CirnoStateAccess.NBT_LAST_KNOWN_CIRNO_DATA);
    }



    public static void removeCompanion(Player player, ItemStack stack) {
        UUID uuid = CirnoStateAccess.getCompanionUUID(stack);
        if (uuid == null) return;
        // Snapshot Cirno's entity data before discarding so inventory survives
        if (!player.level().isClientSide && player.getServer() != null) {
            boolean found = false;
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity e = level.getEntity(uuid);
                if (e instanceof CirnoEntity cirno) {
                    CompoundTag snapshot = new CompoundTag();
                    cirno.addAdditionalSaveData(snapshot);
                    CirnoStateAccess.setCompanionSavedData(stack, snapshot);
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
                player.getPersistentData().putUUID("CirnoPendingDiscardUUID", uuid);
            }
        }
        CirnoStateAccess.clearCompanionUUID(stack);
    }



    /**
     * Snapshots the live companion's NBT into durable player data WITHOUT discarding
     * her — she stays alive and visible in the world. Used on both death and logout
     * when cirnoPartOfOwner is false: a durable backup is still taken in case the
     * server stops or her chunk is later cleaned up by something else, but she isn't
     * actively removed.
     */
    static void snapshotCompanionWithoutRemoving(Player player) {
        ItemStack stack = CirnoStateAccess.getCirnoStack(player);
        if (stack == null) return;
        UUID uuid = CirnoStateAccess.getCompanionUUID(stack);
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
        ItemStack stack = CirnoStateAccess.getCirnoStack(player);
        if (stack != null) {
            removeCompanion(player, stack);
        }
    }



    /**
     * Called by CirnoEntity when she "dies" and cirnoSpawnTombstoneOnDeath = false
     * (only reachable when cirnoCanDie is also enabled). Rather than leaving a
     * lingering invisible entity in the world, death is treated exactly like the
     * player-toggled Hidden state: she's snapshotted and discarded, and a revive
     * countdown runs until she's respawned from that snapshot — no external item
     * required either way. Works for either identity, matched the same way
     * snapshotCirnoDurable()/handleExternalCapture() do: the Curios-equipped
     * companion (countdown lives on the player, see tickCirnoRevive), or a
     * command slot (countdown lives on that slot, see tickCommandCirnoRevive).
     * Previously this only handled the Curios case and silently did nothing —
     * healing her back to full HP without ever actually removing her — for a
     * command-spawned Cirno, which is why she "didn't die" outside the curio.
     */
    public static void handleCompanionDeath(Player player, CirnoEntity cirno) {
        if (player.level().isClientSide) return;
        UUID uuid = cirno.getUUID();

        // Command slot first, same precedence as snapshotCirnoDurable/handleExternalCapture.
        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = CirnoStateAccess.getSlotTag(player, i);
            if (slot == null || !slot.hasUUID(CirnoStateAccess.SLOT_UUID) || !uuid.equals(slot.getUUID(CirnoStateAccess.SLOT_UUID))) continue;

            // Guard against re-entering if she's already stored/reviving.
            if (slot.getBoolean(CirnoStateAccess.SLOT_STORED)) return;

            CompoundTag snapshot = new CompoundTag();
            cirno.addAdditionalSaveData(snapshot);
            slot.put(CirnoStateAccess.SLOT_DATA, snapshot);
            cirno.allowNextRemoval();
            cirno.discard();

            slot.remove(CirnoStateAccess.SLOT_UUID);
            slot.putBoolean(CirnoStateAccess.SLOT_STORED, true);
            slot.putBoolean(CirnoStateAccess.SLOT_DEAD, false);
            int cooldown = CirnoCommonConfig.cirnoReviveCooldown;
            slot.putInt(CirnoStateAccess.SLOT_REVIVE_TICKS_LEFT, cooldown);
            CirnoStateAccess.putSlotTag(player, i, slot);

            player.displayClientMessage(
                    Component.literal("§b[Cirno] She'll be back in " + (cooldown / 20) + "s..."),
                    true
            );
            return;
        }

        // Otherwise: the Curios-equipped companion.
        ItemStack stack = CirnoStateAccess.getCirnoStack(player);
        if (stack == null) return;

        // Guard against re-entering the death/storage flow if she's already hidden
        // (e.g. already dead-and-reviving, or manually hidden by the player) — without
        // this, a second die() call could re-snapshot a discarded entity or reset an
        // in-progress revive countdown.
        if (CirnoStateAccess.getCompanionHidden(stack)) return;

        removeCompanion(player, stack); // snapshots her NBT into the item and discards her
        CirnoStateAccess.setCompanionHidden(stack, true);
        // Death-hide is NOT a player H press — clear the flag so H-to-show stays blocked
        // until the revive timer fires and she's properly restored.
        CirnoStateAccess.setHiddenByPlayer(stack, false);
        CirnoStateAccess.setRequiresItemRevive(stack, false); // recoverable from our own snapshot, no item needed

        int cooldown = CirnoCommonConfig.cirnoReviveCooldown;
        player.getPersistentData().putInt(CirnoStateAccess.NBT_CIRNO_REVIVE_TICKS_LEFT, cooldown);
        player.displayClientMessage(
                Component.literal("§b[Cirno] She'll be back in " + (cooldown / 20) + "s..."),
                true
        );
    }



    /** Counts down the death revive timer and respawns her from the stored snapshot when it elapses. */
    static void tickCirnoRevive(Player player) {
        CompoundTag data = player.getPersistentData();
        int ticksLeft = data.getInt(CirnoStateAccess.NBT_CIRNO_REVIVE_TICKS_LEFT);
        if (ticksLeft <= 0) return;

        ticksLeft--;
        if (ticksLeft > 0) {
            data.putInt(CirnoStateAccess.NBT_CIRNO_REVIVE_TICKS_LEFT, ticksLeft);
            return;
        }

        data.remove(CirnoStateAccess.NBT_CIRNO_REVIVE_TICKS_LEFT);

        ItemStack stack = CirnoStateAccess.getCirnoStack(player);
        if (stack == null) return; // unequipped mid-revive — stays stored/hidden until re-equipped

        CirnoStateAccess.setCompanionHidden(stack, false);
        CirnoStateAccess.setHiddenByPlayer(stack, false);
        CirnoStateAccess.setRequiresItemRevive(stack, false);
        if (CirnoCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()) {
            spawnCompanion(player, stack);
        }
        player.displayClientMessage(Component.literal("§b[Cirno] She's back!"), true);
    }



    /** Summons (shows) the Curious Cirno if she's currently stored/hidden. */
    public static void summonCurioCompanion(Player player) {
        ItemStack stack = CirnoStateAccess.getCirnoStack(player);
        if (stack == null) return;
        if (player.getPersistentData().getInt(CirnoStateAccess.NBT_CIRNO_REVIVE_TICKS_LEFT) > 0) return;
        long readyLeft = CirnoStateAccess.curioReviveReadyRemaining(player);
        if (readyLeft > 0) {
            player.displayClientMessage(CirnoStateAccess.reviveCooldownMessage(readyLeft), true);
            return;
        }

        CompoundTag externalNBT = CirnoStateAccess.extractAndClearExternallyStoredCirno(player, CirnoStateAccess.canSummonFromFilm());

        if (CirnoStateAccess.requiresItemRevive(stack) && externalNBT == null) {
            // Resolve the best-known identity for this curio's Cirno FIRST —
            // same lookup order used below — so the film gate check right after
            // can match the exact film that would hold her, not just "any film
            // this player happens to be carrying". The curio's own tracked UUID
            // is null exactly while she's captured in an external item (see
            // bindRevivedCirno's comment), which is precisely the case this
            // gate needs to get right, so falling back to null here (which
            // would match ANY player-owned film, including an unrelated command
            // slot's) is not good enough.
            CompoundTag fallback = CirnoStateAccess.getCompanionSavedData(stack);
            if (fallback == null) fallback = getDurableLastKnownCirnoData(player);
            UUID storedUuid = fallback != null && fallback.hasUUID("UUID")
                    ? fallback.getUUID("UUID") : CirnoStateAccess.getCompanionUUID(stack);

            // A film holding this exact Cirno is reachable right now, but film
            // summoning is disabled — leave her stored. Do NOT fall through to
            // the durable-snapshot restore below: that exists to cover a storage
            // item stuck in an unloaded chunk, not to quietly resurrect her from
            // a backup the moment the real (gated) item is found instead.
            if (CirnoStateAccess.hasGatedFilmReachable(player, storedUuid)) {
                player.displayClientMessage(
                        Component.literal("§c[Cirno] She's held in a film — film revival is disabled."), true);
                return;
            }

            // The item can be in an unloaded chunk, outside every safe scan. Use
            // the durable snapshot as the fallback and flag its storage UUID for
            // cleanup when that chunk later becomes reachable. The old entity
            // unload breadcrumb also removes a still-existing entity on load.
            DupeCleanupHandler.markStorageUuidForCleanup(player, storedUuid);
            if (storedUuid != null && CirnoStateAccess.findLoadedEntity(player, storedUuid) == null) {
                player.getPersistentData().putUUID("CirnoPendingDiscardUUID", storedUuid);
            }
            if (fallback != null) CirnoStateAccess.setCompanionSavedData(stack, fallback);
        }

        CirnoStateAccess.setCompanionHidden(stack, false);
        CirnoStateAccess.setHiddenByPlayer(stack, false);
        CirnoStateAccess.setRequiresItemRevive(stack, false);
        if (externalNBT != null) CirnoStateAccess.setCompanionSavedData(stack, externalNBT);
        player.getPersistentData().remove(CirnoStateAccess.NBT_CIRNO_REVIVE_READY_AT);
        spawnCompanion(player, stack);
        sendVisibilitySync(player, false);
    }



    /** Stores (hides) the Curious Cirno if she's currently summoned. */
    public static void storeCurioCompanion(Player player) {
        ItemStack stack = CirnoStateAccess.getCirnoStack(player);
        if (stack == null) return;
        if (!CirnoStateAccess.getCompanionHidden(stack)) {
            removeCompanion(player, stack);
            CirnoStateAccess.setCompanionHidden(stack, true);
            CirnoStateAccess.setHiddenByPlayer(stack, true);
            sendVisibilitySync(player, true);
        }
    }



    /**
     * TLM fires this BEFORE ItemFilm#filmToMaid calls readAdditionalSaveData().
     * Preserve the UUID from the film on that temporary maid and mark it as a
     * Cirno-film revival. The later EntityJoinLevelEvent can then replace the
     * temporary normal maid with the actual Cirno without losing the identity.
     *
     * This hook is deliberately restricted to TLM's film item, so the normal TLM
     * Shrine revive from a film still gets identity-preserving conversion.
     * <p>
     * NOT gated on cirnoCanSummonFromFilm (unlike the two call sites above). This
     * hook only restores identity (the real stored UUID) onto the temporary maid —
     * it never decides whether she comes back as CirnoEntity; that decision belongs
     * entirely to handlerEntityJoinLevel below. Skipping the hand-off here when the
     * setting is disabled used to mean TLM's own Shrine/tombstone revival (which
     * runs regardless of our config — TLM doesn't know it exists) would join with a
     * brand-new random UUID instead of her real one. handlerEntityJoinLevel's own
     * anti-dup check and tracking cleanup then couldn't recognize her by identity at
     * all, which is exactly what let a stale "stored/dead" slot or curio spawn a
     * second, genuinely duplicate Cirno later — regardless of whether this first one
     * got converted back into CirnoEntity or was correctly left as an ordinary maid.
     */
    public static void handlerMaidAndItemTransformToMaid(MaidAndItemTransformEvent.ToMaid event) {
        ItemStack item = event.getItem();
        ResourceLocation itemId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(item.getItem());
        if (!CirnoStateAccess.FILM_RL.equals(itemId)) return;

        CompoundTag data = event.getData();
        if (!data.getBoolean("IsCirnoCompanion")) return;
        if (!data.hasUUID("UUID")) return;

        event.getMaid().setUUID(data.getUUID("UUID"));
        event.getMaid().getPersistentData().putBoolean(CirnoStateAccess.NBT_TLM_FILM_REVIVAL, true);
        // A plain EntityMaid throws away IsCirnoCompanion / OwnerPlayerUUID, so keep the
        // film's full data here for handlerEntityJoinLevel to rebuild Cirno from.
        event.getMaid().getPersistentData().put(CirnoStateAccess.NBT_TLM_FILM_DATA, data.copy());
    }



    /**
     * TLM's film-to-maid path constructs a normal EntityMaid and then adds it to the
     * level. For a Cirno-tagged film revival, replace that temporary maid with a
     * Cirno carrying the exact UUID already restored by the ToMaid hook above. The
     * duplicate check happens before creating anything new.
     * <p>
     * NOT restricted to film any more. Originally this only fired when
     * {@link CirnoStateAccess#NBT_TLM_FILM_REVIVAL} was set (i.e. only for a film right-clicked
     * through {@link #handlerMaidAndItemTransformToMaid}), which meant every OTHER
     * path TLM uses to reconstruct a maid entity from saved data — most importantly
     * its own tombstone/Shrine revival, which recreates the maid entity directly
     * rather than going through the film item at all — silently produced a plain
     * touhou_little_maid:maid instead of a CirnoEntity: she'd come back with her
     * inventory and stats intact but with none of Cirno's AI, flight, mount or
     * telekinesis behaviour, and untracked by any of our own systems. Any freshly
     * joined plain EntityMaid whose OWN saved data already carries
     * IsCirnoCompanion (regardless of how it got there) is now caught here.
     * Vanilla's Entity#load restores the original UUID from that data by itself for
     * an entity-to-entity revival like the tombstone, so no separate UUID hand-off
     * hook (like the film-specific ToMaid one above) is needed for that case.
     * <p>
     * IMPORTANT: neither cirnoCanSummonFromFilm nor cirnoReviveCooldown is consulted
     * here. Those settings only control OUR summon paths (H key, panel, /cirno,
     * curio). A revive that TLM performs on its own (film / photo / slab click,
     * tombstone + Shrine) always brings her back as a CirnoEntity and rebinds the
     * tracker that owned her identity. Leaving her as a plain maid would either
     * duplicate her (the slot still says "stored/dead" and a later summon builds a
     * second one) or leave her slot occupied forever with nothing that can bring
     * her back. bindRevivedCirno also clears any pending revive cooldown.
     */
    public static void handlerEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof EntityMaid maid) || maid instanceof CirnoEntity) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        // Film revival (incl. the Shrine): the film's real data was stashed on this
        // temporary maid by the ToMaid hook, because a plain EntityMaid can't hold
        // IsCirnoCompanion itself. Any other path falls back to the maid's own save.
        CompoundTag stashed = maid.getPersistentData().contains(CirnoStateAccess.NBT_TLM_FILM_DATA, 10)
                ? maid.getPersistentData().getCompound(CirnoStateAccess.NBT_TLM_FILM_DATA) : null;
        CompoundTag data = stashed != null ? stashed.copy() : maid.saveWithoutId(new CompoundTag());
        if (!data.getBoolean("IsCirnoCompanion")) return;

        UUID ownerUuid = data.hasUUID("OwnerPlayerUUID") ? data.getUUID("OwnerPlayerUUID") : maid.getOwnerUUID();
        if (ownerUuid == null) return;

        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null) return;

        // The film ToMaid hook explicitly restores the stored film UUID onto the
        // entity before it joins the level (unconditionally now — see that hook's
        // comment). Every other revival path (tombstone, Shrine, ...) is
        // entity-to-entity and already carries her real UUID through maid.load()
        // by the time she gets here.
        UUID storedUuid = maid.getUUID();

        // Absolute anti-duplication guard: never create a second live Cirno for the
        // same stored identity. If something already restored her, consume the TLM
        // maid join and simply rebind tracking to the existing entity. Unconditional
        // on purpose — this is purely "don't end up with two of her", never a
        // "should this summon be allowed" decision.
        for (ServerLevel lvl : level.getServer().getAllLevels()) {
            Entity existing = lvl.getEntity(storedUuid);
            if (existing instanceof CirnoEntity existingCirno && existingCirno.isAlive()) {
                event.setCanceled(true);
                bindRevivedCirno(owner, existingCirno);
                return;
            }
        }

        CirnoEntity cirno = InitEntity.CIRNO.get().create(level);
        if (cirno == null) return;

        CompoundTag restoreData = data.copy();
        cirno.load(restoreData);
        cirno.setUUID(storedUuid);
        if (stashed != null) {
            // Film data carries the position she was captured at; use the spot TLM
            // actually placed the temporary maid (in front of the Shrine) instead.
            cirno.moveTo(maid.getX(), maid.getY(), maid.getZ(), maid.getYRot(), 0f);
        }
        cirno.setOwnerPlayer(owner);
        cirno.setOwnerUUID(owner.getUUID());
        cirno.setTame(true);
        cirno.wakeFromMaidBed();

        event.setCanceled(true);
        level.addFreshEntity(cirno);
        bindRevivedCirno(owner, cirno);
    }



    /** Reconnects a Shrine/film-restored Cirno to the tracker that owned the UUID. */
    private static void bindRevivedCirno(Player owner, CirnoEntity cirno) {
        UUID uuid = cirno.getUUID();
        CirnoStateAccess.migrateLegacySlotIfNeeded(owner);

        // Command mode keeps the UUID through death, so this is the normal exact match.
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = CirnoStateAccess.getSlotTag(owner, i);
            if (slot != null && slot.hasUUID(CirnoStateAccess.SLOT_UUID) && uuid.equals(slot.getUUID(CirnoStateAccess.SLOT_UUID))) {
                slot.putBoolean(CirnoStateAccess.SLOT_STORED, false);
                slot.putBoolean(CirnoStateAccess.SLOT_VISIBLE, true);
                slot.putBoolean(CirnoStateAccess.SLOT_DEAD, false);
                slot.remove(CirnoStateAccess.SLOT_DATA);
                slot.remove(CirnoStateAccess.SLOT_REVIVE_TICKS_LEFT);
                slot.remove(CirnoStateAccess.SLOT_REVIVE_READY_AT);
                CirnoStateAccess.putSlotTag(owner, i, slot);
                return;
            }
        }

        // Curio mode clears its UUID when TLM captures/drops her into the external item.
        // In that state CirnoStateAccess.requiresItemRevive is the proof that this UUID belongs back on the
        // Curio item; do not steal an actively tracked Curio with a different UUID.
        ItemStack curio = CirnoStateAccess.getCirnoStack(owner);
        if (curio != null && CirnoStateAccess.getCompanionUUID(curio) == null && CirnoStateAccess.requiresItemRevive(curio)) {
            owner.getPersistentData().remove(CirnoStateAccess.NBT_CIRNO_REVIVE_READY_AT);
            CirnoStateAccess.setCompanionUUID(curio, uuid);
            CirnoStateAccess.setCompanionHidden(curio, false);
            CirnoStateAccess.setHiddenByPlayer(curio, false);
            CirnoStateAccess.setRequiresItemRevive(curio, false);
        }
    }
}
