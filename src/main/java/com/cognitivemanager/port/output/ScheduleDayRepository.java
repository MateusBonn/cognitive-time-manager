package com.cognitivemanager.port.output;

import com.cognitivemanager.domain.model.ScheduleDay;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * SECONDARY PORT (Driven / Outbound)
 *
 * Repository contract for {@link ScheduleDay} persistence.
 * The domain layer depends ONLY on this interface — never on JPA, SQL, or any
 * storage technology. The concrete implementation lives in the adapter layer
 * ({@link com.cognitivemanager.adapter.out.persistence.JpaScheduleDayRepository}).
 *
 * This interface is the boundary that makes the domain fully testable without
 * any database infrastructure (satisfying RNF02 — minimum coupling).
 */
public interface ScheduleDayRepository {

    /**
     * Persists or updates a {@link ScheduleDay}.
     * If a record with the same {@code id} exists, it is replaced.
     *
     * @param scheduleDay The aggregate root to persist
     * @return The persisted instance (may have updated metadata)
     */
    ScheduleDay save(ScheduleDay scheduleDay);

    /**
     * Finds the schedule day for a specific developer on a specific date.
     * Returns empty if no schedule has been allocated yet.
     *
     * @param developerId The developer's unique identifier
     * @param date        The target date
     * @return An {@link Optional} containing the schedule, or empty
     */
    Optional<ScheduleDay> findByDeveloperIdAndDate(String developerId, LocalDate date);

    /**
     * Finds a schedule day by its unique ID.
     *
     * @param id The aggregate root's UUID
     * @return An {@link Optional} containing the schedule, or empty
     */
    Optional<ScheduleDay> findById(UUID id);

    /**
     * Deletes the schedule day with the given ID.
     * Used when a full recalculation requires clearing stale data.
     *
     * @param id UUID of the schedule day to delete
     */
    void delete(UUID id);
}
