package net.zhaiji.catburger.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.config.CatBurgerCommonConfig;
import net.zhaiji.catburger.entity.CirnoEntity;

import java.util.function.Supplier;

/**
 * Sent client→server when the player clicks the Follow toggle button
 * in the maid GUI for a specific CirnoEntity.
 * Only works if cirnoFollowsOwner is enabled in config (acts as a master gate).
 */
public class ToggleMaidFollowPacket {

    private final int entityId;
    private final boolean follow;

    public ToggleMaidFollowPacket(int entityId, boolean follow) {
        this.entityId = entityId;
        this.follow = follow;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(follow);
    }

    public static ToggleMaidFollowPacket decode(FriendlyByteBuf buf) {
        return new ToggleMaidFollowPacket(buf.readInt(), buf.readBoolean());
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            // Config acts as master enable gate — if disabled server-wide, button has no effect
            if (!CatBurgerCommonConfig.cirnoFollowsOwner && follow) return;
            // Find the entity in any server level
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity e = level.getEntity(entityId);
                if (e instanceof CirnoEntity cirno) {
                    cirno.setPerEntityFollow(follow);
                    return;
                }
            }
        });
        ctx.setPacketHandled(true);
    }
}
