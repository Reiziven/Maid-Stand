package net.zhaiji.catburger.entity;

/**
 * Which airborne/ground pose the companion Cirno should show while she follows her owner.
 * <p>
 * Computed server-side by {@link CirnoFlightTracker} and synced to clients through entity
 * data. On the client, {@link CirnoEntity} maps this into flags and YSM roamingVars so
 * YSM models can play {@code fly} / {@code fall} / {@code elytra_fly}.
 * <p>
 * The ordinal is what gets synced, so only ever append new values at the end.
 */
public enum CirnoFlightState {
    /** Not driven by the owner. Vanilla TLM / YSM behaviour. */
    NONE,
    /** Standing, walking, running. */
    GROUNDED,
    /** Short upward hop. */
    JUMP,
    /** Short downward drop. */
    FALL,
    /** Sustained flight / hover / creative-style flight. */
    FLY,
    /** Owner is gliding with an elytra. */
    ELYTRA,
    /** Owner is gliding with an elytra and being boosted by a firework rocket. */
    ELYTRA_BOOST;

    private static final CirnoFlightState[] BY_ID = values();

    public static CirnoFlightState byId(int id) {
        return id >= 0 && id < BY_ID.length ? BY_ID[id] : NONE;
    }

    public boolean isElytra() {
        return this == ELYTRA || this == ELYTRA_BOOST;
    }

    public boolean isAirborne() {
        return this == JUMP || this == FALL || this == FLY || isElytra();
    }
}
