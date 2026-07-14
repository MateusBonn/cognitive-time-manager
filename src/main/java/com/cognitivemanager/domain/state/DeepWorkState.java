package com.cognitivemanager.domain.state;

import com.cognitivemanager.domain.constants.EngineConstants;
import com.cognitivemanager.domain.model.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DESIGN PATTERN: State Pattern — Concrete State: DEEP_WORK
 *
 * The most cognitively demanding state. This state contains the full three-scenario
 * interruption evaluation logic specified in §4 of the requirements.
 *
 * Invariant: {@code blockStartTime} is always set when entering this state,
 * enabling precise accumulation calculation at interruption time (RF03).
 *
 * Transitions:
 * <ul>
 *   <li>Deep Work block completes normally → {@link ForcedRestState}</li>
 *   <li>Meeting ≤ 25 min away → emergency exit → {@link ForcedRestState}</li>
 *   <li>Meeting 25–105 min away → developer decision (Option A: {@link ShallowWorkState},
 *       Option B: stays in {@link DeepWorkState} until auto-rest is triggered)</li>
 *   <li>Meeting ≥ 105 min away → auto-truncated → {@link ForcedRestState}</li>
 * </ul>
 */
public final class DeepWorkState implements DeveloperState {

    private final LocalDateTime blockStartTime;
    private final int preBlockAccumulatedDeepWork;

    /**
     * @param blockStartTime                Exact instant this Deep Work block started
     * @param preBlockAccumulatedDeepWork   Deep Work minutes accumulated BEFORE this block
     */
    public DeepWorkState(LocalDateTime blockStartTime, int preBlockAccumulatedDeepWork) {
        this.blockStartTime = Objects.requireNonNull(blockStartTime, "blockStartTime must not be null");
        this.preBlockAccumulatedDeepWork = Math.max(0, preBlockAccumulatedDeepWork);
    }

    @Override
    public CognitiveState getCognitiveState() {
        return CognitiveState.DEEP_WORK;
    }

    // -------------------------------------------------------------------------
    // Core interruption logic — three scenarios (§4)
    // -------------------------------------------------------------------------

    @Override
    public InterruptionResult handleMeetingInterruption(
            Meeting urgentMeeting, LocalDateTime now, int accumulatedDeepWork) {

        Objects.requireNonNull(urgentMeeting, "urgentMeeting must not be null");
        Objects.requireNonNull(now, "now must not be null");

        long windowMinutes = ChronoUnit.MINUTES.between(now, urgentMeeting.getStartTime());

        if (windowMinutes <= EngineConstants.EMERGENCY_THRESHOLD) {
            return scenarioOne_EmergencyCooldown(urgentMeeting, now, windowMinutes);
        } else if (windowMinutes < EngineConstants.SAFE_THRESHOLD) {
            return scenarioTwo_DecisionWindow(urgentMeeting, now, windowMinutes);
        } else {
            return scenarioThree_SafeAutoTruncation(urgentMeeting, now, windowMinutes);
        }
    }

    /**
     * Cenário 1: Janela ≤ 25 min — Emergency Cool-down (RF05).
     * <p>
     * Actions:
     * <ol>
     *   <li>Interrupts Deep Work at the exact current second.</li>
     *   <li>Computes elapsed Deep Work minutes and records as an interrupted block.</li>
     *   <li>Creates a FORCED_REST block covering the remaining window to the meeting.</li>
     *   <li>Creates a SHALLOW_WORK block for the meeting itself.</li>
     * </ol>
     *
     * The accumulated time from the interrupted Deep Work is still counted toward
     * the daily limit (RF03: "O acumulado de Deep Work é contabilizado até o exato segundo atual").
     */
    private InterruptionResult scenarioOne_EmergencyCooldown(
            Meeting meeting, LocalDateTime now, long windowMinutes) {

        // Elapsed minutes in this Deep Work block (truncated to whole minutes)
        int elapsedDeepWorkMinutes = (int) ChronoUnit.MINUTES.between(blockStartTime, now);

        List<TimeBlock> blocks = new ArrayList<>();

        // Record the interrupted Deep Work (counted toward L_max)
        if (elapsedDeepWorkMinutes > 0) {
            blocks.add(TimeBlock.createInterrupted(blockStartTime, elapsedDeepWorkMinutes, CognitiveState.DEEP_WORK));
        }

        // Forced rest from now until meeting start
        if (windowMinutes > 0) {
            blocks.add(TimeBlock.create(now, (int) windowMinutes, CognitiveState.FORCED_REST));
        }

        // Meeting block as SHALLOW_WORK
        blocks.add(TimeBlock.create(
                meeting.getStartTime(),
                meeting.getDurationMinutes(),
                CognitiveState.SHALLOW_WORK));

        return InterruptionResult.emergencyCooldown(meeting, blocks, windowMinutes);
    }

