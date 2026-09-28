package net.zhaiji.cirno.init;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.zhaiji.cirno.Cirno;
import net.zhaiji.cirno.item.CirnoItem;

import java.util.function.Supplier;

public class InitCreativeModeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Cirno.MOD_ID);

    public static final Supplier<CreativeModeTab> CIRNO_TAB = CREATIVE_MODE_TAB.register(
            "cirno_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(InitItem.CIRNO.get()))
                    .title(Component.translatable("creativetab.cirno.cirno_tab"))
                    .displayItems(((itemDisplayParameters, output) -> {
                        output.accept(InitItem.OATHPIN.get());
                        // One entry per skin — all the same "cirno" item, just with the
                        // Skin NBT tag set, so every entry behaves identically (curio
                        // tick, companion tracking, recipes) and only looks different.
                        for (String skin : CirnoItem.SKINS) {
                            output.accept(CirnoItem.withSkin(InitItem.CIRNO.get(), skin));
                        }
                    }))
                    .build()
    );
}

