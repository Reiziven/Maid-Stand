package net.zhaiji.cirno;

import net.neoforged.bus.api.IEventBus;
import net.zhaiji.cirno.client.event.ClientEventManager;

public class CirnoClient {
    public static void init(IEventBus modBus,IEventBus gameBus) {
        ClientEventManager.init(modBus, gameBus);
    }
}
