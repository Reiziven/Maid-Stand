package net.zhaiji.cirno.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.brain.task.MaidClearSleepTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.server.level.ServerLevel;
import net.zhaiji.cirno.compat.CuriosCompat;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.init.InitItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(MaidClearSleepTask.class)
public class MaidClearSleepTaskMixin {
    /**
     * 如果睡眠被打断似乎也会触发？那这件事可万万不可告诉玩家
     */
    @Inject(
            method = "start(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;J)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;stopSleeping()V"
            )
    )
    public void cirno$start(ServerLevel worldIn, EntityMaid entityIn, long gameTimeIn, CallbackInfo ci) {
        if (!CirnoCommonConfig.wakeUpCanResetCooldown) return;
        if (CuriosCompat.hasCirno(entityIn)) {
            entityIn.getCooldowns().removeCooldown(InitItem.CIRNO.get());
        }
    }
}
