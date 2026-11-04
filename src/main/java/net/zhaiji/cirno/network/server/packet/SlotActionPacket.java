package net.zhaiji.cirno.network.server.packet;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.zhaiji.cirno.Cirno;

import net.zhaiji.cirno.event.CommandCirnoManager;
import net.zhaiji.cirno.event.CompanionLifecycleHandler;
import net.zhaiji.cirno.event.TelekinesisHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zhaiji.cirno.init.InitItem;
import net.zhaiji.cirno.network.PacketManager;
import net.zhaiji.cirno.network.client.packet.SlotSyncPacket;


/**
 * Sent client→server when the player clicks an action button in the Quick-Select screen.
 *
 * slotIndex: 0 = curio, 1–4 = command slots
 * action:    SUMMON, STORE, ADD  (ADD: creative/spectator spawns a fresh Cirno;
 *             with an Oathpin in the main hand it converts the looked-at maid instead)
 */
public class SlotActionPacket implements CustomPacketPayload {
    public static final Type<SlotActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Cirno.MOD_ID, "slot_action_packet"));
    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SlotActionPacket> STREAM_CODEC =
            StreamCodec.of((buf, packet) -> packet.encode(buf), SlotActionPacket::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }


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

    public void handler(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.player() instanceof ServerPlayer sp ? sp : null;
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
    }
}