package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.zhaiji.cirno.event.CirnoStateAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.entity.CirnoEntity;

import java.util.UUID;

/**
 * Sent client→server when the player clicks a slot card (not the action button)
 * in the Cirno Quick-Select screen. Opens the maid inventory GUI for that slot.
 *
 * slotIndex: 0 = curio companion, 1–4 = command-slot companions
 */
public class OpenMaidInventoryPacket implements CustomPacketPayload {
    public static final Type<OpenMaidInventoryPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "open_maid_inventory_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, OpenMaidInventoryPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), OpenMaidInventoryPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


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

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
            if (player == null || player.getServer() == null) return;

            UUID uuid = (slotIndex == 0)
                    ? CirnoStateAccess.getCompanionUUIDPublic(player)
                    : CirnoStateAccess.getCommandCirnoUUID(player, slotIndex);

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
    }
}
