package net.zhaiji.catburger.network.server.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.catburger.event.CommonEventHandler;
import net.zhaiji.catburger.network.PacketManager;
import net.zhaiji.catburger.network.client.packet.SlotSyncPacket;

import java.util.function.Supplier;

/**
 * Sent client→server when the player clicks an action button in the Quick-Select screen.
 *
 * slotIndex: 0 = curio, 1–4 = command slots
 * action:    SUMMON, STORE, ADD
 */
public class SlotActionPacket {

    public enum Action { SUMMON, STORE, ADD }

    private final int slotIndex;
    private final Action action;

    public SlotActionPacket(int slotIndex, Action action) {
        this.slotIndex = slotIndex;
        this.action = action;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(slotIndex);
        buf.writeVarInt(action.ordinal());
    }

    public static SlotActionPacket decode(FriendlyByteBuf buf) {
        int idx = buf.readVarInt();
        Action action = Action.values()[buf.readVarInt()];
        return new SlotActionPacket(idx, action);
    }

    public void handler(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context ctx = supplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            if (slotIndex == 0) {
                // Curio slot
                switch (action) {
                    case SUMMON -> CommonEventHandler.summonCurioCompanion(player);
                    case STORE  -> CommonEventHandler.storeCurioCompanion(player);
                    default -> { /* ADD not applicable to curio */ }
                }
            } else {
                // Command slots 1–4
                switch (action) {
                    case SUMMON -> CommonEventHandler.spawnOrRestoreCommandCirnoAt(player, slotIndex, true);
                    case STORE  -> CommonEventHandler.storeCommandCirnoAt(player, slotIndex);
                    case ADD -> {
                        GameType gm = player.gameMode.getGameModeForPlayer();
                        if (gm == GameType.CREATIVE || gm == GameType.SPECTATOR) {
                            CommonEventHandler.addCommandCirno(player, true, true);
                        }
                        // ADD is silently blocked in SURVIVAL and ADVENTURE
                    }
                }
            }

            // Push updated state back to the client
            SlotSyncPacket sync = CommonEventHandler.buildSlotSyncPacket(player);
            PacketManager.sendToClient(sync, player);
        });
        ctx.setPacketHandled(true);
    }
}
