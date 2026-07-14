package com.cognitivemanager.port.in;

import com.cognitivemanager.domain.model.ScheduleDay;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PRIMARY PORT (Driver / Inbound Use Case)
 *
 * Defines the entry point for cognitive time allocation operations.
 * This port belongs to the hexagonal architecture boundary: anything outside
 * the domain (REST controllers, RabbitMQ consumers, schedulers) calls this
 * interface — never the application service directly.
 *
 * RF02: Calculates recursive time windows from free intervals.
 * RF01: Triggered after calendar event mutations are ingested.
 */
public interface AllocateTimeUseCase {

    /**
     * Computes and persists the optimal cognitive schedule for a developer's day.
     * If a schedule already exists for this date, it is replaced.
     *
     * @param developerId  Unique identifier for the developer
     * @param date         Target date for allocation
     * @param workdayStart Start of the work window (inclusive)
     * @param workdayEnd   End of the work window (exclusive)
     * @return The fully allocated and persisted {@link ScheduleDay}
     */
    ScheduleDay allocateDay(
            String developerId,
            LocalDate date,
            LocalDateTime workdayStart,
            LocalDateTime workdayEnd);

    /**
     * Re-runs allocation after a schedule change (new meeting, cancellation, etc.).
     * Clears previously computed blocks and rebuilds from the current meeting list.
     *
     * RF01: Called by {@link ProcessMeetingEventUseCase} on every calendar mutation.
     *
     * @param developerId  Unique identifier for the developer
     * @param date         Date whose schedule must be recalculated
     * @param workdayStart Start of the work window
     * @param workdayEnd   End of the work window
     * @return The freshly allocated {@link ScheduleDay}
     */
    ScheduleDay reallocateDay(
            String developerId,
            LocalDate date,
            LocalDateTime workdayStart,
            LocalDateTime workdayEnd);
}
