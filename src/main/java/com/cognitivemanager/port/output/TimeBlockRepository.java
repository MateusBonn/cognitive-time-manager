package com.cognitivemanager.port.output;

import com.cognitivemanager.domain.model.TimeBlock;

import java.time.LocalDate;
import java.util.List;

/**
 * SECONDARY PORT (Driven / Outbound)
 *
 * Read-only query port for {@link TimeBlock} data.
 * Provides aggregated queries needed by the application services without
 * exposing JPA or persistence details to the domain.
 *
 * Note: writes go through {@link ScheduleDayRepository#save} (aggregate root pattern);
 * this port only exposes read operations on blocks, particularly the Deep Work
 * accumulation query critical for the daily limit enforcement (RF03).
 */
public interface TimeBlockRepository {

    /**
     * Returns all time blocks allocated for a developer on a given date.
     *
     * @param developerId The developer's unique identifier
     * @param date        The target date
     * @return List of blocks (may be empty if no schedule exists)
     */
    List<TimeBlock> findByDeveloperIdAndDate(String developerId, LocalDate date);

    /**
     * Returns the total Deep Work minutes accumulated by a developer on a specific date.
     * This is the key query driving the L_max daily limit enforcement (RF03).
     *
     * Returns 0 if no DEEP_WORK blocks exist for the given developer and date.
     *
     * @param developerId The developer's unique identifier
     * @param date        The target date
     * @return Sum of all DEEP_WORK block durations in minutes
     */
    int sumDeepWorkMinutesByDeveloperAndDate(String developerId, LocalDate date);
}
