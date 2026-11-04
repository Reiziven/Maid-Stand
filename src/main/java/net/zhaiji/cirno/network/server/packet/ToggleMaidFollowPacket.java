package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.entity.CirnoEntity;


/**
 * Sent client→server when the player clicks the Follow toggle button
 * in the maid GUI for a specific CirnoEntity.
 * Only works if cirnoFollowsOwner is enabled in config (acts as a master gate).
 */
public class ToggleMaidFollowPacket implements CustomPacketPayload {
    public static final Type<ToggleMaidFollowPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "toggle_maid_follow_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, ToggleMaidFollowPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), ToggleMaidFollowPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


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

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player == null) return;
            // Config acts as master enable gate — if disabled server-wide, button has no effect
            if (!CirnoCommonConfig.cirnoFollowsOwner && follow) return;
            // Find the entity in any server level
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity e = level.getEntity(entityId);
                if (e instanceof CirnoEntity cirno) {
                    cirno.setPerEntityFollow(follow);
                    return;
                }
            }
        });
    }
}
