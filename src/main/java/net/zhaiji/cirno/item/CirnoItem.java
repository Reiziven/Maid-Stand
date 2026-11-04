package net.zhaiji.cirno.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.zhaiji.cirno.client.animation.CirnoItemAnimations;
import net.zhaiji.cirno.client.render.CirnoItemClientExtensions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

public class CirnoItem extends Item implements GeoItem {

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    /**
     * NBT key holding which visual skin this specific stack uses. Every skin
     * shares the exact same behavior (Curios tick, tooltip, recipes,
     * companion tracking) — this is purely a cosmetic model/texture/animation
     * pick, so nothing outside the rendering code needs to know it exists.
     */
    private static final String TAG_SKIN = "Skin";
    public static final String DEFAULT_SKIN = "cirno";

    /**
     * Every skin that ships a model/texture/animation triple under
     * assets/cirno/{geo,textures/item,animations}/&lt;skin&gt;.*. Add a name
     * here (plus its lang entries) to make a new skin selectable — no other
     * code needs to change.
     */
    public static final List<String> SKINS = List.of("cirno", "solyn", "spirit", "tatsumaki");

    public CirnoItem() {
        super(new Item.Properties().stacksTo(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    /** The skin id stored on {@code stack}, or {@link #DEFAULT_SKIN} if unset/unknown. */
    public static String getSkin(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(TAG_SKIN)) return DEFAULT_SKIN;
        String skin = tag.getString(TAG_SKIN);
        return SKINS.contains(skin) ? skin : DEFAULT_SKIN;
    }

    /** A fresh stack of this item using the given skin (for the creative tab, /give, etc.). */
    public static ItemStack withSkin(Item item, String skin) {
        ItemStack stack = new ItemStack(item);
        if (!DEFAULT_SKIN.equals(skin)) {
            stack.getOrCreateTag().putString(TAG_SKIN, skin);
        }
        return stack;
    }

    @Override
    public Component getName(ItemStack stack) {
        String skin = getSkin(stack);
        return DEFAULT_SKIN.equals(skin) ? super.getName(stack) : Component.translatable("item.cirno." + skin);
    }

    // ── GeoItem ──────────────────────────────────────────────────────────────

    /**
     * Animation controllers are purely a client rendering concern. On a dedicated server
     * nothing ever renders this item, so we register nothing — and, crucially, we never
     * touch {@link CirnoItemAnimations} (which references client-only classes). Keeping
     * all client types out of THIS class is what lets it load on a dedicated server.
     */
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        controllers.add(new AnimationController<>(this, "controller", 2, CirnoItemAnimations::handle));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // Tell Forge to use our GeckoLib BEWLR for in-hand / GUI rendering. The extension itself
    // lives in a client-only class that is only instantiated here, on the client.
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new CirnoItemClientExtensions());
    }

    // Curios behaviour (equip / tick) lives in compat.curios.CirnoCurioItem and is only
    // registered when Curios is installed, so this class never references the Curios API.

    @Override
    public void appendHoverText(ItemStack itemStack, @Nullable Level level,
                                List<Component> list, TooltipFlag tooltipFlag) {
        super.appendHoverText(itemStack, level, list, tooltipFlag);
        String skin = getSkin(itemStack);
        list.add(Component.translatable("item.cirno." + skin + ".tooltip"));
    }
}
