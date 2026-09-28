package net.zhaiji.cirno.network.server.packet;

import net.zhaiji.cirno.event.CommandCirnoManager;
import net.zhaiji.cirno.event.CompanionLifecycleHandler;
import net.zhaiji.cirno.event.TelekinesisHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.NetworkEvent;
import net.zhaiji.cirno.init.InitItem;
import net.zhaiji.cirno.network.PacketManager;
import net.zhaiji.cirno.network.client.packet.SlotSyncPacket;

import java.util.function.Supplier;

/**
 * Sent client→server when the player clicks an action button in the Quick-Select screen.
 *
 * slotIndex: 0 = curio, 1–4 = command slots
 * action:    SUMMON, STORE, ADD  (ADD: creative/spectator spawns a fresh Cirno;
 *             with an Oathpin in the main hand it converts the looked-at maid instead)
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
                    case SUMMON -> CompanionLifecycleHandler.summonCurioCompanion(player);
                    case STORE  -> CompanionLifecycleHandler.storeCurioCompanion(player);
                    default -> { /* ADD not applicable to curio */ }
                }
            } else {
                // Command slots 1–4
                switch (action) {
                    case SUMMON -> {
                        if (CommandCirnoManager.spawnOrRestoreCommandCirnoAt(player, slotIndex, true) != null) {
                            // Mirror /cirno add and /cirno toggle (CirnoCommand): summoning a
                            // command Cirno grants attribute buffs and telekinesis just like the
                            // command does. This was previously only wired up on the OP-only
                            // /cirno command, so players using the in-game Quick-Select GUI (this
                            // packet) never got buffs or telekinesis for their command Cirno.
                            TelekinesisHandler.applyAttributeBuffs(player);
                            TelekinesisHandler.enableTelekinesisMode(player);
                        }
                    }
                    case STORE  -> CommandCirnoManager.storeCommandCirnoAt(player, slotIndex);
                    case ADD -> {
                        if (player.getMainHandItem().is(InitItem.OATHPIN.get())) {
                            // Oathpin in main hand (works in any game mode): transform the maid
                            // the player is looking at into a command Cirno.
                            CommandCirnoManager.convertLookedAtMaidToCommandCirno(player);
                        } else {
                            GameType gm = player.gameMode.getGameModeForPlayer();
                            if (gm == GameType.CREATIVE || gm == GameType.SPECTATOR) {
                                if (CommandCirnoManager.addCommandCirno(player, true, true) >= 0) {
                                    TelekinesisHandler.applyAttributeBuffs(player);
                                    TelekinesisHandler.enableTelekinesisMode(player);
                                }
                            }
                            // Otherwise ADD is silently blocked in SURVIVAL and ADVENTURE
                        }
                    }
                }
            }

            // Push updated state back to the client
            SlotSyncPacket sync = CommandCirnoManager.buildSlotSyncPacket(player);
            PacketManager.sendToClient(sync, player);
        });
        ctx.setPacketHandled(true);
    }
}