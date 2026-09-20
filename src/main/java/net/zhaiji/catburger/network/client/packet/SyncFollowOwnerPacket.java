package net.zhaiji.catburger.network.client.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.client.gui.CirnoQuickSelectScreen;

import java.util.function.Supplier;

/**
 * Sent server→client after the server processes a follow-owner toggle.
 * Carries the authoritative state so the GUI updates immediately.
 */
public class SyncFollowOwnerPacket {

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

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> CirnoQuickSelectScreen.syncFollowState(following));
        ctx.setPacketHandled(true);
    }
}
