package net.zhaiji.cirno.mixin;

import net.zhaiji.cirno.entity.CirnoEntity;
import net.zhaiji.cirno.entity.CirnoFlightState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Exposes Cirno's existing flight state to OpenYSM's standard ctrl.fly query.
 * This does not select, start, stop, or dirty an animation; YSM's own model
 * controller remains responsible for resolving FLY versus FALL and other poses.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.client.animation.molang.CtrlBinding", remap = false)
public abstract class YsmCtrlFlyMixin {
    @Inject(
            method = "isFlying(Lcom/elfmcys/yesstevemodel/geckolib3/core/molang/context/IContext;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void cirno$cirnoCtrlFly(Object context, CallbackInfoReturnable<Boolean> callback) {
        Object entity = getContextEntity(context);
        if (entity instanceof CirnoEntity cirno && cirno.getVehicle() == null) {
            boolean flying = cirno.isMountFlying() || cirno.getFlightState() == CirnoFlightState.FLY;
            if (flying) {
                callback.setReturnValue(true);
            }
        }
    }

    private static Object getContextEntity(Object context) {
        if (context == null) {
            return null;
        }
        try {
            Method method = context.getClass().getMethod("entity");
            return method.invoke(context);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
