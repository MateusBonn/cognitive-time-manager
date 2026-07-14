package com.cognitivemanager.domain.engine;

import com.cognitivemanager.domain.constants.EngineConstants;
import com.cognitivemanager.domain.model.InterruptionResult;
import com.cognitivemanager.domain.model.InterruptionScenario;
import com.cognitivemanager.domain.model.Meeting;
import com.cognitivemanager.domain.state.DeveloperState;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Domain service that routes interruption requests to the correct state handler.
 *
 * DESIGN PATTERN: State Pattern — this handler is the context object that
 * delegates all business logic to the current {@link DeveloperState}. It does
 * not contain any scenario-specific code; the states own their own logic.
 *
 * DESIGN PATTERN: Strategy Pattern (implicit) — the correct state already
 * encodes the right algorithm; this handler simply invokes it.
 *
 * Responsibility:
 * <ul>
 *   <li>Pre-validates that the meeting is in the future.</li>
 *   <li>Classifies the window (static utility — usable before a state is loaded).</li>
 *   <li>Delegates to the state's {@code handleMeetingInterruption} method.</li>
 * </ul>
 *
 * Per RNF03: no framework annotations. Instantiated by the application service.
 */
public final class InterruptionHandler {

    /**
     * Evaluates an urgent meeting interruption and returns the computed result.
     *
     * @param currentState       Developer's current cognitive state (State Pattern context)
     * @param urgentMeeting      The meeting triggering the interruption
     * @param now                Current timestamp — must be before meeting start
     * @param accumulatedDeepWork Minutes of Deep Work already consumed today
     * @return Fully computed {@link InterruptionResult} with proposed blocks and scenario
     * @throws IllegalArgumentException if the meeting has already started
     */
    public InterruptionResult handle(
            DeveloperState currentState,
            Meeting urgentMeeting,
            LocalDateTime now,
            int accumulatedDeepWork) {

        Objects.requireNonNull(currentState, "currentState must not be null");
        Objects.requireNonNull(urgentMeeting, "urgentMeeting must not be null");
        Objects.requireNonNull(now, "now must not be null");

        long windowMinutes = ChronoUnit.MINUTES.between(now, urgentMeeting.getStartTime());

        if (windowMinutes < 0) {
            throw new IllegalArgumentException(
                    "Meeting has already started. Meeting start: " + urgentMeeting.getStartTime()
                    + ", now: " + now);
        }

        // Delegate entirely to the current state — this is the State Pattern in action.
        // DeepWorkState contains the three-scenario logic; other states have simpler handling.
        return currentState.handleMeetingInterruption(urgentMeeting, now, accumulatedDeepWork);
    }

    /**
     * Classifies an interruption window without requiring a loaded state context.
     * Useful for pre-processing events before the session state is reconstructed.
     *
     * @param windowMinutes Minutes between now and the meeting start
     * @return The applicable {@link InterruptionScenario}
     */
    public static InterruptionScenario classifyWindow(long windowMinutes) {
        if (windowMinutes <= EngineConstants.EMERGENCY_THRESHOLD) {
            return InterruptionScenario.EMERGENCY_COOLDOWN;
        } else if (windowMinutes < EngineConstants.SAFE_THRESHOLD) {
            return InterruptionScenario.DECISION_REQUIRED;
        } else {
            return InterruptionScenario.SAFE_AUTO_TRUNCATION;
        }
    }
}
