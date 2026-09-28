package net.zhaiji.cirno.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class OathpinItem extends Item {
    public OathpinItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.cirno.oathpin.tooltip").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("item.cirno.oathpin.tooltip2").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("item.cirno.oathpin.tooltip3").withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.cirno.oathpin.tooltip4").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.cirno.oathpin.tooltip5").withStyle(ChatFormatting.DARK_GRAY));
    }
}
