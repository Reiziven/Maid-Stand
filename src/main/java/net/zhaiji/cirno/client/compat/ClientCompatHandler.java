package net.zhaiji.cirno.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.zhaiji.cirno.client.render.CirnoItemRenderer;
import net.zhaiji.cirno.client.render.CirnoRenderData;
import net.zhaiji.cirno.client.render.CirnoFloat;
import net.zhaiji.cirno.compat.CuriosCompat;
import net.zhaiji.cirno.compat.CompatManager;
import net.zhaiji.cirno.compat.TLMCompat;
import net.zhaiji.cirno.config.CirnoClientConfig;
import net.zhaiji.cirno.entity.CirnoEntity;
import net.zhaiji.cirno.init.InitItem;
import org.joml.Quaternionf;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public class ClientCompatHandler {

    private static CirnoItemRenderer geoRenderer;

    private static CirnoItemRenderer getGeoRenderer() {
        if (geoRenderer == null) geoRenderer = new CirnoItemRenderer();
        return geoRenderer;
    }

    /**
     * Client-only cosmetic nudge (see {@link CirnoClientConfig#cirnoCarryRenderForwardOffset}
     * and friends) applied to the carried player's rendered model only — the actual, server-synced
     * riding position from {@link CirnoEntity#positionRider} is untouched. Push here, pop in the
     * matching {@link #handlerRenderPlayerCarry$Post}.
     */
    public static void handlerRenderPlayerCarry$Pre(RenderLivingEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player.getVehicle() instanceof CirnoEntity)) return;

        double forward = CirnoClientConfig.cirnoCarryRenderForwardOffset;
        double side = CirnoClientConfig.cirnoCarryRenderSideOffset;
        double up = CirnoClientConfig.cirnoCarryRenderUpOffset;
        if (forward == 0 && side == 0 && up == 0) return;

        float yawRad = (float) Math.toRadians(player.getViewYRot(event.getPartialTick()));
        double x = (-Math.sin(yawRad) * forward) + (Math.cos(yawRad) * side);
        double z = (Math.cos(yawRad) * forward) + (Math.sin(yawRad) * side);

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(x, up, z);
    }

    /** Pops the pose pushed in {@link #handlerRenderPlayerCarry$Pre}, using the same guard condition. */
    public static void handlerRenderPlayerCarry$Post(RenderLivingEvent.Post event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player.getVehicle() instanceof CirnoEntity)) return;

        double forward = CirnoClientConfig.cirnoCarryRenderForwardOffset;
        double side = CirnoClientConfig.cirnoCarryRenderSideOffset;
        double up = CirnoClientConfig.cirnoCarryRenderUpOffset;
        if (forward == 0 && side == 0 && up == 0) return;

        event.getPoseStack().popPose();
    }

    public static void handlerRenderLivingEvent$Post(RenderLivingEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (!CompatManager.isYSMLoad() && !(CompatManager.isTLMLoad() && TLMCompat.canRender(entity))) return;

        Item item = InitItem.CIRNO.get();
        // Curios is optional: without it there is never an equipped Cirno to render here.
        if (!CuriosCompat.isCirnoVisible(entity)) return;
        {

            PoseStack matrixStack = event.getPoseStack();
            float partialTicks = event.getPartialTick();
            float viewYRot = entity.getViewYRot(partialTicks);
            float headPitch = entity.getViewXRot(partialTicks);
            MultiBufferSource renderTypeBuffer = event.getMultiBufferSource();
            int light = event.getPackedLight();

            CirnoRenderData data = CirnoRenderData.RENDER_DATA_MAP
                    .computeIfAbsent(entity, e -> new CirnoRenderData(viewYRot, entity));

            // ── Physics tick ─────────────────────────────────────────────────
            int currentTick = entity.tickCount;
            double entityX = entity.getX(), entityY = entity.getY(), entityZ = entity.getZ();

            if (currentTick != data.lastTick) {
                if (currentTick - data.lastTick > 5) {
                    data.dragX = entityX; data.dragY = entityY; data.dragZ = entityZ;
                    data.dragYaw = viewYRot; data.springYaw = viewYRot; data.velocity = 0;
                    data.dragCrouchOffset = entity.isCrouching() ? -0.5 : 0;
                }
                data.prevDragX = data.dragX; data.prevDragY = data.dragY; data.prevDragZ = data.dragZ;
                data.prevDragCrouchOffset = data.dragCrouchOffset;
                double targetCrouch = entity.isCrouching() ? -0.5 : 0;
                if (CirnoClientConfig.dragEnabled) {
                    data.dragX += (entityX - data.dragX) * CirnoClientConfig.dragStrength;
                    data.dragY += (entityY - data.dragY) * CirnoClientConfig.dragStrength;
                    data.dragZ += (entityZ - data.dragZ) * CirnoClientConfig.dragStrength;
                    data.dragCrouchOffset += (targetCrouch - data.dragCrouchOffset) * CirnoClientConfig.dragStrength;
                } else { data.dragX = entityX; data.dragY = entityY; data.dragZ = entityZ; data.dragCrouchOffset = targetCrouch; }
                data.prevDragYaw = data.dragYaw;
                if (CirnoClientConfig.rotationDragEnabled) {
                    float diff = CirnoClientConfig.rotationDragUseWrapDegrees
                            ? Mth.wrapDegrees(viewYRot - data.dragYaw) : viewYRot - data.dragYaw;
                    data.dragYaw += diff * (float) CirnoClientConfig.rotationDragSmoothness;
                } else { data.dragYaw = viewYRot; }
                data.prevSpringYaw = data.springYaw;
                if (CirnoClientConfig.springEnabled) {
                    float disp = viewYRot - data.springYaw;
                    data.velocity = data.velocity * (float) CirnoClientConfig.springDamping
                            + (float) CirnoClientConfig.springStiffness * disp;
                    data.springYaw += data.velocity;
                } else { data.springYaw = viewYRot; data.velocity = 0; }
                data.lastTick = currentTick;
            }

            // ── Rotation ──────────────────────────────────────────────────────
            float usedYaw;
            if (CirnoClientConfig.springEnabled)
                usedYaw = Mth.lerp(partialTicks, data.prevSpringYaw, data.springYaw);
            else if (CirnoClientConfig.rotationDragEnabled)
                usedYaw = Mth.lerp(partialTicks, data.prevDragYaw, data.dragYaw);
            else usedYaw = viewYRot;

            double yawRad = Math.toRadians(-usedYaw);

            // ── Offsets ───────────────────────────────────────────────────────
            double lerpX = Mth.lerp(partialTicks, entity.xo, entityX);
            double lerpY = Mth.lerp(partialTicks, entity.yo, entityY);
            double lerpZ = Mth.lerp(partialTicks, entity.zo, entityZ);
            double dX = Mth.lerp(partialTicks, data.prevDragX, data.dragX) - lerpX;
            double dY = Mth.lerp(partialTicks, data.prevDragY, data.dragY) - lerpY;
            double dZ = Mth.lerp(partialTicks, data.prevDragZ, data.dragZ) - lerpZ;
            double crouch = Mth.lerp(partialTicks, data.prevDragCrouchOffset, data.dragCrouchOffset);

            double xOff = Math.cos(yawRad - Mth.HALF_PI) * CirnoClientConfig.frontBackOffset
                    - Math.cos(yawRad) * CirnoClientConfig.leftRightOffset + dX;
            double yOff = 1.5 + CirnoFloat.getFloatSpeed(entity, partialTicks)
                    + CirnoClientConfig.verticalOffset + dY + crouch;
            double zOff = -Math.sin(yawRad - Mth.HALF_PI) * CirnoClientConfig.frontBackOffset
                    + Math.sin(yawRad) * CirnoClientConfig.leftRightOffset + dZ;

            float renderYaw = (CirnoClientConfig.springEnabled
                    || CirnoClientConfig.rotationDragEnabled && CirnoClientConfig.rotationDragAffectOrientation)
                    ? usedYaw : viewYRot;

            // ── Render via GeckoLib ───────────────────────────────────────────
            matrixStack.pushPose();
            matrixStack.translate(xOff, yOff, zOff);
            float scale = (float) CirnoClientConfig.scale;
            matrixStack.scale(scale, scale, scale);
            matrixStack.mulPose(new Quaternionf().rotateY((float) Math.PI));
            matrixStack.mulPose(Axis.YP.rotationDegrees(-renderYaw));
            matrixStack.mulPose(Axis.XP.rotationDegrees(-headPitch));

            // IMPORTANT: pass the actual equipped stack, not CIRNO.getDefaultInstance().
            // The variant is stored on the ItemStack, so using a fresh/default stack here
            // strips the Skin tag and makes every equipped variant render as Cirno.
            ItemStack equippedStack = CuriosCompat.getCirnoStack(entity);
            getGeoRenderer().renderByItem(equippedStack,
                    ItemDisplayContext.HEAD, matrixStack, renderTypeBuffer, light,
                    OverlayTexture.NO_OVERLAY);

            matrixStack.popPose();
        }
    }
}
