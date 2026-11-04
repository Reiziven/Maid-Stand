package net.zhaiji.cirno.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * 1.21 replacement for the removed {@code ItemStack#getTag / getOrCreateTag}.
 *
 * <p>Arbitrary per-stack NBT now lives in {@link DataComponents#CUSTOM_DATA}. These helpers keep
 * the old "null if no tag" semantics so call-sites barely change. Cirno's own typed stack data
 * (e.g. the skin) uses a real component instead: see
 * {@link net.zhaiji.cirno.init.InitDataComponent}.
 */
public final class StackData {
    private StackData() {
    }

    /** Defensive copy of the stack's custom-data tag, or null if the stack has none. */
    @Nullable
    public static CompoundTag get(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    /**
     * Live, READ-ONLY view (no copy) for hot paths such as inventory scans. Never mutate the
     * result - use {@link #update}.
     */
    @Nullable
    public static CompoundTag peek(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.getUnsafe();
    }

    /** Mutates (creating if needed) the stack's custom-data tag. */
    public static void update(ItemStack stack, Consumer<CompoundTag> updater) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, updater);
    }

    /** Removes a key; no-op (and does not create the component) if the stack has no custom data. */
    public static void remove(ItemStack stack, String key) {
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            update(stack, tag -> tag.remove(key));
        }
    }
}
