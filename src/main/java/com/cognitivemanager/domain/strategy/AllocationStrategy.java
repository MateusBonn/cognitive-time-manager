package com.cognitivemanager.domain.strategy;

import com.cognitivemanager.domain.model.TimeBlock;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DESIGN PATTERN: Strategy Pattern — Algorithm Interface
 *
 * Defines the contract for time block allocation algorithms.
 * The engine selects the appropriate concrete strategy based on the day's
 * schedule density (meeting count and total meeting minutes).
 *
 * Two strategies are provided:
 * <ul>
 *   <li>{@link SparseScheduleStrategy} — for light meeting days; maximises rest (R_max).</li>
 *   <li>{@link DenseScheduleStrategy} — for heavy meeting days; minimises rest (R_min)
 *       to pack more Deep Work into fragmented gaps.</li>
 * </ul>
 *
 * The Strategy Pattern is the correct fit here because:
 * <ul>
 *   <li>The allocation algorithm changes based on a runtime condition (day density).</li>
 *   <li>Both variants share the same recursive structure but differ in the rest
 *       duration decision — the only parameter that should vary.</li>
 *   <li>New strategies (e.g., TRAVEL_DAY, DEADLINE_SPRINT) can be added
 *       without touching the engine — satisfying Open/Closed (RNF02).</li>
 * </ul>
 *
 * Per RNF03: no framework annotations.
 */
public interface AllocationStrategy {

    /**
     * Recursively allocates time blocks for a contiguous free window.
     *
     * The recursion terminates when:
     * <ul>
     *   <li>{@code freeMinutes < D_min} → classify remaining time as SHALLOW_WORK</li>
     *   <li>{@code accumulatedDeepWork >= L_max} → all remaining time is SHALLOW_WORK</li>
     * </ul>
     *
     * @param windowStart         Start of the free time window
     * @param freeMinutes         Total available minutes in this window (must be ≥ 0)
     * @param accumulatedDeepWork Deep Work minutes already consumed today (0 to L_max)
     * @return Ordered list of {@link TimeBlock}s filling the window exactly
     */
    List<TimeBlock> allocate(LocalDateTime windowStart, int freeMinutes, int accumulatedDeepWork);

    /**
     * Human-readable name for logging and debugging.
     */
    String getStrategyName();

    /**
     * Returns {@code true} if this strategy should be applied given the day's
     * meeting load. Used by the engine to select among registered strategies.
     *
     * @param meetingCountToday    Total meetings scheduled for the day
     * @param totalMeetingMinutes  Sum of all meeting durations in minutes
     */
    boolean isApplicable(int meetingCountToday, int totalMeetingMinutes);
}
