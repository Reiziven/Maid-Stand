package net.zhaiji.catburger.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.event.CommonEventHandler;

import java.util.function.Supplier;

/**
 * Sent client→server every tick while the player holds right-click on a controlled mob.
 * Carries the current eye position and look direction so the server can place the mob precisely.
 */
public class TelekinesisHoldPacket {

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

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ServerPlayer player = ctx.getSender();
        Vec3 eye = new Vec3(eyeX, eyeY, eyeZ);
        Vec3 look = new Vec3(lookX, lookY, lookZ);
        ctx.enqueueWork(() -> {
            if (player != null) CommonEventHandler.holdTelekinesisTarget(player, eye, look);
        });
        ctx.setPacketHandled(true);
    }
}
