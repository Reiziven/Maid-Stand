package net.zhaiji.cirno.entity;

import net.zhaiji.cirno.event.CirnoStateAccess;
import net.zhaiji.cirno.event.CommandCirnoManager;
import net.zhaiji.cirno.event.CompanionLifecycleHandler;
import net.zhaiji.cirno.event.PlayerLifecycleHandler;
import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.Entity.RemovalReason;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.compat.YsmCarryAnimationCompat;
import net.zhaiji.cirno.init.InitEntity;
import net.zhaiji.cirno.util.CirnoChunkLoadingManager;
import net.minecraft.world.level.ChunkPos;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Companion entity spawned when the Cirno curio is equipped.
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
     * Set to true by trusted code (event handlers, dimension change, H key)
     * immediately before calling discard() or remove(). When cirnoUnloadProtection
     * is on, any removal NOT preceded by this flag is blocked — same approach
     * ANChorCore uses with its ThreadLocal allow-removal flag.
     */
    private boolean trustedRemovalPending = false;

    /** Duplicate candidates must disappear without saving over the live Cirno's data. */
    private boolean discardDuplicatePending = false;

    /** Ticks since the last periodic NBT autosave (see CirnoCommonConfig#cirnoAutoSaveEnabled). */
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

    // How often (in ticks) to check the leash-distance recall below. Distance to the
    // owner doesn't need checking every single tick — this is purely a "did she get
    // left behind / is she still catching up after a chunk reload" safety net, not the
    // tight per-tick positioning isFollowingOwner() already handles on its own.
    private static final int LEASH_CHECK_INTERVAL = 20; // 1 second

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

    /**
     * Animation-only horizontal follow speed for YSM. This is deliberately separate from
     * Entity.deltaMovement: owner-follow uses direct setPos() positioning, and feeding the
     * interpolation velocity into deltaMovement would make Minecraft physically move/collide
     * Cirno a second time. YSM's q.ground_speed mixin reads this value instead.
     */
    private double ysmFollowGroundSpeed;

    // ── Client-side flight-var resync throttling ─────────────────────────────
    // applyYsmFlightVars() used to run unconditionally every client tick (20/s) as a
    // safety net for entity data arriving a frame late after a teleport/dimension change.
    // The side effect: it sets rouletteAnimDirty = true every single tick, which kept
    // re-triggering TLM/YSM's roulette-anim sync and stomped on any *other* roulette
    // animation playing at the time (dance, etc.) — those clips move her body, so being
    // re-applied 20x/sec broke them instead of letting them play through.
    // Now: apply immediately on an actual state change, and otherwise only re-apply as a
    // periodic safety net (and only while an airborne pose is active, since that's the
    // only case the stale-data problem applies to) at this interval instead of every tick.
    @Nullable
    private CirnoFlightState lastAppliedClientFlightState = null;
    private int clientFlightVarsRefreshTicks = 0;
    private static final int CLIENT_FLIGHT_VARS_REFRESH_INTERVAL = 20; // was: every tick

    // ── Rideable "princess carry" flight mount ───────────────────────────────
    // Whether she currently has a saddle equipped — a rider only ever appears via
    // Entity#startRiding once this is true. Persisted so she stays saddled across saves.
    private static final EntityDataAccessor<Boolean> DATA_MOUNT_SADDLED =
            SynchedEntityData.defineId(CirnoEntity.class, EntityDataSerializers.BOOLEAN);
    // True while a mounted rider currently has flight toggled on (double-tap jump).
    // Synced purely so client-side rendering/animation can react to it.
    private static final EntityDataAccessor<Boolean> DATA_MOUNT_FLYING =
            SynchedEntityData.defineId(CirnoEntity.class, EntityDataSerializers.BOOLEAN);
    // Keys used in the rider's persistent data to save/restore their flight ability
    // across the mount/dismount so survival players can't keep permanent creative-style
    // flight after hopping off (see grantFlightAbility / revokeFlightAbility).
    private static final String NBT_SAVED_MAYFLY = "CirnoMountSavedMayFly";
    private static final String NBT_SAVED_FLYING = "CirnoMountSavedFlying";
    private static final String NBT_ABILITY_SAVED = "CirnoMountAbilitySaved";

    /** Vanilla's double-tap-W sprint state is stored on the rider; Cirno uses it as her run state. */
    private static final float MOUNT_SPRINT_SPEED_MULTIPLIER = 1.30F;

    // ── Mounted dismount: double-tap sneak ───────────────────────────────────
    // Sneak while flying is now "descend" (mirrors creative flight), so a single sneak press
    // can no longer double as "dismount" the way it used to — only a second press arriving
    // shortly after the first does. Tracked server-side only (handleMountedTick never runs
    // on the client — see tick()'s early return), using the same rising-edge + tick-window
    // approach vanilla uses for its own double-tap-forward sprint trigger.

    public CirnoEntity(EntityType<CirnoEntity> type, Level level) {
        super((EntityType<EntityMaid>)(EntityType<?>) type, level);
        this.setEntityInvulnerable(!CirnoCommonConfig.cirnoCanDie);
        this.setNoAi(!CirnoCommonConfig.cirnoAiEnabled);
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
    /** Animation-only speed reported to YSM while owner-follow is active. */
    public double getYsmFollowGroundSpeed() {
        return ysmFollowGroundSpeed;
    }

    public boolean isFollowingOwner() {
        if (perEntityFollowOwner != null) return perEntityFollowOwner;
        return CirnoCommonConfig.cirnoFollowsOwner;
    }

    /** Called server-side by ToggleMaidFollowPacket to flip this entity's follow state. */
    public void setPerEntityFollow(boolean follow) {
        this.perEntityFollowOwner = follow;
        this.nbtDirty = true;
        // Owner just asked her to actively follow — don't leave her stuck asleep
        // in the Maid Bed while we start dragging her around via setPos().
        if (follow) {
            wakeFromMaidBed();
        }
    }

    /**
     * Forces her out of TLM's vanilla sleep state (Maid Bed). Called whenever the
     * owner actively summons or recalls her — the per-entity Follow Owner toggle,
     * the H key, or a Maid Panel Summon — so she doesn't stay glued in the sleep
     * pose (or get repositioned to the owner's side still "asleep") the moment
     * they clearly want her up and active. Mirrors what TLM's own
     * MaidClearSleepTask does when her sleep is interrupted (see
     * MaidClearSleepTaskMixin): calling stopSleeping() is the one authoritative
     * "leave bed" action, so nothing else needs to be reset by hand here — her
     * own canBrainMoving() override already stops TLM's bed-seeking/REST
     * behaviors from pulling her back in while she's following.
     */
    public void wakeFromMaidBed() {
        if (this.isSleeping()) {
            this.stopSleeping();
        }
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
                && CirnoCommonConfig.cirnoDisablePhysicsWhileFollowing
                && CirnoCommonConfig.cirnoAttackSeeThroughBlocks) {
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

    // ── Flight animation state ───────────────────────────────────────────────

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLIGHT_STATE, (byte) CirnoFlightState.NONE.ordinal());
        this.entityData.define(DATA_MOUNT_SADDLED, false);
        this.entityData.define(DATA_MOUNT_FLYING, false);
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
     * variables. The standard YSM {@code ctrl.fly} predicate is supplied separately for Cirno.
     */
    private void applyYsmFlightVars(CirnoFlightState state) {
        // Mounted flight is a separate control path from owner-follow flight, but the YSM
        // model should receive the same "fly" signal so both paths use the same animation.
        CirnoFlightState effectiveState = isMountFlying() ? CirnoFlightState.FLY : state;

        // EntityMaid.roamingVars is public and synced when roamingVarsUpdateFlag changes.
        this.roamingVars.put("cirno_fly", effectiveState == CirnoFlightState.FLY ? 1f : 0f);
        this.roamingVars.put("cirno_fall", effectiveState == CirnoFlightState.FALL ? 1f : 0f);
        this.roamingVars.put("cirno_jump", effectiveState == CirnoFlightState.JUMP ? 1f : 0f);
        this.roamingVars.put("cirno_elytra", effectiveState.isElytra() ? 1f : 0f);
        this.roamingVars.put("cirno_elytra_boost", effectiveState == CirnoFlightState.ELYTRA_BOOST ? 1f : 0f);
        this.roamingVarsUpdateFlag++;
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

    // ── Rideable "princess carry" flight mount ───────────────────────────────
    //
    // The "Mount Cirno" keybind while holding a saddle both equips it the first time and mounts
    // the presser (tryMountFromKey) — the same single gesture whether or not she's saddled yet.
    // Right-click on a held saddle only ever dismounts (see mobInteract); every other right-click
    // falls through to her normal maid interactions. While riding, movement is fully
    // player-controlled (see travel()): walking on the ground, or flying once the rider double-taps
    // jump. That double-tap is handled entirely by vanilla — we just grant the rider `mayfly` for
    // the duration of the ride (see grantFlightAbility/revokeFlightAbility, hooked into
    // add/removePassenger below) so the game's own creative-flight-toggle input does the rest, and
    // read back `getAbilities().flying` in travel() to know whether she should be airborne.
    //
    // Once flying, controls mirror creative-mode flight exactly: jump ascends, sneak descends,
    // and holding sprint (Ctrl) flies fast (see cirnoMountFlyFastSpeed). Sneak no longer doubles
    // as a dismount gesture — the rider bails out by right-clicking while holding a saddle
    // instead (see mobInteract()). On the ground, forward is walking speed and double-tapping W
    // sprints her (vanilla's own double-tap-forward toggle), which also switches her to the
    // sprint animation.

    public boolean isMountSaddled() {
        return this.entityData.get(DATA_MOUNT_SADDLED);
    }

    private void setMountSaddled(boolean saddled) {
        this.entityData.set(DATA_MOUNT_SADDLED, saddled);
        this.nbtDirty = true;
    }

    /** True while a mounted rider currently has flight toggled on. Valid on both sides. */
    public boolean isMountFlying() {
        return this.entityData.get(DATA_MOUNT_FLYING);
    }

    private void setMountFlying(boolean flying) {
        if (flying != isMountFlying()) {
            this.entityData.set(DATA_MOUNT_FLYING, flying);
        }
    }

    /** Whether the given player is allowed to saddle/ride her, per cirnoMountOwnerOnly. */
    private boolean canBeRiddenBy(Player player) {
        if (!CirnoCommonConfig.cirnoMountOwnerOnly) return true;
        Player owner = getOwnerPlayer();
        return owner != null && owner.getUUID().equals(player.getUUID());
    }

    /** Server-side only. Mounts {@code player} if Cirno is currently free. */
    private void tryMount(Player player) {
        if (level().isClientSide || this.isVehicle()) return;
        player.startRiding(this, true);
    }

    /**
     * Grants the rider `mayfly` for the duration of the ride so vanilla's own double-tap-jump
     * toggle can turn `flying` on/off — travel() just reads that back. The player's original
     * mayfly/flying values are stashed in their persistent data and restored the moment they
     * stop riding (see removePassenger), so a survival player can never walk away from a ride
     * with permanent creative-style flight.
     */
    private void grantFlightAbility(Player player) {
        if (player.level().isClientSide) return;
        // Config: Cirno can't fly while carrying — never hand out mayfly.
        if (!CirnoCommonConfig.cirnoMountCanFly) return;
        CompoundTag pdata = player.getPersistentData();
        pdata.putBoolean(NBT_SAVED_MAYFLY, player.getAbilities().mayfly);
        pdata.putBoolean(NBT_SAVED_FLYING, player.getAbilities().flying);
        pdata.putBoolean(NBT_ABILITY_SAVED, true);
        player.getAbilities().mayfly = true;
        player.onUpdateAbilities();
    }

    private void revokeFlightAbility(Player player) {
        if (player.level().isClientSide) return;
        CompoundTag pdata = player.getPersistentData();
        if (!pdata.getBoolean(NBT_ABILITY_SAVED)) return;
        boolean savedMayFly = pdata.getBoolean(NBT_SAVED_MAYFLY);
        boolean savedFlying = pdata.getBoolean(NBT_SAVED_FLYING);
        pdata.remove(NBT_SAVED_MAYFLY);
        pdata.remove(NBT_SAVED_FLYING);
        pdata.remove(NBT_ABILITY_SAVED);

        boolean wasFlying = player.getAbilities().flying;
        player.getAbilities().mayfly = savedMayFly;
        player.getAbilities().flying = savedFlying;
        player.onUpdateAbilities();

        if (wasFlying && !savedFlying && CirnoCommonConfig.cirnoMountDismountSlowFallTicks > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING,
                    CirnoCommonConfig.cirnoMountDismountSlowFallTicks, 0, false, false, true));
        }
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (!level().isClientSide && passenger instanceof Player player) {
            grantFlightAbility(player);
            setMountFlying(false);

            if (player instanceof ServerPlayer serverPlayer) {
                YsmCarryAnimationCompat.start(this, serverPlayer);
            }
        }
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (!level().isClientSide && passenger instanceof Player player) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                YsmCarryAnimationCompat.stop(this, serverPlayer);
            }
            revokeFlightAbility(player);
        }
        if (this.getPassengers().isEmpty()) {
            setMountFlying(false);
            setNoGravity(false);
            // Clear her own run animation right at the moment of dismount instead of leaving
            // it to whatever the rider's sprint flag happened to be mid-dismount (double-W sprint
            // or Ctrl fast-fly can both still be "on" the instant stopRiding() fires) — otherwise
            // she can visibly stay stuck in the sprint pose until something else touches the flag.
            this.setSprinting(false);
        }
    }

    /** Only Cirno's rider can steer her — everyone else's clicks fall through to normal maid interaction. */
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        if (CirnoCommonConfig.cirnoMountEnabled && isMountSaddled()
                && this.getFirstPassenger() instanceof Player player) {
            return player;
        }
        return null;
    }

    /**
     * Princess-carry position, tucked against her chest instead of on her back/head.
     *
     * <p>YSM's {@code carryon:entity}/{@code carryon:princess} animation pair (see
     * {@link YsmCarryAnimationCompat}, {@link YsmPrincessCarryMixin}) only bends the visual arms —
     * it never moves the rider entity itself. Without an explicit offset the rider sits at the
     * inherited default riding position, which is near the top of Cirno's hitbox (she's registered
     * at full player-size, {@code 0.6 x 1.8}, for YSM/hitbox compatibility) — i.e. up near her head,
     * not in her arms. The three offsets are the server-authoritative {@code cirnoMountCarry*}
     * options in the common config; see {@link CirnoClientConfig} for a client-only, purely
     * cosmetic nudge on top of this if the pose looks slightly off for a particular resource/model
     * pack on just your own screen.</p>
     */
    @Override
    protected void positionRider(Entity passenger, MoveFunction moveFunction) {
        if (!this.hasPassenger(passenger)) return;
        float yawRad = (float) Math.toRadians(this.yBodyRot);
        double forward = CirnoCommonConfig.cirnoMountCarryForwardOffset;
        double side = CirnoCommonConfig.cirnoMountCarrySideOffset;
        double up = CirnoCommonConfig.cirnoMountCarryUpOffset;
        double px = this.getX() + (-Math.sin(yawRad) * forward) + (Math.cos(yawRad) * side);
        double py = this.getY() + up;
        double pz = this.getZ() + (Math.cos(yawRad) * forward) + (Math.sin(yawRad) * side);
        moveFunction.accept(passenger, px, py, pz);
        if (passenger instanceof LivingEntity living) {
            living.setYBodyRot(this.yBodyRot);
            living.yHeadRot = this.yHeadRot;
        }
    }

    /**
     * Server-side housekeeping for a mount in progress: keeps her AI/animation state quiet
     * while the rider is steering (actual movement happens in {@link #travel(Vec3)}, which
     * the game calls every tick regardless of this).
     */
    private void handleMountedTick() {
        if (!(getControllingPassenger() instanceof Player rider)) return;

        if (!level().isClientSide && rider instanceof ServerPlayer serverPlayer) {
            YsmCarryAnimationCompat.tick(this, serverPlayer);
        }

        // While mounted, the rider owns movement state. In particular, vanilla's double-tap-W
        // sprint flag belongs to the player, so do not clear Cirno's sprint state here.
        this.fallDistance = 0f;
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (this.isAlive() && this.isVehicle() && getControllingPassenger() instanceof Player rider) {
            this.setYRot(rider.getYRot());
            this.yRotO = this.getYRot();
            this.setXRot(rider.getXRot() * 0.5F);
            this.yBodyRot = this.getYRot();
            this.yHeadRot = this.getYRot();

            // Config: cirnoMountCanFly = false disables mounted flight entirely. If the rider still
            // ends up flying (config changed mid-ride, or they already had creative flight), drop them.
            if (!CirnoCommonConfig.cirnoMountCanFly && rider.getAbilities().flying) {
                if (!level().isClientSide) {
                    rider.getAbilities().flying = false;
                    rider.onUpdateAbilities();
                }
            }
            boolean flying = CirnoCommonConfig.cirnoMountCanFly && rider.getAbilities().flying;
            boolean wasFlying = isMountFlying();
            setMountFlying(flying);
            this.setNoGravity(flying);

            // YSM's automatic "fly" state should own the airborne pose. Stop only the
            // temporary carrier clip when transitioning into flight; do not manually trigger "fly".
            if (!level().isClientSide && CirnoCommonConfig.cirnoFlyAnimationEnabled
                    && flying != wasFlying) {
                if (flying) {
                    YsmCarryAnimationCompat.stopCarrier(this);
                } else {
                    YsmCarryAnimationCompat.startCarrier(this);
                }
            }

            float strafe = rider.xxa * 0.6F;
            float forward = rider.zza;
            if (forward <= 0.0F) forward *= 0.3F; // backwards is slower, like a normal ridden mount

            if (flying) {
                this.setSprinting(false);
                // Holding the sprint key (Ctrl by default) while flying is the same "fly fast"
                // gesture creative mode uses — just read rider.isSprinting() back for a speed boost.
                boolean fastFly = rider.isSprinting();
                this.setSpeed((float) (fastFly
                        ? CirnoCommonConfig.cirnoMountFlyFastSpeed
                        : CirnoCommonConfig.cirnoMountFlySpeed));

                // The rider's second jump toggles vanilla mayfly/flying on. Once flying,
                // keep Cirno in her own controlled flight mode rather than using her follow pose.
                setFlightState(CirnoFlightState.FLY);
            } else {
                boolean sprinting = rider.isSprinting() && forward > 0.0F;
                this.setSprinting(sprinting);
                float groundSpeed = (float) CirnoCommonConfig.cirnoMountWalkSpeed;
                if (sprinting) {
                    groundSpeed *= MOUNT_SPRINT_SPEED_MULTIPLIER;
                }
                this.setSpeed(groundSpeed);

                // A single jump starts a normal mount jump. A second jump while airborne is
                // handled by vanilla's mayfly toggle and moves us into the flying branch above.
                if (rider.jumping && this.onGround()) {
                    this.jumpFromGround();
                }

                if (getFlightState() == CirnoFlightState.FLY) {
                    setFlightState(CirnoFlightState.NONE);
                }
            }

            super.travel(new Vec3(strafe, 0.0, forward));

            if (flying) {
                Vec3 dm = this.getDeltaMovement();
                double vy = dm.y;
                if (rider.jumping) {
                    // Space: ascend, exactly like creative flight.
                    vy = Math.min(vy + CirnoCommonConfig.cirnoMountFlyVerticalAccel,
                            CirnoCommonConfig.cirnoMountFlyVerticalSpeed);
                } else if (rider.isShiftKeyDown()) {
                    // Sneak: descend, exactly like creative flight.
                    vy = Math.max(vy - CirnoCommonConfig.cirnoMountFlyVerticalAccel,
                            -CirnoCommonConfig.cirnoMountFlyVerticalSpeed);
                } else {
                    vy *= CirnoCommonConfig.cirnoMountFlyHoverDamping;
                }
                this.setDeltaMovement(dm.x, vy, dm.z);
                this.fallDistance = 0f;
            }
            return;
        }
        super.travel(travelVector);
    }

    /**
     * Dismount interaction only. Anything else falls through to TLM's own handling.
     * <p>
     * There's deliberately no click-driven "equip + mount" or "mount" case here anymore — the
     * first used to hijack right-click only while she was unsaddled (shift + right-click +
     * saddle), the second hijacked it permanently once saddled, so either way you could no
     * longer right-click her for anything else (opening her GUI, feeding her, etc.). Saddling
     * and mounting are both driven by the "Mount Cirno" keybind while holding a saddle instead —
     * see {@link #tryMountFromKey(Player)}, driven from ClientEventHandler /
     * CirnoMountRequestPacket — which leaves right-click free for everything else.
     */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (CirnoCommonConfig.cirnoMountEnabled) {
            ItemStack stack = player.getItemInHand(hand);
            boolean holdingSaddle = stack.is(Items.SADDLE);

            // Already her rider, holding a saddle: mirrors the mount gesture in reverse to dismount.
            if (holdingSaddle && this.hasPassenger(player)) {
                if (!level().isClientSide) {
                    player.stopRiding();
                }
                return InteractionResult.sidedSuccess(level().isClientSide);
            }
        }
        return super.mobInteract(player, hand);
    }

    /**
     * Server-side: saddles her (if she isn't already) and mounts {@code player}, in response to
     * the "Mount Cirno" keybind. Handles both the first-ever saddle-and-mount and every mount
     * after that with the same single gesture (key + saddle in hand), so right-click never gets
     * claimed for any part of this feature. Re-checks every precondition server-side since the
     * request arrives over the network. Returns whether the mount actually happened, purely so
     * the caller can decide whether to give feedback.
     */
    public boolean tryMountFromKey(Player player) {
        if (level().isClientSide || !CirnoCommonConfig.cirnoMountEnabled) return false;
        if (this.isVehicle() || !this.isAlive()) return false;
        if (!canBeRiddenBy(player)) {
            player.displayClientMessage(
                    Component.literal("§c[Cirno] Only her owner can ride her."), true);
            return false;
        }

        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        ItemStack saddleStack = mainHand.is(Items.SADDLE) ? mainHand
                : (offHand.is(Items.SADDLE) ? offHand : ItemStack.EMPTY);
        if (saddleStack.isEmpty()) return false;

        if (!isMountSaddled()) {
            setMountSaddled(true);
            this.playSound(SoundEvents.HORSE_SADDLE, 1.0F, 1.0F);
        }

        tryMount(player);
        return true;
    }

    /** Ejects the rider (if any) and restores their flight ability. Safe to call even if she isn't mounted. */
    public void dismountRiderIfAny() {
        if (this.getFirstPassenger() instanceof Player) {
            this.ejectPassengers();
        }
    }

    // ── Tick / follow logic ───────────────────────────────────────────────────

    @Override
    public void tick() {
        boolean mounted = this.isVehicle() && getControllingPassenger() instanceof Player;

        this.noPhysics = !mounted && isFollowingOwner()
                && CirnoCommonConfig.cirnoDisablePhysicsWhileFollowing;

        super.tick();

        // Client: keep YSM roamingVars in sync with the latest flight state. Applied
        // immediately whenever the state actually changes; otherwise only re-applied
        // periodically (see CLIENT_FLIGHT_VARS_REFRESH_INTERVAL) as a safety net for
        // entity data arriving a frame late after a teleport / dimension change, and
        // only while a flight pose is active — an idle (NONE) state, e.g. while a
        // dance or other roulette animation is playing, is left completely alone so
        // it isn't repeatedly interrupted.
        if (level().isClientSide) {
            CirnoFlightState state = getFlightState();
            boolean changed = state != lastAppliedClientFlightState;
            if (changed) {
                clientFlightVarsRefreshTicks = 0;
            } else if (state != CirnoFlightState.NONE) {
                clientFlightVarsRefreshTicks++;
            }
            if (changed || (state != CirnoFlightState.NONE
                    && clientFlightVarsRefreshTicks >= CLIENT_FLIGHT_VARS_REFRESH_INTERVAL)) {
                lastAppliedClientFlightState = state;
                clientFlightVarsRefreshTicks = 0;
                applyYsmFlightVars(state);
            }
            return;
        }


        // Keep the chunk she's standing in force-loaded so it never becomes eligible to
        // unload out from under her in the first place — this is what actually makes
        // cirnoUnloadProtection work; the guard in remove() alone just leaves her stuck
        // in a frozen "ghost" state once a chunk starts unloading around her.
        if (CirnoCommonConfig.cirnoUnloadProtection && level() instanceof ServerLevel serverLevel) {
            CirnoChunkLoadingManager.update(this.getUUID(), serverLevel, new ChunkPos(this.blockPosition()));
        }

        Player owner = getOwnerPlayer();
        if (owner == null || !owner.isAlive()) {
            // When unload protection is on she stays in the world indefinitely —
            // no grace-period countdown, no self-removal.
            if (!CirnoCommonConfig.cirnoUnloadProtection) {
                ownerMissingTicks++;
                if (ownerMissingTicks >= OWNER_MISSING_GRACE) {
                    ownerMissingTicks = OWNER_MISSING_GRACE;
                }
            }
            return;
        }
        ownerMissingTicks = 0;

        // Keep the exact source chunk on the owner so a dimension change can
        // load and transfer this entity even if the chunk is unloaded later.
        PlayerLifecycleHandler.recordCirnoLocation(owner, this);

        // ── Leash-distance recall ────────────────────────────────────────────
        // Independent of isFollowingOwner(): that flag only controls the tight
        // drag-along positioning while actively following, so when it's off (the
        // default) or the owner simply outran her, nothing else ever brings her
        // back — she's left to TLM's own AI, which has no notion of "the owner is
        // now several hundred blocks away in a chunk I've never even loaded". This
        // check runs regardless of follow mode and regardless of *why* she fell
        // behind, including having just been reloaded by the chunk-loading
        // watchdog after sitting in a stuck chunk.
        //
        // Mirrors vanilla tameable-pet recall (TamableAnimal's own teleport-to-owner
        // goal), not a blind teleportTo(owner.getX()/Y()/Z()): only fires while the
        // owner is actually standing on solid ground (never mid-jump, mid-fall, in an
        // elytra glide, riding, or swimming — any of which could put the owner's own
        // Y well off actual ground), and even then only lands her on an *actual* safe,
        // solid, collision-free spot found near the owner, exactly like a wolf or cat
        // would. If no safe spot turns up this check, she simply stays put and tries
        // again next interval rather than ever being dropped into mid-air, inside a
        // wall, or off a ledge.
        if (CirnoCommonConfig.cirnoMaxLeashDistance > 0 && this.tickCount % LEASH_CHECK_INTERVAL == 0
                && owner.level() == this.level() && owner.onGround() && !owner.isPassenger()
                && !owner.isSwimming() && !owner.isFallFlying()) {
            double leashSqr = CirnoCommonConfig.cirnoMaxLeashDistance * CirnoCommonConfig.cirnoMaxLeashDistance;
            if (this.distanceToSqr(owner) > leashSqr && level() instanceof ServerLevel serverLevel) {
                BlockPos safeSpot = findSafeRecallSpot(serverLevel, owner.blockPosition());
                if (safeSpot != null) {
                    this.teleportTo(safeSpot.getX() + 0.5, safeSpot.getY(), safeSpot.getZ() + 0.5);
                }
            }
        }

        // Deferred discard: removeCompanion() couldn't reach us while we were in an
        // unloaded chunk — now that we're ticking, execute the pending removal.
        // Two independent breadcrumb mechanisms can apply: the single-UUID one used
        // by the Curios-equipped companion (always at most one at a time), and the
        // per-slot list used by command-mode Cirnos (up to CirnoStateAccess.MAX_COMMAND_CIRNOS
        // of her can be mid-unload at once).
        CompoundTag pdata = owner.getPersistentData();
        if (pdata.hasUUID("CirnoPendingDiscardUUID")) {
            UUID pendingUUID = pdata.getUUID("CirnoPendingDiscardUUID");
            if (pendingUUID.equals(this.getUUID())) {
                pdata.remove("CirnoPendingDiscardUUID");
                this.allowNextRemoval();
                this.discard();
                return;
            }
        }
        CommandCirnoManager.consumePendingCommandDiscard(owner, this);
        if (this.isRemoved()) return;

        // Duplicate safety net: if a replacement was already spawned and tracked for
        // this owner's curio while this (older) entity was dormant, remove this one
        // instead of leaving two Cirnos. Checked every 2 s, and not on the first ticks
        // after joining so freshly-bound revivals get time to be registered.
        if (this.tickCount > 40 && this.tickCount % 40 == 0
                && CompanionLifecycleHandler.isOrphanedDuplicate(owner, this)) {
            this.discardDuplicate();
            return;
        }

        if (CirnoCommonConfig.cirnoAutoSaveEnabled) {
            autoSaveTicks++;
            if (autoSaveTicks >= Math.max(20, CirnoCommonConfig.cirnoAutoSaveIntervalTicks)) {
                autoSaveTicks = 0;
                if (nbtDirty) {
                    nbtDirty = false;
                    CompanionLifecycleHandler.snapshotCirnoDurable(owner, this);
                }
            }
        }

        if (mounted) {
            handleMountedTick();
        } else if (isFollowingOwner()) {
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
            java.util.List<CirnoEntity> formation = CommandCirnoManager.getOrderedLiveCirnosPublic(owner);
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
            double currentX = this.getX();
            double currentY = this.getY();
            double currentZ = this.getZ();
            double newX = Mth.lerp(FOLLOW_LERP_SPEED, currentX, targetX);
            double newY = Mth.lerp(FOLLOW_LERP_SPEED, currentY, targetY);
            double newZ = Mth.lerp(FOLLOW_LERP_SPEED, currentZ, targetZ);

            if (distSqr > FOLLOW_SNAP_DISTANCE_SQR) {
                // FOLLOW SNAP FALLBACK: only hard-teleport when the owner/follow target is
                // more than 8 blocks away. Normal following never teleports every tick.
                this.teleportTo(targetX, targetY, targetZ);
            }

            // FOLLOW POSITION + YSM-ONLY SPEED: keep direct setPos() as the sole movement
            // mechanism so Minecraft does not apply the same follow displacement again through
            // physics/collision. Record the equivalent speed separately for YSM's q.ground_speed.
            if (distSqr <= FOLLOW_SNAP_DISTANCE_SQR) {
                double movedX = newX - currentX;
                double movedY = newY - currentY;
                double movedZ = newZ - currentZ;

                this.setPos(newX, newY, newZ);
                this.ysmFollowGroundSpeed = Math.sqrt(movedX * movedX + movedZ * movedZ) * 20.0D;
            } else {
                // A >8 block recovery is a teleport, not physical movement. For animation, use
                // the owner's current horizontal speed rather than exposing the snap as velocity.
                double ownerVx = owner.getDeltaMovement().x;
                double ownerVz = owner.getDeltaMovement().z;
                this.ysmFollowGroundSpeed = Math.sqrt(ownerVx * ownerVx + ownerVz * ownerVz) * 20.0D;
            }

            this.setYRot(owner.getYRot());
            this.yHeadRot = owner.getYHeadRot();
            this.yBodyRot = owner.yBodyRot;

            // Mirror the owner's pitch only while the owner is actually Elytra flying.
            // Do not use Cirno's animation state here, because that state can be FLY
            // while the owner is still Elytra flying.
            CirnoFlightState currentFlightState = computeFlightState(owner);
            if (owner.isFallFlying()) {
                this.setXRot(owner.getXRot());
                this.xRotO = owner.xRotO;
            }

            // Jump / fall / fly / elytra pose. Runs after the position update above so the
            // "is there floor under her" test sees where she actually is this tick.
            this.setFlightState(currentFlightState);

            // Mirror the owner's running animation. While following, her actual
            // movement is driven by the position lerp/teleport above rather than
            // real AI/physics (canBrainMoving() is false here), so isSprinting()
            // would never get set naturally and TLM's model would never pick the
            // running pose no matter how fast she's being dragged along. Syncing
            // it directly fixes that. Only the run animation is synced this way
            // for now — other poses (sneak, swim, etc.) are left as-is.
            this.setSprinting(owner.isSprinting());

            // Preserve the original vertical follow mirror.  This is deliberately
            // Y-only: X/Z follow stays direct setPos() so we do not physically move
            // Cirno a second time.  Y movement follows the owner as before.
            this.setDeltaMovement(this.getDeltaMovement().x, owner.getDeltaMovement().y, this.getDeltaMovement().z);

            // GENERAL OWNER-GROUND MIRROR (movement/animation support).
            // Keep the existing owner -> Cirno on-ground mirror for all normal follow cases;
            // other parts of the maid movement/animation pipeline can rely on it.
            this.setOnGround(owner.onGround());

            // OWNER CREATIVE-FLIGHT ANIMATION MIRROR (special case only).
            // When this Cirno is following her owner and the owner is in ability/creative
            // flight, deliberately reuse the already-working "owner is grounded / Cirno is
            // airborne" animation situation. We do NOT mirror the player's fly animation,
            // and we do NOT change the actual owner or Cirno flight state/physics here.
            // This is intentionally separate from the general mirror above so the normal
            // owner-ground behavior remains unchanged when the owner is not flying.
            if (isFollowingOwner() && owner.getAbilities().flying) {
                this.setOnGround(true);
            }

            this.fallDistance = 0f;

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
        if (!CirnoCommonConfig.cirnoFlyAnimationEnabled
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
        return flightTracker.update(input, Math.max(1, CirnoCommonConfig.cirnoFlyDelayTicks));
    }

    /**
     * Finds a safe spot to land the leash-distance recall near {@code center} (the
     * owner's position): solid, sturdy ground directly beneath, and no block
     * collision at the spot itself, exactly what vanilla's own tameable-pet
     * teleport-to-owner search requires before it will actually move the pet. Tries
     * the owner's own feet first, then a handful of small random nearby offsets, and
     * gives up (returns null) rather than ever picking somewhere unsafe.
     */
    @Nullable
    private BlockPos findSafeRecallSpot(ServerLevel level, BlockPos center) {
        if (isSafeStandingSpot(level, center)) return center;
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = this.random.nextInt(7) - 3; // -3..3
            int dz = this.random.nextInt(7) - 3;
            int dy = this.random.nextInt(3) - 1;  // -1..1
            BlockPos candidate = center.offset(dx, dy, dz);
            if (isSafeStandingSpot(level, candidate)) return candidate;
        }
        return null;
    }

    private boolean isSafeStandingSpot(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) return false;
        AABB box = this.getBoundingBox().move(
                pos.getX() + 0.5 - this.getX(),
                pos.getY() - this.getY(),
                pos.getZ() + 0.5 - this.getZ());
        return level.noCollision(this, box);
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

        if (CirnoCommonConfig.cirnoRetargetAttackers) {
            Entity attacker = source.getEntity();
            if (attacker instanceof Mob mob) {
                Player owner = getOwnerPlayer();
                if (owner != null && owner.isAlive()) {
                    mob.setTarget(owner);
                }
            }
        }

        if (CirnoCommonConfig.cirnoCanDie) {
            return super.hurt(source, amount);
        }
        return false;
    }

    @Override
    public void die(DamageSource source) {
        if (!CirnoCommonConfig.cirnoCanDie) return;
        if (level().isClientSide) return;
        if (this.isRemoved() || this.dead) return;

        if (this.getMaidBauble().fireEvent((b, s) -> b.onDeath(this, s, source))) return;
        if (MinecraftForge.EVENT_BUS.post(new MaidDeathEvent(this, source))) return;
        if (ForgeHooks.onLivingDeath(this, source)) return;

        Player owner = getOwnerPlayer();

        if (CirnoCommonConfig.cirnoSpawnTombstoneOnDeath) {
            // Real death: flag whichever tracking system owns her (curio item or a
            // command slot) as "gone, needs a real item to come back from" BEFORE
            // handing off to TLM's own death handling below (tombstone included via
            // dropEquipment()). Without this, the stale UUID/hidden bookkeeping left
            // over from a normal auto-revive death would let her be freely
            // re-summoned — a duplicate, blank Cirno — the instant the curio is
            // re-equipped or Store/Summon is pressed again, tombstone or no tombstone.
            if (owner != null) {
                CompanionLifecycleHandler.handleTombstoneDeath(owner, this);
            }
            super.die(source);
            return;
        }

        if (owner == null) {
            this.setHealth(this.getMaxHealth());
            this.setInvisible(true);
            this.setEntityInvulnerable(true);
            return;
        }

        this.setHealth(this.getMaxHealth());
        CompanionLifecycleHandler.handleCompanionDeath(owner, this);
    }

    @Override
    protected void dropEquipment() {
        if (CirnoCommonConfig.cirnoSpawnTombstoneOnDeath) {
            // Let TLM spawn its normal tombstone for Cirno like any other maid.
            super.dropEquipment();
            return;
        }
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
        if (!level().isClientSide && CirnoCommonConfig.cirnoUnloadProtection) {
            // KILLED is only allowed when cirnoCanDie is explicitly enabled
            boolean isAllowedKill = reason == RemovalReason.KILLED && CirnoCommonConfig.cirnoCanDie;
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
                    CompanionLifecycleHandler.snapshotCirnoDurable(owner, this);
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
        // Removal is actually going through: make sure a rider isn't left dangling on an
        // entity that's about to vanish, and gets their flight ability restored properly
        // (removePassenger, triggered by this, handles the restore).
        dismountRiderIfAny();
        if (!level().isClientSide) {
            Player owner = getOwnerPlayer();
            if (owner != null && !discardDuplicatePending) {
                CompanionLifecycleHandler.snapshotCirnoDurable(owner, this);
                if (wasExternalRemoval && reason == RemovalReason.DISCARDED) {
                    CompanionLifecycleHandler.handleExternalCapture(owner, this);
                }
            }
            // Legitimate removal actually going through — release her forced chunk so
            // it doesn't stay loaded forever with nothing standing in it.
            CirnoChunkLoadingManager.release(this.getUUID());
        }
        super.remove(reason);
    }

    /** Removes a duplicate candidate without invoking protection or durable-save side effects. */
    public void discardDuplicate() {
        discardDuplicatePending = true;
        trustedRemovalPending = true;
        discard();
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
        // Our own code (net.zhaiji.cirno) is intentionally NOT listed — our trusted
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
        return !CirnoCommonConfig.cirnoCanDie;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return true;
    }

    @Override
    public boolean canBrainMoving() {
        // Block AI-driven movement when our custom follow (or a rider) handles positioning,
        // OR when sitting (so TLM's MaidFollowOwnerTask won't walk her toward the owner).
        return !isFollowingOwner() && !this.isOrderedToSit() && !this.isVehicle();
    }

    @Override
    public boolean isPickable() {
        return CirnoCommonConfig.cirnoIsPickable;
    }

    // ── Persistence (NBT) ────────────────────────────────────────────────────

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerPlayerUUID != null)
            tag.putUUID("OwnerPlayerUUID", ownerPlayerUUID);
        tag.putInt("OwnerMissingTicks", ownerMissingTicks);
        tag.putBoolean("Invulnerable", !CirnoCommonConfig.cirnoCanDie);
        // Brand Cirno so she can be identified even after entity type conversion (soul spell, camera, etc.)
        tag.putBoolean("IsCirnoCompanion", true);
        if (perEntityFollowOwner != null)
            tag.putBoolean("PerEntityFollowOwner", perEntityFollowOwner);
        tag.putBoolean("CirnoMountSaddled", isMountSaddled());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("OwnerPlayerUUID"))
            ownerPlayerUUID = tag.getUUID("OwnerPlayerUUID");
        ownerMissingTicks = tag.getInt("OwnerMissingTicks");
        this.setEntityInvulnerable(!CirnoCommonConfig.cirnoCanDie);
        if (tag.contains("PerEntityFollowOwner"))
            perEntityFollowOwner = tag.getBoolean("PerEntityFollowOwner");
        if (tag.contains("CirnoMountSaddled"))
            this.entityData.set(DATA_MOUNT_SADDLED, tag.getBoolean("CirnoMountSaddled"));
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