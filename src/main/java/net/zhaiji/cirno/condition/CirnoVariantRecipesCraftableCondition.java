package net.zhaiji.cirno.condition;

import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.zhaiji.cirno.config.CirnoCommonConfig;

/**
 * NeoForge recipe condition making all Cirno variant altar recipes configurable.
 * Json type: "cirno:cirno_variant_recipes_craftable" under the "neoforge:conditions" key.
 * Codec is registered in {@link net.zhaiji.cirno.init.InitCondition}.
 */
public record CirnoVariantRecipesCraftableCondition() implements ICondition {
    public static final CirnoVariantRecipesCraftableCondition INSTANCE = new CirnoVariantRecipesCraftableCondition();
    public static final MapCodec<CirnoVariantRecipesCraftableCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(IContext context) {
        return CirnoCommonConfig.CIRNO_VARIANT_RECIPES_CRAFTABLE.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
