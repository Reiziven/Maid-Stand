package net.zhaiji.cirno.network.server.packet;

import net.zhaiji.cirno.event.CompanionLifecycleHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Sent client→server when the player presses the "toggle companion visible" key. */
public class ToggleCompanionVisibilityPacket {

    public void encode(FriendlyByteBuf buf) {}

    public static ToggleCompanionVisibilityPacket decode(FriendlyByteBuf buf) {
        return new ToggleCompanionVisibilityPacket();
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player != null) {
                CompanionLifecycleHandler.toggleCompanionVisibility(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
