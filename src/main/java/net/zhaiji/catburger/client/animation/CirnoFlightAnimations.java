package net.zhaiji.catburger.client.animation;

import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.zhaiji.catburger.entity.CirnoEntity;
import net.zhaiji.catburger.entity.CirnoFlightState;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BiPredicate;

/**
 * Registers Cirno's flight state into OpenYSM's Touhou Little Maid animation
 * pipeline. This is the same pipeline OpenYSM uses for the built-in maid
 * "run" animation; it is NOT the generic player ctrl.fly predicate.
 *
 * The important OpenYSM 2.6.6.6 detail is:
 *   MaidAnimation.registerAnimationStates()
 *       -> TouhouMaidAnimationPredicate.registerHandler(...)
 *
 * The built-in maid states are:
 *   priority 0: death/sleep/swim/ladder
 *   priority 1: sit
 *   priority 2: jump
 *   priority 3: run/walk
 *
 * We register Cirno "fly" at priority 1, so it is selected before the
 * built-in maid jump/fall state (priority 2), but only when the entity is
 * specifically a Cirno and its synced flight state is FLY.
 *
 * Reflection is intentional: YSM is an optional dependency of the addon, so
 * this class must still load when YSM is not installed.
 */
public final class CirnoFlightAnimations {
    private static boolean registered = false;

