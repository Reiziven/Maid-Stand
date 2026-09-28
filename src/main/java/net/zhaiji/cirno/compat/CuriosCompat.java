package net.zhaiji.cirno.compat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.zhaiji.cirno.compat.curios.CuriosHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Safe facade over the OPTIONAL Curios dependency.
 * <p>
 * Nothing in this class imports Curios. Every call checks {@link #isLoaded()} first and
 * only then touches {@link CuriosHooks}, so the JVM never tries to resolve a Curios
 * class when the mod is absent. Without Curios the "Curious" (equipped item) Cirno
 * simply doesn't exist, while command Cirnos (/cirno, Oathpin) keep working.
 */
public final class CuriosCompat {
    private static final boolean LOADED = ModList.get().isLoaded("curios");

    private CuriosCompat() {}

    public static boolean isLoaded() {
        return LOADED;
    }

    public static void init(IEventBus modBus, IEventBus gameBus) {
        if (LOADED) CuriosHooks.init(modBus, gameBus);
    }

    /** Client only. Registers the floating-head Curios renderer. */
    public static void registerRenderer() {
        if (LOADED) CuriosHooks.registerRenderer();
    }

    public static boolean hasCirno(LivingEntity entity) {
        return LOADED && CuriosHooks.hasCirno(entity);
    }

    @Nullable
    public static ItemStack getCirnoStack(LivingEntity entity) {
        return LOADED ? CuriosHooks.getCirnoStack(entity) : null;
    }

    public static boolean isCirnoVisible(LivingEntity entity) {
        return LOADED && CuriosHooks.isCirnoVisible(entity);
    }
}
