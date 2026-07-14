package com.cognitivemanager.domain.model;

import com.cognitivemanager.domain.constants.EngineConstants;

/**
 * Classifies the three possible interruption scenarios based on the size of the
 * time window between the current instant and the upcoming meeting start time.
 *
 * Thresholds (per specification §4):
 * <pre>
 *   Janela ≤ 25 min                 → EMERGENCY_COOLDOWN
 *   25 min < Janela < 105 min       → DECISION_REQUIRED
 *   Janela ≥ 105 min                → SAFE_AUTO_TRUNCATION
 * </pre>
 *
 * The exact values are defined in {@link EngineConstants}.
 */
public enum InterruptionScenario {

    /**
     * Cenário 1: Janela ≤ {@value EngineConstants#EMERGENCY_THRESHOLD} min.
     * Deep Work is interrupted immediately. Accumulated Deep Work is counted
     * to the exact second. Developer is forced into FORCED_REST until the meeting.
     */
    EMERGENCY_COOLDOWN,

    /**
     * Cenário 2: {@value EngineConstants#EMERGENCY_THRESHOLD} min < Janela
     *             < {@value EngineConstants#SAFE_THRESHOLD} min.
     * Developer receives two options:
     * <ol>
     *   <li>SWITCH_TO_SHALLOW — transition to SHALLOW_WORK immediately.</li>
     *   <li>PERSIST_DEEP_WORK — continue DEEP_WORK; mandatory rest begins
     *       at M_start − {@value EngineConstants#PRE_MEETING_REST} min.</li>
     * </ol>
     */
    DECISION_REQUIRED,

    /**
     * Cenário 3: Janela ≥ {@value EngineConstants#SAFE_THRESHOLD} min.
     * Engine auto-truncates the current Deep Work block at
     * M_start − {@value EngineConstants#PRE_MEETING_REST} min, injects
     * a {@value EngineConstants#PRE_MEETING_REST}-min FORCED_REST block,
     * and marks the meeting slot as SHALLOW_WORK.
     */
    SAFE_AUTO_TRUNCATION
}
