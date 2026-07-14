package com.cognitivemanager.domain.state;

import com.cognitivemanager.domain.model.CognitiveState;
import com.cognitivemanager.domain.model.InterruptionResult;
import com.cognitivemanager.domain.model.Meeting;

import java.time.LocalDateTime;

/**
 * DESIGN PATTERN: State Pattern — Context Interface
 *
 * Defines the contract for all cognitive states a developer can be in.
 * Each concrete state ({@link DeepWorkState}, {@link ShallowWorkState},
 * {@link ForcedRestState}, {@link IdleState}) encapsulates its own interruption
 * transition logic, removing any need for large {@code if/else instanceof} chains
 * in the engine.
 *
 * The State Pattern is the correct solution here because:
 * <ul>
 *   <li>The behaviour of {@code handleMeetingInterruption} is fundamentally
 *       different depending on the current state (DEEP_WORK requires the three-scenario
 *       evaluation; SHALLOW_WORK simply absorbs interruptions).</li>
 *   <li>Adding a new state (e.g., LUNCH_BREAK) requires only a new class, not
 *       modification of the engine — satisfying the Open/Closed Principle (RNF02).</li>
 * </ul>
 *
 * Per RNF03: no framework annotations.
 */
public interface DeveloperState {

    /**
     * Returns the {@link CognitiveState} enum value this state represents.
     * Used for labelling blocks, serialization, and WebSocket notifications.
     */
    CognitiveState getCognitiveState();

    /**
     * Evaluates an urgent meeting interruption and returns a fully computed
     * {@link InterruptionResult} containing proposed time blocks and the scenario.
     *
     * The three scenarios (EMERGENCY_COOLDOWN, DECISION_REQUIRED, SAFE_AUTO_TRUNCATION)
     * are only relevant when the developer is in DEEP_WORK. Other states delegate
     * to simpler logic.
     *
     * @param urgentMeeting           The meeting causing the interruption
     * @param now                     Exact current timestamp (second-precision matters for accumulation)
     * @param accumulatedDeepWork     Minutes of Deep Work accumulated today so far
     * @return The computed {@link InterruptionResult}
     */
    InterruptionResult handleMeetingInterruption(
            Meeting urgentMeeting, LocalDateTime now, int accumulatedDeepWork);

    /**
     * Evaluates whether the state should transition to another state based on
     * the current time (e.g., rest period has ended → transition to IDLE).
     * Returns {@code this} if no transition is warranted.
     *
     * @param now Current timestamp
     * @return The next state, or {@code this} if unchanged
     */
    DeveloperState transition(LocalDateTime now);

    /**
     * Human-readable description of what this state means for the developer.
     * Surfaced in WebSocket notifications.
     */
    String getStateDescription();

    /**
     * Returns {@code true} if the engine is allowed to start a new allocation block
     * for this state. For example, FORCED_REST and DEEP_WORK return {@code false}
     * because a block is already running.
     */
    boolean canStartNewBlock();
}
