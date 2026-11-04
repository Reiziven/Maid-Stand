package net.zhaiji.cirno.network.client.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.network.client.ClientPacketHandler;


public class PlayerDeathPacket implements CustomPacketPayload {
    public static final Type<PlayerDeathPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "player_death_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, PlayerDeathPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), PlayerDeathPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static PlayerDeathPacket decode(FriendlyByteBuf buf) {
        return new PlayerDeathPacket();
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientPacketHandler.handlerPlayerDeathPacket(this);
        });
    }
}
