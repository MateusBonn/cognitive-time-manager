package com.cognitivemanager.domain.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value object carrying the outcome of an interruption evaluation.
 *
 * Produced by {@link com.cognitivemanager.domain.engine.InterruptionHandler} and
 * consumed by the application layer to persist blocks and push WebSocket notifications.
 *
 * Uses named static factory methods to express intent at the call site.
 *
 * Per RNF03: no framework annotations.
 */
public final class InterruptionResult {

    private final InterruptionScenario scenario;
    private final Meeting interruptingMeeting;
    private final List<TimeBlock> proposedBlocks;
    private final long windowMinutes;
    private final String message;
    private final LocalDateTime evaluatedAt;

    private InterruptionResult(
            InterruptionScenario scenario,
            Meeting interruptingMeeting,
            List<TimeBlock> proposedBlocks,
            long windowMinutes,
            String message) {
        this.scenario = Objects.requireNonNull(scenario);
        this.interruptingMeeting = Objects.requireNonNull(interruptingMeeting);
        this.proposedBlocks = List.copyOf(proposedBlocks); // defensive immutable copy
        this.windowMinutes = windowMinutes;
        this.message = Objects.requireNonNull(message);
        this.evaluatedAt = LocalDateTime.now();
    }

    // -------------------------------------------------------------------------
    // DESIGN PATTERN: Factory Pattern — semantic constructors
    // -------------------------------------------------------------------------

    /**
     * Factory for Cenário 1: Emergency Cool-down (Janela ≤ 25 min).
     * Deep Work interrupted immediately; developer enters FORCED_REST.
     */
    public static InterruptionResult emergencyCooldown(
            Meeting meeting, List<TimeBlock> forcedRestBlocks, long windowMinutes) {
        return new InterruptionResult(
                InterruptionScenario.EMERGENCY_COOLDOWN,
                meeting,
                forcedRestBlocks,
                windowMinutes,
                String.format(
                        "EMERGENCY: Reunião '%s' em %d min. Deep Work interrompido. " +
                        "Entrando em Descanso Forçado imediatamente até %s.",
                        meeting.getTitle(), windowMinutes, meeting.getStartTime()));
    }

    /**
     * Factory for Cenário 2: Decision Window (25 min < Janela < 105 min).
     * Blocks represent Option A (switch to Shallow). Option B blocks are computed
     * on-demand when the developer chooses PERSIST_DEEP_WORK.
     */
    public static InterruptionResult decisionRequired(
            Meeting meeting, List<TimeBlock> optionABlocks, long windowMinutes) {
        LocalDateTime mandatoryRestAt = meeting.getStartTime()
                .minusMinutes(com.cognitivemanager.domain.constants.EngineConstants.PRE_MEETING_REST);
        return new InterruptionResult(
                InterruptionScenario.DECISION_REQUIRED,
                meeting,
                optionABlocks,
                windowMinutes,
                String.format(
                        "DECISÃO NECESSÁRIA: Reunião '%s' em %d min. " +
                        "Opção A: Mudar para Shallow Work agora. " +
                        "Opção B: Continuar Deep Work — descanso obrigatório às %s.",
                        meeting.getTitle(), windowMinutes, mandatoryRestAt.toLocalTime()));
    }

    /**
     * Factory for Cenário 3: Safe Auto-truncation (Janela ≥ 105 min).
     * Engine auto-injects rest at M_start − 20 min; no user decision required.
     */
    public static InterruptionResult safeAutoTruncation(
            Meeting meeting, List<TimeBlock> autoBlocks, long windowMinutes) {
        return new InterruptionResult(
                InterruptionScenario.SAFE_AUTO_TRUNCATION,
                meeting,
                autoBlocks,
                windowMinutes,
                String.format(
                        "JANELA SEGURA: Reunião '%s' em %d min. " +
                        "Deep Work auto-truncado. Descanso de 20 min agendado às %s.",
                        meeting.getTitle(), windowMinutes,
                        meeting.getStartTime()
                               .minusMinutes(com.cognitivemanager.domain.constants.EngineConstants.PRE_MEETING_REST)
                               .toLocalTime()));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public InterruptionScenario getScenario() { return scenario; }
    public Meeting getInterruptingMeeting() { return interruptingMeeting; }
    public List<TimeBlock> getProposedBlocks() { return proposedBlocks; }
    public long getWindowMinutes() { return windowMinutes; }
    public String getMessage() { return message; }
    public LocalDateTime getEvaluatedAt() { return evaluatedAt; }

    /** True when this result requires an explicit developer choice before being applied. */
    public boolean requiresUserDecision() {
        return scenario == InterruptionScenario.DECISION_REQUIRED;
    }

    @Override
    public String toString() {
        return String.format("InterruptionResult{scenario=%s, window=%d min, blocks=%d, evaluatedAt=%s}",
                scenario, windowMinutes, proposedBlocks.size(), evaluatedAt);
    }
}
