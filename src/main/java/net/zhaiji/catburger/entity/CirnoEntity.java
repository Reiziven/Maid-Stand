package net.zhaiji.catburger.entity;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.zhaiji.catburger.config.CatBurgerCommonConfig;
import net.zhaiji.catburger.compat.CompatManager;
import net.zhaiji.catburger.event.CommonEventHandler;
import net.zhaiji.catburger.init.InitEntity;
import net.zhaiji.catburger.util.CirnoChunkLoadingManager;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Companion entity spawned when the CatBurger curio is equipped.
 * Extends EntityMaid so TLM handles all rendering, animation and YSM compat.
 */
public class CirnoEntity extends EntityMaid {

    /** UUID of the player this companion belongs to. */
    @Nullable
    private UUID ownerPlayerUUID;

    /**
     * Grace-period ticks before giving up when owner is not found.
     * Kept small — she's transient and should only exist while the owner is online.
     * Has no effect when cirnoUnloadProtection is true (she waits indefinitely).
     */
    private int ownerMissingTicks = 0;
    private static final int OWNER_MISSING_GRACE = 60; // 3 seconds

    /**
     * Set to true by trusted code (CommonEventHandler, dimension change, H key)
     * immediately before calling discard() or remove(). When cirnoUnloadProtection
     * is on, any removal NOT preceded by this flag is blocked — same approach
     * ANChorCore uses with its ThreadLocal allow-removal flag.
     */
    private boolean trustedRemovalPending = false;

    /** Ticks since the last periodic NBT autosave (see CatBurgerCommonConfig#cirnoAutoSaveEnabled). */
    private int autoSaveTicks = 0;
    /** Set when inventory or equipment changes — autosave only serializes when this is true. */
    private boolean nbtDirty = false;

    /**
     * Per-entity follow-owner override. {@code null} means "use the global config default".
     * Set via the Follow button in the maid GUI to control this specific entity independently.
     */
    @Nullable
    private Boolean perEntityFollowOwner = null;

    // ── Smooth follow tuning ─────────────────────────────────────────────────
    private static final double FOLLOW_DEADZONE_SQR = 0.04; // ~0.2 blocks
    private static final double FOLLOW_SNAP_DISTANCE_SQR = 8.0 * 8.0; // 8 blocks
    private static final double FOLLOW_LERP_SPEED = 0.35;
    // Hover her feet slightly above the owner's feet (less than half a block) instead of
    // exactly level. With noPhysics on there's no ground collision to rest her on, so
    // matching the owner's Y exactly left her feet rendering a hair into the floor.
    private static final double FOLLOW_Y_OFFSET = 0.15;
    // If she's ever found closer to the owner's own position than this, something knocked
    // her out of place (equip-triggered attack goal, a shove, etc.) — the real follow spot
    // is always ~0.9 blocks out at the offset below, so overlapping the owner is never
    // correct and gets pulled back out immediately rather than waiting on the deadzone.
    private static final double MIN_OWNER_DISTANCE_SQR = 0.36; // 0.6 blocks

    // ── Flight animation state (jump / fall / fly / elytra) ─────────────────
    // Picked server-side by CirnoFlightTracker from what the owner is doing, then synced so the
    // client's animation predicates (CirnoFlightAnimations) can read it. NONE = hands off.
    private static final EntityDataAccessor<Byte> DATA_FLIGHT_STATE =
            SynchedEntityData.defineId(CirnoEntity.class, EntityDataSerializers.BYTE);
    // How far under her feet a solid block still counts as "she's standing on something". A bit more
    // than FOLLOW_Y_OFFSET, so her normal hover height, half-block stairs and the follow-lerp lag
    // after a landing don't read as "no floor".
    private static final double GROUND_PROBE_DEPTH = 0.6;
    // An owner Y change bigger than this in a single tick is a teleport, not movement.
    private static final double MAX_PLAUSIBLE_OWNER_DY = 8.0;

    private final CirnoFlightTracker flightTracker = new CirnoFlightTracker();
    /** Owner's Y last tick. The server's own player deltaMovement isn't reliable, so speed comes from this. */
    private double lastOwnerY = Double.NaN;

