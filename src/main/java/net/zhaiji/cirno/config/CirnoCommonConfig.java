package net.zhaiji.cirno.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.event.config.ModConfigEvent;

public class CirnoCommonConfig {
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
    // Companion AI
    public static boolean cirnoAiEnabled;
    public static boolean cirnoFollowsOwner;
    public static boolean cirnoRetargetAttackers;
    // Companion death / revive
    public static boolean cirnoCanDie;
    public static int cirnoReviveCooldown;
    public static boolean cirnoSpawnTombstoneOnDeath;
    public static boolean cirnoCanSummonFromFilm;
    public static boolean cirnoIsPickable;
    public static boolean cirnoDisableMobCollision;
    public static boolean cirnoDisablePhysicsWhileFollowing;
    // Only takes effect when cirnoFollowsOwner AND cirnoDisablePhysicsWhileFollowing are both
    // true — that's the only situation she can end up physically embedded in a block.
    public static boolean cirnoAttackSeeThroughBlocks;

    // Jump / fall / fly / elytra pose while following (see CirnoFlightTracker)
    public static boolean cirnoFlyAnimationEnabled;
    public static int cirnoFlyDelayTicks;

    // ── Rideable "princess carry" flight mount (see CirnoEntity mount logic) ──────────
    /** Master switch for the whole saddle/ride/fly feature. */
    public static boolean cirnoMountEnabled;
    /** Only her owner (the curio equipper) may saddle/ride her. */
    public static boolean cirnoMountOwnerOnly;
    /** Whether a carried rider can fly (double-tap jump) while riding Cirno. */
    public static boolean cirnoMountCanFly;
    /** Movement speed (blocks/tick, roughly) while carried on the ground, not flying. */
    public static double cirnoMountWalkSpeed;
    /** Movement speed (blocks/tick, roughly) while flying. */
    public static double cirnoMountFlySpeed;
    /** Movement speed (blocks/tick, roughly) while flying with sprint (Ctrl) held — creative-style fast flight. */
    public static double cirnoMountFlyFastSpeed;
    /** Max vertical ascend speed while flying and holding jump. */
    public static double cirnoMountFlyVerticalSpeed;
    /** How much vertical speed is gained per tick while holding jump during flight. */
    public static double cirnoMountFlyVerticalAccel;
    /** Multiplier applied to vertical speed each tick jump isn't held, while flying — <1 = settle/hover. */
    public static double cirnoMountFlyHoverDamping;
    /** Ticks of Slow Falling granted on dismount if the rider was airborne, so mid-flight dismounts aren't a death sentence. 0 disables. */
    public static int cirnoMountDismountSlowFallTicks;
    /** Forward (toward her facing) offset in blocks for where the rider is carried. */
    public static double cirnoMountCarryForwardOffset;
    /** Sideways offset in blocks for where the rider is carried (positive = her right). */
    public static double cirnoMountCarrySideOffset;
    /** Vertical offset in blocks (added to her Y) for where the rider is carried. */
    public static double cirnoMountCarryUpOffset;

