package net.zhaiji.cirno.compat.curios;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.zhaiji.cirno.event.CompanionLifecycleHandler;
import net.zhaiji.cirno.init.InitItem;
import net.zhaiji.cirno.client.render.CirnoRenderer;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

import java.util.Optional;

/**
 * The ONLY place (together with {@link CirnoCurioItem}) that touches the Curios API.
 * <p>
 * This class must never be referenced directly from code that runs without Curios;
 * always go through {@link net.zhaiji.cirno.compat.CuriosCompat}, which checks that
 * Curios is loaded before this class is ever resolved by the JVM.
 */
public final class CuriosHooks {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private CuriosHooks() {}

    public static void init(IEventBus modBus, IEventBus gameBus) {
        modBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(CuriosHooks::registerCurio));
        gameBus.addListener(CuriosHooks::handlerCurioChangeEvent);
    }

    /**
     * Runs as a deferred common-setup task. Wrapped so a failure is reported with context
     * (which item, the real cause) instead of an anonymous "error in a deferred task".
     */
    private static void registerCurio() {
        try {
            CuriosApi.registerCurio(InitItem.CIRNO.get(), new CirnoCurioItem());
        } catch (Exception | LinkageError e) {
            LOGGER.error("Cirno: failed to register the Cirno item with Curios. It will not be "
                    + "equippable in a Curios slot until this is fixed. Please report with the full log.", e);
        }
    }

    private static void handlerCurioChangeEvent(CurioChangeEvent event) {
        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            CompanionLifecycleHandler.handleCurioChange(player, event.getFrom(), event.getTo());
        }
    }

    public static void registerRenderer() {
        CuriosRendererRegistry.register(InitItem.CIRNO.get(), CirnoRenderer::new);
    }

    private static Optional<SlotResult> find(LivingEntity entity) {
        return CuriosApi.getCuriosInventory(entity)
                .map(inv -> inv.findFirstCurio(InitItem.CIRNO.get()))
                .orElse(Optional.empty());
    }

    public static boolean hasCirno(LivingEntity entity) {
        return find(entity).isPresent();
    }

    public static ItemStack getCirnoStack(LivingEntity entity) {
        return find(entity).map(SlotResult::stack).orElse(null);
    }

    /** True if Cirno is equipped AND her slot is set visible. */
    public static boolean isCirnoVisible(LivingEntity entity) {
        return find(entity).map(r -> r.slotContext().visible()).orElse(false);
    }
}
