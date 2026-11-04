package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.zhaiji.cirno.event.CirnoStateAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.entity.CirnoEntity;

import java.util.UUID;

/** Sent client→server when the player presses the "open Cirno GUI" key. */
public class OpenCirnoGuiPacket implements CustomPacketPayload {
    public static final Type<OpenCirnoGuiPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "open_cirno_gui_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, OpenCirnoGuiPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), OpenCirnoGuiPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(FriendlyByteBuf buf) {}

    public static OpenCirnoGuiPacket decode(FriendlyByteBuf buf) {
        return new OpenCirnoGuiPacket();
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player == null) return;
            UUID uuid = CirnoStateAccess.getCompanionUUIDPublic(player);
            if (uuid == null) return;
            
            // Search across ALL dimensions, not just the player's current dimension
            if (player.getServer() == null) return;
            CirnoEntity cirno = null;
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof CirnoEntity) {
                    cirno = (CirnoEntity) entity;
                    break;
                }
            }
            
            // Open GUI regardless of distance, dimension, or sleep state
            if (cirno != null && cirno.isAlive()) {
                cirno.openMaidGui(player);
            }
        });
    }
}
