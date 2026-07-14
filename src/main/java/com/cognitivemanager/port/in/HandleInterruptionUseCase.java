package com.cognitivemanager.port.in;

import com.cognitivemanager.domain.model.InterruptionResult;
import com.cognitivemanager.domain.model.Meeting;
import com.cognitivemanager.domain.model.ScheduleDay;

import java.time.LocalDateTime;

/**
 * PRIMARY PORT (Driver / Inbound Use Case)
 *
 * Entry point for handling meeting interruptions against an active Deep Work session.
 *
 * RF04: Re-calculates and proposes transition options when an urgent meeting appears.
 * RF05: Forces immediate rest if the window to the meeting is ≤ 25 min.
 */
public interface HandleInterruptionUseCase {

    /**
     * Evaluates an urgent meeting interruption against the developer's current state
     * and returns the computed scenario with proposed time blocks.
     *
     * For EMERGENCY_COOLDOWN, the result is applied immediately (no user input required).
     * For DECISION_REQUIRED, the result is sent to the developer via WebSocket and
     * the decision is awaited via {@link #applyInterruptionDecision}.
     * For SAFE_AUTO_TRUNCATION, the result is applied immediately.
     *
     * @param developerId   Developer whose active session is being interrupted
     * @param urgentMeeting The meeting causing the interruption
     * @param now           Exact current timestamp (second-precision for accumulation)
     * @return The computed {@link InterruptionResult}
     */
    InterruptionResult handleInterruption(
            String developerId,
            Meeting urgentMeeting,
            LocalDateTime now);

    /**
     * Applies the developer's explicit decision following a DECISION_REQUIRED result.
     *
     * Option A — {@link InterruptionDecision#SWITCH_TO_SHALLOW}:
     *   Transitions immediately to SHALLOW_WORK. No rest is scheduled before the meeting.
     *   All time from now to meeting start is free for lightweight tasks.
     *
     * Option B — {@link InterruptionDecision#PERSIST_DEEP_WORK}:
     *   Maintains DEEP_WORK until M_start − 20 min.
     *   At that point, a 20-min FORCED_REST is automatically injected.
     *   The Deep Work time up to that truncation point is counted toward L_max.
     *
     * @param developerId Developer making the decision
     * @param decision    The chosen option
     * @param meetingId   UUID string of the meeting that triggered DECISION_REQUIRED
     * @return The updated {@link ScheduleDay} with the decision applied
     */
    ScheduleDay applyInterruptionDecision(
            String developerId,
            InterruptionDecision decision,
            String meetingId);

    /**
     * The developer's response to a DECISION_REQUIRED interruption scenario.
     */
    enum InterruptionDecision {

        /**
         * Opção A: Switch to SHALLOW_WORK immediately.
         * No cognitive rest is needed; the developer is already winding down.
         */
        SWITCH_TO_SHALLOW,

        /**
         * Opção B: Continue DEEP_WORK.
         * The engine schedules mandatory FORCED_REST starting at M_start − 20 min.
         * Deep Work elapsed time is counted toward the daily limit.
         */
        PERSIST_DEEP_WORK
    }
}
