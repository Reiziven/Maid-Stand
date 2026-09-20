package net.zhaiji.catburger.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.zhaiji.catburger.config.CatBurgerClientConfig;
import net.zhaiji.catburger.item.CatBurgerItem;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.ICurioRenderer;

public class CatBurgerRenderer implements ICurioRenderer {

    /** Shared GeckoLib item renderer – safe to reuse across calls. */
    private static CatBurgerItemRenderer geoRenderer;

    private static CatBurgerItemRenderer getGeoRenderer() {
        if (geoRenderer == null) geoRenderer = new CatBurgerItemRenderer();
        return geoRenderer;
    }

    public static double getFloatSpeed(LivingEntity entity, float partialTicks) {
        return CatBurgerClientConfig.floatDistance / 2
                * Math.sin((entity.tickCount + partialTicks) * Mth.HALF_PI / CatBurgerClientConfig.time);
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
        CatBurgerRenderData data = CatBurgerRenderData.RENDER_DATA_MAP
                .computeIfAbsent(entity, e -> new CatBurgerRenderData(viewYRot, entity));

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
            if (CatBurgerClientConfig.dragEnabled) {
                data.dragX += (entityX - data.dragX) * CatBurgerClientConfig.dragStrength;
                data.dragY += (entityY - data.dragY) * CatBurgerClientConfig.dragStrength;
                data.dragZ += (entityZ - data.dragZ) * CatBurgerClientConfig.dragStrength;
                data.dragCrouchOffset += (targetCrouch - data.dragCrouchOffset) * CatBurgerClientConfig.dragStrength;
            } else { data.dragX = entityX; data.dragY = entityY; data.dragZ = entityZ; data.dragCrouchOffset = targetCrouch; }
            data.prevDragYaw = data.dragYaw;
            if (CatBurgerClientConfig.rotationDragEnabled) {
                float diff = CatBurgerClientConfig.rotationDragUseWrapDegrees
                        ? Mth.wrapDegrees(viewYRot - data.dragYaw) : viewYRot - data.dragYaw;
                data.dragYaw += diff * (float) CatBurgerClientConfig.rotationDragSmoothness;
            } else { data.dragYaw = viewYRot; }
            data.prevSpringYaw = data.springYaw;
            if (CatBurgerClientConfig.springEnabled) {
                float disp = viewYRot - data.springYaw;
                data.velocity = data.velocity * (float) CatBurgerClientConfig.springDamping
                        + (float) CatBurgerClientConfig.springStiffness * disp;
                data.springYaw += data.velocity;
            } else { data.springYaw = viewYRot; data.velocity = 0; }
            data.lastTick = currentTick;
        }

        // ── Rotation ────────────────────────────────────────────────────────
        float usedYaw;
        if (CatBurgerClientConfig.springEnabled)
            usedYaw = Mth.lerp(partialTicks, data.prevSpringYaw, data.springYaw);
        else if (CatBurgerClientConfig.rotationDragEnabled)
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

        double xOff = Math.cos(yawRad - Mth.HALF_PI) * CatBurgerClientConfig.frontBackOffset
                + Math.cos(yawRad) * CatBurgerClientConfig.leftRightOffset + ldX;
        double yOff = getFloatSpeed(entity, partialTicks) + CatBurgerClientConfig.verticalOffset + dY + crouch;
        double zOff = Math.sin(yawRad - Mth.HALF_PI) * CatBurgerClientConfig.frontBackOffset
                + Math.sin(yawRad) * CatBurgerClientConfig.leftRightOffset + ldZ;

        float renderHeadYaw = (CatBurgerClientConfig.springEnabled
                || CatBurgerClientConfig.rotationDragEnabled && CatBurgerClientConfig.rotationDragAffectOrientation)
                ? offsetHeadYaw : viewYRot - bodyRot;

        // ── Render via GeckoLib ─────────────────────────────────────────────
        matrixStack.pushPose();
        matrixStack.mulPose(new Quaternionf().rotateZ((float) Math.PI));
        matrixStack.translate(xOff, yOff, zOff);
        float scale = (float) CatBurgerClientConfig.scale;
        matrixStack.scale(scale, scale, scale);
        matrixStack.mulPose(Axis.YP.rotationDegrees(-renderHeadYaw));
        matrixStack.mulPose(Axis.XP.rotationDegrees(-headPitch));

        CatBurgerItemRenderer renderer = getGeoRenderer();
        renderer.renderByItem(stack, net.minecraft.world.item.ItemDisplayContext.HEAD,
                matrixStack, renderTypeBuffer, light,
                net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);

        matrixStack.popPose();
    }
}
