package com.cognitivemanager.domain.model;

import com.cognitivemanager.domain.constants.EngineConstants;

import java.time.LocalDate;
import java.util.*;

/**
 * Aggregate root for a developer's cognitive schedule on a single calendar day.
 *
 * Immutable: every mutation returns a new instance. This makes the aggregate
 * trivially thread-safe and simplifies event replay (no defensive copies needed
 * when passing to the engine or storing in the repository).
 *
 * Tracks:
 * <ul>
 *   <li>All allocated {@link TimeBlock}s for the day.</li>
 *   <li>All known {@link Meeting}s (inputs to the allocation engine).</li>
 *   <li>The cumulative Deep Work minutes consumed (against {@link EngineConstants#L_MAX}).</li>
 * </ul>
 *
 * Per RNF03: no framework annotations. No Jakarta/JPA on this class.
 */
public final class ScheduleDay {

    private final UUID id;
    private final LocalDate date;
    private final String developerId;
    private final List<TimeBlock> blocks;
    private final List<Meeting> meetings;
    private final int cumulativeDeepWorkMinutes;

    private ScheduleDay(
            UUID id,
            LocalDate date,
            String developerId,
            List<TimeBlock> blocks,
            List<Meeting> meetings,
            int cumulativeDeepWorkMinutes) {
        this.id = id;
        this.date = date;
        this.developerId = developerId;
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
        this.meetings = Collections.unmodifiableList(new ArrayList<>(meetings));
        this.cumulativeDeepWorkMinutes = cumulativeDeepWorkMinutes;
    }

    // -------------------------------------------------------------------------
    // DESIGN PATTERN: Factory Pattern — named constructors
    // -------------------------------------------------------------------------

    /** Creates an empty schedule day with no blocks and no meetings. */
    public static ScheduleDay createEmpty(String developerId, LocalDate date) {
        Objects.requireNonNull(developerId, "developerId must not be null");
        Objects.requireNonNull(date, "date must not be null");
        return new ScheduleDay(UUID.randomUUID(), date, developerId,
                List.of(), List.of(), 0);
    }

    /**
     * Reconstitutes a persisted ScheduleDay from the repository layer.
     * Used by the JPA adapter to map entity → domain object.
     */
    public static ScheduleDay reconstitute(
            UUID id, LocalDate date, String developerId,
            List<TimeBlock> blocks, List<Meeting> meetings) {
        int cumulative = blocks.stream()
                .filter(TimeBlock::isDeepWork)
                .mapToInt(TimeBlock::getDurationMinutes)
                .sum();
        return new ScheduleDay(id, date, developerId, blocks, meetings, cumulative);
    }

    // -------------------------------------------------------------------------
    // Immutable mutation — return new instances
    // -------------------------------------------------------------------------

    /**
     * Returns a new ScheduleDay with the given list of blocks replacing the current ones.
     * Recalculates cumulative Deep Work automatically.
     */
    public ScheduleDay withBlocks(List<TimeBlock> newBlocks) {
        int newCumulative = newBlocks.stream()
                .filter(TimeBlock::isDeepWork)
                .mapToInt(TimeBlock::getDurationMinutes)
                .sum();
        return new ScheduleDay(this.id, this.date, this.developerId,
                newBlocks, this.meetings, newCumulative);
    }

    /**
     * Returns a new ScheduleDay with an additional meeting appended.
     * Called when a calendar event arrives for this day.
     */
    public ScheduleDay withMeeting(Meeting meeting) {
        Objects.requireNonNull(meeting, "meeting must not be null");
        // Idempotent: skip if the same meeting ID already exists
        boolean alreadyPresent = this.meetings.stream()
                .anyMatch(m -> m.getId().equals(meeting.getId()));
        if (alreadyPresent) {
            return this;
        }
        List<Meeting> updated = new ArrayList<>(this.meetings);
        updated.add(meeting);
        return new ScheduleDay(this.id, this.date, this.developerId,
                this.blocks, updated, this.cumulativeDeepWorkMinutes);
    }

    /**
     * Returns a new ScheduleDay without the meeting identified by meetingId.
     * Used for cancellation events.
     */
    public ScheduleDay withoutMeeting(UUID meetingId) {
        List<Meeting> updated = this.meetings.stream()
                .filter(m -> !m.getId().equals(meetingId))
                .collect(java.util.stream.Collectors.toList());
        return new ScheduleDay(this.id, this.date, this.developerId,
                this.blocks, updated, this.cumulativeDeepWorkMinutes);
    }

    // -------------------------------------------------------------------------
    // Domain queries
    // -------------------------------------------------------------------------

    /**
     * Returns true if the cumulative Deep Work has reached or exceeded
     * {@link EngineConstants#L_MAX} (250 min). All subsequent free windows
     * must be classified as SHALLOW_WORK (RF03).
     */
    public boolean isDailyDeepWorkLimitReached() {
        return cumulativeDeepWorkMinutes >= EngineConstants.L_MAX;
    }

    /**
     * Returns how many Deep Work minutes remain before the daily limit.
     * Returns 0 if the limit has already been reached.
     */
    public int getRemainingDeepWorkCapacity() {
        return Math.max(0, EngineConstants.L_MAX - cumulativeDeepWorkMinutes);
    }

    /**
     * Returns total meetings duration in minutes (used by the strategy selector
     * to distinguish dense vs. sparse schedule days).
     */
    public int getTotalMeetingMinutes() {
        return meetings.stream().mapToInt(Meeting::getDurationMinutes).sum();
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public LocalDate getDate() { return date; }
    public String getDeveloperId() { return developerId; }
    public List<TimeBlock> getBlocks() { return blocks; }
    public List<Meeting> getMeetings() { return meetings; }
    public int getCumulativeDeepWorkMinutes() { return cumulativeDeepWorkMinutes; }

    @Override
    public String toString() {
        return String.format("ScheduleDay{developerId='%s', date=%s, blocks=%d, meetings=%d, deepWork=%d/%d min}",
                developerId, date, blocks.size(), meetings.size(),
                cumulativeDeepWorkMinutes, EngineConstants.L_MAX);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScheduleDay other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
