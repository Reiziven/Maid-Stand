package net.zhaiji.catburger.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class CatBurgerCommonConfig {
    public static boolean totemEffectActive;
    public static boolean wakeUpCanResetCooldown;
    public static int totemCooldown;
    public static int curiosCooldown;
    public static int foodRestorationFromCurios;
    public static int foodMaxRestoration;
    public static boolean usePercentageHealthRestoration;
    public static double percentageHealthRestoration;
    public static int healthRestorationFromTotem;
    public static int foodRestorationFromTotem;
    public static int saturationRestorationFromTotem;
    // Companion entity
    public static boolean companionEntityEnabled;
    public static boolean useYsmModel;
    public static String ysmModelId;
    // Companion AI
    public static boolean cirnoAiEnabled;
    public static boolean cirnoFollowsOwner;
    public static boolean cirnoRetargetAttackers;
    // Companion death / revive
    public static boolean cirnoCanDie;
    public static int cirnoReviveCooldown;
    public static boolean cirnoIsPickable;
    public static boolean cirnoDisableMobCollision;
    public static boolean cirnoDisablePhysicsWhileFollowing;
    // Only takes effect when cirnoFollowsOwner AND cirnoDisablePhysicsWhileFollowing are both
    // true — that's the only situation she can end up physically embedded in a block.
    public static boolean cirnoAttackSeeThroughBlocks;

    // Jump / fall / fly / elytra pose while following (see CirnoFlightTracker)
    public static boolean cirnoFlyAnimationEnabled;
    public static int cirnoFlyDelayTicks;

    // Companion persistence — prevent Cirno from being unloaded/discarded by
    // the game engine, chunk unloads, or third-party mods. Only explicit trusted
    // paths (H key, TLM smart slab / photo, death system, dimension change) may
    // remove her, matching what ANChorCore does for anchor-equipped maids.
    public static boolean cirnoUnloadProtection;
    public static boolean cirnoPartOfOwner;

    // Companion NBT autosave (safety net against losing her inventory if she's ever
    // discarded through a path that doesn't explicitly snapshot her first)
    public static boolean cirnoAutoSaveEnabled;
    public static int cirnoAutoSaveIntervalTicks;

    // When true, attribute buffs and telekinesis require companionEntityEnabled = true
    public static boolean requireCompanionForBuffs;

    // Attribute buffs (applied while companion is active)
    public static boolean attributeBuffsEnabled;
    public static double bonusAttackDamage;
    public static double bonusMaxHealth;

    // Telekinesis shield (auto-activates on fatal damage / low HP)
    public static boolean telekinesisShieldEnabled;
    public static int telekinesisShieldDuration; // ticks
    public static int telekinesisShieldCooldown; // ticks
    public static double telekinesisRepelRange;  // blocks radius to repel targets

    // Active telekinesis control mode (keybind-toggled, right-click mob to control)
    public static boolean telekinesisControlEnabled;
    public static int telekinesisControlDuration; // ticks
    public static int telekinesisControlCooldown; // ticks

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder()
            .comment(
                    "配置",
                    "config"
            )
            .push("Config");

    private static final ForgeConfigSpec.BooleanValue TOTEM_EFFECT_ACTIVE = BUILDER
            .comment("Enable CatBurger totem effect")
            .define(
                    "active",
                    true
            );

    private static final ForgeConfigSpec.BooleanValue WAKE_UP_CAN_RESET_COOLDOWN = BUILDER
            .comment("Reset totem cooldown when waking up from bed")
            .define(
                    "wakeUpCanResetCooldown",
                    true
            );

    private static final ForgeConfigSpec.IntValue TOTEM_COOLDOWN_VALUE = BUILDER
            .comment("Totem effect cooldown in ticks")
            .defineInRange(
                    "totemCooldown",
                    36000,
                    0,
                    Integer.MAX_VALUE
            );

    private static final ForgeConfigSpec.IntValue CURIOS_COOLDOWN_VALUE = BUILDER
            .comment("Curios effect cooldown in ticks")
            .defineInRange(
                    "curiosCooldown",
                    1200,
                    0,
                    Integer.MAX_VALUE
            );

    private static final ForgeConfigSpec.IntValue FOOD_RESTORATION_VALUE = BUILDER
            .comment("Hunger value restored by Curios effect")
            .defineInRange(
                    "foodRestorationFromCurios",
                    1,
                    0,
                    20
            );

    private static final ForgeConfigSpec.IntValue FOOD_MAX_RESTORATION = BUILDER
            .comment("Maximum hunger value that can be restored by Curios")
            .defineInRange(
                    "foodMaxRestoration",
                    18,
                    1,
                    20
            );

    private static final ForgeConfigSpec.BooleanValue USE_PERCENTAGE_HEALTH_RESTORATION = BUILDER
            .comment("Use percentage-based health restoration (mutually exclusive with fixed value mode)")
            .define(
                    "usePercentageHealthRestoration",
                    true
            );

    private static final ForgeConfigSpec.DoubleValue PERCENTAGE_HEALTH_RESTORATION = BUILDER
            .comment("Health restoration percentage based on max health (only effective when percentage mode is enabled)")
            .defineInRange(
                    "percentageHealthRestoration",
                    100.0,
                    0.0,
                    100.0
            );

    private static final ForgeConfigSpec.IntValue HEALTH_VALUE = BUILDER
            .comment("Health points restored when totem triggers")
            .defineInRange(
                    "healthRestorationFromTotem",
                    20,
                    0,
                    Integer.MAX_VALUE
            );

    private static final ForgeConfigSpec.IntValue FOOD_VALUE = BUILDER
            .comment("Hunger value restored when totem triggers")
            .defineInRange(
                    "foodRestorationFromTotem",
                    20,
                    0,
                    20
            );

    private static final ForgeConfigSpec.IntValue SATURATION_VALUE = BUILDER
            .comment("Saturation restored when totem triggers")
            .defineInRange(
                    "saturationRestorationFromTotem",
                    20,
                    0,
                    20
            );

    private static final ForgeConfigSpec.BooleanValue COMPANION_ENTITY_ENABLED = BUILDER
            .comment("Spawn a companion entity (Cirno) when the curio is equipped (requires TLM)")
            .define("companionEntityEnabled", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_AI_ENABLED = BUILDER
            .comment("Enable Cirno's maid AI (inventory, tasks, attack). When false she has no AI.")
            .define("cirnoAiEnabled", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_FOLLOWS_OWNER = BUILDER
            .comment("Snap Cirno to the player's side every tick. Disable to let her walk freely via AI.")
            .define("cirnoFollowsOwner", false);

    private static final ForgeConfigSpec.BooleanValue CIRNO_RETARGET_ATTACKERS = BUILDER
            .comment("When something attacks Cirno, redirect that entity's attack target to the owner player instead.")
            .define("cirnoRetargetAttackers", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_CAN_DIE = BUILDER
            .comment("Allow Cirno to actually die when her health reaches 0. When false she is invulnerable (default).")
            .define("cirnoCanDie", false);

    private static final ForgeConfigSpec.IntValue CIRNO_REVIVE_COOLDOWN = BUILDER
            .comment("Ticks before Cirno revives after dying (only used when cirnoCanDie = true). Default 1200 = 60s.")
            .defineInRange("cirnoReviveCooldown", 1200, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue CIRNO_IS_PICKABLE = BUILDER
            .comment("Allow Cirno to be targeted and hit by players and projectiles. When false she has no hitbox interaction.")
            .define("cirnoIsPickable", false);

    private static final ForgeConfigSpec.BooleanValue CIRNO_DISABLE_MOB_COLLISION = BUILDER
            .comment("Disable mob collision for Cirno. When false (default), collision is only disabled while cirnoFollowsOwner is active. When true, collision is always disabled.")
            .define("cirnoDisableMobCollision", false);

    private static final ForgeConfigSpec.BooleanValue CIRNO_DISABLE_PHYSICS_WHILE_FOLLOWING = BUILDER
            .comment("Disable block/physics collision for Cirno only while cirnoFollowsOwner is active. " +
                    "She's fully attached to the player's position in that mode anyway, so this just lets " +
                    "her pass through walls/blocks instead of getting stuck or jittering against them " +
                    "when the follow point ends up inside geometry. Has no effect when cirnoFollowsOwner is false.")
            .define("cirnoDisablePhysicsWhileFollowing", false);

    private static final ForgeConfigSpec.BooleanValue CIRNO_ATTACK_SEE_THROUGH_BLOCKS = BUILDER
            .comment("Let Cirno see and attack targets through blocks (ignores block line-of-sight for her " +
                    "target detection/attack checks). Only ever takes effect while BOTH cirnoFollowsOwner and " +
                    "cirnoDisablePhysicsWhileFollowing are true, because that's the only combination that can " +
                    "leave her clipped inside a block (the follow point can end up in wall geometry once physics " +
                    "collision is off). Without this, a maid whose eyes are inside a solid block fails every " +
                    "line-of-sight check and will never even attempt to attack, unlike vanilla mobs which can " +
                    "usually still see out even when partially stuck. Has no effect in any other configuration.")
            .define("cirnoAttackSeeThroughBlocks", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_FLY_ANIMATION_ENABLED = BUILDER
            .comment("While cirnoFollowsOwner is active, choose Cirno's airborne pose from what her owner is doing: "
                    + "grounded when they stand, elytra / rocket-boost while they glide, creative fly during ability flight, "
                    + "and jump -> fall -> fly for ordinary airtime. Poses need matching clips in her model (fly, fall, "
                    + "elytra_fly, elytra_fly_boost); anything a model lacks falls back to the built-in jump. "
                    + "Set to false to leave all of it to TLM's default behaviour.")
            .define("cirnoFlyAnimationEnabled", true);

    private static final ForgeConfigSpec.IntValue CIRNO_FLY_DELAY_TICKS = BUILDER
            .comment("How long an ordinary airborne stretch (not elytra, not creative flight) may show jump / fall "
                    + "before Cirno switches to fly, in ticks. Default 15 = 0.75s, which comfortably covers a normal jump. "
                    + "Hovering in place switches to fly sooner regardless of this. Landing is always instant.")
            .defineInRange("cirnoFlyDelayTicks", 15, 1, 200);

    private static final ForgeConfigSpec.BooleanValue CIRNO_UNLOAD_PROTECTION = BUILDER
            .comment("When true, Cirno cannot be removed by chunk unloads, server cleanup, or third-party mods. " +
                    "Only trusted paths — the H key, TLM smart slab/photo, the death/revive system, and dimension " +
                    "change — are allowed to discard her. Mirrors what ANChorCore does for anchor-equipped maids. " +
                    "Has no effect on /kill, the death system when cirnoCanDie = true, or explicit hide/store actions.")
            .define("cirnoUnloadProtection", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_PART_OF_OWNER = BUILDER
            .comment("Whether every Cirno belonging to this player (the Curios companion AND any /cirno " +
                    "command-spawned ones) is bound to her owner's fate. When true, she disappears — " +
                    "snapshotted and discarded, then revived/respawned the normal way — the moment the owner " +
                    "dies OR logs out. When false, neither death nor logout removes her: she stays alive and " +
                    "visible in the world, with only a durable backup snapshot taken. This is the single " +
                    "switch for that decision; cirnoUnloadProtection is unrelated — it only controls whether " +
                    "untrusted code (chunk unloads, other mods) is allowed to remove her at all.")
            .define("cirnoPartOfOwner", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_AUTO_SAVE_ENABLED = BUILDER
            .comment("Periodically back up Cirno's NBT (inventory, equipment, etc.) to a durable location " +
                    "(the owning player's data, and the CatBurger item when she's linked to one) while she's " +
                    "alive, in addition to saving on every known removal path. This is a safety net so her " +
                    "items survive even if she's ever discarded through a path this mod doesn't explicitly " +
                    "handle (another mod, a crash, /kill, etc.).")
            .define("cirnoAutoSaveEnabled", true);

    private static final ForgeConfigSpec.IntValue CIRNO_AUTO_SAVE_INTERVAL_TICKS = BUILDER
            .comment("How often (in ticks) to run the autosave described above. Default 200 = every 10s.")
            .defineInRange("cirnoAutoSaveIntervalTicks", 200, 20, 12000);

    private static final ForgeConfigSpec.BooleanValue USE_YSM_MODEL = BUILDER
            .comment("Use a YSM model for the companion entity (requires Yes Steve Model)")
            .define("useYsmModel", false);

    private static final ForgeConfigSpec.ConfigValue<String> YSM_MODEL_ID = BUILDER
            .comment("YSM model ID to use for the companion entity. Use /ysm model set @s to find model IDs. " +
                    "Set to \"none\" to skip YSM model loading entirely and let YSM or TLM handle the model themselves.")
            .define("ysmModelId", "none");

    // ── Attribute buffs ───────────────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue REQUIRE_COMPANION_FOR_BUFFS_VALUE = BUILDER
            .comment("When true, attribute buffs and telekinesis only work if companionEntityEnabled is also true (Cirno must be present)")
            .define("requireCompanionForBuffs", false);

    private static final ForgeConfigSpec.BooleanValue ATTRIBUTE_BUFFS_ENABLED_VALUE = BUILDER
            .comment("Apply bonus attack damage and max health while the companion is active")
            .define("attributeBuffsEnabled", true);

    private static final ForgeConfigSpec.DoubleValue BONUS_ATTACK_DAMAGE_VALUE = BUILDER
            .comment("Bonus attack damage added to the player while companion is active")
            .defineInRange("bonusAttackDamage", 4.0, 0.0, 1000.0);

    private static final ForgeConfigSpec.DoubleValue BONUS_MAX_HEALTH_VALUE = BUILDER
            .comment("Bonus max health added to the player while companion is active")
            .defineInRange("bonusMaxHealth", 10.0, 0.0, 1000.0);

    // ── Telekinesis shield (auto) ──────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue TELEKINESIS_SHIELD_ENABLED_VALUE = BUILDER
            .comment("Auto-activate telekinesis shield on fatal damage or when HP drops below 30%")
            .define("telekinesisShieldEnabled", true);

    private static final ForgeConfigSpec.IntValue TELEKINESIS_SHIELD_DURATION_VALUE = BUILDER
            .comment("Duration of the telekinesis shield in ticks (default 200 = 10s)")
            .defineInRange("telekinesisShieldDuration", 200, 20, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue TELEKINESIS_SHIELD_COOLDOWN_VALUE = BUILDER
            .comment("Cooldown of the telekinesis shield in ticks after it expires (default 1200 = 60s)")
            .defineInRange("telekinesisShieldCooldown", 1200, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue TELEKINESIS_REPEL_RANGE_VALUE = BUILDER
            .comment("Radius in blocks within which enemies are repelled during telekinesis shield")
            .defineInRange("telekinesisRepelRange", 6.0, 1.0, 64.0);

    // ── Active telekinesis control ────────────────────────────────────────────

    private static final ForgeConfigSpec.BooleanValue TELEKINESIS_CONTROL_ENABLED_VALUE = BUILDER
            .comment("Enable active telekinesis control mode (keybind-toggled, right-click mob to control it)")
            .define("telekinesisControlEnabled", true);

    private static final ForgeConfigSpec.IntValue TELEKINESIS_CONTROL_DURATION_VALUE = BUILDER
            .comment("Duration a mob stays under telekinesis control in ticks (default 200 = 10s)")
            .defineInRange("telekinesisControlDuration", 200, 20, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue TELEKINESIS_CONTROL_COOLDOWN_VALUE = BUILDER
            .comment("Cooldown before telekinesis control can be used again in ticks (default 600 = 30s)")
            .defineInRange("telekinesisControlCooldown", 600, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static void handlerModConfigEvent(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            totemEffectActive = TOTEM_EFFECT_ACTIVE.get();
            wakeUpCanResetCooldown = WAKE_UP_CAN_RESET_COOLDOWN.get();
            totemCooldown = TOTEM_COOLDOWN_VALUE.get();
            curiosCooldown = CURIOS_COOLDOWN_VALUE.get();
            foodRestorationFromCurios = FOOD_RESTORATION_VALUE.get();
            foodMaxRestoration = FOOD_MAX_RESTORATION.get();
            usePercentageHealthRestoration = USE_PERCENTAGE_HEALTH_RESTORATION.get();
            percentageHealthRestoration = PERCENTAGE_HEALTH_RESTORATION.get();
            healthRestorationFromTotem = HEALTH_VALUE.get();
            foodRestorationFromTotem = FOOD_VALUE.get();
            saturationRestorationFromTotem = SATURATION_VALUE.get();
            companionEntityEnabled = COMPANION_ENTITY_ENABLED.get();
            cirnoAiEnabled = CIRNO_AI_ENABLED.get();
            cirnoFollowsOwner = CIRNO_FOLLOWS_OWNER.get();
            cirnoRetargetAttackers = CIRNO_RETARGET_ATTACKERS.get();
            cirnoCanDie = CIRNO_CAN_DIE.get();
            cirnoReviveCooldown = CIRNO_REVIVE_COOLDOWN.get();
            cirnoIsPickable = CIRNO_IS_PICKABLE.get();
            cirnoDisableMobCollision = CIRNO_DISABLE_MOB_COLLISION.get();
            cirnoDisablePhysicsWhileFollowing = CIRNO_DISABLE_PHYSICS_WHILE_FOLLOWING.get();
            cirnoAttackSeeThroughBlocks = CIRNO_ATTACK_SEE_THROUGH_BLOCKS.get();
            cirnoFlyAnimationEnabled = CIRNO_FLY_ANIMATION_ENABLED.get();
            cirnoFlyDelayTicks = CIRNO_FLY_DELAY_TICKS.get();
            cirnoAutoSaveEnabled = CIRNO_AUTO_SAVE_ENABLED.get();
            cirnoAutoSaveIntervalTicks = CIRNO_AUTO_SAVE_INTERVAL_TICKS.get();
            cirnoUnloadProtection = CIRNO_UNLOAD_PROTECTION.get();
            cirnoPartOfOwner = CIRNO_PART_OF_OWNER.get();
            useYsmModel = USE_YSM_MODEL.get();
            ysmModelId = YSM_MODEL_ID.get();
            requireCompanionForBuffs = REQUIRE_COMPANION_FOR_BUFFS_VALUE.get();
            attributeBuffsEnabled = ATTRIBUTE_BUFFS_ENABLED_VALUE.get();
            bonusAttackDamage = BONUS_ATTACK_DAMAGE_VALUE.get();
            bonusMaxHealth = BONUS_MAX_HEALTH_VALUE.get();
            telekinesisShieldEnabled = TELEKINESIS_SHIELD_ENABLED_VALUE.get();
            telekinesisShieldDuration = TELEKINESIS_SHIELD_DURATION_VALUE.get();
            telekinesisShieldCooldown = TELEKINESIS_SHIELD_COOLDOWN_VALUE.get();
            telekinesisRepelRange = TELEKINESIS_REPEL_RANGE_VALUE.get();
            telekinesisControlEnabled = TELEKINESIS_CONTROL_ENABLED_VALUE.get();
            telekinesisControlDuration = TELEKINESIS_CONTROL_DURATION_VALUE.get();
            telekinesisControlCooldown = TELEKINESIS_CONTROL_COOLDOWN_VALUE.get();
        }
    }
}

