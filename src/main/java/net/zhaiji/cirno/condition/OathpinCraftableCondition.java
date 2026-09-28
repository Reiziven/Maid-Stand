package net.zhaiji.cirno.condition;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;
import net.zhaiji.cirno.Cirno;
import net.zhaiji.cirno.config.CirnoCommonConfig;

/** Forge recipe condition used to make the single Oathpin altar recipe configurable. */
public class OathpinCraftableCondition implements ICondition {
    public static final ResourceLocation ID = new ResourceLocation(Cirno.MOD_ID, "oathpin_craftable");

    @Override
    public ResourceLocation getID() {
        return ID;
    }

    @Override
    public boolean test(IContext context) {
        return CirnoCommonConfig.OATHPIN_CRAFTABLE.get();
    }

    public static class Serializer implements IConditionSerializer<OathpinCraftableCondition> {
        public static final Serializer INSTANCE = new Serializer();

        @Override
        public ResourceLocation getID() {
            return ID;
        }

        @Override
        public OathpinCraftableCondition read(JsonObject json) {
            return new OathpinCraftableCondition();
        }

        @Override
        public void write(JsonObject json, OathpinCraftableCondition value) {
            // No recipe-specific data is needed; the config is the single switch.
        }
    }
}
