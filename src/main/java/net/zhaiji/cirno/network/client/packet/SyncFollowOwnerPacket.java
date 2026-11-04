package net.zhaiji.cirno.network.client.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.client.gui.CirnoQuickSelectScreen;


/**
 * Sent server→client after the server processes a follow-owner toggle.
 * Carries the authoritative state so the GUI updates immediately.
 */
public class SyncFollowOwnerPacket implements CustomPacketPayload {
    public static final Type<SyncFollowOwnerPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "sync_follow_owner_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SyncFollowOwnerPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), SyncFollowOwnerPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    private final boolean following;

    public SyncFollowOwnerPacket(boolean following) {
        this.following = following;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(following);
    }

    public static SyncFollowOwnerPacket decode(FriendlyByteBuf buf) {
        return new SyncFollowOwnerPacket(buf.readBoolean());
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> CirnoQuickSelectScreen.syncFollowState(following));
    }
}
