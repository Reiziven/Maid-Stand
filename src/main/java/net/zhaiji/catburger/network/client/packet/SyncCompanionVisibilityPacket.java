package net.zhaiji.catburger.network.client.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.client.event.ClientEventHandler;

import java.util.function.Supplier;

/**
 * Sent server→client after the server processes a visibility toggle.
 * Carries the authoritative hidden state so the client never drifts.
 */
public class SyncCompanionVisibilityPacket {

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

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> ClientEventHandler.syncCompanionHidden(hidden));
        ctx.setPacketHandled(true);
    }
}
