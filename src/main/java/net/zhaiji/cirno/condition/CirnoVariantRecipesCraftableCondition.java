package net.zhaiji.cirno.condition;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.zhaiji.cirno.Cirno;
import net.zhaiji.cirno.config.CirnoCommonConfig;

/** Forge recipe condition used to make all Cirno variant altar recipes configurable. */
public class CirnoVariantRecipesCraftableCondition implements ICondition {
    public static final ResourceLocation ID = new ResourceLocation(Cirno.MOD_ID, "cirno_variant_recipes_craftable");

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return CirnoCommonConfig.CIRNO_VARIANT_RECIPES_CRAFTABLE.get();
    }

    public static class Serializer implements IConditionSerializer<CirnoVariantRecipesCraftableCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public ResourceLocation getID() {
            return ID;
        }

        @Override
        public CirnoVariantRecipesCraftableCondition read(JsonObject json) {
            return new CirnoVariantRecipesCraftableCondition();
        }

        @Override
        public void write(JsonObject json, CirnoVariantRecipesCraftableCondition value) {
            // No recipe-specific data is needed; the config is the single switch.
        }
    }
}
