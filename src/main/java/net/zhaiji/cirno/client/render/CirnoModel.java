package net.zhaiji.cirno.client.render;

import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;
import net.zhaiji.cirno.item.CirnoItem;
import software.bernie.geckolib.model.GeoModel;

/**
 * One shared GeckoLib model for every skin of {@link CirnoItem}. GeckoLib's
 * SingletonGeoAnimatable pattern only gives {@code getXResource} the Item
 * instance (shared by every stack of that item), not the ItemStack being
 * rendered — so {@link CirnoItemRenderer} sets {@link #setSkin} to the
 * current stack's skin right before each render call, and these methods just
 * build the resource path for whichever skin is currently set.
 */
public class CirnoModel extends GeoModel<CirnoItem> {

    private String skin = CirnoItem.DEFAULT_SKIN;

    public void setSkin(String skin) {
        this.skin = skin;
    }

    @Override
    public ResourceLocation getModelResource(CirnoItem animatable) {
        return new ResourceLocation(Cirno.MOD_ID, "geo/" + skin + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CirnoItem animatable) {
        return new ResourceLocation(Cirno.MOD_ID, "textures/item/" + skin + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(CirnoItem animatable) {
        return new ResourceLocation(Cirno.MOD_ID, "animations/" + skin + ".animation.json");
    }
}

