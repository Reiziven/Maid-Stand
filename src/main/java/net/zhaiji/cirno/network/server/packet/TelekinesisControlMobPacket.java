package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.zhaiji.cirno.event.TelekinesisHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Sent client→server when the player right-clicks a mob while in telekinesis mode.
 * Carries the target entity's UUID.
 */
public class TelekinesisControlMobPacket implements CustomPacketPayload {
    public static final Type<TelekinesisControlMobPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "telekinesis_control_mob_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, TelekinesisControlMobPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), TelekinesisControlMobPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    private final UUID targetUUID;

    public TelekinesisControlMobPacket(UUID targetUUID) {
        this.targetUUID = targetUUID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetUUID);
    }

    public static TelekinesisControlMobPacket decode(FriendlyByteBuf buf) {
        return new TelekinesisControlMobPacket(buf.readUUID());
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player != null) {
                TelekinesisHandler.startTelekinesisControl(player, targetUUID);
            }
        });
    }
}
