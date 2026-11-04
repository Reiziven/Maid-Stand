package net.zhaiji.cirno.compat.curios;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** Curios behaviour for the Cirno item; only ever instantiated when Curios is loaded. */
public class CirnoCurioItem implements ICurioItem {

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (slotContext.entity() instanceof Player player
                && player.tickCount % CirnoCommonConfig.curiosCooldown == 0) {
            FoodData foodData = player.getFoodData();
            int foodLevel = foodData.getFoodLevel();
            if (foodLevel < CirnoCommonConfig.foodMaxRestoration) {
                foodData.setFoodLevel(Math.min(
                        foodLevel + CirnoCommonConfig.foodRestorationFromCurios,
                        CirnoCommonConfig.foodMaxRestoration));
            }
        }
    }
}