    /**
     * Cenário 2: 25 min < Janela < 105 min — Decision Window (RF04).
     * <p>
     * Returns Option A blocks (immediate switch to Shallow Work).
     * Option B blocks are computed by the application service when the developer
     * selects PERSIST_DEEP_WORK, because the exact transition times depend on
     * the developer's decision moment.
     * <p>
     * Option A: Shallow Work from now until meeting start.
     * Option B (not pre-computed here):
     *   - Continue Deep Work until M_start − 20 min
     *   - FORCED_REST for exactly 20 min
     *   - Meeting as SHALLOW_WORK
     */
    private InterruptionResult scenarioTwo_DecisionWindow(
            Meeting meeting, LocalDateTime now, long windowMinutes) {

        // Option A blocks: switch to Shallow Work immediately
        List<TimeBlock> optionABlocks = new ArrayList<>();
        optionABlocks.add(TimeBlock.create(now, (int) windowMinutes, CognitiveState.SHALLOW_WORK));
        optionABlocks.add(TimeBlock.create(
                meeting.getStartTime(),
                meeting.getDurationMinutes(),
                CognitiveState.SHALLOW_WORK));

        return InterruptionResult.decisionRequired(meeting, optionABlocks, windowMinutes);
    }

    /**
     * Cenário 3: Janela ≥ 105 min — Safe Auto-truncation.
     * <p>
     * Actions (no developer decision required):
     * <ol>
     *   <li>Auto-truncates Deep Work at M_start − 20 min.</li>
     *   <li>Injects exactly 20 min of FORCED_REST.</li>
     *   <li>Assigns the meeting slot as SHALLOW_WORK.</li>
     * </ol>
     *
     * Remaining window = M_start − now − 20 min (the Deep Work extension).
     * This remaining Deep Work is counted toward the daily limit.
     */
    private InterruptionResult scenarioThree_SafeAutoTruncation(
            Meeting meeting, LocalDateTime now, long windowMinutes) {

        LocalDateTime deepWorkEnd = meeting.getStartTime().minusMinutes(EngineConstants.PRE_MEETING_REST);
        long deepWorkExtension = ChronoUnit.MINUTES.between(now, deepWorkEnd);

        List<TimeBlock> blocks = new ArrayList<>();

        // Continuation of Deep Work until the auto-truncation point
        if (deepWorkExtension > 0) {
            blocks.add(TimeBlock.create(now, (int) deepWorkExtension, CognitiveState.DEEP_WORK));
        }

        // Mandatory 20-min pre-meeting cognitive rest
        blocks.add(TimeBlock.create(deepWorkEnd, EngineConstants.PRE_MEETING_REST, CognitiveState.FORCED_REST));

        // Meeting as SHALLOW_WORK
        blocks.add(TimeBlock.create(
                meeting.getStartTime(),
                meeting.getDurationMinutes(),
                CognitiveState.SHALLOW_WORK));

        return InterruptionResult.safeAutoTruncation(meeting, blocks, windowMinutes);
    }

    // -------------------------------------------------------------------------
    // Transition & metadata
    // -------------------------------------------------------------------------

    @Override
    public DeveloperState transition(LocalDateTime now) {
        // The engine drives transitions via block completion events;
        // this state does not self-terminate via time alone.
        return this;
    }

    @Override
    public String getStateDescription() {
        return "Deep Work: Foco cognitivo máximo. Proteja esta janela de interrupções. " +
               "Bloco iniciado às " + blockStartTime.toLocalTime() + ".";
    }

    @Override
    public boolean canStartNewBlock() {
        return false; // Already executing a Deep Work block
    }

    public LocalDateTime getBlockStartTime() {
        return blockStartTime;
    }

    public int getPreBlockAccumulatedDeepWork() {
        return preBlockAccumulatedDeepWork;
    }

    /**
     * Computes the total accumulated Deep Work including the elapsed time
     * in the current block. Used for real-time display in the dashboard.
     *
     * @param now Current instant
     * @return Total Deep Work minutes (pre-block + elapsed in current block)
     */
    public int computeTotalAccumulated(LocalDateTime now) {
        int elapsed = (int) ChronoUnit.MINUTES.between(blockStartTime, now);
        return preBlockAccumulatedDeepWork + Math.max(0, elapsed);
    }
}
