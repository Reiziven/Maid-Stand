package net.zhaiji.cirno.client.event;

import net.zhaiji.cirno.event.CirnoStateAccess;
import com.github.tartaricacid.touhoulittlemaid.client.renderer.entity.EntityMaidRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
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
import net.zhaiji.cirno.client.CirnoKeybinds;
import net.zhaiji.cirno.client.ClientTKState;
import net.zhaiji.cirno.client.gui.CirnoQuickSelectScreen;
import net.zhaiji.cirno.config.CirnoCommonConfig;
import net.zhaiji.cirno.init.InitEntity;
import net.zhaiji.cirno.init.InitItem;
import net.zhaiji.cirno.network.PacketManager;
import net.zhaiji.cirno.network.server.packet.OpenCirnoGuiPacket;
import net.zhaiji.cirno.network.server.packet.TelekinesisControlMobPacket;
import net.zhaiji.cirno.network.server.packet.TelekinesisHoldPacket;
import net.zhaiji.cirno.network.server.packet.ToggleCompanionVisibilityPacket;
import net.zhaiji.cirno.network.server.packet.ToggleTelekinesisModePacket;
import net.zhaiji.cirno.network.server.packet.CirnoMountDismountPacket;
import net.zhaiji.cirno.entity.CirnoEntity;
// OpenCirnoMenuPacket retained for backward compatibility

public class ClientEventHandler {

    /** Tracks companion visibility state client-side to show accurate toggle messages. */
    private static boolean companionHidden = false;

    /**
     * True if the server has told us this player currently has telekinesis access
     * through a command-spawned ("/cirno add") Cirno that is actually summoned —
     * a reserved-but-stored slot does not count. Unlike the Curios-equipped
     * companion — whose presence the Curios API already keeps in sync on the
     * client — a command Cirno's slot data lives only in the server player's
     * persistent NBT, so it has to be pushed to the client explicitly via
     * {@link net.zhaiji.cirno.network.client.packet.SyncTKAccessPacket}.
     * Defaults to false until the first sync arrives after login.
     */
    private static boolean commandCirnoTKAccess = false;

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
    /**
     * Cirno mount sprint input state.  Vanilla double-tap-forward sprinting is gated by
     * LocalPlayer#canVehicleSprint, while Cirno also needs the same sprint flag during her
     * creative-style flight.  Keep a tiny client-side fallback here so the vehicle gets the
     * exact state from the actual rebindable W/Ctrl keys even when vanilla refuses to toggle it.
     */
    private static final int CIRNO_DOUBLE_TAP_SPRINT_WINDOW_TICKS = 7;
    private static boolean cirnoForwardKeyWasDown = false;
    private static int cirnoLastForwardTapTick = Integer.MIN_VALUE;
    private static boolean cirnoGroundSprint = false;
    private static boolean cirnoMountSprintStateKnown = false;
    private static boolean cirnoMountSprintSent = false;

