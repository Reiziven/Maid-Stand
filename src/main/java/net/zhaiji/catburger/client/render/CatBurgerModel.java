package net.zhaiji.catburger.client.render;

import net.minecraft.resources.ResourceLocation;
import net.zhaiji.catburger.CatBurger;
import net.zhaiji.catburger.item.CatBurgerItem;
import software.bernie.geckolib.model.GeoModel;

public class CatBurgerModel extends GeoModel<CatBurgerItem> {

    private static final ResourceLocation MODEL    = new ResourceLocation(CatBurger.MOD_ID, "geo/cat_burger.geo.json");
    private static final ResourceLocation TEXTURE  = new ResourceLocation(CatBurger.MOD_ID, "textures/item/cat_burger.png");
    private static final ResourceLocation ANIM     = new ResourceLocation(CatBurger.MOD_ID, "animations/cat_burger.animation.json");

    @Override
    public ResourceLocation getModelResource(CatBurgerItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CatBurgerItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CatBurgerItem animatable) {
        return ANIM;
    }
}
