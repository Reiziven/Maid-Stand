package net.zhaiji.catburger.client.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.zhaiji.catburger.client.render.CatBurgerItemRenderer;
import net.zhaiji.catburger.client.render.CatBurgerRenderData;
import net.zhaiji.catburger.client.render.CatBurgerRenderer;
import net.zhaiji.catburger.compat.CompatManager;
import net.zhaiji.catburger.compat.TLMCompat;
import net.zhaiji.catburger.config.CatBurgerClientConfig;
import net.zhaiji.catburger.init.InitItem;
import org.joml.Quaternionf;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.Optional;

public class ClientCompatHandler {

    private static CatBurgerItemRenderer geoRenderer;

    private static CatBurgerItemRenderer getGeoRenderer() {
        if (geoRenderer == null) geoRenderer = new CatBurgerItemRenderer();
        return geoRenderer;
    }

    public static void handlerRenderLivingEvent$Post(RenderLivingEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (!CompatManager.isYSMLoad() && !(CompatManager.isTLMLoad() && TLMCompat.canRender(entity))) return;

        Item item = InitItem.CAT_BURGER.get();
        CuriosApi.getCuriosInventory(entity).ifPresent(iCuriosItemHandler -> {
            Optional<SlotResult> slotResult = iCuriosItemHandler.findFirstCurio(item);
            if (slotResult.isEmpty() || !slotResult.get().slotContext().visible()) return;

            PoseStack matrixStack = event.getPoseStack();
            float partialTicks = event.getPartialTick();
            float viewYRot = entity.getViewYRot(partialTicks);
            float headPitch = entity.getViewXRot(partialTicks);
            MultiBufferSource renderTypeBuffer = event.getMultiBufferSource();
            int light = event.getPackedLight();

            CatBurgerRenderData data = CatBurgerRenderData.RENDER_DATA_MAP
                    .computeIfAbsent(entity, e -> new CatBurgerRenderData(viewYRot, entity));

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

            // ── Rotation ──────────────────────────────────────────────────────
            float usedYaw;
            if (CatBurgerClientConfig.springEnabled)
                usedYaw = Mth.lerp(partialTicks, data.prevSpringYaw, data.springYaw);
            else if (CatBurgerClientConfig.rotationDragEnabled)
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

            double xOff = Math.cos(yawRad - Mth.HALF_PI) * CatBurgerClientConfig.frontBackOffset
                    - Math.cos(yawRad) * CatBurgerClientConfig.leftRightOffset + dX;
            double yOff = 1.5 + CatBurgerRenderer.getFloatSpeed(entity, partialTicks)
                    + CatBurgerClientConfig.verticalOffset + dY + crouch;
            double zOff = -Math.sin(yawRad - Mth.HALF_PI) * CatBurgerClientConfig.frontBackOffset
                    + Math.sin(yawRad) * CatBurgerClientConfig.leftRightOffset + dZ;

            float renderYaw = (CatBurgerClientConfig.springEnabled
                    || CatBurgerClientConfig.rotationDragEnabled && CatBurgerClientConfig.rotationDragAffectOrientation)
                    ? usedYaw : viewYRot;

            // ── Render via GeckoLib ───────────────────────────────────────────
            matrixStack.pushPose();
            matrixStack.translate(xOff, yOff, zOff);
            float scale = (float) CatBurgerClientConfig.scale;
            matrixStack.scale(scale, scale, scale);
            matrixStack.mulPose(new Quaternionf().rotateY((float) Math.PI));
            matrixStack.mulPose(Axis.YP.rotationDegrees(-renderYaw));
            matrixStack.mulPose(Axis.XP.rotationDegrees(-headPitch));

            getGeoRenderer().renderByItem(item.getDefaultInstance(),
                    ItemDisplayContext.HEAD, matrixStack, renderTypeBuffer, light,
                    OverlayTexture.NO_OVERLAY);

            matrixStack.popPose();
        });
    }
}
