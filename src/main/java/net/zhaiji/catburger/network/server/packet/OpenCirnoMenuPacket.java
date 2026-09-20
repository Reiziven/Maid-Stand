package net.zhaiji.catburger.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.event.CommonEventHandler;

import java.util.function.Supplier;

/**
 * Sent client→server when the player holds Shift while pressing the "toggle
 * companion visible" key (H). Opens the per-slot command-Cirno control menu
 * instead of the plain store/summon-all toggle.
 */
public class OpenCirnoMenuPacket {

    public void encode(FriendlyByteBuf buf) {}

    public static OpenCirnoMenuPacket decode(FriendlyByteBuf buf) {
        return new OpenCirnoMenuPacket();
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                CommonEventHandler.openCirnoMenu(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
