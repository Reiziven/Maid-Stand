package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.zhaiji.cirno.event.CompanionLifecycleHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * Sent client→server when the player holds Shift while pressing the "toggle
 * companion visible" key (H). Opens the per-slot command-Cirno control menu
 * instead of the plain store/summon-all toggle.
 */
public class OpenCirnoMenuPacket implements CustomPacketPayload {
    public static final Type<OpenCirnoMenuPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "open_cirno_menu_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, OpenCirnoMenuPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), OpenCirnoMenuPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    public void encode(FriendlyByteBuf buf) {}

    public static OpenCirnoMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenCirnoMenuPacket();
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player != null) {
                CompanionLifecycleHandler.openCirnoMenu(player);
            }
        });
    }
}