    // Companion persistence — prevent Cirno from being unloaded/discarded by
    // the game engine, chunk unloads, or third-party mods. Only explicit trusted
    // paths (H key, TLM smart slab / photo, death system, dimension change) may
    // remove her, matching what ANChorCore does for anchor-equipped maids.
    public static boolean cirnoUnloadProtection;
    public static boolean cirnoPartOfOwner;
    /** Distance (blocks) beyond which she's teleported straight to her owner, independent
     *  of cirnoFollowsOwner (which only governs the tight drag-along positioning). Also the
     *  safety net that recovers her after a watchdog re-load from a stuck/ghost chunk. */
    public static double cirnoMaxLeashDistance;

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
            );

    static { BUILDER.comment("Cirno totem: effect, cooldown and what it restores when triggered").push("Totem"); }

    private static final ForgeConfigSpec.BooleanValue TOTEM_EFFECT_ACTIVE = BUILDER
            .comment("Enable Cirno totem effect")
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

    static { BUILDER.pop(); }

    static { BUILDER.comment("Curio (equipped item) passive effect: cooldown and hunger restoration").push("Curios"); }

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

    static { BUILDER.pop(); }

    static { BUILDER.comment("Attribute bonuses granted while the companion is active").push("Buffs"); }

    private static final ForgeConfigSpec.BooleanValue REQUIRE_COMPANION_FOR_BUFFS_VALUE = BUILDER
            .comment("When true, attribute buffs and telekinesis only work if companionEntityEnabled is also true (Cirno must be present)")
            .define("requireCompanionForBuffs", false);

    private static final ForgeConfigSpec.BooleanValue ATTRIBUTE_BUFFS_ENABLED_VALUE = BUILDER
            .comment("Apply bonus attack damage and max health while the companion is active")
            .define("attributeBuffsEnabled", true);

    private static final ForgeConfigSpec.DoubleValue BONUS_ATTACK_DAMAGE_VALUE = BUILDER
            .comment("Bonus attack damage added to the player while companion is active")
            .defineInRange("bonusAttackDamage", 1.0, 0.0, 1000.0);

    private static final ForgeConfigSpec.DoubleValue BONUS_MAX_HEALTH_VALUE = BUILDER
            .comment("Bonus max health added to the player while companion is active")
            .defineInRange("bonusMaxHealth", 4.0, 0.0, 1000.0);

    static { BUILDER.pop(); }

    static { BUILDER.comment("Everything about the Cirno companion entity").push("Cirno"); }

    static { BUILDER.comment("Core behaviour: enable/AI/following/leash").push("General"); }

    private static final ForgeConfigSpec.BooleanValue COMPANION_ENTITY_ENABLED = BUILDER
            .comment("Spawn a companion entity (Cirno) when the curio is equipped (requires TLM)")
            .define("companionEntityEnabled", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_AI_ENABLED = BUILDER
            .comment("Enable Cirno's maid AI (inventory, tasks, attack). When false she has no AI.")
            .define("cirnoAiEnabled", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_FOLLOWS_OWNER = BUILDER
            .comment("Snap Cirno to the player's side every tick. Disable to let her walk freely via AI.")
            .define("cirnoFollowsOwner", true);

    private static final ForgeConfigSpec.DoubleValue CIRNO_MAX_LEASH_DISTANCE = BUILDER
            .comment("If Cirno ever ends up more than this many blocks from her owner in the same dimension " +
                    "(e.g. the owner traveled away while she was left behind, or she's recovering from having " +
                    "been stuck in a chunk that only just came back), she's teleported straight to the owner's " +
                    "side on the next check — regardless of cirnoFollowsOwner, which only controls the tighter " +
                    "drag-along positioning while actively following. Set to 0 to disable this recall entirely.")
            .defineInRange("cirnoMaxLeashDistance", 48.0, 0.0, 1024.0);

    private static final ForgeConfigSpec.BooleanValue CIRNO_PART_OF_OWNER = BUILDER
            .comment("Whether every Cirno belonging to this player (the Curios companion AND any /cirno " +
                    "command-spawned ones) is bound to her owner's fate. When true, she disappears — " +
                    "snapshotted and discarded, then revived/respawned the normal way — the moment the owner " +
                    "dies OR logs out. When false, neither death nor logout removes her: she stays alive and " +
                    "visible in the world, with only a durable backup snapshot taken. This is the single " +
                    "switch for that decision; cirnoUnloadProtection is unrelated — it only controls whether " +
                    "untrusted code (chunk unloads, other mods) is allowed to remove her at all.")
            .define("cirnoPartOfOwner", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_IS_PICKABLE = BUILDER
            .comment("Allow Cirno to be targeted and hit by players and projectiles. When false she has no hitbox interaction.")
            .define("cirnoIsPickable", true);

    static { BUILDER.pop(); }

    static { BUILDER.comment("Targeting and attack behaviour").push("Combat"); }

    private static final ForgeConfigSpec.BooleanValue CIRNO_RETARGET_ATTACKERS = BUILDER
            .comment("When something attacks Cirno, redirect that entity's attack target to the owner player instead.")
            .define("cirnoRetargetAttackers", false);

    private static final ForgeConfigSpec.BooleanValue CIRNO_ATTACK_SEE_THROUGH_BLOCKS = BUILDER
            .comment("Let Cirno see and attack targets through blocks (ignores block line-of-sight for her " +
                    "target detection/attack checks). Only ever takes effect while BOTH cirnoFollowsOwner and " +
                    "cirnoDisablePhysicsWhileFollowing are true, because that's the only combination that can " +
                    "leave her clipped inside a block (the follow point can end up in wall geometry once physics " +
                    "collision is off). Without this, a maid whose eyes are inside a solid block fails every " +
                    "line-of-sight check and will never even attempt to attack, unlike vanilla mobs which can " +
                    "usually still see out even when partially stuck. Has no effect in any other configuration.")
            .define("cirnoAttackSeeThroughBlocks", true);

    static { BUILDER.pop(); }

    static { BUILDER.comment("Death, revive and tombstone behaviour").push("Death"); }

    private static final ForgeConfigSpec.BooleanValue CIRNO_CAN_DIE = BUILDER
            .comment("Allow Cirno to actually die when her health reaches 0. When false she is invulnerable (default).")
            .define("cirnoCanDie", true);

    private static final ForgeConfigSpec.IntValue CIRNO_REVIVE_COOLDOWN = BUILDER
            .comment("Ticks before Cirno revives after dying (only used when cirnoCanDie = true). " +
                    "With cirnoSpawnTombstoneOnDeath = false this is the automatic revive timer. " +
                    "With cirnoSpawnTombstoneOnDeath = true it is the delay before THIS mod's own summon " +
                    "(H key, panel, /cirno, curio) may bring her back from a film. It never delays a revive " +
                    "done through TLM's own items (film/photo/slab click, tombstone + Shrine). Default 12000 = 10min.")
            .defineInRange("cirnoReviveCooldown", 12000, 0, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue CIRNO_SPAWN_TOMBSTONE_ON_DEATH = BUILDER
            .comment("Only used when cirnoCanDie = true. When false (default), Cirno never leaves a TLM " +
                    "tombstone behind: on death she is snapshotted into the curio or player body and automatically " +
                    "revived after cirnoReviveCooldown ticks, exactly like the player-toggled Hidden state. " +
                    "When true, she dies for real instead — TLM's normal maid death handling runs and a " +
                    "tombstone is dropped like any other Touhou Little Maid — and the automatic maid revive " +
                    "described above is disabled entirely (cirnoReviveCooldown no longer applies); getting " +
                    "her back is then whatever TLM's own tombstone/revival process requires.")
            .define("cirnoSpawnTombstoneOnDeath", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_CAN_SUMMON_FROM_FILM = BUILDER
            .comment("Only relevant when cirnoSpawnTombstoneOnDeath = true. Whether THIS mod's own summon " +
                    "(H key, panel, /cirno, curio) may revive Cirno from a touhou_little_maid:film that holds " +
                    "her. When false, our summon refuses and leaves her stored. When true, it is allowed, subject " +
                    "to cirnoReviveCooldown. This setting and the cooldown apply ONLY to our own summon: reviving " +
                    "her through TLM itself (film/photo/slab click, tombstone + Shrine) is never blocked or " +
                    "delayed, and she always comes back as Cirno so no duplicate or orphaned slot is created.")
            .define("cirnoCanSummonFromFilm", true);

    static { BUILDER.pop(); }

    static { BUILDER.comment("Collision and physics while following").push("Physics"); }

    private static final ForgeConfigSpec.BooleanValue CIRNO_DISABLE_MOB_COLLISION = BUILDER
            .comment("Disable mob collision for Cirno. When false (default), collision is only disabled while cirnoFollowsOwner is active. When true, collision is always disabled.")
            .define("cirnoDisableMobCollision", false);

    private static final ForgeConfigSpec.BooleanValue CIRNO_DISABLE_PHYSICS_WHILE_FOLLOWING = BUILDER
            .comment("Disable block/physics collision for Cirno only while cirnoFollowsOwner is active. " +
                    "She's fully attached to the player's position in that mode anyway, so this just lets " +
                    "her pass through walls/blocks instead of getting stuck or jittering against them " +
                    "when the follow point ends up inside geometry. Has no effect when cirnoFollowsOwner is false.")
            .define("cirnoDisablePhysicsWhileFollowing", true);

    static { BUILDER.pop(); }

    static { BUILDER.comment("Airborne pose animations while following").push("Animation"); }

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

    static { BUILDER.pop(); }

    static { BUILDER.comment("Unload protection and NBT autosave").push("Persistence"); }

    private static final ForgeConfigSpec.BooleanValue CIRNO_UNLOAD_PROTECTION = BUILDER
            .comment("When true, Cirno cannot be removed by chunk unloads, server cleanup, or third-party mods. " +
                    "Only trusted paths — the H key, TLM smart slab/photo, the death/revive system, and dimension " +
                    "change — are allowed to discard her. Mirrors what ANChorCore does for anchor-equipped maids. " +
                    "Has no effect on /kill, the death system when cirnoCanDie = true, or explicit hide/store actions.")
            .define("cirnoUnloadProtection", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_AUTO_SAVE_ENABLED = BUILDER
            .comment("Periodically back up Cirno's NBT (inventory, equipment, etc.) to a durable location " +
                    "(the owning player's data, and the Cirno item when she's linked to one) while she's " +
                    "alive, in addition to saving on every known removal path. This is a safety net so her " +
                    "items survive even if she's ever discarded through a path this mod doesn't explicitly " +
                    "handle (another mod, a crash, /kill, etc.).")
            .define("cirnoAutoSaveEnabled", true);

    private static final ForgeConfigSpec.IntValue CIRNO_AUTO_SAVE_INTERVAL_TICKS = BUILDER
            .comment("How often (in ticks) to run the autosave described above. Default 200 = every 10s.")
            .defineInRange("cirnoAutoSaveIntervalTicks", 200, 20, 12000);

    static { BUILDER.pop(); }

    static { BUILDER.pop(); }

    static { BUILDER.comment("Princess-carry ride/flight mount (saddle Cirno and be carried)").push("Carry"); }

    private static final ForgeConfigSpec.BooleanValue CIRNO_MOUNT_ENABLED = BUILDER
            .comment("Master switch: let players saddle Cirno and be carried princess-style, controlling her "
                    + "movement including flight (double-tap jump). Both saddling her the first time and mounting "
                    + "her after that use the \"Mount Cirno\" keybind (rebindable in Controls) while holding a "
                    + "saddle, rather than any right-click gesture, so right-click stays entirely free for her "
                    + "normal maid interactions.")
            .define("cirnoMountEnabled", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_MOUNT_OWNER_ONLY = BUILDER
            .comment("Only this Cirno's owner (the player whose curio slot spawned her) may saddle or ride her.")
            .define("cirnoMountOwnerOnly", true);

    private static final ForgeConfigSpec.BooleanValue CIRNO_MOUNT_CAN_FLY = BUILDER
            .comment("Whether Cirno can fly while carrying her rider (double-tap jump to toggle flight). "
                    + "When false, she can only walk/jump on the ground while carrying, the rider is never granted "
                    + "flight, and the whole Carry -> Flight and Carry -> Speed fly settings are ignored. "
                    + "Carrying itself is still controlled by cirnoMountEnabled.")
            .define("cirnoMountCanFly", true);

    static { BUILDER.comment("Ground and flight movement speeds").push("Speed"); }

    private static final ForgeConfigSpec.DoubleValue CIRNO_MOUNT_WALK_SPEED = BUILDER
            .comment("Ground movement speed while she's carrying her rider but not flying (not sprinting). "
                    + "Lowered from the old 0.35 default so a plain, un-sprinted carry feels like a walk rather "
                    + "than a jog; double-tap W to sprint still speeds her up on top of this.")
            .defineInRange("cirnoMountWalkSpeed", 0.2, 0.01, 5.0);

    private static final ForgeConfigSpec.DoubleValue CIRNO_MOUNT_FLY_SPEED = BUILDER
            .comment("Horizontal movement speed while flying with a rider.")
            .defineInRange("cirnoMountFlySpeed", 0.55, 0.01, 5.0);

    private static final ForgeConfigSpec.DoubleValue CIRNO_MOUNT_FLY_FAST_SPEED = BUILDER
            .comment("Horizontal movement speed while flying with a rider AND holding sprint (Ctrl by default) — "
                    + "the same 'fly fast' gesture creative mode uses.")
            .defineInRange("cirnoMountFlyFastSpeed", 1.1, 0.01, 5.0);

    static { BUILDER.pop(); }

    static { BUILDER.comment("Vertical flight handling and dismount safety").push("Flight"); }

    private static final ForgeConfigSpec.DoubleValue CIRNO_MOUNT_FLY_VERTICAL_SPEED = BUILDER
            .comment("Maximum climb speed (blocks/tick) while flying and holding jump.")
            .defineInRange("cirnoMountFlyVerticalSpeed", 0.5, 0.01, 5.0);

    private static final ForgeConfigSpec.DoubleValue CIRNO_MOUNT_FLY_VERTICAL_ACCEL = BUILDER
            .comment("Vertical speed gained per tick while holding jump during flight, up to cirnoMountFlyVerticalSpeed.")
            .defineInRange("cirnoMountFlyVerticalAccel", 0.08, 0.001, 1.0);

    private static final ForgeConfigSpec.DoubleValue CIRNO_MOUNT_FLY_HOVER_DAMPING = BUILDER
            .comment("Multiplier applied to vertical speed each tick jump isn't held while flying. Lower = settles "
                    + "to a hover faster; closer to 1.0 = glides/sinks more gradually.")
            .defineInRange("cirnoMountFlyHoverDamping", 0.8, 0.0, 0.999);

    private static final ForgeConfigSpec.IntValue CIRNO_MOUNT_DISMOUNT_SLOW_FALL_TICKS = BUILDER
            .comment("Ticks of Slow Falling granted on dismount if the rider was flying, so bailing out mid-air "
                    + "isn't an instant death sentence. Set to 0 to disable.")
            .defineInRange("cirnoMountDismountSlowFallTicks", 60, 0, Integer.MAX_VALUE);

    static { BUILDER.pop(); }

    static { BUILDER.comment("Where the rider is held relative to Cirno").push("Position"); }

    private static final ForgeConfigSpec.DoubleValue CIRNO_MOUNT_CARRY_FORWARD_OFFSET = BUILDER
            .comment("Princess-carry position: how far in front of her the rider is held, in blocks.")
            .defineInRange("cirnoMountCarryForwardOffset", 0.5, -2.0, 2.0);

    private static final ForgeConfigSpec.DoubleValue CIRNO_MOUNT_CARRY_SIDE_OFFSET = BUILDER
            .comment("Princess-carry position: sideways offset (positive = her right side), in blocks.")
            .defineInRange("cirnoMountCarrySideOffset", 0.0, -2.0, 2.0);

    private static final ForgeConfigSpec.DoubleValue CIRNO_MOUNT_CARRY_UP_OFFSET = BUILDER
            .comment("Princess-carry position: vertical offset from Cirno's feet. Negative values place the rider below her.")
            .defineInRange("cirnoMountCarryUpOffset", 0, -2.0, 3.0);

    static { BUILDER.pop(); }

    static { BUILDER.pop(); }

    static { BUILDER.comment("Telekinesis abilities").push("Telekinesis"); }

    static { BUILDER.comment("Automatic shield on fatal damage / low HP").push("Shield"); }

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

    static { BUILDER.pop(); }

    static { BUILDER.comment("Active mob control mode (keybind, right-click a mob)").push("Control"); }

    private static final ForgeConfigSpec.BooleanValue TELEKINESIS_CONTROL_ENABLED_VALUE = BUILDER
            .comment("Enable active telekinesis control mode (keybind-toggled, right-click mob to control it)")
            .define("telekinesisControlEnabled", true);

    private static final ForgeConfigSpec.IntValue TELEKINESIS_CONTROL_DURATION_VALUE = BUILDER
            .comment("Duration a mob stays under telekinesis control in ticks (default 200 = 10s)")
            .defineInRange("telekinesisControlDuration", 200, 20, Integer.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue TELEKINESIS_CONTROL_COOLDOWN_VALUE = BUILDER
            .comment("Cooldown before telekinesis control can be used again in ticks (default 600 = 30s)")
            .defineInRange("telekinesisControlCooldown", 600, 0, Integer.MAX_VALUE);

    static { BUILDER.pop(); }

    static { BUILDER.pop(); }

    static { BUILDER.comment("Survival crafting availability of altar recipes").push("Recipes"); }

    public static final ForgeConfigSpec.BooleanValue OATHPIN_CRAFTABLE = BUILDER
            .comment("Whether the Oathpin altar recipe is available in survival. It remains obtainable through commands when disabled.")
            .define("oathpinCraftable", true);

    public static final ForgeConfigSpec.BooleanValue CIRNO_VARIANT_RECIPES_CRAFTABLE = BUILDER
            .comment("Whether the Cirno, Solyn, Spirit, and Tatsumaki altar recipes are available in survival. " +
                    "When disabled, the variants remain obtainable through commands/other item sources.")
            .define("cirnoVariantRecipesCraftable", true);

    static { BUILDER.pop(); }

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
            cirnoSpawnTombstoneOnDeath = CIRNO_SPAWN_TOMBSTONE_ON_DEATH.get();
            cirnoCanSummonFromFilm = CIRNO_CAN_SUMMON_FROM_FILM.get();
            cirnoIsPickable = CIRNO_IS_PICKABLE.get();
            cirnoDisableMobCollision = CIRNO_DISABLE_MOB_COLLISION.get();
            cirnoDisablePhysicsWhileFollowing = CIRNO_DISABLE_PHYSICS_WHILE_FOLLOWING.get();
            cirnoAttackSeeThroughBlocks = CIRNO_ATTACK_SEE_THROUGH_BLOCKS.get();
            cirnoFlyAnimationEnabled = CIRNO_FLY_ANIMATION_ENABLED.get();
            cirnoFlyDelayTicks = CIRNO_FLY_DELAY_TICKS.get();
            cirnoMountEnabled = CIRNO_MOUNT_ENABLED.get();
            cirnoMountOwnerOnly = CIRNO_MOUNT_OWNER_ONLY.get();
            cirnoMountCanFly = CIRNO_MOUNT_CAN_FLY.get();
            cirnoMountWalkSpeed = CIRNO_MOUNT_WALK_SPEED.get();
            cirnoMountFlySpeed = CIRNO_MOUNT_FLY_SPEED.get();
            cirnoMountFlyFastSpeed = CIRNO_MOUNT_FLY_FAST_SPEED.get();
            cirnoMountFlyVerticalSpeed = CIRNO_MOUNT_FLY_VERTICAL_SPEED.get();
            cirnoMountFlyVerticalAccel = CIRNO_MOUNT_FLY_VERTICAL_ACCEL.get();
            cirnoMountFlyHoverDamping = CIRNO_MOUNT_FLY_HOVER_DAMPING.get();
            cirnoMountDismountSlowFallTicks = CIRNO_MOUNT_DISMOUNT_SLOW_FALL_TICKS.get();
            cirnoMountCarryForwardOffset = CIRNO_MOUNT_CARRY_FORWARD_OFFSET.get();
            cirnoMountCarrySideOffset = CIRNO_MOUNT_CARRY_SIDE_OFFSET.get();
            cirnoMountCarryUpOffset = CIRNO_MOUNT_CARRY_UP_OFFSET.get();
            cirnoAutoSaveEnabled = CIRNO_AUTO_SAVE_ENABLED.get();
            cirnoAutoSaveIntervalTicks = CIRNO_AUTO_SAVE_INTERVAL_TICKS.get();
            cirnoUnloadProtection = CIRNO_UNLOAD_PROTECTION.get();
            cirnoMaxLeashDistance = CIRNO_MAX_LEASH_DISTANCE.get();
            cirnoPartOfOwner = CIRNO_PART_OF_OWNER.get();
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