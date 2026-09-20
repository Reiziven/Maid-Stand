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

/**
 * Sent client→server when the player clicks a slot card (not the action button)
 * in the Cirno Quick-Select screen. Opens the maid inventory GUI for that slot.
 *
 * slotIndex: 0 = curio companion, 1–4 = command-slot companions
 */
public class OpenMaidInventoryPacket {

    private final int slotIndex;

    public OpenMaidInventoryPacket(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slotIndex);
    }

    public static OpenMaidInventoryPacket decode(FriendlyByteBuf buf) {
        return new OpenMaidInventoryPacket(buf.readVarInt());
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.getServer() == null) return;

            UUID uuid = (slotIndex == 0)
                    ? CommonEventHandler.getCompanionUUIDPublic(player)
                    : CommonEventHandler.getCommandCirnoUUID(player, slotIndex);

            if (uuid == null) return;

            CirnoEntity cirno = null;
            for (ServerLevel level : player.getServer().getAllLevels()) {
                Entity e = level.getEntity(uuid);
                if (e instanceof CirnoEntity ce) { cirno = ce; break; }
            }

            if (cirno != null && cirno.isAlive() && !cirno.isSleeping()) {
                cirno.openMaidGui(player);
            }
        });
        ctx.setPacketHandled(true);
    }
}
