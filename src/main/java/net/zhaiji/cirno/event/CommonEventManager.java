package net.zhaiji.cirno.event;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.zhaiji.cirno.command.CirnoCommand;
import net.zhaiji.cirno.compat.CompatManager;
import net.zhaiji.cirno.compat.TLMCompat;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.datagen.DataGenHandler;

public class CommonEventManager {
    public static void init(IEventBus modBus, IEventBus gameBus) {
        CommonEventManager.modBusListener(modBus);
        CommonEventManager.gameBusListener(gameBus);
        // Curios is optional: hooks are only registered when it is installed.
        net.zhaiji.cirno.compat.CuriosCompat.init(modBus, gameBus);
        if (CompatManager.isTLMLoad()) {
            TLMCompat.init(modBus, gameBus);
        }
    }

    public static void modBusListener(IEventBus modBus) {
        modBus.addListener(CirnoCommonConfig::handlerModConfigEvent);
        modBus.addListener(DataGenHandler::handlerGatherDataEvent);
    }

    public static void gameBusListener(IEventBus gameBus) {
        // Combat: totem-of-undying-style revival, kill-credit passthrough.
        gameBus.addListener(EventPriority.HIGHEST, CombatEventHandler::handlerLivingDeathEvent);
        gameBus.addListener(EventPriority.LOWEST, CombatEventHandler::handlerLivingHurtEvent);

        // Companion lifecycle: curio equip/unequip, TLM maid/item transform, join-level rebind.
        gameBus.addListener(EventPriority.HIGHEST, CompanionLifecycleHandler::handlerMaidAndItemTransformToMaid);
        gameBus.addListener(EventPriority.HIGHEST, CompanionLifecycleHandler::handlerEntityJoinLevel);

        // Telekinesis: shield auto-trigger and physical projectile block.
        gameBus.addListener(EventPriority.HIGHEST, TelekinesisHandler::handlerLivingDamageEvent);
        gameBus.addListener(EventPriority.HIGHEST, TelekinesisHandler::handlerProjectileImpact);

        // Player lifecycle: wake up, login/logout, dimension change, clone (death/respawn carry-over).
        gameBus.addListener(PlayerLifecycleHandler::handlerPlayerWakeUpEvent);
        gameBus.addListener(PlayerLifecycleHandler::handlerPlayerLoggedIn);
        gameBus.addListener(PlayerLifecycleHandler::handlerPlayerLoggedOut);
        gameBus.addListener(PlayerLifecycleHandler::handlerPlayerChangedDimension);
        gameBus.addListener(EventPriority.HIGHEST, PlayerLifecycleHandler::handlerPlayerClone);

        // Per-tick dispatcher: delegates to Telekinesis / Companion / CommandCirno / DupeCleanup.
        gameBus.addListener(PlayerTickHandler::handlerPlayerTick);

        // External storage (smart slab / photo / film) container interactions.
        // (Right-clicking a slab/photo/film on a block is left to TLM; the Shrine/TLM revive
        // is converted to Cirno by CompanionLifecycleHandler instead.)
        gameBus.addListener(CirnoStorageHandler::handlerContainerOpen);

        gameBus.addListener(CommonEventManager::handlerRegisterCommands);
    }

    public static void handlerRegisterCommands(RegisterCommandsEvent event) {
        CirnoCommand.register(event.getDispatcher());
    }
}
