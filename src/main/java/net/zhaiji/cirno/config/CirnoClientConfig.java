package net.zhaiji.cirno.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class CirnoClientConfig {
    public static double scale;
    public static double floatDistance;
    public static double time;
    public static double frontBackOffset;
    public static double verticalOffset;
    public static double leftRightOffset;
    public static boolean dragEnabled;
    public static double dragStrength;
    public static boolean rotationDragEnabled;
    public static double rotationDragSmoothness;
    public static boolean rotationDragUseWrapDegrees;
    public static boolean rotationDragAffectOrientation;
    public static boolean springEnabled;
    public static double springStiffness;
    public static double springDamping;
    /** Scale of the Cirno Quick-Select overlay (Shift+H). Range 0.5–2.0, default 1.0. */
    public static double quickSelectScale;
    /** Visual style of the Quick-Select overlay. Valid values: CIRNO, HAKUREI. */
    public static String quickSelectStyle;
    /**
     * Client-only, purely cosmetic nudge on top of the server's {@code cirnoMountCarry*} position,
     * applied only to how the carried player's model renders on THIS client (every other player still
     * sees them at the synced server position). Use this to compensate for a resource pack/model
     * that looks slightly off on your own screen without changing the actual riding position for
     * everyone. Does not affect collision, camera anchor, or hitbox — render only.
     */
    public static double cirnoCarryRenderForwardOffset;
    public static double cirnoCarryRenderSideOffset;
    public static double cirnoCarryRenderUpOffset;
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder()
            .comment("client config");

    static { BUILDER.comment("Size, position and float animation of the displayed model").push("Display"); }

    private static final ForgeConfigSpec.DoubleValue SCALE = BUILDER
            .comment("Display scale")
            .defineInRange(
                    "scale",
                    0.7,
                    0.1,
                    10d
            );

    private static final ForgeConfigSpec.DoubleValue FLOAT_DISTANCE = BUILDER
            .comment("Float distance (blocks)")
            .defineInRange(
                    "floatDistance",
                    0.1,
                    0,
                    100d
            );

    private static final ForgeConfigSpec.DoubleValue FLOAT_CYCLE_DURATION = BUILDER
            .comment("Time to complete a float cycle (tick)")
            .defineInRange(
                    "time",
                    15,
                    1,
                    100d
            );

    private static final ForgeConfigSpec.DoubleValue FRONT_BACK_OFFSET = BUILDER
            .comment("Front & back offset")
            .defineInRange(
                    "frontBackOffset",
                    -0.4,
                    -100d,
                    100d
            );

    private static final ForgeConfigSpec.DoubleValue VERTICAL_OFFSET = BUILDER
            .comment("Vertical offset")
            .defineInRange(
                    "verticalOffset",
                    0,
                    -100d,
                    100d
            );

    private static final ForgeConfigSpec.DoubleValue LEFT_RIGHT_OFFSET = BUILDER
            .comment("Left & right offset")
            .defineInRange(
                    "leftRightOffset",
                    0.8,
                    -100d,
                    100d
            );

    static { BUILDER.pop(); }

    static { BUILDER.comment("Drag and spring physics effects").push("Motion"); }

    private static final ForgeConfigSpec.BooleanValue DRAG_ENABLED = BUILDER
            .comment("Enable drag system for position lag effect")
            .define(
                    "dragEnabled",
                    true
            );

    private static final ForgeConfigSpec.DoubleValue DRAG_STRENGTH = BUILDER
            .comment("Drag strength factor")
            .defineInRange(
                    "dragStrength",
                    0.2,
                    0.01,
                    1.0
            );

    private static final ForgeConfigSpec.BooleanValue ROTATION_DRAG_ENABLED = BUILDER
            .comment("Enable rotation drag system for head rotation lag effect")
            .define(
                    "rotationDragEnabled",
                    true
            );

    private static final ForgeConfigSpec.DoubleValue ROTATION_DRAG_SMOOTHNESS = BUILDER
            .comment("Rotation drag smoothness factor")
            .defineInRange(
                    "rotationDragSmoothness",
                    0.2,
                    0.01,
                    1.0
            );

    private static final ForgeConfigSpec.BooleanValue ROTATION_DRAG_USE_WRAP_DEGREES = BUILDER
            .comment("Use wrapDegrees for angle wrap-around handling (shortest path rotation)")
            .define(
                    "rotationDragUseWrapDegrees",
                    false
            );

    private static final ForgeConfigSpec.BooleanValue ROTATION_DRAG_AFFECT_ORIENTATION = BUILDER
            .comment("Allow rotation drag to affect burger model orientation (Y-axis rotation)")
            .define(
                    "rotationDragAffectOrientation",
                    false
            );

    private static final ForgeConfigSpec.BooleanValue SPRING_ENABLED = BUILDER
            .comment("Enable spring physics system for head rotation smoothing")
            .define(
                    "springEnabled",
                    false
            );

    private static final ForgeConfigSpec.DoubleValue SPRING_STIFFNESS = BUILDER
            .comment("Spring stiffness coefficient")
            .defineInRange(
                    "springStiffness",
                    0.9,
                    0.1,
                    2.0
            );

    private static final ForgeConfigSpec.DoubleValue SPRING_DAMPING = BUILDER
            .comment("Spring damping coefficient")
            .defineInRange(
                    "springDamping",
                    0.7,
                    0.1,
                    0.99
            );

    static { BUILDER.pop(); }

    static { BUILDER.comment("Quick-Select overlay (Shift+H)").push("QuickSelect"); }

    private static final ForgeConfigSpec.DoubleValue QUICK_SELECT_SCALE = BUILDER
            .comment("Scale of the Cirno Quick-Select overlay (Shift+H). Range 0.5–2.0.")
            .defineInRange("quickSelectScale", 1.0, 0.5, 2.0);

    private static final ForgeConfigSpec.ConfigValue<String> QUICK_SELECT_STYLE = BUILDER
            .comment("Visual style of the Quick-Select overlay. Options: CIRNO (ice-blue), HAKUREI (shrine-maiden red/gold), WHEEL (WIP).")
            .define("quickSelectStyle", "HAKUREI");

    static { BUILDER.pop(); }

    static { BUILDER.comment("Client-only render nudges for the carried player model").push("Carry"); }

    private static final ForgeConfigSpec.DoubleValue CIRNO_CARRY_RENDER_FORWARD_OFFSET = BUILDER
            .comment("Client-only render-only nudge, in blocks, on top of the server's carry position. "
                    + "Only changes how it looks on your own screen; does not move the actual (synced) position.")
            .defineInRange("cirnoCarryRenderForwardOffset", -0.04, -2.0, 2.0);

    private static final ForgeConfigSpec.DoubleValue CIRNO_CARRY_RENDER_SIDE_OFFSET = BUILDER
            .comment("Client-only render-only sideways nudge, in blocks (positive = her right side).")
            .defineInRange("cirnoCarryRenderSideOffset", 0.059, -2.0, 2.0);

    private static final ForgeConfigSpec.DoubleValue CIRNO_CARRY_RENDER_UP_OFFSET = BUILDER
            .comment("Client-only render-only vertical nudge, in blocks.")
            .defineInRange("cirnoCarryRenderUpOffset", 0.34, -2.0, 2.0);

    static { BUILDER.pop(); }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static void handlerModConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            scale = SCALE.get();
            floatDistance = FLOAT_DISTANCE.get();
            time = FLOAT_CYCLE_DURATION.get();
            frontBackOffset = FRONT_BACK_OFFSET.get();
            verticalOffset = VERTICAL_OFFSET.get();
            leftRightOffset = LEFT_RIGHT_OFFSET.get();
            dragEnabled = DRAG_ENABLED.get();
            dragStrength = DRAG_STRENGTH.get();
            rotationDragEnabled = ROTATION_DRAG_ENABLED.get();
            rotationDragSmoothness = ROTATION_DRAG_SMOOTHNESS.get();
            rotationDragUseWrapDegrees = ROTATION_DRAG_USE_WRAP_DEGREES.get();
            rotationDragAffectOrientation = ROTATION_DRAG_AFFECT_ORIENTATION.get();
            springEnabled = SPRING_ENABLED.get();
            springStiffness = SPRING_STIFFNESS.get();
            springDamping = SPRING_DAMPING.get();
            quickSelectScale = QUICK_SELECT_SCALE.get();
            quickSelectStyle = QUICK_SELECT_STYLE.get();
            cirnoCarryRenderForwardOffset = CIRNO_CARRY_RENDER_FORWARD_OFFSET.get();
            cirnoCarryRenderSideOffset = CIRNO_CARRY_RENDER_SIDE_OFFSET.get();
            cirnoCarryRenderUpOffset = CIRNO_CARRY_RENDER_UP_OFFSET.get();
        }
    }
}