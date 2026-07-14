package com.cognitivemanager.domain.state;

import com.cognitivemanager.domain.model.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * DESIGN PATTERN: State Pattern — Concrete State: FORCED_REST
 *
 * Mandatory cognitive recovery window. This state cannot be voluntarily exited
 * by the developer — only by either: (a) the rest timer reaching its end time,
 * or (b) an overriding meeting that starts before the rest ends.
 *
 * Biological rationale: after 85–125 min of Deep Work, the prefrontal cortex
 * requires a recovery period before re-entering a focused state. Skipping rest
 * leads to compounding fatigue and diminishing returns.
 *
 * Transitions:
 * <ul>
 *   <li>{@code now >= restEndTime} → {@link IdleState}</li>
 *   <li>Meeting starts before rest ends → rest is shortened (meeting takes priority)</li>
 * </ul>
 */
public final class ForcedRestState implements DeveloperState {

    private final LocalDateTime restStartTime;
    private final LocalDateTime restEndTime;

    /**
     * @param restStartTime When the rest began
     * @param restEndTime   When the rest is scheduled to end (must be after restStartTime)
     */
    public ForcedRestState(LocalDateTime restStartTime, LocalDateTime restEndTime) {
        this.restStartTime = Objects.requireNonNull(restStartTime, "restStartTime must not be null");
        this.restEndTime = Objects.requireNonNull(restEndTime, "restEndTime must not be null");
        if (!restEndTime.isAfter(restStartTime)) {
            throw new IllegalArgumentException(
                    "restEndTime must be after restStartTime. Got: " + restStartTime + " → " + restEndTime);
        }
    }

    @Override
    public CognitiveState getCognitiveState() {
        return CognitiveState.FORCED_REST;
    }

    /**
     * Handles a meeting interruption during Forced Rest.
     * <p>
     * If the meeting starts before the rest ends, the rest is shortened to the
     * meeting start time (the meeting takes scheduling priority, but no Deep Work
     * accumulation changes — the rest is just cut short).
     * <p>
     * If the meeting starts after the rest ends, the rest completes normally and
     * the meeting is simply scheduled in the subsequent IDLE window.
     */
    @Override
    public InterruptionResult handleMeetingInterruption(
            Meeting urgentMeeting, LocalDateTime now, int accumulatedDeepWork) {

        Objects.requireNonNull(urgentMeeting, "urgentMeeting must not be null");
        Objects.requireNonNull(now, "now must not be null");

        long windowMinutes = ChronoUnit.MINUTES.between(now, urgentMeeting.getStartTime());
        long remainingRestMinutes = ChronoUnit.MINUTES.between(now, restEndTime);

        List<TimeBlock> blocks = new ArrayList<>();

        if (windowMinutes < remainingRestMinutes) {
            // Meeting starts before rest ends — rest is cut short
            if (windowMinutes > 0) {
                blocks.add(TimeBlock.createInterrupted(now, (int) windowMinutes, CognitiveState.FORCED_REST));
            }
        }
        // else: rest completes naturally before the meeting — no changes needed to rest block

        // Meeting slot as SHALLOW_WORK
        blocks.add(TimeBlock.create(
                urgentMeeting.getStartTime(),
                urgentMeeting.getDurationMinutes(),
                CognitiveState.SHALLOW_WORK));

        return InterruptionResult.safeAutoTruncation(urgentMeeting, blocks, windowMinutes);
    }

    /**
     * Checks if the rest period has ended and transitions to IDLE if so.
     */
    @Override
    public DeveloperState transition(LocalDateTime now) {
        if (!now.isBefore(restEndTime)) {
            return new IdleState();
        }
        return this;
    }

    @Override
    public String getStateDescription() {
        return String.format(
                "Descanso Forçado: Recuperação cognitiva obrigatória até %s. " +
                "Não inicie novas tarefas cognitivas.",
                restEndTime.toLocalTime());
    }

    @Override
    public boolean canStartNewBlock() {
        return false; // Must complete rest before allocating a new block
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public LocalDateTime getRestStartTime() { return restStartTime; }
    public LocalDateTime getRestEndTime() { return restEndTime; }

    /**
     * Returns minutes remaining in the rest period from the given instant.
     * Returns 0 if the rest has already completed.
     */
    public long remainingMinutes(LocalDateTime now) {
        return Math.max(0, ChronoUnit.MINUTES.between(now, restEndTime));
    }

    /**
     * Returns the planned rest duration in minutes.
     */
    public int plannedDurationMinutes() {
        return (int) ChronoUnit.MINUTES.between(restStartTime, restEndTime);
    }
}
