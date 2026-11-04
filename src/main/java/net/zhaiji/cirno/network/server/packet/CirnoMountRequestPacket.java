package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.entity.CirnoEntity;


/**
 * Sent client→server when the player presses the "Mount Cirno" keybind while holding a saddle
 * and looking at a CirnoEntity. Handles both the first-ever saddle-and-mount and every mount
 * after that — the server (CirnoEntity#tryMountFromKey) figures out which applies. Exists so
 * neither part of the feature needs a click gesture (previously shift+right-click to saddle,
 * then a plain right-click to mount, which overloaded right-click and blocked her normal maid
 * interactions — see CirnoEntity#mobInteract).
 * <p>
 * Targets whatever Cirno the client's own crosshair/raycast picked (see ClientEventHandler,
 * which reads {@code Minecraft.hitResult}) by entity id — the same "what are you looking at"
 * check a plain right-click would have used, so this doesn't depend on the target being your
 * bound curio/command companion. The server re-validates distance so a spoofed id can't be used
 * to mount a far-away Cirno.
 */
public class CirnoMountRequestPacket implements CustomPacketPayload {
    public static final Type<CirnoMountRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "cirno_mount_request_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, CirnoMountRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), CirnoMountRequestPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    /** Generous margin over vanilla interact reach, since this only needs to reject spoofed ids. */
    private static final double MAX_REACH_SQ = 36.0; // 6 blocks

    private final int cirnoEntityId;

    public CirnoMountRequestPacket(int cirnoEntityId) {
        this.cirnoEntityId = cirnoEntityId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(cirnoEntityId);
    }

    public static CirnoMountRequestPacket decode(FriendlyByteBuf buf) {
        return new CirnoMountRequestPacket(buf.readVarInt());
    }

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player == null) return;

            Entity entity = player.level().getEntity(cirnoEntityId);
            if (!(entity instanceof CirnoEntity cirno)) return;
            if (player.distanceToSqr(cirno) > MAX_REACH_SQ) return;

            cirno.tryMountFromKey(player);
        });
    }
}
