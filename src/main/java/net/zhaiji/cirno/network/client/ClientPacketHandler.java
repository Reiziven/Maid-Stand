package net.zhaiji.cirno.network.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.zhaiji.cirno.init.InitItem;
import net.zhaiji.cirno.network.client.packet.PlayerDeathPacket;

public class ClientPacketHandler {
    public static void handlerPlayerDeathPacket(PlayerDeathPacket packet) {
        Minecraft.getInstance().gameRenderer.displayItemActivation(new ItemStack(InitItem.CIRNO.get()));
    }
}
