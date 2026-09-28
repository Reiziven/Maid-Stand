package net.zhaiji.cirno.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.zhaiji.cirno.Cirno;
import net.zhaiji.cirno.item.CirnoItem;
import net.zhaiji.cirno.item.OathpinItem;

public class InitItem {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Cirno.MOD_ID);

    public static final RegistryObject<Item> CIRNO = ITEMS.register("cirno", CirnoItem::new);
    public static final RegistryObject<Item> OATHPIN = ITEMS.register("oathpin", OathpinItem::new);
}
