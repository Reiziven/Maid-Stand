package net.zhaiji.cirno.mixin;

import net.minecraft.world.entity.player.Player;
import net.zhaiji.cirno.entity.CirnoEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Extends YSM's princess-carry predicate to Cirno's normal Minecraft riding
 * relationship. The animation controller is provided by YSM itself; no
 * separate carry-system mod is required.
 */
@Pseudo
@Mixin(targets = "com.elfmcys.yesstevemodel.o0oO0OO0OoO0o00OOo0oOOo0", remap = false)
public abstract class YsmPrincessCarryMixin {
    @Inject(
            method = "Oo0Oo0o00O00Oo0OOoOOoooo(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void cirno$cirnoPrincessCarry(
            Player player, CallbackInfoReturnable<Boolean> callback) {
        if (player.getVehicle() instanceof CirnoEntity) {
            callback.setReturnValue(true);
        }
    }
}
