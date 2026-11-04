package net.zhaiji.cirno.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * NeoForge 1.21 capability lookups (replaces {@code getCapability(ForgeCapabilities.ITEM_HANDLER)}
 * + LazyOptional). Capabilities are now queried from the level / entity and return null if absent.
 */
public final class ItemHandlerAccess {
    private ItemHandlerAccess() {
    }

    /** Item handler of a block entity with no side context (null side = internal/"any" view). */
    @Nullable
    public static IItemHandler ofBlockEntity(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), be.getBlockState(), be, null);
    }

    /** Item handler of an entity (e.g. horse/donkey/mule inventories, modded mobs). */
    @Nullable
    public static IItemHandler ofEntity(Entity entity) {
        return entity.getCapability(Capabilities.ItemHandler.ENTITY);
    }
}
