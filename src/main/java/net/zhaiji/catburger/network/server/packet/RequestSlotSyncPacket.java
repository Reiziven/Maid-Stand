package net.zhaiji.catburger.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.event.CommonEventHandler;
import net.zhaiji.catburger.network.PacketManager;
import net.zhaiji.catburger.network.client.packet.SlotSyncPacket;

import java.util.function.Supplier;

/**
 * Sent client→server when the Quick-Select screen opens.
 * The server replies with a {@link SlotSyncPacket} containing all slot states.
 */
public class RequestSlotSyncPacket {

    public void encode(FriendlyByteBuf buf) {}

    public static RequestSlotSyncPacket decode(FriendlyByteBuf buf) {
        return new RequestSlotSyncPacket();
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            SlotSyncPacket sync = CommonEventHandler.buildSlotSyncPacket(player);
            PacketManager.sendToClient(sync, player);
        });
        ctx.setPacketHandled(true);
    }
}
