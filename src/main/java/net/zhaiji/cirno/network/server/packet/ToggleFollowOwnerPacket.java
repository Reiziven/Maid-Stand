package net.zhaiji.cirno.network.server.packet;

import net.zhaiji.cirno.event.CommandCirnoManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.entity.CirnoEntity;
import net.zhaiji.cirno.network.PacketManager;
import net.zhaiji.cirno.network.client.packet.SyncFollowOwnerPacket;

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
            CirnoCommonConfig.cirnoFollowsOwner = !CirnoCommonConfig.cirnoFollowsOwner;
            // Follow just turned on for this player's Cirno(s) — don't leave any of
            // them stuck asleep in the Maid Bed while they're about to start following.
            if (CirnoCommonConfig.cirnoFollowsOwner) {
                for (CirnoEntity cirno : CommandCirnoManager.getOrderedLiveCirnosPublic(player)) {
                    cirno.wakeFromMaidBed();
                }
            }
            // Sync the new value back to the requesting client
            PacketManager.sendToClient(new SyncFollowOwnerPacket(CirnoCommonConfig.cirnoFollowsOwner), player);
        });
        ctx.setPacketHandled(true);
    }
}
