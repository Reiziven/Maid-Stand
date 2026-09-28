package net.zhaiji.cirno.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.zhaiji.cirno.item.CirnoItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Used for rendering the item in GUI, hand, and item frames.
 * The Curios floating-head rendering is handled separately in {@link CirnoRenderer}.
 */
public class CirnoItemRenderer extends GeoItemRenderer<CirnoItem> {

    /**
     * GeckoLib puts the model origin (feet, centered) at these coordinates of the
     * item's 1x1x1 cube before drawing (see GeoItemRenderer). Used to re-center
     * the model inside an inventory slot.
     */
    private static final float GECKO_ORIGIN_X = 0.5f;
    private static final float GECKO_ORIGIN_Y = 0.51f;
    private static final float GECKO_ORIGIN_Z = 0.5f;

    /**
     * Per-skin GUI fitting: { centerX, centerY, centerZ, scale }.
     * <p>
     * center* = center of the skin's visible bounding box in blocks (model units / 16),
     * measured from the model origin. scale = factor that shrinks the skin so its
     * front view fits inside one 16x16 inventory slot (~90% of the slot). The skins
     * are all very different sizes (roughly 1.9 to 3.5 blocks tall), which is why
     * a single value in the item's JSON "display" block cannot fit all of them.
     * <p>
     * To tweak: make a skin smaller/larger by editing its scale (last number).
     */
    private static float[] guiFit(String skin) {
        return switch (skin) {
            case "solyn"     -> new float[]{ 0.013f, 1.481f,  0.400f, 0.286f };
            case "spirit"    -> new float[]{ 0.000f, 1.669f, -0.081f, 0.253f };
            case "tatsumaki" -> new float[]{ 0.000f, 1.356f, -0.031f, 0.332f };
            default          -> new float[]{ 0.013f, 0.775f,  0.238f, 0.478f }; // "cirno"
        };
    }

    private final CirnoModel cirnoModel;

    public CirnoItemRenderer() {
        this(new CirnoModel());
    }

    private CirnoItemRenderer(CirnoModel model) {
        super(model);
        this.cirnoModel = model;
    }

    /**
     * Every render entry point (hand, GUI, item frame, and the Curios
     * floating-head renderer, which calls this directly with the equipped
     * stack) goes through here, so pushing the stack's skin onto the shared
     * model right before delegating is enough to keep every one of them in
     * sync with whichever skin this particular stack actually has.
     */
    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                              MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        String skin = CirnoItem.getSkin(stack);
        cirnoModel.setSkin(skin);

        if (transformType != ItemDisplayContext.GUI) {
            super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }

        // GUI / inventory slots / creative tab only. Without this the full-size entity
        // model is drawn at ~1 block per 16px, so it towers over the slot and spills
        // into neighbouring ones, and (Bedrock models face -Z) it shows its back.
        float[] fit = guiFit(skin);
        float scale = fit[3];

        poseStack.pushPose();
        // Pivot at the middle of the slot's cube...
        poseStack.translate(0.5f, 0.5f, 0.5f);
        poseStack.scale(scale, scale, scale);
        // ...turn the model around so its front faces the viewer...
        poseStack.mulPose(Axis.YP.rotationDegrees(180f));
        // ...and move the model's own center onto that pivot.
        poseStack.translate(
                -(GECKO_ORIGIN_X + fit[0]),
                -(GECKO_ORIGIN_Y + fit[1]),
                -(GECKO_ORIGIN_Z + fit[2]));
        super.renderByItem(stack, transformType, poseStack, bufferSource, packedLight, packedOverlay);
        poseStack.popPose();
    }
}
