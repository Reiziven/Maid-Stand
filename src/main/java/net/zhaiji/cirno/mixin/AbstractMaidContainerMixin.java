package net.zhaiji.cirno.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.inventory.container.AbstractMaidContainer;
import net.minecraft.world.entity.player.Player;
import net.zhaiji.cirno.entity.CirnoEntity;
import net.zhaiji.cirno.event.CirnoStateAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to bypass distance checks for CirnoEntity GUI containers.
 * Allows opening Cirno's inventory from any distance and across dimensions.
 */
@Pseudo
@Mixin(AbstractMaidContainer.class)
public class AbstractMaidContainerMixin {
    
    @Shadow
    @Final
    protected EntityMaid maid;
    
    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true, remap = false)
    private void cirno$bypassDistanceCheckForCirno(Player playerIn, CallbackInfoReturnable<Boolean> cir) {
        // If this is a CirnoEntity, bypass distance checks entirely
        if (this.maid instanceof CirnoEntity) {
            if (this.maid == null) {
                cir.setReturnValue(false);
                return;
            }
            if (!maid.isOwnedBy(playerIn)) {
                cir.setReturnValue(false);
                return;
            }
            if (!maid.isAlive() || maid.isSleeping()) {
                cir.setReturnValue(false);
                return;
            }
            // Reject a stale duplicate: a chunk-unload "ghost" (or any other leftover
            // copy) can still answer isAlive()/isOwnedBy() truthfully even after a
            // replacement has taken over as the real tracked Cirno. Without this check,
            // bypassing the distance check below kept that ghost's inventory screen
            // open and editable forever, which is what was letting its items get
            // duplicated against the replacement's. The tracker (Curio slot / command
            // slot UUID) is the single source of truth for "who is Cirno right now".
            if (!CirnoStateAccess.isTrackedCompanionUUID(playerIn, maid.getUUID())) {
                cir.setReturnValue(false);
                return;
            }
            // Skip distance check for CirnoEntity - return true if all other checks pass
            cir.setReturnValue(true);
        }
    }
}
