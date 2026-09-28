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
 * Exposes Cirno's existing flight state to YSM 2.6.5's standard ctrl.fly query.
 * The actual animation controller remains untouched.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.OoOOOOOo0oo0000oooOOOoOO", remap = false)
public abstract class Ysm265CtrlFlyMixin {
    @Inject(
            method = "o0OOooo0o0OO00OoOOOo0o0O(Lcom/elfmcys/yesstevemodel/oo0oOO0000o0Ooooo0OoOo0O;)Z",
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
