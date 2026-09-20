package net.zhaiji.catburger.client.render;

import net.minecraft.resources.ResourceLocation;
import net.zhaiji.catburger.CatBurger;
import net.zhaiji.catburger.item.CatBurgerItem;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Used for rendering the item in GUI, hand, and item frames.
 * The Curios floating-head rendering is handled separately in {@link CatBurgerRenderer}.
 */
public class CatBurgerItemRenderer extends GeoItemRenderer<CatBurgerItem> {
    public CatBurgerItemRenderer() {
        super(new CatBurgerModel());
    }
}
