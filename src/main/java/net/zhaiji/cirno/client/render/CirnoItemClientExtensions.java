package net.zhaiji.cirno.client.render;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Client item extension that points Forge at the GeckoLib BEWLR for in-hand / GUI rendering.
 * <p>
 * Lives in its own class (instead of an anonymous class inside {@code CirnoItem}) so that
 * {@code CirnoItem} never has client-only types on its own class-verification path.
 */
public final class CirnoItemClientExtensions implements IClientItemExtensions {
    private CirnoItemRenderer renderer;

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) {
            renderer = new CirnoItemRenderer();
        }
        return renderer;
    }
}
