package net.zhaiji.cirno.compat;

import com.github.tartaricacid.touhoulittlemaid.init.InitDataComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * Single access point for the maid data Touhou Little Maid stores on smart slabs / photos / films.
 *
 * <p>VERIFIED against TLM 1.5.3 (neoforge, mc 1.21.1) by decompiling ItemFilm/ItemPhoto/ItemSmartSlab:
 * on 1.20.1 the data was the "MaidInfo" compound inside the stack's NBT; on 1.21.1 TLM registers its own
 * data component {@code InitDataComponent.MAID_INFO} (type {@link CustomData}) and the maid's saved
 * compound IS that component's tag (no nested "MaidInfo" key any more). The inner keys this mod reads
 * ("IsCirnoCompanion", "OwnerPlayerUUID", "UUID") live in that compound.
 *
 * <p>TLM classes are only touched inside {@link Tlm}, which the JVM loads lazily, and only when TLM is
 * installed, so the mod still loads without TLM.
 */
public final class TlmStorageData {
    private TlmStorageData() {
    }

    /** Read-only live view of the stored maid compound, or null if the stack has none / TLM absent. */
    @Nullable
    public static CompoundTag peekMaidInfo(ItemStack stack) {
        if (stack.isEmpty() || !CompatManager.isTLMLoad()) return null;
        return Tlm.peek(stack);
    }

    /** Copy of the stored maid compound (empty compound if absent) - safe to mutate. */
    public static CompoundTag copyMaidInfo(ItemStack stack) {
        CompoundTag info = peekMaidInfo(stack);
        return info == null ? new CompoundTag() : info.copy();
    }

    private static final class Tlm {
        @Nullable
        static CompoundTag peek(ItemStack stack) {
            CustomData data = stack.get(InitDataComponent.MAID_INFO.get());
            return data == null ? null : data.getUnsafe();
        }
    }
}
