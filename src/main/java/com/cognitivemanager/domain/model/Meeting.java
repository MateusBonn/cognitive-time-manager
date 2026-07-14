package com.cognitivemanager.domain.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain model representing an external calendar meeting event.
 *
 * Immutable value object. Arrives via the Adapter Pattern from external
 * calendar systems (Teams / Google Calendar / Outlook) through
 * {@link com.cognitivemanager.adapter.in.messaging.MeetingEventConsumer}.
 *
 * Per RNF03: no framework annotations.
 */
public final class Meeting {

    private final UUID id;
    private final String title;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final boolean urgent;
    private final String sourceSystem; // "TEAMS" | "GOOGLE" | "OUTLOOK"

    public Meeting(UUID id, String title, LocalDateTime startTime, LocalDateTime endTime, boolean urgent) {
        this(id, title, startTime, endTime, urgent, "UNKNOWN");
    }

    public Meeting(
            UUID id,
            String title,
            LocalDateTime startTime,
            LocalDateTime endTime,
            boolean urgent,
            String sourceSystem) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.title = Objects.requireNonNull(title, "title must not be null");
        this.startTime = Objects.requireNonNull(startTime, "startTime must not be null");
        this.endTime = Objects.requireNonNull(endTime, "endTime must not be null");
        this.sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem must not be null");
        this.urgent = urgent;

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException(
                    "endTime must be strictly after startTime. startTime=" + startTime + ", endTime=" + endTime);
        }
    }

    // -------------------------------------------------------------------------
    // Computed properties
    // -------------------------------------------------------------------------

    /**
     * Returns duration of this meeting in whole minutes (truncated).
     * Used to size the SHALLOW_WORK block assigned to the meeting slot.
     */
    public int getDurationMinutes() {
        return (int) ChronoUnit.MINUTES.between(startTime, endTime);
    }

    /**
     * Returns the window (in minutes) from a given instant until this meeting starts.
     * Used by the interruption handler to determine the interruption scenario.
     *
     * @param from The reference point (typically {@code LocalDateTime.now()})
     * @return Positive minutes remaining; negative if meeting is already past
     */
    public long minutesUntilStart(LocalDateTime from) {
        return ChronoUnit.MINUTES.between(from, startTime);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public boolean isUrgent() { return urgent; }
    public String getSourceSystem() { return sourceSystem; }

    @Override
    public String toString() {
        return String.format("Meeting{title='%s', %s → %s, urgent=%s, source=%s}",
                title, startTime, endTime, urgent, sourceSystem);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Meeting other)) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
