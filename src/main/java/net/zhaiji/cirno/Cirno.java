package net.zhaiji.cirno;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.zhaiji.cirno.config.CirnoClientConfig;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.entity.CirnoEntity;
import net.zhaiji.cirno.event.CommonEventManager;
import net.zhaiji.cirno.init.InitCondition;
import net.zhaiji.cirno.init.InitCreativeModeTab;
import net.zhaiji.cirno.init.InitDataComponent;
import net.zhaiji.cirno.init.InitEntity;
import net.zhaiji.cirno.init.InitItem;
import net.zhaiji.cirno.network.PacketManager;
import net.zhaiji.cirno.util.CirnoChunkLoadingManager;

/**
 * NeoForge 1.21.1 entry point. NeoForge injects the mod event bus and the mod container
 * straight into the constructor (no more FMLJavaModLoadingContext / ModLoadingContext /
 * DistExecutor).
 */
@Mod(Cirno.MOD_ID)
public class Cirno {
    public static final String MOD_ID = "cirno";

    public Cirno(IEventBus modBus, ModContainer container) {
        IEventBus gameBus = NeoForge.EVENT_BUS;

        container.registerConfig(ModConfig.Type.COMMON, CirnoCommonConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, CirnoClientConfig.SPEC);

        InitItem.ITEMS.register(modBus);
        InitCreativeModeTab.CREATIVE_MODE_TAB.register(modBus);
        InitEntity.ENTITIES.register(modBus);
        InitDataComponent.DATA_COMPONENTS.register(modBus);
        InitCondition.CONDITION_CODECS.register(modBus);

        modBus.addListener(Cirno::registerEntityAttributes);
        // Networking: payload registration (see PacketManager, ported in phase 2).
        modBus.addListener(PacketManager::registerPayloads);
        // Chunk-load tickets (replaces ForgeChunkManager.setForcedChunkLoadingCallback).
        modBus.addListener(CirnoChunkLoadingManager::registerTicketController);

        CommonEventManager.init(modBus, gameBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            // Kept in a separate class so server-side never loads client classes.
            CirnoClient.init(modBus, gameBus);
        }
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(InitEntity.CIRNO.get(), CirnoEntity.createAttributes().build());
    }
}
