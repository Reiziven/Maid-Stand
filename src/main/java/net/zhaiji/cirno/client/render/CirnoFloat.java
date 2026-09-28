package net.zhaiji.cirno.client.render;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.zhaiji.cirno.config.CirnoClientConfig;

/** Curios-free helper (CirnoRenderer implements a Curios interface, so it must not be loaded without Curios). */
public final class CirnoFloat {
    private CirnoFloat() {}

    public static double getFloatSpeed(LivingEntity entity, float partialTicks) {
        return CirnoClientConfig.floatDistance / 2
                * Math.sin((entity.tickCount + partialTicks) * Mth.HALF_PI / CirnoClientConfig.time);
    }
}
