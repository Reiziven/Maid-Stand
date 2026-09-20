package net.zhaiji.catburger.client.gui;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.zhaiji.catburger.CatBurger;
import net.zhaiji.catburger.config.CatBurgerClientConfig;
import net.zhaiji.catburger.config.CatBurgerCommonConfig;
import net.zhaiji.catburger.compat.CompatManager;
import net.zhaiji.catburger.network.PacketManager;
import net.zhaiji.catburger.network.client.packet.SlotSyncPacket;
import net.zhaiji.catburger.network.server.packet.OpenMaidInventoryPacket;
import net.zhaiji.catburger.network.server.packet.RequestSlotSyncPacket;
import net.zhaiji.catburger.network.server.packet.SlotActionPacket;
import net.zhaiji.catburger.network.server.packet.ToggleFollowOwnerPacket;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Cirno Quick-Select overlay.
 * Three styles, selected via {@code quickSelectStyle} in the client config:
 *
 *  CIRNO   — left-anchored vertical cards, Cirno ice-blue palette
 *  HAKUREI — left-anchored vertical cards, Reimu shrine-red/gold palette
 *  WHEEL   — transparent radial wheel, screen-centered, icons on a circle
 */
public class CirnoQuickSelectScreen extends Screen {

    // ── Textures ──────────────────────────────────────────────────────────────
    private static final ResourceLocation ICON_CURIO =
            new ResourceLocation(CatBurger.MOD_ID, "textures/gui/qs_icon_curio.png");
    private static final ResourceLocation ICON_SLOT  =
            new ResourceLocation(CatBurger.MOD_ID, "textures/gui/qs_icon_slot.png");

    // ── Card-layout constants (CIRNO / HAKUREI) ────────────────────────────
    private static final int CARD_W   = 210;
    private static final int CARD_H   = 38;
    private static final int CARD_GAP = 2;
    private static final int ICON_SZ  = 28;
    private static final int PAD      = 5;
    private static final int HINT_W   = 14;
    private static final int BTN_W    = 52;
    private static final int BTN_H    = 16;
    private static final int HEADER_H = 18;

    // ── Wheel-layout constants ────────────────────────────────────────────
    /** Radius of the icon ring (pixels, unscaled). */
    private static final int WHEEL_RADIUS    = 100;
    /** Icon tile size on the wheel. */
    private static final int WHEEL_ICON_SZ   = 32;
    /** Extra padding around icon in the tile. */
    private static final int WHEEL_TILE_PAD  = 5;
    /** Total tile size including padding. */
    private static final int WHEEL_TILE_SZ   = WHEEL_ICON_SZ + WHEEL_TILE_PAD * 2;
    /** Radius of the semi-transparent background disc. */
    private static final int WHEEL_BG_RADIUS = WHEEL_RADIUS + WHEEL_TILE_SZ;

    // ── State ─────────────────────────────────────────────────────────────────
    private boolean synced      = false;
    private boolean hasCurio    = false;
    private final List<SlotSyncPacket.SlotInfo> slotInfos = new ArrayList<>();
    private int hoveredCard     = -1;
    private int pendingCard     = -1;
    private float shimmerTick   = 0f;
    private boolean wheelMode   = false;
    private GuiTheme theme;

    /** Cooldown duration in milliseconds for Store / Summon / Add actions. */
    private static final long ACTION_COOLDOWN_MS = 3000L;
    /** Timestamp of the last triggered action (shared across panel opens). */
    private static long lastActionTime = 0L;

    /** Client-side mirror of cirnoFollowsOwner, updated via SyncFollowOwnerPacket. */
    private static boolean followOwnerState = false;

    /** Width of the Follow toggle button in the header (unscaled). */
    private static final int FOLLOW_BTN_W = 56;
    /** Height of the Follow toggle button in the header (unscaled). */
    private static final int FOLLOW_BTN_H = 12;

    /** Cached preview entity used for the entity-model icon in each card. Null when TLM is not loaded. */
    @Nullable
    private static EntityMaid previewEntity = null;

