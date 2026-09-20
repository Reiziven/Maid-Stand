package net.zhaiji.catburger.entity;

/**
 * Decides which {@link CirnoFlightState} Cirno should show, from a handful of facts about her owner.
 * Deliberately free of any Minecraft types so the whole jump / fall / fly interplay can be unit-tested
 * in plain Java. One instance per Cirno; call {@link #update} once per server tick.
 *
 * <h3>Priority order</h3>
 * <ol>
 *   <li><b>Owner on ground</b> – grounded pose, instantly. Special case: if Cirno herself has no solid
 *       block under her feet, go straight to FLY so she never looks like she is falling while the
 *       owner stands still.</li>
 *   <li><b>Owner gliding with an elytra</b> – ELYTRA, or ELYTRA_BOOST while a firework rocket is boosting.</li>
 *   <li><b>Owner in creative / ability flight</b> – FLY. Vertical speed is ignored completely.</li>
 *   <li><b>Normal airborne</b> – track air time. While it is short: clear upward speed = JUMP, clear
 *       downward speed = FALL. After the window, or once vertical speed has stayed near zero
 *       (hovering) = FLY.</li>
 * </ol>
 */
public final class CirnoFlightTracker {

    static final double JUMP_SPEED = 0.08;
    static final double FALL_SPEED = -0.10;
    static final double HOVER_SPEED = 0.03;
    static final int HOVER_TICKS = 6;
    static final int TAKEOFF_GRACE_TICKS = 2;
    static final int SUPPORT_REGAIN_TICKS = 3;
    private static final double SMOOTHING = 0.5;

    public record Input(boolean ownerOnGround, boolean ownerFallFlying, boolean rocketBoost,
                        boolean ownerAbilityFlight, double ownerDy, boolean cirnoHasGround) {
    }

    private CirnoFlightState state = CirnoFlightState.GROUNDED;
    private double verticalSpeed;
    private int airTicks;
    private int hoverTicks;
    private int supportTicks;
    private boolean flyingWithoutSupport;

    public CirnoFlightState update(Input in, int flyDelayTicks) {
        if (in.ownerOnGround()) {
            airTicks = 0;
            hoverTicks = 0;
            verticalSpeed = 0.0;
            boolean supported = in.cirnoHasGround();
            supportTicks = supported ? Math.min(supportTicks + 1, SUPPORT_REGAIN_TICKS) : 0;
            if (!supported) {
                flyingWithoutSupport = true;
                return state = CirnoFlightState.FLY;
            }
            if (flyingWithoutSupport && supportTicks < SUPPORT_REGAIN_TICKS) {
                return state = CirnoFlightState.FLY;
            }
            flyingWithoutSupport = false;
            return state = CirnoFlightState.GROUNDED;
        }
        flyingWithoutSupport = false;
        supportTicks = 0;

        airTicks++;
        verticalSpeed += (in.ownerDy() - verticalSpeed) * SMOOTHING;

        if (in.ownerFallFlying()) {
            hoverTicks = 0;
            return state = in.rocketBoost() ? CirnoFlightState.ELYTRA_BOOST : CirnoFlightState.ELYTRA;
        }

        if (in.ownerAbilityFlight()) {
            hoverTicks = 0;
            return state = CirnoFlightState.FLY;
        }

        if (Math.abs(verticalSpeed) < HOVER_SPEED) {
            hoverTicks++;
        } else {
            hoverTicks = 0;
        }

        if (airTicks <= TAKEOFF_GRACE_TICKS
                && (state == CirnoFlightState.GROUNDED || state == CirnoFlightState.NONE)) {
            return state;
        }

        if (airTicks > flyDelayTicks || hoverTicks >= HOVER_TICKS) {
            return state = CirnoFlightState.FLY;
        }
        if (verticalSpeed >= JUMP_SPEED) {
            return state = CirnoFlightState.JUMP;
        }
        if (verticalSpeed <= FALL_SPEED) {
            return state = CirnoFlightState.FALL;
        }

        return switch (state) {
            case JUMP, FALL, FLY -> state;
            default -> state = CirnoFlightState.FALL;
        };
    }

    public void reset() {
        state = CirnoFlightState.GROUNDED;
        verticalSpeed = 0.0;
        airTicks = 0;
        hoverTicks = 0;
        supportTicks = 0;
        flyingWithoutSupport = false;
    }
}
