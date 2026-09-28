package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.cirno.entity.CirnoEntity;

import java.util.function.Supplier;

/**
 * Sent client→server when the player right-clicks while holding a saddle and riding a
 * CirnoEntity. Exists because raycasting onto your own mount to "click" it is unreliable
 * (the camera usually isn't pointed at her hitbox while she's carrying you) — the client
 * instead detects "right click + saddle in hand while riding" directly from input state
 * (see ClientEventHandler) and just tells the server to drop them off.
 */
public class CirnoMountDismountPacket {

    public void encode(FriendlyByteBuf buf) {}

    public static CirnoMountDismountPacket decode(FriendlyByteBuf buf) {
        return new CirnoMountDismountPacket();
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            if (player.getVehicle() instanceof CirnoEntity cirno && cirno.hasPassenger(player)) {
                player.stopRiding();
            }
        });
        ctx.setPacketHandled(true);
    }
}
