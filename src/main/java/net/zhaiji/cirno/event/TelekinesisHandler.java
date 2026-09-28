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

public class TelekinesisHandler {

    // ── Telekinesis shield: block incoming projectiles physically ────────────

    public static void handlerProjectileImpact(net.minecraftforge.event.entity.ProjectileImpactEvent event) {
        if (!(event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hitResult)) return;
        if (!(hitResult.getEntity() instanceof ServerPlayer player)) return;
        if (!CirnoCommonConfig.telekinesisShieldEnabled) return;
        // Any Cirno source counts here — curio companion OR command-spawned Cirno —
        // same rule as active TK control (hasTKAccess), not curio-only.
        if (!CirnoStateAccess.hasTKAccess(player)) return;
        if (!buffsAndTKAllowed()) return;
        if (CirnoStateAccess.getShieldTicksLeft(player) <= 0) return;

        // Block all projectiles except those fired by the shielded player themselves
        Entity owner = event.getProjectile().getOwner();
        if (owner != null && owner.getUUID().equals(player.getUUID())) return;

        event.setCanceled(true);
        event.getProjectile().discard();
    }



    // ── Pre-damage: telekinesis shield auto-trigger ───────────────────────────

    public static void handlerLivingDamageEvent(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CirnoCommonConfig.telekinesisShieldEnabled) return;
        // Any Cirno source counts here — curio companion OR command-spawned Cirno —
        // same rule as active TK control (hasTKAccess), not curio-only.
        if (!CirnoStateAccess.hasTKAccess(player)) return;
        if (!buffsAndTKAllowed()) return;

        float hp = player.getHealth();
        float max = player.getMaxHealth();
        boolean lowHp = (hp / max) <= 0.30f;
        boolean fatalDamage = event.getAmount() >= hp;

        if ((lowHp || fatalDamage) && CirnoStateAccess.getShieldCooldown(player) <= 0 && CirnoStateAccess.getShieldTicksLeft(player) <= 0) {
            activateTelekinesisShield(player);
        }