    public CirnoEntity(EntityType<CirnoEntity> type, Level level) {
        super((EntityType<EntityMaid>)(EntityType<?>) type, level);
        this.setEntityInvulnerable(!CatBurgerCommonConfig.cirnoCanDie);
        this.setNoAi(!CatBurgerCommonConfig.cirnoAiEnabled);
        this.setSilent(false);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return EntityMaid.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0);
    }

    // ── Per-entity follow override ────────────────────────────────────────────

    /**
     * Returns true if this specific entity should follow its owner.
     * If the per-entity override has been set (via the GUI button), that value wins.
     * Otherwise falls back to the global config default.
     * Always false if the global config disables the feature entirely.
     */
    public boolean isFollowingOwner() {
        if (perEntityFollowOwner != null) return perEntityFollowOwner;
        return CatBurgerCommonConfig.cirnoFollowsOwner;
    }

    /** Called server-side by ToggleMaidFollowPacket to flip this entity's follow state. */
    public void setPerEntityFollow(boolean follow) {
        this.perEntityFollowOwner = follow;
        this.nbtDirty = true;
    }

    // ── Line of sight bypass ──────────────────────────────────────────────────

    /**
     * When cirnoFollowsOwner + noPhysics are both on, her eye position can end up inside
     * solid block geometry, which causes every raytrace-based line-of-sight check to fail
     * immediately (the ray starts inside a block). Overriding this on the entity itself
     * (rather than on Sensing) catches both target detection and the attack execution check.
     */
    @Override
    public boolean hasLineOfSight(Entity target) {
        if (isFollowingOwner()
                && CatBurgerCommonConfig.cirnoDisablePhysicsWhileFollowing
                && CatBurgerCommonConfig.cirnoAttackSeeThroughBlocks) {
            return target.level() == this.level() && target.isAlive();
        }
        return super.hasLineOfSight(target);
    }

    // ── Owner ─────────────────────────────────────────────────────────────────

    public void setOwnerPlayer(Player player) {
        this.ownerPlayerUUID = player.getUUID();
        this.setOwnerUUID(player.getUUID());
        this.setTame(true);
    }

    @Nullable
    public Player getOwnerPlayer() {
        if (ownerPlayerUUID == null || level().isClientSide) return null;
        return level().getServer() == null ? null
                : level().getServer().getPlayerList().getPlayer(ownerPlayerUUID);
    }

    // ── YSM / TLM model ──────────────────────────────────────────────────────

    public void applyModel() {
        if (CatBurgerCommonConfig.useYsmModel && CompatManager.isYSMLoad()
                && !"none".equalsIgnoreCase(CatBurgerCommonConfig.ysmModelId)) {
            this.setIsYsmModel(true);
            this.setYsmModel(
                    CatBurgerCommonConfig.ysmModelId,
                    "",
                    Component.literal("")
            );
        }
    }

    // ── Flight animation state ───────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLIGHT_STATE, (byte) CirnoFlightState.NONE.ordinal());
    }

    /** Current pose picked by her owner's movement. Valid on both sides (synced entity data). */
    public CirnoFlightState getFlightState() {
        return CirnoFlightState.byId(this.entityData.get(DATA_FLIGHT_STATE));
    }

    private void setFlightState(CirnoFlightState state) {
        CirnoFlightState prev = getFlightState();
        this.entityData.set(DATA_FLIGHT_STATE, (byte) state.ordinal());
        // Push into YSM roamingVars whenever the pose changes (server).
        // Client also re-applies every tick in tick() so the values stay live
        // even if the entity data arrived a frame late.
        if (state != prev) {
            applyYsmFlightVars(state);
        }
    }

    /**
     * Write the current flight pose into YSM roaming variables so model controllers
     * can select {@code fly} / {@code fall} / {@code elytra_fly} etc.
     * <pre>
     *   v.roaming.cirno_fly          = 1 when FLY
     *   v.roaming.cirno_fall         = 1 when FALL
     *   v.roaming.cirno_jump         = 1 when JUMP
     *   v.roaming.cirno_elytra       = 1 when ELYTRA or ELYTRA_BOOST
     *   v.roaming.cirno_elytra_boost = 1 when ELYTRA_BOOST
     * </pre>
     * Models that already use the standard animation names can gate them on these
     * variables (or on {@code isFallFlying()} / {@code onGround()} which we also drive).
     */
    private void applyYsmFlightVars(CirnoFlightState state) {
        // EntityMaid.roamingVars is public and synced when roamingVarsUpdateFlag changes.
        this.roamingVars.put("cirno_fly", state == CirnoFlightState.FLY ? 1f : 0f);
        this.roamingVars.put("cirno_fall", state == CirnoFlightState.FALL ? 1f : 0f);
        this.roamingVars.put("cirno_jump", state == CirnoFlightState.JUMP ? 1f : 0f);
        this.roamingVars.put("cirno_elytra", state.isElytra() ? 1f : 0f);
        this.roamingVars.put("cirno_elytra_boost", state == CirnoFlightState.ELYTRA_BOOST ? 1f : 0f);
        this.roamingVarsUpdateFlag++;
        this.rouletteAnimDirty = true; // triggers the existing YSM sync path on EntityMaid
    }

    /**
     * Client only: report gliding while her owner is gliding, so YSM (and any other
     * renderer that keys off isFallFlying) gets the elytra pose.
     * Deliberately NOT applied on the server: isFallFlying() switches LivingEntity#travel
     * over to elytra physics (including fly-into-wall damage).
     */
    @Override
    public boolean isFallFlying() {
        return super.isFallFlying() || (this.level().isClientSide && this.getFlightState().isElytra());
    }

    /**
     * Client only: report not-on-ground for any airborne flight pose so YSM's
     * movement predicates see sustained air time (needed for fly vs jump).
     * Server keeps the real physics flag.
     */
    @Override
    public boolean onGround() {
        if (this.level().isClientSide && this.getFlightState().isAirborne()) {
            return false;
        }
        return super.onGround();
    }

    // ── Tick / follow logic ───────────────────────────────────────────────────

    @Override
    public void tick() {
        this.noPhysics = isFollowingOwner()
                && CatBurgerCommonConfig.cirnoDisablePhysicsWhileFollowing;

        super.tick();

        // Client: keep YSM roamingVars in sync with the latest flight state every tick.
        // Entity data can lag by a frame after a teleport / dimension change.
        if (level().isClientSide) {
            applyYsmFlightVars(getFlightState());
            return;
        }


        // Keep the chunk she's standing in force-loaded so it never becomes eligible to
        // unload out from under her in the first place — this is what actually makes
        // cirnoUnloadProtection work; the guard in remove() alone just leaves her stuck
        // in a frozen "ghost" state once a chunk starts unloading around her.
        if (CatBurgerCommonConfig.cirnoUnloadProtection && level() instanceof ServerLevel serverLevel) {
            CirnoChunkLoadingManager.update(this.getUUID(), serverLevel, new ChunkPos(this.blockPosition()));
        }

        Player owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            // When unload protection is on she stays in the world indefinitely —
            // no grace-period countdown, no self-removal.
            if (!CatBurgerCommonConfig.cirnoUnloadProtection) {
                ownerMissingTicks++;
                if (ownerMissingTicks >= OWNER_MISSING_GRACE) {
                    ownerMissingTicks = OWNER_MISSING_GRACE;
                }
            }
            return;
        }
        ownerMissingTicks = 0;

        // Deferred discard: removeCompanion() couldn't reach us while we were in an
        // unloaded chunk — now that we're ticking, execute the pending removal.
        // Two independent breadcrumb mechanisms can apply: the single-UUID one used
        // by the Curios-equipped companion (always at most one at a time), and the
        // per-slot list used by command-mode Cirnos (up to CommonEventHandler.MAX_COMMAND_CIRNOS
        // of her can be mid-unload at once).
        CompoundTag pdata = owner.getPersistentData();
        if (pdata.hasUUID("CatBurgerPendingDiscardUUID")) {
            UUID pendingUUID = pdata.getUUID("CatBurgerPendingDiscardUUID");
            if (pendingUUID.equals(this.getUUID())) {
                pdata.remove("CatBurgerPendingDiscardUUID");
                this.allowNextRemoval();
                this.discard();
                return;
            }
        }
        CommonEventHandler.consumePendingCommandDiscard(owner, this);
        if (this.isRemoved()) return;

        if (CatBurgerCommonConfig.cirnoAutoSaveEnabled) {
            autoSaveTicks++;
            if (autoSaveTicks >= Math.max(20, CatBurgerCommonConfig.cirnoAutoSaveIntervalTicks)) {
                autoSaveTicks = 0;
                if (nbtDirty) {
                    nbtDirty = false;
                    CommonEventHandler.snapshotCirnoDurable(owner, this);
                }
            }
        }

        if (isFollowingOwner()) {
            // Cancel any AI-driven pathing (e.g. an attack goal that picked up a target the
            // moment she had a weapon equipped) before it can fight our own positioning
            // below — that competition was what made her visibly reposition/jitter on
            // weapon-equip even though canBrainMoving() was already off.
            this.getNavigation().stop();

            // Multi-Cirno formation: figure out which "slot" this Cirno holds among all
            // of the owner's currently live ones (see getOrderedLiveCirnosPublic — Curios
            // companion first, then command slots 1..MAX_COMMAND_CIRNOS in creation order).
            // Recomputed fresh every tick, so if the group shrinks (one gets stored/removed)
            // the rest reflow into the earlier slots automatically instead of leaving a gap.
            java.util.List<CirnoEntity> formation = CommonEventHandler.getOrderedLiveCirnosPublic(owner);
            int formationIndex = formation.indexOf(this);
            if (formationIndex < 0) formationIndex = 0; // not found yet this tick — default to the primary spot
            double[] localOffset = formationOffset(formationIndex, Math.max(formation.size(), 1));

            double yaw = Math.toRadians(owner.getYRot());
            double offsetX =  Math.cos(yaw) * localOffset[0] - Math.sin(yaw) * localOffset[1];
            double offsetZ =  Math.sin(yaw) * localOffset[0] + Math.cos(yaw) * localOffset[1];
            double targetX = owner.getX() + offsetX;
            double targetY = owner.getY() + FOLLOW_Y_OFFSET;
            double targetZ = owner.getZ() + offsetZ;

            double distSqr = this.distanceToSqr(targetX, targetY, targetZ);

            double ownerDistSqr = this.distanceToSqr(owner.getX(), owner.getY(), owner.getZ());
            double newY = Mth.lerp(FOLLOW_LERP_SPEED, this.getY(), targetY);

            if (distSqr > FOLLOW_SNAP_DISTANCE_SQR) {
                this.teleportTo(targetX, targetY, targetZ);
            } else if (distSqr > FOLLOW_DEADZONE_SQR || ownerDistSqr < MIN_OWNER_DISTANCE_SQR) {
                double newX = Mth.lerp(FOLLOW_LERP_SPEED, this.getX(), targetX);
                double newZ = Mth.lerp(FOLLOW_LERP_SPEED, this.getZ(), targetZ);
                this.setPos(newX, newY, newZ);
            } else {
                // Horizontally close enough to skip — but Y still gets repinned every tick
                // regardless of that deadzone. The vertical velocity mirrored onto her below
                // (for jump/fall animation) is still being integrated into her real position
                // by super.tick()'s normal gravity handling every tick noPhysics is on, and
                // with no ground collision left to stop it, skipping this would let her
                // gradually sink below the floor over time instead of just staying put.
                this.setPos(this.getX(), newY, this.getZ());
            }

            this.setYRot(owner.getYRot());
            this.yHeadRot = owner.getYHeadRot();
            this.yBodyRot = owner.yBodyRot;

            // Mirror the owner's running animation. While following, her actual
            // movement is driven by the position lerp/teleport above rather than
            // real AI/physics (canBrainMoving() is false here), so isSprinting()
            // would never get set naturally and TLM's model would never pick the
            // running pose no matter how fast she's being dragged along. Syncing
            // it directly fixes that. Only the run animation is synced this way
            // for now — other poses (sneak, swim, etc.) are left as-is.
            this.setSprinting(owner.isSprinting());

            this.setDeltaMovement(this.getDeltaMovement().x, owner.getDeltaMovement().y, this.getDeltaMovement().z);
            this.setOnGround(owner.onGround());
            this.fallDistance = 0f;

            // Jump / fall / fly / elytra pose. Runs after the position update above so the
            // "is there floor under her" test sees where she actually is this tick.
            this.setFlightState(computeFlightState(owner));
        } else {
            if (this.isSprinting()) {
                // Not following anymore — don't leave her stuck "running in place"
                // from a stale sync above; her own AI/movement takes over from here.
                this.setSprinting(false);
            }
            // Same for the airborne pose: hand animation control back to TLM.
            this.flightTracker.reset();
            this.lastOwnerY = Double.NaN;
            this.setFlightState(CirnoFlightState.NONE);
        }
    }

    /**
     * Works out which pose Cirno should show from what her owner is doing. The priority order and
     * timing live in {@link CirnoFlightTracker}; this just gathers the facts (server-side only).
     */
    private CirnoFlightState computeFlightState(Player owner) {
        // Always sample, even while handing control back, so the first dy after a pause is sane.
        double ownerY = owner.getY();
        double dy = Double.isNaN(lastOwnerY) ? 0.0 : ownerY - lastOwnerY;
        lastOwnerY = ownerY;
        if (Math.abs(dy) > MAX_PLAUSIBLE_OWNER_DY) dy = 0.0;

        // Feature off, or the owner is doing something TLM already animates properly on its own
        // terms (swimming, climbing, riding, sleeping...): don't fight it.
        if (!CatBurgerCommonConfig.cirnoFlyAnimationEnabled
                || owner.isPassenger() || owner.isSleeping() || owner.isDeadOrDying()
                || owner.isInWater() || owner.isInLava() || owner.isSwimming() || owner.onClimbable()) {
            flightTracker.reset();
            return CirnoFlightState.NONE;
        }

        boolean ownerOnGround = owner.onGround();
        boolean fallFlying = owner.isFallFlying();
        CirnoFlightTracker.Input input = new CirnoFlightTracker.Input(
                ownerOnGround,
                fallFlying,
                fallFlying && isBoostedByRocket(owner),
                owner.getAbilities().flying,
                dy,
                // Only matters while the owner is standing, so skip the collision query otherwise.
                !ownerOnGround || hasGroundBelow());
        return flightTracker.update(input, Math.max(1, CatBurgerCommonConfig.cirnoFlyDelayTicks));
    }

    /** True if a solid block sits within {@link #GROUND_PROBE_DEPTH} under Cirno's own feet. */
    private boolean hasGroundBelow() {
        AABB box = this.getBoundingBox();
        AABB probe = new AABB(box.minX + 0.05, box.minY - GROUND_PROBE_DEPTH, box.minZ + 0.05,
                box.maxX - 0.05, box.minY, box.maxZ - 0.05);
        for (VoxelShape shape : this.level().getBlockCollisions(this, probe)) {
            if (!shape.isEmpty()) return true;
        }
        return false;
    }

    /**
     * True while a firework rocket the owner launched is riding along with them. A rocket that is
     * boosting an elytra flight stays glued to the player's position, whereas one shot from a
     * crossbow leaves the 1-block search box within a tick or two.
     */
    private static boolean isBoostedByRocket(Player owner) {
        return !owner.level().getEntitiesOfClass(FireworkRocketEntity.class,
                owner.getBoundingBox().inflate(1.0), rocket -> rocket.getOwner() == owner).isEmpty();
    }

    /**
     * Owner-relative {right, forward} formation offset (in blocks) for a Cirno at
     * {@code index} within a group of {@code total} currently active Cirnos. Positive
     * "right" is the original single-Cirno side, positive "forward" is in front of
     * the owner (so the existing -0.4 "behind" value stays negative here too).
     * <p>
     * Slot 0 is untouched from the original single-Cirno spot ("left", per the
     * original behaviour) so nothing changes visually when only one Cirno is out.
     * Slot 1 mirrors that to the opposite side ("right"). Slot 2 drops back to a
     * spot centred a bit further behind the owner. Anything beyond that keeps
     * spreading out further back so extra Cirnos never stack on top of each other.
     */
    private static double[] formationOffset(int index, int total) {
        if (total <= 1) {
            return new double[]{ 0.8, -0.4 };
        }
        switch (index) {
            case 0:
                return new double[]{ 0.8, -0.4 };   // original spot, unchanged ("left")
            case 1:
                return new double[]{ -0.8, -0.4 };  // mirrored to the other side ("right")
            case 2:
                return new double[]{ 0.0, -1.2 };   // a bit further behind, centred
            default:
                // 4th+: keep alternating sides, each pair stepping further back.
                double side = ((index - 3) % 2 == 0) ? 1.0 : -1.0;
                int lane = (index - 3) / 2 + 1;
                return new double[]{ side * 0.8 * lane, -1.2 - 0.6 * lane };
        }
    }

    // ── Invulnerability / targeting guards ───────────────────────────────────

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;

        if (CatBurgerCommonConfig.cirnoRetargetAttackers) {
            Entity attacker = source.getEntity();
            if (attacker instanceof Mob mob) {
                Player owner = getOwnerPlayer();
                if (owner != null && owner.isAlive()) {
                    mob.setTarget(owner);
                }
            }
        }

        if (CatBurgerCommonConfig.cirnoCanDie) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (!CatBurgerCommonConfig.cirnoCanDie) return;
        if (level().isClientSide) return;
        if (this.isRemoved() || this.dead) return;

        if (this.getMaidBauble().fireEvent((b, s) -> b.onDeath(this, s, source))) return;
        if (MinecraftForge.EVENT_BUS.post(new MaidDeathEvent(this, source))) return;
        if (ForgeHooks.onLivingDeath(this, source)) return;

        Player owner = getOwnerPlayer();
        if (owner == null) {
            this.setHealth(this.getMaxHealth());
            this.setInvisible(true);
            this.setEntityInvulnerable(true);
            return;
        }

        this.setHealth(this.getMaxHealth());
        CommonEventHandler.handleCompanionDeath(owner);
    }

    @Override
    protected void dropEquipment() {
        // Never let TLM spawn a tombstone for Cirno
    }

    /** Mark NBT dirty whenever a slot changes so the autosave only fires when needed. */
    @Override
    public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {
        super.setItemSlot(slot, stack);
        nbtDirty = true;
    }

    /**
     * Catch-all NBT safety net — fires on every removal path (discard, death, /kill, chunk
     * unload, another mod's cleanup, etc.) via Entity#remove(RemovalReason).
     */
    @Override
    public void remove(RemovalReason reason) {
        if (!level().isClientSide && CatBurgerCommonConfig.cirnoUnloadProtection) {
            // KILLED is only allowed when cirnoCanDie is explicitly enabled
            boolean isAllowedKill = reason == RemovalReason.KILLED && CatBurgerCommonConfig.cirnoCanDie;
            // DISCARDED is allowed when we set the flag ourselves, OR when TLM's own
            // native code is the caller (smart slab / photo capture, backpack, etc. all
            // call remove(DISCARDED) directly without knowing about our flag — blocking
            // those left her successfully captured into the item's NBT while the live
            // entity stayed behind, a duplicate exactly like the unloaded-chunk ghost).
            boolean isTrustedDiscard = reason == RemovalReason.DISCARDED
                    && (trustedRemovalPending || isTrustedThirdPartyCaller());
            // CHANGED_DIMENSION is always allowed (dimension travel)
            boolean isDimensionChange = reason == RemovalReason.CHANGED_DIMENSION;

            if (!isAllowedKill && !isTrustedDiscard && !isDimensionChange) {
                // Untrusted removal — snapshot her and block it
                Player owner = getOwnerPlayer();
                if (owner != null) {
                    CommonEventHandler.snapshotCirnoDurable(owner, this);
                }
                // Don't call super — entity stays alive
                return;
            }
        }
        // Remember whether WE initiated this removal (H key, /cirno, death-store,
        // dimension change, ...) before clearing the flag below. Those call sites
        // already update their own UUID/hidden/stored bookkeeping themselves. If
        // this is false, nobody in our mod knew this removal was happening — it's
        // TLM's own code discarding her straight into an item (smart slab, photo,
        // backpack, marriage, ...) — and our tracking needs to be told separately,
        // or it keeps pointing at a UUID that no longer exists in the world.
        boolean wasExternalRemoval = !trustedRemovalPending;
        trustedRemovalPending = false;
        if (!level().isClientSide) {
            Player owner = getOwnerPlayer();
            if (owner != null) {
                CommonEventHandler.snapshotCirnoDurable(owner, this);
                if (wasExternalRemoval && reason == RemovalReason.DISCARDED) {
                    CommonEventHandler.handleExternalCapture(owner, this);
                }
            }
            // Legitimate removal actually going through — release her forced chunk so
            // it doesn't stay loaded forever with nothing standing in it.
            CirnoChunkLoadingManager.release(this.getUUID());
        }
        super.remove(reason);
    }

    /**
     * Must be called by trusted code immediately before discard() so the protection
     * layer knows the removal is intentional.
     */
    public void allowNextRemoval() {
        this.trustedRemovalPending = true;
    }

    /**
     * Mirrors ANChorCore's own AnchorCoreBauble#isCallerAllowed (from Touhou Little Maid:
     * Spell, package com.github.yimeng261) almost verbatim — that's the actual reference
     * implementation the rest of this protection scheme was modeled on, and it's proven
     * to work against this exact TLM/Curios/Forge ecosystem in production. A narrow
     * TLM-only check breaks the moment a legitimate removal passes through Curios'
     * internal handlers, Forge's networking/reflection layers, or any other ordinary
     * plumbing between TLM and this entity — none of which are "untrusted" third-party
     * interference, just normal call-stack noise.
     */
    private static boolean isTrustedThirdPartyCaller(String className) {
        // Only TLM-ecosystem callers that legitimately discard the entity into an item
        // (smart slab, photo capture, backpack, marriage, etc.) are trusted here.
        // Our own code (net.zhaiji.catburger) is intentionally NOT listed — our trusted
        // removals must set trustedRemovalPending=true via allowNextRemoval() instead,
        // otherwise the flag would be bypassed and protection would never trigger.
        return className.startsWith("com.github.tartaricacid") || // Touhou Little Maid itself
                className.startsWith("com.github.yimeng261") || // Touhou Little Maid: Spell / ANChorCore
                className.contains("maid");
    }

    /**
     * True if any frame of the current call stack is a trusted caller per
     * {@link #isTrustedThirdPartyCaller(String)} — meaning this removal was initiated
     * by TLM itself (smart slab / photo capture, backpack, marriage, etc.) or another
     * trusted source, rather than some unrelated/untrusted mod.
     */
    private static boolean isTrustedThirdPartyCaller() {
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            if (isTrustedThirdPartyCaller(e.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (source.is(DamageTypes.IN_WALL)) return true;
        return !CatBurgerCommonConfig.cirnoCanDie;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return true;
    }

    @Override
    public boolean canBrainMoving() {
        // Block AI-driven movement when our custom follow handles positioning,
        // OR when sitting (so TLM's MaidFollowOwnerTask won't walk her toward the owner).
        return !isFollowingOwner() && !this.isOrderedToSit();
    }

    @Override
    public boolean isPickable() {
        return CatBurgerCommonConfig.cirnoIsPickable;
    }

    @Override
    public boolean isPushable() {
        return !isFollowingOwner() && !CatBurgerCommonConfig.cirnoDisableMobCollision;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return !isFollowingOwner() && !CatBurgerCommonConfig.cirnoDisableMobCollision;
    }

    // ── Persistence (NBT) ────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerPlayerUUID != null)
            tag.putUUID("OwnerPlayerUUID", ownerPlayerUUID);
        tag.putInt("OwnerMissingTicks", ownerMissingTicks);
        tag.putBoolean("Invulnerable", !CatBurgerCommonConfig.cirnoCanDie);
        // Brand Cirno so she can be identified even after entity type conversion (soul spell, camera, etc.)
        tag.putBoolean("IsCatBurgerCompanion", true);
        if (perEntityFollowOwner != null)
            tag.putBoolean("PerEntityFollowOwner", perEntityFollowOwner);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerPlayerUUID"))
            ownerPlayerUUID = tag.getUUID("OwnerPlayerUUID");
        ownerMissingTicks = tag.getInt("OwnerMissingTicks");
        this.setEntityInvulnerable(!CatBurgerCommonConfig.cirnoCanDie);
        if (tag.contains("PerEntityFollowOwner"))
            perEntityFollowOwner = tag.getBoolean("PerEntityFollowOwner");
    }

    // ── Spawn helper ─────────────────────────────────────────────────────────

    /**
     * Spawns a CirnoEntity companion for the given player and returns its UUID.
     * Must be called server-side only.
     */
    public static UUID spawnFor(Player player) {
        ServerLevel level = (ServerLevel) player.level();
        CirnoEntity cirno = InitEntity.CIRNO.get().create(level);
        if (cirno == null) return null;
        cirno.setOwnerPlayer(player);
        cirno.moveTo(player.getX(), player.getY(), player.getZ(),
                player.getYRot(), 0f);
        cirno.applyModel();
        level.addFreshEntity(cirno);
        return cirno.getUUID();
    }

    /**
     * Finds and removes the companion entity with the given UUID.
     * Searches all server levels so it works correctly across dimension changes.
     */
    public static void removeFor(Player player, UUID companionUUID) {
        if (companionUUID == null || player.level().isClientSide) return;
        if (player.getServer() == null) return;
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity e = level.getEntity(companionUUID);
            if (e != null) {
                e.discard();
                return;
            }
        }
    }
}