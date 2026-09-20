package net.zhaiji.catburger.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.config.CatBurgerCommonConfig;
import net.zhaiji.catburger.network.PacketManager;
import net.zhaiji.catburger.network.client.packet.SyncFollowOwnerPacket;

import java.util.function.Supplier;

/** Sent client→server when the player clicks the Follow toggle button in the GUI. */
public class ToggleFollowOwnerPacket {

    public void encode(FriendlyByteBuf buf) {}

    public static ToggleFollowOwnerPacket decode(FriendlyByteBuf buf) {
        return new ToggleFollowOwnerPacket();
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            // Flip the live config value
            CatBurgerCommonConfig.cirnoFollowsOwner = !CatBurgerCommonConfig.cirnoFollowsOwner;
            // Sync the new value back to the requesting client
            PacketManager.sendToClient(new SyncFollowOwnerPacket(CatBurgerCommonConfig.cirnoFollowsOwner), player);
        });
        ctx.setPacketHandled(true);
    }
}
