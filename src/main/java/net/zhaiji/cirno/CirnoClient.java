package net.zhaiji.cirno;

import net.minecraftforge.eventbus.api.IEventBus;
import net.zhaiji.cirno.client.event.ClientEventManager;

public class CirnoClient {
    public static void init(IEventBus modBus,IEventBus gameBus) {
        ClientEventManager.init(modBus, gameBus);
    }
}