    public static void handlerFMLClientSetupEvent(FMLClientSetupEvent event) {
        net.zhaiji.cirno.compat.CuriosCompat.registerRenderer();
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
        boolean toggleVisibleKeyDown = CirnoKeybinds.TOGGLE_COMPANION_VISIBLE.isDown();
        if (toggleVisibleKeyDown) {
            toggleVisibleHoldTicks++;
            if (toggleVisibleHoldTicks == TOGGLE_VISIBLE_HOLD_THRESHOLD) {
                // Drain queued clicks so the tap-path doesn't fire on release
                while (CirnoKeybinds.TOGGLE_COMPANION_VISIBLE.consumeClick()) { /* drain */ }
                // Open the Quick-Select overlay
                mc.setScreen(new net.zhaiji.cirno.client.gui.CirnoQuickSelectScreen());
            }
        } else {
            if (toggleVisibleKeyWasDown && toggleVisibleHoldTicks < TOGGLE_VISIBLE_HOLD_THRESHOLD) {
                // Released before hold threshold → treat as a tap
                while (CirnoKeybinds.TOGGLE_COMPANION_VISIBLE.consumeClick()) { /* drain */ }
                if (toggleVisibilityCooldown > 0) {
                    player.displayClientMessage(
                            Component.literal("§c[Cirno] Wait " + (toggleVisibilityCooldown / 20 + 1) + "s..."),
                            true
                    );
                } else {
                    toggleVisibilityCooldown = TOGGLE_VISIBILITY_COOLDOWN_TICKS;
                    // H is the "all Cirnos" action, so it starts the same panel cooldown
                    // on every Cirno. A panel action only starts its own slot's cooldown.
                    CirnoQuickSelectScreen.startAllActionCooldowns();
                    PacketManager.sendToServer(new ToggleCompanionVisibilityPacket());
                }
            } else if (toggleVisibleKeyWasDown) {
                // Released after hold fired — drain anything leftover
                while (CirnoKeybinds.TOGGLE_COMPANION_VISIBLE.consumeClick()) { /* drain */ }
            }
            toggleVisibleHoldTicks = 0;
        }
        toggleVisibleKeyWasDown = toggleVisibleKeyDown;

        if (CirnoKeybinds.TOGGLE_TELEKINESIS_MODE.consumeClick()) {
            // hasTKAccessPublic alone only ever sees the Curios-equipped companion here:
            // a command-spawned ("/cirno add") Cirno's slot data lives exclusively in the
            // ServerPlayer's persistent NBT and is never mirrored onto the client's own
            // player object, so checking it directly on the client always reports "no
            // Cirno" for that case. commandCirnoTKAccess is the server-pushed substitute
            // for that half of the check (see SyncTKAccessPacket).
            if (!CirnoStateAccess.hasCurioPublic(player) && !commandCirnoTKAccess) {
                // no Cirno — G does nothing
            } else if (companionHidden && CirnoCommonConfig.requireCompanionForBuffs) {
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

        if (CirnoKeybinds.OPEN_CIRNO_GUI.consumeClick()) {
            PacketManager.sendToServer(new OpenCirnoGuiPacket());
        }

        // Saddle and/or mount Cirno: the keybind replaces what used to be shift+right-click to
        // saddle her and a plain right-click to mount her once saddled — both click-driven, both
        // ate into right-click for her normal maid interactions (see CirnoEntity#mobInteract).
        // Targets whatever CirnoEntity the crosshair is actually on (same idea as a normal
        // right-click interact) rather than "your" bound companion, so it works on any Cirno you
        // look at. Only bother sending the packet while actually holding a saddle and looking at
        // one; the server re-validates distance and every other precondition anyway.
        if (CirnoKeybinds.MOUNT_CIRNO.consumeClick()) {
            boolean holdingSaddle = player.getMainHandItem().is(net.minecraft.world.item.Items.SADDLE)
                    || player.getOffhandItem().is(net.minecraft.world.item.Items.SADDLE);
            if (holdingSaddle && mc.hitResult instanceof net.minecraft.world.phys.EntityHitResult entityHit
                    && entityHit.getEntity() instanceof CirnoEntity cirno) {
                PacketManager.sendToServer(
                        new net.zhaiji.cirno.network.server.packet.CirnoMountRequestPacket(cirno.getId()));
            }
        }

        // Cirno's mount controls need the rider sprint flag on the SERVER.  Vanilla's
        // double-W / sprint-key logic is not consistently allowed for custom vehicles,
        // so mirror the actual configurable W/Ctrl keys and send the normal vanilla sprint
        // command whenever that state changes.
        updateCirnoMountSprintInput(mc, player);

        // While a mob/projectile is held and right-click is still pressed, send the hold packet every tick
        if (heldMobUUID != null) {
            if (mc.options.keyUse.isDown()) {
                PacketManager.sendToServer(new net.zhaiji.cirno.network.server.packet.TelekinesisHoldPacket(
                        player.getEyePosition(),
                        player.getLookAngle()
                ));
            } else {
                // Right-click released — drop the mob/projectile
                heldMobUUID = null;
                PacketManager.sendToServer(new net.zhaiji.cirno.network.server.packet.TelekinesisControlMobPacket(
                        new java.util.UUID(0, 0) // sentinel: release
                ));
            }
        }

        // Princess-carry mount: right-click while holding a saddle and riding a Cirno dismounts.
        // Handled from raw input rather than a raycast/mobInteract click, since the camera is
        // usually not pointed at your own mount's hitbox while she's carrying you — this is most
        // useful mid-flight, as an alternative to letting go and sneaking down to the dismount key.
        if (player.getVehicle() instanceof CirnoEntity && mc.options.keyUse.consumeClick()) {
            if (player.getMainHandItem().is(net.minecraft.world.item.Items.SADDLE)
                    || player.getOffhandItem().is(net.minecraft.world.item.Items.SADDLE)) {
                PacketManager.sendToServer(new CirnoMountDismountPacket());
            }
        }

        // Projectile grab: when TK mode is on and right-click fires, scan ahead for a nearby projectile
        if (heldMobUUID == null && ClientTKState.isModeActive() && CirnoCommonConfig.telekinesisControlEnabled) {
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

    /** Keeps the server-side rider sprint flag aligned with Cirno's mount controls. */
    private static void updateCirnoMountSprintInput(Minecraft mc, Player player) {
        if (!(player instanceof LocalPlayer localPlayer)) {
            cirnoForwardKeyWasDown = false;
            cirnoLastForwardTapTick = Integer.MIN_VALUE;
            cirnoGroundSprint = false;
            cirnoMountSprintStateKnown = false;
            cirnoMountSprintSent = false;
            return;
        }

        if (!(player.getVehicle() instanceof CirnoEntity cirno)) {
            // Just dismounted (or was never mounted) while we still held the forced sprint flag
            // on. Vanilla's own key handling never turned sprinting ON in the first place — we
            // did, directly — so it has no reason to turn it OFF either once we stop touching it.
            // Left alone, the rider's isSprinting flag (and therefore Cirno's mirrored run pose
            // in the follow-mode tick logic) stays stuck true forever. Release it explicitly here.
            if (cirnoMountSprintStateKnown && cirnoMountSprintSent) {
                localPlayer.setSprinting(false);
                if (localPlayer.connection != null) {
                    localPlayer.connection.send(new ServerboundPlayerCommandPacket(
                            localPlayer, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                }
            }
            cirnoForwardKeyWasDown = false;
            cirnoLastForwardTapTick = Integer.MIN_VALUE;
            cirnoGroundSprint = false;
            cirnoMountSprintStateKnown = false;
            cirnoMountSprintSent = false;
            return;
        }

        boolean forwardDown = mc.options.keyUp.isDown();
        boolean forwardPressed = forwardDown && !cirnoForwardKeyWasDown;
        int tick = localPlayer.tickCount;

        if (forwardPressed) {
            if (tick - cirnoLastForwardTapTick <= CIRNO_DOUBLE_TAP_SPRINT_WINDOW_TICKS) {
                cirnoGroundSprint = true;
            }
            cirnoLastForwardTapTick = tick;
        }

        // A ground sprint lasts only while W is actually held.  This also prevents a
        // double-W sprint from leaking into the next movement burst.
        if (!forwardDown) {
            cirnoGroundSprint = false;
        }

        boolean flying = cirno.isMountFlying();
        boolean sprintWanted;
        if (flying) {
            // Flight's fast mode is intentionally Ctrl-only; double-W is a ground sprint gesture.
            sprintWanted = mc.options.keySprint.isDown();
        } else {
            // On the ground either normal sprint (Ctrl) or double-W may request sprint.
            sprintWanted = forwardDown
                    && (mc.options.keySprint.isDown() || cirnoGroundSprint);
        }

        boolean stateChanged = !cirnoMountSprintStateKnown
                || cirnoMountSprintSent != sprintWanted
                || localPlayer.isSprinting() != sprintWanted;
        if (stateChanged) {
            localPlayer.setSprinting(sprintWanted);
            if (localPlayer.connection != null) {
                localPlayer.connection.send(new ServerboundPlayerCommandPacket(
                        localPlayer,
                        sprintWanted
                                ? ServerboundPlayerCommandPacket.Action.START_SPRINTING
                                : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
            }
            cirnoMountSprintSent = sprintWanted;
            cirnoMountSprintStateKnown = true;
        }

        cirnoForwardKeyWasDown = forwardDown;
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

    /**
     * Called by {@link net.zhaiji.cirno.network.client.packet.SyncTKAccessPacket} —
     * server is the source of truth for whether a command-spawned Cirno currently
     * grants this player telekinesis access. No chat message here; this just quietly
     * keeps the G-key gate accurate, the same way {@code companionHidden} does for H.
     */
    public static void syncCommandCirnoTKAccess(boolean hasAccess) {
        commandCirnoTKAccess = hasAccess;
    }

    /** Client-side entity right-click: send telekinesis control packet if TK mode is active. */
    public static void handlerEntityInteractClient(PlayerInteractEvent.EntityInteract event) {
        if (!event.getEntity().level().isClientSide) return;
        if (!CirnoCommonConfig.telekinesisControlEnabled) return;
        if (!ClientTKState.isModeActive()) return;

        Entity target = event.getTarget();
        if (target instanceof Player) return;
        // Skip entities that open an inventory/GUI on right-click (e.g. TLM maids)
        if (target instanceof net.minecraft.world.entity.LivingEntity le
                && net.zhaiji.cirno.compat.CompatManager.isTLMLoad()
                && net.zhaiji.cirno.compat.TLMCompat.canRender(le)) return;

        heldMobUUID = target.getUUID();
        PacketManager.sendToServer(new TelekinesisControlMobPacket(target.getUUID()));
        event.setCanceled(true);
    }
}