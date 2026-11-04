package net.zhaiji.cirno.init;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zhaiji.cirno.Cirno;
import net.zhaiji.cirno.item.CirnoItem;
import net.zhaiji.cirno.item.OathpinItem;

public class InitItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(net.minecraft.core.registries.Registries.ITEM, Cirno.MOD_ID);

    public static final DeferredHolder<Item, Item> CIRNO = ITEMS.register("cirno", CirnoItem::new);
    public static final DeferredHolder<Item, Item> OATHPIN = ITEMS.register("oathpin", OathpinItem::new);
}
