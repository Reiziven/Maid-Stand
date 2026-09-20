package net.zhaiji.catburger.client.event;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.zhaiji.catburger.client.CatBurgerKeybinds;
import net.zhaiji.catburger.client.animation.CirnoFlightAnimations;
import net.zhaiji.catburger.client.compat.ClientCompatHandler;
import net.zhaiji.catburger.compat.CompatManager;
import net.zhaiji.catburger.config.CatBurgerClientConfig;

public class ClientEventManager {
    public static void init(IEventBus modBus, IEventBus gameBus) {
        ClientEventManager.modBusListener(modBus);
        ClientEventManager.gameBusListener(gameBus);
    }

    public static void modBusListener(IEventBus modBus) {
        if (!CompatManager.isYSMLoad()) {
            modBus.addListener(ClientEventHandler::handlerFMLClientSetupEvent);
        }
        // CirnoFlightAnimations is a no-op now (YSM-only path lives in CirnoEntity).
        // Still register so nothing else breaks if someone calls it.
        if (CompatManager.isYSMLoad() || CompatManager.isTLMLoad()) {
            modBus.addListener(CirnoFlightAnimations::onClientSetup);
        }
        modBus.addListener(CatBurgerClientConfig::handlerModConfigEvent);
        modBus.addListener(ClientEventHandler::handlerEntityRenderers);
        modBus.addListener(CatBurgerKeybinds::register);
    }

    public static void gameBusListener(IEventBus gameBus) {
        if (CompatManager.isYSMLoad() || CompatManager.isTLMLoad()) {
            gameBus.addListener(ClientCompatHandler::handlerRenderLivingEvent$Post);
        }
        gameBus.addListener(ClientEventHandler::handlerClientTick);
        gameBus.addListener(EventPriority.HIGHEST, ClientEventHandler::handlerEntityInteractClient);
    }
}
