package net.zhaiji.cirno.client.event;

import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.zhaiji.cirno.client.CirnoKeybinds;
import net.zhaiji.cirno.client.animation.CirnoFlightAnimations;
import net.zhaiji.cirno.client.compat.ClientCompatHandler;
import net.zhaiji.cirno.compat.CompatManager;
import net.zhaiji.cirno.config.CirnoClientConfig;

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
        modBus.addListener(CirnoClientConfig::handlerModConfigEvent);
        modBus.addListener(ClientEventHandler::handlerEntityRenderers);
        modBus.addListener(CirnoKeybinds::register);
    }

    public static void gameBusListener(IEventBus gameBus) {
        if (CompatManager.isYSMLoad() || CompatManager.isTLMLoad()) {
            gameBus.addListener(ClientCompatHandler::handlerRenderLivingEvent$Post);
        }
        gameBus.addListener(ClientCompatHandler::handlerRenderPlayerCarry$Pre);
        gameBus.addListener(ClientCompatHandler::handlerRenderPlayerCarry$Post);
        gameBus.addListener(ClientEventHandler::handlerClientTick);
        gameBus.addListener(EventPriority.HIGHEST, ClientEventHandler::handlerEntityInteractClient);
    }
}
