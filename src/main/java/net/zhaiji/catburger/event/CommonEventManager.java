package net.zhaiji.catburger.event;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.zhaiji.catburger.command.CirnoCommand;
import net.zhaiji.catburger.compat.CompatManager;
import net.zhaiji.catburger.compat.TLMCompat;
import net.zhaiji.catburger.config.CatBurgerCommonConfig;
import net.zhaiji.catburger.datagen.DataGenHandler;

public class CommonEventManager {
    public static void init(IEventBus modBus, IEventBus gameBus) {
        CommonEventManager.modBusListener(modBus);
        CommonEventManager.gameBusListener(gameBus);
        if (CompatManager.isTLMLoad()) {
            TLMCompat.init(modBus, gameBus);
        }
    }

    public static void modBusListener(IEventBus modBus) {
        modBus.addListener(CatBurgerCommonConfig::handlerModConfigEvent);
        modBus.addListener(DataGenHandler::handlerGatherDataEvent);
    }

    public static void gameBusListener(IEventBus gameBus) {
        gameBus.addListener(EventPriority.HIGHEST, CommonEventHandler::handlerLivingDeathEvent);
        gameBus.addListener(EventPriority.HIGHEST, CommonEventHandler::handlerLivingDamageEvent);
        gameBus.addListener(EventPriority.LOWEST, CommonEventHandler::handlerLivingHurtEvent);
        gameBus.addListener(EventPriority.HIGHEST, CommonEventHandler::handlerProjectileImpact);
        gameBus.addListener(CommonEventHandler::handlerPlayerWakeUpEvent);
        gameBus.addListener(CommonEventHandler::handlerCurioChangeEvent);
        gameBus.addListener(CommonEventHandler::handlerPlayerLoggedIn);
        gameBus.addListener(CommonEventHandler::handlerPlayerLoggedOut);
        gameBus.addListener(CommonEventHandler::handlerPlayerTick);
        gameBus.addListener(CommonEventHandler::handlerPlayerChangedDimension);
        gameBus.addListener(EventPriority.HIGHEST, CommonEventHandler::handlerPlayerClone);
        gameBus.addListener(EventPriority.HIGH, CommonEventHandler::handlerRightClickBlockForCirnoStorage);
        gameBus.addListener(CommonEventHandler::handlerContainerOpen);
        gameBus.addListener(CommonEventManager::handlerRegisterCommands);
    }

    public static void handlerRegisterCommands(RegisterCommandsEvent event) {
        CirnoCommand.register(event.getDispatcher());
    }
}
