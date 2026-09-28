package net.zhaiji.cirno.network.client.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.cirno.client.event.ClientEventHandler;

import java.util.function.Supplier;

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
public class SyncTKAccessPacket {

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

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> ClientEventHandler.syncCommandCirnoTKAccess(hasAccess));
        ctx.setPacketHandled(true);
    }
}
