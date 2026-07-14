package com.cognitivemanager.domain.model;

import com.cognitivemanager.domain.constants.EngineConstants;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * DESIGN PATTERN: Factory Pattern (static factory methods)
 *
 * Immutable value object representing a contiguous block of allocated cognitive time.
 * Invariants are enforced at creation time; no setter exists.
 *
 * The two factory methods ({@link #create} and {@link #createInterrupted}) encapsulate
 * all business rules for block construction, preventing invalid states from ever existing.
 *
 * Per RNF03: zero framework annotations. Pure Java domain class.
 */
public final class TimeBlock {

    private final UUID id;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final int durationMinutes;
    private final CognitiveState state;
    private final boolean interrupted;

    private TimeBlock(
            UUID id,
            LocalDateTime startTime,
            LocalDateTime endTime,
            int durationMinutes,
            CognitiveState state,
            boolean interrupted) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
        this.state = state;
        this.interrupted = interrupted;
    }

    // -------------------------------------------------------------------------
    // DESIGN PATTERN: Factory Pattern — guarded constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a normal (non-interrupted) time block.
     * Validates that duration is positive and state is non-null.
     *
     * @param startTime       Block start (inclusive)
     * @param durationMinutes Block length in minutes (must be > 0)
     * @param state           Cognitive state label for this block
     * @return A valid, immutable {@link TimeBlock}
     * @throws IllegalArgumentException if durationMinutes ≤ 0
     */
    public static TimeBlock create(LocalDateTime startTime, int durationMinutes, CognitiveState state) {
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(state, "state must not be null");
        if (durationMinutes <= 0) {
            throw new IllegalArgumentException(
                    "durationMinutes must be positive, got: " + durationMinutes);
        }
        return new TimeBlock(
                UUID.randomUUID(),
                startTime,
                startTime.plusMinutes(durationMinutes),
                durationMinutes,
                state,
                false);
    }

    /**
     * Creates an interrupted time block — used when Deep Work is cut short
     * by an emergency meeting (Cenário 1: Janela ≤ 25 min).
     * <p>
     * Unlike {@link #create}, this permits zero-minute duration because the
     * interruption may occur at the exact second the block started.
     * The accumulated time is still counted toward the daily limit (RF03).
     *
     * @param startTime             Original block start
     * @param actualDurationMinutes How many minutes actually elapsed before interruption
     * @param state                 Cognitive state at time of interruption
     * @return A flagged-as-interrupted {@link TimeBlock}
     * @throws IllegalArgumentException if actualDurationMinutes is negative
     */
    public static TimeBlock createInterrupted(
            LocalDateTime startTime, int actualDurationMinutes, CognitiveState state) {
        Objects.requireNonNull(startTime, "startTime must not be null");
        Objects.requireNonNull(state, "state must not be null");
        if (actualDurationMinutes < 0) {
            throw new IllegalArgumentException(
                    "actualDurationMinutes cannot be negative, got: " + actualDurationMinutes);
        }
        return new TimeBlock(
                UUID.randomUUID(),
                startTime,
                startTime.plusMinutes(actualDurationMinutes),
                actualDurationMinutes,
                state,
                true);
    }

    // -------------------------------------------------------------------------
    // Query methods
    // -------------------------------------------------------------------------

    public UUID getId() {
        return id;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public CognitiveState getState() {
        return state;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    /** Convenience: true when state == DEEP_WORK (used for accumulation math). */
    public boolean isDeepWork() {
        return state == CognitiveState.DEEP_WORK;
    }

    /**
     * Returns true if this block contributed to the daily Deep Work limit
     * (i.e. it is a DEEP_WORK block, whether interrupted or not — RF03: accumulation
     * is counted up to the exact second of interruption).
     */
    public boolean countsTowardDailyLimit() {
        return state == CognitiveState.DEEP_WORK;
    }

    /**
     * Returns the remaining minutes until daily limit ({@link EngineConstants#L_MAX})
     * is consumed, given the current accumulated total.
     */
    public static int remainingCapacity(int accumulatedDeepWorkMinutes) {
        return Math.max(0, EngineConstants.L_MAX - accumulatedDeepWorkMinutes);
    }

    @Override
    public String toString() {
        return String.format("TimeBlock{state=%s, %s → %s (%d min)%s}",
                state, startTime, endTime, durationMinutes,
                interrupted ? " [INTERRUPTED]" : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TimeBlock other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