    public CirnoQuickSelectScreen() {
        super(Component.literal("Cirno Panel"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        String style = CatBurgerClientConfig.quickSelectStyle;
        wheelMode = GuiTheme.isWheel(style);
        theme     = GuiTheme.fromConfig(style); // used for wheel accent colours too
        // Seed follow state from live config (server-side value, same process on single-player)
        followOwnerState = CatBurgerCommonConfig.cirnoFollowsOwner;
        PacketManager.sendToServer(new RequestSlotSyncPacket());
    }

    /** Called by SyncFollowOwnerPacket when the server confirms the new follow state. */
    public static void syncFollowState(boolean following) {
        followOwnerState = following;
    }

    public void applySync(boolean hasCurio, List<SlotSyncPacket.SlotInfo> slots) {
        this.hasCurio    = hasCurio;
        this.slotInfos.clear();
        this.slotInfos.addAll(slots);
        this.synced      = true;
        this.pendingCard = -1;
    }

    // ── Top-level render dispatch ─────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        shimmerTick += partialTick * 0.04f;
        if (wheelMode) renderWheel(gfx, mouseX, mouseY, partialTick);
        else           renderCards(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  WHEEL RENDER PATH
    // ══════════════════════════════════════════════════════════════════════════

    private void renderWheel(GuiGraphics gfx, int mouseX, int mouseY, float pt) {
        float s = (float) CatBurgerClientConfig.quickSelectScale;
        int totalSlots = synced ? slotInfos.size() : 5;
        int cx = width  / 2;
        int cy = height / 2;

        // ── Detect hovered slice by angle from center ─────────────────────────
        hoveredCard = -1;
        if (synced) {
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double distSq = dx * dx + dy * dy;
            // Only register hover when mouse is away from the dead-zone at center
            int deadZone  = (int) (WHEEL_RADIUS * s * 0.65f); // matches hole radius
            int outerEdge = (int) ((WHEEL_RADIUS + WHEEL_TILE_SZ) * s);
            if (distSq > deadZone * deadZone && distSq < (long) outerEdge * outerEdge) {
                double mouseAngle = Math.toDegrees(Math.atan2(dy, dx));
                if (mouseAngle < 0) mouseAngle += 360;
                double sliceAngle = 360.0 / totalSlots;
                // First icon sits at the top (-90°), going clockwise
                double firstAngle = -90.0;
                for (int i = 0; i < totalSlots; i++) {
                    double slotCenter = (firstAngle + i * sliceAngle + 360) % 360;
                    double diff = ((mouseAngle - slotCenter) % 360 + 360) % 360;
                    if (diff > 180) diff = 360 - diff;
                    if (diff <= sliceAngle / 2.0) { hoveredCard = i; break; }
                }
            }
        }

        // ── Background donut — outer disc minus transparent center hole ──────
        int bgR    = (int) (WHEEL_BG_RADIUS * s);
        int holeR  = (int) (WHEEL_RADIUS * s * 0.65f); // hole radius = 65% of icon ring
        drawDonut(gfx, cx, cy, bgR, holeR, 0x55000000);
        drawCircleOutline(gfx, cx, cy, bgR,   0x44FFFFFF);
        drawCircleOutline(gfx, cx, cy, holeR, 0x33FFFFFF);

        // ── Center info — shown just below the hole when hovering ────────────
        if (!synced) {
            long pulse = (System.currentTimeMillis() / 400) % 4;
            String loadStr = "Loading" + "·".repeat((int) pulse);
            int lw = font.width(loadStr);
            gfx.drawString(font, loadStr, cx - lw / 2, cy - 4, 0x88AAAAAA, false);
        } else if (hoveredCard >= 0 && hoveredCard < slotInfos.size()) {
            SlotSyncPacket.SlotInfo info = slotInfos.get(hoveredCard);
            String label   = info.isCurio() ? "Curio" : "Slot " + info.index();
            String stateTx = stateLabel(info);
            String action  = actionLabel(info);
            int lw = font.width(label);
            int sw = font.width(stateTx);
            int aw = font.width(action);
            gfx.drawString(font, label,   cx - lw / 2, cy - 14, 0xFFFFFFFF, true);
            gfx.drawString(font, stateTx, cx - sw / 2, cy - 4,  stateColorWheel(info), true);
            gfx.drawString(font, action,  cx - aw / 2, cy + 6,  0xFFDDEEFF, true);
        } else {
            // Idle: show title + follow toggle hint
            String title = "Cirno Panel";
            int tw = font.width(title);
            gfx.drawString(font, title, cx - tw / 2, cy - 10, 0xAADDEEFF, false);
            // Follow mini-toggle in center of wheel
            String followStr = followOwnerState ? "§a▶ Follow ON" : "§5▶ Follow OFF";
            String followPlain = followOwnerState ? "▶ Follow ON" : "▶ Follow OFF";
            int fw = font.width(followPlain);
            // Detect hover for the follow text
            boolean followHov = Math.abs(mouseX - cx) < fw / 2 + 4 && Math.abs(mouseY - (cy + 6)) < 6;
            int followBg = followHov ? (followOwnerState ? 0xCC1A6644 : 0xCC442244) : 0x66000020;
            fill(gfx, cx - fw / 2 - 3, cy + 2, fw + 6, 12, followBg);
            gfx.drawString(font, followStr, cx - fw / 2, cy + 4, followOwnerState ? 0xFF88FFCC : 0xFFCC99FF, false);
        }

        // ── Slot icons arranged on the ring ───────────────────────────────────
        double sliceAngle = 360.0 / totalSlots;
        double startAngle = -90.0; // first slot at top

        for (int i = 0; i < totalSlots; i++) {
            double angleDeg = startAngle + i * sliceAngle;
            double angleRad = Math.toRadians(angleDeg);
            int ix = cx + (int) (Math.cos(angleRad) * WHEEL_RADIUS * s);
            int iy = cy + (int) (Math.sin(angleRad) * WHEEL_RADIUS * s);

            boolean hovered = (hoveredCard == i);
            boolean pending = (pendingCard == i);

            if (!synced || pending) {
                drawWheelTilePlaceholder(gfx, ix, iy, hovered, s);
            } else {
                drawWheelTile(gfx, ix, iy, slotInfos.get(i), hovered, i + 1, s);
            }
        }
    }

    private void drawWheelTile(GuiGraphics gfx, int cx, int cy,
                                SlotSyncPacket.SlotInfo info, boolean hovered,
                                int num, float s) {
        int ts = (int) (WHEEL_TILE_SZ * s);
        int is = (int) (WHEEL_ICON_SZ * s);
        int tileX = cx - ts / 2;
        int tileY = cy - ts / 2;
        int iconX = cx - is / 2;
        int iconY = cy - is / 2;

        // Tile background — slight transparency, more opaque when hovered
        int tileBg = hovered ? 0xCC1A1A2E : 0x99111122;
        gfx.fill(tileX, tileY, tileX + ts, tileY + ts, tileBg);

        // Border: double ring like screenshot — outer light blue, inner colored by state
        int outerBorder = hovered ? 0xFFBBDDFF : 0x99AACCEE;
        int innerBorder = stateColorWheel(info);
        drawBorder(gfx, tileX,     tileY,     ts,     ts,     outerBorder);
        drawBorder(gfx, tileX + 1, tileY + 1, ts - 2, ts - 2, innerBorder);

        // Icon — tinted by state
        float[] tint = stateWheelTint(info);
        RenderSystem.setShaderColor(tint[0], tint[1], tint[2], hovered ? 1.0f : 0.85f);
        ResourceLocation tex = info.isCurio() ? ICON_CURIO : ICON_SLOT;
        gfx.blit(tex, iconX, iconY, 0, 0, is, is, is, is);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // State pip — tiny square, bottom-right of tile
        int pipSize = Math.max(3, (int) (4 * s));
        int pipX = tileX + ts - pipSize - 1;
        int pipY = tileY + ts - pipSize - 1;
        gfx.fill(pipX, pipY, pipX + pipSize, pipY + pipSize, stateColorWheel(info));

        // Number hint — top-left of tile, small
        gfx.drawString(font, String.valueOf(num), tileX + 2, tileY + 2, 0xCCDDEEFF, false);
    }

    private void drawWheelTilePlaceholder(GuiGraphics gfx, int cx, int cy,
                                           boolean hovered, float s) {
        int ts = (int) (WHEEL_TILE_SZ * s);
        int tileX = cx - ts / 2;
        int tileY = cy - ts / 2;
        gfx.fill(tileX, tileY, tileX + ts, tileY + ts, 0x66111122);
        drawBorder(gfx, tileX, tileY, ts, ts, 0x44AACCEE);
    }

    // ── Donut (filled ring, transparent center hole) ──────────────────────────
    private static void drawDonut(GuiGraphics gfx, int cx, int cy, int outerR, int innerR, int color) {
        for (int dy = -outerR; dy <= outerR; dy++) {
            int outerHw = (int) Math.sqrt((double) outerR * outerR - (double) dy * dy);
            int innerHw = (dy >= -innerR && dy <= innerR)
                    ? (int) Math.sqrt((double) innerR * innerR - (double) dy * dy)
                    : 0;
            if (innerHw > 0) {
                // left band
                gfx.fill(cx - outerHw, cy + dy, cx - innerHw, cy + dy + 1, color);
                // right band
                gfx.fill(cx + innerHw, cy + dy, cx + outerHw, cy + dy + 1, color);
            } else {
                gfx.fill(cx - outerHw, cy + dy, cx + outerHw, cy + dy + 1, color);
            }
        }
    }

    // ── Circle outline (scanline edge) ────────────────────────────────────────
    private static void drawCircleOutline(GuiGraphics gfx, int cx, int cy, int r, int color) {
        int thickness = 1;
        for (int dy = -r; dy <= r; dy++) {
            int hw  = (int) Math.sqrt((double) r * r - (double) dy * dy);
            int hw2 = (int) Math.sqrt(Math.max(0.0, (double)(r - thickness) * (r - thickness) - (double) dy * dy));
            // left edge
            gfx.fill(cx - hw, cy + dy, cx - hw2, cy + dy + 1, color);
            // right edge
            gfx.fill(cx + hw2, cy + dy, cx + hw, cy + dy + 1, color);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CARD RENDER PATH  (CIRNO / HAKUREI)
    // ══════════════════════════════════════════════════════════════════════════

    private void renderCards(GuiGraphics gfx, int mouseX, int mouseY, float pt) {
        final GuiTheme t = theme;
        float s = (float) CatBurgerClientConfig.quickSelectScale;
        int totalCards = synced ? slotInfos.size() : 5;

        int panelW       = (int) (CARD_W * s);
        int cardsH       = (int) ((totalCards * CARD_H + (totalCards - 1) * CARD_GAP) * s);
        int panelH       = (int) ((HEADER_H + 4) * s) + cardsH;
        int outerPad     = (int) (7 * s);
        int startX       = outerPad;
        int startY       = (height - panelH) / 2;
        int cardsOriginY = startY + (int) ((HEADER_H + 4) * s);

        hoveredCard = -1;
        for (int i = 0; i < totalCards; i++) {
            int cy = cardsOriginY + (int) (i * (CARD_H + CARD_GAP) * s);
            int ch = (int) (CARD_H * s);
            if (mouseX >= startX && mouseX < startX + panelW && mouseY >= cy && mouseY < cy + ch)
                hoveredCard = i;
        }

        int bx = startX - outerPad, by = startY - outerPad;
        int bw = panelW + outerPad * 2, bh = panelH + outerPad * 2;

        fill(gfx, bx - 2, by - 2, bw + 4, bh + 4, t.outerGlow());
        fill(gfx, bx, by, bw, bh, t.panelBg());
        drawBorder(gfx, bx,     by,     bw,     bh,     t.borderOuter());
        drawBorder(gfx, bx + 2, by + 2, bw - 4, bh - 4, t.borderInner());

        renderHeader(gfx, startX, startY, panelW, s, t);
        int divY = startY + (int) (HEADER_H * s);
        fill(gfx, startX, divY, panelW, 1, t.borderOuter());
        fill(gfx, startX, divY + 1, panelW, 1, 0x44FFFFFF);

        gfx.pose().pushPose();
        gfx.pose().translate(startX, cardsOriginY, 0);
        gfx.pose().scale(s, s, 1f);
        for (int i = 0; i < totalCards; i++) {
            int cy = i * (CARD_H + CARD_GAP);
            boolean hov  = (hoveredCard == i);
            boolean pend = (pendingCard == i);
            if (!synced || pend) drawLoadingRow(gfx, cy, hov, t);
            else                 drawSlotRow(gfx, cy, slotInfos.get(i), hov, i, t, startX, cardsOriginY, s);
        }
        gfx.pose().popPose();

        float flicker   = 0.65f + 0.35f * (float) Math.sin(shimmerTick * 2.5);
        int cornerAlpha = (int) (flicker * 0xFF) << 24;
        int cornerCol   = (cornerAlpha & 0xFF000000) | (t.borderOuter() & 0x00FFFFFF);
        gfx.drawString(font, t.decoCorner(), bx + 2,       by + 2,       cornerCol, false);
        gfx.drawString(font, t.decoCorner(), bx + bw - 10, by + 2,       cornerCol, false);
        gfx.drawString(font, t.decoCorner(), bx + 2,       by + bh - 10, cornerCol, false);
        gfx.drawString(font, t.decoCorner(), bx + bw - 10, by + bh - 10, cornerCol, false);
    }

    private void renderHeader(GuiGraphics gfx, int x, int y, int panelW, float s, GuiTheme t) {
        fill(gfx, x, y, panelW, (int) (HEADER_H * s), t.panelBgLite());
        int shimmerW = (int) (30 * s);
        int shimmerX = x + (int) ((shimmerTick % 1.0f) * panelW);
        fill(gfx, shimmerX - shimmerW / 2, y, shimmerW, (int) (HEADER_H * s), t.shimmerColor());
        String title = t.decoHeader() + " Cirno Panel " + t.decoHeader();
        int tw = font.width(title);
        gfx.drawString(font, title, x + (int) ((panelW / s - tw) / 2 * s),
                y + (int) ((HEADER_H * s - 8) / 2), t.colTitle(), false);

        // ── Follow toggle button (right side of header) ────────────────────
        int btnW = (int) (FOLLOW_BTN_W * s);
        int btnH = (int) (FOLLOW_BTN_H * s);
        int btnX = x + panelW - btnW - (int) (PAD * s);
        int btnY = y + ((int) (HEADER_H * s) - btnH) / 2;
        boolean followHovered = isFollowBtnHovered(btnX, btnY, btnW, btnH);
        int btnFill = followOwnerState
                ? (followHovered ? 0xCC1A6644 : 0xAA115533)   // ON  — green
                : (followHovered ? 0xCC442244 : 0xAA331133);  // OFF — muted purple
        int btnBorder = followOwnerState ? 0xFF44DD88 : 0xFF8855AA;
        fill(gfx, btnX, btnY, btnW, btnH, btnFill);
        drawBorder(gfx, btnX, btnY, btnW, btnH, btnBorder);
        drawBorder(gfx, btnX + 1, btnY + 1, btnW - 2, btnH - 2, 0x33FFFFFF);
        String btnLabel = followOwnerState ? "Follow ON" : "Follow OFF";
        int lw = font.width(btnLabel);
        int btnTextCol = followOwnerState ? 0xFF88FFCC : 0xFFCC99FF;
        gfx.drawString(font, btnLabel, btnX + (btnW - lw) / 2, btnY + (btnH - 8) / 2, btnTextCol, false);
    }

    /** Returns the screen-space bounds of the Follow button for hit-testing (unscaled coords). */
    private int[] followBtnBoundsScreen(float s, int x, int y, int panelW) {
        int btnW = (int) (FOLLOW_BTN_W * s);
        int btnH = (int) (FOLLOW_BTN_H * s);
        int btnX = x + panelW - btnW - (int) (PAD * s);
        int btnY = y + ((int) (HEADER_H * s) - btnH) / 2;
        return new int[]{ btnX, btnY, btnW, btnH };
    }

    private boolean isFollowBtnHovered(int btnX, int btnY, int btnW, int btnH) {
        Minecraft mc = Minecraft.getInstance();
        double mx = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()  / mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        return mx >= btnX && mx < btnX + btnW && my >= btnY && my < btnY + btnH;
    }

    private void drawSlotRow(GuiGraphics gfx, int y, SlotSyncPacket.SlotInfo info,
                             boolean hovered, int idx, GuiTheme t,
                             int originScreenX, int originScreenY, float s) {
        fill(gfx, 0, y, CARD_W, CARD_H, hovered ? t.cardHover() : t.cardNormal());
        if (hovered) {
            fill(gfx, 0, y, 3, CARD_H, t.accentBar());
            drawBorder(gfx, 0, y, CARD_W, CARD_H, t.borderGlow());
        } else {
            fill(gfx, 0, y, 1, CARD_H, t.borderInner());
            drawBorder(gfx, 0, y, CARD_W, CARD_H, 0x44334477);
        }

        String bullet  = info.isCurio() ? t.decoBulletCurio() : t.decoBulletSlot();
        int bulletCol  = info.isCurio() ? t.borderOuter() : stateColor(info, t);
        gfx.drawString(font, bullet, PAD, y + (CARD_H - 8) / 2, bulletCol, false);
        gfx.drawString(font, String.valueOf(idx + 1), PAD + 8, y + (CARD_H - 8) / 2, t.colHint(), false);

        int iconX = HINT_W + PAD + 6, iconY = y + (CARD_H - ICON_SZ) / 2;
        fill(gfx, iconX - 2, iconY - 2, ICON_SZ + 4, ICON_SZ + 4, 0x33000022);
        fill(gfx, iconX - 1, iconY - 1, ICON_SZ + 2, ICON_SZ + 2, 0x22FFFFFF);

        EntityMaid preview = (info.state() != SlotSyncPacket.SlotState.EMPTY) ? getPreviewEntity(info) : null;
        if (preview != null) {
            // Convert card-local icon center to absolute screen coords for renderEntityInInventory
            double rot = (System.currentTimeMillis() / 30.0) % 360.0;
            Quaternionf entityPose = new Quaternionf().rotateZ((float) Math.PI);
            entityPose.mul(new Quaternionf().rotateY((float) Math.toRadians(rot)));
            int screenCX = (int) (originScreenX + (iconX + ICON_SZ / 2f) * s);
            int screenBY = (int) (originScreenY + (y + CARD_H - 2) * s);
            int entityScale = Math.max(1, (int) (14 * s));
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            // renderEntityInInventory uses its own PoseStack from root — must use screen coords
            // Temporarily reset the GuiGraphics pose to screen-root so entity renders correctly
            gfx.pose().pushPose();
            gfx.pose().setIdentity();
            InventoryScreen.renderEntityInInventory(gfx, screenCX, screenBY, entityScale, entityPose, null, preview);
            gfx.pose().popPose();
        } else {
            float[] tint = stateTint(info, t);
            RenderSystem.setShaderColor(tint[0], tint[1], tint[2], 0.92f);
            gfx.blit(info.isCurio() ? ICON_CURIO : ICON_SLOT, iconX, iconY, 0, 0, ICON_SZ, ICON_SZ, ICON_SZ, ICON_SZ);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        int pipX = iconX + ICON_SZ - 4, pipY = iconY + ICON_SZ - 4;
        fill(gfx, pipX - 1, pipY - 1, 6, 6, 0xFF000020);
        fill(gfx, pipX, pipY, 4, 4, stateColor(info, t));

        int textX = iconX + ICON_SZ + 6;
        String slotLabel = info.isCurio() ? "Curio" : slotDisplayName(info);
        gfx.drawString(font, slotLabel, textX, y + 7, t.colLabel(), false);
        gfx.drawString(font, t.decoStateDot() + stateLabel(info), textX, y + 17, stateColor(info, t), false);

        if (!(info.isCurio() && info.state() == SlotSyncPacket.SlotState.EMPTY)) {
            // Hide "+ Add" button in survival and adventure mode
            boolean canAdd = info.state() != SlotSyncPacket.SlotState.EMPTY
                    || isCreativeOrSpectator();
            if (!canAdd) return;
            long elapsed   = System.currentTimeMillis() - lastActionTime;
            boolean onCooldown = elapsed < ACTION_COOLDOWN_MS;
            String btnText;
            if (onCooldown) {
                long remaining = (ACTION_COOLDOWN_MS - elapsed + 999) / 1000; // ceiling seconds
                btnText = remaining + "s";
            } else {
                btnText = switch (info.state()) {
                    case SUMMONED -> "Store";
                    case STORED   -> "Summon";
                    case EMPTY    -> "+ Add";
                };
            }
            int btnFill;
            if (onCooldown) {
                btnFill = 0x66333344;
            } else {
                btnFill = switch (info.state()) {
                    case SUMMONED -> hovered ? t.btnStoreHov()  : t.btnStore();
                    case STORED   -> hovered ? t.btnSummonHov() : t.btnSummon();
                    case EMPTY    -> hovered ? t.btnAddHov()    : t.btnAdd();
                };
            }
            int btnX = CARD_W - BTN_W - PAD, btnY = y + (CARD_H - BTN_H) / 2;
            fill(gfx, btnX, btnY, BTN_W, BTN_H, btnFill);
            drawBorder(gfx, btnX,     btnY,     BTN_W,     BTN_H,     onCooldown ? 0x44556677 : t.btnBorder());
            drawBorder(gfx, btnX + 1, btnY + 1, BTN_W - 2, BTN_H - 2, 0x33FFFFFF);
            int tw = font.width(btnText);
            int btnTextColor = onCooldown ? 0x99AABBCC : t.btnText();
            gfx.drawString(font, btnText, btnX + (BTN_W - tw) / 2, btnY + (BTN_H - 8) / 2, btnTextColor, false);
        }
    }

    private void drawLoadingRow(GuiGraphics gfx, int y, boolean hovered, GuiTheme t) {
        fill(gfx, 0, y, CARD_W, CARD_H, 0x880A0D20);
        drawBorder(gfx, 0, y, CARD_W, CARD_H, 0x33334477);
        long pulse = (System.currentTimeMillis() / 400) % 4;
        gfx.drawString(font, t.decoCorner() + " Loading" + "·".repeat((int) pulse),
                HINT_W + PAD + ICON_SZ + 10, y + (CARD_H - 8) / 2, t.colHint(), false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !synced) return super.mouseClicked(mx, my, button);
        if (wheelMode) {
            // Check follow toggle click in wheel center
            if (hoveredCard < 0) {
                int cx = width / 2, cy = height / 2;
                String followPlain = followOwnerState ? "▶ Follow ON" : "▶ Follow OFF";
                int fw = font.width(followPlain);
                if (Math.abs(mx - cx) < fw / 2 + 4 && Math.abs(my - (cy + 6)) < 6) {
                    PacketManager.sendToServer(new ToggleFollowOwnerPacket());
                    followOwnerState = !followOwnerState;
                    return true;
                }
            }
            // Click on hovered slice → open maid inventory
            if (hoveredCard >= 0) { openMaidInventory(hoveredCard); return true; }
            return super.mouseClicked(mx, my, button);
        }
        // Card mode hit-test
        float s = (float) CatBurgerClientConfig.quickSelectScale;
        int totalCards   = slotInfos.size();
        int cardsH       = (int) ((totalCards * CARD_H + (totalCards - 1) * CARD_GAP) * s);
        int panelH       = (int) ((HEADER_H + 4) * s) + cardsH;
        int startX       = (int) (7 * s);
        int cardsOriginY = (height - panelH) / 2 + (int) ((HEADER_H + 4) * s);
        int panelW       = (int) (CARD_W * s);
        int startY       = (height - panelH) / 2;

        // Check Follow button click (in header)
        int[] fb = followBtnBoundsScreen(s, startX, startY, panelW);
        if (mx >= fb[0] && mx < fb[0] + fb[2] && my >= fb[1] && my < fb[1] + fb[3]) {
            PacketManager.sendToServer(new ToggleFollowOwnerPacket());
            // Optimistic local flip so button reacts immediately
            followOwnerState = !followOwnerState;
            return true;
        }

        for (int i = 0; i < totalCards; i++) {
            int cy = cardsOriginY + (int) (i * (CARD_H + CARD_GAP) * s);
            int ch = (int) (CARD_H * s);
            if (mx < startX || mx >= startX + panelW || my < cy || my >= cy + ch) continue;
            // Check if click landed on the action button
            SlotSyncPacket.SlotInfo info = slotInfos.get(i);
            boolean hasBtn = !(info.isCurio() && info.state() == SlotSyncPacket.SlotState.EMPTY);
            if (hasBtn) {
                int btnX    = (int) ((CARD_W - BTN_W - PAD) * s);
                int btnYOff = (int) (((CARD_H - BTN_H) / 2.0) * s);
                if (mx >= startX + btnX && mx < startX + btnX + (int) (BTN_W * s)
                        && my >= cy + btnYOff && my < cy + btnYOff + (int) (BTN_H * s)) {
                    triggerAction(i); return true;
                }
            }
            // Anywhere else on the card → open maid inventory
            openMaidInventory(i); return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5 && synced) {
            int idx = keyCode - GLFW.GLFW_KEY_1;
            if (idx < slotInfos.size()) { triggerAction(idx); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openMaidInventory(int idx) {
        if (!synced || idx < 0 || idx >= slotInfos.size()) return;
        SlotSyncPacket.SlotInfo info = slotInfos.get(idx);
        // Only open inventory for a summoned maid
        if (info.state() != SlotSyncPacket.SlotState.SUMMONED) return;
        int slotIndex = info.isCurio() ? 0 : info.index();
        PacketManager.sendToServer(new OpenMaidInventoryPacket(slotIndex));
        onClose();
    }

    private void triggerAction(int idx) {
        if (!synced || pendingCard >= 0 || idx < 0 || idx >= slotInfos.size()) return;
        // Enforce cooldown for Store / Summon / Add
        if (System.currentTimeMillis() - lastActionTime < ACTION_COOLDOWN_MS) return;
        SlotSyncPacket.SlotInfo info = slotInfos.get(idx);
        if (info.isCurio() && info.state() == SlotSyncPacket.SlotState.EMPTY) return;
        SlotActionPacket.Action action = switch (info.state()) {
            case SUMMONED -> SlotActionPacket.Action.STORE;
            case STORED   -> SlotActionPacket.Action.SUMMON;
            case EMPTY    -> SlotActionPacket.Action.ADD;
        };
        PacketManager.sendToServer(new SlotActionPacket(info.isCurio() ? 0 : info.index(), action));
        lastActionTime = System.currentTimeMillis();
        pendingCard = idx;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the YSM model name, TLM model name, or "Slot N" as fallback, from per-slot packet data. */
    private static String slotDisplayName(SlotSyncPacket.SlotInfo info) {
        // YSM model takes priority
        if (!info.ysmModelId().isEmpty()) return info.ysmModelId();
        // TLM / geckolib model name
        if (!info.modelId().isEmpty()) {
            // modelId is a resource location string like "namespace:path" — show just the path part
            String id = info.modelId();
            int colon = id.lastIndexOf(':');
            return colon >= 0 ? id.substring(colon + 1) : id;
        }
        return "Slot " + info.index();
    }

    private static String stateLabel(SlotSyncPacket.SlotInfo info) {
        return switch (info.state()) {
            case SUMMONED -> "Summoned";
            case STORED   -> "Stored";
            case EMPTY    -> "Empty";
        };
    }

    private static String actionLabel(SlotSyncPacket.SlotInfo info) {
        return switch (info.state()) {
            case SUMMONED -> "[ Store ]";
            case STORED   -> "[ Summon ]";
            case EMPTY    -> "[ + Add ]";
        };
    }

    private static int stateColor(SlotSyncPacket.SlotInfo info, GuiTheme t) {
        return switch (info.state()) {
            case SUMMONED -> t.colSummoned();
            case STORED   -> t.colStored();
            case EMPTY    -> t.colEmpty();
        };
    }

    /** Wheel uses fixed colours independent of theme. */
    private static int stateColorWheel(SlotSyncPacket.SlotInfo info) {
        return switch (info.state()) {
            case SUMMONED -> 0xFF66FFBB;
            case STORED   -> 0xFFFFDD44;
            case EMPTY    -> 0xFF8899AA;
        };
    }

    private static float[] stateTint(SlotSyncPacket.SlotInfo info, GuiTheme t) {
        return switch (info.state()) {
            case SUMMONED -> t.tintSummoned();
            case STORED   -> t.tintStored();
            case EMPTY    -> t.tintEmpty();
        };
    }

    private static float[] stateWheelTint(SlotSyncPacket.SlotInfo info) {
        return switch (info.state()) {
            case SUMMONED -> new float[]{ 0.6f, 1.0f, 0.75f };
            case STORED   -> new float[]{ 1.0f, 0.9f, 0.4f  };
            case EMPTY    -> new float[]{ 0.5f, 0.5f, 0.6f  };
        };
    }

    private static void fill(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x, y, x + w, y + h, color);
    }

    /**
     * Returns a reusable preview EntityMaid configured with the model from the given SlotInfo.
     * Returns null if TLM is not loaded or the world is unavailable.
     */
    @Nullable
    private static EntityMaid getPreviewEntity(SlotSyncPacket.SlotInfo info) {
        if (!CompatManager.isTLMLoad()) return null;
        Level world = Minecraft.getInstance().level;
        if (world == null) return null;
        if (previewEntity == null) {
            previewEntity = new EntityMaid(world);
        }
        // Apply TLM model ID if present
        if (!info.modelId().isEmpty()) {
            previewEntity.setModelId(info.modelId());
        }
        // Apply YSM model if configured
        if (!info.ysmModelId().isEmpty() && CompatManager.isYSMLoad()) {
            previewEntity.setIsYsmModel(true);
            previewEntity.setYsmModel(info.ysmModelId(), "", Component.literal(""));
        } else {
            previewEntity.setIsYsmModel(false);
        }
        return previewEntity;
    }

    private static boolean isCreativeOrSpectator() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null) return false;
        GameType gm = mc.gameMode.getPlayerMode();
        return gm == GameType.CREATIVE || gm == GameType.SPECTATOR;
    }


    private static void drawBorder(GuiGraphics gfx, int x, int y, int w, int h, int color) {
        gfx.fill(x,         y,         x + w, y + 1,     color);
        gfx.fill(x,         y + h - 1, x + w, y + h,     color);
        gfx.fill(x,         y + 1,     x + 1, y + h - 1, color);
        gfx.fill(x + w - 1, y + 1,     x + w, y + h - 1, color);
    }
}
