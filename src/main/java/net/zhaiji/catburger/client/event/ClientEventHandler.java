package net.zhaiji.catburger.client.event;

import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.zhaiji.catburger.client.CatBurgerKeybinds;
import net.zhaiji.catburger.client.ClientTKState;
import net.zhaiji.catburger.client.render.CatBurgerRenderer;
import net.zhaiji.catburger.config.CatBurgerCommonConfig;
import net.zhaiji.catburger.event.CommonEventHandler;
import net.zhaiji.catburger.init.InitEntity;
import net.zhaiji.catburger.init.InitItem;
import net.zhaiji.catburger.network.PacketManager;
import net.zhaiji.catburger.network.server.packet.OpenCirnoGuiPacket;
import net.zhaiji.catburger.network.server.packet.TelekinesisControlMobPacket;
import net.zhaiji.catburger.network.server.packet.TelekinesisHoldPacket;
import net.zhaiji.catburger.network.server.packet.ToggleCompanionVisibilityPacket;
import net.zhaiji.catburger.network.server.packet.ToggleTelekinesisModePacket;
// OpenCirnoMenuPacket retained for backward compatibility
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

public class ClientEventHandler {

    /** Tracks companion visibility state client-side to show accurate toggle messages. */
    private static boolean companionHidden = false;

    /** Cooldown ticks remaining before the H key can toggle visibility again (client-side). */
    private static int toggleVisibilityCooldown = 0;
    /** Cooldown duration in ticks (1 second). */
    private static final int TOGGLE_VISIBILITY_COOLDOWN_TICKS = 60;

    /** Whether the companion-visible key was already held last tick (prevents repeated firing). */
    private static boolean toggleVisibleKeyWasDown = false;
    /** Ticks the companion-visible key has been held continuously. */
    private static int toggleVisibleHoldTicks = 0;
    /** How many ticks the key must be held before it triggers (0.25 s). */
    private static final int TOGGLE_VISIBLE_HOLD_THRESHOLD = 5;

    /** UUID of the mob currently held under telekinesis, null if none. */
    private static java.util.UUID heldMobUUID = null;

    public static void handlerFMLClientSetupEvent(FMLClientSetupEvent event) {
        CuriosRendererRegistry.register(InitItem.CAT_BURGER.get(), CatBurgerRenderer::new);
    }

