package net.zhaiji.cirno.condition;

import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.zhaiji.cirno.config.CirnoCommonConfig;

/**
 * NeoForge recipe condition making the single Oathpin altar recipe configurable.
 * Json type: "cirno:oathpin_craftable" under the "neoforge:conditions" key.
 * Codec is registered in {@link net.zhaiji.cirno.init.InitCondition}.
 */
public record OathpinCraftableCondition() implements ICondition {
    public static final OathpinCraftableCondition INSTANCE = new OathpinCraftableCondition();
    public static final MapCodec<OathpinCraftableCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(IContext context) {
        return CirnoCommonConfig.OATHPIN_CRAFTABLE.get();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
