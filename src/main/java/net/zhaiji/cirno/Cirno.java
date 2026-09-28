package net.zhaiji.cirno;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.zhaiji.cirno.config.CirnoClientConfig;
import net.zhaiji.cirno.condition.CirnoVariantRecipesCraftableCondition;
import net.zhaiji.cirno.condition.OathpinCraftableCondition;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.entity.CirnoEntity;
import net.zhaiji.cirno.event.CommonEventManager;
import net.zhaiji.cirno.init.InitCreativeModeTab;
import net.zhaiji.cirno.init.InitEntity;
import net.zhaiji.cirno.init.InitItem;
import net.zhaiji.cirno.network.PacketManager;
import net.zhaiji.cirno.util.CirnoChunkLoadingManager;

@Mod(Cirno.MOD_ID)
public class Cirno {
    public static final String MOD_ID = "cirno";

    public Cirno() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        IEventBus gameBus = MinecraftForge.EVENT_BUS;

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CirnoCommonConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, CirnoClientConfig.SPEC);

        InitItem.ITEMS.register(modBus);
        InitCreativeModeTab.CREATIVE_MODE_TAB.register(modBus);
        InitEntity.ENTITIES.register(modBus);
        modBus.addListener(Cirno::registerEntityAttributes);
        modBus.addListener(Cirno::registerCraftingConditions);
        modBus.addListener((FMLCommonSetupEvent event) ->
                event.enqueueWork(CirnoChunkLoadingManager::registerCallback));

        PacketManager.registry();
        CommonEventManager.init(modBus, gameBus);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            CirnoClient.init(modBus, gameBus);
        });
    }

    private static void registerEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(InitEntity.CIRNO.get(), CirnoEntity.createAttributes().build());
    }


    private static void registerCraftingConditions(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CraftingHelper.register(OathpinCraftableCondition.Serializer.INSTANCE);
            CraftingHelper.register(CirnoVariantRecipesCraftableCondition.Serializer.INSTANCE);
        });
    }
}
