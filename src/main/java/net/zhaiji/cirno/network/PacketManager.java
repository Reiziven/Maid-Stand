package net.zhaiji.cirno.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.zhaiji.cirno.network.client.packet.PlayerDeathPacket;
import net.zhaiji.cirno.network.client.packet.SlotSyncPacket;
import net.zhaiji.cirno.network.client.packet.SyncCompanionVisibilityPacket;
import net.zhaiji.cirno.network.client.packet.SyncFollowOwnerPacket;
import net.zhaiji.cirno.network.client.packet.SyncTKAccessPacket;
import net.zhaiji.cirno.network.server.packet.CirnoMountDismountPacket;
import net.zhaiji.cirno.network.server.packet.CirnoMountRequestPacket;
import net.zhaiji.cirno.network.server.packet.OpenCirnoGuiPacket;
import net.zhaiji.cirno.network.server.packet.OpenCirnoMenuPacket;
import net.zhaiji.cirno.network.server.packet.OpenMaidInventoryPacket;
import net.zhaiji.cirno.network.server.packet.RequestSlotSyncPacket;
import net.zhaiji.cirno.network.server.packet.SlotActionPacket;
import net.zhaiji.cirno.network.server.packet.TelekinesisControlMobPacket;
import net.zhaiji.cirno.network.server.packet.TelekinesisHoldPacket;
import net.zhaiji.cirno.network.server.packet.ToggleCompanionVisibilityPacket;
import net.zhaiji.cirno.network.server.packet.ToggleFollowOwnerPacket;
import net.zhaiji.cirno.network.server.packet.ToggleMaidFollowPacket;
import net.zhaiji.cirno.network.server.packet.ToggleTelekinesisModePacket;

/**
 * NeoForge 1.21.1 networking: every packet is a {@link CustomPacketPayload} registered on a
 * {@link PayloadRegistrar} (replaces the Forge SimpleChannel). Handlers run on the main thread.
 */
public class PacketManager {
    public static final String VERSION = "1.0";

    /** Hook for the mod event bus (see Cirno constructor). */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);

        // Server -> Client
        registrar.playToClient(PlayerDeathPacket.TYPE, PlayerDeathPacket.STREAM_CODEC, PlayerDeathPacket::handler);
        registrar.playToClient(SlotSyncPacket.TYPE, SlotSyncPacket.STREAM_CODEC, SlotSyncPacket::handler);
        registrar.playToClient(SyncCompanionVisibilityPacket.TYPE, SyncCompanionVisibilityPacket.STREAM_CODEC, SyncCompanionVisibilityPacket::handler);
        registrar.playToClient(SyncFollowOwnerPacket.TYPE, SyncFollowOwnerPacket.STREAM_CODEC, SyncFollowOwnerPacket::handler);
        registrar.playToClient(SyncTKAccessPacket.TYPE, SyncTKAccessPacket.STREAM_CODEC, SyncTKAccessPacket::handler);

        // Client -> Server
        registrar.playToServer(CirnoMountDismountPacket.TYPE, CirnoMountDismountPacket.STREAM_CODEC, CirnoMountDismountPacket::handler);
        registrar.playToServer(CirnoMountRequestPacket.TYPE, CirnoMountRequestPacket.STREAM_CODEC, CirnoMountRequestPacket::handler);
        registrar.playToServer(OpenCirnoGuiPacket.TYPE, OpenCirnoGuiPacket.STREAM_CODEC, OpenCirnoGuiPacket::handler);
        registrar.playToServer(OpenCirnoMenuPacket.TYPE, OpenCirnoMenuPacket.STREAM_CODEC, OpenCirnoMenuPacket::handler);
        registrar.playToServer(OpenMaidInventoryPacket.TYPE, OpenMaidInventoryPacket.STREAM_CODEC, OpenMaidInventoryPacket::handler);
        registrar.playToServer(RequestSlotSyncPacket.TYPE, RequestSlotSyncPacket.STREAM_CODEC, RequestSlotSyncPacket::handler);
        registrar.playToServer(SlotActionPacket.TYPE, SlotActionPacket.STREAM_CODEC, SlotActionPacket::handler);
        registrar.playToServer(TelekinesisControlMobPacket.TYPE, TelekinesisControlMobPacket.STREAM_CODEC, TelekinesisControlMobPacket::handler);
        registrar.playToServer(TelekinesisHoldPacket.TYPE, TelekinesisHoldPacket.STREAM_CODEC, TelekinesisHoldPacket::handler);
        registrar.playToServer(ToggleCompanionVisibilityPacket.TYPE, ToggleCompanionVisibilityPacket.STREAM_CODEC, ToggleCompanionVisibilityPacket::handler);
        registrar.playToServer(ToggleFollowOwnerPacket.TYPE, ToggleFollowOwnerPacket.STREAM_CODEC, ToggleFollowOwnerPacket::handler);
        registrar.playToServer(ToggleMaidFollowPacket.TYPE, ToggleMaidFollowPacket.STREAM_CODEC, ToggleMaidFollowPacket::handler);
        registrar.playToServer(ToggleTelekinesisModePacket.TYPE, ToggleTelekinesisModePacket.STREAM_CODEC, ToggleTelekinesisModePacket::handler);
    }

    public static <MSG extends CustomPacketPayload> void sendToClient(MSG msg, ServerPlayer serverPlayer) {
        PacketDistributor.sendToPlayer(serverPlayer, msg);
    }

    /** Client -> server. Must only be called on the logical client. */
    public static <MSG extends CustomPacketPayload> void sendToServer(MSG msg) {
        PacketDistributor.sendToServer(msg);
    }
}
