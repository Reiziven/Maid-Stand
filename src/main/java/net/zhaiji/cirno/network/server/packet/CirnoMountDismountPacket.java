package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.entity.CirnoEntity;


/**
 * Sent client→server when the player right-clicks while holding a saddle and riding a
 * CirnoEntity. Exists because raycasting onto your own mount to "click" it is unreliable
 * (the camera usually isn't pointed at her hitbox while she's carrying you) — the client
 * instead detects "right click + saddle in hand while riding" directly from input state
 * (see ClientEventHandler) and just tells the server to drop them off.
 */
public class CirnoMountDismountPacket implements CustomPacketPayload {
    public static final Type<CirnoMountDismountPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "cirno_mount_dismount_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, CirnoMountDismountPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), CirnoMountDismountPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(FriendlyByteBuf buf) {}

    public static CirnoMountDismountPacket decode(FriendlyByteBuf buf) {
        return new CirnoMountDismountPacket();
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player == null) return;
            if (player.getVehicle() instanceof CirnoEntity cirno && cirno.hasPassenger(player)) {
                player.stopRiding();
            }
        });
    }
}
