package net.zhaiji.catburger.client.gui;

/**
 * All visual constants that differ between Quick-Select UI themes.
 * Add new themes by creating additional static instances below.
 */
public record GuiTheme(
        // ── Panel ─────────────────────────────────────────────────────────────
        int panelBg,
        int panelBgLite,
        // ── Borders ───────────────────────────────────────────────────────────
        int borderOuter,
        int borderInner,
        int borderGlow,
        // ── Cards ─────────────────────────────────────────────────────────────
        int cardNormal,
        int cardHover,
        int accentBar,      // left-edge glow bar on hover
        // ── Text ──────────────────────────────────────────────────────────────
        int colTitle,
        int colLabel,
        int colHint,
        // ── State indicators ──────────────────────────────────────────────────
        int colSummoned,
        int colStored,
        int colEmpty,
        // ── Buttons ───────────────────────────────────────────────────────────
        int btnStore,
        int btnStoreHov,
        int btnSummon,
        int btnSummonHov,
        int btnAdd,
        int btnAddHov,
        int btnText,
        int btnBorder,
        // ── Icon tints [r,g,b] × 3 states (summoned, stored, empty) ──────────
        float[] tintSummoned,
        float[] tintStored,
        float[] tintEmpty,
        // ── Corner / header decorations ───────────────────────────────────────
        String decoCorner,   // character drawn at panel corners
        String decoHeader,   // flanking decoration in the title
        String decoBulletCurio,
        String decoBulletSlot,
        String decoStateDot, // prefix before state text
        // ── Header shimmer colour ─────────────────────────────────────────────
        int shimmerColor,
        // ── Background glow behind the whole panel ────────────────────────────
        int outerGlow
) {

    // ════════════════════════════════════════════════════════════════════════
    //  CIRNO — ice-blue, midnight-dark, snowflake motifs
    // ════════════════════════════════════════════════════════════════════════
    public static final GuiTheme CIRNO = new GuiTheme(
            /* panelBg       */ 0xEA0A0F2A,
            /* panelBgLite   */ 0xEA0E1438,
            /* borderOuter   */ 0xFF8BBFE8,
            /* borderInner   */ 0xFF3A6BA8,
            /* borderGlow    */ 0xAAC8E8FF,
            /* cardNormal    */ 0xBB0D1535,
            /* cardHover     */ 0xCC1A2E60,
            /* accentBar     */ 0xAAC8E8FF,
            /* colTitle      */ 0xFFDDF4FF,
            /* colLabel      */ 0xFFBBDDFF,
            /* colHint       */ 0x88A0C8FF,
            /* colSummoned   */ 0xFF66FFBB,
            /* colStored     */ 0xFFFFE066,
            /* colEmpty      */ 0xFF8899AA,
            /* btnStore      */ 0xCC8B1A2A,
            /* btnStoreHov   */ 0xCCCC2233,
            /* btnSummon     */ 0xCC1A5C36,
            /* btnSummonHov  */ 0xCC22AA55,
            /* btnAdd        */ 0xCC1A3A6E,
            /* btnAddHov     */ 0xCC2255AA,
            /* btnText       */ 0xFFEEF8FF,
            /* btnBorder     */ 0x99AADDFF,
            /* tintSummoned  */ new float[]{ 0.55f, 1.00f, 0.75f },
            /* tintStored    */ new float[]{ 1.00f, 0.92f, 0.45f },
            /* tintEmpty     */ new float[]{ 0.45f, 0.50f, 0.60f },
            /* decoCorner    */ "❄",
            /* decoHeader    */ "✦",
            /* decoBulletCurio */ "❄",
            /* decoBulletSlot  */ "◆",
            /* decoStateDot  */ "◇ ",
            /* shimmerColor  */ 0x0CFFFFFF,
            /* outerGlow     */ 0x2233AAFF
    );

    // ════════════════════════════════════════════════════════════════════════
    //  HAKUREI — Reimu's shrine-maiden palette.
    //  Warm deep-crimson panels, gold double-borders, ofuda-paper feel,
    //  cherry-blossom (✿) and yin-yang (☯) motifs, vermillion accent bars.
    // ════════════════════════════════════════════════════════════════════════
    public static final GuiTheme HAKUREI = new GuiTheme(
            /* panelBg       */ 0xEA1A0508,   // very dark blood-red
            /* panelBgLite   */ 0xEA25080C,   // slightly lighter crimson
            /* borderOuter   */ 0xFFE8C44A,   // shrine gold
            /* borderInner   */ 0xFFB8882A,   // darker gold
            /* borderGlow    */ 0xFFFFDD88,   // warm gold glow on hover
            /* cardNormal    */ 0xBB1C0608,   // dark crimson card bg
            /* cardHover     */ 0xCC3D0E12,   // hover: brighter crimson
            /* accentBar     */ 0xFFFFDD88,   // gold accent bar
            /* colTitle      */ 0xFFFFF0CC,   // warm parchment-white
            /* colLabel      */ 0xFFFFDDAA,   // warm gold text
            /* colHint       */ 0x88DDAA66,   // muted gold hint
            /* colSummoned   */ 0xFFFF8888,   // soft red (yin)
            /* colStored     */ 0xFFFFCC44,   // gold-amber
            /* colEmpty      */ 0xFF997766,   // muted warm brown
            /* btnStore      */ 0xCC6B0A0E,   // deep crimson
            /* btnStoreHov   */ 0xCCAA1018,
            /* btnSummon     */ 0xCC7A5000,   // dark gold/amber
            /* btnSummonHov  */ 0xCCCC8800,
            /* btnAdd        */ 0xCC3A1A00,   // dark warm brown
            /* btnAddHov     */ 0xCC662800,
            /* btnText       */ 0xFFFFF5DD,   // parchment
            /* btnBorder     */ 0x99E8C44A,   // gold
            /* tintSummoned  */ new float[]{ 1.00f, 0.60f, 0.60f },
            /* tintStored    */ new float[]{ 1.00f, 0.85f, 0.35f },
            /* tintEmpty     */ new float[]{ 0.55f, 0.40f, 0.35f },
            /* decoCorner    */ "✿",
            /* decoHeader    */ "☯",
            /* decoBulletCurio */ "☯",
            /* decoBulletSlot  */ "✿",
            /* decoStateDot  */ "» ",
            /* shimmerColor  */ 0x0CFFEE88,   // warm gold shimmer
            /* outerGlow     */ 0x22CC4400    // faint red outer glow
    );

    /** Returns the theme matching the config string, defaulting to CIRNO. */
    public static GuiTheme fromConfig(String name) {
        if ("HAKUREI".equalsIgnoreCase(name)) return HAKUREI;
        // WHEEL has its own render path; return CIRNO as a fallback colour source
        return CIRNO;
    }

    /** True when the config value selects the radial wheel layout. */
    public static boolean isWheel(String name) {
        return "WHEEL".equalsIgnoreCase(name);
    }
}
