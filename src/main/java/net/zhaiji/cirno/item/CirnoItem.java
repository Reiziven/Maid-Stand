package net.zhaiji.cirno.item;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.zhaiji.cirno.client.render.CirnoItemRenderer;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public class CirnoItem extends Item implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * NBT key holding which visual skin this specific stack uses. Every skin
     * shares the exact same behavior (Curios tick, tooltip, recipes,
     * companion tracking) — this is purely a cosmetic model/texture/animation
     * pick, so nothing outside the rendering code needs to know it exists.
     */
    private static final String TAG_SKIN = "Skin";
    public static final String DEFAULT_SKIN = "cirno";

    /**
     * Every skin that ships a model/texture/animation triple under
     * assets/cirno/{geo,textures/item,animations}/&lt;skin&gt;.*. Add a name
     * here (plus its lang entries) to make a new skin selectable — no other
     * code needs to change.
     */
    public static final List<String> SKINS = List.of("cirno", "solyn", "spirit", "tatsumaki");

    public CirnoItem() {
        super(new Item.Properties().stacksTo(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /** The skin id stored on {@code stack}, or {@link #DEFAULT_SKIN} if unset/unknown. */
    public static String getSkin(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SKIN)) return DEFAULT_SKIN;
        String skin = tag.getString(TAG_SKIN);
        return SKINS.contains(skin) ? skin : DEFAULT_SKIN;
    }

    /** A fresh stack of this item using the given skin (for the creative tab, /give, etc.). */
    public static ItemStack withSkin(Item item, String skin) {
        ItemStack stack = new ItemStack(item);
        if (!DEFAULT_SKIN.equals(skin)) {
            stack.getOrCreateTag().putString(TAG_SKIN, skin);
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        String skin = getSkin(stack);
        return DEFAULT_SKIN.equals(skin) ? super.getName(stack) : Component.translatable("item.cirno." + skin);
    }

    // ── GeoItem ──────────────────────────────────────────────────────────────

    private static final RawAnimation ANIM_IDLE  = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_WALK  = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ANIM_RUN   = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ANIM_SNEAK = RawAnimation.begin().thenLoop("sneak");
    private static final RawAnimation ANIM_JUMP  = RawAnimation.begin().thenLoop("jump");
    private static final RawAnimation ANIM_SWIM  = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation ANIM_SLEEP = RawAnimation.begin().thenPlay("sleep");

    /**
     * The single-clip name inside each non-default skin's own animation file
     * (they only ship one static "hide unused bones" pose each, unlike the
     * full idle/walk/run/... set the default Cirno skin has). Keyed by skin id.
     */
    private static String poseAnimationName(String skin) {
        return switch (skin) {
            case "spirit" -> "spirit.animation";
            case "tatsumaki" -> "tatsumaki.animation.json";
            default -> skin; // "solyn"'s clip is literally named "solyn"
        };
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 2, state -> {
            // Which stack is actually being rendered right now (GeckoLib populates
            // this for item animatables) tells us which skin is active, since
            // CirnoItemRenderer swaps geo/texture/animation per-stack.
            ItemStack renderedStack = state.getData(software.bernie.geckolib.constant.DataTickets.ITEMSTACK);
            String skin = renderedStack != null ? getSkin(renderedStack) : DEFAULT_SKIN;

            if (!DEFAULT_SKIN.equals(skin)) {
                // Non-default skins only have a single static pose right now —
                // no movement-reactive animation set to switch between.
                state.getController().setAnimation(RawAnimation.begin().thenPlay(poseAnimationName(skin)));
                return PlayState.CONTINUE;
            }

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
            private CirnoItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null)
                    renderer = new CirnoItemRenderer();
                return renderer;
            }
        });
    }

    // Curios behaviour (equip / tick) lives in compat.curios.CirnoCurioItem and is only
    // registered when Curios is installed, so this class never references the Curios API.

    @Override
    public void appendHoverText(ItemStack itemStack, @Nullable Level level,
                                List<Component> list, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, level, list, tooltipFlag);
        String skin = getSkin(itemStack);
        list.add(Component.translatable("item.cirno." + skin + ".tooltip"));
    }
}
