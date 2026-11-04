package net.zhaiji.cirno.init;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zhaiji.cirno.Cirno;

/**
 * Data components replacing the 1.20.1 ItemStack NBT tags.
 *
 * <p>TODO(neoforge-port): phase 3 - CirnoItem.getSkin / withSkin must read/write {@link #SKIN}
 * instead of stack.getTag(). Any other custom stack NBT found in CirnoStateAccess /
 * DupeCleanupHandler should get its own component here (or use DataComponents.CUSTOM_DATA
 * as a drop-in for opaque CompoundTag blobs).
 */
public class InitDataComponent {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Cirno.MOD_ID);

    /** Visual skin id of a Cirno item stack ("cirno", "solyn", "spirit", "tatsumaki"). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> SKIN =
            DATA_COMPONENTS.registerComponentType("skin", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));
}