    public static void handlerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(InitEntity.CIRNO.get(), EntityMaidRenderer::new);
    }

    /** Polls keybinds every client tick and sends the appropriate packet to the server. */
    public static void handlerClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.screen != null) return;

        if (toggleVisibilityCooldown > 0) toggleVisibilityCooldown--;

        // tap H  → toggle visibility
        // hold H → open Quick-Select screen (after TOGGLE_VISIBLE_HOLD_THRESHOLD ticks)
        boolean toggleVisibleKeyDown = CatBurgerKeybinds.TOGGLE_COMPANION_VISIBLE.isDown();
        if (toggleVisibleKeyDown) {
            toggleVisibleHoldTicks++;
            if (toggleVisibleHoldTicks == TOGGLE_VISIBLE_HOLD_THRESHOLD) {
                // Drain queued clicks so the tap-path doesn't fire on release
                while (CatBurgerKeybinds.TOGGLE_COMPANION_VISIBLE.consumeClick()) { /* drain */ }
                // Open the Quick-Select overlay
                mc.setScreen(new net.zhaiji.catburger.client.gui.CirnoQuickSelectScreen());
            }
        } else {
            if (toggleVisibleKeyWasDown && toggleVisibleHoldTicks < TOGGLE_VISIBLE_HOLD_THRESHOLD) {
                // Released before hold threshold → treat as a tap
                while (CatBurgerKeybinds.TOGGLE_COMPANION_VISIBLE.consumeClick()) { /* drain */ }
                if (toggleVisibilityCooldown > 0) {
                    player.displayClientMessage(
                            Component.literal("§c[Cirno] Wait " + (toggleVisibilityCooldown / 20 + 1) + "s..."),
                            true
                    );
                } else {
                    toggleVisibilityCooldown = TOGGLE_VISIBILITY_COOLDOWN_TICKS;
                    PacketManager.sendToServer(new ToggleCompanionVisibilityPacket());
                }
            } else if (toggleVisibleKeyWasDown) {
                // Released after hold fired — drain anything leftover
                while (CatBurgerKeybinds.TOGGLE_COMPANION_VISIBLE.consumeClick()) { /* drain */ }
            }
            toggleVisibleHoldTicks = 0;
        }
        toggleVisibleKeyWasDown = toggleVisibleKeyDown;

        if (CatBurgerKeybinds.TOGGLE_TELEKINESIS_MODE.consumeClick()) {
            if (!CommonEventHandler.hasTKAccessPublic(player)) {
                // no Cirno — G does nothing
            } else if (companionHidden && CatBurgerCommonConfig.requireCompanionForBuffs) {
                player.displayClientMessage(
                        Component.literal("§c[Cirno] Can't use telekinesis while hidden"),
                        true
                );
            } else {
                ClientTKState.toggle();
                player.displayClientMessage(
                        Component.literal(ClientTKState.isModeActive()
                                ? "§b[Cirno] Telekinesis ON — right-click a mob to control"
                                : "§7[Cirno] Telekinesis OFF"),
                        true
                );
                PacketManager.sendToServer(new ToggleTelekinesisModePacket());
            }
        }

        if (CatBurgerKeybinds.OPEN_CIRNO_GUI.consumeClick()) {
            PacketManager.sendToServer(new OpenCirnoGuiPacket());
        }

        // While a mob/projectile is held and right-click is still pressed, send the hold packet every tick
        if (heldMobUUID != null) {
            if (mc.options.keyUse.isDown()) {
                PacketManager.sendToServer(new net.zhaiji.catburger.network.server.packet.TelekinesisHoldPacket(
                        player.getEyePosition(),
                        player.getLookAngle()
                ));
            } else {
                // Right-click released — drop the mob/projectile
                heldMobUUID = null;
                PacketManager.sendToServer(new net.zhaiji.catburger.network.server.packet.TelekinesisControlMobPacket(
                        new java.util.UUID(0, 0) // sentinel: release
                ));
            }
        }

        // Projectile grab: when TK mode is on and right-click fires, scan ahead for a nearby projectile
        if (heldMobUUID == null && ClientTKState.isModeActive() && CatBurgerCommonConfig.telekinesisControlEnabled) {
            if (mc.options.keyUse.consumeClick()) {
                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle();
                double range = 10.0;
                Vec3 end = eye.add(look.scale(range));
                AABB searchBox = new AABB(eye, end).inflate(2.0);
                Projectile closest = null;
                double closestDist = Double.MAX_VALUE;
                for (Projectile proj : player.level().getEntitiesOfClass(Projectile.class, searchBox,
                        p -> p.isAlive() && !(p.getOwner() instanceof Player))) {
                    // Only grab projectiles roughly in front of the player
                    Vec3 toProj = proj.position().subtract(eye);
                    if (toProj.dot(look) < 0) continue;
                    double dist = toProj.length();
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = proj;
                    }
                }
                if (closest != null) {
                    heldMobUUID = closest.getUUID();
                    PacketManager.sendToServer(new TelekinesisControlMobPacket(closest.getUUID()));
                }
            }
        }
    }

    /** Called by SyncCompanionVisibilityPacket — server is the source of truth for hidden state. */
    public static void syncCompanionHidden(boolean hidden) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        companionHidden = hidden;
        if (player != null) {
            player.displayClientMessage(
                    Component.literal(hidden ? "§7[Cirno] Stand hidden" : "§f[Cirno] Stand visible"),
                    true
            );
        }
    }

    /** Client-side entity right-click: send telekinesis control packet if TK mode is active. */
    public static void handlerEntityInteractClient(PlayerInteractEvent.EntityInteract event) {
        if (!event.getEntity().level().isClientSide) return;
        if (!CatBurgerCommonConfig.telekinesisControlEnabled) return;
        if (!ClientTKState.isModeActive()) return;

        Entity target = event.getTarget();
        if (target instanceof Player) return;
        // Skip entities that open an inventory/GUI on right-click (e.g. TLM maids)
        if (target instanceof net.minecraft.world.entity.LivingEntity le
                && net.zhaiji.catburger.compat.CompatManager.isTLMLoad()
                && net.zhaiji.catburger.compat.TLMCompat.canRender(le)) return;

        heldMobUUID = target.getUUID();
        PacketManager.sendToServer(new TelekinesisControlMobPacket(target.getUUID()));
        event.setCanceled(true);
    }
}
