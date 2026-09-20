package net.zhaiji.catburger.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.entity.CirnoEntity;
import net.zhaiji.catburger.event.CommonEventHandler;

import java.util.UUID;
import java.util.function.Supplier;

/** Sent client→server when the player presses the "open Cirno GUI" key. */
public class OpenCirnoGuiPacket {

    public void encode(FriendlyByteBuf buf) {}

    public static OpenCirnoGuiPacket decode(FriendlyByteBuf buf) {
        return new OpenCirnoGuiPacket();
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            UUID uuid = CommonEventHandler.getCompanionUUIDPublic(player);
            if (uuid == null) return;
            
            // Search across ALL dimensions, not just the player's current dimension
            if (player.getServer() == null) return;
            CirnoEntity cirno = null;
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity entity = level.getEntity(uuid);
                if (entity instanceof CirnoEntity) {
                    cirno = (CirnoEntity) entity;
                    break;
                }
            }
            
            // Open GUI regardless of distance, dimension, or sleep state
            if (cirno != null && cirno.isAlive()) {
                cirno.openMaidGui(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
