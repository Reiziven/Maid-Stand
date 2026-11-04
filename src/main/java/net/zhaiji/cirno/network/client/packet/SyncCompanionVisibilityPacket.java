package net.zhaiji.cirno.network.client.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.client.event.ClientEventHandler;


/**
 * Sent server→client after the server processes a visibility toggle.
 * Carries the authoritative hidden state so the client never drifts.
 */
public class SyncCompanionVisibilityPacket implements CustomPacketPayload {
    public static final Type<SyncCompanionVisibilityPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "sync_companion_visibility_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SyncCompanionVisibilityPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), SyncCompanionVisibilityPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    private final boolean hidden;

    public SyncCompanionVisibilityPacket(boolean hidden) {
        this.hidden = hidden;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(hidden);
    }

    public static SyncCompanionVisibilityPacket decode(FriendlyByteBuf buf) {
        return new SyncCompanionVisibilityPacket(buf.readBoolean());
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientEventHandler.syncCompanionHidden(hidden));
    }
}