    private CirnoFlightAnimations() {}

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(CirnoFlightAnimations::register);
    }

    public static void register() {
        if (registered) {
            return;
        }

        try {
            // YSM 2.6.5 has its own obfuscated TLM-maid animation registry.
            // Register there first; if that exact registry is not present, fall back to OpenYSM.
            if (registerYsm265()) {
                registered = true;
                debug("YSM 2.6.5 maid fly + elytra_fly states registered.");
                return;
            }

            Class<?> predicateClass = Class.forName(
                    "com.elfmcys.yesstevemodel.forge.client.animation.predicate.TouhouMaidAnimationPredicate");

            Class<?> animationStateClass = Class.forName(
                    "com.elfmcys.yesstevemodel.client.animation.AnimationState");

            Class<?> loopTypeClass = Class.forName(
                    "com.elfmcys.yesstevemodel.geckolib3.core.builder.ILoopType$EDefaultLoopTypes");

            @SuppressWarnings("unchecked")
            Object loop = Enum.valueOf(
                    (Class<? extends Enum>) loopTypeClass.asSubclass(Enum.class), "LOOP");

            /*
             * Erased constructor:
             * AnimationState(String, ILoopType, int, BiPredicate)
             */
            Constructor<?> stateCtor = null;
            for (Constructor<?> ctor : animationStateClass.getConstructors()) {
                Class<?>[] p = ctor.getParameterTypes();
                if (p.length == 4
                        && p[0] == String.class
                        && p[2] == int.class
                        && BiPredicate.class.isAssignableFrom(p[3])) {
                    stateCtor = ctor;
                    break;
                }
            }

            if (stateCtor == null) {
                debug("Could not find YSM AnimationState constructor.");
                return;
            }

            BiPredicate<Object, Object> predicate = (maidObject, animationEvent) -> {
                if (!(maidObject instanceof CirnoEntity cirno)) {
                    return false;
                }

                boolean flying = cirno.getFlightState() == CirnoFlightState.FLY;

                // Throttle this to state changes rather than filling chat every frame.
                if (flying != lastPredicateResult) {
                    lastPredicateResult = flying;
                    debug("YSM fly predicate = " + flying
                            + " | state=" + cirno.getFlightState());
                }

                return flying;
            };

            Object animationState = stateCtor.newInstance(
                    "fly",
                    loop,
                    1, // before built-in maid jump (priority 2)
                    predicate
            );

            Method registerHandler = predicateClass.getMethod(
                    "registerHandler", animationStateClass);

            registerHandler.invoke(null, animationState);

            registered = true;
            debug("Registered YSM maid animation state 'fly' at priority 1.");
        } catch (Throwable t) {
            debug("YSM fly registration FAILED: " + t.getClass().getSimpleName()
                    + ": " + String.valueOf(t.getMessage()));
            t.printStackTrace();
        }
    }

    /**
     * YSM Forge 2.6.5 / MC 1.20.1.
     *
     * YSM's TLM renderer has a dedicated maid animation registry, but unlike the
     * player registry it only contains jump/run/walk/etc. It does NOT register
     * fly or elytra_fly for EntityMaid. The player fly predicate also checks
     * Player.getAbilities().flying, so it can never match Cirno.
     *
     * The obfuscated names below are the names in YSM 2.6.5. We use reflection so
     * CatBurger remains loadable without YSM installed.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean registerYsm265() {
        try {
            Class<?> registryClass = Class.forName(
                    "com.elfmcys.yesstevemodel.ooOOOo0OooOoooO0OoOOOO0o");
            Class<?> stateClass = Class.forName(
                    "com.elfmcys.yesstevemodel.oOoO0Oo0oO0o00O0oO0ooooo");
            Class<?> loopClass = Class.forName(
                    "com.elfmcys.yesstevemodel.OooOO0OOOoO0oOO0OOOO0Ooo");
            Class<?> animationContextClass = Class.forName(
                    "com.elfmcys.yesstevemodel.OO00O0o0OooOOOo00OO00o00");
            Class<?> maidClass = Class.forName(
                    "com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid");

            Object loop = Enum.valueOf((Class<? extends Enum>) loopClass.asSubclass(Enum.class), "LOOP");

            Constructor<?> ctor = stateClass.getConstructor(
                    String.class, loopClass, int.class, BiPredicate.class);
            Method registerHandler = registryClass.getMethod("Oo0Oo0o00O00Oo0OOoOOoooo", stateClass);

            BiPredicate<Object, Object> flyPredicate = (maidObject, event) -> {
                if (!(maidObject instanceof CirnoEntity cirno)) return false;
                boolean match = cirno.getFlightState() == CirnoFlightState.FLY;
                debugState("YSM fly", cirno, match);
                return match;
            };

            BiPredicate<Object, Object> elytraPredicate = (maidObject, event) -> {
                if (!(maidObject instanceof CirnoEntity cirno)) return false;
                CirnoFlightState flightState = cirno.getFlightState();
                boolean match = flightState == CirnoFlightState.ELYTRA
                        || flightState == CirnoFlightState.ELYTRA_BOOST;
                debugState("YSM elytra_fly (including rocket boost)", cirno, match);
                return match;
            };

            Object flyState = ctor.newInstance("fly", loop, 1, flyPredicate);
            Object elytraState = ctor.newInstance("elytra_fly", loop, 1, elytraPredicate);

            registerHandler.invoke(null, flyState);
            registerHandler.invoke(null, elytraState);

            debug("YSM 2.6.5 registry found; registered fly + elytra_fly at priority 1 (ELYTRA_BOOST reuses elytra_fly).");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            debug("YSM 2.6.5 registration FAILED: " + t.getClass().getSimpleName()
                    + ": " + String.valueOf(t.getMessage()));
            t.printStackTrace();
            return false;
        }
    }

    private static boolean lastFlyResult = false;
    private static boolean lastElytraResult = false;

    private static void debugState(String label, CirnoEntity cirno, boolean match) {
        if (label.equals("YSM fly")) {
            if (match != lastFlyResult) {
                lastFlyResult = match;
                debug(label + " predicate = " + match + " | state=" + cirno.getFlightState());
            }
        } else {
            if (match != lastElytraResult) {
                lastElytraResult = match;
                debug(label + " predicate = " + match + " | state=" + cirno.getFlightState());
            }
        }
    }

    private static boolean lastPredicateResult = false;

    private static void debug(String message) {
        System.out.println("[CatBurger/Cirno YSM] " + message);
    }
}
