package net.zhaiji.cirno.network.client.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.client.event.ClientEventHandler;


/**
 * Sent server→client whenever this player's command-slot ("/cirno add", no
 * Curios item required) telekinesis eligibility changes — true only while at
 * least one command Cirno is actually summoned (live in the world), not merely
 * reserved/stored in a slot.
 * <p>
 * The G-key handler on the client needs to know whether telekinesis is
 * available before it sends {@link ToggleTelekinesisModePacket} — but a
 * command-spawned Cirno lives entirely in the {@code ServerPlayer}'s
 * persistent data (see CirnoStateAccess's command-slot NBT), which is never
 * synced to the client's own player object. Without this packet the client
 * only ever sees the Curios-equipped companion (whose presence IS synced, via
 * the Curios API itself) and silently refuses to let G do anything for a
 * command-spawned Cirno, even though the server would happily allow it.
 */
public class SyncTKAccessPacket implements CustomPacketPayload {
    public static final Type<SyncTKAccessPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "sync_t_k_access_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SyncTKAccessPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), SyncTKAccessPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    private final boolean hasAccess;

    public SyncTKAccessPacket(boolean hasAccess) {
        this.hasAccess = hasAccess;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(hasAccess);
    }

    public static SyncTKAccessPacket decode(FriendlyByteBuf buf) {
        return new SyncTKAccessPacket(buf.readBoolean());
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> ClientEventHandler.syncCommandCirnoTKAccess(hasAccess));
    }
}
