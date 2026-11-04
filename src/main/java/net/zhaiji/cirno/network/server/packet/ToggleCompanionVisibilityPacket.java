package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.zhaiji.cirno.event.CompanionLifecycleHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/** Sent client→server when the player presses the "toggle companion visible" key. */
public class ToggleCompanionVisibilityPacket implements CustomPacketPayload {
    public static final Type<ToggleCompanionVisibilityPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "toggle_companion_visibility_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ToggleCompanionVisibilityPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), ToggleCompanionVisibilityPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(FriendlyByteBuf buf) {}

    public static ToggleCompanionVisibilityPacket decode(FriendlyByteBuf buf) {
        return new ToggleCompanionVisibilityPacket();
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player != null) {
                CompanionLifecycleHandler.toggleCompanionVisibility(player);
            }
        });
    }
}
