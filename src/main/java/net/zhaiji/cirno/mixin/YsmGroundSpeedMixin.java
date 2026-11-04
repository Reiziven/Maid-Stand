package net.zhaiji.cirno.mixin;

import net.zhaiji.cirno.entity.CirnoEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps owner-follow movement animation-only. Cirno is positioned with setPos(), so feeding the
 * follow interpolation into Entity.deltaMovement would make Minecraft physically move/collide
 * her a second time. YSM's q.ground_speed is therefore supplied from Cirno's recorded follow
 * displacement instead, while every other entity keeps YSM's normal deltaMovement calculation.
 */
// TODO(neoforge-port-ysm): target class/method names are from YSM 1.20.1 (obfuscated). Re-resolve against the YSM 1.21.1 NeoForge jar; @Pseudo + defaultRequire 0 make a miss harmless (feature just inactive).
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin.QueryBinding", remap = false)
public abstract class YsmGroundSpeedMixin {
    @Inject(
            method = "getGroundSpeed(Lnet/minecraft/world/entity/Entity;)F",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void cirno$cirnoGroundSpeed(net.minecraft.world.entity.Entity entity,
                                                     CallbackInfoReturnable<Float> callback) {
        if (entity instanceof CirnoEntity cirno && cirno.getVehicle() == null) {
            callback.setReturnValue((float) cirno.getYsmFollowGroundSpeed());
        }
    }
}
