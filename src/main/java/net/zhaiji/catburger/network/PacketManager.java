package net.zhaiji.catburger.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.zhaiji.catburger.CatBurger;
import net.zhaiji.catburger.network.client.packet.PlayerDeathPacket;
import net.zhaiji.catburger.network.client.packet.SyncCompanionVisibilityPacket;
import net.zhaiji.catburger.network.client.packet.SyncFollowOwnerPacket;
import net.zhaiji.catburger.network.client.packet.SlotSyncPacket;
import net.zhaiji.catburger.network.server.packet.OpenCirnoGuiPacket;
import net.zhaiji.catburger.network.server.packet.OpenCirnoMenuPacket;
import net.zhaiji.catburger.network.server.packet.OpenMaidInventoryPacket;
import net.zhaiji.catburger.network.server.packet.RequestSlotSyncPacket;
import net.zhaiji.catburger.network.server.packet.SlotActionPacket;
import net.zhaiji.catburger.network.server.packet.TelekinesisControlMobPacket;
import net.zhaiji.catburger.network.server.packet.TelekinesisHoldPacket;
import net.zhaiji.catburger.network.server.packet.ToggleCompanionVisibilityPacket;
import net.zhaiji.catburger.network.server.packet.ToggleFollowOwnerPacket;
import net.zhaiji.catburger.network.server.packet.ToggleMaidFollowPacket;
import net.zhaiji.catburger.network.server.packet.ToggleTelekinesisModePacket;

public class PacketManager {
    public static final String VERSION = "1.0";

    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(CatBurger.MOD_ID, "main"),
            () -> VERSION,
            VERSION::equals,
            VERSION::equals
    );

    public static void registry() {
        int id = 0;
        // Server → Client
        INSTANCE.messageBuilder(PlayerDeathPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(PlayerDeathPacket::encode)
                .decoder(PlayerDeathPacket::decode)
                .consumerMainThread(PlayerDeathPacket::handler)
                .add();

        INSTANCE.messageBuilder(SyncCompanionVisibilityPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncCompanionVisibilityPacket::encode)
                .decoder(SyncCompanionVisibilityPacket::decode)
                .consumerMainThread(SyncCompanionVisibilityPacket::handler)
                .add();

        // Client → Server
        INSTANCE.messageBuilder(ToggleCompanionVisibilityPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleCompanionVisibilityPacket::encode)
                .decoder(ToggleCompanionVisibilityPacket::decode)
                .consumerMainThread(ToggleCompanionVisibilityPacket::handler)
                .add();

        INSTANCE.messageBuilder(ToggleTelekinesisModePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleTelekinesisModePacket::encode)
                .decoder(ToggleTelekinesisModePacket::decode)
                .consumerMainThread(ToggleTelekinesisModePacket::handler)
                .add();

        INSTANCE.messageBuilder(TelekinesisControlMobPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TelekinesisControlMobPacket::encode)
                .decoder(TelekinesisControlMobPacket::decode)
                .consumerMainThread(TelekinesisControlMobPacket::handler)
                .add();

        INSTANCE.messageBuilder(TelekinesisHoldPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TelekinesisHoldPacket::encode)
                .decoder(TelekinesisHoldPacket::decode)
                .consumerMainThread(TelekinesisHoldPacket::handler)
                .add();

        INSTANCE.messageBuilder(OpenCirnoGuiPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenCirnoGuiPacket::encode)
                .decoder(OpenCirnoGuiPacket::decode)
                .consumerMainThread(OpenCirnoGuiPacket::handler)
                .add();

        INSTANCE.messageBuilder(OpenCirnoMenuPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenCirnoMenuPacket::encode)
                .decoder(OpenCirnoMenuPacket::decode)
                .consumerMainThread(OpenCirnoMenuPacket::handler)
                .add();

        // Quick-Select screen packets
        INSTANCE.messageBuilder(RequestSlotSyncPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestSlotSyncPacket::encode)
                .decoder(RequestSlotSyncPacket::decode)
                .consumerMainThread(RequestSlotSyncPacket::handler)
                .add();

        INSTANCE.messageBuilder(SlotSyncPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SlotSyncPacket::encode)
                .decoder(SlotSyncPacket::decode)
                .consumerMainThread(SlotSyncPacket::handler)
                .add();

        INSTANCE.messageBuilder(SlotActionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SlotActionPacket::encode)
                .decoder(SlotActionPacket::decode)
                .consumerMainThread(SlotActionPacket::handler)
                .add();

        INSTANCE.messageBuilder(OpenMaidInventoryPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(OpenMaidInventoryPacket::encode)
                .decoder(OpenMaidInventoryPacket::decode)
                .consumerMainThread(OpenMaidInventoryPacket::handler)
                .add();

        // Follow-owner toggle
        INSTANCE.messageBuilder(ToggleFollowOwnerPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleFollowOwnerPacket::encode)
                .decoder(ToggleFollowOwnerPacket::decode)
                .consumerMainThread(ToggleFollowOwnerPacket::handler)
                .add();

        INSTANCE.messageBuilder(SyncFollowOwnerPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncFollowOwnerPacket::encode)
                .decoder(SyncFollowOwnerPacket::decode)
                .consumerMainThread(SyncFollowOwnerPacket::handler)
                .add();

        INSTANCE.messageBuilder(ToggleMaidFollowPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ToggleMaidFollowPacket::encode)
                .decoder(ToggleMaidFollowPacket::decode)
                .consumerMainThread(ToggleMaidFollowPacket::handler)
                .add();
    }

    public static <MSG> void sendToClient(MSG msg, ServerPlayer serverPlayer) {
        INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), msg);
    }

    public static <MSG> void sendToServer(MSG msg) {
        INSTANCE.sendToServer(msg);
    }
}