        if (CirnoStateAccess.getShieldTicksLeft(player) > 0) {
            if (event.getSource().getDirectEntity() instanceof Projectile) {
                event.setCanceled(true);
            }
        }
    }



    public static void toggleTelekinesisMode(Player player) {
        if (!CirnoCommonConfig.telekinesisControlEnabled) return;
        if (!CirnoStateAccess.hasTKAccess(player)) return;
        if (!tkControlAllowed(player)) return;
        CompoundTag data = player.getPersistentData();
        boolean active = data.getBoolean(CirnoStateAccess.NBT_TK_MODE_ACTIVE);
        data.putBoolean(CirnoStateAccess.NBT_TK_MODE_ACTIVE, !active);
    }



    /** Enables telekinesis mode for command-spawned Cirno (bypasses curio requirement). */
    public static void enableTelekinesisMode(Player player) {
        if (!CirnoCommonConfig.telekinesisControlEnabled) return;
        player.getPersistentData().putBoolean(CirnoStateAccess.NBT_TK_MODE_ACTIVE, true);
    }



    public static void startTelekinesisControl(Player player, UUID targetUUID) {
        if (!CirnoCommonConfig.telekinesisControlEnabled) return;
        if (!CirnoStateAccess.hasTKAccess(player)) return;
        if (!tkControlAllowed(player)) return;
        if (player.level().isClientSide) return;

        if (targetUUID.equals(new UUID(0, 0))) {
            clearTelekinesisControl(player);
            return;
        }

        if (CirnoStateAccess.getTKControlCooldown(player) > 0) {
            int remaining = CirnoStateAccess.getTKControlCooldown(player) / 20;
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
        if (data.hasUUID(CirnoStateAccess.NBT_TK_CONTROLLED_UUID) && targetUUID.equals(data.getUUID(CirnoStateAccess.NBT_TK_CONTROLLED_UUID))) {
            data.putBoolean(CirnoStateAccess.NBT_TK_HOLD_ACTIVE, true);
            return;
        }

        data.putUUID(CirnoStateAccess.NBT_TK_CONTROLLED_UUID, targetUUID);
        data.putInt(CirnoStateAccess.NBT_TK_CONTROL_TICKS_LEFT, CirnoCommonConfig.telekinesisControlDuration);
        data.putBoolean(CirnoStateAccess.NBT_TK_HOLD_ACTIVE, true);
        if (target instanceof Mob mob) mob.setNoAi(true);
        player.displayClientMessage(
                Component.literal("§b[Cirno] Controlling: " + target.getName().getString()),
                true
        );
    }



    public static void holdTelekinesisTarget(Player player, Vec3 eyePos, Vec3 lookDir) {
        if (player.level().isClientSide) return;
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(CirnoStateAccess.NBT_TK_CONTROLLED_UUID)) return;

        UUID controlledUUID = data.getUUID(CirnoStateAccess.NBT_TK_CONTROLLED_UUID);
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
        data.putBoolean(CirnoStateAccess.NBT_TK_HOLD_ACTIVE, true);
    }



    // ── Companion-required gate ───────────────────────────────────────────────

    /**
     * Returns true if buffs/shield are allowed given current config. Callers are
     * responsible for first checking that a companion source (curio or command
     * Cirno) actually exists — this only applies the requireCompanionForBuffs gate
     * on top of that.
     */
     static boolean buffsAndTKAllowed() {
        if (CirnoCommonConfig.requireCompanionForBuffs) {
            return CirnoCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad();
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
        if (!CirnoCommonConfig.requireCompanionForBuffs) return true;
        // Curio companion must exist and not be hidden
        if (CirnoStateAccess.hasCurio(player)) {
            ItemStack stack = CirnoStateAccess.getCirnoStack(player);
            if (stack != null && CirnoStateAccess.getCompanionUUID(stack) != null && !CirnoStateAccess.getCompanionHidden(stack)) return true;
        }
        // Any live command-slot Cirno also counts
        return CommandCirnoManager.findAnyLiveCommandCirnoPublic(player) != null;
    }



    // ── Attribute buffs ───────────────────────────────────────────────────────

    public static void applyAttributeBuffs(Player player) {
        if (!CirnoCommonConfig.attributeBuffsEnabled) return;
        if (!buffsAndTKAllowed()) return;

        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null && attack.getModifier(CirnoStateAccess.ATTACK_MODIFIER_UUID) == null) {
            attack.addPermanentModifier(new AttributeModifier(
                    CirnoStateAccess.ATTACK_MODIFIER_UUID, "cirno_attack_buff",
                    CirnoCommonConfig.bonusAttackDamage,
                    AttributeModifier.Operation.ADDITION));
        }

        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null && health.getModifier(CirnoStateAccess.HEALTH_MODIFIER_UUID) == null) {
            health.addPermanentModifier(new AttributeModifier(
                    CirnoStateAccess.HEALTH_MODIFIER_UUID, "cirno_health_buff",
                    CirnoCommonConfig.bonusMaxHealth,
                    AttributeModifier.Operation.ADDITION));
        }
    }



    public static void removeAttributeBuffs(Player player) {
        AttributeInstance attack = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack != null) attack.removeModifier(CirnoStateAccess.ATTACK_MODIFIER_UUID);

        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.removeModifier(CirnoStateAccess.HEALTH_MODIFIER_UUID);
    }



    // ── Telekinesis shield internals ──────────────────────────────────────────

    private static void activateTelekinesisShield(Player player) {
        CompoundTag data = player.getPersistentData();
        data.putInt(CirnoStateAccess.NBT_SHIELD_TICKS_LEFT, CirnoCommonConfig.telekinesisShieldDuration);
    }



     static void tickShield(Player player) {
        CompoundTag data = player.getPersistentData();

        int cooldown = data.getInt(CirnoStateAccess.NBT_SHIELD_COOLDOWN);
        if (cooldown > 0) {
            data.putInt(CirnoStateAccess.NBT_SHIELD_COOLDOWN, cooldown - 1);
        }

        int ticksLeft = data.getInt(CirnoStateAccess.NBT_SHIELD_TICKS_LEFT);
        if (ticksLeft <= 0) return;

        data.putInt(CirnoStateAccess.NBT_SHIELD_TICKS_LEFT, ticksLeft - 1);

        if (ticksLeft - 1 <= 0) {
            data.putInt(CirnoStateAccess.NBT_SHIELD_COOLDOWN, CirnoCommonConfig.telekinesisShieldCooldown);
        }

        double range = CirnoCommonConfig.telekinesisRepelRange;
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

     static void tickTelekinesisControl(Player player) {
        CompoundTag data = player.getPersistentData();

        int cooldown = data.getInt(CirnoStateAccess.NBT_TK_CONTROL_COOLDOWN);
        if (cooldown > 0) {
            data.putInt(CirnoStateAccess.NBT_TK_CONTROL_COOLDOWN, cooldown - 1);
        }

        if (!data.hasUUID(CirnoStateAccess.NBT_TK_CONTROLLED_UUID)) return;

        boolean holdActive = data.getBoolean(CirnoStateAccess.NBT_TK_HOLD_ACTIVE);
        if (!holdActive) {
            clearTelekinesisControl(player);
            return;
        }
        data.putBoolean(CirnoStateAccess.NBT_TK_HOLD_ACTIVE, false);

        int ticksLeft = data.getInt(CirnoStateAccess.NBT_TK_CONTROL_TICKS_LEFT);
        if (ticksLeft <= 0) {
            clearTelekinesisControl(player);
        } else {
            data.putInt(CirnoStateAccess.NBT_TK_CONTROL_TICKS_LEFT, ticksLeft - 1);
            if (ticksLeft - 1 <= 0) {
                clearTelekinesisControl(player);
            }
        }
    }



    // ── Cirno mob retarget (proximity scan) ──────────────────────────────────

    /**
     * Every tick, find any Mob within 8 blocks of any of the owner's live Cirnos
     * (Curios-equipped companion OR command-spawned) that is currently targeting
     * that Cirno, and immediately force-set its target to the owner instead.
     * Only runs when cirnoRetargetAttackers is enabled.
     * <p>
     * Previously this only ever looked at the Curios-companion Cirno (resolved
     * from the equipped curio stack), so a command-spawned Cirno's attackers were
     * never retargeted at all — the same curio-only bug that shield/TK control
     * used to have. This now covers every live Cirno the owner has out, same as
     * {@link CommandCirnoManager#getOrderedLiveCirnosPublic(Player)} does for
     * everything else.
     */
     static void tickCirnoRetarget(Player player) {
        if (!CirnoCommonConfig.cirnoRetargetAttackers) return;
        if (!CirnoCommonConfig.companionEntityEnabled) return;

        ServerLevel level = (ServerLevel) player.level();
        for (CirnoEntity cirno : CommandCirnoManager.getOrderedLiveCirnosPublic(player)) {
            if (!cirno.isAlive()) continue;

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
    }



     static void clearTelekinesisControl(Player player) {
        CompoundTag data = player.getPersistentData();
        if (data.hasUUID(CirnoStateAccess.NBT_TK_CONTROLLED_UUID)) {
            UUID uuid = data.getUUID(CirnoStateAccess.NBT_TK_CONTROLLED_UUID);
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
            data.remove(CirnoStateAccess.NBT_TK_CONTROLLED_UUID);
        }
        data.remove(CirnoStateAccess.NBT_TK_CONTROL_TICKS_LEFT);
        data.remove(CirnoStateAccess.NBT_TK_HOLD_ACTIVE);
        data.putInt(CirnoStateAccess.NBT_TK_CONTROL_COOLDOWN, CirnoCommonConfig.telekinesisControlCooldown);
    }
}
