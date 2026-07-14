package com.cognitivemanager.domain.constants;

/**
 * Biological and cognitive constants that govern the time allocation engine.
 *
 * Values derived from ultradian rhythm research (Kleitman, 1963) and flow-state
 * psychology (Csikszentmihalyi). The 85–125 min window maps the natural cycle of
 * sustained focused attention before a mandatory recovery phase is required.
 *
 * These constants belong to the DOMAIN layer and carry zero infrastructure dependencies.
 * They are the single source of truth for all allocation algorithms (RF02, RF03, RF05).
 */
public final class EngineConstants {

    private EngineConstants() {
        throw new UnsupportedOperationException("Constant class — do not instantiate");
    }

    // -------------------------------------------------------------------------
    // Deep Work window (minutes)
    // -------------------------------------------------------------------------

    /** D_min — minimum Deep Work block duration (minutes). Below this, flow cannot be reached. */
    public static final int D_MIN = 85;

    /** D_max — maximum Deep Work block duration (minutes). Beyond this, cognitive degradation begins. */
    public static final int D_MAX = 125;

    // -------------------------------------------------------------------------
    // Rest / recovery window (minutes)
    // -------------------------------------------------------------------------

    /** R_min — minimum cognitive rest duration (minutes). Used on dense/fragmented days. */
    public static final int R_MIN = 15;

    /** R_max — maximum cognitive rest duration (minutes). Used on sparse/open days. */
    public static final int R_MAX = 25;

    // -------------------------------------------------------------------------
    // Daily cumulative limit
    // -------------------------------------------------------------------------

    /**
     * L_max — maximum cumulative daily Deep Work (minutes).
     * Once reached, all remaining free windows are classified as SHALLOW_WORK (RF03).
     */
    public static final int L_MAX = 250;

    // -------------------------------------------------------------------------
    // Interruption window thresholds (minutes)
    // -------------------------------------------------------------------------

    /**
     * EMERGENCY_THRESHOLD — if the window to the next meeting is at or below this value,
     * an emergency cool-down is triggered immediately (RF05: Cenário 1, Janela ≤ 25 min).
     */
    public static final int EMERGENCY_THRESHOLD = 25;

    /**
     * SAFE_THRESHOLD — if the window to the next meeting is at or above this value,
     * the engine performs a safe auto-truncation (RF04: Cenário 3, Janela ≥ 105 min).
     * Values strictly between EMERGENCY_THRESHOLD and SAFE_THRESHOLD trigger the
     * decision window (Cenário 2).
     */
    public static final int SAFE_THRESHOLD = 105;

    /**
     * PRE_MEETING_REST — mandatory pre-meeting cognitive rest injected by the engine
     * in Cenário 2 (Option B) and Cenário 3 (auto-truncation). Always 20 minutes.
     */
    public static final int PRE_MEETING_REST = 20;
}
