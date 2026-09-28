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

public class CombatEventHandler {

    // ── Death prevention ──────────────────────────────────────────────────────

    public static void handlerLivingDeathEvent(LivingDeathEvent event) {
        if (!CirnoCommonConfig.totemEffectActive) return;
        Item item = InitItem.CIRNO.get();
        if (event.getEntity() instanceof Player player && !player.getCooldowns().isOnCooldown(item)) {
            {
                if (net.zhaiji.cirno.compat.CuriosCompat.hasCirno(player)) {
                    FoodData foodData = player.getFoodData();
                    if (CirnoCommonConfig.usePercentageHealthRestoration) {
                        float maxHealth = player.getMaxHealth();
                        float restoreAmount = maxHealth * ((float) CirnoCommonConfig.percentageHealthRestoration / 100.0f);
                        player.setHealth(restoreAmount);
                    } else {
                        player.setHealth(CirnoCommonConfig.healthRestorationFromTotem);
                    }
                    foodData.setFoodLevel(CirnoCommonConfig.foodRestorationFromTotem);
                    foodData.setSaturation(CirnoCommonConfig.saturationRestorationFromTotem);
                    player.getCooldowns().addCooldown(item, CirnoCommonConfig.totemCooldown);
                    player.level().broadcastEntityEvent(player, (byte) 35);
                    PacketManager.sendToClient(new PlayerDeathPacket(), (ServerPlayer) player);
                    event.setCanceled(true);
                }
            }
        }

        if (!event.isCanceled() && event.getEntity() instanceof Player player) {
            TelekinesisHandler.removeAttributeBuffs(player);
            if (CirnoCommonConfig.companionEntityEnabled && CompatManager.isTLMLoad()) {
                if (CirnoCommonConfig.cirnoPartOfOwner) {
                    CompanionLifecycleHandler.removeCompanion(player);
                } else {
                    // Not bound to the owner's fate: leave her alive in the world —
                    // just snapshot her current data as a durable backup.
                    CompanionLifecycleHandler.snapshotCompanionWithoutRemoving(player);
                }
            }
            CirnoStateAccess.applyPartOfOwnerPolicyToCommandCirnos(player);
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
}
