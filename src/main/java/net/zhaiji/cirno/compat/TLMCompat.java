package net.zhaiji.cirno.compat;

import com.github.tartaricacid.touhoulittlemaid.api.event.MaidDeathEvent;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraftforge.eventbus.api.IEventBus;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.init.InitItem;

public class TLMCompat {
    public static boolean canRender(LivingEntity entity) {
        return entity instanceof EntityMaid;
    }

    public static void init(IEventBus modBus, IEventBus gameBus) {
        TLMCompat.modBusListener(modBus);
        TLMCompat.gameBusListener(gameBus);
    }

    public static void modBusListener(IEventBus modBus) {
    }

    public static void gameBusListener(IEventBus gameBus) {
        gameBus.addListener(TLMCompat::handlerMaidDeathEvent);
    }

    public static void handlerMaidDeathEvent(MaidDeathEvent event) {
        if (!CirnoCommonConfig.totemEffectActive) return;
        EntityMaid maid = event.getMaid();
        Item item = InitItem.CIRNO.get();
        ItemCooldowns cooldowns = maid.getCooldowns();
        if (CuriosCompat.hasCirno(maid) && !cooldowns.isOnCooldown(item)) {
            if (CirnoCommonConfig.usePercentageHealthRestoration) {
                float maxHealth = maid.getMaxHealth();
                float restoreAmount = maxHealth * ((float) CirnoCommonConfig.percentageHealthRestoration / 100.0f);
                maid.setHealth(restoreAmount);
            } else {
                maid.setHealth(CirnoCommonConfig.healthRestorationFromTotem);
            }
            cooldowns.addCooldown(item, CirnoCommonConfig.totemCooldown);
            maid.level().broadcastEntityEvent(maid, (byte) 35);
            event.setCanceled(true);
        }
    }
}
