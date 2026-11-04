package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.zhaiji.cirno.event.CommandCirnoManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.entity.CirnoEntity;
import net.zhaiji.cirno.network.PacketManager;
import net.zhaiji.cirno.network.client.packet.SyncFollowOwnerPacket;


/** Sent client→server when the player clicks the Follow toggle button in the GUI. */
public class ToggleFollowOwnerPacket implements CustomPacketPayload {
    public static final Type<ToggleFollowOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "toggle_follow_owner_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ToggleFollowOwnerPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), ToggleFollowOwnerPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(FriendlyByteBuf buf) {}

    public static ToggleFollowOwnerPacket decode(FriendlyByteBuf buf) {
        return new ToggleFollowOwnerPacket();
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player == null) return;
            // Flip the live config value
            CirnoCommonConfig.cirnoFollowsOwner = !CirnoCommonConfig.cirnoFollowsOwner;
            // Follow just turned on for this player's Cirno(s) — don't leave any of
            // them stuck asleep in the Maid Bed while they're about to start following.
            if (CirnoCommonConfig.cirnoFollowsOwner) {
                for (CirnoEntity cirno : CommandCirnoManager.getOrderedLiveCirnosPublic(player)) {
                    cirno.wakeFromMaidBed();
                }
            }
            // Sync the new value back to the requesting client
            PacketManager.sendToClient(new SyncFollowOwnerPacket(CirnoCommonConfig.cirnoFollowsOwner), player);
        });
    }
}
