package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.zhaiji.cirno.event.CommandCirnoManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.network.PacketManager;
import net.zhaiji.cirno.network.client.packet.SlotSyncPacket;


/**
 * Sent client→server when the Quick-Select screen opens.
 * The server replies with a {@link SlotSyncPacket} containing all slot states.
 */
public class RequestSlotSyncPacket implements CustomPacketPayload {
    public static final Type<RequestSlotSyncPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "request_slot_sync_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, RequestSlotSyncPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), RequestSlotSyncPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(FriendlyByteBuf buf) {}

    public static RequestSlotSyncPacket decode(FriendlyByteBuf buf) {
        return new RequestSlotSyncPacket();
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player == null) return;
            SlotSyncPacket sync = CommandCirnoManager.buildSlotSyncPacket(player);
            PacketManager.sendToClient(sync, player);
        });
    }
}
