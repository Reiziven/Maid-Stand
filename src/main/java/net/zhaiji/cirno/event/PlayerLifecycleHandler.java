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

public class PlayerLifecycleHandler {

    // ── Dimension change ──────────────────────────────────────────────────────

    public static void handlerPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!CirnoCommonConfig.companionEntityEnabled) return;
        if (!CompatManager.isTLMLoad()) return;
        Player player = event.getEntity();
        if (player.level().isClientSide || player.getServer() == null
                || !(player.level() instanceof ServerLevel destination)) return;

        // Transfer the existing entities through Minecraft's dimension-change
        // path. Do not snapshot/discard/recreate them: their UUIDs and live state
        // remain the identity tracked by the Curio/command slots.
        ItemStack curio = CirnoStateAccess.getCirnoStack(player);
        if (curio != null && !CirnoStateAccess.getCompanionHidden(curio)) {
            UUID uuid = CirnoStateAccess.getCompanionUUID(curio);
            CirnoEntity live = findOrLoadCirno(player, uuid);
            CirnoEntity moved = transferCirnoWithPlayer(live, player, destination);
            if (moved != null) {
                moved.setInvisible(false);
                CirnoStateAccess.setCompanionUUID(curio, moved.getUUID());
            }
        }

        CirnoStateAccess.migrateLegacySlotIfNeeded(player);
        for (int i = 1; i <= CirnoStateAccess.MAX_COMMAND_CIRNOS; i++) {
            CompoundTag slot = CirnoStateAccess.getSlotTag(player, i);
            UUID uuid = slot != null && slot.hasUUID(CirnoStateAccess.SLOT_UUID) ? slot.getUUID(CirnoStateAccess.SLOT_UUID) : null;
            CirnoEntity live = findOrLoadCirno(player, uuid);
            CirnoEntity moved = transferCirnoWithPlayer(live, player, destination);
            if (moved != null) {
                if (slot != null) {
                    moved.setInvisible(!slot.getBoolean(CirnoStateAccess.SLOT_VISIBLE));
                    slot.putUUID(CirnoStateAccess.SLOT_UUID, moved.getUUID());
                    CirnoStateAccess.putSlotTag(player, i, slot);
                }
            }
        }
    }



    @Nullable
    private static CirnoEntity findOrLoadCirno(Player player, @Nullable UUID uuid) {
        if (uuid == null || player.getServer() == null) return null;
        // findLoadedEntity already falls back to loading her last-known chunk from
        // disk (see its own doc) when she isn't currently resident in memory, so a
        // single call here covers both "she's already ticking somewhere" and "she's
        // dormant in a chunk nobody's visited since" without duplicating that logic.
        Entity entity = CirnoStateAccess.findLoadedEntity(player, uuid);
        return entity instanceof CirnoEntity cirno && cirno.isAlive() ? cirno : null;
    }



    /** Called by CirnoEntity while it is ticking, before its chunk can unload. */
    public static void recordCirnoLocation(Player player, CirnoEntity cirno) {
        if (player.level().isClientSide || !(cirno.level() instanceof ServerLevel level)) return;
        CompoundTag data = player.getPersistentData();
        net.minecraft.nbt.ListTag locations = data.contains(CirnoStateAccess.NBT_CIRNO_LAST_LOCATIONS, 9)
                ? data.getList(CirnoStateAccess.NBT_CIRNO_LAST_LOCATIONS, 10) : new net.minecraft.nbt.ListTag();
        CompoundTag location = null;
        for (int i = 0; i < locations.size(); i++) {
            CompoundTag candidate = locations.getCompound(i);
            if (candidate.hasUUID("UUID") && cirno.getUUID().equals(candidate.getUUID("UUID"))) {
                location = candidate;
                break;
            }
        }
        if (location == null) {
            location = new CompoundTag();
            locations.add(location);
        }
        location.putUUID("UUID", cirno.getUUID());
        location.putString("Dimension", level.dimension().location().toString());
        location.putInt("ChunkX", cirno.chunkPosition().x);
        location.putInt("ChunkZ", cirno.chunkPosition().z);
        data.put(CirnoStateAccess.NBT_CIRNO_LAST_LOCATIONS, locations);
    }



    @Nullable
    private static CirnoEntity transferCirnoWithPlayer(@Nullable CirnoEntity cirno, Player player,
                                                       ServerLevel destination) {
        if (cirno == null || !cirno.isAlive()) return null;
        CirnoEntity transferred = cirno;
        if (cirno.level() != destination) {
            // cirno.changeDimension(destination) routes through vanilla's
            // Entity#findDimensionEntryPoint, which looks for an actual portal block
            // near her CURRENT position to work out where to place her on the other
            // side. She never physically touches a portal herself — the player does,
            // and this is just bringing her along programmatically — so that lookup
            // routinely finds nothing and changeDimension() silently returns null,
            // leaving her behind in the source dimension for good (and anything that
            // then looks for her near the player in the new dimension, like the maid
            // panel, finds nothing either, since she's genuinely still back there).
            //
            // Do the transfer directly instead: full saved data copied onto a new
            // instance carrying her exact same UUID, homed straight at the player's
            // own position in the destination level. This is not a fresh/blank
            // replacement — it mirrors exactly what changeDimension() itself does
            // under the hood (getType().create + restoreFrom + moveTo + add, then
            // remove the source), just without the portal lookup that has no reason
            // to succeed here.
            CirnoEntity moved = InitEntity.CIRNO.get().create(destination);
            if (moved == null) return null;

            CompoundTag snapshot = cirno.saveWithoutId(new CompoundTag());
            moved.load(snapshot);
            moved.setUUID(cirno.getUUID());
            moved.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            destination.addFreshEntity(moved);

            cirno.allowNextRemoval();
            cirno.remove(Entity.RemovalReason.CHANGED_DIMENSION);

            transferred = moved;
        }

        transferred.setOwnerPlayer(player);
        transferred.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        transferred.wakeFromMaidBed();
        return transferred;
    }



    // ── Wake up ───────────────────────────────────────────────────────────────

    public static void handlerPlayerWakeUpEvent(PlayerWakeUpEvent event) {
        if (!CirnoCommonConfig.wakeUpCanResetCooldown) return;
        Player player = event.getEntity();
        Item item = InitItem.CIRNO.get();
        if (net.zhaiji.cirno.compat.CuriosCompat.hasCirno(player)) {
            player.getCooldowns().removeCooldown(item);
        }
    }



    // ── Login ─────────────────────────────────────────────────────────────────

    public static void handlerPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        TelekinesisHandler.removeAttributeBuffs(player);
        TelekinesisHandler.clearTelekinesisControl(player);

        if (CirnoCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()) {
            if (CirnoCommonConfig.cirnoPartOfOwner) {
                CompanionLifecycleHandler.removeCompanion(player);
            } else {
                // Not bound to the owner's fate: leave her alive in the world, waiting
                // for the owner to log back in — just snapshot her as a durable backup.
                CompanionLifecycleHandler.snapshotCompanionWithoutRemoving(player);
            }
        }
        CirnoStateAccess.applyPartOfOwnerPolicyToCommandCirnos(player);
        PlayerTickHandler.clearSyncedCommandCirnoTKCache(player);
    }



    public static void handlerPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack stack = CirnoStateAccess.getCirnoStack(player);

        // Always apply attribute buffs when curio is equipped.
        if (stack != null) {
            TelekinesisHandler.applyAttributeBuffs(player);
        }

        // Companion entity only when enabled + TLM loaded.
        if (!CirnoCommonConfig.companionEntityEnabled || !CompatManager.isTLMLoad()) return;

        // Command-spawned Cirnos are stored on logout when they are part of their owner.
        // Restore only those logout snapshots here; manually stored Cirnos stay stored.
        int restoredCommand = CommandCirnoManager.restoreCommandCirnosAfterLogin(player);
        if (restoredCommand > 0) {
            TelekinesisHandler.applyAttributeBuffs(player);
            TelekinesisHandler.enableTelekinesisMode(player);
        }

        // No Curios item — command Cirnos are still handled above.
        if (stack == null) return;

        // If hidden, no entity should exist — nothing to do.
        if (CirnoStateAccess.getCompanionHidden(stack)) return;

        UUID existingUUID = CirnoStateAccess.getCompanionUUID(stack);
        if (existingUUID != null && player.getServer() != null) {
            // Check every loaded level (she may be in a different dimension than the
            // one the player is logging back into) and, if she isn't resident in
            // memory anywhere, give her last-known chunk a chance to load from disk
            // before ever concluding she's gone — see findLoadedEntity's doc for why
            // skipping that step is exactly what let a dormant-but-still-real Cirno
            // get replaced with a fresh snapshot copy while the original sat intact
            // elsewhere, ready to reappear later as a duplicate.
            Entity e = CirnoStateAccess.findLoadedEntity(player, existingUUID);
            if (e instanceof CirnoEntity cirno && cirno.isAlive()) {
                cirno.setOwnerPlayer(player);
                return;
            }
            // Not found YET. At login time her chunk's entities are usually still
            // loading asynchronously, so "not found" does not mean "gone". Replacing
            // her right now is what duplicated an already-summoned Cirno on every
            // join (the real one finished loading a moment later). Give her a grace
            // window and only respawn if she still hasn't shown up after it.
            player.getPersistentData().putInt(CirnoStateAccess.NBT_LOGIN_VERIFY_TICKS, LOGIN_VERIFY_TIMEOUT_TICKS);
            return;
        }

        CompanionLifecycleHandler.spawnCompanion(player, stack);
    }

    /** How long after login we keep looking for the tracked Cirno before replacing her (5 s). */
    private static final int LOGIN_VERIFY_TIMEOUT_TICKS = 100;

    /** Checked every few ticks during the window, not every tick. */
    private static final int LOGIN_VERIFY_CHECK_INTERVAL = 5;

    /**
     * Runs from the player tick while a post-login verification is pending. Adopts
     * the original Cirno as soon as she resolves; only when the whole grace window
     * elapses without finding her is the stale UUID cleared and a replacement made.
     */
    static void tickLoginVerify(Player player) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(CirnoStateAccess.NBT_LOGIN_VERIFY_TICKS)) return;

        int ticksLeft = data.getInt(CirnoStateAccess.NBT_LOGIN_VERIFY_TICKS) - 1;
        boolean timedOut = ticksLeft <= 0;
        if (!timedOut) {
            data.putInt(CirnoStateAccess.NBT_LOGIN_VERIFY_TICKS, ticksLeft);
            if (ticksLeft % LOGIN_VERIFY_CHECK_INTERVAL != 0) return;
        } else {
            data.remove(CirnoStateAccess.NBT_LOGIN_VERIFY_TICKS);
        }

        ItemStack stack = CirnoStateAccess.getCirnoStack(player);
        if (stack == null || CirnoStateAccess.getCompanionHidden(stack)) {
            data.remove(CirnoStateAccess.NBT_LOGIN_VERIFY_TICKS);
            return;
        }
        UUID uuid = CirnoStateAccess.getCompanionUUID(stack);
        if (uuid == null) {
            // Something else (H key, equip, ...) already resolved it.
            data.remove(CirnoStateAccess.NBT_LOGIN_VERIFY_TICKS);
            return;
        }

        Entity e = CirnoStateAccess.findLoadedEntity(player, uuid);
        if (e instanceof CirnoEntity cirno && cirno.isAlive()) {
            data.remove(CirnoStateAccess.NBT_LOGIN_VERIFY_TICKS);
            cirno.setOwnerPlayer(player);
            return;
        }
        if (timedOut) {
            CirnoStateAccess.clearCompanionUUID(stack);
            CompanionLifecycleHandler.spawnCompanion(player, stack);
        }
    }



    // ── Death / respawn NBT carry-over ────────────────────────────────────────

    /**
     * Vanilla's player respawn creates a brand-new ServerPlayer object and does
     * NOT copy {@link Player#getPersistentData()} onto it — that's the standard,
     * well-known Forge gotcha for any mod that stores its own data there instead
     * of on an ItemStack or a Capability. Without this handler, everything the
     * command-mode Cirno system stores on the player (all 3 slots, plus pending
     * discard breadcrumbs) is silently wiped the moment the player dies, even
     * though CirnoStateAccess.applyPartOfOwnerPolicyToCommandCirnos() correctly wrote it just before that —
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
}