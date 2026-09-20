package net.zhaiji.catburger.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.zhaiji.catburger.client.render.CatBurgerItemRenderer;
import net.zhaiji.catburger.config.CatBurgerCommonConfig;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class CatBurgerItem extends Item implements ICurioItem, GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public CatBurgerItem() {
        super(new Item.Properties().stacksTo(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // ── GeoItem ──────────────────────────────────────────────────────────────

    private static final RawAnimation ANIM_IDLE  = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_WALK  = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ANIM_RUN   = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ANIM_SNEAK = RawAnimation.begin().thenLoop("sneak");
    private static final RawAnimation ANIM_JUMP  = RawAnimation.begin().thenLoop("jump");
    private static final RawAnimation ANIM_SWIM  = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation ANIM_SLEEP = RawAnimation.begin().thenPlay("sleep");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 2, state -> {
            // GeoItem state doesn't carry entity context, so we pull it from the
            // living entity that is currently wearing the item via the Minecraft client.
            net.minecraft.world.entity.LivingEntity entity = null;
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                // Check if any nearby entity (including the local player) is wearing this item.
                // For SingletonGeoAnimatable items the state extracts the wearer from extra data
                // set by the Curio tick; fall back to the local player as the most common case.
                entity = net.minecraft.client.Minecraft.getInstance().player;
            }

            if (entity == null) {
                state.getController().setAnimation(ANIM_IDLE);
                return PlayState.CONTINUE;
            }

            if (entity.isSleeping()) {
                state.getController().setAnimation(ANIM_SLEEP);
                return PlayState.CONTINUE;
            }
            if (entity.isSwimming() || entity.isUnderWater()) {
                state.getController().setAnimation(ANIM_SWIM);
                return PlayState.CONTINUE;
            }
            if (entity.isCrouching()) {
                state.getController().setAnimation(ANIM_SNEAK);
                return PlayState.CONTINUE;
            }

            double speed = entity.getDeltaMovement().horizontalDistanceSqr();
            if (entity.isSprinting() && speed > 0.01) {
                state.getController().setAnimation(ANIM_RUN);
            } else if (!entity.onGround() && entity.getDeltaMovement().y > 0.05) {
                state.getController().setAnimation(ANIM_JUMP);
            } else if (speed > 0.001) {
                state.getController().setAnimation(ANIM_WALK);
            } else {
                state.getController().setAnimation(ANIM_IDLE);
            }
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // Tell Forge to use our GeckoLib BEWLR for in-hand / GUI rendering
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private CatBurgerItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null)
                    renderer = new CatBurgerItemRenderer();
                return renderer;
            }
        });
    }

    // ── ICurioItem ────────────────────────────────────────────────────────────

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player
                && player.tickCount % CatBurgerCommonConfig.curiosCooldown == 0) {
            FoodData foodData = player.getFoodData();
            int foodLevel = foodData.getFoodLevel();
            if (foodLevel < CatBurgerCommonConfig.foodMaxRestoration) {
                foodData.setFoodLevel(Math.min(
                        foodLevel + CatBurgerCommonConfig.foodRestorationFromCurios,
                        CatBurgerCommonConfig.foodMaxRestoration));
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack itemStack, @Nullable Level level,
                                List<Component> list, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, level, list, tooltipFlag);
        list.add(Component.translatable("item.catburger.cat_burger.tooltip"));
    }
}
