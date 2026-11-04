package net.zhaiji.cirno.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.zhaiji.cirno.config.CirnoClientConfig;
import net.zhaiji.cirno.item.CirnoItem;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

/** Curios-only renderer. Loaded exclusively via CuriosHooks when Curios is installed. */
public class CirnoRenderer implements ICurioRenderer {

    /** Shared GeckoLib item renderer – safe to reuse across calls. */
    private static CirnoItemRenderer geoRenderer;

    private static CirnoItemRenderer getGeoRenderer() {
        if (geoRenderer == null) geoRenderer = new CirnoItemRenderer();
        return geoRenderer;
    }

    public static double getFloatSpeed(LivingEntity entity, float partialTicks) {
        return CirnoFloat.getFloatSpeed(entity, partialTicks);
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            ItemStack stack,
            SlotContext slotContext,
            PoseStack matrixStack,
            net.minecraft.client.renderer.entity.RenderLayerParent<T, M> renderLayerParent,
            MultiBufferSource renderTypeBuffer,
            int light,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        LivingEntity entity = slotContext.entity();
        float viewYRot = entity.getViewYRot(partialTicks);
        CirnoRenderData data = CirnoRenderData.RENDER_DATA_MAP
                .computeIfAbsent(entity, e -> new CirnoRenderData(viewYRot, entity));

        // ── Physics tick ────────────────────────────────────────────────────
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

        // ── Rotation ────────────────────────────────────────────────────────
        float usedYaw;
        if (CirnoClientConfig.springEnabled)
            usedYaw = Mth.lerp(partialTicks, data.prevSpringYaw, data.springYaw);
        else if (CirnoClientConfig.rotationDragEnabled)
            usedYaw = Mth.lerp(partialTicks, data.prevDragYaw, data.dragYaw);
        else usedYaw = viewYRot;

        float bodyRot = Mth.lerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float offsetHeadYaw = usedYaw - bodyRot;
        double yawRad = Math.toRadians(offsetHeadYaw);

        // ── Offsets ─────────────────────────────────────────────────────────
        double lerpX = Mth.lerp(partialTicks, entity.xo, entityX);
        double lerpY = Mth.lerp(partialTicks, entity.yo, entityY);
        double lerpZ = Mth.lerp(partialTicks, entity.zo, entityZ);
        double dX = Mth.lerp(partialTicks, data.prevDragX, data.dragX) - lerpX;
        double dY = Mth.lerp(partialTicks, data.prevDragY, data.dragY) - lerpY;
        double dZ = Mth.lerp(partialTicks, data.prevDragZ, data.dragZ) - lerpZ;
        double crouch = Mth.lerp(partialTicks, data.prevDragCrouchOffset, data.dragCrouchOffset);
        double bodyRad = Math.toRadians(bodyRot);
        double cosB = Math.cos(bodyRad), sinB = Math.sin(bodyRad);
        double ldX = -dX * cosB - dZ * sinB, ldZ = dX * sinB - dZ * cosB;

        double xOff = Math.cos(yawRad - Mth.HALF_PI) * CirnoClientConfig.frontBackOffset
                + Math.cos(yawRad) * CirnoClientConfig.leftRightOffset + ldX;
        double yOff = getFloatSpeed(entity, partialTicks) + CirnoClientConfig.verticalOffset + dY + crouch;
        double zOff = Math.sin(yawRad - Mth.HALF_PI) * CirnoClientConfig.frontBackOffset
                + Math.sin(yawRad) * CirnoClientConfig.leftRightOffset + ldZ;

        float renderHeadYaw = (CirnoClientConfig.springEnabled
                || CirnoClientConfig.rotationDragEnabled && CirnoClientConfig.rotationDragAffectOrientation)
                ? offsetHeadYaw : viewYRot - bodyRot;

        // ── Render via GeckoLib ─────────────────────────────────────────────
        matrixStack.pushPose();
        matrixStack.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        matrixStack.translate(xOff, yOff, zOff);
        float scale = (float) CirnoClientConfig.scale;
        matrixStack.scale(scale, scale, scale);
        matrixStack.mulPose(Axis.YP.rotationDegrees(-renderHeadYaw));
        matrixStack.mulPose(Axis.XP.rotationDegrees(-headPitch));

        CirnoItemRenderer renderer = getGeoRenderer();
        renderer.renderByItem(stack, net.minecraft.world.item.ItemDisplayContext.HEAD,
                matrixStack, renderTypeBuffer, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);

        matrixStack.popPose();
    }
}
