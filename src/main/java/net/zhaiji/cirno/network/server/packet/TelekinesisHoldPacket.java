package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.zhaiji.cirno.event.TelekinesisHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;


/**
 * Sent client→server every tick while the player holds right-click on a controlled mob.
 * Carries the current eye position and look direction so the server can place the mob precisely.
 */
public class TelekinesisHoldPacket implements CustomPacketPayload {
    public static final Type<TelekinesisHoldPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "telekinesis_hold_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, TelekinesisHoldPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), TelekinesisHoldPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


    private final double eyeX, eyeY, eyeZ;
    private final double lookX, lookY, lookZ;

    public TelekinesisHoldPacket(Vec3 eyePos, Vec3 lookDir) {
        this.eyeX = eyePos.x; this.eyeY = eyePos.y; this.eyeZ = eyePos.z;
        this.lookX = lookDir.x; this.lookY = lookDir.y; this.lookZ = lookDir.z;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeDouble(eyeX); buf.writeDouble(eyeY); buf.writeDouble(eyeZ);
        buf.writeDouble(lookX); buf.writeDouble(lookY); buf.writeDouble(lookZ);
    }

    public static TelekinesisHoldPacket decode(FriendlyByteBuf buf) {
        double ex = buf.readDouble(), ey = buf.readDouble(), ez = buf.readDouble();
        double lx = buf.readDouble(), ly = buf.readDouble(), lz = buf.readDouble();
        return new TelekinesisHoldPacket(new Vec3(ex, ey, ez), new Vec3(lx, ly, lz));
    }

    public void handler(IPayloadContext ctx) {
        ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
        Vec3 eye = new Vec3(eyeX, eyeY, eyeZ);
        Vec3 look = new Vec3(lookX, lookY, lookZ);
        ctx.enqueueWork(() -> {
            if (player != null) TelekinesisHandler.holdTelekinesisTarget(player, eye, look);
        });
    }
}
