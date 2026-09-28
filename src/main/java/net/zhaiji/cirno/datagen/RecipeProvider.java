package net.zhaiji.cirno.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

/**
 * Recipe datagen entry point.
 *
 * Cirno variants use Touhou Little Maid's altar recipe format and are kept as
 * static data resources under data/touhou_little_maid/recipes/altar, so they
 * do not get replaced by vanilla crafting recipes during datagen.
 */
public class RecipeProvider extends net.minecraft.data.recipes.RecipeProvider {
    public RecipeProvider(PackOutput packOutput) {
        super(packOutput);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> consumer) {
        // Intentionally empty.
        // Cirno variant recipes are TLM altar_crafting recipes supplied as
        // static JSON resources rather than minecraft:crafting_shaped recipes.
    }
}
