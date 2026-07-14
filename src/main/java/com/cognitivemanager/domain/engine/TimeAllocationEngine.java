package com.cognitivemanager.domain.engine;

import com.cognitivemanager.domain.model.CognitiveState;
import com.cognitivemanager.domain.model.Meeting;
import com.cognitivemanager.domain.model.ScheduleDay;
import com.cognitivemanager.domain.model.TimeBlock;
import com.cognitivemanager.domain.strategy.AllocationStrategy;
import com.cognitivemanager.domain.strategy.DenseScheduleStrategy;
import com.cognitivemanager.domain.strategy.SparseScheduleStrategy;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Core domain engine: consumes a {@link ScheduleDay} with known meetings and
 * produces an optimised list of {@link TimeBlock}s by filling every free window.
 *
 * DESIGN PATTERN: Strategy Pattern — the engine selects the appropriate
 * {@link AllocationStrategy} at runtime based on the day's meeting density.
 *
 * Processing logic:
 * <ol>
 *   <li>Sort meetings by start time.</li>
 *   <li>Walk the workday timeline; for each gap between meetings (or between
 *       workday start/end and meetings), call the strategy to fill the gap.</li>
 *   <li>Label each meeting slot itself as SHALLOW_WORK.</li>
 *   <li>Return a new, immutable {@link ScheduleDay} containing all blocks.</li>
 * </ol>
 *
 * RNF01: All computation runs in memory. For typical workdays (≤ 20 meetings,
 * 9-hour day) the recursive engine completes in &lt; 5 ms — well within the 50 ms SLA.
 *
 * Per RNF03: no framework annotations. This class is pure Java.
 */
public final class TimeAllocationEngine {

    /**
     * Strategy registry. Evaluated in order — first applicable strategy wins.
     * DenseScheduleStrategy is checked first because it has the more specific
     * applicability condition (both count AND total minutes must be high).
     * SparseScheduleStrategy acts as the safe default.
     */
    private final List<AllocationStrategy> strategies;

    public TimeAllocationEngine() {
        this.strategies = List.of(
                new DenseScheduleStrategy(),
                new SparseScheduleStrategy()
        );
    }

    /**
     * Processes the developer's day and returns a fully allocated {@link ScheduleDay}.
     *
     * @param day          The input schedule day (must contain all known meetings)
     * @param workdayStart Start of the productive day (e.g., 09:00)
     * @param workdayEnd   End of the productive day (e.g., 18:00)
     * @return A new {@link ScheduleDay} with all blocks populated
     */
    public ScheduleDay processDay(
            ScheduleDay day,
            LocalDateTime workdayStart,
            LocalDateTime workdayEnd) {

        if (!workdayEnd.isAfter(workdayStart)) {
            throw new IllegalArgumentException(
                    "workdayEnd must be after workdayStart. Got: " + workdayStart + " → " + workdayEnd);
        }

        // Sort meetings chronologically, filter to those within the workday
        List<Meeting> sortedMeetings = day.getMeetings().stream()
                .filter(m -> m.getStartTime().isBefore(workdayEnd)
                          && m.getEndTime().isAfter(workdayStart))
                .sorted(Comparator.comparing(Meeting::getStartTime))
                .toList();

        int totalMeetingMinutes = sortedMeetings.stream()
                .mapToInt(Meeting::getDurationMinutes)
                .sum();

        // DESIGN PATTERN: Strategy Pattern — runtime algorithm selection
        AllocationStrategy strategy = selectStrategy(sortedMeetings.size(), totalMeetingMinutes);

        List<TimeBlock> allBlocks = new ArrayList<>();
        int accumulated = day.getCumulativeDeepWorkMinutes();
        LocalDateTime cursor = workdayStart;

        for (Meeting meeting : sortedMeetings) {
            // Clamp meeting to workday boundaries
            LocalDateTime meetingStart = laterOf(meeting.getStartTime(), workdayStart);
            LocalDateTime meetingEnd = earlierOf(meeting.getEndTime(), workdayEnd);

            // Fill the free window BEFORE this meeting
            if (meetingStart.isAfter(cursor)) {
                long freeMinutes = ChronoUnit.MINUTES.between(cursor, meetingStart);
                if (freeMinutes > 0) {
                    List<TimeBlock> freeBlocks = strategy.allocate(cursor, (int) freeMinutes, accumulated);
                    allBlocks.addAll(freeBlocks);
                    accumulated = recalculate(accumulated, freeBlocks);
                }
            }

            // Meeting itself → SHALLOW_WORK
            int meetingDuration = (int) ChronoUnit.MINUTES.between(meetingStart, meetingEnd);
            if (meetingDuration > 0) {
                allBlocks.add(TimeBlock.create(meetingStart, meetingDuration, CognitiveState.SHALLOW_WORK));
            }

            cursor = meetingEnd;
        }

        // Fill any remaining time after the last meeting (or the entire day if no meetings)
        if (cursor.isBefore(workdayEnd)) {
            long remainingMinutes = ChronoUnit.MINUTES.between(cursor, workdayEnd);
            if (remainingMinutes > 0) {
                List<TimeBlock> remainingBlocks = strategy.allocate(cursor, (int) remainingMinutes, accumulated);
                allBlocks.addAll(remainingBlocks);
            }
        }

        return day.withBlocks(allBlocks);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Selects the most appropriate strategy for the day's meeting density.
     * Falls back to {@link SparseScheduleStrategy} if no strategy explicitly claims
     * applicability (which should never happen given the current strategy set).
     */
    private AllocationStrategy selectStrategy(int meetingCount, int totalMeetingMinutes) {
        return strategies.stream()
                .filter(s -> s.isApplicable(meetingCount, totalMeetingMinutes))
                .findFirst()
                .orElse(new SparseScheduleStrategy());
    }

    /**
     * Recalculates accumulated Deep Work by adding all DEEP_WORK block durations
     * from a freshly generated block list.
     */
    private int recalculate(int currentAccumulated, List<TimeBlock> newBlocks) {
        return currentAccumulated + newBlocks.stream()
                .filter(TimeBlock::isDeepWork)
                .mapToInt(TimeBlock::getDurationMinutes)
                .sum();
    }

    private LocalDateTime laterOf(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }

    private LocalDateTime earlierOf(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
