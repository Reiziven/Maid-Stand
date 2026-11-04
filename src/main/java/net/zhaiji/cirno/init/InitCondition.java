package net.zhaiji.cirno.init;

import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.zhaiji.cirno.Cirno;
import net.zhaiji.cirno.condition.CirnoVariantRecipesCraftableCondition;
import net.zhaiji.cirno.condition.OathpinCraftableCondition;

import java.util.function.Supplier;

/** Registers the recipe-condition codecs (replaces CraftingHelper.register(serializer)). */
public class InitCondition {
    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Cirno.MOD_ID);

    public static final Supplier<MapCodec<OathpinCraftableCondition>> OATHPIN_CRAFTABLE =
            CONDITION_CODECS.register("oathpin_craftable", () -> OathpinCraftableCondition.CODEC);
    public static final Supplier<MapCodec<CirnoVariantRecipesCraftableCondition>> CIRNO_VARIANT_RECIPES_CRAFTABLE =
            CONDITION_CODECS.register("cirno_variant_recipes_craftable", () -> CirnoVariantRecipesCraftableCondition.CODEC);
}
