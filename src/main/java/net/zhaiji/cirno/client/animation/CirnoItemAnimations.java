package net.zhaiji.cirno.client.animation;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.zhaiji.cirno.item.CirnoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * CLIENT-ONLY animation logic for the Cirno item model.
 * <p>
 * This used to live inline in {@link CirnoItem#registerControllers}, as a lambda that
 * assigned {@code Minecraft.getInstance().player} (a {@code LocalPlayer}) into a
 * {@code LivingEntity} local. The JVM verifier has to load {@code LocalPlayer} to prove that
 * assignment is legal, and {@code LocalPlayer} does not exist on a dedicated server — so
 * merely loading {@code CirnoItem} there threw {@link NoClassDefFoundError} and took the whole
 * server down during mod loading.
 * <p>
 * Keeping every client type in THIS class means {@code CirnoItem} can be verified on any
 * side; this class is only ever touched from {@code CirnoItem#registerControllers}, which
 * itself refuses to run on a dedicated server.
 */
public final class CirnoItemAnimations {
    private CirnoItemAnimations() {
    }

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ANIM_RUN = RawAnimation.begin().thenLoop("run");
    private static final RawAnimation ANIM_SNEAK = RawAnimation.begin().thenLoop("sneak");
    private static final RawAnimation ANIM_JUMP = RawAnimation.begin().thenLoop("jump");
    private static final RawAnimation ANIM_SWIM = RawAnimation.begin().thenLoop("swim");
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

    public static PlayState handle(AnimationState<CirnoItem> state) {
        // Which stack is actually being rendered right now (GeckoLib populates
        // this for item animatables) tells us which skin is active, since
        // CirnoItemRenderer swaps geo/texture/animation per-stack.
        ItemStack renderedStack = state.getData(DataTickets.ITEMSTACK);
        String skin = renderedStack != null ? CirnoItem.getSkin(renderedStack) : CirnoItem.DEFAULT_SKIN;

        if (!CirnoItem.DEFAULT_SKIN.equals(skin)) {
            // Non-default skins only have a single static pose right now —
            // no movement-reactive animation set to switch between.
            state.getController().setAnimation(RawAnimation.begin().thenPlay(poseAnimationName(skin)));
            return PlayState.CONTINUE;
        }

        // GeoItem state doesn't carry entity context, so fall back to the local player,
        // which is the wearer in the overwhelmingly common (first-person / GUI) case.
        LivingEntity entity = currentClientPlayer();

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
    }

    private static LivingEntity currentClientPlayer() {
        return Minecraft.getInstance().player;
    }
}
