package net.zhaiji.cirno.compat;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.EntityCapability;
import net.zhaiji.cirno.entity.CirnoEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridges Cirno's normal passenger relationship to YSM's built-in carry
 * animations, using the same player-capability mechanism as the TLM
 * "Carry Owner" addon.
 *
 * <p>This class has no dependency on the Carry On mod. The animation IDs are
 * YSM animation names; the {@code carryon:} namespace belongs to YSM's
 * carry-animation library.</p>
 *
 * <p>TODO(neoforge-port-ysm): the obfuscated class/field/method names below are from the 1.20.1 Forge
 * build of YSM. The 1.21.1 NeoForge build has different names and no longer uses Forge capabilities
 * (it will expose either a NeoForge EntityCapability or a data attachment). Both are handled
 * generically in {@link #playPlayerAnimation}; only the three constants need updating
 * (decompile the YSM jar and look at what its player-animation hook is). Until then the bridge
 * silently stays inactive - riding itself still works.</p>
 *
 * <p>YSM is optional, so its obfuscated capability API is accessed lazily via
 * reflection. If YSM is absent or its internals change, Cirno's riding still
 * works and the animation bridge simply becomes inactive.</p>
 */
public final class YsmCarryAnimationCompat {
    private static final int REFRESH_INTERVAL_TICKS = 20;

    private static final String YSM_PROVIDER_CLASS =
            "com.elfmcys.yesstevemodel.Oo00o0OooOOo0ooOoo0oO0o0";
    private static final String YSM_CAPABILITY_FIELD =
            "Oo0Oo0o00O00Oo0OOoOOoooo";
    private static final String YSM_PLAY_METHOD =
            "Oo0Oo0o00O00Oo0OOoOOoooo";

    /** Either a NeoForge EntityCapability or an AttachmentType, depending on the YSM build. */
    private static Object ysmCapability;
    private static Method playAnimationMethod;
    private static boolean apiResolved;

    private YsmCarryAnimationCompat() {
    }

    /** Starts the YSM carry animations when the passenger is added. */
    public static void start(CirnoEntity cirno, ServerPlayer player) {
        startCarrier(cirno);
        playPlayerAnimation(player, "carryon:princess");
    }

    /**
     * Refreshes the carrier-side clip periodically while grounded. The rider's own
     * {@code carryon:princess} animation is intentionally NOT re-sent here: it was already set once
     * in {@link #start}, YSM keeps it playing on its own, and re-triggering it every
     * {@value #REFRESH_INTERVAL_TICKS} ticks restarted the clip from frame zero, which is what caused
     * the periodic animation "hiccup" (and the position jump some players read as a carry-position
     * bug). The carrier clip is intentionally not replayed during Cirno's flight state so it cannot
     * override YSM's automatic fly state.
     */
    public static void tick(CirnoEntity cirno, ServerPlayer player) {
        if (cirno.tickCount % REFRESH_INTERVAL_TICKS != 0) {
            return;
        }

        if (!cirno.isMountFlying()) {
            startCarrier(cirno);
        }
    }

    /** Starts only the carrier-side carry animation. */
    public static void startCarrier(CirnoEntity cirno) {
        if (!cirno.isYsmModel()) {
            return;
        }

        try {
            cirno.playRouletteAnim("carryon:entity");
        } catch (RuntimeException ignored) {
            // Optional animation compatibility; riding itself must survive.
        }
    }

    /** Stops only the carrier-side carry animation so YSM's automatic fly state can take over. */
    public static void stopCarrier(CirnoEntity cirno) {
        if (!cirno.isYsmModel()) {
            return;
        }

        try {
            cirno.stopRouletteAnim();
        } catch (RuntimeException ignored) {
            // Optional animation compatibility; riding itself must survive.
        }
    }

    /** Clears both YSM carry animations when the rider dismounts. */
    public static void stop(CirnoEntity cirno, ServerPlayer player) {
        try {
            if (cirno.isYsmModel()) {
                cirno.stopRouletteAnim();
            }
        } catch (RuntimeException ignored) {
            // Optional animation compatibility.
        }

        playPlayerAnimation(player, "");
    }

    private static void playPlayerAnimation(ServerPlayer player, String animation) {
        if (!resolveApi()) {
            return;
        }

        try {
            Object value = null;
            if (ysmCapability instanceof AttachmentType<?> attachment) {
                value = player.getData(attachment);
            } else if (ysmCapability instanceof EntityCapability<?, ?> capability) {
                @SuppressWarnings("unchecked")
                EntityCapability<?, Void> voidCap = (EntityCapability<?, Void>) capability;
                value = player.getCapability(voidCap);
            }
            if (value == null) return;
            try {
                ensurePlayMethod(value);
                playAnimationMethod.invoke(value, player, animation);
            } catch (ReflectiveOperationException ignored) {
                // YSM changed its internal implementation.
            }
        } catch (RuntimeException ignored) {
            // Optional YSM integration must never break the riding code.
        }
    }

    /** Resolve the YSM capability once, on first use. */
    private static synchronized boolean resolveApi() {
        if (apiResolved) {
            return ysmCapability != null && playAnimationMethod != null;
        }
        apiResolved = true;

        try {
            Class<?> providerClass = Class.forName(YSM_PROVIDER_CLASS);
            Field capabilityField = providerClass.getField(YSM_CAPABILITY_FIELD);
            ysmCapability = capabilityField.get(null);

            // The concrete capability object is only available from a player,
            // so resolve the invocation method when playPlayerAnimation runs.
            return ysmCapability != null;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            ysmCapability = null;
            return false;
        }
    }

    private static void ensurePlayMethod(Object capabilityValue) throws ReflectiveOperationException {
        if (playAnimationMethod == null) {
            playAnimationMethod = capabilityValue.getClass().getMethod(
                    YSM_PLAY_METHOD, ServerPlayer.class, String.class);
        }
    }
}
