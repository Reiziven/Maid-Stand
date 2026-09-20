package net.zhaiji.catburger.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.event.CommonEventHandler;

import java.util.function.Supplier;

/** Sent client→server when the player presses the "toggle telekinesis mode" key. */
public class ToggleTelekinesisModePacket {

    public void encode(FriendlyByteBuf buf) {}

    public static ToggleTelekinesisModePacket decode(FriendlyByteBuf buf) {
        return new ToggleTelekinesisModePacket();
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                CommonEventHandler.toggleTelekinesisMode(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
