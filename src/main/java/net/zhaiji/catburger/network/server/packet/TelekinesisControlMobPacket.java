package net.zhaiji.catburger.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.event.CommonEventHandler;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Sent client→server when the player right-clicks a mob while in telekinesis mode.
 * Carries the target entity's UUID.
 */
public class TelekinesisControlMobPacket {

    private final UUID targetUUID;

    public TelekinesisControlMobPacket(UUID targetUUID) {
        this.targetUUID = targetUUID;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(targetUUID);
    }

    public static TelekinesisControlMobPacket decode(FriendlyByteBuf buf) {
        return new TelekinesisControlMobPacket(buf.readUUID());
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                CommonEventHandler.startTelekinesisControl(player, targetUUID);
            }
        });
        ctx.setPacketHandled(true);
    }
}
